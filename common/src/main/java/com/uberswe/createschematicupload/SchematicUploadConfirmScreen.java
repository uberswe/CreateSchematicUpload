package com.uberswe.createschematicupload;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public class SchematicUploadConfirmScreen extends Screen {
    private final Path filePath;
    private final String fileName;

    public SchematicUploadConfirmScreen(Path filePath) {
        super(Component.translatable("createschematicupload.confirm.title"));
        this.filePath = filePath;
        this.fileName = filePath.getFileName().toString();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(new Button(
                centerX - 105, centerY + 10, 100, 20,
                Component.translatable("createschematicupload.confirm.upload"),
                button -> {
                    SchematicUploadHandler.confirmUpload(filePath);
                    this.onClose();
                }
        ));

        this.addRenderableWidget(new Button(
                centerX + 5, centerY + 10, 100, 20,
                Component.translatable("createschematicupload.confirm.cancel"),
                button -> this.onClose()
        ));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        drawCenteredString(poseStack, this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        drawCenteredString(poseStack, this.font,
                Component.translatable("createschematicupload.confirm.message", fileName),
                this.width / 2, this.height / 2 - 20, 0xCCCCCC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
