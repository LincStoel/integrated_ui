package com.lincstoel.integratedui.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An equipment backend whose every call goes through {@link CompatGuard}, as the backends use parts of Curios and
 * Accessories outside their APIs. If one stops linking, the backend acts like {@link EquipmentBackend#NONE}, and the
 * menus pad out the missing slots (see IntegratedInventoryMenu) so both sides still agree.
 */
final class GuardedBackend implements EquipmentBackend
{
    private final String modId;
    private final EquipmentBackend backend;

    GuardedBackend(String modId, EquipmentBackend backend)
    {
        this.modId = modId;
        this.backend = backend;
    }

    @Override
    public List<EquipmentGroup> collectGroups(Player player)
    {
        return CompatGuard.get(this.modId, () -> this.backend.collectGroups(player), List.of());
    }

    @Override
    public void addSlots(Player player, List<EquipmentGroup> groups, Consumer<Slot> adder)
    {
        CompatGuard.run(this.modId, () -> this.backend.addSlots(player, groups, adder));
    }

    @Nullable
    @Override
    public Component getSlotName(Slot slot)
    {
        return CompatGuard.get(this.modId, () -> this.backend.getSlotName(slot), null);
    }

    @Override
    public Component getPanelName()
    {
        return this.backend.getPanelName();
    }

    @Override
    public Predicate<Slot> createVisibilityFilter(Player player)
    {
        Predicate<Slot> filter = CompatGuard.get(this.modId, () -> this.backend.createVisibilityFilter(player), slot -> false);
        return slot -> CompatGuard.test(this.modId, () -> filter.test(slot));
    }

    @Override
    public boolean hasUnusedSlots()
    {
        return this.backend.hasUnusedSlots();
    }

    @Override
    public boolean areUnusedSlotsShown(Player player)
    {
        return CompatGuard.get(this.modId, () -> this.backend.areUnusedSlotsShown(player), true);
    }

    @Override
    public void setUnusedSlotsShown(Player player, boolean shown)
    {
        CompatGuard.run(this.modId, () -> this.backend.setUnusedSlotsShown(player, shown));
    }

    @Override
    public boolean hasFullScreen()
    {
        return CompatGuard.test(this.modId, this.backend::hasFullScreen);
    }

    @Override
    public boolean openFullScreen()
    {
        return CompatGuard.test(this.modId, this.backend::openFullScreen);
    }
}
