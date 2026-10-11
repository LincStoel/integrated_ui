package com.lincstoel.integratedui.compat.backpacked;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.client.ClientEvents;
import com.lincstoel.integratedui.compat.mousetweaks.MouseTweaksBackpackedScreen;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.mrcrayfish.backpacked.client.gui.screen.CustomiseBackpackScreen;
import com.mrcrayfish.backpacked.client.gui.screen.inventory.BackpackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side Backpacked calls made from common code, and returning to the integrated inventory from Backpacked's
 * full backpack screen when that was opened from the band. Only used when Backpacked is installed.
 */
public final class BackpackedClient
{
    /** Whether Backpacked's screen on show was opened from the band, so closing it goes back to the inventory. */
    private static boolean openedFromBand;

    private BackpackedClient() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener(BackpackedClient::onKeyPressed);
        NeoForge.EVENT_BUS.addListener(BackpackedClient::onClientTick);
    }

    /**
     * The screen for a menu showing a backpack (which is always a {@link BackpackedInventoryMenu}). With Mouse Tweaks,
     * a version that tells it which slots it may use.
     */
    @SuppressWarnings("unchecked")
    public static AbstractContainerScreen<IntegratedInventoryMenu> createScreen(IntegratedInventoryMenu menu, Inventory inventory, Component title)
    {
        BackpackedInventoryMenu backpackMenu = (BackpackedInventoryMenu) menu;
        AbstractContainerScreen<?> screen = IntegratedUI.isMouseTweaksLoaded() ? MouseTweaksBackpackedScreen.create(backpackMenu, inventory, title)
            : new BackpackedInventoryScreen(backpackMenu, inventory, title);
        return (AbstractContainerScreen<IntegratedInventoryMenu>) screen;
    }

    /** The band's button is about to open Backpacked's full screen. */
    static void noteFullScreenOpened()
    {
        openedFromBand = true;
    }

    /**
     * Closing Backpacked's screen (with escape or the inventory key) asks for the integrated inventory instead, which
     * then takes its place without the screen closing in between, as closing a container item's screen does.
     */
    private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event)
    {
        Minecraft mc = Minecraft.getInstance();
        if(!openedFromBand || !(event.getScreen() instanceof BackpackScreen) || !ClientEvents.replacesInventory())
        {
            return;
        }
        boolean closes = event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event.getKeyCode(), event.getScanCode());
        if(closes)
        {
            event.setCanceled(true);
            openedFromBand = false;
            ClientEvents.requestInventory();
        }
    }

    /** Forgets where Backpacked's screen came from once it, and the customisation screen it opens, are left. */
    private static void onClientTick(ClientTickEvent.Post event)
    {
        Screen screen = Minecraft.getInstance().screen;
        if(openedFromBand && !(screen instanceof BackpackScreen) && !(screen instanceof CustomiseBackpackScreen) && !isPending(screen))
        {
            openedFromBand = false;
        }
    }

    /** Right after the button is pressed, the integrated inventory is still showing until Backpacked's screen arrives. */
    private static boolean isPending(Screen screen)
    {
        return screen instanceof BackpackedInventoryScreen;
    }
}
