package com.uberswe.createschematicupload.forge;

import com.mojang.logging.LogUtils;
import com.uberswe.createschematicupload.ConfigValues;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("createschematicupload")
public class CreateschematicuploadForge {
    private static final Logger LOGGER = LogUtils.getLogger();

    public CreateschematicuploadForge() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);
        LOGGER.info("CreateSchematicUpload loaded (Forge)");
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        syncConfig();
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        syncConfig();
    }

    private static void syncConfig() {
        ConfigValues.enabled = ForgeConfig.ENABLED.get();
        ConfigValues.autoUpload = ForgeConfig.AUTO_UPLOAD.get();
        ConfigValues.baseUrl = ForgeConfig.BASE_URL.get();
    }
}
