#!/usr/bin/env bash
# ============================================================================
# Mixin Injection Target Verification Test
# ============================================================================
# Verifies that our mixin's @Redirect actually matches a real method call in
# Create's bytecode. This catches:
#   - Using annotations not available at runtime (e.g. @WrapOperation without
#     MixinExtras — the exact bug this test was written for)
#   - Target descriptor mismatches after SRG/intermediary remapping
#   - Missing or renamed methods in the target class
#   - Create API changes that break the injection point
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
echo "=== Mixin Target Test for MC ${MC_VERSION}, Create ${CREATE_VERSION} ==="
echo ""

# ---------------------------------------------------------------------------
# 2. Locate the Create dependency JAR in the Gradle cache
# ---------------------------------------------------------------------------
# Try the Modrinth format first (mc/1.18.2, mc/1.19.2, mc/1.20.1)
CREATE_JAR=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" \
    -path "*/maven.modrinth/create/*" \
    -name "*.jar" \
    ! -name "*-sources*" \
    ! -name "*-javadoc*" \
    2>/dev/null | grep "${CREATE_VERSION}" | head -1)

# If not found, try the Create Maven format (mc/1.21.1)
if [ -z "$CREATE_JAR" ]; then
    CREATE_JAR=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" \
        -path "*/com.simibubi.create/*" \
        -name "*${CREATE_VERSION}*slim*.jar" \
        2>/dev/null | head -1)
fi

# Fallback: try any Create JAR matching the version
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
# 3. Find our built mod JAR (pick the first loader available)
# ---------------------------------------------------------------------------
OUR_JAR=""
for loader in forge neoforge fabric; do
    if [ -d "${loader}/build/libs" ]; then
        candidate=$(find "$(pwd)/${loader}/build/libs" -maxdepth 1 -name '*.jar' ! -name '*-sources*' 2>/dev/null | head -1)
        if [ -n "$candidate" ]; then
            OUR_JAR="$candidate"
            echo "Mod JAR:    $(basename "$OUR_JAR") ($loader)"
            break
        fi
    fi
done

if [ -z "$OUR_JAR" ]; then
    echo "SKIP: No built mod JAR found. Run ./gradlew build first."
    exit 0
fi
echo ""

# ---------------------------------------------------------------------------
# 4. Extract class files to a temp dir
# ---------------------------------------------------------------------------
TMPDIR=$(mktemp -d)
trap 'rm -rf "$TMPDIR"' EXIT

(cd "$TMPDIR" && jar xf "$CREATE_JAR" \
    com/simibubi/create/content/schematics/client/SchematicAndQuillHandler.class \
    com/simibubi/create/content/schematics/SchematicExport.class \
    2>/dev/null) || true

(cd "$TMPDIR" && jar xf "$OUR_JAR" \
    com/uberswe/createschematicupload/mixin/SchematicAndQuillHandlerMixin.class \
    2>/dev/null) || true

CREATE_CLASS="$TMPDIR/com/simibubi/create/content/schematics/client/SchematicAndQuillHandler.class"
MIXIN_CLASS="$TMPDIR/com/uberswe/createschematicupload/mixin/SchematicAndQuillHandlerMixin.class"

if [ ! -f "$CREATE_CLASS" ]; then
    fail "Could not extract SchematicAndQuillHandler.class from Create JAR"
    echo ""; echo "FAILED"; exit 1
fi

if [ ! -f "$MIXIN_CLASS" ]; then
    fail "Could not extract SchematicAndQuillHandlerMixin.class from mod JAR"
    echo ""; echo "FAILED"; exit 1
fi

# ---------------------------------------------------------------------------
# 5. Decompile both classes with javap
# ---------------------------------------------------------------------------
MIXIN_VERBOSE=$(javap -v -p "$MIXIN_CLASS" 2>&1)
CREATE_DISASM=$(javap -c -p "$CREATE_CLASS" 2>&1)

# ---------------------------------------------------------------------------
# TEST A: Mixin must NOT use @WrapOperation (requires MixinExtras at runtime)
# ---------------------------------------------------------------------------
echo "--- Test A: No @WrapOperation (MixinExtras not available on all loaders) ---"
if echo "$MIXIN_VERBOSE" | grep -qi "WrapOperation"; then
    fail "Mixin references @WrapOperation — will silently fail on Forge (Mixin 0.8.5 has no MixinExtras)"
else
    pass "No @WrapOperation reference found"
fi

# ---------------------------------------------------------------------------
# TEST B: Mixin must use @Redirect (core Mixin, works everywhere)
# ---------------------------------------------------------------------------
echo "--- Test B: Uses @Redirect annotation (core Mixin) ---"
if echo "$MIXIN_VERBOSE" | grep -q "org/spongepowered/asm/mixin/injection/Redirect"; then
    pass "@Redirect annotation present in bytecode"
else
    fail "No @Redirect annotation found — mixin has no injection"
fi

# ---------------------------------------------------------------------------
# TEST C: @Redirect target must reference SchematicExport.saveSchematic
# ---------------------------------------------------------------------------
echo "--- Test C: @Redirect targets SchematicExport.saveSchematic ---"
if echo "$MIXIN_VERBOSE" | grep -q "SchematicExport;saveSchematic"; then
    pass "@Redirect target references SchematicExport.saveSchematic"
else
    fail "@Redirect target does not reference SchematicExport.saveSchematic"
fi

# ---------------------------------------------------------------------------
# TEST D: Create's SchematicAndQuillHandler must have saveSchematic method
# ---------------------------------------------------------------------------
echo "--- Test D: Target class has saveSchematic method ---"
if echo "$CREATE_DISASM" | grep -q "saveSchematic"; then
    pass "saveSchematic method exists in SchematicAndQuillHandler"
else
    fail "saveSchematic method NOT found in SchematicAndQuillHandler"
fi

# ---------------------------------------------------------------------------
# TEST E: saveSchematic must contain an invokestatic to SchematicExport.saveSchematic
#         (this is the actual call site our @Redirect intercepts)
# ---------------------------------------------------------------------------
echo "--- Test E: Target method calls SchematicExport.saveSchematic ---"
INVOKE_LINE=$(echo "$CREATE_DISASM" | grep "Method com/simibubi/create/content/schematics/SchematicExport.saveSchematic" | head -1)
if [ -n "$INVOKE_LINE" ]; then
    pass "invokestatic to SchematicExport.saveSchematic found"
    echo "        $INVOKE_LINE"
else
    fail "No call to SchematicExport.saveSchematic found in saveSchematic method"
fi

# ---------------------------------------------------------------------------
# TEST F: Verify the method descriptor contains the expected parameter types
#         Level and BlockPos must appear (they may be Mojang-mapped or SRG-mapped,
#         but SRG keeps class names identical — only method/field names change)
# ---------------------------------------------------------------------------
echo "--- Test F: Method descriptor has expected parameter types ---"
if [ -n "$INVOKE_LINE" ]; then
    DESCRIPTOR_OK=true

    if echo "$INVOKE_LINE" | grep -q "Level"; then
        pass "Descriptor contains Level parameter"
    else
        fail "Descriptor missing Level parameter"
        DESCRIPTOR_OK=false
    fi

    if echo "$INVOKE_LINE" | grep -q "BlockPos"; then
        pass "Descriptor contains BlockPos parameter"
    else
        fail "Descriptor missing BlockPos parameter"
        DESCRIPTOR_OK=false
    fi

    if echo "$INVOKE_LINE" | grep -q "SchematicExportResult"; then
        pass "Descriptor returns SchematicExportResult"
    else
        fail "Descriptor does not return SchematicExportResult"
        DESCRIPTOR_OK=false
    fi

    if echo "$INVOKE_LINE" | grep -q "Path"; then
        pass "Descriptor contains Path parameter"
    else
        fail "Descriptor missing Path parameter"
        DESCRIPTOR_OK=false
    fi
else
    fail "Cannot verify descriptor — invokestatic not found (see Test E)"
fi

# ---------------------------------------------------------------------------
# TEST G: Cross-reference — extract the full target descriptor from our mixin
#         annotation and verify it matches what's in Create's bytecode
# ---------------------------------------------------------------------------
echo "--- Test G: Cross-reference mixin target with Create bytecode ---"
# Our mixin target (annotation constant pool) uses JVM descriptor format:
#   Lcom/.../SchematicExport;saveSchematic(Ljava/nio/file/Path;...Z...)Lcom/.../SchematicExportResult;
# javap -c output uses a slightly different format:
#   com/.../SchematicExport.saveSchematic:(Ljava/nio/file/Path;...Z...)Lcom/.../SchematicExportResult;
#
# Strategy: extract just the parameter+return descriptor "(...)R" from both and compare.
# The descriptor portion is identical in both formats.

# From mixin: grab the full target string, extract descriptor starting at "("
OUR_TARGET=$(echo "$MIXIN_VERBOSE" | grep "SchematicExport;saveSchematic(" | head -1 | tr -d ' ')
OUR_DESC=$(echo "$OUR_TARGET" | sed 's/.*saveSchematic//' | tr -d '"')

# From Create: grab descriptor from ":" onward (javap format is method:descriptor)
CREATE_DESC=""
if [ -n "$INVOKE_LINE" ]; then
    CREATE_DESC=$(echo "$INVOKE_LINE" | sed 's/.*saveSchematic://' | tr -d ' ')
fi

if [ -n "$OUR_DESC" ] && [ -n "$CREATE_DESC" ]; then
    if [ "$OUR_DESC" = "$CREATE_DESC" ]; then
        pass "Method descriptor MATCHES Create's bytecode exactly"
        echo "        Descriptor: $OUR_DESC"
    else
        fail "Method descriptor MISMATCH"
        echo "        Mixin:  $OUR_DESC"
        echo "        Create: $CREATE_DESC"
    fi
else
    fail "Could not extract descriptors for comparison"
    echo "        Mixin:  '${OUR_DESC:-<empty>}'"
    echo "        Create: '${CREATE_DESC:-<empty>}'"
fi

# ---------------------------------------------------------------------------
# TEST H: Our mixin method must call SchematicUploadHandler.onSchematicSaved
#         (verify the hook is actually wired up)
# ---------------------------------------------------------------------------
echo "--- Test H: Mixin method calls SchematicUploadHandler.onSchematicSaved ---"
MIXIN_DISASM=$(javap -c -p "$MIXIN_CLASS" 2>&1)
if echo "$MIXIN_DISASM" | grep -q "SchematicUploadHandler.onSchematicSaved"; then
    pass "onSchematicSaved call present in mixin method"
else
    fail "onSchematicSaved NOT called — upload hook is not wired"
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
