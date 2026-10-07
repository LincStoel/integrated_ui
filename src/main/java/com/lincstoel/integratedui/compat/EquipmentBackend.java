package com.lincstoel.integratedui.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Supplies the extra equipment slots (Curios or Accessories) shown in the panel to the left of the inventory.
 */
public interface EquipmentBackend
{
    /** Server side: the slot groups the player currently has, in display order. */
    List<EquipmentGroup> collectGroups(Player player);

    /** Both sides: adds exactly {@code sum(group.size)} slots, in order. Positions are assigned later. */
    void addSlots(Player player, List<EquipmentGroup> groups, Consumer<Slot> adder);

    /** Client side: the name shown when hovering an empty slot of this backend, or null. */
    @Nullable
    Component getSlotName(Slot slot);

    /** Client side: the name of the panel, used in the toggle tooltip. */
    Component getPanelName();

    /**
     * Client side: which of the menu's equipment slots should currently be shown. Computed once per call, so the
     * returned filter can be applied to every slot cheaply.
     */
    default Predicate<Slot> createVisibilityFilter(Player player)
    {
        return slot -> true;
    }

    /** Whether this backend distinguishes unused slots that can be hidden. */
    default boolean hasUnusedSlots()
    {
        return false;
    }

    /** Client side: whether unused slots are currently shown. */
    default boolean areUnusedSlotsShown(Player player)
    {
        return true;
    }

    /** Client side: shows or hides unused slots (synced to the server where the backend stores it). */
    default void setUnusedSlotsShown(Player player, boolean shown) {}

    /** Whether {@link #openFullScreen()} is supported. */
    default boolean hasFullScreen()
    {
        return false;
    }

    /** Client side: opens the backend's own full screen. Returns false if unsupported. */
    boolean openFullScreen();

    EquipmentBackend NONE = new EquipmentBackend()
    {
        @Override
        public List<EquipmentGroup> collectGroups(Player player)
        {
            return List.of();
        }

        @Override
        public void addSlots(Player player, List<EquipmentGroup> groups, Consumer<Slot> adder) {}

        @Override
        public Component getSlotName(Slot slot)
        {
            return null;
        }

        @Override
        public Component getPanelName()
        {
            return Component.empty();
        }

        @Override
        public boolean openFullScreen()
        {
            return false;
        }
    };

    /**
     * Placeholder used when the client cannot find a slot the server reported, so that slot indexes stay
     * aligned between both sides until the menu is reopened.
     */
    class DisabledSlot extends Slot
    {
        public DisabledSlot()
        {
            super(new SimpleContainer(1), 0, 0, 0);
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

        @Override
        public boolean isActive()
        {
            return false;
        }
    }
}
