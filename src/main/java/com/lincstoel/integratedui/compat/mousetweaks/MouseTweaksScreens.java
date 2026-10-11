package com.lincstoel.integratedui.compat.mousetweaks;

import com.lincstoel.integratedui.client.IntegratedInventoryScreen;
import com.lincstoel.integratedui.client.InventoryScreenParts;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import yalter.mousetweaks.api.IMTModGuiContainer3Ex;

import java.util.List;

/**
 * The integrated inventory screens as Mouse Tweaks sees them, through its API: only the slots on show, with the band
 * before the crafting grid (see {@link InventoryScreenParts#getMouseTweaksSlots}). Without this it would use every
 * slot, moving items into band rows scrolled out of view and into the collapsed equipment panel. Only used when Mouse
 * Tweaks is installed; Backpacked's screen has its own, {@link MouseTweaksBackpackedScreen}.
 */
public final class MouseTweaksScreens
{
    private MouseTweaksScreens() {}

    public static IntegratedInventoryScreen create(IntegratedInventoryMenu menu, Inventory inventory, Component title)
    {
        return new Plain(menu, inventory, title);
    }

    static boolean isCraftingOutput(Slot slot)
    {
        return slot instanceof ResultSlot;
    }

    /** As vanilla screens do it (Mouse Tweaks' default for them): stops right-drag spreading, and the release after. */
    static boolean disableRightDragging(AbstractContainerScreen<?> screen)
    {
        screen.skipNextRelease = true;
        if(screen.isQuickCrafting && screen.quickCraftingButton == 1)
        {
            screen.isQuickCrafting = false;
            return true;
        }
        return false;
    }

    private static final class Plain extends IntegratedInventoryScreen implements IMTModGuiContainer3Ex
    {
        Plain(IntegratedInventoryMenu menu, Inventory inventory, Component title)
        {
            super(menu, inventory, title);
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
            return isCraftingOutput(slot);
        }

        @Override
        public boolean MT_isIgnored(Slot slot)
        {
            return InventoryScreenParts.isSlotHidden(slot);
        }

        @Override
        public boolean MT_disableRMBDraggingFunctionality()
        {
            return disableRightDragging(this);
        }

        @Override
        public void MT_clickSlot(Slot slot, int mouseButton, ClickType clickType)
        {
            this.slotClicked(slot, slot.index, mouseButton, clickType);
        }
    }
}
