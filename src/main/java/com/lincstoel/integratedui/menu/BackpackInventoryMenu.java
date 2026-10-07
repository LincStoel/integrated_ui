package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.compat.EquipmentGroup;
import com.lincstoel.integratedui.network.OpenVanillaInventoryPayload;
import com.mrcrayfish.backpacked.BackpackHelper;
import com.mrcrayfish.backpacked.common.Navigate;
import com.mrcrayfish.backpacked.common.backpack.UnlockableSlots;
import com.mrcrayfish.backpacked.core.ModSyncedDataKeys;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.BackpackedInventoryAccess;
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
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;

/**
 * The player's inventory menu with the selected backpack's slots and any Curios/Accessories slots appended.
 * <p>
 * Extending {@link InventoryMenu} (as Quark's backpack menu does) keeps the 2x2 crafting grid, armor, offhand and
 * recipe book working, and lets other mods recognise this as the player's own inventory. Slot layout:
 * <pre>
 *   0        crafting result      5-8   armor          36-44 hotbar
 *   1-4      crafting grid        9-35  main inventory 45    offhand
 *   46..     backpack slots, then equipment slots
 * </pre>
 * Backpack slots are Backpacked's own {@link BackpackSlot}s, so Backpacked's unlock packet and client sync
 * (which operate on any open menu containing {@link UnlockableSlot}s) work here unchanged.
 */
public class BackpackInventoryMenu extends InventoryMenu implements SyncUnlockableSlots
{
    public static final int RESULT_SLOT = 0;
    public static final int INV_START = 9;
    public static final int HOTBAR_START = 36;
    public static final int HOTBAR_END = 45;
    public static final int OFFHAND_SLOT = 45;
    public static final int BACKPACK_START = 46;

    /** Slots moved here are hidden: off screen and inactive. */
    public static final int HIDDEN = -10000;

    private final Player player;
    private final Container backpack;
    private final OpenData data;
    private final InventoryUnlockableController controller;
    private final int backpackEnd;
    private final int equipmentStart;
    private final int equipmentEnd;
    private final int[] vanillaSlotY;

    public BackpackInventoryMenu(int windowId, Inventory inventory, Container backpack, OpenData data)
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

        checkContainerSize(backpack, data.backpackSize());
        backpack.startOpen(this.player);
        this.controller = new InventoryUnlockableController(backpack, data.slots(), List.of(inventory, backpack));
        for(int i = 0; i < data.backpackSize(); i++)
        {
            this.addSlot(new GridSlot(this.controller, backpack, i));
        }
        this.backpackEnd = this.slots.size();

        this.equipmentStart = this.slots.size();
        Equipment.get().addSlots(this.player, data.equipment(), this::addSlot);
        this.equipmentEnd = this.slots.size();
        if(this.equipmentEnd - this.equipmentStart != data.equipmentSize())
        {
            IntegratedUI.LOGGER.error("Equipment slot count mismatch (expected {}, got {}); inventory may desync",
                data.equipmentSize(), this.equipmentEnd - this.equipmentStart);
        }
    }

    public static BackpackInventoryMenu fromNetwork(int windowId, Inventory inventory, RegistryFriendlyByteBuf buf)
    {
        OpenData data = OpenData.STREAM_CODEC.decode(buf);
        return new BackpackInventoryMenu(windowId, inventory, new SimpleContainer(data.backpackSize()), data);
    }

    /**
     * Server side: opens this menu for the player's selected backpack, moved by {@code navigate}. If the player has
     * no backpack anymore, the client is told to open the vanilla inventory instead (it already skipped it).
     */
    public static void open(ServerPlayer player, Navigate navigate)
    {
        int current = player.containerMenu instanceof BackpackInventoryMenu menu ? menu.getBackpackIndex() : BackpackHelper.getSelectedBackpackIndex(player);
        int index = current < 0 ? -1 : BackpackHelper.navigateBackpackIndex(player, current, navigate);
        if(navigate != Navigate.CURRENT && index == current && player.containerMenu instanceof BackpackInventoryMenu)
        {
            return;
        }
        BackpackInventory inventory = index >= 0 ? ((BackpackedInventoryAccess) player).backpacked$GetBackpackInventory(index) : null;
        if(inventory == null || !(inventory.getBackpackStack().getItem() instanceof BackpackItem item))
        {
            if(!(player.containerMenu instanceof BackpackInventoryMenu))
            {
                PacketDistributor.sendToPlayer(player, OpenVanillaInventoryPayload.INSTANCE);
            }
            return;
        }

        ItemStack stack = inventory.getBackpackStack();
        ModSyncedDataKeys.SELECTED_BACKPACK.setValue(player, index);
        UnlockableSlots slots = Objects.requireNonNullElse(item.getUnlockableSlots(stack), UnlockableSlots.ALL);
        boolean hasPrevious = BackpackHelper.navigateBackpackIndex(player, index, Navigate.PREVIOUS) != index;
        boolean hasNext = BackpackHelper.navigateBackpackIndex(player, index, Navigate.NEXT) != index;
        Component title = stack.getHoverName();
        List<EquipmentGroup> equipment = Equipment.get().collectGroups(player);
        OpenData data = new OpenData(index, item.getColumnCount(), item.getRowCount(), slots, hasPrevious, hasNext, title, createDisplayStack(stack), equipment);

        // Carry the cursor stack over, like Quark does when swapping inventory menus
        ItemStack carried = player.containerMenu.getCarried();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.openMenu(new SimpleMenuProvider((id, playerInventory, p) -> new BackpackInventoryMenu(id, playerInventory, inventory, data), title),
            buf -> OpenData.STREAM_CODEC.encode(buf, data));
        player.containerMenu.setCarried(carried);
        player.containerMenu.broadcastChanges();
    }

    /** The backpack for the icon on the "open backpack" button, without its (potentially large) contents. */
    private static ItemStack createDisplayStack(ItemStack stack)
    {
        ItemStack display = stack.copyWithCount(1);
        display.remove(DataComponents.CONTAINER);
        return display;
    }

    @Override
    public MenuType<?> getType()
    {
        return ModMenus.BACKPACK_INVENTORY.get();
    }

    public OpenData getData()
    {
        return this.data;
    }

    public int getBackpackIndex()
    {
        return this.data.backpackIndex();
    }

    public UnlockableController getSlotController()
    {
        return this.controller;
    }

    public int getBackpackEnd()
    {
        return this.backpackEnd;
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
        for(int i = 0; i < this.data.backpackSize(); i++)
        {
            GridSlot slot = (GridSlot) this.slots.get(BACKPACK_START + i);
            int column = i % columns - scrollColumn;
            int row = i / columns - scrollRow;
            slot.shown = column >= 0 && column < visibleColumns && row >= 0 && row < visibleRows;
            slot.x = slot.shown ? gridX + column * 18 : HIDDEN;
            slot.y = slot.shown ? gridY + row * 18 : HIDDEN;
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
    public void handleSyncSlots(ServerPlayer player, List<UnlockableSlot> unlockedSlots)
    {
        List<Integer> indexes = unlockedSlots.stream().map(slot -> slot.index).toList();
        Network.PLAY.sendToPlayer(() -> player, new MessageSyncUnlockSlot(indexes));
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

        if(index == RESULT_SLOT)
        {
            if(!this.moveItemStackTo(stack, INV_START, HOTBAR_END, true) && !this.moveToBackpack(stack))
            {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        }
        else if(index < INV_START || index == OFFHAND_SLOT || this.isEquipmentSlot(index))
        {
            if(!this.moveItemStackTo(stack, INV_START, HOTBAR_END, false) && !this.moveToBackpack(stack))
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
            else if(!fromBackpack && this.equipmentEnd > this.equipmentStart && this.moveItemStackTo(stack, this.equipmentStart, this.equipmentEnd, false))
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
                if(!this.moveToBackpack(stack) && !this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false))
                {
                    return ItemStack.EMPTY;
                }
            }
            else if(!this.moveItemStackTo(stack, INV_START, HOTBAR_START, false) && !this.moveToBackpack(stack))
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

    private boolean moveToBackpack(ItemStack stack)
    {
        return this.moveItemStackTo(stack, BACKPACK_START, this.backpackEnd, false);
    }

    /**
     * A backpack slot that can be scrolled out of view. Hidden slots are moved off screen, so Backpacked's lock
     * overlay (drawn at each slot's position) isn't visible for them either.
     */
    public static class GridSlot extends BackpackSlot
    {
        private boolean shown = true;

        public GridSlot(UnlockableController controller, Container container, int index)
        {
            super(controller, container, index, HIDDEN, HIDDEN);
        }

        public boolean isShown()
        {
            return this.shown;
        }

        @Override
        public boolean isActive()
        {
            return this.shown && super.isActive();
        }
    }
}
