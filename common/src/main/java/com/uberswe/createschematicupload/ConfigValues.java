package com.uberswe.createschematicupload;

/**
 * Loader-agnostic config value holder. Each loader syncs its own config
 * system (NeoForge ModConfigSpec, Fabric JSON, etc.) into these fields.
 */
public class ConfigValues {
    public static boolean enabled = true;
    public static boolean autoUpload = true;
    public static String baseUrl = "https://createmod.com";
}
