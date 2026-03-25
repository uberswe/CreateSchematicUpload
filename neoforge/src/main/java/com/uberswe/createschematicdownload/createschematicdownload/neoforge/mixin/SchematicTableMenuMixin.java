package com.uberswe.createschematicdownload.createschematicdownload.neoforge.mixin;

import com.simibubi.create.content.schematics.table.SchematicTableBlockEntity;
import com.simibubi.create.content.schematics.table.SchematicTableMenu;
import com.simibubi.create.foundation.gui.menu.MenuBase;
import com.uberswe.createschematicdownload.createschematicdownload.CreateSchematicDownload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(SchematicTableMenu.class)
public abstract class SchematicTableMenuMixin extends MenuBase<SchematicTableBlockEntity> {
    protected SchematicTableMenuMixin(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    @ModifyConstant(
            method = "addSlots",
            constant = @Constant(intValue = 59)
    )
    private @Coerce int createschematicdownload$patchIOSlotLocations(int y) {
        return y + CreateSchematicDownload.TABLE_Y_DIFFERENCE;
    }

    @Redirect(
            method = "addSlots",
            at = @At(value = "NEW", target = "(Lnet/minecraft/world/Container;III)Lnet/minecraft/world/inventory/Slot;")
    )
    private Slot createschematicdownload$patchInventorySlotLocations(Container container, int slot, int x, int y) {
        return new Slot(container, slot, x, y + CreateSchematicDownload.TABLE_Y_DIFFERENCE);
    }
}
