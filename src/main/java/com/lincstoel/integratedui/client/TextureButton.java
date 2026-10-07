package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;

/**
 * A button drawn from a whole (non-atlas) texture, with a separate texture when hovered.
 */
public class TextureButton extends Button
{
    private final ResourceLocation texture;
    private final ResourceLocation hoveredTexture;

    public TextureButton(int width, int height, ResourceLocation texture, ResourceLocation hoveredTexture, OnPress onPress)
    {
        super(0, 0, width, height, CommonComponents.EMPTY, onPress, DEFAULT_NARRATION);
        this.texture = texture;
        this.hoveredTexture = hoveredTexture;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        ResourceLocation texture = this.isHoveredOrFocused() ? this.hoveredTexture : this.texture;
        graphics.blit(texture, this.getX(), this.getY(), 0, 0, this.width, this.height, this.width, this.height);
    }
}
