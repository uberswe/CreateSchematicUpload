package com.uberswe.createschematichelper.neoforge.compat;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.uberswe.createschematichelper.ConfigValues;
import com.uberswe.createschematichelper.SchematicUploadHandler;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.swzo.create_blueprinted.api.ShareProvider;
import net.swzo.create_blueprinted.api.ShareProviderRegistry;
import net.swzo.create_blueprinted.gui.ExportButton;
import net.swzo.create_blueprinted.gui.ShareButton;
import net.swzo.create_blueprinted.gui.SmallIconButton;
import net.swzo.create_blueprinted.handler.SchematicImageHandler;
import net.swzo.create_blueprinted.render.SchematicRenderSettings;
import org.slf4j.Logger;

import java.net.URL;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Integration with Create: Blueprinted's ShareProvider API (2.2.2+). This class references
 * Blueprinted types directly and must only be classloaded when create_blueprinted is present.
 *
 * Blueprinted owns the render: its share pipeline produces the screenshot and hands the
 * image bytes to {@link CreateModComShareProvider#onRender}, which uploads the schematic
 * file together with that image and completes with the resulting createmod.com URL.
 * Blueprinted then shows the clickable link in chat itself.
 */
public final class BlueprintedCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final CreateModComShareProvider PROVIDER = new CreateModComShareProvider();

    private BlueprintedCompat() {}

    public static void register() {
        ShareProviderRegistry.register(PROVIDER);
        SchematicUploadHandler.setBlueprintedShareDelegate(BlueprintedCompat::share);
        LOGGER.info("Registered CreateMod.com share provider with Create: Blueprinted");
    }

    /**
     * Routes the save-prompt upload through Blueprinted's render pipeline with our
     * provider pinned, regardless of which provider the player has set active for
     * the schematic table's share button.
     */
    private static void share(String schematicFileName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            throw new IllegalStateException("Cannot share a schematic without a player");
        }
        new SchematicImageHandler(PROVIDER.id(), schematicFileName,
                mc.player.createCommandSourceStack(),
                SchematicRenderSettings.builder(), PROVIDER).share();
    }

    /**
     * A 15px button matching Blueprinted's export/share/refresh column, so our mode
     * toggle can sit directly below them with the same look.
     */
    public static IconButton createSmallButton(int x, int y, ScreenElement icon) {
        return new SmallIconButton(x, y, icon);
    }

    /**
     * Toggles Blueprinted's export and share buttons alongside our local/download mode
     * switch. Their replacement refresh button is not handled here — it is assigned to
     * Create's refreshButton field, which the mixin already toggles.
     */
    public static void setShareButtonsVisible(Iterable<?> widgets, boolean visible) {
        for (Object widget : widgets) {
            if (widget instanceof ExportButton button) {
                button.visible = button.active = visible;
            } else if (widget instanceof ShareButton button) {
                button.visible = button.active = visible;
            }
        }
    }

    private static final class CreateModComShareProvider implements ShareProvider {
        @Override
        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath("createschematichelper", "createmod_com");
        }

        @Override
        public Component destinationName() {
            return Component.literal("CreateMod.com");
        }

        @Override
        public String destinationUrl() {
            String url = ConfigValues.baseUrl;
            return url != null && url.startsWith("https://") && url.length() < MAX_URL_CHAR_LENGTH
                    ? url : "https://createmod.com";
        }

        @Override
        public Future<URL> onRender(ResourceLocation handlerId, String schematicName,
                                    SchematicRenderSettings renderSettings, byte[] imageByteArray) {
            return SchematicUploadHandler.uploadWithImage(schematicName, imageByteArray);
        }

        @Override
        public List<Component> extras() {
            return List.of(
                    Component.translatable("text.createschematichelper.share_note1")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.translatable("text.createschematichelper.share_note2")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.translatable("text.createschematichelper.share_note3")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }

        @Override
        public boolean includeSchematicData() {
            return true;
        }

        @Override
        public int timeout() {
            // Blueprinted blocks a background thread on the upload future for this long;
            // large schematics on slow connections need more than the 30s default
            return 300;
        }
    }
}
