package com.lincstoel.integratedui.compat.jei;

import com.lincstoel.integratedui.compat.CompatGuard;
import mezz.jei.common.Internal;

/**
 * Shows and hides JEI's overlays from the button next to the equipment panel (see EquipmentPanel), as JEI's own
 * toggle key does. JEI's API has no way to do this, so this uses its internal toggle state. Only used when JEI is
 * installed, and kept apart from {@link JeiCompat} so that the button doesn't load the plugin class.
 */
public final class JeiOverlayToggle
{
    private JeiOverlayToggle() {}

    /** Whether JEI's overlays are shown; true if that can't be told, so the button doesn't look off when it isn't. */
    public static boolean isShown()
    {
        return CompatGuard.get(CompatGuard.JEI, () -> Internal.getClientToggleState().isOverlayEnabled(), true);
    }

    public static void toggle()
    {
        CompatGuard.run(CompatGuard.JEI, () -> Internal.getClientToggleState().toggleOverlayEnabled());
    }
}
