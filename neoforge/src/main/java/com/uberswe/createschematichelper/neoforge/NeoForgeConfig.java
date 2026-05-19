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

    public static final ModConfigSpec.BooleanValue RENDER_360 = BUILDER
            .comment("Render 360° rotation view (disable for faster uploads with only 4 featured angles)")
            .define("render360", true);

    public static final ModConfigSpec.IntValue FRAME_COUNT = BUILDER
            .comment("Number of frames in the 360° rotation view (default 120, min 4)")
            .defineInRange("frameCount", 120, 4, 720);

    public static final ModConfigSpec.ConfigValue<String> ASPECT_RATIO = BUILDER
            .comment("Aspect ratio for rendered images (e.g. '16:9', '4:3', '1:1')")
            .define("aspectRatio", "16:9");

    public static final ModConfigSpec.IntValue OVERRIDE_WIDTH = BUILDER
            .comment("Override image width in pixels (0 = automatic based on schematic size and aspect ratio)")
            .defineInRange("overrideWidth", 0, 0, 4096);

    public static final ModConfigSpec.IntValue OVERRIDE_HEIGHT = BUILDER
            .comment("Override image height in pixels (0 = automatic based on schematic size)")
            .defineInRange("overrideHeight", 0, 0, 4096);

    public static final ModConfigSpec.BooleanValue SAVE_FEATURED_FRAMES = BUILDER
            .comment("Save the 4 featured perspective images locally to the schematics folder")
            .define("saveFeaturedFrames", false);

    public static final ModConfigSpec.BooleanValue SAVE_ALL_FRAMES = BUILDER
            .comment("Save all rotation frames locally to the schematics folder")
            .define("saveAllFrames", false);

    public static final ModConfigSpec.ConfigValue<String> IMAGE_FORMAT = BUILDER
            .comment("Image format for rendered frames: 'jpeg' (smaller files, recommended) or 'png' (lossless)")
            .define("imageFormat", "jpeg", o -> o instanceof String s && (s.equals("jpeg") || s.equals("png")));

    static final ModConfigSpec SPEC = BUILDER.build();
}
