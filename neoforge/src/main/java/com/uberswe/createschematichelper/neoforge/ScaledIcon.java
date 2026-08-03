package com.uberswe.createschematichelper.neoforge;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders a standard 16x16 icon scaled down to 13x13 so it fits the icon area of
 * Create: Blueprinted's 15px SmallIconButton (which draws its icon at +1,+1).
 */
public class ScaledIcon implements ScreenElement {
    private static final float SCALE = 13f / 16f;
    private final ScreenElement inner;

    public ScaledIcon(ScreenElement inner) {
        this.inner = inner;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SCALE, SCALE, 1);
        inner.render(graphics, 0, 0);
        graphics.pose().popPose();
    }
}
