package com.lincstoel.integratedui.compat.curios;

import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.event.SlotModifiersUpdatedEvent;

public final class CuriosEvents
{
    private CuriosEvents() {}

    /** Curios slot counts changed: reopen the menu so its slot list matches again. */
    public static void onSlotModifiersUpdated(SlotModifiersUpdatedEvent event)
    {
        if(Equipment.currentKind() == Equipment.Kind.CURIOS && event.getEntity() instanceof ServerPlayer player
            && player.containerMenu instanceof IntegratedInventoryMenu)
        {
            IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        }
    }
}
