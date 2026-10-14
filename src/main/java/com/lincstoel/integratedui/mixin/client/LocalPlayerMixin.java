package com.lincstoel.integratedui.mixin.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.create.CreateClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a screen of another mod that was opened from the inventory return there when it is closed, in place of
 * closing to the world (which would show the world for a moment and recentre the cursor). Create's filter screens
 * close through here from several places (escape, the inventory key, their confirm button).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin
{
    @Inject(method = "closeContainer", at = @At("HEAD"), cancellable = true)
    private void integratedUi$returnToInventory(CallbackInfo ci)
    {
        if(IntegratedUI.isCreateLoaded() && CreateClient.returnToInventory())
        {
            ci.cancel();
        }
    }
}
