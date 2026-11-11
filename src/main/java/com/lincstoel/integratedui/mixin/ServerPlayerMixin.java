package com.lincstoel.integratedui.mixin;

import com.lincstoel.integratedui.menu.Mounts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Captures the screen a mount opens, rather than opening it, while the band is finding out what that screen holds (see
 * {@link Mounts}); and opens it seamlessly when the band asks for it. Both ways a mount's screen opens go through here:
 * vanilla's horse screen, and any menu (NeoForge's openMenu, which the one without extra data also ends in).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin
{
    @Inject(method = "openHorseInventory", at = @At("HEAD"), cancellable = true)
    private void integratedUi$captureHorseScreen(AbstractHorse horse, Container container, CallbackInfo ci)
    {
        if(Mounts.captureHorse((ServerPlayer) (Object) this, horse, container))
        {
            ci.cancel();
        }
    }

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;", at = @At("HEAD"), cancellable = true)
    private void integratedUi$captureMenu(@Nullable MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraData, CallbackInfoReturnable<OptionalInt> cir)
    {
        if(provider != null && Mounts.captureMenu((ServerPlayer) (Object) this, provider, extraData))
        {
            cir.setReturnValue(OptionalInt.empty());
        }
    }

    @ModifyVariable(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;", at = @At("HEAD"), argsOnly = true)
    private MenuProvider integratedUi$openSeamlessly(MenuProvider provider)
    {
        return Mounts.wrapSeamless(provider);
    }
}
