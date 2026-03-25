package com.uberswe.createschematicdownload.createschematicdownload;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateSchematicDownload {
	public static final String MOD_ID = "createschematicdownload";
	public static final String MOD_NAME = "CreateSchematicDownload";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	public static final ResourceLocation ICONS_ATLAS = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/icons.png");
	public static final ResourceLocation TABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/schematic_table.png");
	public static final Component URL_FIELD_HINT = Component.translatable("text.createschematicdownload.url_field_hint");
	public static final Component DOWNLOAD_SCHEMATIC_TOOLTIP = Component.translatable("text.createschematicdownload.download_schematic");
	public static final Component CHOOSE_SCHEMATIC_TOOLTIP = Component.translatable("text.createschematicdownload.choose_local_schematic");

	public static void init() {
	}
}
