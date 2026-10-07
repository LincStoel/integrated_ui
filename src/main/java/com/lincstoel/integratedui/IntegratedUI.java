package com.lincstoel.integratedui;

import com.lincstoel.integratedui.compat.curios.CuriosEvents;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.menu.ModMenus;
import com.lincstoel.integratedui.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Shows the selected Backpacked backpack directly inside the survival inventory screen.
 * <p>
 * The approach (replacing the inventory menu with an {@link net.minecraft.world.inventory.InventoryMenu}
 * subclass that carries extra backpack slots) is derived from Quark's backpack by Vazkii, WireSegal et al.
 */
@Mod(IntegratedUI.MOD_ID)
public class IntegratedUI
{
    public static final String MOD_ID = "integrated_ui";
    public static final Logger LOGGER = LogUtils.getLogger();

    public IntegratedUI(IEventBus modBus, ModContainer container)
    {
        ModMenus.REGISTER.register(modBus);
        modBus.addListener(ModNetwork::register);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        if(ModList.get().isLoaded("curios") && !ModList.get().isLoaded("accessories"))
        {
            NeoForge.EVENT_BUS.addListener(CuriosEvents::onSlotModifiersUpdated);
        }
    }

    public static ResourceLocation rl(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
