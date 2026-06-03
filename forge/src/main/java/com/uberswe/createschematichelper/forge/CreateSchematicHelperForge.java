package com.uberswe.createschematichelper.forge;

import com.mojang.brigadier.Command;
import com.mojang.logging.LogUtils;
import com.uberswe.createschematichelper.ConfigValues;
import com.uberswe.createschematichelper.SchematicUploadHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import static net.minecraft.commands.Commands.literal;

@Mod("createschematichelper")
public class CreateSchematicHelperForge {
    private static final Logger LOGGER = LogUtils.getLogger();

    public CreateSchematicHelperForge() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);

        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
            event.getDispatcher().register(literal("csh")
                .then(literal("upload").executes(ctx -> {
                    SchematicUploadHandler.confirmPendingUpload();
                    return Command.SINGLE_SUCCESS;
                }))
            )
        );

        ModList.get().getModContainerById("createschematichelper")
                .ifPresent(mc -> ConfigValues.modVersion = mc.getModInfo().getVersion().toString());

        LOGGER.info("CreateSchematicHelper loaded (Forge)");
    }

    private void onConfigLoad(ModConfigEvent.Loading event) { syncConfig(); }
    private void onConfigReload(ModConfigEvent.Reloading event) { syncConfig(); }

    private static void syncConfig() {
        ConfigValues.enabled = ForgeConfig.ENABLED.get();
        ConfigValues.autoUpload = ForgeConfig.AUTO_UPLOAD.get();
        ConfigValues.baseUrl = ForgeConfig.BASE_URL.get();
        ConfigValues.render360 = ForgeConfig.RENDER_360.get();
        ConfigValues.frameCount = ForgeConfig.FRAME_COUNT.get();
        ConfigValues.aspectRatio = ForgeConfig.ASPECT_RATIO.get();
        ConfigValues.overrideWidth = ForgeConfig.OVERRIDE_WIDTH.get();
        ConfigValues.overrideHeight = ForgeConfig.OVERRIDE_HEIGHT.get();
        ConfigValues.saveFeaturedFrames = ForgeConfig.SAVE_FEATURED_FRAMES.get();
        ConfigValues.saveAllFrames = ForgeConfig.SAVE_ALL_FRAMES.get();
        ConfigValues.imageFormat = ForgeConfig.IMAGE_FORMAT.get();
        ConfigValues.promptBeforeUpload = ForgeConfig.PROMPT_BEFORE_UPLOAD.get();
    }
}
