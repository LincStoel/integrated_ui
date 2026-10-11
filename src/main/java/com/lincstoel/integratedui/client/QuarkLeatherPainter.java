package com.lincstoel.integratedui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the band like Quark's own backpack screen, straight from Quark's texture: its stitched leather frame and
 * slots. Only used for the worn Quark backpack, so Quark (and its texture, or a resource pack's) is always there; none
 * of Quark's art is copied into this mod. The frame's edges are tiled from the stitching, and its inside filled with
 * the leather's colour.
 */
final class QuarkLeatherPainter implements ContainerBand.Painter
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("quark", "textures/misc/backpack_gui.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int LEATHER = 0xFF9E492A;

    // Where Quark's band (a stitched frame around a 9x3 grid of slots) is on its texture
    private static final int FRAME_LEFT = 5;
    private static final int FRAME_TOP = 81;
    private static final int FRAME_RIGHT = 171;
    private static final int FRAME_BOTTOM = 140;
    private static final int SLOT_U = 7;
    private static final int SLOT_V = 83;
    /** The frame's thickness on each side (the bottom and right ones include a shadow). */
    private static final int TOP = 2;
    private static final int LEFT = 2;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 3;
    /** The stitching repeats every this many pixels. */
    private static final int STITCH = 10;

    @Override
    public void background(GuiGraphics graphics, int x, int y, int width, int height)
    {
        int right = x + width - RIGHT;
        int bottom = y + height - BOTTOM;
        graphics.fill(x + LEFT, y + TOP, right, bottom, LEATHER);
        // Corners
        blit(graphics, x, y, FRAME_LEFT, FRAME_TOP, LEFT, TOP);
        blit(graphics, right, y, FRAME_RIGHT - RIGHT, FRAME_TOP, RIGHT, TOP);
        blit(graphics, x, bottom, FRAME_LEFT, FRAME_BOTTOM - BOTTOM, LEFT, BOTTOM);
        blit(graphics, right, bottom, FRAME_RIGHT - RIGHT, FRAME_BOTTOM - BOTTOM, RIGHT, BOTTOM);
        // Edges, tiled from one stretch of stitching each
        for(int i = x + LEFT; i < right; i += STITCH)
        {
            int w = Math.min(STITCH, right - i);
            blit(graphics, i, y, SLOT_U, FRAME_TOP, w, TOP);
            blit(graphics, i, bottom, SLOT_U, FRAME_BOTTOM - BOTTOM, w, BOTTOM);
        }
        for(int j = y + TOP; j < bottom; j += STITCH)
        {
            int h = Math.min(STITCH, bottom - j);
            blit(graphics, x, j, FRAME_LEFT, SLOT_V + 2, LEFT, h);
            blit(graphics, right, j, FRAME_RIGHT - RIGHT, SLOT_V + 2, RIGHT, h);
        }
    }

    @Override
    public void slots(GuiGraphics graphics, int x, int y, int width, int height)
    {
        for(int j = y; j < y + height; j += 18)
        {
            for(int i = x; i < x + width; i += 18)
            {
                blit(graphics, i, j, SLOT_U, SLOT_V, 18, 18);
            }
        }
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height)
    {
        graphics.blit(TEXTURE, x, y, u, v, width, height, TEXTURE_SIZE, TEXTURE_SIZE);
    }
}
