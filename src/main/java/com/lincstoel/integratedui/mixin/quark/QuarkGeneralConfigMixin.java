package com.lincstoel.integratedui.mixin.quark;

import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.violetmoon.quark.base.config.QuarkGeneralConfig;

/** Quark's buttons and keybinds only work on vanilla screens unless allowed in its config, so allow ours. */
@Mixin(value = QuarkGeneralConfig.class, remap = false)
public abstract class QuarkGeneralConfigMixin
{
    @ModifyReturnValue(method = "isScreenAllowed", at = @At("RETURN"))
    private static boolean integrated_ui$allowInventoryScreens(boolean allowed, Object screen)
    {
        return allowed || QuarkClientCompat.isIntegratedInventory(screen);
    }
}
