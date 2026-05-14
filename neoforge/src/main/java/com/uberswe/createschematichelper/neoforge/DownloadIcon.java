package com.uberswe.createschematichelper.neoforge;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class DownloadIcon implements ScreenElement {
    private static final ResourceLocation ICONS_ATLAS = ResourceLocation.fromNamespaceAndPath("createschematichelper", "textures/gui/icons.png");

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.blit(ICONS_ATLAS, x, y, 0, 0, 16, 16);
    }
}
