package com.lincstoel.integratedui.mixin.quark;

import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.violetmoon.quark.base.client.handler.InventoryButtonHandler;

/**
 * Quark only gives its player inventory buttons (and their keybinds) to screens that are an {@code InventoryScreen},
 * so treat ours as one. Otherwise they'd be taken for a container and get container buttons that act on the
 * crafting grid.
 */
@Mixin(value = InventoryButtonHandler.class, remap = false)
public abstract class InventoryButtonHandlerMixin
{
    @WrapOperation(method = {"initGui", "forGui"}, at = @At(value = "CONSTANT", args = "classValue=net/minecraft/client/gui/screens/inventory/InventoryScreen"))
    private static boolean integrated_ui$treatAsPlayerInventory(Object screen, Operation<Boolean> original)
    {
        return original.call(screen) || QuarkClientCompat.isIntegratedInventory(screen);
    }
}
