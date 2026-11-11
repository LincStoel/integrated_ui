package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.compat.Equipment;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The integrated inventory showing the Bundles page in the band: a slot for each bundle (see {@link Bundles}) the
 * player carries, in the inventory or in a Curios/Accessories slot. Unlike the band's other pages, these slots don't
 * hold anything themselves: each shows the bundle in another of the menu's slots, and clicking it takes the bundle's
 * selected item out (or puts the held item in) rather than picking up the bundle. The selection is moved with the
 * mouse wheel on the client, and sent to the server (see SelectBundleItemPayload).
 * <p>
 * Once a bundle is moved, added or gone, the menu is reopened with the new set (see {@link #isShowing}).
 */
public class BundleInventoryMenu extends IntegratedInventoryMenu
{
    /** The band's colour on this page: a bundle's leather. */
    public static final int TINT = 0xB07B4F;

    private final BundleSources sources;
    private final int bundleCount;
    /** The selected item of each slot's bundle, as an index into its contents (clamped when used). Both sides. */
    private final int[] selected;

    private BundleInventoryMenu(int windowId, Inventory inventory, OpenData data, List<Bundles.Location> locations, BundleSources sources)
    {
        super(windowId, inventory, sources, data, (container, index) -> new BundleSlot(container, index, index < locations.size()));
        this.sources = sources;
        this.bundleCount = locations.size();
        this.selected = new int[data.bandSize()];
        int[] menuSlots = new int[locations.size()];
        for(int i = 0; i < menuSlots.length; i++)
        {
            menuSlots[i] = locations.get(i).toMenuSlot(this);
        }
        sources.bind(this.slots, menuSlots);
    }

    public static BundleInventoryMenu fromNetwork(int windowId, Inventory inventory, OpenData data, RegistryFriendlyByteBuf buf)
    {
        List<Bundles.Location> locations = Bundles.Location.LIST_STREAM_CODEC.decode(buf);
        return new BundleInventoryMenu(windowId, inventory, data, locations, new BundleSources(data.bandSize()));
    }

    /** Server side: opens the Bundles page for the bundles found. Returns false if there are none. */
    public static boolean open(ServerPlayer player, List<Bundles.Found> bundles)
    {
        if(bundles.isEmpty())
        {
            return false;
        }
        List<Bundles.Location> locations = bundles.stream().map(Bundles.Found::location).toList();
        int columns = Math.min(bundles.size(), ShulkerContainer.COLUMNS);
        int rows = Mth.positiveCeilDiv(bundles.size(), columns);
        ItemStack icon = Bundles.emptied(bundles.getFirst().stack());
        OpenData data = new OpenData(OpenData.Band.BUNDLES, -1, columns, rows, TINT, Component.translatable("integrated_ui.gui.bundles"), icon,
            Equipment.collect(player));
        openMenu(player, data, (id, playerInventory, p) -> new BundleInventoryMenu(id, playerInventory, data, locations, new BundleSources(data.bandSize())),
            buf -> Bundles.Location.LIST_STREAM_CODEC.encode(buf, locations));
        return true;
    }

    @Override
    public int getUsableBandSize()
    {
        return this.bundleCount;
    }

    /** Which of the page's slots this menu slot is (from 0), or -1 if it isn't one with a bundle place behind it. */
    public int getBundleIndex(Slot slot)
    {
        int index = slot.index - this.getBackpackStart();
        return index >= 0 && index < this.bundleCount ? index : -1;
    }

    /** The bundle shown in the given page slot, or empty. */
    public ItemStack getBundle(int index)
    {
        return this.sources.getItem(index);
    }

    /** The menu slot the given page slot's bundle is really in (in the inventory or an equipment slot), or null. */
    @Nullable
    public Slot getSourceSlot(int index)
    {
        return this.sources.getSource(index);
    }

    /** The index of the selected item in the given page slot's bundle. */
    public int getSelected(int index)
    {
        int size = Bundles.getContents(this.getBundle(index)).size();
        return index >= 0 && index < this.selected.length && size > 0 ? Math.min(this.selected[index], size - 1) : 0;
    }

    /** The selected item in the given page slot's bundle, or empty. Not to be changed. */
    public ItemStack getSelectedItem(int index)
    {
        return Bundles.getItem(this.getBundle(index), this.getSelected(index));
    }

    /**
     * How much of the selected stack one click takes: all of it, or a full stack if it is larger (as a seed pouch's
     * contents can be).
     */
    private int getTakeableCount(int index)
    {
        ItemStack item = this.getSelectedItem(index);
        return Math.min(item.getCount(), item.getMaxStackSize());
    }

    /** Selects an item in the given page slot's bundle. */
    public void select(int index, int item)
    {
        if(index >= 0 && index < this.selected.length)
        {
            this.selected[index] = Math.max(0, item);
        }
    }

    /**
     * Client side: moves the selection in the given page slot's bundle by {@code step}, wrapping around. Returns the
     * new selection, or -1 if the bundle is empty.
     */
    public int stepSelection(int index, int step)
    {
        int size = Bundles.getContents(this.getBundle(index)).size();
        if(size == 0)
        {
            return -1;
        }
        int item = Math.floorMod(this.getSelected(index) + step, size);
        this.select(index, item);
        return item;
    }

    @Override
    public boolean isShowing(BandTargets.BandTarget target)
    {
        if(!(target instanceof BandTargets.BandTarget.BundlePage bundles) || bundles.found().size() != this.bundleCount)
        {
            return false;
        }
        for(int i = 0; i < this.bundleCount; i++)
        {
            Slot source = this.sources.getSource(i);
            if(source == null || source.getItem() != bundles.found().get(i).stack())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean isShownTargetGone()
    {
        // While a bundle is on the cursor it is only being moved: the page is redone once it is put down
        return !Bundles.isBundle(this.getCarried());
    }

    @Override
    protected boolean canMoveToBand()
    {
        return false;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Clicks on the page's slots: run on both sides, the client predicting what the server does

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player)
    {
        if(slotId < this.getBackpackStart() || slotId >= this.getBackpackEnd())
        {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        int index = slotId - this.getBackpackStart();
        Slot source = this.sources.getSource(index);
        if(source == null || !Bundles.isBundle(source.getItem()) || !source.allowModification(player))
        {
            return;
        }
        ItemStack bundle = source.getItem();
        boolean changed = switch(clickType)
        {
            case PICKUP -> this.getCarried().isEmpty() ? this.takeSelected(player, bundle, index, button == 0) : this.insertCarried(player, bundle, source, index, button == 0);
            case QUICK_MOVE -> this.moveSelectedToInventory(player, bundle, index);
            case THROW -> this.throwSelected(player, bundle, index, button == 1);
            case CLONE -> {
                ItemStack item = this.getSelectedItem(index);
                if(player.hasInfiniteMaterials() && this.getCarried().isEmpty() && !item.isEmpty())
                {
                    this.setCarried(item.copyWithCount(item.getMaxStackSize()));
                }
                yield false;
            }
            // Number keys, double-click collecting and drag-spreading don't apply to a bundle's contents
            default -> false;
        };
        if(changed)
        {
            source.setChanged();
        }
    }

    /** Takes the selected stack (or half of it) out of the bundle onto the cursor. */
    private boolean takeSelected(Player player, ItemStack bundle, int index, boolean whole)
    {
        int count = this.getTakeableCount(index);
        ItemStack taken = Bundles.take(player, bundle, this.getSelected(index), whole ? count : (count + 1) / 2);
        if(taken.isEmpty())
        {
            return false;
        }
        this.setCarried(taken);
        return true;
    }

    /** Puts the held stack (or one of it) into the bundle, selecting it there. */
    private boolean insertCarried(Player player, ItemStack bundle, Slot source, int index, boolean whole)
    {
        ItemStack carried = this.getCarried();
        int inserted;
        if(whole)
        {
            inserted = Bundles.insert(player, bundle, carried, source, SlotAccess.of(this::getCarried, this::setCarried));
        }
        else
        {
            // The single item stands in for the cursor, so a bundle setting the cursor sets only that
            ItemStack[] one = {carried.copyWithCount(1)};
            inserted = Bundles.insert(player, bundle, one[0], source, SlotAccess.of(() -> one[0], stack -> one[0] = stack));
            if(inserted >= 0)
            {
                carried.shrink(1);
            }
        }
        if(inserted < 0)
        {
            return false;
        }
        this.select(index, inserted);
        return true;
    }

    /** Moves what fits of the selected stack into the main inventory and hotbar. */
    private boolean moveSelectedToInventory(Player player, ItemStack bundle, int index)
    {
        ItemStack moving = this.getSelectedItem(index).copyWithCount(this.getTakeableCount(index));
        int before = moving.getCount();
        if(moving.isEmpty() || !this.moveItemStackTo(moving, INV_START, HOTBAR_END, false))
        {
            return false;
        }
        Bundles.take(player, bundle, this.getSelected(index), before - moving.getCount());
        return true;
    }

    /** Drops one of the selected stack, or all of it. */
    private boolean throwSelected(Player player, ItemStack bundle, int index, boolean whole)
    {
        if(!this.getCarried().isEmpty())
        {
            return false;
        }
        ItemStack taken = Bundles.take(player, bundle, this.getSelected(index), whole ? this.getTakeableCount(index) : 1);
        if(taken.isEmpty())
        {
            return false;
        }
        if(!player.level().isClientSide)
        {
            player.drop(taken, true);
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------

    /**
     * The page's "container": each of its slots shows the bundle in one of the menu's other slots, or nothing once
     * something else is there. Nothing can be put in or taken out through it; clicks are handled by the menu.
     */
    private static final class BundleSources implements Container
    {
        private final int size;
        @Nullable
        private List<Slot> slots;
        private int[] menuSlots = new int[0];

        BundleSources(int size)
        {
            this.size = size;
        }

        void bind(List<Slot> slots, int[] menuSlots)
        {
            this.slots = slots;
            this.menuSlots = menuSlots;
        }

        @Nullable
        Slot getSource(int index)
        {
            if(this.slots == null || index < 0 || index >= this.menuSlots.length)
            {
                return null;
            }
            int slot = this.menuSlots[index];
            return slot >= 0 && slot < this.slots.size() ? this.slots.get(slot) : null;
        }

        @Override
        public int getContainerSize()
        {
            return this.size;
        }

        @Override
        public boolean isEmpty()
        {
            for(int i = 0; i < this.size; i++)
            {
                if(!this.getItem(i).isEmpty())
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index)
        {
            Slot source = this.getSource(index);
            if(source == null)
            {
                return ItemStack.EMPTY;
            }
            ItemStack stack = source.getItem();
            return Bundles.isBundle(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int index, int count)
        {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index)
        {
            return ItemStack.EMPTY;
        }

        /** Ignored: the bundle is synced through the slot it is really in. */
        @Override
        public void setItem(int index, ItemStack stack) {}

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player)
        {
            return true;
        }

        @Override
        public void clearContent() {}
    }

    /** A slot of the page: shows a bundle, and is never placed into or picked up from the way other slots are. */
    public static class BundleSlot extends Slot implements ScrollingSlot
    {
        private final boolean exists;
        private boolean shown = true;

        BundleSlot(Container container, int index, boolean exists)
        {
            super(container, index, HIDDEN, HIDDEN);
            this.exists = exists;
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
            return false;
        }

        @Override
        public boolean mayPickup(Player player)
        {
            return false;
        }

        /** Never set from the client's syncing: the bundle comes in through the slot it is really in. */
        @Override
        public void set(ItemStack stack) {}
    }
}
