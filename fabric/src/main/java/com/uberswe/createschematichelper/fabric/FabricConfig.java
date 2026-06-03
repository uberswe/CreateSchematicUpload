package com.uberswe.createschematichelper.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.uberswe.createschematichelper.ConfigValues;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FabricConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "createschematichelper.json");

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                JsonObject obj = GSON.fromJson(json, JsonObject.class);

                if (obj.has("enabled")) ConfigValues.enabled = obj.get("enabled").getAsBoolean();
                if (obj.has("autoUpload")) ConfigValues.autoUpload = obj.get("autoUpload").getAsBoolean();
                if (obj.has("baseUrl")) ConfigValues.baseUrl = obj.get("baseUrl").getAsString();
                if (obj.has("render360")) ConfigValues.render360 = obj.get("render360").getAsBoolean();
                if (obj.has("frameCount")) ConfigValues.frameCount = obj.get("frameCount").getAsInt();
                if (obj.has("aspectRatio")) ConfigValues.aspectRatio = obj.get("aspectRatio").getAsString();
                if (obj.has("overrideWidth")) ConfigValues.overrideWidth = obj.get("overrideWidth").getAsInt();
                if (obj.has("overrideHeight")) ConfigValues.overrideHeight = obj.get("overrideHeight").getAsInt();
                if (obj.has("saveFeaturedFrames")) ConfigValues.saveFeaturedFrames = obj.get("saveFeaturedFrames").getAsBoolean();
                if (obj.has("saveAllFrames")) ConfigValues.saveAllFrames = obj.get("saveAllFrames").getAsBoolean();
                if (obj.has("imageFormat")) ConfigValues.imageFormat = obj.get("imageFormat").getAsString();
                if (obj.has("promptBeforeUpload")) ConfigValues.promptBeforeUpload = obj.get("promptBeforeUpload").getAsBoolean();
            } catch (Exception e) {
                LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", ConfigValues.enabled);
            obj.addProperty("autoUpload", ConfigValues.autoUpload);
            obj.addProperty("baseUrl", ConfigValues.baseUrl);
            obj.addProperty("render360", ConfigValues.render360);
            obj.addProperty("frameCount", ConfigValues.frameCount);
            obj.addProperty("aspectRatio", ConfigValues.aspectRatio);
            obj.addProperty("overrideWidth", ConfigValues.overrideWidth);
            obj.addProperty("overrideHeight", ConfigValues.overrideHeight);
            obj.addProperty("saveFeaturedFrames", ConfigValues.saveFeaturedFrames);
            obj.addProperty("saveAllFrames", ConfigValues.saveAllFrames);
            obj.addProperty("imageFormat", ConfigValues.imageFormat);
            obj.addProperty("promptBeforeUpload", ConfigValues.promptBeforeUpload);
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
