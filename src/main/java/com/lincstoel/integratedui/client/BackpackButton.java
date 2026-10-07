package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Opens the full Backpacked screen; drawn as a small picture of the backpack itself.
 */
public class BackpackButton extends Button
{
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("backpacked", "backpack/button_enabled");
    private static final ResourceLocation BACKGROUND_FOCUSED = ResourceLocation.fromNamespaceAndPath("backpacked", "backpack/button_enabled_focused");

    private final Supplier<ItemStack> stack;

    public BackpackButton(Supplier<ItemStack> stack, OnPress onPress)
    {
        super(0, 0, 12, 12, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
        this.stack = stack;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        graphics.blitSprite(this.isHoveredOrFocused() ? BACKGROUND_FOCUSED : BACKGROUND, this.getX(), this.getY(), this.width, this.height);
        ItemStack stack = this.stack.get();
        if(!stack.isEmpty())
        {
            graphics.pose().pushPose();
            graphics.pose().translate(this.getX() + 1, this.getY() + 1, 0);
            graphics.pose().scale(0.625F, 0.625F, 1.0F);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
    }
}
