package com.lincstoel.integratedui.compat.jei;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.client.IntegratedInventoryScreen;
import com.lincstoel.integratedui.client.IntegratedScreen;
import com.lincstoel.integratedui.compat.backpacked.BackpackedInventoryMenu;
import com.lincstoel.integratedui.compat.backpacked.BackpackedInventoryScreen;
import com.lincstoel.integratedui.compat.quark.QuarkBackpackMenu;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.ModMenus;
import com.lincstoel.integratedui.menu.ShulkerInventoryMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Only loaded by JEI. Keeps JEI's overlays clear of the side panels and lets the "+" button fill the 2x2 grid, taking
 * ingredients from the band as well as the inventory. Every inventory screen and menu is registered, the Backpacked
 * and Quark ones only when those mods are installed. Nothing is registered with the JEI integration turned off in
 * the client config.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin
{
    @Override
    public ResourceLocation getPluginUid()
    {
        return IntegratedUI.rl("jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration)
    {
        if(!IntegratedUI.isJeiEnabled())
        {
            return;
        }
        // JEI matches screens by instanceof, so this also covers the screens' Mouse Tweaks versions
        registerScreen(registration, IntegratedInventoryScreen.class);
        if(IntegratedUI.isBackpackedLoaded())
        {
            registerBackpackedScreen(registration);
        }
    }

    private static void registerBackpackedScreen(IGuiHandlerRegistration registration)
    {
        registerScreen(registration, BackpackedInventoryScreen.class);
    }

    /** {@code type} is one of the {@link IntegratedScreen}s. */
    private static <T extends AbstractContainerScreen<?>> void registerScreen(IGuiHandlerRegistration registration, Class<T> type)
    {
        registration.addGuiContainerHandler(type, new IGuiContainerHandler<T>()
        {
            @Override
            public List<Rect2i> getGuiExtraAreas(T screen)
            {
                return ((IntegratedScreen) screen).parts().getExtraAreas();
            }
        });
        registration.addRecipeClickArea(type, 137, 29, 10, 13, RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration)
    {
        if(!IntegratedUI.isJeiEnabled())
        {
            return;
        }
        // The menu type is shared by all menu classes, but JEI matches transfer handlers on the menu's exact class
        registration.addRecipeTransferHandler(new CraftingTransfer<>(IntegratedInventoryMenu.class));
        registration.addRecipeTransferHandler(new CraftingTransfer<>(ShulkerInventoryMenu.class));
        if(IntegratedUI.isBackpackedLoaded())
        {
            registration.addRecipeTransferHandler(new CraftingTransfer<>(BackpackedInventoryMenu.class));
        }
        if(IntegratedUI.isQuarkLoaded())
        {
            registration.addRecipeTransferHandler(new CraftingTransfer<>(QuarkBackpackMenu.class));
        }
    }

    /**
     * Fills the crafting grid (slots 1-4) from the main inventory and hotbar (slots 9-44), and from whichever band
     * slots can be taken from right now (not while a shown shulker box is held elsewhere, nor Backpacked's locked slots).
     */
    private record CraftingTransfer<C extends IntegratedInventoryMenu>(Class<C> menuClass) implements IRecipeTransferInfo<C, RecipeHolder<CraftingRecipe>>
    {
        @Override
        public Class<? extends C> getContainerClass()
        {
            return this.menuClass;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<MenuType<C>> getMenuType()
        {
            return Optional.of((MenuType<C>) ModMenus.INTEGRATED_INVENTORY.get());
        }

        @Override
        public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType()
        {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public boolean canHandle(C menu, RecipeHolder<CraftingRecipe> recipe)
        {
            return true;
        }

        @Override
        public List<Slot> getRecipeSlots(C menu, RecipeHolder<CraftingRecipe> recipe)
        {
            return new ArrayList<>(menu.slots.subList(1, 5));
        }

        @Override
        public List<Slot> getInventorySlots(C menu, RecipeHolder<CraftingRecipe> recipe)
        {
            List<Slot> slots = new ArrayList<>(menu.slots.subList(IntegratedInventoryMenu.INV_START, IntegratedInventoryMenu.HOTBAR_END));
            for(Slot slot : menu.slots.subList(IntegratedInventoryMenu.BACKPACK_START, menu.getBackpackEnd()))
            {
                if(Minecraft.getInstance().player != null && slot.mayPickup(Minecraft.getInstance().player))
                {
                    slots.add(slot);
                }
            }
            return slots;
        }
    }
}
