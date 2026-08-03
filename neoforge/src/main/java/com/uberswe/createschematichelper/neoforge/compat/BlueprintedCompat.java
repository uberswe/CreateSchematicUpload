package com.uberswe.createschematichelper.neoforge.compat;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.uberswe.createschematichelper.ConfigValues;
import com.uberswe.createschematichelper.SchematicUploadHandler;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.swzo.create_blueprinted.api.ShareProvider;
import net.swzo.create_blueprinted.api.ShareProviderRegistry;
import net.swzo.create_blueprinted.gui.CBGuiTextures;
import net.swzo.create_blueprinted.gui.ExportButton;
import net.swzo.create_blueprinted.gui.ShareButton;
import net.swzo.create_blueprinted.gui.SmallIconButton;
import net.swzo.create_blueprinted.render.SchematicRenderSettings;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URL;

/**
 * Integration with Create: Blueprinted's ShareProvider API. This class references
 * Blueprinted types directly and must only be classloaded when create_blueprinted is present.
 */
public final class BlueprintedCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BlueprintedCompat() {}

    public static void register() {
        ShareProviderRegistry.register(new CreateModComShareProvider());
        LOGGER.info("Registered CreateMod.com share provider with Create: Blueprinted");
    }

    /**
     * A 15px button matching Blueprinted's export/share/refresh column, so our mode
     * toggle can sit directly below them with the same look.
     */
    public static IconButton createSmallButton(int x, int y, ScreenElement icon) {
        return new SmallIconButton(x, y, icon);
    }

    /**
     * Toggles Blueprinted's export button alongside our local/download mode switch.
     * Their replacement refresh button is not handled here — it is assigned to Create's
     * refreshButton field, which the mixin already toggles. Their share button is
     * removed and replaced with our own (see {@link #findShareButton}).
     */
    public static void setShareButtonsVisible(Iterable<?> widgets, boolean visible) {
        for (Object widget : widgets) {
            if (widget instanceof ExportButton button) {
                button.visible = button.active = visible;
            }
        }
    }

    /** Locates Blueprinted's ShareButton among the screen's widgets, if present. */
    public static @Nullable IconButton findShareButton(Iterable<?> widgets) {
        for (Object widget : widgets) {
            if (widget instanceof ShareButton button) {
                return button;
            }
        }
        return null;
    }

    /**
     * Our replacement for Blueprinted's share button: same icon and slot, but with our
     * own tooltip and a click handler that runs the full 360° upload pipeline directly,
     * skipping Blueprinted's single-image render entirely.
     */
    public static IconButton createShareButton(int x, int y, Runnable onClick) {
        IconButton button = new SmallIconButton(x, y, CBGuiTextures.SHARE_ICON);
        button.withCallback(onClick);
        button.getToolTip().add(Component.translatable("text.createschematichelper.share_title"));
        button.getToolTip().add(Component.translatable("text.createschematichelper.share_upload")
                .withStyle(ChatFormatting.GRAY));
        button.getToolTip().add(Component.translatable("text.createschematichelper.share_note1")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        button.getToolTip().add(Component.translatable("text.createschematichelper.share_note2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        button.getToolTip().add(Component.translatable("text.createschematichelper.share_note3")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return button;
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
        public @Nullable URL onRender(ResourceLocation handlerId, String schematicName,
                                      SchematicRenderSettings renderSettings, byte[] imageByteArray) {
            // Blueprinted's single rendered image is ignored: sharing runs the same full
            // pipeline as the chat upload link (360° rotation render + featured frames).
            return SchematicUploadHandler.shareSchematic(schematicName);
        }
    }
}
