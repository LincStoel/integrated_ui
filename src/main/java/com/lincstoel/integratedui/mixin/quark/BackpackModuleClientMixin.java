package com.lincstoel.integratedui.mixin.quark;

import com.lincstoel.integratedui.client.ClientEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.violetmoon.quark.addons.oddities.module.BackpackModule;

/**
 * Quark swaps the vanilla inventory screen for its backpack screen while a backpack is worn. While the integrated
 * inventory replaces the player's inventory, leave the vanilla screen to it instead (see ClientEvents), so that
 * Quark doesn't cancel it first.
 */
@Mixin(value = BackpackModule.Client.class, remap = false)
public abstract class BackpackModuleClientMixin
{
    @Inject(method = {"onOpenGUI", "clientTick"}, at = @At("HEAD"), cancellable = true)
    private void integrated_ui$leaveToIntegratedInventory(CallbackInfo ci)
    {
        if(ClientEvents.replacesInventory())
        {
            ci.cancel();
        }
    }
}
