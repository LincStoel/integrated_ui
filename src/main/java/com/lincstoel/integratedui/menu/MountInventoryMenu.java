package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.compat.EquipmentBackend;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The integrated inventory showing the Mount page in the band: the equipment (saddle, armor) and storage of whatever
 * the player rides, taken from the mount's own menu (see {@link Mounts}). That menu is built alongside this one and
 * never opened; its slots, other than the player's inventory, are this menu's band slots, so they behave exactly as in
 * the mount's own screen. The page isn't a grid that scrolls: the client lays its slots out around a portrait of the
 * mount.
 * <p>
 * Once the player dismounts, or the mount's own menu would no longer be valid (for one, a donkey given or relieved of
 * its chest gets a new inventory), the menu is reopened straight away, so nothing goes through the old one.
 */
public class MountInventoryMenu extends IntegratedInventoryMenu
{
    /** The band's colour on this page: saddle leather. */
    public static final int TINT = 0x9C6B3F;

    private final Player viewer;
    /** The mount: on the client, as the client sees it, which may be null if it isn't loaded. */
    @Nullable
    private final Entity vehicle;
    /** The mount's own menu, whose slots these are, or null on the client if it couldn't be built. */
    @Nullable
    private final AbstractContainerMenu shadow;
    private final boolean[] storage;
    private final int storageColumns;

    private MountInventoryMenu(int windowId, Inventory inventory, OpenData data, @Nullable Entity vehicle, @Nullable AbstractContainerMenu shadow,
                               List<Slot> lifted, boolean[] storage, int storageColumns)
    {
        super(windowId, inventory, new SimpleContainer(lifted.size()), data, (container, index) -> lifted.get(index));
        this.viewer = inventory.player;
        this.vehicle = vehicle;
        this.shadow = shadow;
        this.storage = storage;
        this.storageColumns = storageColumns;
    }

    public static MountInventoryMenu fromNetwork(int windowId, Inventory inventory, OpenData data, RegistryFriendlyByteBuf buf)
    {
        Entity vehicle = inventory.player.level().getEntity(buf.readVarInt());
        AbstractContainerMenu shadow = Mounts.readShadow(windowId, inventory, vehicle, buf);
        int count = buf.readVarInt();
        boolean[] storage = new boolean[count];
        for(int i = 0; i < count; i++)
        {
            storage[i] = buf.readBoolean();
        }
        int storageColumns = buf.readVarInt();
        List<Slot> lifted = shadow != null ? Mounts.liftSlots(shadow) : List.of();
        if(lifted.size() != count)
        {
            if(shadow != null)
            {
                IntegratedUI.LOGGER.error("Mount slot count mismatch (expected {}, got {}); its slots are left out", count, lifted.size());
            }
            // Placeholders, so slot indexes still line up with the server's
            lifted = new ArrayList<>(count);
            for(int i = 0; i < count; i++)
            {
                lifted.add(new EquipmentBackend.DisabledSlot());
            }
        }
        return new MountInventoryMenu(windowId, inventory, data, vehicle, shadow, lifted, storage, storageColumns);
    }

    /**
     * Server side: opens the Mount page for the player's vehicle. Returns false if it can't be shown (see {@link
     * Mounts#findShowable}), leaving the open menu as it is.
     */
    public static boolean open(ServerPlayer player)
    {
        Entity vehicle = player.getVehicle();
        if(vehicle == null || !Mounts.isAllowed(vehicle.getType()))
        {
            return false;
        }
        Mounts.Recipe recipe = Mounts.capture(player, vehicle);
        AbstractContainerMenu shadow = recipe != null ? Mounts.createShadow(recipe, player) : null;
        if(shadow == null)
        {
            return false;
        }
        List<Slot> lifted = Mounts.liftSlots(shadow);
        Consumer<RegistryFriendlyByteBuf> clientData;
        try
        {
            clientData = recipe.clientData(shadow, player.registryAccess());
        }
        catch(RuntimeException e)
        {
            IntegratedUI.LOGGER.warn("Couldn't describe the menu of mount {} to the client", vehicle, e);
            clientData = null;
        }
        if(clientData == null || lifted.stream().noneMatch(Slot::isActive))
        {
            shadow.removed(player);
            return false;
        }
        boolean[] storage = Mounts.findStorage(lifted, recipe instanceof Mounts.Recipe.Horse);
        int storageColumns = Mounts.countStorageColumns(lifted, storage);
        ItemStack pick = vehicle.getPickResult();
        ItemStack icon = pick == null || pick.isEmpty() ? new ItemStack(Items.SADDLE) : pick.copyWithCount(1);
        OpenData data = new OpenData(OpenData.Band.MOUNT, -1, lifted.size(), 1, TINT, vehicle.getDisplayName(), icon, Equipment.collect(player));
        Consumer<RegistryFriendlyByteBuf> recipeData = clientData;
        openMenu(player, data, (id, playerInventory, p) -> new MountInventoryMenu(id, playerInventory, data, vehicle, shadow, lifted, storage, storageColumns),
            buf -> {
                buf.writeVarInt(vehicle.getId());
                recipeData.accept(buf);
                buf.writeVarInt(lifted.size());
                for(boolean isStorage : storage)
                {
                    buf.writeBoolean(isStorage);
                }
                buf.writeVarInt(storageColumns);
            });
        if(!(player.containerMenu instanceof MountInventoryMenu menu) || menu.shadow != shadow)
        {
            shadow.removed(player);
            return false;
        }
        return true;
    }

    /** The mount, or null on the client if it isn't loaded. */
    @Nullable
    public Entity getVehicle()
    {
        return this.vehicle;
    }

    /** The page's {@code index}th slot (from 0). */
    public Slot getMountSlot(int index)
    {
        return this.slots.get(this.getBackpackStart() + index);
    }

    public int getMountSlotCount()
    {
        return this.getBackpackEnd() - this.getBackpackStart();
    }

    /** Whether the page's {@code index}th slot is storage, rather than equipment such as a saddle. */
    public boolean isStorage(int index)
    {
        return index >= 0 && index < this.storage.length && this.storage[index];
    }

    /** How many columns the mount's own screen shows its storage in. */
    public int getStorageColumns()
    {
        return this.storageColumns;
    }

    /**
     * Server side: whether the page shows what it no longer can, as the player dismounted or the mount's own menu
     * would have closed.
     */
    private boolean isGone()
    {
        return this.vehicle == null || this.shadow == null || this.viewer.getVehicle() != this.vehicle || !this.vehicle.isAlive()
            || !this.shadow.stillValid(this.viewer);
    }

    @Override
    public boolean isShowing(BandTargets.BandTarget target)
    {
        return target instanceof BandTargets.BandTarget.Mount mount && mount.vehicle() == this.vehicle && !this.isGone();
    }

    @Override
    public boolean isUpdateDue(ServerPlayer player)
    {
        // Every tick, so nothing goes through a mount's menu that is no longer valid
        if(this.isGone())
        {
            Mounts.forget(player);
            return true;
        }
        return super.isUpdateDue(player);
    }

    /** Fills the storage only, and only its slots in use: equipment is filled first, see {@link #moveToBandEquipment}. */
    @Override
    protected boolean moveToBand(ItemStack stack)
    {
        if(!this.isBandShown())
        {
            return false;
        }
        boolean moved = false;
        // Onto stacks of the same item first, then into empty slots
        for(int pass = 0; pass < 2 && !stack.isEmpty(); pass++)
        {
            for(int i = 0; i < this.getMountSlotCount() && !stack.isEmpty(); i++)
            {
                Slot slot = this.getMountSlot(i);
                if(this.isStorage(i) && slot.isActive() && slot.hasItem() == (pass == 0))
                {
                    moved |= this.moveItemStackTo(stack, slot.index, slot.index + 1, false);
                }
            }
        }
        return moved;
    }

    /** Saddles, armor and the like go onto the mount first, as in its own screen. */
    @Override
    protected boolean moveToBandEquipment(ItemStack stack)
    {
        if(!this.isBandShown())
        {
            return false;
        }
        for(int i = 0; i < this.getMountSlotCount(); i++)
        {
            Slot slot = this.getMountSlot(i);
            if(!this.isStorage(i) && slot.isActive() && !slot.hasItem() && slot.mayPlace(stack)
                && this.moveItemStackTo(stack, slot.index, slot.index + 1, false))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        if(this.shadow != null)
        {
            this.shadow.removed(player);
        }
    }

    /**
     * Server side: opens the mount's own screen, as the inventory key would without the band, returning here when it
     * closes (see OpenFromInventory on the client).
     */
    public void openOwnScreen(ServerPlayer player)
    {
        if(this.isGone())
        {
            return;
        }
        Entity mount = this.vehicle;
        ItemStack carried = this.getCarried();
        this.setCarried(ItemStack.EMPTY);
        SuspendedMenus.open(player, () -> Mounts.openOwnScreen(player, mount));
        if(player.containerMenu == player.inventoryMenu)
        {
            // It didn't open after all: back to the inventory, which the client still shows
            player.getInventory().placeItemBackInInventory(carried);
            BandTargets.open(player, Navigation.CURRENT);
            return;
        }
        player.containerMenu.setCarried(carried);
        player.containerMenu.broadcastChanges();
    }
}
