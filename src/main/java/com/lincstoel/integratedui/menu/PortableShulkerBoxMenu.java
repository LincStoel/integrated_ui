package com.lincstoel.integratedui.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's shulker box menu for a container item (usually a shulker box) in the player's inventory, shown in the
 * plain shulker box screen, which returns to the inventory when closed. The item's own inventory slot is locked while
 * it is open (and greyed out on screen), so it can't be moved, dropped or swapped away; should it be lost anyway, the
 * menu closes.
 */
public class PortableShulkerBoxMenu extends ShulkerBoxMenu implements PortableMenu
{
    /** Server side only. */
    @Nullable
    private final ShulkerContainer shulkerBox;
    private final Inventory inventory;
    /** The item's inventory slot, kept in step on the client so it can lock and grey it out too. */
    private final DataSlot itemSlot;

    private PortableShulkerBoxMenu(int windowId, Inventory inventory, @Nullable ShulkerContainer shulkerBox, SimpleContainer clientContainer, DataSlot itemSlot)
    {
        super(windowId, inventory, shulkerBox != null ? shulkerBox : clientContainer);
        this.shulkerBox = shulkerBox;
        this.inventory = inventory;
        this.itemSlot = itemSlot;
        this.addDataSlot(itemSlot);
    }

    /** Server side. */
    public PortableShulkerBoxMenu(int windowId, Inventory inventory, ShulkerContainer shulkerBox)
    {
        this(windowId, inventory, shulkerBox, null, DataSlot.standalone());
        this.itemSlot.set(shulkerBox.getInventorySlot());
    }

    public static PortableShulkerBoxMenu fromNetwork(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf)
    {
        DataSlot itemSlot = DataSlot.standalone();
        itemSlot.set(buf.readVarInt());
        return new PortableShulkerBoxMenu(windowId, inventory, null, new SimpleContainer(ContainerItems.SHULKER_BOX_SIZE), itemSlot);
    }

    @Override
    public MenuType<?> getType()
    {
        return ModMenus.PORTABLE_SHULKER_BOX.get();
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
        if(this.shulkerBox != null)
        {
            this.shulkerBox.resolve();
            this.itemSlot.set(this.shulkerBox.getInventorySlot());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.shulkerBox == null || this.shulkerBox.resolve() == ShulkerContainer.State.AVAILABLE;
    }
}
