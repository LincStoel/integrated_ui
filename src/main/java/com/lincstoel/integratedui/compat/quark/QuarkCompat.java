package com.lincstoel.integratedui.compat.quark;

import net.minecraft.world.entity.player.Player;
import org.violetmoon.quark.addons.oddities.module.BackpackModule;
import org.violetmoon.quark.base.Quark;

/** Quark calls made from common code. Only used when Quark is installed. */
public final class QuarkCompat
{
    private QuarkCompat() {}

    /** Whether Quark's backpack is enabled in Quark's config and worn, in which case the band can show it. */
    public static boolean isWearingQuarkBackpack(Player player)
    {
        return Quark.ZETA.modules.isEnabled(BackpackModule.class) && BackpackModule.isEntityWearingBackpack(player);
    }
}
