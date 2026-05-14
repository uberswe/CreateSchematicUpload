package com.uberswe.createschematicdownload.createschematicdownload.neoforge.mixin;

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
import com.uberswe.createschematicdownload.createschematicdownload.CreateSchematicDownload;
import com.uberswe.createschematicdownload.createschematicdownload.SchematicDownloader;
import com.uberswe.createschematicdownload.createschematicdownload.neoforge.DownloadIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SchematicTableScreen.class)
public abstract class SchematicTableScreenMixin extends AbstractSimiContainerScreen<SchematicTableMenu> {
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
    private EditBox createschematicdownload$urlField;
    @Unique
    private IconButton createschematicdownload$modeButton;

    public SchematicTableScreenMixin(SchematicTableMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void createschematicdownload$addUrlField(CallbackInfo ci, @Local(name = "x") int x, @Local(name = "y") int y) {
        this.createschematicdownload$urlField = new EditBox(this.font, x + 26, y + 26, 154, 18, CommonComponents.EMPTY);
        this.createschematicdownload$urlField.setMaxLength(255);
        this.createschematicdownload$urlField.setTextColor(-1);
        this.createschematicdownload$urlField.setTextColorUneditable(-1);
        this.createschematicdownload$urlField.setBordered(false);
        this.createschematicdownload$urlField.setHint(CreateSchematicDownload.URL_FIELD_HINT);
        this.createschematicdownload$urlField.setResponder(s -> {
            if (s.isEmpty()) {
                this.createschematicdownload$urlField.setHint(CreateSchematicDownload.URL_FIELD_HINT);
            } else {
                this.createschematicdownload$urlField.setSuggestion(null);
            }
        });
        this.addRenderableWidget(this.createschematicdownload$urlField);

        this.createschematicdownload$modeButton = new IconButton(x + 208, y + 11, AllIcons.I_OPEN_FOLDER);
        this.createschematicdownload$modeButton.withCallback(this::createschematicdownload$toggleMode);
        this.addRenderableWidget(this.createschematicdownload$modeButton);

        // Sets up all the visibilities of the elements without duplicating logic.
        this.createschematicdownload$toggleMode();
    }

    @Inject(
            method = "lambda$init$0", // Confirm button callback
            at = @At("HEAD"),
            cancellable = true
    )
    private void createschematicdownload$downloadSchematic(CallbackInfo ci) {
        if (!menu.canWrite() || !this.createschematicdownload$urlField.isVisible()) {
            return;
        } else if (this.createschematicdownload$urlField.getValue().isBlank()) {
            ci.cancel();
            return;
        }

        this.lastChasingProgress = this.chasingProgress = this.progress = 0;
        String downloadedSchematicName = SchematicDownloader.downloadSchematic(this.createschematicdownload$urlField.getValue());
        if (downloadedSchematicName != null) {
            CreateClient.SCHEMATIC_SENDER.startNewUpload(downloadedSchematicName);
            this.createschematicdownload$urlField.setValue("");
        } else {
            this.createschematicdownload$urlField.setValue("");
            this.createschematicdownload$urlField.setHint(Component.empty());
            this.createschematicdownload$urlField.setSuggestion("Failed to download schematic");
        }

        ci.cancel();
    }

    @Unique
    private void createschematicdownload$toggleMode() {
        boolean localMode = this.createschematicdownload$urlField.isVisible();
        this.schematicsLabel.visible = this.schematicsLabel.active = localMode;
        this.folderButton.visible = this.folderButton.active = localMode;
        this.refreshButton.visible = this.refreshButton.active = localMode;
        if (this.schematicsArea != null) {
            this.schematicsArea.visible = this.schematicsArea.active = localMode;
        }

        this.createschematicdownload$urlField.visible = this.createschematicdownload$urlField.active = !localMode;

        this.createschematicdownload$modeButton.setToolTip(localMode ? CreateSchematicDownload.DOWNLOAD_SCHEMATIC_TOOLTIP : CreateSchematicDownload.CHOOSE_SCHEMATIC_TOOLTIP);
        this.createschematicdownload$modeButton.setIcon(localMode ? new DownloadIcon() : AllIcons.I_OPEN_FOLDER);
    }

    @ModifyConstant(
            method = "init",
            constant = @Constant(intValue = 206)
    )
    private int createschematicdownload$patchRefreshButtonX(int x) {
        return x + 2;
    }

    @ModifyConstant(
            method = "init",
            constant = @Constant(intValue = 21, ordinal = 2)
    )
    private int createschematicdownload$patchRefreshButtonY(int y) {
        return 32;
    }

    @WrapOperation(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"
            )
    )
    private int createschematicdownload$stopLabelRender(GuiGraphics instance, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        if (!this.createschematicdownload$urlField.isVisible()) {
            return original.call(instance, font, text, x, y, color);
        } else {
            return 0;
        }
    }

    @WrapOperation(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            )
    )
    private void createschematicdownload$patchRender(AllGuiTextures instance, GuiGraphics graphics, int x, int y, Operation<Void> original) {
        if (instance == AllGuiTextures.SCHEMATIC_TABLE && this.createschematicdownload$urlField.isVisible()) {
            graphics.blit(CreateSchematicDownload.TABLE_TEXTURE, x, y, 0, 0, AllGuiTextures.SCHEMATIC_TABLE.getWidth(), AllGuiTextures.SCHEMATIC_TABLE.getHeight());
        } else {
            original.call(instance, graphics, x, y);
        }
    }
}
