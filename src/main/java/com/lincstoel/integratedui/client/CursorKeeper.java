package com.lincstoel.integratedui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps the cursor in place across a screen the server opens. The server closes the current menu before opening the
 * new one, so the mouse is grabbed for a moment in between, and releasing it again moves the cursor to the middle of
 * the window.
 */
public final class CursorKeeper
{
    /** How long a kept position waits for its screen, in case the server never opens one. */
    private static final long TIMEOUT_MILLIS = 2000;

    private static double mouseX;
    private static double mouseY;
    private static long keptUntil;
    /** The screen the position was kept on, which doesn't count as the next one if it is reinitialised. */
    @Nullable
    private static Screen keptOn;

    private CursorKeeper() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener(CursorKeeper::onScreenInit);
    }

    /** Notes where the cursor is, to put it back there when the next screen opens. */
    public static void keepForNextScreen()
    {
        Minecraft mc = Minecraft.getInstance();
        mouseX = mc.mouseHandler.xpos();
        mouseY = mc.mouseHandler.ypos();
        keptUntil = System.currentTimeMillis() + TIMEOUT_MILLIS;
        keptOn = mc.screen;
    }

    private static void onScreenInit(ScreenEvent.Init.Post event)
    {
        // Init.Post comes after the cursor has been centred
        if(keptUntil == 0 || event.getScreen() == keptOn)
        {
            return;
        }
        boolean expired = System.currentTimeMillis() > keptUntil;
        keptUntil = 0;
        keptOn = null;
        if(!expired)
        {
            Minecraft mc = Minecraft.getInstance();
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mouseX, mouseY);
        }
    }
}
