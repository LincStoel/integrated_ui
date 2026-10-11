package com.lincstoel.integratedui.compat.mousetweaks;

import com.lincstoel.integratedui.IntegratedUI;
import yalter.mousetweaks.Main;

/** Reads Mouse Tweaks' settings. Only used when Mouse Tweaks is installed. */
public final class MouseTweaksCompat
{
    private static boolean failed;

    private MouseTweaksCompat() {}

    /**
     * Whether Mouse Tweaks moves items when the wheel turns over a slot (its "wheel tweak"), in which case it gets the
     * scroll over our slots. Assumed on if its settings can't be read (they aren't part of its API).
     */
    public static boolean isWheelTweakOn()
    {
        if(failed)
        {
            return true;
        }
        try
        {
            return Main.config == null || Main.config.wheelTweak;
        }
        catch(LinkageError e)
        {
            failed = true;
            IntegratedUI.LOGGER.warn("Couldn't read Mouse Tweaks' settings; assuming its scroll wheel tweak is on", e);
            return true;
        }
    }
}
