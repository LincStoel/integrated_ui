package com.lincstoel.integratedui.compat.create;

import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.menu.SeamlessMenuProvider;
import com.lincstoel.integratedui.menu.SuspendedMenus;
import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;
import com.simibubi.create.content.logistics.filter.FilterItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Opens a Create filter's screen (list, attribute or package filter) from the inventory, as Quark does for shulker
 * boxes. Only used when Create is installed.
 * <p>
 * Create's filter screen only edits the filter in the main hand (it closes as soon as the main hand holds something
 * else), so a filter elsewhere in the inventory is swapped into the main hand while its screen is open and swapped
 * back when the screen closes (or when the player logs out).
 */
public final class CreateCompat
{
    /** Players whose filter is in their main hand for its screen: the swap, and the filter's menu. */
    private static final Map<UUID, OpenFilter> SWAPS = new HashMap<>();

    private CreateCompat() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener(CreateCompat::onContainerClosed);
        NeoForge.EVENT_BUS.addListener(CreateCompat::onLoggedOut);
    }

    public static boolean isFilter(ItemStack stack)
    {
        return stack.getItem() instanceof FilterItem;
    }

    /** Whether the menu is one of Create's filter menus. */
    public static boolean isFilterMenu(AbstractContainerMenu menu)
    {
        return menu instanceof AbstractFilterMenu;
    }

    public static void openFilter(ServerPlayer player, int slot)
    {
        Inventory inventory = player.getInventory();
        if(!player.isAlive() || player.isSpectator() || !player.containerMenu.getCarried().isEmpty() || !isFilterAt(inventory, slot))
        {
            return;
        }
        // The filter's screen replaces whatever menu is open, without closing its screen on the client (see
        // SeamlessMenuProvider), and a container's menu is held to return to (see SuspendedMenus)
        SuspendedMenus.open(player, () -> {
            SWAPS.remove(player.getUUID());
            // Closing the open menu may have returned crafting grid items to the inventory
            if(isFilterAt(inventory, slot))
            {
                openFilterAt(player, inventory, slot);
            }
        });
    }

    private static void openFilterAt(ServerPlayer player, Inventory inventory, int slot)
    {
        Swap swap = null;
        if(slot != inventory.selected)
        {
            swap = new Swap(slot, inventory.selected);
            swap.apply(inventory);
        }
        // As FilterItem.use does for a held filter; its menu reads the filter from the main hand
        ItemStack filter = inventory.getSelected();
        FilterItem item = (FilterItem) filter.getItem();
        player.openMenu(new SeamlessMenuProvider(item::createMenu, item.getDisplayName()), buf -> ItemStack.STREAM_CODEC.encode(buf, filter));
        if(swap != null)
        {
            if(isFilterMenu(player.containerMenu))
            {
                SWAPS.put(player.getUUID(), new OpenFilter(swap, player.containerMenu));
            }
            else
            {
                swap.apply(inventory);
            }
        }
    }

    /** Puts a filter that was swapped into the main hand back where it came from once its screen closes. */
    private static void onContainerClosed(PlayerContainerEvent.Close event)
    {
        if(event.getEntity() instanceof ServerPlayer player)
        {
            OpenFilter open = SWAPS.get(player.getUUID());
            if(open != null && open.menu == event.getContainer())
            {
                SWAPS.remove(player.getUUID());
                open.swap.apply(player.getInventory());
            }
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if(event.getEntity() instanceof ServerPlayer player)
        {
            OpenFilter open = SWAPS.remove(player.getUUID());
            if(open != null && player.containerMenu == open.menu)
            {
                open.swap.apply(player.getInventory());
            }
        }
    }

    private static boolean isFilterAt(Inventory inventory, int slot)
    {
        return slot >= 0 && slot < inventory.getContainerSize()
            && CompatGuard.test(CompatGuard.CREATE, () -> isFilter(inventory.getItem(slot)));
    }

    private record OpenFilter(Swap swap, AbstractContainerMenu menu) {}

    /** Swapping two slots both ways, so applying it again undoes it and nothing can be lost. */
    private record Swap(int filterSlot, int handSlot)
    {
        void apply(Inventory inventory)
        {
            ItemStack filter = inventory.getItem(this.filterSlot);
            inventory.setItem(this.filterSlot, inventory.getItem(this.handSlot));
            inventory.setItem(this.handSlot, filter);
        }
    }
}
