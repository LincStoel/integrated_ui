package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * A small button drawn from a GUI sprite, in the same style as Backpacked's mini buttons, whose sprite can
 * change (e.g. a collapse/expand arrow).
 */
public class SpriteButton extends Button
{
    private final Supplier<ResourceLocation> sprite;

    public SpriteButton(int width, int height, Supplier<ResourceLocation> sprite, OnPress onPress)
    {
        super(0, 0, width, height, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
        this.sprite = sprite;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        graphics.blitSprite(this.sprite.get(), this.getX(), this.getY(), this.width, this.height);
        if(this.isHovered && this.active)
        {
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x80FFFFFF);
        }
    }
}
