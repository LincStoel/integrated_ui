package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Labels for our icon-only buttons. */
public final class ButtonLabels
{
    private ButtonLabels() {}

    /** Shows {@code label} as the button's tooltip, and makes it the button's message, which the narrator reads. */
    public static void set(AbstractWidget button, Component label)
    {
        button.setTooltip(Tooltip.create(label));
        button.setMessage(label);
    }
}
