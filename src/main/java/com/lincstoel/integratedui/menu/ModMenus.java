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

    public static final DeferredHolder<MenuType<?>, MenuType<IntegratedInventoryMenu>> INTEGRATED_INVENTORY =
        REGISTER.register("integrated_inventory", () -> IMenuTypeExtension.create(IntegratedInventoryMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<PortableShulkerBoxMenu>> PORTABLE_SHULKER_BOX =
        REGISTER.register("portable_shulker_box", () -> IMenuTypeExtension.create(PortableShulkerBoxMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<PortableChestMenu>> PORTABLE_CHEST =
        REGISTER.register("portable_chest", () -> IMenuTypeExtension.create(PortableChestMenu::fromNetwork));

    private ModMenus() {}
}
