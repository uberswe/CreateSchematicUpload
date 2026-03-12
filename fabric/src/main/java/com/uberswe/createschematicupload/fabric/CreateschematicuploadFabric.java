package com.uberswe.createschematicupload.fabric;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;

public class CreateschematicuploadFabric implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        FabricConfig.load();
        LOGGER.info("CreateSchematicUpload loaded (Fabric)");
    }
}
