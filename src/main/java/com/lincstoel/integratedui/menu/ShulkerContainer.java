package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

/**
 * Server side: the contents of a container item (see {@link ContainerItems}) such as a shulker box in the player's
 * inventory, read from and written straight back to the item's {@link DataComponents#CONTAINER} component.
 * <p>
 * The shulker box itself stays in the inventory and can be moved around while it is shown, so the container
 * follows the stack: by identity, or failing that (picking a stack up splits it into a copy) by an identical stack.
 * Its contents can only be changed while the stack is actually in the inventory; while it is on the cursor or in the
 * crafting grid they are shown but locked, and once it is gone the menu is reopened (see {@link ShulkerInventoryMenu}).
 */
public class ShulkerContainer implements Container
{
    public static final int COLUMNS = 9;

    /** The inventory indexes searched for shulker boxes, in the order they appear on screen: main inventory, hotbar, offhand. */
    static final int[] INVENTORY_ORDER = createInventoryOrder();

    private final Player player;
    private final Inventory inventory;
    private final int size;
    private final int usableSize;
    /** At least {@link #size} long; longer if the item holds more than that, so nothing beyond is lost on saving. */
    private NonNullList<ItemStack> items;
    private ItemStack stack;
    private int slot = -1;
    /** A copy of the stack as last seen, to recognise it once it has been split into a copy. */
    private Snapshot snapshot;
    @Nullable
    private ItemContainerContents loadedFrom;
    private State state = State.AVAILABLE;

    /**
     * {@code size} is the number of slots the container offers, of which only the first {@code usableSize} take
     * items (the rest only pad out a grid).
     */
    public ShulkerContainer(Player player, ItemStack stack, int size, int usableSize)
    {
        this.player = player;
        this.size = size;
        this.usableSize = Math.min(usableSize, size);
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
        this.inventory = player.getInventory();
        this.stack = stack;
        this.snapshot = new Snapshot(stack, stack.copy());
        this.resolve();
    }

    private static int[] createInventoryOrder()
    {
        int[] order = new int[Inventory.INVENTORY_SIZE + 1];
        int i = 0;
        for(int slot = Inventory.getSelectionSize(); slot < Inventory.INVENTORY_SIZE; slot++)
        {
            order[i++] = slot;
        }
        for(int slot = 0; slot < Inventory.getSelectionSize(); slot++)
        {
            order[i++] = slot;
        }
        order[i] = Inventory.SLOT_OFFHAND;
        return order;
    }

    public ItemStack getStack()
    {
        return this.stack;
    }

    public Snapshot getSnapshot()
    {
        return this.snapshot;
    }

    /** The shulker box's index in the player's inventory, or -1 while it isn't there. */
    public int getInventorySlot()
    {
        return this.slot;
    }

    public State getState()
    {
        return this.state;
    }

    /** Whether the contents may be changed: the shulker box is in the inventory, right where it was last seen. */
    public boolean isAccessible()
    {
        return this.slot >= 0 && this.inventory.getItem(this.slot) == this.stack && !this.stack.isEmpty();
    }

    /** Finds the shulker box again after it has moved, and reloads its contents if they changed. */
    public State resolve()
    {
        if(!this.isAccessible())
        {
            this.slot = this.find(true);
            if(this.slot < 0)
            {
                this.slot = this.find(false);
            }
            if(this.slot >= 0)
            {
                this.stack = this.inventory.getItem(this.slot);
            }
        }
        if(this.isAccessible())
        {
            this.state = State.AVAILABLE;
            this.reloadIfChanged();
            this.snapshot = new Snapshot(this.stack, this.stack.copy());
        }
        else
        {
            this.slot = -1;
            this.state = this.isHeldElsewhere() ? State.HELD : State.GONE;
        }
        return this.state;
    }

    /** Whether the box is on the cursor or in the crafting grid, where it is only briefly (e.g. to dye it). */
    private boolean isHeldElsewhere()
    {
        AbstractContainerMenu menu = this.player.containerMenu;
        if(this.snapshot.matches(menu.getCarried()))
        {
            return true;
        }
        return menu instanceof InventoryMenu inventoryMenu && inventoryMenu.getCraftSlots().getItems().stream().anyMatch(this.snapshot::matches);
    }

    private int find(boolean sameStack)
    {
        for(int i : INVENTORY_ORDER)
        {
            ItemStack stack = this.inventory.getItem(i);
            if(sameStack ? stack == this.stack && !stack.isEmpty() : this.snapshot.matches(stack))
            {
                return i;
            }
        }
        return -1;
    }

    private void reloadIfChanged()
    {
        ItemContainerContents contents = this.stack.get(DataComponents.CONTAINER);
        if(contents != this.loadedFrom)
        {
            this.loadedFrom = contents;
            int stored = contents != null ? (int) contents.stream().count() : 0;
            this.items = NonNullList.withSize(Math.max(this.size, stored), ItemStack.EMPTY);
            if(contents != null)
            {
                contents.copyInto(this.items);
            }
        }
    }

    /** Writes the contents back to the shulker box, if it is still there. */
    private void save()
    {
        if(this.isAccessible())
        {
            ItemContainerContents contents = ItemContainerContents.fromItems(this.items);
            this.stack.set(DataComponents.CONTAINER, contents);
            this.loadedFrom = contents;
        }
    }

    @Override
    public int getContainerSize()
    {
        return this.size;
    }

    @Override
    public boolean isEmpty()
    {
        return this.items.subList(0, this.size).stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int index)
    {
        return this.items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count)
    {
        if(!this.isAccessible())
        {
            return ItemStack.EMPTY;
        }
        this.reloadIfChanged();
        ItemStack removed = ContainerHelper.removeItem(this.items, index, count);
        if(!removed.isEmpty())
        {
            this.save();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index)
    {
        if(!this.isAccessible())
        {
            return ItemStack.EMPTY;
        }
        this.reloadIfChanged();
        ItemStack removed = ContainerHelper.takeItem(this.items, index);
        this.save();
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack)
    {
        if(!this.isAccessible())
        {
            giveBack(this.player, stack);
            return;
        }
        this.reloadIfChanged();
        this.items.set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.save();
    }

    /**
     * A stack put into a container that can't take it any more (its item is gone or moving) would be lost: it goes
     * back to the player instead. The slots refuse such stacks, so this is only a last resort.
     */
    public static void giveBack(Player player, ItemStack stack)
    {
        if(!stack.isEmpty())
        {
            IntegratedUI.LOGGER.warn("Returned {} put into an inaccessible container item to {}", stack, player.getGameProfile().getName());
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack)
    {
        return index < this.usableSize && ContainerItems.canPlace(this.player, this.stack, index, stack);
    }

    /** Called after a slot's stack was changed in place (e.g. added to), which must be saved too. */
    @Override
    public void setChanged()
    {
        this.save();
    }

    @Override
    public boolean stillValid(Player player)
    {
        // Losing the shulker box reopens the menu rather than closing it
        return true;
    }

    @Override
    public void clearContent()
    {
        if(this.isAccessible())
        {
            for(int i = 0; i < this.size; i++)
            {
                this.items.set(i, ItemStack.EMPTY);
            }
            this.save();
        }
    }

    public enum State
    {
        /** In the inventory: contents can be changed. */
        AVAILABLE,
        /** On the cursor or in the crafting grid: contents are shown but locked. */
        HELD,
        /** No longer with the player. */
        GONE
    }

    /**
     * A shulker box stack and a copy of it taken at the same time. Picking a stack up or moving it splits it into a
     * new stack, leaving the original empty (and so useless for comparing), so the copy recognises the new stack.
     */
    public record Snapshot(ItemStack stack, ItemStack copy)
    {
        public boolean matches(ItemStack other)
        {
            return other.getCount() == 1 && ItemStack.isSameItemSameComponents(other, this.copy);
        }
    }
}
