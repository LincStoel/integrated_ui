package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.compat.EquipmentBackend;
import com.lincstoel.integratedui.compat.backpacked.BackpackedInventoryMenu;
import com.lincstoel.integratedui.compat.quark.QuarkBackpackMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The player's inventory menu with the band's slots (a backpack's, or a container item's such as a shulker box) and any Curios/Accessories
 * slots appended.
 * <p>
 * Extending {@link InventoryMenu} (as Quark's backpack menu does) keeps the 2x2 crafting grid, armor, offhand and
 * recipe book working, and lets other mods recognise this as the player's own inventory. Slot layout:
 * <pre>
 *   0        crafting result      5-8   armor          36-44 hotbar
 *   1-4      crafting grid        9-35  main inventory 45    offhand
 *   46..     band slots (none when the band is empty), then equipment slots
 * </pre>
 * This class has no Backpacked dependency: with nothing in the band it is used as is, a container item such as a
 * shulker box is shown by {@link ShulkerInventoryMenu}, a backpack by Backpacked's {@link BackpackedInventoryMenu}, and a worn
 * Quark backpack by {@link QuarkBackpackMenu}. The band slots are
 * still called backpack slots here, as they mostly hold one.
 */
public class IntegratedInventoryMenu extends InventoryMenu
{
    public static final int RESULT_SLOT = 0;
    public static final int INV_START = 9;
    public static final int HOTBAR_START = 36;
    public static final int HOTBAR_END = 45;
    public static final int OFFHAND_SLOT = 45;
    public static final int BACKPACK_START = 46;

    /** Slots moved here are hidden: off screen and inactive. */
    public static final int HIDDEN = -10000;

    private static final int BUTTON_EQUIPMENT_SHOWN = 0;
    private static final int BUTTON_EQUIPMENT_HIDDEN = 1;
    /** The most ticks between two {@link #updateTargets}, for changes the inventory doesn't count (e.g. pickups, Curios). */
    private static final int UPDATE_INTERVAL = 10;

    private final Player player;
    private final Container backpack;
    private final OpenData data;
    private final int backpackEnd;
    private final int equipmentStart;
    private final int equipmentEnd;
    private final int[] vanillaSlotY;
    /**
     * Where what the band shows is among everything it can show (from 1; 0 if it isn't among them), and how many
     * there are. Server-computed, see {@link #updateTargets}.
     */
    private final DataSlot targetPosition = DataSlot.standalone();
    private final DataSlot targetCount = DataSlot.standalone();
    /** Server side, with an empty band: whether there was anything to show at the last check. */
    @Nullable
    private Boolean hadTargets;
    /** Whether the equipment panel is open on the client. Told by the client, see {@link #equipmentButton}. */
    private boolean equipmentShown = true;
    /** Server side: the player inventory's change count at the last {@link #updateTargets}, and the ticks since. */
    private int lastInventoryChange = -1;
    private int ticksSinceUpdate;

    /** A menu with nothing in the band. */
    public IntegratedInventoryMenu(int windowId, Inventory inventory, OpenData data)
    {
        this(windowId, inventory, new SimpleContainer(0), data, (container, index) -> {
            throw new IllegalStateException("No band slots with an empty band");
        });
    }

    protected IntegratedInventoryMenu(int windowId, Inventory inventory, Container backpack, OpenData data, BackpackSlotFactory backpackSlots)
    {
        super(inventory, !inventory.player.level().isClientSide, inventory.player);
        this.containerId = windowId;
        this.player = inventory.player;
        this.backpack = backpack;
        this.data = data;

        this.vanillaSlotY = new int[this.slots.size()];
        for(int i = 0; i < this.slots.size(); i++)
        {
            this.vanillaSlotY[i] = this.slots.get(i).y;
        }

        checkContainerSize(backpack, data.bandSize());
        backpack.startOpen(this.player);
        for(int i = 0; i < data.bandSize(); i++)
        {
            this.addSlot(backpackSlots.create(backpack, i));
        }
        this.backpackEnd = this.slots.size();

        this.equipmentStart = this.slots.size();
        Equipment.get(data.equipment().backend()).addSlots(this.player, data.equipment().groups(), this::addSlot);
        if(this.slots.size() - this.equipmentStart != data.equipmentSize())
        {
            IntegratedUI.LOGGER.error("Equipment slot count mismatch (expected {}, got {}); inventory may desync",
                data.equipmentSize(), this.slots.size() - this.equipmentStart);
        }
        // Placeholders for any the backend couldn't build here, so slot indexes still line up with the server's
        while(this.slots.size() - this.equipmentStart < data.equipmentSize())
        {
            this.addSlot(new EquipmentBackend.DisabledSlot());
        }
        this.equipmentEnd = this.slots.size();
        this.addDataSlot(this.targetPosition);
        this.addDataSlot(this.targetCount);
    }

    public static IntegratedInventoryMenu fromNetwork(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf)
    {
        OpenData data = OpenData.STREAM_CODEC.decode(buf);
        return switch(data.band())
        {
            case BACKPACK -> BackpackedInventoryMenu.fromNetwork(windowId, inventory, data, buf);
            case CONTAINER_ITEM -> ShulkerInventoryMenu.fromNetwork(windowId, inventory, data, buf);
            case QUARK_BACKPACK -> QuarkBackpackMenu.fromNetwork(windowId, inventory, data, buf);
            case NONE -> new IntegratedInventoryMenu(windowId, inventory, data);
        };
    }

    /**
     * Server side: opens this menu showing what the player last looked at in the band, moved by {@code navigation}
     * (see {@link BandTargets}). With nothing to show, it opens with an empty band.
     */
    public static void open(ServerPlayer player, Navigation navigation)
    {
        BandTargets.open(player, navigation);
    }

    /** Server side: opens this menu with nothing in the band. */
    public static void openWithoutBand(ServerPlayer player)
    {
        OpenData data = OpenData.withoutBand(Equipment.collect(player));
        openMenu(player, data, (id, playerInventory, p) -> new IntegratedInventoryMenu(id, playerInventory, data), buf -> {});
    }

    /** An item for the band header, without its (potentially large) contents. */
    protected static ItemStack createDisplayStack(ItemStack stack)
    {
        ItemStack display = stack.copyWithCount(1);
        display.remove(DataComponents.CONTAINER);
        return display;
    }

    /**
     * Opens the menu, sending {@code data} followed by anything {@code extraData} writes. The screen stays open on
     * the client, and switching from another integrated inventory menu (to show something else in the band) keeps
     * the cursor stack and the crafting grid: the player never left their inventory. Closing it still empties the grid.
     */
    public static void openMenu(ServerPlayer player, OpenData data, MenuConstructor constructor, Consumer<RegistryFriendlyByteBuf> extraData)
    {
        AbstractContainerMenu previous = player.containerMenu;
        // Carry the cursor stack over, like Quark does when swapping inventory menus
        ItemStack carried = previous.getCarried();
        previous.setCarried(ItemStack.EMPTY);
        // Taken out first, so the previous menu has nothing to give back when it is closed
        List<ItemStack> craftingGrid = previous instanceof IntegratedInventoryMenu menu ? menu.takeCraftingGrid() : List.of();
        boolean equipmentShown = !(previous instanceof IntegratedInventoryMenu menu) || menu.equipmentShown;
        player.openMenu(new SeamlessMenuProvider(constructor, data.title()), buf -> {
            OpenData.STREAM_CODEC.encode(buf, data);
            extraData.accept(buf);
        });
        if(player.containerMenu instanceof IntegratedInventoryMenu menu && menu != previous)
        {
            menu.restoreCraftingGrid(craftingGrid);
            menu.equipmentShown = equipmentShown;
            // So the band's header is complete (previous/next, position) from the first frame
            menu.updateTargets(player);
        }
        else
        {
            craftingGrid.forEach(player.getInventory()::placeItemBackInInventory);
        }
        player.containerMenu.setCarried(carried);
        player.containerMenu.broadcastChanges();
    }

    private List<ItemStack> takeCraftingGrid()
    {
        CraftingContainer grid = this.getCraftSlots();
        List<ItemStack> items = new ArrayList<>(grid.getContainerSize());
        for(int i = 0; i < grid.getContainerSize(); i++)
        {
            items.add(grid.removeItemNoUpdate(i));
        }
        return items;
    }

    private void restoreCraftingGrid(List<ItemStack> items)
    {
        CraftingContainer grid = this.getCraftSlots();
        for(int i = 0; i < items.size(); i++)
        {
            if(!items.get(i).isEmpty())
            {
                // Updates the crafting result too
                grid.setItem(i, items.get(i));
            }
        }
    }

    @Override
    public MenuType<?> getType()
    {
        return ModMenus.INTEGRATED_INVENTORY.get();
    }

    public OpenData getData()
    {
        return this.data;
    }

    public int getBackpackIndex()
    {
        return this.data.backpackIndex();
    }

    public int getBackpackEnd()
    {
        return this.backpackEnd;
    }

    /** How many of the band's slots are usable; any others, at the end of the grid, are left out. */
    public int getUsableBandSize()
    {
        return this.data.bandSize();
    }

    public int getEquipmentStart()
    {
        return this.equipmentStart;
    }

    public int getEquipmentEnd()
    {
        return this.equipmentEnd;
    }

    public boolean isEquipmentSlot(int index)
    {
        return index >= this.equipmentStart && index < this.equipmentEnd;
    }

    /** Whether the band's previous button has anywhere to go. Synced from the server. */
    public boolean hasPrevious()
    {
        // Navigation wraps around, so either way leads somewhere whenever there is anything else to show
        return this.targetCount.get() > 1;
    }

    /** Whether the band's next button has anywhere to go. Synced from the server. */
    public boolean hasNext()
    {
        return this.targetCount.get() > 1;
    }

    /** Where what the band shows is among everything it can show, from 1, or 0 if unknown. Synced from the server. */
    public int getTargetPosition()
    {
        return this.targetPosition.get();
    }

    /** How many things the band can show. Synced from the server. */
    public int getTargetCount()
    {
        return this.targetCount.get();
    }

    /** Server side: whether this menu shows the given target in its band. */
    public boolean isShowing(BandTargets.BandTarget target)
    {
        return false;
    }

    /** Server side: whether what the band shows is no longer there to show, so the menu should be reopened. */
    protected boolean isShownTargetGone()
    {
        return true;
    }

    /** Whether quick-moving into the band is currently allowed. */
    protected boolean canMoveToBand()
    {
        return true;
    }

    /**
     * Server side, every tick: whether to {@link #updateTargets} now. Straight away when the inventory changed through
     * a menu, and otherwise every {@link #UPDATE_INTERVAL} ticks, as looking through it isn't free.
     */
    public boolean isUpdateDue(ServerPlayer player)
    {
        int changes = player.getInventory().getTimesChanged();
        if(changes != this.lastInventoryChange || ++this.ticksSinceUpdate >= UPDATE_INTERVAL)
        {
            this.lastInventoryChange = changes;
            this.ticksSinceUpdate = 0;
            return true;
        }
        return false;
    }

    /**
     * Server side: updates the previous/next buttons from what there is to show. Returns true if the menu should be
     * reopened: because what it shows is gone, because something to show turned up while the band was empty, or
     * because the player's Curios/Accessories slots changed.
     */
    public boolean updateTargets(ServerPlayer player)
    {
        if(!Equipment.collect(player).equals(this.data.equipment()))
        {
            return true;
        }
        List<BandTargets.BandTarget> targets = BandTargets.collect(player);
        if(!this.data.hasBand())
        {
            boolean any = !targets.isEmpty();
            boolean appeared = any && Boolean.FALSE.equals(this.hadTargets);
            this.hadTargets = any;
            return appeared;
        }
        int index = BandTargets.indexOf(targets, this::isShowing);
        if(index < 0)
        {
            this.targetPosition.set(0);
            this.targetCount.set(0);
            return this.isShownTargetGone();
        }
        this.targetPosition.set(index + 1);
        this.targetCount.set(targets.size());
        return false;
    }

    /** Client side: pushes the main inventory and hotbar down to make room for the backpack band. */
    public void offsetInventorySlots(int offset)
    {
        for(int i = INV_START; i < HOTBAR_END; i++)
        {
            this.slots.get(i).y = this.vanillaSlotY[i] + offset;
        }
    }

    /** Client side: shows the window of the backpack grid starting at the given column and row. */
    public void scrollBackpack(int gridX, int gridY, int visibleColumns, int visibleRows, int scrollColumn, int scrollRow)
    {
        int columns = this.data.columns();
        for(int i = 0; i < this.data.bandSize(); i++)
        {
            Slot slot = this.slots.get(BACKPACK_START + i);
            int column = i % columns - scrollColumn;
            int row = i / columns - scrollRow;
            boolean shown = column >= 0 && column < visibleColumns && row >= 0 && row < visibleRows;
            if(slot instanceof ScrollingSlot scrolling)
            {
                scrolling.setShown(shown);
            }
            slot.x = shown ? gridX + column * 18 : HIDDEN;
            slot.y = shown ? gridY + row * 18 : HIDDEN;
        }
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.backpack.stillValid(player);
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        this.backpack.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);
        if(!slot.hasItem())
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean fromBackpack = index >= BACKPACK_START && index < this.backpackEnd;

        // Whatever doesn't fit where it goes first spills over into the next place (the band, mostly)
        if(index == RESULT_SLOT)
        {
            if(!moveEither(stack, () -> this.moveItemStackTo(stack, INV_START, HOTBAR_END, true), () -> this.moveToBackpack(stack)))
            {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        }
        else if(index < INV_START || index == OFFHAND_SLOT || this.isEquipmentSlot(index))
        {
            if(!moveEither(stack, () -> this.moveItemStackTo(stack, INV_START, HOTBAR_END, false), () -> this.moveToBackpack(stack)))
            {
                return ItemStack.EMPTY;
            }
        }
        else
        {
            EquipmentSlot target = player.getEquipmentSlotForItem(stack);
            int armorIndex = 8 - target.getIndex();
            if(target.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !this.slots.get(armorIndex).hasItem())
            {
                if(!this.moveItemStackTo(stack, armorIndex, armorIndex + 1, false))
                {
                    return ItemStack.EMPTY;
                }
            }
            else if(target == EquipmentSlot.OFFHAND && !this.slots.get(OFFHAND_SLOT).hasItem())
            {
                if(!this.moveItemStackTo(stack, OFFHAND_SLOT, OFFHAND_SLOT + 1, false))
                {
                    return ItemStack.EMPTY;
                }
            }
            else if(!fromBackpack && this.moveToEquipment(player, stack))
            {
                // Equipped into a Curios/Accessories slot
            }
            else if(fromBackpack)
            {
                if(!this.moveItemStackTo(stack, INV_START, HOTBAR_END, false))
                {
                    return ItemStack.EMPTY;
                }
            }
            else if(index < HOTBAR_START)
            {
                if(!moveEither(stack, () -> this.moveToBackpack(stack), () -> this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)))
                {
                    return ItemStack.EMPTY;
                }
            }
            else if(!moveEither(stack, () -> this.moveItemStackTo(stack, INV_START, HOTBAR_START, false), () -> this.moveToBackpack(stack)))
            {
                return ItemStack.EMPTY;
            }
        }

        if(stack.isEmpty())
        {
            slot.setByPlayer(ItemStack.EMPTY, original);
        }
        else
        {
            slot.setChanged();
        }
        if(stack.getCount() == original.getCount())
        {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if(index == RESULT_SLOT)
        {
            player.drop(stack, false);
        }
        return original;
    }

    /** Moves what fits of the stack with {@code first}, then what is left with {@code second}. Returns whether any of it moved. */
    private static boolean moveEither(ItemStack stack, BooleanSupplier first, BooleanSupplier second)
    {
        boolean moved = first.getAsBoolean();
        return (!stack.isEmpty() && second.getAsBoolean()) || moved;
    }

    private boolean moveToBackpack(ItemStack stack)
    {
        return this.canMoveToBand() && this.moveItemStackTo(stack, BACKPACK_START, this.backpackEnd, false);
    }

    /**
     * Equips the stack into the Curios/Accessories slots the player can see: none while the panel is collapsed (or
     * covered by the recipe book), and none the backend hides (such as unused Accessories slots).
     */
    private boolean moveToEquipment(Player player, ItemStack stack)
    {
        if(!this.equipmentShown || this.equipmentEnd == this.equipmentStart)
        {
            return false;
        }
        Predicate<Slot> shown = Equipment.get(this.data.equipment().backend()).createVisibilityFilter(player);
        boolean moved = false;
        for(int i = this.equipmentStart; i < this.equipmentEnd && !stack.isEmpty(); i++)
        {
            if(shown.test(this.slots.get(i)))
            {
                moved |= this.moveItemStackTo(stack, i, i + 1, false);
            }
        }
        return moved;
    }

    /** Whether the equipment panel is open on the client, so its slots can be quick-moved into. See {@link #clickMenuButton}. */
    public boolean isEquipmentShown()
    {
        return this.equipmentShown;
    }

    /**
     * The menu button the client presses (see {@link #clickMenuButton}) to tell the server whether the equipment
     * panel is open, which only the client knows. Also applied on the client, so its quick-move predictions match.
     */
    public static int equipmentButton(boolean shown)
    {
        return shown ? BUTTON_EQUIPMENT_SHOWN : BUTTON_EQUIPMENT_HIDDEN;
    }

    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if(id == BUTTON_EQUIPMENT_SHOWN || id == BUTTON_EQUIPMENT_HIDDEN)
        {
            this.equipmentShown = id == BUTTON_EQUIPMENT_SHOWN;
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @FunctionalInterface
    protected interface BackpackSlotFactory
    {
        Slot create(Container backpack, int index);
    }

    /** A backpack slot that is told when it is scrolled out of view, so it can deactivate itself. */
    public interface ScrollingSlot
    {
        void setShown(boolean shown);
    }
}
