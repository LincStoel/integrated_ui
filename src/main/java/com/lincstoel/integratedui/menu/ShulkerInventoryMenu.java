package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.compat.Equipment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * The integrated inventory showing a container item (see {@link ContainerItems}), such as a shulker box or a
 * Supplementaries sack, from the player's inventory in the band. Works without Backpacked. The item's contents are
 * only editable while it is in the inventory: while it is on the cursor (or in the crafting grid) its slots are
 * locked, and once it is gone the menu is reopened (showing whatever is next).
 * <p>
 * The slots are laid out in as few rows as fit; any slots left over at the end of the last row are left out.
 */
public class ShulkerInventoryMenu extends IntegratedInventoryMenu
{
    /** Server side only. */
    @Nullable
    private final ShulkerContainer shulkerBox;
    private final DataSlot locked;
    private final int usableSize;
    private final ContainerItems.OwnScreen ownScreen;

    private ShulkerInventoryMenu(int windowId, Inventory inventory, Container container, @Nullable ShulkerContainer shulkerBox, OpenData data, int usableSize,
                                 ContainerItems.OwnScreen ownScreen, DataSlot locked)
    {
        super(windowId, inventory, container, data, (c, index) -> new ShulkerSlot(c, index, index < usableSize,
            shulkerBox != null ? shulkerBox::isAccessible : () -> locked.get() == 0));
        this.shulkerBox = shulkerBox;
        this.locked = locked;
        this.usableSize = usableSize;
        this.ownScreen = ownScreen;
        this.addDataSlot(locked);
    }

    public static ShulkerInventoryMenu fromNetwork(int windowId, Inventory inventory, OpenData data, RegistryFriendlyByteBuf buf)
    {
        int usableSize = buf.readVarInt();
        ContainerItems.OwnScreen ownScreen = ContainerItems.OwnScreen.byId(buf.readVarInt());
        return new ShulkerInventoryMenu(windowId, inventory, new SimpleContainer(data.bandSize()), null, data, usableSize, ownScreen, DataSlot.standalone());
    }

    /** Server side: opens the menu for a container item stack in the player's inventory. */
    public static void open(ServerPlayer player, ItemStack stack)
    {
        int size = ContainerItems.getSize(stack);
        int rows = Mth.positiveCeilDiv(size, ShulkerContainer.COLUMNS);
        int columns = Mth.positiveCeilDiv(size, rows);
        ShulkerContainer container = new ShulkerContainer(player, stack, columns * rows, size);
        ContainerItems.OwnScreen ownScreen = ContainerItems.getOwnScreen(stack, size);
        OpenData data = new OpenData(OpenData.Band.CONTAINER_ITEM, -1, columns, rows, ContainerItems.getTint(stack), stack.getHoverName(),
            createDisplayStack(stack), Equipment.collect(player));
        BandTargets.rememberShulkerBox(player, container.getSnapshot());
        openMenu(player, data, (id, playerInventory, p) -> new ShulkerInventoryMenu(id, playerInventory, container, container, data, size, ownScreen, DataSlot.standalone()),
            buf -> {
                buf.writeVarInt(size);
                buf.writeVarInt(ownScreen.ordinal());
            });
    }

    /** Whether the shown container item can be opened in its own screen, from the band header. */
    public boolean opensOwnScreen()
    {
        return this.ownScreen != ContainerItems.OwnScreen.NONE;
    }

    @Override
    public int getUsableBandSize()
    {
        return this.usableSize;
    }

    /** Whether the shown shulker box's slots are locked, because the box is on the cursor or in the crafting grid. */
    public boolean isLocked()
    {
        return this.locked.get() != 0;
    }

    @Override
    public boolean isShowing(BandTargets.BandTarget target)
    {
        return this.shulkerBox != null && target instanceof BandTargets.BandTarget.ShulkerBox box && box.stack() == this.shulkerBox.getStack();
    }

    @Override
    protected boolean isShownTargetGone()
    {
        // While on the cursor or in the crafting grid the box is still kept, just locked
        return this.shulkerBox == null || this.shulkerBox.getState() == ShulkerContainer.State.GONE;
    }

    @Override
    protected boolean canMoveToBand()
    {
        return this.shulkerBox == null ? !this.isLocked() : this.shulkerBox.isAccessible();
    }

    @Override
    public void broadcastChanges()
    {
        if(this.shulkerBox != null)
        {
            this.locked.set(this.shulkerBox.resolve() == ShulkerContainer.State.AVAILABLE ? 0 : 1);
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        if(this.shulkerBox != null && this.shulkerBox.getState() != ShulkerContainer.State.GONE)
        {
            BandTargets.rememberShulkerBox(player, this.shulkerBox.getSnapshot());
        }
    }

    /** Server side: renames the shown shulker box, then reopens the menu to show the new name. */
    public void rename(ServerPlayer player, String name)
    {
        if(this.shulkerBox == null || !this.shulkerBox.isAccessible())
        {
            return;
        }
        ItemStack stack = this.shulkerBox.getStack();
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

    /** Server side: opens the shown container item in its own screen (see {@link ContainerItems#openOwnScreen}). */
    public void openOwnScreen(ServerPlayer player)
    {
        if(this.shulkerBox != null && this.shulkerBox.isAccessible())
        {
            ContainerItems.openOwnScreen(player, this.shulkerBox.getInventorySlot());
        }
    }

    /**
     * A shulker box slot: no shulker boxes inside, and locked while the box is held elsewhere. A sack's slots left
     * over at the end of the grid don't exist, and are never active.
     */
    public static class ShulkerSlot extends Slot implements ScrollingSlot
    {
        private final boolean exists;
        private final BooleanSupplier usable;
        private boolean shown = true;

        public ShulkerSlot(Container container, int index, boolean exists, BooleanSupplier usable)
        {
            super(container, index, HIDDEN, HIDDEN);
            this.exists = exists;
            this.usable = usable;
        }

        @Override
        public void setShown(boolean shown)
        {
            this.shown = shown;
        }

        @Override
        public boolean isActive()
        {
            return this.shown && this.exists;
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            // No container items inside, whatever they allow themselves: that also keeps the shown one out of its own
            // band. On the server, the container adds the item's own restrictions (such as a sack's)
            return this.exists && this.usable.getAsBoolean() && stack.canFitInsideContainerItems() && !ContainerItems.isContainerItemType(stack)
                && this.container.canPlaceItem(this.getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player)
        {
            return this.exists && this.usable.getAsBoolean();
        }
    }
}
