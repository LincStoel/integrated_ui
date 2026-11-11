package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.client.renderer.RenderType;

/** Vanilla's bundle tooltip, with an outline and highlight around the selected item. */
public class ClientSelectedBundleTooltip extends ClientBundleTooltip
{
    // As ClientBundleTooltip lays out its grid
    private static final int CELL_WIDTH = 18;
    private static final int CELL_HEIGHT = 20;
    private static final int OUTLINE_COLOR = 0xFFFFFFFF;

    private final int size;
    private final int selected;

    public ClientSelectedBundleTooltip(SelectedBundleTooltip tooltip)
    {
        super(tooltip.contents());
        this.size = tooltip.contents().size();
        this.selected = tooltip.selected();
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics)
    {
        super.renderImage(font, x, y, graphics);
        if(this.selected < 0 || this.selected >= this.size)
        {
            return;
        }
        int columns = Math.max(2, (int) Math.ceil(Math.sqrt(this.size + 1.0)));
        int cellX = x + this.selected % columns * CELL_WIDTH + 1;
        int cellY = y + this.selected / columns * CELL_HEIGHT + 1;
        // The item sits one pixel in from its cell's corner
        AbstractContainerScreen.renderSlotHighlight(graphics, cellX + 1, cellY + 1, 0);
        int right = cellX + CELL_WIDTH;
        int bottom = cellY + CELL_HEIGHT;
        graphics.fill(RenderType.guiOverlay(), cellX, cellY, right, cellY + 1, OUTLINE_COLOR);
        graphics.fill(RenderType.guiOverlay(), cellX, bottom - 1, right, bottom, OUTLINE_COLOR);
        graphics.fill(RenderType.guiOverlay(), cellX, cellY + 1, cellX + 1, bottom - 1, OUTLINE_COLOR);
        graphics.fill(RenderType.guiOverlay(), right - 1, cellY + 1, right, bottom - 1, OUTLINE_COLOR);
    }
}
