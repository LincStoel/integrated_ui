package com.lincstoel.integratedui.compat.create;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.client.PortableScreens;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.network.OpenFilterPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of opening a filter from the inventory, see {@link CreateCompat}: right-clicking a Create filter in
 * the player's inventory with an empty cursor opens its screen, as Quark does for shulker boxes. Only used when
 * Create is installed.
 * <p>
 * The filter screen replaces the screen it is opened from without closing it in between, and closing a filter
 * opened from the inventory goes straight back there, so the cursor stays where it is throughout.
 */
public final class CreateClient
{
    /** The screen a filter was last asked for from, until the filter's screen replaces it. */
    @Nullable
    private static Screen requestedFrom;
    /** Whether the open filter screen was opened by a right-click, and so may return to the inventory (see PortableScreens). */
    private static boolean returnsToInventory;

    private CreateClient() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener(CreateClient::onMouseClicked);
        NeoForge.EVENT_BUS.addListener(CreateClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(CreateClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(CreateClient::onLoggingOut);
    }

    private static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event)
    {
        if(event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        {
            return;
        }
        Slot slot = getOpenableSlot(event.getScreen());
        if(slot != null)
        {
            event.setCanceled(true);
            requestedFrom = event.getScreen();
            PortableScreens.noteOpenedFrom(event.getScreen());
            PacketDistributor.sendToServer(new OpenFilterPayload(slot.getContainerSlot()));
        }
    }

    private static void onTooltip(ItemTooltipEvent event)
    {
        Slot slot = getOpenableSlot(Minecraft.getInstance().screen);
        if(slot != null && slot.getItem() == event.getItemStack())
        {
            event.getToolTip().add(Component.translatable("integrated_ui.misc.right_click_to_open").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Notes whether a filter screen is the one asked for (the server opens it straight over the screen it was asked from). */
    private static void onScreenOpening(ScreenEvent.Opening event)
    {
        if(isFilterScreen(event.getNewScreen()))
        {
            returnsToInventory = requestedFrom != null && event.getCurrentScreen() == requestedFrom;
            requestedFrom = null;
        }
    }

    /**
     * Called as the player's open menu is closed on the client. If it is a filter opened from the inventory, returns
     * there in its place (keeping the cursor where it is) and returns true; the server puts the filter back as its
     * menu closes. Every way out of Create's filter screen (escape, the inventory key, its confirm button) comes
     * through here.
     */
    public static boolean returnToInventory()
    {
        if(!returnsToInventory || !isFilterScreen(Minecraft.getInstance().screen))
        {
            return false;
        }
        // Cleared first, as returning to the vanilla inventory closes the menu again
        returnsToInventory = false;
        return PortableScreens.returnToInventory();
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        requestedFrom = null;
        returnsToInventory = false;
    }

    private static boolean isFilterScreen(@Nullable Screen screen)
    {
        return screen instanceof AbstractContainerScreen<?> containerScreen
            && CompatGuard.test(CompatGuard.CREATE, () -> CreateCompat.isFilterMenu(containerScreen.getMenu()));
    }

    /** The hovered slot, if it holds a filter in the player's inventory that a right-click would open. */
    @Nullable
    private static Slot getOpenableSlot(@Nullable Screen screen)
    {
        // Not from a filter's own screen, where right-clicking the inventory picks up items to filter by
        if(!(screen instanceof AbstractContainerScreen<?> containerScreen) || screen instanceof CreativeModeInventoryScreen
            || Screen.hasShiftDown() || !ClientConfig.RIGHT_CLICK_OPENS_FILTERS.get() || !IntegratedUI.isCreateEnabled()
            || isFilterScreen(screen))
        {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null || player.isSpectator() || !containerScreen.getMenu().getCarried().isEmpty())
        {
            return null;
        }
        Slot slot = containerScreen.getSlotUnderMouse();
        if(slot == null || slot.container != player.getInventory() || !slot.mayPickup(player)
            || !CompatGuard.test(CompatGuard.CREATE, () -> CreateCompat.isFilter(slot.getItem())))
        {
            return null;
        }
        return slot;
    }
}
