package com.uberswe.createschematicdownload.createschematicdownload.fabric;

import com.uberswe.createschematicdownload.createschematicdownload.CreateSchematicDownload;
import net.fabricmc.api.ModInitializer;

public class CreateSchematicDownloadFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        CreateSchematicDownload.init();
    }
}
