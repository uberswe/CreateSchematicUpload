package com.uberswe.createschematicupload.neoforge;

import com.mojang.logging.LogUtils;
import com.uberswe.createschematicupload.ConfigValues;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@Mod("createschematicupload")
public class CreateschematicuploadNeoForge {
    private static final Logger LOGGER = LogUtils.getLogger();

    public CreateschematicuploadNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfig.SPEC);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);
        LOGGER.info("CreateSchematicUpload loaded (NeoForge)");
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        syncConfig();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        syncConfig();
    }

    private static void syncConfig() {
        ConfigValues.enabled = NeoForgeConfig.ENABLED.get();
        ConfigValues.autoUpload = NeoForgeConfig.AUTO_UPLOAD.get();
        ConfigValues.baseUrl = NeoForgeConfig.BASE_URL.get();
    }
}
