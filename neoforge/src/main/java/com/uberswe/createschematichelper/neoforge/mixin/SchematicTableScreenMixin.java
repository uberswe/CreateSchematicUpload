package com.uberswe.createschematichelper.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import com.simibubi.create.content.schematics.table.SchematicTableScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.uberswe.createschematichelper.SchematicDownloadHandler;
import com.uberswe.createschematichelper.neoforge.DownloadIcon;
import com.uberswe.createschematichelper.neoforge.ScaledIcon;
import com.uberswe.createschematichelper.neoforge.compat.BlueprintedCompat;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Higher priority than Blueprinted's mixin (default 1000) so our init tail runs after
// theirs and can toggle the export/share buttons it adds.
@Mixin(value = SchematicTableScreen.class, priority = 1500)
public abstract class SchematicTableScreenMixin extends AbstractSimiContainerScreen<SchematicTableMenu> {
    @Unique
    private static final ResourceLocation createschematichelper$TABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("createschematichelper", "textures/gui/schematic_table.png");
    @Unique
    private static final Component createschematichelper$URL_FIELD_HINT = Component.translatable("text.createschematichelper.url_field_hint");
    @Unique
    private static final Component createschematichelper$DOWNLOAD_TITLE = Component.translatable("text.createschematichelper.download_title");
    @Unique
    private static final Component createschematichelper$DOWNLOAD_TOOLTIP = Component.translatable("text.createschematichelper.download_schematic");
    @Unique
    private static final Component createschematichelper$LOCAL_TOOLTIP = Component.translatable("text.createschematichelper.choose_local_schematic");
    // Create: Blueprinted stacks 15px buttons at x+205: export (y+1), share (y+18) and its
    // replacement refresh button (y+35). Our mode toggle continues that column at y+52,
    // built as a matching SmallIconButton via BlueprintedCompat.
    @Unique
    private static final boolean createschematichelper$BLUEPRINTED = ModList.get().isLoaded("create_blueprinted");

    @Shadow
    private float lastChasingProgress;
    @Shadow
    private float chasingProgress;
    @Shadow
    private float progress;
    @Shadow
    private Label schematicsLabel;
    @Shadow
    private ScrollInput schematicsArea;
    @Shadow
    private IconButton folderButton;
    @Shadow
    private IconButton refreshButton;
    @Unique
    private EditBox createschematichelper$urlField;
    @Unique
    private IconButton createschematichelper$modeButton;

    public SchematicTableScreenMixin(SchematicTableMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void createschematichelper$addUrlField(CallbackInfo ci, @Local(name = "x") int x, @Local(name = "y") int y) {
        this.createschematichelper$urlField = new EditBox(this.font, x + 26, y + 26, 154, 18, CommonComponents.EMPTY);
        this.createschematichelper$urlField.setMaxLength(255);
        this.createschematichelper$urlField.setTextColor(-1);
        this.createschematichelper$urlField.setTextColorUneditable(-1);
        this.createschematichelper$urlField.setBordered(false);
        this.createschematichelper$urlField.setHint(createschematichelper$URL_FIELD_HINT);
        this.createschematichelper$urlField.setResponder(s -> {
            if (s.isEmpty()) {
                this.createschematichelper$urlField.setHint(createschematichelper$URL_FIELD_HINT);
            } else {
                this.createschematichelper$urlField.setSuggestion(null);
            }
        });
        this.addRenderableWidget(this.createschematichelper$urlField);

        // Blueprinted anchors its column to topPos; Create's local y is topPos + 2,
        // so the column positions must use topPos or the gaps come out uneven.
        this.createschematichelper$modeButton = createschematichelper$BLUEPRINTED
                ? BlueprintedCompat.createSmallButton(this.leftPos + 205, this.topPos + 52, new ScaledIcon(AllIcons.I_OPEN_FOLDER))
                : new IconButton(x + 208, y + 11, AllIcons.I_OPEN_FOLDER);
        this.createschematichelper$modeButton.withCallback(this::createschematichelper$toggleMode);
        this.addRenderableWidget(this.createschematichelper$modeButton);

        this.createschematichelper$toggleMode();
    }

    @Inject(
            method = "lambda$init$0",
            at = @At("HEAD"),
            cancellable = true
    )
    private void createschematichelper$downloadSchematic(CallbackInfo ci) {
        if (!menu.canWrite() || !this.createschematichelper$urlField.isVisible()) {
            return;
        } else if (this.createschematichelper$urlField.getValue().isBlank()) {
            ci.cancel();
            return;
        }

        this.lastChasingProgress = this.chasingProgress = this.progress = 0;
        String downloadedSchematicName = SchematicDownloadHandler.downloadSchematic(this.createschematichelper$urlField.getValue());
        if (downloadedSchematicName != null) {
            CreateClient.SCHEMATIC_SENDER.startNewUpload(downloadedSchematicName);
            this.createschematichelper$urlField.setValue("");
        } else {
            this.createschematichelper$urlField.setValue("");
            this.createschematichelper$urlField.setHint(Component.empty());
            this.createschematichelper$urlField.setSuggestion("Failed to download schematic");
        }

        ci.cancel();
    }

    @Unique
    private void createschematichelper$toggleMode() {
        boolean localMode = this.createschematichelper$urlField.isVisible();
        this.schematicsLabel.visible = this.schematicsLabel.active = localMode;
        this.folderButton.visible = this.folderButton.active = localMode;
        this.refreshButton.visible = this.refreshButton.active = localMode;
        if (this.schematicsArea != null) {
            this.schematicsArea.visible = this.schematicsArea.active = localMode;
        }
        if (createschematichelper$BLUEPRINTED) {
            BlueprintedCompat.setShareButtonsVisible(this.renderables, localMode);
        }

        this.createschematichelper$urlField.visible = this.createschematichelper$urlField.active = !localMode;

        this.createschematichelper$modeButton.setToolTip(localMode ? createschematichelper$DOWNLOAD_TOOLTIP : createschematichelper$LOCAL_TOOLTIP);
        ScreenElement icon = localMode ? new DownloadIcon() : AllIcons.I_OPEN_FOLDER;
        this.createschematichelper$modeButton.setIcon(createschematichelper$BLUEPRINTED ? new ScaledIcon(icon) : icon);
    }

    @ModifyConstant(
            method = "init",
            constant = @Constant(intValue = 206)
    )
    private int createschematichelper$patchRefreshButtonX(int x) {
        // Blueprinted removes and re-adds the refresh button itself, so leave Create's original alone.
        return createschematichelper$BLUEPRINTED ? x : x + 2;
    }

    @ModifyConstant(
            method = "init",
            constant = @Constant(intValue = 21, ordinal = 2)
    )
    private int createschematichelper$patchRefreshButtonY(int y) {
        return createschematichelper$BLUEPRINTED ? y : 32;
    }

    @WrapOperation(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"
            )
    )
    private int createschematichelper$stopLabelRender(GuiGraphics instance, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        if (!this.createschematichelper$urlField.isVisible()) {
            return original.call(instance, font, text, x, y, color);
        } else {
            return 0;
        }
    }

    @WrapOperation(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"
            )
    )
    private int createschematichelper$patchTitle(GuiGraphics instance, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
        // Only replace the idle title; Create's own "Uploading..."/"Finished" states
        // (text != this.title) stay visible in download mode too.
        if (this.createschematichelper$urlField.isVisible() && text == this.title) {
            // x was centered for the original text's width; re-center for ours
            int adjustedX = x + (font.width(text) - font.width(createschematichelper$DOWNLOAD_TITLE)) / 2;
            return original.call(instance, font, createschematichelper$DOWNLOAD_TITLE, adjustedX, y, color, shadow);
        }
        return original.call(instance, font, text, x, y, color, shadow);
    }

    @WrapOperation(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            )
    )
    private void createschematichelper$patchRender(AllGuiTextures instance, GuiGraphics graphics, int x, int y, Operation<Void> original) {
        if (instance == AllGuiTextures.SCHEMATIC_TABLE && this.createschematichelper$urlField.isVisible()) {
            graphics.blit(createschematichelper$TABLE_TEXTURE, x, y, 0, 0, AllGuiTextures.SCHEMATIC_TABLE.getWidth(), AllGuiTextures.SCHEMATIC_TABLE.getHeight());
        } else {
            original.call(instance, graphics, x, y);
        }
    }
}
