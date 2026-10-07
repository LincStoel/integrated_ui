package com.lincstoel.integratedui.mixin;

import com.lincstoel.integratedui.config.StartupConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.violetmoon.quark.addons.oddities.module.BackpackModule;
import org.violetmoon.zeta.Zeta;
import org.violetmoon.zeta.module.ZetaModule;

/**
 * Every module toggle Zeta applies (initial load and each config reload) goes through
 * {@link ZetaModule#setEnabled(Zeta, boolean)}, so forcing the value here overrides Quark's config.
 */
@Mixin(value = ZetaModule.class, remap = false)
public abstract class ZetaModuleMixin
{
    @ModifyVariable(method = "setEnabled", at = @At("HEAD"), argsOnly = true)
    private boolean integrated_ui$disableQuarkBackpack(boolean willEnable)
    {
        if(willEnable && (Object) this instanceof BackpackModule && StartupConfig.disableQuarkBackpack())
        {
            return false;
        }
        return willEnable;
    }
}
