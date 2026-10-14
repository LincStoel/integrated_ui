package com.lincstoel.integratedui.mixin.client;

import com.lincstoel.integratedui.client.OpenFromInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns closing a screen that was opened from the inventory (or a container's screen) into going back there (see
 * {@link OpenFromInventory#returnFromScreen}). Every way of closing a screen ends here, whichever mod's screen it is.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin
{
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void integratedUi$returnFromScreen(@Nullable Screen screen, CallbackInfo ci)
    {
        if(screen == null && OpenFromInventory.returnFromScreen())
        {
            ci.cancel();
        }
    }
}
