package com.uberswe.createschematichelper.forge;

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

    public static final ForgeConfigSpec.BooleanValue RENDER_360 = BUILDER
            .comment("Render 120-frame 360 degree rotation view (disable for faster uploads with only 4 featured angles)")
            .define("render360", true);

    public static final ForgeConfigSpec.IntValue FRAME_COUNT = BUILDER
            .comment("Number of frames in the 360° rotation view (default 120, min 4)")
            .defineInRange("frameCount", 120, 4, 720);

    public static final ForgeConfigSpec.ConfigValue<String> ASPECT_RATIO = BUILDER
            .comment("Aspect ratio for rendered images (e.g. '16:9', '4:3', '1:1')")
            .define("aspectRatio", "16:9");

    public static final ForgeConfigSpec.IntValue OVERRIDE_WIDTH = BUILDER
            .comment("Override image width in pixels (0 = automatic based on schematic size and aspect ratio)")
            .defineInRange("overrideWidth", 0, 0, 4096);

    public static final ForgeConfigSpec.IntValue OVERRIDE_HEIGHT = BUILDER
            .comment("Override image height in pixels (0 = automatic based on schematic size)")
            .defineInRange("overrideHeight", 0, 0, 4096);

    public static final ForgeConfigSpec.BooleanValue SAVE_FEATURED_FRAMES = BUILDER
            .comment("Save the 4 featured perspective images locally to the schematics folder")
            .define("saveFeaturedFrames", false);

    public static final ForgeConfigSpec.BooleanValue SAVE_ALL_FRAMES = BUILDER
            .comment("Save all 120 rotation frames locally to the schematics folder")
            .define("saveAllFrames", false);

    public static final ForgeConfigSpec.ConfigValue<String> IMAGE_FORMAT = BUILDER
            .comment("Image format for rendered frames: 'jpeg' (smaller files, recommended) or 'png' (lossless)")
            .define("imageFormat", "jpeg");

    static final ForgeConfigSpec SPEC = BUILDER.build();
}
