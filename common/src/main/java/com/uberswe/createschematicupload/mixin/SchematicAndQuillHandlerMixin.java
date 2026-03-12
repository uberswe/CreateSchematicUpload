package com.uberswe.createschematicupload.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.schematics.SchematicExport;
import com.simibubi.create.content.schematics.client.SchematicAndQuillHandler;
import com.uberswe.createschematicupload.SchematicUploadHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;

@Mixin(value = SchematicAndQuillHandler.class, remap = false)
public class SchematicAndQuillHandlerMixin {

    @WrapOperation(
            method = "saveSchematic",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/schematics/SchematicExport;saveSchematic(Ljava/nio/file/Path;Ljava/lang/String;ZLnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lcom/simibubi/create/content/schematics/SchematicExport$SchematicExportResult;",
                    remap = true
            ),
            remap = false
    )
    private static SchematicExport.SchematicExportResult onSaveSchematic(
            Path dir, String fileName, boolean overwrite, Level level, BlockPos first, BlockPos second,
            Operation<SchematicExport.SchematicExportResult> original) {
        SchematicExport.SchematicExportResult result = original.call(dir, fileName, overwrite, level, first, second);
        if (result != null) {
            SchematicUploadHandler.onSchematicSaved(result.file());
        }
        return result;
    }
}
