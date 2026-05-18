package com.uberswe.createschematichelper;

/**
 * Loader-agnostic config value holder. Each loader syncs its own config
 * system (Forge ForgeConfigSpec, Fabric JSON, etc.) into these fields.
 */
public class ConfigValues {
    public static boolean enabled = true;
    public static boolean autoUpload = true;
    public static String baseUrl = "https://createmod.com";
    public static String modVersion = "1.0.0";
    public static boolean render360 = true;
    public static boolean saveFeaturedFrames = false;
    public static boolean saveAllFrames = false;
    public static String imageFormat = "jpeg";
}
