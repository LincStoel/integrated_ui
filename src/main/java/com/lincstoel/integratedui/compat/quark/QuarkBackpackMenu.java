package com.lincstoel.integratedui.compat.quark;

import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.menu.BandTargets;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import com.lincstoel.integratedui.menu.OpenData;
import com.lincstoel.integratedui.menu.ShulkerContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;
import org.violetmoon.quark.addons.oddities.module.BackpackModule;

/**
 * The integrated inventory showing the Quark backpack worn in the chest slot, in place of Quark's own backpack
 * screen. The backpack's 27 slots are read from and written straight back to its {@link DataComponents#CONTAINER}
 * component, as Quark's own menu does. Once the backpack is taken off (Quark only allows that while it is empty),
 * the menu is reopened showing whatever is next. Only used when Quark is installed.
 */
public class QuarkBackpackMenu extends IntegratedInventoryMenu
{
    public static final int COLUMNS = 9;
    public static final int ROWS = 3;

    /** Server side only. */
    @Nullable
    private final BackpackContainer backpack;

    private QuarkBackpackMenu(int windowId, Inventory inventory, Container container, @Nullable BackpackContainer backpack, OpenData data)
    {
        super(windowId, inventory, container, data, BackpackSlot::new);
        this.backpack = backpack;
    }

    public static QuarkBackpackMenu fromNetwork(int windowId, Inventory inventory, OpenData data, RegistryFriendlyByteBuf buf)
    {
        return new QuarkBackpackMenu(windowId, inventory, new SimpleContainer(data.bandSize()), null, data);
    }

    /** Server side: opens the menu for the backpack the player is wearing. Returns false if there is none. */
    public static boolean open(ServerPlayer player)
    {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if(!stack.is(BackpackModule.backpack))
        {
            return false;
        }
        BackpackContainer container = new BackpackContainer(player, stack);
        OpenData data = new OpenData(OpenData.Band.QUARK_BACKPACK, -1, COLUMNS, ROWS, -1,
            stack.getHoverName(), createDisplayStack(stack), Equipment.collect(player));
        openMenu(player, data, (id, playerInventory, p) -> new QuarkBackpackMenu(id, playerInventory, container, container, data), buf -> {});
        return true;
    }

    /** Server side: renames the worn backpack, then reopens the menu to show the new name. */
    public static void rename(ServerPlayer player, String name)
    {
        ItemStack stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if(!stack.is(BackpackModule.backpack))
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
        BandTargets.open(player, Navigation.CURRENT);
    }

    @Override
    public boolean isShowing(BandTargets.BandTarget target)
    {
        return this.backpack != null && this.backpack.isAccessible() && target instanceof BandTargets.BandTarget.QuarkBackpack;
    }

    @Override
    protected boolean isShownTargetGone()
    {
        return this.backpack == null || !this.backpack.isAccessible();
    }

    @Override
    protected boolean canMoveToBand()
    {
        return this.backpack == null || this.backpack.isAccessible();
    }

    private static boolean isBlocked(ItemStack stack)
    {
        return BackpackModule.backpackBlockedTag != null && stack.is(BackpackModule.backpackBlockedTag);
    }

    /** A backpack slot, which takes anything Quark's backpack takes. */
    public static class BackpackSlot extends Slot implements ScrollingSlot
    {
        private boolean shown = true;

        public BackpackSlot(Container container, int index)
        {
            super(container, index, HIDDEN, HIDDEN);
        }

        @Override
        public void setShown(boolean shown)
        {
            this.shown = shown;
        }

        @Override
        public boolean isActive()
        {
            return this.shown;
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return !isBlocked(stack) && this.container.canPlaceItem(this.getContainerSlot(), stack);
        }
    }

    /**
     * Server side: the contents of the backpack worn in the chest slot. Changes are only made while that very stack
     * is still worn.
     */
    private static class BackpackContainer implements Container
    {
        private final Player player;
        private final ItemStack stack;
        private final NonNullList<ItemStack> items = NonNullList.withSize(COLUMNS * ROWS, ItemStack.EMPTY);
        @Nullable
        private ItemContainerContents loadedFrom;

        BackpackContainer(Player player, ItemStack stack)
        {
            this.player = player;
            this.stack = stack;
            this.reloadIfChanged();
        }

        boolean isAccessible()
        {
            return this.player.getItemBySlot(EquipmentSlot.CHEST) == this.stack && !this.stack.isEmpty();
        }

        private void reloadIfChanged()
        {
            ItemContainerContents contents = this.stack.get(DataComponents.CONTAINER);
            if(contents != this.loadedFrom)
            {
                this.loadedFrom = contents;
                this.items.replaceAll(stack -> ItemStack.EMPTY);
                if(contents != null)
                {
                    contents.copyInto(this.items);
                }
            }
        }

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
            return this.items.size();
        }

        @Override
        public boolean isEmpty()
        {
            this.reloadIfChanged();
            return this.items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int index)
        {
            this.reloadIfChanged();
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
                ShulkerContainer.giveBack(this.player, stack);
                return;
            }
            this.reloadIfChanged();
            this.items.set(index, stack);
            stack.limitSize(this.getMaxStackSize(stack));
            this.save();
        }

        @Override
        public boolean canPlaceItem(int index, ItemStack stack)
        {
            return this.isAccessible() && !isBlocked(stack);
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
            // Losing the backpack reopens the menu rather than closing it
            return true;
        }

        @Override
        public void clearContent()
        {
            if(this.isAccessible())
            {
                this.items.replaceAll(stack -> ItemStack.EMPTY);
                this.save();
            }
        }
    }
}
