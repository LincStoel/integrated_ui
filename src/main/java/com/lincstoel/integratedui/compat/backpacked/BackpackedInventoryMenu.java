package com.lincstoel.integratedui.compat.backpacked;

import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.menu.BandTargets;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import com.lincstoel.integratedui.menu.OpenData;
import com.mrcrayfish.backpacked.BackpackHelper;
import com.mrcrayfish.backpacked.common.backpack.UnlockableSlots;
import com.mrcrayfish.backpacked.core.ModSyncedDataKeys;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
import com.mrcrayfish.backpacked.inventory.ManagementInventory;
import com.mrcrayfish.backpacked.inventory.container.SyncUnlockableSlots;
import com.mrcrayfish.backpacked.inventory.container.UnlockableController;
import com.mrcrayfish.backpacked.inventory.container.slot.BackpackSlot;
import com.mrcrayfish.backpacked.inventory.container.slot.UnlockableSlot;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageSyncUnlockSlot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;

/**
 * The integrated inventory with a Backpacked backpack. Backpack slots are Backpacked's own {@link BackpackSlot}s,
 * so Backpacked's unlock packet and client sync (which operate on any open menu containing
 * {@link UnlockableSlot}s) work here unchanged.
 * <p>
 * Only loaded when Backpacked is installed. Methods called from common code only use common types in their
 * signatures, so callers never link against Backpacked classes.
 */
public class BackpackedInventoryMenu extends IntegratedInventoryMenu implements SyncUnlockableSlots
{
    private final InventoryUnlockableController controller;

    private BackpackedInventoryMenu(int windowId, Inventory inventory, Container backpack, OpenData data, UnlockableSlots slots)
    {
        this(windowId, inventory, backpack, data, new InventoryUnlockableController(backpack, slots, List.of(inventory, backpack)));
    }

    private BackpackedInventoryMenu(int windowId, Inventory inventory, Container backpack, OpenData data, InventoryUnlockableController controller)
    {
        super(windowId, inventory, backpack, data, (container, index) -> new GridSlot(controller, container, index));
        this.controller = controller;
    }

    public static IntegratedInventoryMenu fromNetwork(int windowId, Inventory inventory, OpenData data, RegistryFriendlyByteBuf buf)
    {
        UnlockableSlots slots = UnlockableSlots.STREAM_CODEC.decode(buf);
        return new BackpackedInventoryMenu(windowId, inventory, new SimpleContainer(data.bandSize()), data, slots);
    }

    /** The indexes of the player's equipped backpacks, in Backpacked's order. */
    public static int[] getEquippedBackpacks(Player player)
    {
        IntList indexes = new IntArrayList();
        for(int i = 0; i < ManagementInventory.getMaxEquipable(); i++)
        {
            if(BackpackHelper.getBackpackStack(player, i).getItem() instanceof BackpackItem)
            {
                indexes.add(i);
            }
        }
        return indexes.toIntArray();
    }

    /** The index of the backpack Backpacked last showed the player. */
    public static int getSelectedBackpack(Player player)
    {
        return BackpackHelper.getSelectedBackpackIndex(player);
    }

    /**
     * Server side: opens the menu for the equipped backpack with the given index, and makes it Backpacked's selected
     * backpack. Returns false if there is no such backpack, in which case nothing was opened.
     */
    public static boolean open(ServerPlayer player, int index)
    {
        BackpackInventory inventory = ((BackpackedInventoryAccess) player).backpacked$GetBackpackInventory(index);
        if(inventory == null || !(inventory.getBackpackStack().getItem() instanceof BackpackItem item))
        {
            return false;
        }

        ItemStack stack = inventory.getBackpackStack();
        ModSyncedDataKeys.SELECTED_BACKPACK.setValue(player, index);
        UnlockableSlots slots = Objects.requireNonNullElse(item.getUnlockableSlots(stack), UnlockableSlots.ALL);
        Component title = stack.getHoverName();
        OpenData data = new OpenData(OpenData.Band.BACKPACK, index, item.getColumnCount(), item.getRowCount(), -1, title, createDisplayStack(stack),
            Equipment.collect(player));
        openMenu(player, data, (id, playerInventory, p) -> new BackpackedInventoryMenu(id, playerInventory, inventory, data, slots),
            buf -> UnlockableSlots.STREAM_CODEC.encode(buf, slots));
        return true;
    }

    @Override
    public boolean isShowing(BandTargets.BandTarget target)
    {
        return target instanceof BandTargets.BandTarget.Backpack backpack && backpack.index() == this.getBackpackIndex();
    }

    /**
     * Server side: renames the backpack shown in the player's open integrated inventory. Mirrors Backpacked's own
     * rename handling, which only accepts renames while its own backpack screen is open.
     */
    public static void rename(ServerPlayer player, int backpackIndex, String name)
    {
        ItemStack stack = BackpackHelper.getBackpackStack(player, backpackIndex);
        if(stack.isEmpty())
        {
            return;
        }
        if(name.isBlank())
        {
            stack.remove(DataComponents.CUSTOM_NAME);
        }
        else
        {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        // Reopen so the new name is shown
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
    }

    public UnlockableController getSlotController()
    {
        return this.controller;
    }

    @Override
    public void handleSyncSlots(ServerPlayer player, List<UnlockableSlot> unlockedSlots)
    {
        List<Integer> indexes = unlockedSlots.stream().map(slot -> slot.index).toList();
        Network.PLAY.sendToPlayer(() -> player, new MessageSyncUnlockSlot(indexes));
    }

    /**
     * A backpack slot that can be scrolled out of view. Hidden slots are moved off screen, so Backpacked's lock
     * overlay (drawn at each slot's position) isn't visible for them either.
     */
    public static class GridSlot extends BackpackSlot implements ScrollingSlot
    {
        private boolean shown = true;

        public GridSlot(UnlockableController controller, Container container, int index)
        {
            super(controller, container, index, HIDDEN, HIDDEN);
        }

        @Override
        public void setShown(boolean shown)
        {
            this.shown = shown;
        }

        @Override
        public boolean isActive()
        {
            return this.shown && super.isActive();
        }
    }
}
