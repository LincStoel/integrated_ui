package com.lincstoel.integratedui.compat.supplementaries;

import com.lincstoel.integratedui.menu.ShulkerContainer;
import net.mehvahdjukaar.supplementaries.common.inventories.VariableSizeContainerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Supplementaries' sack menu for a sack in the player's inventory. The sack's own inventory slot is locked while it
 * is open (and greyed out on screen), so it can't be moved, dropped or swapped away; should it be lost anyway, the
 * menu closes. Only used when Supplementaries is installed.
 */
public class PortableSackMenu extends VariableSizeContainerMenu
{
    /** Server side only. */
    @Nullable
    private final ShulkerContainer sack;
    private final Inventory inventory;
    /** The sack's inventory slot, kept in step on the client so it can lock and grey it out too. */
    private final DataSlot sackSlot;

    private PortableSackMenu(int windowId, Inventory inventory, Container container, @Nullable ShulkerContainer sack, int size, DataSlot sackSlot)
    {
        super(SupplementariesCompat.PORTABLE_SACK.get(), windowId, inventory, container, size);
        this.sack = sack;
        this.inventory = inventory;
        this.sackSlot = sackSlot;
        this.addDataSlot(sackSlot);
    }

    /** Server side. */
    PortableSackMenu(int windowId, Inventory inventory, ShulkerContainer sack, int size)
    {
        this(windowId, inventory, sack, sack, size, DataSlot.standalone());
        this.sackSlot.set(sack.getInventorySlot());
    }

    public static PortableSackMenu fromNetwork(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf)
    {
        int size = buf.readVarInt();
        DataSlot sackSlot = DataSlot.standalone();
        sackSlot.set(buf.readVarInt());
        return new PortableSackMenu(windowId, inventory, new SimpleContainer(size), null, size, sackSlot);
    }

    /** The sack's index in the player's inventory, or -1 while it isn't there. */
    public int getSackSlot()
    {
        return this.sackSlot.get();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player)
    {
        boolean onSack = slotId >= 0 && slotId < this.slots.size() && this.isSackSlot(this.slots.get(slotId));
        boolean swapsSack = clickType == ClickType.SWAP && button == this.getSackSlot();
        if(!onSack && !swapsSack)
        {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot)
    {
        return !this.isSackSlot(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    public boolean isSackSlot(Slot slot)
    {
        return slot.container == this.inventory && slot.getContainerSlot() == this.getSackSlot();
    }

    @Override
    public void broadcastChanges()
    {
        if(this.sack != null)
        {
            this.sack.resolve();
            this.sackSlot.set(this.sack.getInventorySlot());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.sack == null || this.sack.resolve() == ShulkerContainer.State.AVAILABLE;
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        if(this.sack != null)
        {
            SupplementariesCompat.playSound(player, 0.8F);
        }
    }
}
