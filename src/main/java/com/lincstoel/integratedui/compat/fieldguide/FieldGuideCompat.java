package com.lincstoel.integratedui.compat.fieldguide;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.lincstoel.integratedui.compat.CompatGuard;

/**
 * Opens the Field Guide from the button next to the equipment panel (see EquipmentPanel), as Field Guide's own
 * button does from the vanilla inventory. Only used when Field Guide is installed.
 */
public final class FieldGuideCompat
{
    private FieldGuideCompat() {}

    /**
     * Opens the guide as Field Guide's inventory button does, which does nothing if the player can't open it (e.g.
     * hasn't got the item). FieldGuideClient.openGuide is for the key and does nothing while a screen is open.
     */
    public static void openGuide()
    {
        CompatGuard.run(CompatGuard.FIELD_GUIDE, () ->
        {
            if(FieldGuideClient.canOpenGuide())
            {
                FieldGuideClient.openDefaultScreen();
            }
        });
    }
}
