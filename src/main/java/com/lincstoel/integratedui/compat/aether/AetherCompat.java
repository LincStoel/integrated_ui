package com.lincstoel.integratedui.compat.aether;

import com.aetherteam.aether.AetherConfig;

/** Common Aether integration. Only used when the Aether is installed. */
public final class AetherCompat
{
    private AetherCompat() {}

    /**
     * Whether the Aether shows accessories in its own menu (its "Use default Accessories' menu" option is off), in
     * which case its menu, opened from the cloud button on the portrait, takes over from our equipment panel.
     */
    public static boolean usesOwnAccessoriesMenu()
    {
        return !AetherConfig.COMMON.use_default_accessories_menu.get();
    }
}
