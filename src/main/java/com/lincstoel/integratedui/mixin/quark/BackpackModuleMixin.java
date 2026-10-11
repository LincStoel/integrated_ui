package com.lincstoel.integratedui.mixin.quark;

import com.lincstoel.integratedui.client.ClientEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.violetmoon.quark.addons.oddities.module.BackpackModule;

/**
 * Every request Quark makes to open its backpack screen (when the inventory opens, or when its held shulker box
 * closes) goes through {@link BackpackModule#requestBackpack()}. While the integrated inventory replaces the
 * player's inventory, ask for that instead: it shows the worn backpack in its band.
 */
@Mixin(value = BackpackModule.class, remap = false)
public abstract class BackpackModuleMixin
{
    @Inject(method = "requestBackpack", at = @At("HEAD"), cancellable = true)
    private static void integrated_ui$openIntegratedInventory(CallbackInfo ci)
    {
        if(ClientEvents.replacesInventory())
        {
            ClientEvents.requestInventory();
            ci.cancel();
        }
    }
}
