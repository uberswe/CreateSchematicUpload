package com.uberswe.createschematichelper.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

public class NeoForgeConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable the schematic upload feature")
            .define("enabled", true);

    public static final ModConfigSpec.BooleanValue AUTO_UPLOAD = BUILDER
            .comment("Automatically upload schematics when saved (if false, a confirmation screen is shown)")
            .define("autoUpload", true);

    public static final ModConfigSpec.ConfigValue<String> BASE_URL = BUILDER
            .comment("Base URL for the createmod.com API")
            .define("baseUrl", "https://createmod.com");

    public static final ModConfigSpec.BooleanValue SAVE_FEATURED_FRAMES = BUILDER
            .comment("Save the 4 featured perspective images locally to the schematics folder")
            .define("saveFeaturedFrames", false);

    public static final ModConfigSpec.BooleanValue SAVE_ALL_FRAMES = BUILDER
            .comment("Save all 120 rotation frames locally to the schematics folder")
            .define("saveAllFrames", false);

    public static final ModConfigSpec.ConfigValue<String> IMAGE_FORMAT = BUILDER
            .comment("Image format for rendered frames: 'jpeg' (smaller files, recommended) or 'png' (lossless)")
            .define("imageFormat", "jpeg", o -> o instanceof String s && (s.equals("jpeg") || s.equals("png")));

    static final ModConfigSpec SPEC = BUILDER.build();
}
