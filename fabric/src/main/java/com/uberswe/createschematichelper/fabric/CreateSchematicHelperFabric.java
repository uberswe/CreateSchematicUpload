package com.uberswe.createschematichelper.fabric;

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import com.uberswe.createschematichelper.ConfigValues;
import com.uberswe.createschematichelper.SchematicUploadHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CreateSchematicHelperFabric implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitializeClient() {
        FabricConfig.load();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("csh")
                .then(literal("upload").executes(ctx -> {
                    SchematicUploadHandler.confirmPendingUpload();
                    return Command.SINGLE_SUCCESS;
                }))
            )
        );
        FabricLoader.getInstance().getModContainer("createschematichelper")
                .ifPresent(mod -> ConfigValues.modVersion = mod.getMetadata().getVersion().getFriendlyString());
        LOGGER.info("CreateSchematicHelper loaded (Fabric)");
    }
}
