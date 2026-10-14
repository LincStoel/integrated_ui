package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import com.lincstoel.integratedui.menu.SuspendedMenus;
import com.lincstoel.integratedui.network.ReturnToMenuPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Opening items in the player's inventory in a screen of their own, and coming back, for every kind of item that can
 * be: right-clicking one with an empty cursor (in any container screen showing the player's inventory, except the
 * creative one) opens it, as Quark does for shulker boxes, and its tooltip says so. Each kind registers an
 * {@link Opener}.
 * <p>
 * The switch is seamless both ways, so the world never shows in between and the cursor stays where it is:
 * <ul>
 * <li>The server opens the new menu straight over the current one, without closing the current screen on the client
 * first (see {@link com.lincstoel.integratedui.menu.SeamlessMenuProvider}). Closing it would show the world for a
 * moment and grab the mouse, which recentres the cursor once it is released again.</li>
 * <li>When a screen opened from the inventory closes, in whatever way (escape, the inventory key, a button of its
 * own, the server closing its menu), it goes straight back to the inventory in place of the world: every close ends
 * in {@code Minecraft.setScreen(null)}, where {@link #returnFromScreen} is hooked in (see MinecraftMixin). A screen
 * opened from any other container's screen (a chest's, say) goes back to that screen the same way: the server holds
 * the container's menu while the item's screen is open (see {@link SuspendedMenus}), and the client keeps its
 * screen.</li>
 * </ul>
 */
public final class OpenFromInventory
{
    /** A kind of item that a right-click in the inventory opens. */
    public interface Opener
    {
        /**
         * Whether right-clicking the stack in {@code screen} opens it, including whether this kind is turned on. The
         * slot is already known to be one of the player's inventory, with an empty cursor.
         */
        boolean canOpen(ItemStack stack, AbstractContainerScreen<?> screen);

        /** Asks the server to open the stack in the given slot of the player's inventory. */
        void open(int inventorySlot);

        /**
         * Whether {@code next}, opened over this kind's screen, is still part of it (a settings page, say), so
         * closing it also returns to where the item was opened from. By default, any other screen ends it.
         */
        default boolean continuesInto(Screen next)
        {
            return false;
        }
    }

    private static final List<Opener> OPENERS = new ArrayList<>();

    /** A screen asked for and not opened yet, and where closing it returns to. */
    @Nullable
    private static Request pending;
    /** The open screen that was opened from somewhere by {@link #expectScreen}, and where it came from. */
    @Nullable
    private static Screen tracked;
    @Nullable
    private static Request trackedRequest;
    /**
     * The screen being returned from, which stays until the screen returned to replaces it: closing a container
     * screen closes it twice (in LocalPlayer.closeContainer, then in Screen.onClose).
     */
    @Nullable
    private static Screen returningFrom;
    /** When to stop keeping {@link #returningFrom} open, in case the screen returned to never comes. */
    private static long returningUntil;

    /** How long a screen is kept open while the screen returned to is on its way. */
    private static final long RETURN_TIMEOUT_MILLIS = 2000;

    private OpenFromInventory() {}

    public static void register()
    {
        // Before the screen handles the click, so mods that open items on a slot click (Quark) don't see it
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, OpenFromInventory::onMouseClicked);
        NeoForge.EVENT_BUS.addListener(OpenFromInventory::onTooltip);
        NeoForge.EVENT_BUS.addListener(OpenFromInventory::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(OpenFromInventory::onLoggingOut);
    }

    /** Adds a kind of item to open. Earlier ones take precedence. */
    public static void addOpener(Opener opener)
    {
        OPENERS.add(opener);
    }

    /**
     * Notes that the server is being asked to open a screen over {@code from}, so that closing it returns to
     * {@code from}: to the inventory if it is the inventory, or to a container's screen (whose menu the server holds,
     * see SuspendedMenus). A screen opened over one opened this way returns to where that one would have. Called for
     * every right-click opening; call it before asking the server for any other screen that should behave the same.
     */
    public static void expectScreen(@Nullable Screen from)
    {
        if(from != null && from == tracked && trackedRequest != null)
        {
            pending = new Request(from, trackedRequest.toInventory, trackedRequest.toScreen, null);
        }
        else if(from instanceof AbstractContainerScreen<?> containerScreen)
        {
            boolean inventory = containerScreen.getMenu() instanceof InventoryMenu;
            pending = new Request(from, inventory, inventory ? null : containerScreen, null);
        }
        else
        {
            pending = new Request(from, false, null, null);
        }
    }

    private static void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event)
    {
        if(event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        {
            return;
        }
        Target target = getTarget(event.getScreen());
        if(target != null)
        {
            event.setCanceled(true);
            expectScreen(event.getScreen());
            pending = new Request(pending.from, pending.toInventory, pending.toScreen, target.opener);
            target.opener.open(target.slot.getContainerSlot());
        }
    }

    private static void onTooltip(ItemTooltipEvent event)
    {
        Target target = getTarget(Minecraft.getInstance().screen);
        if(target != null && target.slot.getItem() == event.getItemStack())
        {
            event.getToolTip().add(Component.translatable("integrated_ui.misc.right_click_to_open").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Follows the screen asked for as it opens (straight over the screen it was asked from), and any it continues into. */
    private static void onScreenOpening(ScreenEvent.Opening event)
    {
        Screen current = event.getCurrentScreen();
        Screen next = event.getNewScreen();
        if(current == returningFrom)
        {
            returningFrom = null;
        }
        if(pending != null && current == pending.from)
        {
            tracked = next;
            trackedRequest = pending;
            pending = null;
        }
        else if(tracked != null && current == tracked)
        {
            if(trackedRequest != null && trackedRequest.opener != null && trackedRequest.opener.continuesInto(next))
            {
                tracked = next;
            }
            else
            {
                stopTracking();
            }
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        pending = null;
        returningFrom = null;
        stopTracking();
    }

    private static void stopTracking()
    {
        tracked = null;
        trackedRequest = null;
    }

    /**
     * Called as the open screen is about to close to the world. If it was opened from the inventory or a container's
     * screen, goes back there in its place and returns true, so the screen isn't closed (until the one returned to
     * replaces it).
     */
    public static boolean returnFromScreen()
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Screen screen = mc.screen;
        pending = null;
        if(screen != null && screen == returningFrom)
        {
            if(System.currentTimeMillis() < returningUntil)
            {
                return true;
            }
            returningFrom = null;
            return false;
        }
        if(screen == null || screen != tracked)
        {
            return false;
        }
        Request request = trackedRequest;
        stopTracking();
        if(request == null || !request.toInventory && request.toScreen == null || player == null || !player.isAlive())
        {
            return false;
        }
        returningFrom = screen;
        returningUntil = System.currentTimeMillis() + RETURN_TIMEOUT_MILLIS;
        AbstractContainerScreen<?> toScreen = request.toScreen;
        if(toScreen != null)
        {
            // The server makes the container's held menu the open one again (closing this screen's menu first, if
            // the client hasn't already), and its screen comes back once whatever is closing this one is done
            PacketDistributor.sendToServer(ReturnToMenuPayload.INSTANCE);
            AbstractContainerMenu menu = toScreen.getMenu();
            mc.tell(() -> {
                if(mc.screen == screen && mc.player == player)
                {
                    player.containerMenu = menu;
                    mc.setScreen(toScreen);
                }
            });
            return true;
        }
        if(ClientEvents.replacesInventory())
        {
            // The server opens the integrated inventory straight over this screen (closing its menu first, if the
            // client hasn't already)
            ClientEvents.requestInventory();
            return true;
        }
        if(player.containerMenu != player.inventoryMenu)
        {
            // Closes the menu as LocalPlayer.closeContainer does, without closing the screen
            player.connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
            player.containerMenu = player.inventoryMenu;
        }
        if(IntegratedUI.isQuarkEnabled() && CompatGuard.test(CompatGuard.QUARK, () -> QuarkClientCompat.requestBackpackIfWorn(player)))
        {
            return true;
        }
        // Once whatever is closing this screen is done
        mc.tell(() -> {
            if(mc.screen == screen && mc.player == player)
            {
                mc.setScreen(new InventoryScreen(player));
            }
        });
        return true;
    }

    /** The hovered slot and the opener that would open it on a right-click, if any. */
    @Nullable
    private static Target getTarget(@Nullable Screen screen)
    {
        if(!(screen instanceof AbstractContainerScreen<?> containerScreen) || screen instanceof CreativeModeInventoryScreen || Screen.hasShiftDown())
        {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null || player.isSpectator() || !containerScreen.getMenu().getCarried().isEmpty())
        {
            return null;
        }
        Slot slot = containerScreen.getSlotUnderMouse();
        if(slot == null || slot.container != player.getInventory() || !slot.mayPickup(player) || slot.getItem().isEmpty())
        {
            return null;
        }
        for(Opener opener : OPENERS)
        {
            if(opener.canOpen(slot.getItem(), containerScreen))
            {
                return new Target(slot, opener);
            }
        }
        return null;
    }

    /**
     * A screen asked for over {@code from}, which returns to the inventory if {@code toInventory}, else to
     * {@code toScreen} if there is one (else to the world), and the opener it was asked for by, if any.
     */
    private record Request(@Nullable Screen from, boolean toInventory, @Nullable AbstractContainerScreen<?> toScreen, @Nullable Opener opener) {}

    private record Target(Slot slot, Opener opener) {}
}
