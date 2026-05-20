package com.uberswe.createschematichelper.forge.mixin;

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
import com.uberswe.createschematichelper.forge.DownloadIcon;
import net.minecraft.client.Minecraft;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    @Unique
    private static final ResourceLocation createschematichelper$TABLE_TEXTURE = new ResourceLocation("createschematichelper", "textures/gui/schematic_table.png");
    @Unique
    private static final Component createschematichelper$URL_FIELD_HINT = Component.translatable("text.createschematichelper.url_field_hint");
    @Unique
    private static final Component createschematichelper$PROCESSING_TITLE = Component.translatable("text.createschematichelper.processing");
    @Unique
    private static final Component createschematichelper$DOWNLOAD_TOOLTIP = Component.translatable("text.createschematichelper.download_schematic");
    @Unique
    private static final Component createschematichelper$LOCAL_TOOLTIP = Component.translatable("text.createschematichelper.choose_local_schematic");

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

        this.createschematichelper$modeButton = new IconButton(x + 208, y + 11, AllIcons.I_OPEN_FOLDER);
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

        String url = this.createschematichelper$urlField.getValue();
        this.createschematichelper$urlField.setValue("");
        this.createschematichelper$urlField.setEditable(false);
        this.createschematichelper$urlField.setHint(Component.translatable("text.createschematichelper.downloading"));
        this.lastChasingProgress = this.chasingProgress = this.progress = 0;

        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.supplyAsync(() -> SchematicDownloadHandler.downloadSchematic(url))
                .thenAccept(downloadedSchematicName -> mc.execute(() -> {
                    this.createschematichelper$urlField.setEditable(true);
                    if (downloadedSchematicName != null) {
                        CreateClient.SCHEMATIC_SENDER.startNewUpload(downloadedSchematicName);
                        this.createschematichelper$urlField.setHint(createschematichelper$URL_FIELD_HINT);
                    } else {
                        this.createschematichelper$urlField.setHint(Component.empty());
                        this.createschematichelper$urlField.setSuggestion("Failed to download schematic");
                    }
                }));
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

    @ModifyConstant(method = "m_7856_", constant = @Constant(intValue = 206), remap = false)
    private int createschematichelper$patchRefreshButtonX(int x) {
        return x + 2;
    }

    @ModifyConstant(method = "m_7856_", constant = @Constant(intValue = 21, ordinal = 2), remap = false)
    private int createschematichelper$patchRefreshButtonY(int y) {
        return 32;
    }

    @WrapOperation(method = "m_7286_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280430_(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"), remap = false)
    private int createschematichelper$stopLabelRender(GuiGraphics instance, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        if (!this.createschematichelper$urlField.isVisible()) {
            return original.call(instance, font, text, x, y, color);
        }
        return 0;
    }

    @WrapOperation(method = "m_7286_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280614_(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), remap = false)
    private int createschematichelper$patchTitle(GuiGraphics instance, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Integer> original) {
        if (this.createschematichelper$urlField.isVisible()) {
            return original.call(instance, font, createschematichelper$PROCESSING_TITLE, x, y, color, shadow);
        }
        return original.call(instance, font, text, x, y, color, shadow);
    }

    @WrapOperation(method = "m_7286_", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"), remap = false)
    private void createschematichelper$patchRender(AllGuiTextures instance, GuiGraphics graphics, int x, int y, Operation<Void> original) {
        if (instance == AllGuiTextures.SCHEMATIC_TABLE && this.createschematichelper$urlField.isVisible()) {
            graphics.blit(createschematichelper$TABLE_TEXTURE, x, y, 0, 0, AllGuiTextures.SCHEMATIC_TABLE.getWidth(), AllGuiTextures.SCHEMATIC_TABLE.getHeight());
        } else {
            original.call(instance, graphics, x, y);
        }
    }
}
