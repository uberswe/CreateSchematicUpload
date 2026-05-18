package com.uberswe.createschematichelper.fabric;

import com.mojang.logging.LogUtils;
import com.uberswe.createschematichelper.ConfigValues;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public class CreateSchematicHelperFabric implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        FabricConfig.load();

        FabricLoader.getInstance().getModContainer("createschematichelper")
                .ifPresent(mod -> ConfigValues.modVersion = mod.getMetadata().getVersion().getFriendlyString());

        LOGGER.info("CreateSchematicHelper loaded (Fabric)");
    }
}
