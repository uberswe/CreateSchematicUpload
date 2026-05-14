package com.uberswe.createschematicdownload.createschematicdownload.neoforge;

import com.uberswe.createschematicdownload.createschematicdownload.CreateSchematicDownload;
import net.neoforged.fml.ModList;

public class CreateSchematicDownloadPlatformImpl {
    public static String getModVersion() {
        return ModList.get().getModContainerById(CreateSchematicDownload.MOD_ID).get().getModInfo().getVersion().toString();
    }
}
