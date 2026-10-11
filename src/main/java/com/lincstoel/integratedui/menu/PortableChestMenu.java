package com.lincstoel.integratedui.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's chest menu, with as many rows as needed, for a container item in the player's inventory that has no
 * screen of its own (see {@link ContainerItems.OwnScreen#CHEST}). Slots past the item's size only pad out the last row
 * and are left out. Otherwise like {@link PortableShulkerBoxMenu}: the item's own inventory slot is locked while it is
 * open, and closing the screen returns to the inventory.
 */
public class PortableChestMenu extends ChestMenu implements PortableMenu
{
    /** Server side only. */
    @Nullable
    private final ShulkerContainer containerItem;
    private final Inventory inventory;
    /** The item's inventory slot, kept in step on the client so it can lock and grey it out too. */
    private final DataSlot itemSlot;

    private PortableChestMenu(int windowId, Inventory inventory, Container container, @Nullable ShulkerContainer containerItem, int rows, int size, DataSlot itemSlot)
    {
        super(ModMenus.PORTABLE_CHEST.get(), windowId, inventory, container, rows);
        this.containerItem = containerItem;
        this.inventory = inventory;
        this.itemSlot = itemSlot;
        // Vanilla's chest slots take anything: swap in ones that keep to what the item takes
        for(int i = 0; i < rows * ShulkerContainer.COLUMNS; i++)
        {
            Slot old = this.slots.get(i);
            Slot slot = new ContainerItemSlot(container, i, old.x, old.y, i < size);
            slot.index = i;
            this.slots.set(i, slot);
        }
        this.addDataSlot(itemSlot);
    }

    /** Server side. */
    public PortableChestMenu(int windowId, Inventory inventory, ShulkerContainer containerItem, int rows, int size)
    {
        this(windowId, inventory, containerItem, containerItem, rows, size, DataSlot.standalone());
        this.itemSlot.set(containerItem.getInventorySlot());
    }

    public static PortableChestMenu fromNetwork(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf)
    {
        int rows = buf.readVarInt();
        int size = buf.readVarInt();
        DataSlot itemSlot = DataSlot.standalone();
        itemSlot.set(buf.readVarInt());
        return new PortableChestMenu(windowId, inventory, new SimpleContainer(rows * ShulkerContainer.COLUMNS), null, rows, size, itemSlot);
    }

    @Override
    public boolean isItemSlot(Slot slot)
    {
        return slot.container == this.inventory && slot.getContainerSlot() == this.itemSlot.get();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player)
    {
        boolean onItem = slotId >= 0 && slotId < this.slots.size() && this.isItemSlot(this.slots.get(slotId));
        boolean swapsItem = clickType == ClickType.SWAP && button == this.itemSlot.get();
        if(!onItem && !swapsItem)
        {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot)
    {
        return !this.isItemSlot(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void broadcastChanges()
    {
        if(this.containerItem != null)
        {
            this.containerItem.resolve();
            this.itemSlot.set(this.containerItem.getInventorySlot());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.containerItem == null || this.containerItem.resolve() == ShulkerContainer.State.AVAILABLE;
    }

    /** A slot of the container item: takes only what the item does, and doesn't exist past the item's size. */
    private static class ContainerItemSlot extends Slot
    {
        private final boolean exists;

        ContainerItemSlot(Container container, int index, int x, int y, boolean exists)
        {
            super(container, index, x, y);
            this.exists = exists;
        }

        @Override
        public boolean isActive()
        {
            return this.exists;
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            // On the server, the container adds the item's own restrictions
            return this.exists && stack.getItem().canFitInsideContainerItems() && this.container.canPlaceItem(this.getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player)
        {
            return this.exists;
        }
    }
}
