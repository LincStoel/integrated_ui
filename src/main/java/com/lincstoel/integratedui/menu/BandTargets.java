package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.backpacked.BackpackedInventoryMenu;
import com.lincstoel.integratedui.compat.quark.QuarkBackpackMenu;
import com.lincstoel.integratedui.compat.quark.QuarkCompat;
import com.lincstoel.integratedui.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Server side: everything the band can show, in the order the previous/next buttons step through it. Equipped
 * backpacks come first (in Backpacked's order), then a worn Quark backpack, then the container items (see
 * {@link ContainerItems}) in the player's inventory. Container items are grouped by kind (by block entity type, so
 * shulker boxes come before Supplementaries sacks), then by colour (undyed first, then in dye order) and otherwise
 * in the order they appear in the inventory screen.
 */
public final class BandTargets
{
    // By player UUID rather than player: respawning (or leaving the End) replaces the player, but not what they looked at

    /** The container item each player last looked at, so reopening the inventory shows it again. */
    private static final Map<UUID, ShulkerContainer.Snapshot> LAST_SHULKER_BOX = new HashMap<>();
    /** The players who last looked at their Quark backpack, so reopening the inventory shows it again. */
    private static final Set<UUID> LAST_QUARK_BACKPACK = new HashSet<>();

    private BandTargets() {}

    public static List<BandTarget> collect(ServerPlayer player)
    {
        List<BandTarget> targets = new ArrayList<>();
        if(IntegratedUI.isBackpackedEnabled())
        {
            for(int index : CompatGuard.get(CompatGuard.BACKPACKED, () -> BackpackedInventoryMenu.getEquippedBackpacks(player), new int[0]))
            {
                targets.add(new BandTarget.Backpack(index));
            }
        }
        if(IntegratedUI.isQuarkEnabled() && CompatGuard.test(CompatGuard.QUARK, () -> QuarkCompat.isWearingQuarkBackpack(player)))
        {
            targets.add(new BandTarget.QuarkBackpack());
        }
        if(!ServerConfig.get(ServerConfig.CONTAINER_ITEMS_IN_BAND))
        {
            return targets;
        }
        List<BandTarget.ShulkerBox> containerItems = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for(int i = 0; i < ShulkerContainer.INVENTORY_ORDER.length; i++)
        {
            ItemStack stack = inventory.getItem(ShulkerContainer.INVENTORY_ORDER[i]);
            if(ContainerItems.isContainerItem(stack))
            {
                containerItems.add(new BandTarget.ShulkerBox(stack, ContainerItems.getGroupKey(stack), ContainerItems.getColorOrder(stack), i));
            }
        }
        containerItems.sort(Comparator.comparing(BandTarget.ShulkerBox::group).thenComparingInt(BandTarget.ShulkerBox::colorOrder)
            .thenComparingInt(BandTarget.ShulkerBox::position));
        targets.addAll(containerItems);
        return targets;
    }

    public static int indexOf(List<BandTarget> targets, Predicate<BandTarget> predicate)
    {
        for(int i = 0; i < targets.size(); i++)
        {
            if(predicate.test(targets.get(i)))
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * Opens the integrated inventory for what is currently shown (or was last shown), moved by {@code navigation}.
     * With nothing to show it opens without a band, unless {@code navigation} asked to move, which does nothing.
     */
    public static void open(ServerPlayer player, Navigation navigation)
    {
        List<BandTarget> targets = collect(player);
        int current = findCurrent(player, targets);
        int index = current;
        if(navigation != Navigation.CURRENT && current >= 0)
        {
            // Wraps around: next from the last goes to the first, and previous from the first to the last
            index = Math.floorMod(current + (navigation == Navigation.NEXT ? 1 : -1), targets.size());
        }
        if(navigation != Navigation.CURRENT && index == current && player.containerMenu instanceof IntegratedInventoryMenu)
        {
            return;
        }
        if(index >= 0 && openTarget(player, targets.get(index)))
        {
            return;
        }
        if(navigation == Navigation.CURRENT)
        {
            IntegratedInventoryMenu.openWithoutBand(player);
        }
    }

    /**
     * What was last looked at is only updated once the new menu is open: opening it closes the previous one, and a
     * closing shulker box menu remembers its box.
     */
    private static boolean openTarget(ServerPlayer player, BandTarget target)
    {
        if(target instanceof BandTarget.ShulkerBox shulkerBox)
        {
            ShulkerInventoryMenu.open(player, shulkerBox.stack());
            LAST_QUARK_BACKPACK.remove(player.getUUID());
            return true;
        }
        boolean quark = target instanceof BandTarget.QuarkBackpack;
        boolean opened = quark ? CompatGuard.test(CompatGuard.QUARK, () -> QuarkBackpackMenu.open(player))
            : CompatGuard.test(CompatGuard.BACKPACKED, () -> BackpackedInventoryMenu.open(player, ((BandTarget.Backpack) target).index()));
        if(opened)
        {
            LAST_SHULKER_BOX.remove(player.getUUID());
            if(quark)
            {
                LAST_QUARK_BACKPACK.add(player.getUUID());
            }
            else
            {
                LAST_QUARK_BACKPACK.remove(player.getUUID());
            }
        }
        return opened;
    }

    /**
     * The target shown in the open menu, else the shulker box or Quark backpack last looked at, else Backpacked's
     * selected backpack.
     */
    private static int findCurrent(ServerPlayer player, List<BandTarget> targets)
    {
        if(targets.isEmpty())
        {
            return -1;
        }
        if(player.containerMenu instanceof IntegratedInventoryMenu menu)
        {
            int index = indexOf(targets, menu::isShowing);
            if(index >= 0)
            {
                return index;
            }
        }
        ShulkerContainer.Snapshot last = LAST_SHULKER_BOX.get(player.getUUID());
        if(last != null)
        {
            int index = indexOf(targets, target -> target instanceof BandTarget.ShulkerBox box && box.stack() == last.stack());
            if(index < 0)
            {
                index = indexOf(targets, target -> target instanceof BandTarget.ShulkerBox box && last.matches(box.stack()));
            }
            if(index >= 0)
            {
                return index;
            }
        }
        if(LAST_QUARK_BACKPACK.contains(player.getUUID()))
        {
            int index = indexOf(targets, target -> target instanceof BandTarget.QuarkBackpack);
            if(index >= 0)
            {
                return index;
            }
        }
        if(IntegratedUI.isBackpackedEnabled())
        {
            int selected = CompatGuard.get(CompatGuard.BACKPACKED, () -> BackpackedInventoryMenu.getSelectedBackpack(player), -1);
            int index = indexOf(targets, target -> target instanceof BandTarget.Backpack backpack && backpack.index() == selected);
            if(index >= 0)
            {
                return index;
            }
        }
        return 0;
    }

    /** Remembers the shulker box a player looked at, so it is shown again when they next open their inventory. */
    static void rememberShulkerBox(Player player, @Nullable ShulkerContainer.Snapshot snapshot)
    {
        // A leaving player's menu is closed after they are forgotten
        if(snapshot != null && !(player instanceof ServerPlayer serverPlayer && serverPlayer.hasDisconnected()))
        {
            LAST_SHULKER_BOX.put(player.getUUID(), snapshot);
        }
    }

    /** Forgets what the player looked at, once they leave. */
    static void forget(Player player)
    {
        LAST_SHULKER_BOX.remove(player.getUUID());
        LAST_QUARK_BACKPACK.remove(player.getUUID());
    }

    static void forgetAll()
    {
        LAST_SHULKER_BOX.clear();
        LAST_QUARK_BACKPACK.clear();
    }

    /** Something the band can show. */
    public sealed interface BandTarget
    {
        /** A backpack equipped through Backpacked, by its Backpacked index. */
        record Backpack(int index) implements BandTarget {}

        /** The Quark backpack worn in the chest slot. */
        record QuarkBackpack() implements BandTarget {}

        /**
         * A container item, such as a shulker box or Supplementaries sack, in the player's inventory. {@code stack}
         * is the very stack in the inventory; the other fields only order the list.
         */
        record ShulkerBox(ItemStack stack, String group, int colorOrder, int position) implements BandTarget {}
    }
}
