package com.uberswe.createschematichelper.fabric;

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import com.uberswe.createschematichelper.ConfigValues;
import com.uberswe.createschematichelper.SchematicUploadHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public class CreateSchematicHelperFabric implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        FabricConfig.load();

        ClientCommandManager.DISPATCHER.register(
            ClientCommandManager.literal("csh")
                .then(ClientCommandManager.literal("upload").executes(ctx -> {
                    SchematicUploadHandler.confirmPendingUpload();
                    return Command.SINGLE_SUCCESS;
                }))
        );

        FabricLoader.getInstance().getModContainer("createschematichelper")
                .ifPresent(mod -> ConfigValues.modVersion = mod.getMetadata().getVersion().getFriendlyString());

        LOGGER.info("CreateSchematicHelper loaded (Fabric)");
    }
}
