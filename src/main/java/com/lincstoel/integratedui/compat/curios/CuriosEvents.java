package com.lincstoel.integratedui.compat.curios;

import com.lincstoel.integratedui.menu.BackpackInventoryMenu;
import com.mrcrayfish.backpacked.common.Navigate;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.event.SlotModifiersUpdatedEvent;

public final class CuriosEvents
{
    private CuriosEvents() {}

    /** Curios slot counts changed: reopen the menu so its slot list matches again. */
    public static void onSlotModifiersUpdated(SlotModifiersUpdatedEvent event)
    {
        if(event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof BackpackInventoryMenu)
        {
            BackpackInventoryMenu.open(player, Navigate.CURRENT);
        }
    }
}
