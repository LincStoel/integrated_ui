package com.lincstoel.integratedui.compat.accessories;

import io.wispforest.accessories.api.AccessoriesHolder;
import io.wispforest.accessories.client.AccessoriesClient;
import io.wispforest.accessories.networking.AccessoriesNetworking;
import io.wispforest.accessories.networking.holder.HolderProperty;
import io.wispforest.accessories.networking.holder.SyncHolderChange;
import net.minecraft.world.entity.player.Player;

/** Client-only Accessories calls, kept separate so the common backend never links client classes. */
final class AccessoriesClientAccess
{
    private AccessoriesClientAccess() {}

    static boolean openScreen()
    {
        return AccessoriesClient.attemptToOpenScreen();
    }

    /** Same as the "unused slots" toggle in Accessories' own screen: applied locally, then synced to the server. */
    static void setUnusedSlotsShown(Player player, boolean shown)
    {
        AccessoriesHolder holder = AccessoriesHolder.get(player);
        if(holder != null)
        {
            holder.showUnusedSlots(shown);
        }
        AccessoriesNetworking.sendToServer(SyncHolderChange.of(HolderProperty.UNUSED_PROP, shown));
    }
}
