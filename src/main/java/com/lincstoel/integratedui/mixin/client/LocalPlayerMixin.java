package com.lincstoel.integratedui.mixin.client;

import com.lincstoel.integratedui.client.ClientEvents;
import com.lincstoel.integratedui.menu.Navigation;
import com.lincstoel.integratedui.network.OpenInventoryPayload;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While riding a mount with a screen of its own, the inventory key asks the server for that screen instead of opening
 * the inventory. Ask for the integrated inventory instead, which shows the mount in the band; the server still opens
 * the mount's own screen if the band can't show it (see Mounts#handleInventoryKey).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin
{
    @Inject(method = "sendOpenInventory", at = @At("HEAD"), cancellable = true)
    private void integratedUi$openIntegratedInventory(CallbackInfo ci)
    {
        if(ClientEvents.replacesInventory())
        {
            PacketDistributor.sendToServer(new OpenInventoryPayload(Navigation.CURRENT, true));
            ci.cancel();
        }
    }
}
