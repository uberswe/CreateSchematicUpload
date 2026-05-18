package com.uberswe.createschematichelper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public class SchematicUploadConfirmScreen extends Screen {
    private final Path filePath;
    private final String fileName;

    public SchematicUploadConfirmScreen(Path filePath) {
        super(Component.translatable("createschematichelper.confirm.title"));
        this.filePath = filePath;
        this.fileName = filePath.getFileName().toString();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.addRenderableWidget(Button.builder(
                Component.translatable("createschematichelper.confirm.upload"),
                button -> {
                    SchematicUploadHandler.confirmUpload(filePath);
                    this.onClose();
                }
        ).bounds(centerX - 105, centerY + 10, 100, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.translatable("createschematichelper.confirm.cancel"),
                button -> this.onClose()
        ).bounds(centerX + 5, centerY + 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("createschematichelper.confirm.message", fileName),
                this.width / 2, this.height / 2 - 20, 0xCCCCCC);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
