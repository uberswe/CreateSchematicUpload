package com.uberswe.createschematicdownload.createschematicdownload.fabric;

import com.uberswe.createschematicdownload.createschematicdownload.CreateSchematicDownload;
import net.fabricmc.loader.api.FabricLoader;

public class CreateSchematicDownloadPlatformImpl {
    public static String getModVersion() {
        return FabricLoader.getInstance().getModContainer(CreateSchematicDownload.MOD_ID).get().getMetadata().getVersion().getFriendlyString();
    }
}
