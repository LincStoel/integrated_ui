package com.lincstoel.integratedui.compat.jei;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.client.BackpackInventoryScreen;
import com.lincstoel.integratedui.menu.BackpackInventoryMenu;
import com.lincstoel.integratedui.menu.ModMenus;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Only loaded by JEI. Keeps JEI's overlays clear of the side panels and lets the "+" button fill the 2x2 grid.
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
        registration.addGuiContainerHandler(BackpackInventoryScreen.class, new IGuiContainerHandler<>()
        {
            @Override
            public List<Rect2i> getGuiExtraAreas(BackpackInventoryScreen screen)
            {
                return screen.getExtraAreas();
            }
        });
        registration.addRecipeClickArea(BackpackInventoryScreen.class, 137, 29, 10, 13, RecipeTypes.CRAFTING);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration)
    {
        // Crafting grid is slots 1-4; the player's main inventory and hotbar are slots 9-44
        registration.addRecipeTransferHandler(BackpackInventoryMenu.class, ModMenus.BACKPACK_INVENTORY.get(), RecipeTypes.CRAFTING,
            1, 4, BackpackInventoryMenu.INV_START, 36);
    }
}
