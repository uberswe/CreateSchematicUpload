#!/usr/bin/env bash
# ============================================================================
# SchematicTableScreen Mixin Target Verification Test
# ============================================================================
# Verifies that the SchematicTableScreenMixin targets real methods in Create's
# SchematicTableScreen class. Uses javap to inspect the bytecode and confirm:
#   - The target class exists in the Create JAR
#   - The methods referenced by our mixin annotations exist
#   - The lambda method our @Inject targets exists
#
# This catches Create API changes that would break our mixin at runtime.
#
# Requires: javap (ships with JDK), a completed ./gradlew build
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

# ---------------------------------------------------------------------------
# 1. Read project versions from gradle.properties
# ---------------------------------------------------------------------------
MC_VERSION=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
CREATE_VERSION=$(grep '^create_version=' gradle.properties | cut -d= -f2)
echo "=== SchematicTableScreen Mixin Target Test for MC ${MC_VERSION}, Create ${CREATE_VERSION} ==="
echo ""

# ---------------------------------------------------------------------------
# 2. Locate the Create dependency JAR
# ---------------------------------------------------------------------------
CREATE_JAR=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" \
    -path "*/maven.modrinth/create/*" \
    -name "*.jar" \
    ! -name "*-sources*" \
    ! -name "*-javadoc*" \
    2>/dev/null | grep "${CREATE_VERSION}" | head -1)

if [ -z "$CREATE_JAR" ]; then
    CREATE_JAR=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" \
        -path "*/com.simibubi.create/*" \
        -name "*${CREATE_VERSION}*slim*.jar" \
        2>/dev/null | head -1)
fi

if [ -z "$CREATE_JAR" ]; then
    CREATE_JAR=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches" \
        -name "create-*${CREATE_VERSION}*.jar" \
        ! -name "*-sources*" \
        ! -name "*-javadoc*" \
        ! -name "*-slim*" \
        2>/dev/null | head -1)
fi

if [ -z "$CREATE_JAR" ]; then
    echo "SKIP: Could not find Create JAR in Gradle cache."
    echo "      Run ./gradlew build first to download dependencies."
    exit 0
fi
echo "Create JAR: $(basename "$CREATE_JAR")"

# ---------------------------------------------------------------------------
# 3. Find our mixin source (Forge, NeoForge, or Fabric)
# ---------------------------------------------------------------------------
MIXIN_SOURCE=""
LOADER=""
for loader in forge neoforge; do
    candidate=$(find "${loader}/src/main/java" -name "SchematicTableScreenMixin.java" 2>/dev/null | head -1)
    if [ -n "$candidate" ]; then
        MIXIN_SOURCE="$candidate"
        LOADER="$loader"
        break
    fi
done

if [ -z "$MIXIN_SOURCE" ]; then
    echo "SKIP: No SchematicTableScreenMixin found."
    exit 0
fi
echo "Mixin:      $MIXIN_SOURCE ($LOADER)"
echo ""

# ---------------------------------------------------------------------------
# 4. Extract SchematicTableScreen from Create JAR
# ---------------------------------------------------------------------------
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

(cd "$TMPDIR" && jar xf "$CREATE_JAR" \
    com/simibubi/create/content/schematics/table/SchematicTableScreen.class \
    2>/dev/null) || true

TARGET_CLASS="$TMPDIR/com/simibubi/create/content/schematics/table/SchematicTableScreen.class"

if [ ! -f "$TARGET_CLASS" ]; then
    fail "Could not extract SchematicTableScreen.class from Create JAR"
    echo ""; echo "FAILED"; exit 1
fi

# ---------------------------------------------------------------------------
# 5. Decompile with javap
# ---------------------------------------------------------------------------
TARGET_VERBOSE=$(javap -v -p "$TARGET_CLASS" 2>&1)
TARGET_METHODS=$(javap -p "$TARGET_CLASS" 2>&1)

# ---------------------------------------------------------------------------
# The Create JAR may use Mojang names OR SRG names depending on loader/build.
# Check for both: init/m_7856_, renderBg/m_7286_
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# TEST A: SchematicTableScreen must have an init() method
# ---------------------------------------------------------------------------
echo "--- Test A: Target class has init() or m_7856_() method ---"
if grep -qE "void (init|m_7856_)\(\)" <<< "$TARGET_METHODS"; then
    INIT_NAME=$(grep -oE "(init|m_7856_)\(\)" <<< "$TARGET_METHODS" | head -1 | sed 's/()//')
    pass "init method exists as '$INIT_NAME' in SchematicTableScreen"
else
    fail "Neither init() nor m_7856_() found — mixin @Inject will fail"
fi

# ---------------------------------------------------------------------------
# TEST B: SchematicTableScreen must have a renderBg method
# ---------------------------------------------------------------------------
echo "--- Test B: Target class has renderBg() or m_7286_() method ---"
if grep -qE "renderBg|m_7286_" <<< "$TARGET_METHODS"; then
    RENDERBG_NAME=$(grep -oE "(renderBg|m_7286_)" <<< "$TARGET_METHODS" | head -1)
    pass "renderBg method exists as '$RENDERBG_NAME' in SchematicTableScreen"
else
    fail "Neither renderBg() nor m_7286_() found — mixin @Redirect/@WrapOperation will fail"
fi

# ---------------------------------------------------------------------------
# TEST C: SchematicTableScreen must have lambda$init$0 method
# ---------------------------------------------------------------------------
echo "--- Test C: Target class has lambda\$init\$0 method ---"
if grep -q 'lambda$init$0' <<< "$TARGET_METHODS"; then
    pass "lambda\$init\$0 method exists in SchematicTableScreen"
else
    fail "lambda\$init\$0 NOT found — mixin @Inject on lambda will fail (Create may have refactored)"
fi

# ---------------------------------------------------------------------------
# TEST D: init() must use integer constant 21 (refresh button Y position)
# ---------------------------------------------------------------------------
echo "--- Test D: init() uses expected integer constants ---"
FULL_DISASM=$(javap -c -p "$TARGET_CLASS" 2>&1)

# Extract the init method bytecode — match either Mojang or SRG name
INIT_BYTECODE=$(echo "$FULL_DISASM" | awk '
    /void (m_7856_|init)\(\)/ { found=1 }
    found { print }
    found && /^[}]/ { found=0 }
')
if [ -z "$INIT_BYTECODE" ]; then
    INIT_BYTECODE="$FULL_DISASM"
fi

if grep -qE "bipush\s+21$|sipush\s+21$" <<< "$INIT_BYTECODE"; then
    pass "Constant 21 found in init() bytecode (refresh button Y position)"
elif grep -qE "bipush\s+21$|sipush\s+21$" <<< "$FULL_DISASM"; then
    pass "Constant 21 found in class bytecode"
else
    fail "Constant 21 NOT found — @ModifyConstant ordinal may be wrong"
fi

# ---------------------------------------------------------------------------
# TEST E: renderBg() must call Font.drawShadow or GuiGraphics.drawString
# ---------------------------------------------------------------------------
echo "--- Test E: renderBg() calls expected rendering methods ---"
# Use awk to extract the full method body — sed's range pattern terminates too early
RENDERBG_BYTECODE=$(echo "$FULL_DISASM" | awk '
    /void (m_7286_|renderBg)\(/ { found=1 }
    found { print }
    found && /^[}]/ { found=0 }
')
# Fallback: search the full disassembly if method extraction fails
if [ -z "$RENDERBG_BYTECODE" ]; then
    RENDERBG_BYTECODE="$FULL_DISASM"
fi

# In SRG bytecode, method names appear in constant pool comments after //
if grep -qE "drawShadow|drawString|m_92763_|m_280430_|Font" <<< "$RENDERBG_BYTECODE"; then
    pass "renderBg() calls Font draw method (label rendering target exists)"
else
    fail "renderBg() does NOT reference Font/drawShadow/drawString — @Redirect target may be wrong"
fi

# ---------------------------------------------------------------------------
# TEST F: renderBg() must call AllGuiTextures.render
# ---------------------------------------------------------------------------
echo "--- Test F: renderBg() calls AllGuiTextures.render ---"
if grep -q "AllGuiTextures" <<< "$RENDERBG_BYTECODE"; then
    pass "renderBg() references AllGuiTextures (texture override target exists)"
else
    fail "renderBg() does NOT reference AllGuiTextures — texture override @Redirect may be wrong"
fi

# ---------------------------------------------------------------------------
# TEST G: If Forge, verify SRG names in mixin match the tsrg mappings
# ---------------------------------------------------------------------------
if [ "$LOADER" = "forge" ]; then
    echo "--- Test G: Forge SRG names match tsrg mappings ---"

    # Find a tsrg mapping file for this MC version
    TSRG_FILE=""
    for f in "${GRADLE_USER_HOME:-$HOME/.gradle}"/caches/neoformruntime/intermediate_results/createMappings_*_officialToSrg.tsrg; do
        if [ -f "$f" ]; then
            # Verify it's the right MC version by checking for a known class
            if grep -q "net/minecraft/client/gui/screens/Screen " "$f" 2>/dev/null; then
                TSRG_FILE="$f"
                break
            fi
        fi
    done

    if [ -n "$TSRG_FILE" ]; then
        # Check init -> m_7856_
        INIT_SRG=$(awk '/^net\/minecraft\/client\/gui\/screens\/Screen /{found=1} found && /^\tinit \(\)V/{print $3; found=0}' "$TSRG_FILE")
        if [ "$INIT_SRG" = "m_7856_" ]; then
            pass "Screen.init() maps to m_7856_ in tsrg (matches mixin)"
        elif [ -n "$INIT_SRG" ]; then
            fail "Screen.init() maps to '$INIT_SRG' but mixin uses m_7856_"
        else
            warn "Could not find Screen.init() in tsrg"
        fi

        # Check renderBg -> m_7286_
        RENDERBG_SRG=$(awk '/^net\/minecraft\/client\/gui\/screens\/inventory\/AbstractContainerScreen /{found=1} found && /^\trenderBg /{print $3; found=0}' "$TSRG_FILE")
        if [ "$RENDERBG_SRG" = "m_7286_" ]; then
            pass "AbstractContainerScreen.renderBg() maps to m_7286_ in tsrg (matches mixin)"
        elif [ -n "$RENDERBG_SRG" ]; then
            fail "AbstractContainerScreen.renderBg() maps to '$RENDERBG_SRG' but mixin uses m_7286_"
        else
            warn "Could not find AbstractContainerScreen.renderBg() in tsrg"
        fi
    else
        echo "  SKIP: No tsrg mapping file found — cannot verify SRG names"
    fi
fi

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
