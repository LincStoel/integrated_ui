package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * A {@link SpriteButton} with a small picture of an item on it (e.g. the shulker box shown in the band).
 */
public class ItemButton extends SpriteButton
{
    private final Supplier<ItemStack> stack;

    public ItemButton(int size, Supplier<ResourceLocation> sprite, Supplier<ItemStack> stack, OnPress onPress)
    {
        super(size, size, sprite, onPress);
        this.stack = stack;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        ItemStack stack = this.stack.get();
        if(!stack.isEmpty())
        {
            graphics.pose().pushPose();
            graphics.pose().translate(this.getX() + 1, this.getY() + 1, 0);
            graphics.pose().scale((this.width - 2) / 16.0F, (this.height - 2) / 16.0F, 1.0F);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }
    }
}
