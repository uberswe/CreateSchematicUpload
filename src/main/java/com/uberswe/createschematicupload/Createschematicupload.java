package com.uberswe.createschematicupload;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(Createschematicupload.MODID)
public class Createschematicupload {
    public static final String MODID = "createschematicupload";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Createschematicupload(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        LOGGER.info("CreateSchematicUpload loaded");
    }
}
