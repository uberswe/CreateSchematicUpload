package com.uberswe.createschematicupload.forge;

import net.minecraftforge.common.ForgeConfigSpec;

public class ForgeConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable the schematic upload feature")
            .define("enabled", true);

    public static final ForgeConfigSpec.BooleanValue AUTO_UPLOAD = BUILDER
            .comment("Automatically upload schematics when saved (if false, a confirmation screen is shown)")
            .define("autoUpload", true);

    public static final ForgeConfigSpec.ConfigValue<String> BASE_URL = BUILDER
            .comment("Base URL for the createmod.com API")
            .define("baseUrl", "https://createmod.com");

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
