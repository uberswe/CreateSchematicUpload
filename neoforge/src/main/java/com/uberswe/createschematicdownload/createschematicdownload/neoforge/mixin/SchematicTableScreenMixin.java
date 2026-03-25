package com.uberswe.createschematicdownload.createschematicdownload.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import com.simibubi.create.content.schematics.table.SchematicTableScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.uberswe.createschematicdownload.createschematicdownload.CreateSchematicDownload;
import com.uberswe.createschematicdownload.createschematicdownload.SchematicDownloader;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
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
    private IconButton refreshButton;
    @Unique
    private EditBox createschematicdownload$urlField;

    public SchematicTableScreenMixin(SchematicTableMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    private void createschematicdownload$addUrlField(CallbackInfo ci, @Local(name = "x") int x, @Local(name = "y") int y) {
        this.createschematicdownload$urlField = new EditBox(this.font, x + 26, y + 51, 154, 18, CommonComponents.EMPTY);
        this.createschematicdownload$urlField.setMaxLength(255);
        this.createschematicdownload$urlField.setTextColor(-1);
        this.createschematicdownload$urlField.setTextColorUneditable(-1);
        this.createschematicdownload$urlField.setBordered(false);
        this.addRenderableWidget(this.createschematicdownload$urlField);
    }

    @Inject(
            method = "lambda$init$0", // Confirm button callback
            at = @At("HEAD"),
            cancellable = true
    )
    private void createschematicdownload$downloadSchematic(CallbackInfo ci) {
        if (!menu.canWrite() || this.createschematicdownload$urlField.getValue().isEmpty()) {
            return;
        }

        this.lastChasingProgress = this.chasingProgress = this.progress = 0;
        String downloadedSchematicName = SchematicDownloader.downloadSchematic(this.createschematicdownload$urlField.getValue());
        if (downloadedSchematicName != null) {
            this.refreshButton.onClick(this.refreshButton.getX(), this.refreshButton.getY()); // Better than duplicating the refresh logic
            CreateClient.SCHEMATIC_SENDER.startNewUpload(downloadedSchematicName);
            ci.cancel();
        }
    }

    @Redirect(
            method = "init",
            at = @At(value = "NEW", target = "Lcom/simibubi/create/foundation/gui/widget/IconButton;", ordinal = 0)
    )
    private IconButton createschematicdownload$patchConfirmButtonLocation(int x, int y, ScreenElement icon) {
        return new IconButton(x, y + CreateSchematicDownload.TABLE_Y_DIFFERENCE, icon);
    }

    @Redirect(
            method = {"init", "renderBg"},
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;getHeight()I")
    )
    private int createschematicdonwload$patchGetHeight(AllGuiTextures instance) {
        if (instance == AllGuiTextures.SCHEMATIC_TABLE) {
            return CreateSchematicDownload.TABLE_NEW_BACKGROUND_HEIGHT;
        } else {
            return instance.getHeight();
        }
    }

    @ModifyConstant(
            method = "renderBg",
            constant = @Constant(intValue = 59)
    )
    private int createschematicdownload$patchProgressBarLocation(int y) {
        return y + CreateSchematicDownload.TABLE_Y_DIFFERENCE;
    }

    @Redirect(
            method = "renderBg",
            at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V")
    )
    private void createschematicdonwload$patchRender(AllGuiTextures instance, GuiGraphics graphics, int x, int y) {
        if (instance == AllGuiTextures.SCHEMATIC_TABLE) {
            graphics.blit(CreateSchematicDownload.TABLE_NEW_BACKGROUND_TEXTURE, x, y, 0, 0, AllGuiTextures.SCHEMATIC_TABLE.getWidth(), CreateSchematicDownload.TABLE_NEW_BACKGROUND_HEIGHT);
        }
    }
}
