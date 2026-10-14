package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.menu.PortableChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Vanilla's chest screen for a container item opened from the inventory, see {@link PortableScreens} and {@link OpenFromInventory}. */
public class PortableChestScreen extends ContainerScreen
{
    private final PortableChestMenu portableMenu;

    public PortableChestScreen(PortableChestMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.portableMenu = menu;
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot)
    {
        super.renderSlot(graphics, slot);
        if(this.portableMenu.isItemSlot(slot))
        {
            PortableScreens.renderLockedSlot(graphics, slot);
        }
    }
}
