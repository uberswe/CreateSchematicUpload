package com.uberswe.createschematichelper.fabric;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import com.uberswe.createschematichelper.ConfigValues;

public class CreateSchematicHelperFabric implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        FabricConfig.load();

        FabricLoader.getInstance().getModContainer("createschematichelper")
                .ifPresent(mc -> ConfigValues.modVersion = mc.getMetadata().getVersion().getFriendlyString());

        LOGGER.info("CreateSchematicHelper loaded (Fabric)");
    }
}
