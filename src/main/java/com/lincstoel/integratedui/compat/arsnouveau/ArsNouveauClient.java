package com.lincstoel.integratedui.compat.arsnouveau;

import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.network.CloseSpellBookPayload;
import com.lincstoel.integratedui.network.OpenSpellBookPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of opening a spell book from the inventory, see {@link ArsNouveauCompat}: right-clicking a spell book
 * in the player's inventory with an empty cursor opens it, as Quark does for shulker boxes. Only used when Ars
 * Nouveau is installed.
 */
public final class ArsNouveauClient
{
    /** The spell book's own screens, and those it opens (settings, particles, familiars...). */
    private static final String ARS_SCREEN_PACKAGE = "com.hollingsworth.arsnouveau.client.gui.";

    /** Whether a book opened from the inventory is in its screen, so the server must be told when it closes. */
    private static boolean bookOpen;
    /** Whether the book was opened from the player's inventory screen, which is reopened when the book is closed. */
    private static boolean returnToInventory;

    private ArsNouveauClient() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener(ArsNouveauClient::onMouseClicked);
        NeoForge.EVENT_BUS.addListener(ArsNouveauClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(ArsNouveauClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(ArsNouveauClient::onLoggingOut);
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
            returnToInventory = event.getScreen() instanceof InventoryScreen
                || ((AbstractContainerScreen<?>) event.getScreen()).getMenu() instanceof IntegratedInventoryMenu;
            PacketDistributor.sendToServer(new OpenSpellBookPayload(slot.getContainerSlot()));
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

    /** The server swapped the book into the given hand (if needed), so it can be opened like a held one. */
    public static void onBookReady(InteractionHand hand)
    {
        bookOpen = true;
        GuiSpellBook.open(hand);
    }

    /** Once the book's screens are left, has the server put the book back, and returns to the inventory. */
    private static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        if(!bookOpen || (mc.screen != null && mc.screen.getClass().getName().startsWith(ARS_SCREEN_PACKAGE)))
        {
            return;
        }
        bookOpen = false;
        PacketDistributor.sendToServer(CloseSpellBookPayload.INSTANCE);
        if(mc.screen == null && returnToInventory && mc.player != null)
        {
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        bookOpen = false;
    }

    /** The hovered slot, if it holds a spell book in the player's inventory that a right-click would open. */
    @Nullable
    private static Slot getOpenableSlot(@Nullable Screen screen)
    {
        if(!(screen instanceof AbstractContainerScreen<?> containerScreen) || screen instanceof CreativeModeInventoryScreen
            || Screen.hasShiftDown() || !ClientConfig.RIGHT_CLICK_OPENS_SPELL_BOOKS.get() || !IntegratedUI.isArsNouveauEnabled())
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
            || !CompatGuard.test(CompatGuard.ARS_NOUVEAU, () -> ArsNouveauCompat.isSpellBook(slot.getItem())))
        {
            return null;
        }
        return slot;
    }
}
