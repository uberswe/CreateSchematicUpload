package com.uberswe.createschematichelper.neoforge;

import com.mojang.logging.LogUtils;
import com.uberswe.createschematichelper.ConfigValues;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

@Mod("createschematichelper")
public class CreateSchematicHelperNeoForge {
    private static final Logger LOGGER = LogUtils.getLogger();

    public CreateSchematicHelperNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, NeoForgeConfig.SPEC);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        ModList.get().getModContainerById("createschematichelper")
                .ifPresent(mc -> ConfigValues.modVersion = mc.getModInfo().getVersion().toString());

        LOGGER.info("CreateSchematicHelper loaded (NeoForge)");
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
        ConfigValues.render360 = NeoForgeConfig.RENDER_360.get();
        ConfigValues.frameCount = NeoForgeConfig.FRAME_COUNT.get();
        ConfigValues.aspectRatio = NeoForgeConfig.ASPECT_RATIO.get();
        ConfigValues.overrideWidth = NeoForgeConfig.OVERRIDE_WIDTH.get();
        ConfigValues.overrideHeight = NeoForgeConfig.OVERRIDE_HEIGHT.get();
        ConfigValues.saveFeaturedFrames = NeoForgeConfig.SAVE_FEATURED_FRAMES.get();
        ConfigValues.saveAllFrames = NeoForgeConfig.SAVE_ALL_FRAMES.get();
        ConfigValues.imageFormat = NeoForgeConfig.IMAGE_FORMAT.get();
        ConfigValues.backgroundImage = NeoForgeConfig.BACKGROUND_IMAGE.get();
    }
}
