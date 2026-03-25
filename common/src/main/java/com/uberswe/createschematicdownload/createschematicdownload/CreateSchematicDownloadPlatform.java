package com.uberswe.createschematicdownload.createschematicdownload;

import dev.architectury.injectables.annotations.ExpectPlatform;

public class CreateSchematicDownloadPlatform {
	@ExpectPlatform
    public static String getModVersion() {
        throw new AssertionError("Method call expected to be replaced at compile time");
    }
}
