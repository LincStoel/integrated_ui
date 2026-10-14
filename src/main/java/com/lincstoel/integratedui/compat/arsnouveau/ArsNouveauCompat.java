package com.lincstoel.integratedui.compat.arsnouveau;

import com.hollingsworth.arsnouveau.common.items.SpellBook;
import com.lincstoel.integratedui.menu.SuspendedMenus;
import com.lincstoel.integratedui.network.SpellBookOpenedPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Opens an Ars Nouveau spell book's spell crafting screen from the inventory, as Quark does for shulker boxes. Only
 * used when Ars Nouveau is installed.
 * <p>
 * Ars Nouveau's screen only edits a book held in a hand (its packets name the hand), so a book elsewhere in the
 * inventory is swapped into the main hand while the screen is open and swapped back when the client says it closed
 * (or when the player logs out).
 */
public final class ArsNouveauCompat
{
    /** Players whose book is in their main hand for the screen: the book's inventory slot and the main hand's. */
    private static final Map<UUID, Swap> SWAPS = new HashMap<>();

    private ArsNouveauCompat() {}

    public static boolean isSpellBook(ItemStack stack)
    {
        return stack.getItem() instanceof SpellBook;
    }

    public static void openSpellBook(ServerPlayer player, int slot)
    {
        restoreSwap(player);
        Inventory inventory = player.getInventory();
        if(!player.isAlive() || player.isSpectator() || !player.containerMenu.getCarried().isEmpty() || !isBookAt(inventory, slot))
        {
            return;
        }
        // The book's screen replaces whatever menu is open, without closing its screen on the client first (see
        // OpenFromInventory), and a container's menu is held to return to (see SuspendedMenus)
        SuspendedMenus.open(player, () -> {
            // Closing the open menu may have returned crafting grid items to the inventory
            if(isBookAt(inventory, slot))
            {
                openBookAt(player, inventory, slot);
            }
        });
    }

    private static void openBookAt(ServerPlayer player, Inventory inventory, int slot)
    {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if(slot == Inventory.SLOT_OFFHAND)
        {
            hand = InteractionHand.OFF_HAND;
        }
        else if(slot != inventory.selected)
        {
            Swap swap = new Swap(slot, inventory.selected);
            swap.apply(inventory);
            SWAPS.put(player.getUUID(), swap);
            player.inventoryMenu.broadcastChanges();
        }
        PacketDistributor.sendToPlayer(player, new SpellBookOpenedPayload(hand));
    }

    /** Puts a book that was swapped into the main hand back where it came from. */
    public static void restoreSwap(ServerPlayer player)
    {
        Swap swap = SWAPS.remove(player.getUUID());
        if(swap != null)
        {
            swap.apply(player.getInventory());
            player.containerMenu.broadcastChanges();
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if(event.getEntity() instanceof ServerPlayer player)
        {
            restoreSwap(player);
        }
    }

    private static boolean isBookAt(Inventory inventory, int slot)
    {
        return slot >= 0 && slot < inventory.getContainerSize() && isSpellBook(inventory.getItem(slot));
    }

    /** Swapping two slots both ways, so applying it again undoes it and nothing can be lost. */
    private record Swap(int bookSlot, int handSlot)
    {
        void apply(Inventory inventory)
        {
            ItemStack book = inventory.getItem(this.bookSlot);
            inventory.setItem(this.bookSlot, inventory.getItem(this.handSlot));
            inventory.setItem(this.handSlot, book);
        }
    }
}
