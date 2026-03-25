package com.uberswe.createschematicdownload.createschematicdownload.neoforge;

import com.uberswe.createschematicdownload.createschematicdownload.CreateSchematicDownload;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

public class DownloadIcon implements ScreenElement {
	@Override
	public void render(GuiGraphics graphics, int x, int y) {
		graphics.blit(CreateSchematicDownload.ICONS_ATLAS, x, y, 0, 0, 16, 16);
	}
}
