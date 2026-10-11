package com.lincstoel.integratedui.compat.quark;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.violetmoon.quark.addons.oddities.module.BackpackModule;
import org.violetmoon.quark.base.Quark;
import org.violetmoon.quark.base.client.handler.InventoryButtonHandler;
import org.violetmoon.quark.base.config.QuarkGeneralConfig;
import org.violetmoon.quark.base.handler.SimilarBlockTypeHandler;
import org.violetmoon.quark.content.management.module.ExpandedItemInteractionsModule;
import org.violetmoon.quark.content.management.module.InventorySortingModule;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Client-side Quark integration. Only used when Quark is installed. */
public final class QuarkClientCompat
{
    private QuarkClientCompat() {}

    /**
     * Checks that the Quark methods our mixins hook into (see the mixin.quark package) are still there. The mixins
     * are optional, so a Quark update that renames one doesn't stop the game; this turns the Quark integration off
     * instead, as half of it wouldn't work.
     */
    public static void checkMixinTargets()
    {
        try
        {
            requireMethods(BackpackModule.Client.class, "onOpenGUI", "clientTick");
            requireMethods(BackpackModule.class, "requestBackpack");
            requireMethods(InventoryButtonHandler.class, "initGui", "forGui");
            requireMethods(QuarkGeneralConfig.class, "isScreenAllowed");
        }
        catch(NoSuchMethodException | LinkageError e)
        {
            CompatGuard.markBroken(CompatGuard.QUARK, e);
        }
    }

    private static void requireMethods(Class<?> type, String... names) throws NoSuchMethodException
    {
        Set<String> declared = Arrays.stream(type.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet());
        for(String name : names)
        {
            if(!declared.contains(name))
            {
                throw new NoSuchMethodException(type.getName() + "." + name);
            }
        }
    }

    /**
     * Whether the screen is one of our inventory screens, which Quark should give the buttons and keybinds it gives
     * the vanilla inventory (such as sorting). Nothing is added for the band: its container's own screen, opened
     * from the band header, gets Quark's container buttons. Never, with the Quark integration turned off.
     */
    public static boolean isIntegratedInventory(Object screen)
    {
        return IntegratedUI.isQuarkEnabled() && screen instanceof AbstractContainerScreen<?> containerScreen && containerScreen.getMenu() instanceof IntegratedInventoryMenu;
    }

    /** Whether Quark puts its sort button above the main inventory of the player's inventory screen. */
    public static boolean showsSortButton()
    {
        return Quark.ZETA.modules.isEnabled(InventorySortingModule.class) && InventorySortingModule.enablePlayerInventory;
    }

    /** Whether Quark opens the stack itself when it is right-clicked in the inventory, as it does shulker boxes. */
    public static boolean opensShulkerBox(ItemStack stack)
    {
        return Quark.ZETA.modules.isEnabled(ExpandedItemInteractionsModule.class) && ExpandedItemInteractionsModule.allowOpeningShulkerBoxes
            && SimilarBlockTypeHandler.isShulkerBox(stack);
    }

    /** Opens Quark's backpack inventory if it is worn (and allowed), as Quark does when its held shulker box closes. */
    public static boolean requestBackpackIfWorn(Player player)
    {
        if(QuarkCompat.isWearingQuarkBackpack(player))
        {
            BackpackModule.requestBackpack();
            return true;
        }
        return false;
    }
}
