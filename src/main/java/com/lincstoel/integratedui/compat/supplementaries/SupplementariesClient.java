package com.lincstoel.integratedui.compat.supplementaries;

import com.lincstoel.integratedui.client.PortableScreens;
import net.mehvahdjukaar.supplementaries.client.screens.VariableSizeContainerScreen;
import net.mehvahdjukaar.supplementaries.common.inventories.VariableSizeContainerMenu;
import net.mehvahdjukaar.supplementaries.reg.ModTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client side of showing a sack in Supplementaries' sack screen, see {@link SupplementariesCompat}. Only used when
 * Supplementaries is installed.
 */
public final class SupplementariesClient
{
    private SupplementariesClient() {}

    public static void register(IEventBus modBus)
    {
        modBus.addListener(SupplementariesClient::onRegisterScreens);
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event)
    {
        // Supplementaries' screen is typed to its base menu, so the menu type's own menu is cast back
        event.<VariableSizeContainerMenu, PortableSackScreen>register(SupplementariesCompat.PORTABLE_SACK.get(),
            (menu, inventory, title) -> new PortableSackScreen((PortableSackMenu) menu, inventory, title));
    }

    /** Supplementaries' sack screen, with the sack's own slot greyed out (returning to the inventory when closed: see OpenFromInventory). */
    private static class PortableSackScreen extends VariableSizeContainerScreen
    {
        private final PortableSackMenu sackMenu;

        PortableSackScreen(PortableSackMenu menu, Inventory inventory, Component title)
        {
            super(menu, inventory, title, ModTextures.SACK_GUI_TEXTURE);
            this.sackMenu = menu;
        }

        @Override
        protected void renderSlot(GuiGraphics graphics, Slot slot)
        {
            super.renderSlot(graphics, slot);
            if(this.sackMenu.isSackSlot(slot))
            {
                PortableScreens.renderLockedSlot(graphics, slot);
            }
        }
    }
}
