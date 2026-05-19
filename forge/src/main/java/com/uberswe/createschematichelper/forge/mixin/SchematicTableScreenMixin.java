package com.uberswe.createschematichelper.forge.mixin;

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
import com.uberswe.createschematichelper.forge.DownloadIcon;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SchematicTableScreen.class)
public abstract class SchematicTableScreenMixin extends AbstractSimiContainerScreen<SchematicTableMenu> {
    @Unique
    private static final ResourceLocation createschematichelper$TABLE_TEXTURE = new ResourceLocation("createschematichelper", "textures/gui/schematic_table.png");
    @Unique
    private static final Component createschematichelper$URL_FIELD_HINT = new TranslatableComponent("text.createschematichelper.url_field_hint");
    @Unique
    private static final Component createschematichelper$PROCESSING_TITLE = new TranslatableComponent("text.createschematichelper.processing");
    @Unique
    private static final Component createschematichelper$DOWNLOAD_TOOLTIP = new TranslatableComponent("text.createschematichelper.download_schematic");
    @Unique
    private static final Component createschematichelper$LOCAL_TOOLTIP = new TranslatableComponent("text.createschematichelper.choose_local_schematic");

    @Shadow private float lastChasingProgress;
    @Shadow private float chasingProgress;
    @Shadow private float progress;
    @Shadow private Label schematicsLabel;
    @Shadow private ScrollInput schematicsArea;
    @Shadow private IconButton folderButton;
    @Shadow private IconButton refreshButton;
    @Unique private EditBox createschematichelper$urlField;
    @Unique private IconButton createschematichelper$modeButton;

    public SchematicTableScreenMixin(SchematicTableMenu container, Inventory inv, Component title) {
        super(container, inv, title);
    }

    // SRG name for init()V — refmap is not loaded at runtime on Forge
    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void createschematichelper$addUrlField(CallbackInfo ci) {
        int x = this.leftPos;
        int y = this.topPos;

        this.createschematichelper$urlField = new EditBox(this.font, x + 26, y + 26, 154, 18, TextComponent.EMPTY);
        this.createschematichelper$urlField.setMaxLength(255);
        this.createschematichelper$urlField.setTextColor(-1);
        this.createschematichelper$urlField.setTextColorUneditable(-1);
        this.createschematichelper$urlField.setBordered(false);
        this.createschematichelper$urlField.setSuggestion(createschematichelper$URL_FIELD_HINT.getString());
        this.createschematichelper$urlField.setResponder(s -> {
            if (s.isEmpty()) {
                this.createschematichelper$urlField.setSuggestion(createschematichelper$URL_FIELD_HINT.getString());
            } else {
                this.createschematichelper$urlField.setSuggestion(null);
            }
        });
        this.addRenderableWidget(this.createschematichelper$urlField);

        this.createschematichelper$modeButton = new IconButton(x + 207, y + 11, AllIcons.I_OPEN_FOLDER);
        this.createschematichelper$modeButton.withCallback(this::createschematichelper$toggleMode);
        this.addRenderableWidget(this.createschematichelper$modeButton);

        this.createschematichelper$toggleMode();
    }

    @Inject(method = "lambda$init$0", at = @At("HEAD"), cancellable = true, remap = false)
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
        this.createschematichelper$urlField.visible = this.createschematichelper$urlField.active = !localMode;
        this.createschematichelper$modeButton.setToolTip(localMode ? createschematichelper$DOWNLOAD_TOOLTIP : createschematichelper$LOCAL_TOOLTIP);
        this.createschematichelper$modeButton.setIcon(localMode ? new DownloadIcon() : AllIcons.I_OPEN_FOLDER);
    }

    @ModifyConstant(method = "m_7856_", constant = @Constant(intValue = 21, ordinal = 3), remap = false)
    private int createschematichelper$patchRefreshButtonY(int y) {
        return 32;
    }

    @Redirect(
            method = "m_7286_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;m_92763_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I"
            ),
            remap = false
    )
    private int createschematichelper$stopLabelRender(Font instance, PoseStack poseStack, Component text, float x, float y, int color) {
        if (!this.createschematichelper$urlField.isVisible()) {
            return instance.drawShadow(poseStack, text, x, y, color);
        }
        return 0;
    }

    @Redirect(
            method = "m_7286_",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lcom/mojang/blaze3d/vertex/PoseStack;IILnet/minecraft/client/gui/GuiComponent;)V"
            ),
            remap = false
    )
    private void createschematichelper$patchRender(AllGuiTextures instance, PoseStack poseStack, int x, int y, GuiComponent guiComponent) {
        if (instance == AllGuiTextures.SCHEMATIC_TABLE && this.createschematichelper$urlField.isVisible()) {
            RenderSystem.setShaderTexture(0, createschematichelper$TABLE_TEXTURE);
            blit(poseStack, x, y, 0, 0, 214, 85);
        } else {
            instance.render(poseStack, x, y, guiComponent);
        }
    }
}
