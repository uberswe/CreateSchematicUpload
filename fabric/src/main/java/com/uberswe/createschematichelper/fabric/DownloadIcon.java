package com.uberswe.createschematichelper.fabric;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;

public class DownloadIcon implements ScreenElement {
    private static final ResourceLocation ICONS_ATLAS = new ResourceLocation("createschematichelper", "textures/gui/icons.png");

    @Override
    public void render(PoseStack ms, int x, int y) {
        RenderSystem.setShaderTexture(0, ICONS_ATLAS);
        GuiComponent.blit(ms, x, y, 0, 0, 16, 16, 256, 256);
    }
}
