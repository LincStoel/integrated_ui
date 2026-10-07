package com.lincstoel.integratedui.compat;

import com.lincstoel.integratedui.compat.accessories.AccessoriesBackend;
import com.lincstoel.integratedui.compat.curios.CuriosBackend;
import net.neoforged.fml.ModList;

/**
 * Picks the equipment slot provider. Accessories wins when present: with the Accessories Compatibility Layer
 * installed, the Curios API is redirected into Accessories, so Accessories already holds the Curios slots.
 */
public final class Equipment
{
    private static EquipmentBackend backend;

    private Equipment() {}

    public static EquipmentBackend get()
    {
        if(backend == null)
        {
            ModList mods = ModList.get();
            if(mods.isLoaded("accessories"))
            {
                backend = new AccessoriesBackend();
            }
            else if(mods.isLoaded("curios"))
            {
                backend = new CuriosBackend();
            }
            else
            {
                backend = EquipmentBackend.NONE;
            }
        }
        return backend;
    }
}
