package com.lincstoel.integratedui.compat.mousetweaks;

import com.lincstoel.integratedui.client.InventoryScreenParts;
import com.lincstoel.integratedui.compat.backpacked.BackpackedInventoryMenu;
import com.lincstoel.integratedui.compat.backpacked.BackpackedInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import yalter.mousetweaks.api.IMTModGuiContainer3Ex;

import java.util.List;

/**
 * Backpacked's integrated inventory screen as Mouse Tweaks sees it, see {@link MouseTweaksScreens}. Kept apart from
 * that class, so neither mod is loaded unless both are installed.
 */
public final class MouseTweaksBackpackedScreen extends BackpackedInventoryScreen implements IMTModGuiContainer3Ex
{
    private MouseTweaksBackpackedScreen(BackpackedInventoryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
    }

    public static BackpackedInventoryScreen create(BackpackedInventoryMenu menu, Inventory inventory, Component title)
    {
        return new MouseTweaksBackpackedScreen(menu, inventory, title);
    }

    @Override
    public boolean MT_isMouseTweaksDisabled()
    {
        return false;
    }

    @Override
    public boolean MT_isWheelTweakDisabled()
    {
        return false;
    }

    @Override
    public List<Slot> MT_getSlots()
    {
        return this.parts.getMouseTweaksSlots();
    }

    @Override
    public Slot MT_getSlotUnderMouse(double mouseX, double mouseY)
    {
        return this.parts.findSlot(mouseX, mouseY, this::isHovering);
    }

    @Override
    public boolean MT_isCraftingOutput(Slot slot)
    {
        return MouseTweaksScreens.isCraftingOutput(slot);
    }

    @Override
    public boolean MT_isIgnored(Slot slot)
    {
        return InventoryScreenParts.isSlotHidden(slot);
    }

    @Override
    public boolean MT_disableRMBDraggingFunctionality()
    {
        return MouseTweaksScreens.disableRightDragging(this);
    }

    @Override
    public void MT_clickSlot(Slot slot, int mouseButton, ClickType clickType)
    {
        this.slotClicked(slot, slot.index, mouseButton, clickType);
    }
}
