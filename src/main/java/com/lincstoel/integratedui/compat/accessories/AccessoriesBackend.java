package com.lincstoel.integratedui.compat.accessories;

import com.lincstoel.integratedui.compat.EquipmentBackend;
import com.lincstoel.integratedui.compat.EquipmentGroup;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.AccessoriesHolder;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.SlotGroupLoader;
import io.wispforest.accessories.menu.AccessoriesInternalSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Builds the same accessory slots as Accessories' own screen (AccessoriesExperimentalMenu): one non-cosmetic
 * {@link AccessoriesInternalSlot} per slot of every slot type in the entity's valid slot groups, in group order.
 * Unused slot types are hidden using Accessories' own per-player "show unused slots" setting.
 */
public class AccessoriesBackend implements EquipmentBackend
{
    @Override
    public List<EquipmentGroup> collectGroups(Player player)
    {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if(capability == null)
        {
            return List.of();
        }
        Map<String, AccessoriesContainer> containers = capability.getContainers();
        List<EquipmentGroup> groups = new ArrayList<>();
        for(SlotType type : getSlotTypes(player))
        {
            AccessoriesContainer container = containers.get(type.name());
            if(container != null && container.slotType() != null && container.getSize() > 0)
            {
                groups.add(new EquipmentGroup(type.name(), container.getSize()));
            }
        }
        return groups;
    }

    private static Collection<SlotType> getSlotTypes(Player player)
    {
        Set<SlotType> types = new LinkedHashSet<>();
        SlotGroupLoader.getValidGroups(player).values().forEach(types::addAll);
        return types;
    }

    @Override
    public void addSlots(Player player, List<EquipmentGroup> groups, Consumer<Slot> adder)
    {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        Map<String, AccessoriesContainer> containers = capability != null ? capability.getContainers() : Map.of();
        for(EquipmentGroup group : groups)
        {
            AccessoriesContainer container = containers.get(group.id());
            for(int i = 0; i < group.size(); i++)
            {
                if(container != null && i < container.getSize())
                {
                    adder.accept(new AccessoriesInternalSlot(container, false, i, 0, 0));
                }
                else
                {
                    adder.accept(new DisabledSlot());
                }
            }
        }
    }

    @Override
    public Predicate<Slot> createVisibilityFilter(Player player)
    {
        if(this.areUnusedSlotsShown(player))
        {
            return slot -> slot instanceof AccessoriesBasedSlot;
        }
        Collection<SlotType> used = AccessoriesAPI.getUsedSlotsFor(player);
        return slot -> slot instanceof AccessoriesBasedSlot accessorySlot && used.contains(accessorySlot.slotType());
    }

    @Override
    public boolean hasUnusedSlots()
    {
        return true;
    }

    @Override
    public boolean areUnusedSlotsShown(Player player)
    {
        AccessoriesHolder holder = AccessoriesHolder.get(player);
        return holder != null && holder.showUnusedSlots();
    }

    @Override
    public void setUnusedSlotsShown(Player player, boolean shown)
    {
        AccessoriesClientAccess.setUnusedSlotsShown(player, shown);
    }

    @Override
    public Component getSlotName(Slot slot)
    {
        if(slot instanceof AccessoriesBasedSlot accessorySlot)
        {
            return Component.translatable(accessorySlot.slotType().translation());
        }
        return null;
    }

    @Override
    public Component getPanelName()
    {
        return Component.translatable("integrated_ui.gui.accessories");
    }

    @Override
    public boolean hasFullScreen()
    {
        return true;
    }

    @Override
    public boolean openFullScreen()
    {
        return AccessoriesClientAccess.openScreen();
    }
}
