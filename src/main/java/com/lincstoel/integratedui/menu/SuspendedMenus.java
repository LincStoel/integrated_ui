package com.lincstoel.integratedui.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of returning to a container (a chest, say) after an item in the player's inventory was opened from its
 * screen (see {@link #open} and OpenFromInventory on the client). The container's menu is put on hold rather than
 * closed while the item's screen is open, and made the open menu again when that screen closes, just as the client
 * puts the container's screen back. Held menus aren't closed in between, so nothing in them (a crafting grid's items,
 * say) is given back or dropped, and a chest stays open.
 * <p>
 * A held menu that isn't returned to (the player logs out, dies, or something else opens a menu) is closed then.
 */
public final class SuspendedMenus
{
    private static final Map<UUID, AbstractContainerMenu> HELD = new HashMap<>();
    /** Whether one of our item menus is being opened, which mustn't close the held menu. */
    private static boolean opening;

    private SuspendedMenus() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener(SuspendedMenus::onContainerOpened);
        NeoForge.EVENT_BUS.addListener(SuspendedMenus::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(SuspendedMenus::onDeath);
    }

    /**
     * Opens an item's screen with {@code open}, in place of the open menu. If that is a container's menu (anything
     * but the player's inventory), it is held to be returned to. If it is the screen of an item opened that way, it
     * is closed, and the held menu stays for the new item's screen to return to.
     */
    public static void open(ServerPlayer player, Runnable open)
    {
        AbstractContainerMenu current = player.containerMenu;
        AbstractContainerMenu held = HELD.get(player.getUUID());
        if(held == null && !(current instanceof InventoryMenu))
        {
            HELD.put(player.getUUID(), current);
            player.containerMenu = player.inventoryMenu;
        }
        else if(current != player.inventoryMenu)
        {
            // The item's screen opened over the held menu (or our own inventory menu)
            player.doCloseContainer();
        }
        opening = true;
        try
        {
            open.run();
        }
        finally
        {
            opening = false;
        }
    }

    /**
     * Client to server: the item's screen closed, and the client went back to the held menu's screen. Closes the
     * item's menu (if the client hasn't already) and makes the held menu the open one again.
     */
    public static void returnToHeld(ServerPlayer player)
    {
        AbstractContainerMenu held = HELD.remove(player.getUUID());
        if(held != null && !(player.containerMenu instanceof InventoryMenu))
        {
            player.doCloseContainer();
        }
        if(held == null || player.containerMenu != player.inventoryMenu || !player.isAlive())
        {
            // Nothing to return to: close the client's screen
            if(held != null)
            {
                close(player, held);
            }
            player.closeContainer();
            return;
        }
        player.containerMenu = held;
        held.sendAllDataToRemote();
    }

    /** Anything else opening (something we didn't open over the held menu) means it won't be returned to. */
    private static void onContainerOpened(PlayerContainerEvent.Open event)
    {
        if(!opening && event.getEntity() instanceof ServerPlayer player)
        {
            closeHeld(player);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if(event.getEntity() instanceof ServerPlayer player)
        {
            closeHeld(player);
        }
    }

    /** Before the player's items drop, so a held crafting grid's items drop with them. */
    private static void onDeath(LivingDeathEvent event)
    {
        if(event.getEntity() instanceof ServerPlayer player)
        {
            closeHeld(player);
        }
    }

    private static void closeHeld(ServerPlayer player)
    {
        AbstractContainerMenu held = HELD.remove(player.getUUID());
        if(held != null)
        {
            close(player, held);
        }
    }

    /** Closes a held menu as ServerPlayer.doCloseContainer would have. */
    private static void close(ServerPlayer player, AbstractContainerMenu held)
    {
        held.removed(player);
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(player, held));
    }
}
