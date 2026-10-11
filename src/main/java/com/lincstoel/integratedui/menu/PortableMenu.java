package com.lincstoel.integratedui.menu;

import net.minecraft.world.inventory.Slot;

/** A menu for a container item opened from the player's inventory, whose own inventory slot is locked while open. */
public interface PortableMenu
{
    /** Whether the slot holds the open container item itself. */
    boolean isItemSlot(Slot slot);
}
