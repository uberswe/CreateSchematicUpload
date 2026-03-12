package com.uberswe.createschematicupload.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.uberswe.createschematicupload.ConfigValues;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FabricConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "createschematicupload.json");

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                JsonObject obj = GSON.fromJson(json, JsonObject.class);

                if (obj.has("enabled")) ConfigValues.enabled = obj.get("enabled").getAsBoolean();
                if (obj.has("autoUpload")) ConfigValues.autoUpload = obj.get("autoUpload").getAsBoolean();
                if (obj.has("baseUrl")) ConfigValues.baseUrl = obj.get("baseUrl").getAsString();
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
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
}
