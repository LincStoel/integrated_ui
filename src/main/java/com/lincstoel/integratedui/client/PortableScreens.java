package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Shared by the screens of container items opened from the player's inventory (shulker boxes, sacks and the like),
 * which grey out the item's own slot. Opening them, and returning to the inventory when they close, is handled by
 * {@link OpenFromInventory}.
 */
public final class PortableScreens
{
    private PortableScreens() {}

    /** Shades the open item's own slot, which is locked. Call after the slot is drawn. */
    public static void renderLockedSlot(GuiGraphics graphics, Slot slot)
    {
        // Above the item, as Quark does for the held shulker box
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x88000000);
        graphics.pose().popPose();
    }
}
