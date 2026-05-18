#!/usr/bin/env bash
# Verify built mod JARs for correctness.
# Checks: mixin JSON present, refmap present and non-empty (Fabric),
# mods.toml/fabric.mod.json present and valid, mod class files present.
set -uo pipefail

ERRORS=0

jar_contains() {
    local jar="$1" pattern="$2"
    jar tf "$jar" 2>/dev/null | grep -q "$pattern"
}

jar_extract() {
    local jar="$1" entry="$2"
    unzip -p "$jar" "$entry" 2>/dev/null
}

check() {
    local jar="$1" desc="$2"
    shift 2

    if [ ! -f "$jar" ]; then
        echo "SKIP: $desc — JAR not found at $jar"
        return
    fi

    echo "=== Verifying $desc: $(basename "$jar") ==="

    # Check that mod class files exist
    if jar_contains "$jar" "com/uberswe/createschematichelper/"; then
        echo "  OK: Mod classes present"
    else
        echo "  FAIL: No createschematichelper classes found"
        ERRORS=$((ERRORS + 1))
    fi

    # Check mixin config
    if jar_contains "$jar" "createschematichelper.mixins.json"; then
        echo "  OK: Mixin config present"
        # Verify mixin config references our mixin class
        local mixin_json
        mixin_json=$(jar_extract "$jar" createschematichelper.mixins.json)
        if echo "$mixin_json" | grep -q "SchematicAndQuillHandlerMixin"; then
            echo "  OK: Mixin class referenced in config"
        else
            echo "  FAIL: SchematicAndQuillHandlerMixin not in mixin config"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo "  FAIL: Mixin config not found"
        ERRORS=$((ERRORS + 1))
    fi

    # Check mixin class file
    if jar_contains "$jar" "SchematicAndQuillHandlerMixin.class"; then
        echo "  OK: Mixin class file present"
    else
        echo "  FAIL: Mixin class file not found"
        ERRORS=$((ERRORS + 1))
    fi

    # Loader-specific checks
    for check_fn in "$@"; do
        $check_fn "$jar"
    done

    echo ""
}

check_forge() {
    local jar="$1"
    if ! jar_contains "$jar" "META-INF/mods.toml"; then
        echo "  FAIL: META-INF/mods.toml not found"
        ERRORS=$((ERRORS + 1))
        return
    fi
    echo "  OK: mods.toml present"

    local mods_toml
    mods_toml=$(jar_extract "$jar" META-INF/mods.toml)

    if echo "$mods_toml" | grep -q 'modId.*=.*"createschematichelper"'; then
        echo "  OK: Correct mod ID in mods.toml"
    else
        echo "  FAIL: Incorrect or missing mod ID in mods.toml"
        ERRORS=$((ERRORS + 1))
    fi

    # Forge uses mandatory, not type
    if echo "$mods_toml" | grep -q 'type.*=.*"required"'; then
        echo "  FAIL: mods.toml uses type=\"required\" (NeoForge syntax) instead of mandatory=true (Forge syntax)"
        ERRORS=$((ERRORS + 1))
    elif echo "$mods_toml" | grep -q 'mandatory.*=.*true'; then
        echo "  OK: mods.toml uses correct mandatory field"
    else
        echo "  WARN: Could not verify dependency format"
    fi

    if echo "$mods_toml" | grep -q 'createschematichelper.mixins.json'; then
        echo "  OK: Mixin config declared in mods.toml"
    else
        echo "  FAIL: Mixin config not declared in mods.toml"
        ERRORS=$((ERRORS + 1))
    fi

    # Check MANIFEST.MF for MixinConfigs attribute (required for Forge to register mixin configs)
    local manifest
    manifest=$(jar_extract "$jar" META-INF/MANIFEST.MF)
    if echo "$manifest" | grep -q 'MixinConfigs:.*createschematichelper.mixins.json'; then
        echo "  OK: MixinConfigs manifest attribute present"
    else
        echo "  FAIL: MixinConfigs manifest attribute missing — Forge will not load the mixin config"
        ERRORS=$((ERRORS + 1))
    fi
}

check_neoforge() {
    local jar="$1"
    if ! jar_contains "$jar" "META-INF/neoforge.mods.toml"; then
        echo "  FAIL: META-INF/neoforge.mods.toml not found"
        ERRORS=$((ERRORS + 1))
        return
    fi
    echo "  OK: neoforge.mods.toml present"

    local mods_toml
    mods_toml=$(jar_extract "$jar" META-INF/neoforge.mods.toml)

    if echo "$mods_toml" | grep -q 'modId.*=.*"createschematichelper"'; then
        echo "  OK: Correct mod ID in neoforge.mods.toml"
    else
        echo "  FAIL: Incorrect or missing mod ID in neoforge.mods.toml"
        ERRORS=$((ERRORS + 1))
    fi

    if echo "$mods_toml" | grep -q 'createschematichelper.mixins.json'; then
        echo "  OK: Mixin config declared in neoforge.mods.toml"
    else
        echo "  FAIL: Mixin config not declared in neoforge.mods.toml"
        ERRORS=$((ERRORS + 1))
    fi
}

check_fabric() {
    local jar="$1"
    if ! jar_contains "$jar" "fabric.mod.json"; then
        echo "  FAIL: fabric.mod.json not found"
        ERRORS=$((ERRORS + 1))
        return
    fi
    echo "  OK: fabric.mod.json present"

    local fabric_json
    fabric_json=$(jar_extract "$jar" fabric.mod.json)

    if echo "$fabric_json" | grep -q '"createschematichelper"'; then
        echo "  OK: Correct mod ID in fabric.mod.json"
    else
        echo "  FAIL: Incorrect or missing mod ID in fabric.mod.json"
        ERRORS=$((ERRORS + 1))
    fi

    # Check refmap
    if jar_contains "$jar" "createschematichelper.refmap.json"; then
        echo "  OK: Refmap present"
        local refmap
        refmap=$(jar_extract "$jar" createschematichelper.refmap.json)
        # Check that refmap has intermediary mappings (class_1937 = Level, class_2338 = BlockPos)
        if echo "$refmap" | grep -q "class_1937"; then
            echo "  OK: Refmap contains intermediary mapping for Level (class_1937)"
        else
            echo "  FAIL: Refmap missing intermediary mapping for Level — mixin will fail at runtime"
            ERRORS=$((ERRORS + 1))
        fi
        if echo "$refmap" | grep -q "class_2338"; then
            echo "  OK: Refmap contains intermediary mapping for BlockPos (class_2338)"
        else
            echo "  FAIL: Refmap missing intermediary mapping for BlockPos — mixin will fail at runtime"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo "  FAIL: Refmap not found in Fabric JAR"
        ERRORS=$((ERRORS + 1))
    fi
}

# Determine which loaders are enabled in settings.gradle
has_loader() {
    grep -q "include '$1'" settings.gradle 2>/dev/null
}

find_jar() {
    local dir="$1"
    if [ -d "$dir" ]; then
        find "$dir" -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' | head -1
    fi
}

FOUND=0

if has_loader forge; then
    FORGE_JAR=$(find_jar forge/build/libs)
    if [ -n "$FORGE_JAR" ]; then
        check "$FORGE_JAR" "Forge" check_forge
        FOUND=1
    else
        echo "FAIL: Forge subproject exists but no JAR found"
        ERRORS=$((ERRORS + 1))
    fi
fi

if has_loader neoforge; then
    NEOFORGE_JAR=$(find_jar neoforge/build/libs)
    if [ -n "$NEOFORGE_JAR" ]; then
        check "$NEOFORGE_JAR" "NeoForge" check_neoforge
        FOUND=1
    else
        echo "FAIL: NeoForge subproject exists but no JAR found"
        ERRORS=$((ERRORS + 1))
    fi
fi

if has_loader fabric; then
    FABRIC_JAR=$(find_jar fabric/build/libs)
    if [ -n "$FABRIC_JAR" ]; then
        check "$FABRIC_JAR" "Fabric" check_fabric
        FOUND=1
    else
        echo "FAIL: Fabric subproject exists but no JAR found"
        ERRORS=$((ERRORS + 1))
    fi
fi

if [ $FOUND -eq 0 ]; then
    echo "ERROR: No built JARs found. Run ./gradlew build first."
    exit 1
fi

echo "================================"
if [ $ERRORS -gt 0 ]; then
    echo "FAILED: $ERRORS error(s) found"
    exit 1
else
    echo "PASSED: All checks passed"
    exit 0
fi
