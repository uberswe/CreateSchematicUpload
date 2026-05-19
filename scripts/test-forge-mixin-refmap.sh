#!/usr/bin/env bash
# ============================================================================
# Forge Mixin Refmap / SRG Name Verification Test
# ============================================================================
# Verifies that Forge mixin annotations will work at runtime by checking:
#   1. If the built JAR contains the declared refmap files, OR
#   2. All mixin method/target references use SRG names with remap=false
#
# Background: Forge uses SRG-named methods at runtime. Without a refmap,
# Mojang-mapped names like "init" or "renderBg" won't be found, causing
# MixinTransformerError at startup.
#
# Requires: a completed ./gradlew build
# ============================================================================
set -uo pipefail

ERRORS=0
TESTS=0

pass() {
    TESTS=$((TESTS + 1))
    echo "  PASS: $1"
}

fail() {
    TESTS=$((TESTS + 1))
    ERRORS=$((ERRORS + 1))
    echo "  FAIL: $1"
}

warn() {
    echo "  WARN: $1"
}

echo "=== Forge Mixin Refmap / SRG Name Verification ==="
echo ""

# ---------------------------------------------------------------------------
# 1. Find the built Forge JAR
# ---------------------------------------------------------------------------
FORGE_JAR=""
for loader in forge; do
    if [ -d "${loader}/build/libs" ]; then
        candidate=$(find "$(pwd)/${loader}/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-sources*' 2>/dev/null | head -1)
        if [ -n "$candidate" ]; then
            FORGE_JAR="$candidate"
            break
        fi
    fi
done

if [ -z "$FORGE_JAR" ]; then
    echo "SKIP: No built Forge JAR found. Run ./gradlew build first."
    exit 0
fi
echo "Forge JAR: $(basename "$FORGE_JAR")"
echo ""

# ---------------------------------------------------------------------------
# 2. Check for refmap files in the JAR
# ---------------------------------------------------------------------------
echo "--- Checking refmap files in JAR ---"

TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

REFMAP_FILES=$(jar tf "$FORGE_JAR" | grep "refmap" || true)
HAS_REFMAP=false

if [ -n "$REFMAP_FILES" ]; then
    HAS_REFMAP=true
    for rf in $REFMAP_FILES; do
        pass "Refmap found in JAR: $rf"
    done
else
    warn "No refmap files found in JAR — all mixin annotations must use SRG names with remap=false"
fi
echo ""

# ---------------------------------------------------------------------------
# 3. Find Forge mixin source files
# ---------------------------------------------------------------------------
echo "--- Checking Forge mixin annotations ---"

FORGE_MIXIN_DIR="forge/src/main/java"
if [ ! -d "$FORGE_MIXIN_DIR" ]; then
    echo "SKIP: No forge source directory found"
    exit 0
fi

MIXIN_FILES=$(find "$FORGE_MIXIN_DIR" -name "*Mixin*.java" -type f 2>/dev/null)
if [ -z "$MIXIN_FILES" ]; then
    echo "SKIP: No mixin files found in forge source"
    exit 0
fi

# ---------------------------------------------------------------------------
# 4. For each mixin file, check method references
# ---------------------------------------------------------------------------
# SRG pattern: m_XXXXX_ (e.g., m_7856_)
SRG_PATTERN='^m_[0-9]+_$'
# Allowed non-SRG patterns that don't need remapping
SAFE_PATTERN='^(lambda\$|<init>|<clinit>)'

for mixin_file in $MIXIN_FILES; do
    echo ""
    echo "  File: $(basename "$mixin_file")"

    # Extract lines with method = "..." from mixin annotations
    METHOD_REFS=$(grep -noE 'method\s*=\s*"[^"]*"' "$mixin_file" || true)

    if [ -z "$METHOD_REFS" ]; then
        pass "No method references found (nothing to check)"
        continue
    fi

    while IFS= read -r line; do
        line_num=$(echo "$line" | cut -d: -f1)
        method_val=$(echo "$line" | sed 's/.*method *= *"\([^"]*\)".*/\1/')

        # Check if this method value is an SRG name or safe pattern
        if echo "$method_val" | grep -qE "$SRG_PATTERN"; then
            # SRG name — check that remap = false is present
            # Look at the surrounding annotation (within 5 lines)
            context=$(sed -n "$((line_num > 5 ? line_num - 5 : 1)),${line_num}p" "$mixin_file")
            end_context=$(sed -n "${line_num},$((line_num + 5))p" "$mixin_file")
            full_context="${context}${end_context}"

            if echo "$full_context" | grep -q "remap\s*=\s*false"; then
                pass "Line $line_num: method=\"$method_val\" (SRG) with remap=false"
            else
                fail "Line $line_num: method=\"$method_val\" (SRG) but missing remap=false — SRG names require remap=false"
            fi
        elif echo "$method_val" | grep -qE "$SAFE_PATTERN"; then
            # Lambda or constructor — check remap=false is present
            context=$(sed -n "$((line_num > 5 ? line_num - 5 : 1)),${line_num}p" "$mixin_file")
            end_context=$(sed -n "${line_num},$((line_num + 5))p" "$mixin_file")
            full_context="${context}${end_context}"

            if echo "$full_context" | grep -q "remap\s*=\s*false"; then
                pass "Line $line_num: method=\"$method_val\" (safe pattern) with remap=false"
            else
                if [ "$HAS_REFMAP" = true ]; then
                    pass "Line $line_num: method=\"$method_val\" (safe pattern) — refmap present"
                else
                    fail "Line $line_num: method=\"$method_val\" needs remap=false (no refmap in JAR, lambda/synth names aren't remapped)"
                fi
            fi
        else
            # Mojang-mapped name — only OK if refmap is present
            if [ "$HAS_REFMAP" = true ]; then
                pass "Line $line_num: method=\"$method_val\" (Mojang) — refmap will handle remapping"
            else
                fail "Line $line_num: method=\"$method_val\" is a Mojang-mapped name but NO REFMAP in JAR — will fail at runtime on Forge"
                echo "        Hint: Use the SRG name with remap=false, or fix refmap generation"
            fi
        fi
    done <<< "$METHOD_REFS"

    # Also check target strings in @At annotations for Mojang method names
    TARGET_REFS=$(grep -noE 'target\s*=\s*"[^"]*"' "$mixin_file" || true)

    if [ -n "$TARGET_REFS" ]; then
        while IFS= read -r line; do
            line_num=$(echo "$line" | cut -d: -f1)
            target_val=$(echo "$line" | sed 's/.*target *= *"\([^"]*\)".*/\1/')

            # Extract the method name from the target descriptor (format: Lclass;methodName(desc)R)
            target_method=$(echo "$target_val" | sed 's/.*;\([^(]*\)(.*/\1/')

            if echo "$target_method" | grep -qE "$SRG_PATTERN"; then
                pass "Line $line_num: target method \"$target_method\" is SRG"
            elif echo "$target_method" | grep -qE "^(render|draw|blit|get|set|is|has|add|remove|clear|size|contains|put)" && [ "$HAS_REFMAP" = false ]; then
                # Check if the enclosing annotation has remap=false
                context=$(sed -n "$((line_num > 10 ? line_num - 10 : 1)),${line_num}p" "$mixin_file")
                end_context=$(sed -n "${line_num},$((line_num + 5))p" "$mixin_file")
                full_context="${context}${end_context}"

                # Check if this is targeting a Create mod class (not vanilla)
                if echo "$target_val" | grep -qE "com/simibubi/create|com/uberswe"; then
                    pass "Line $line_num: target \"$target_method\" is a mod class (not remapped)"
                elif echo "$full_context" | grep -q "remap\s*=\s*false"; then
                    fail "Line $line_num: target method \"$target_method\" is Mojang-mapped in a remap=false annotation — must use SRG name"
                    echo "        Target: $target_val"
                else
                    fail "Line $line_num: target method \"$target_method\" is Mojang-mapped with no refmap"
                    echo "        Target: $target_val"
                fi
            fi
        done <<< "$TARGET_REFS"
    fi
done

# ---------------------------------------------------------------------------
# 5. Also check common mixin configs declare refmaps
# ---------------------------------------------------------------------------
echo ""
echo "--- Checking mixin config declarations ---"

for config_file in $(find . -name "*.mixins.json" -path "*/resources/*" 2>/dev/null | sort -u); do
    config_name=$(basename "$config_file")
    refmap_val=$(grep '"refmap"' "$config_file" | sed 's/.*"refmap" *: *"\([^"]*\)".*/\1/' || true)

    if [ -n "$refmap_val" ]; then
        if echo "$REFMAP_FILES" | grep -q "$refmap_val"; then
            pass "$config_name declares refmap '$refmap_val' and it EXISTS in JAR"
        else
            warn "$config_name declares refmap '$refmap_val' but it is MISSING from JAR"
        fi
    else
        warn "$config_name has no refmap declaration"
    fi
done

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "================================"
echo "Tests run: $TESTS"
if [ $ERRORS -gt 0 ]; then
    echo "FAILED: $ERRORS test(s) failed"
    exit 1
else
    echo "PASSED: All $TESTS tests passed"
    exit 0
fi
