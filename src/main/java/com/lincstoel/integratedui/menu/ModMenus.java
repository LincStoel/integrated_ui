package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(Registries.MENU, IntegratedUI.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<BackpackInventoryMenu>> BACKPACK_INVENTORY =
        REGISTER.register("backpack_inventory", () -> IMenuTypeExtension.create(BackpackInventoryMenu::fromNetwork));

    private ModMenus() {}
}
