package com.uberswe.createschematicdownload.createschematicdownload;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateSchematicDownload {
	public static final String MOD_ID = "createschematicdownload";
	public static final String MOD_NAME = "CreateSchematicDownload";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	public static final ResourceLocation TABLE_NEW_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/schematic_table.png");
	public static final int TABLE_NEW_BACKGROUND_HEIGHT = 110;
	public static final int TABLE_Y_DIFFERENCE = TABLE_NEW_BACKGROUND_HEIGHT - 85; // Used to reposition UI elements such as buttons

	public static void init() {
	}
}
