package com.lincstoel.integratedui.compat.arsnouveau;

import com.hollingsworth.arsnouveau.client.gui.book.GuiSpellBook;
import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.client.OpenFromInventory;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.network.CloseSpellBookPayload;
import com.lincstoel.integratedui.network.OpenSpellBookPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of opening a spell book from the inventory, see {@link ArsNouveauCompat}: right-clicking a spell book
 * opens it (see {@link OpenFromInventory}). Only used when Ars Nouveau is installed.
 */
public final class ArsNouveauClient implements OpenFromInventory.Opener
{
    /** The spell book's own screens, and those it opens (settings, particles, familiars...). */
    private static final String ARS_SCREEN_PACKAGE = "com.hollingsworth.arsnouveau.client.gui.";

    /** Whether a book opened from the inventory is in its screen, so the server must be told when it closes. */
    private static boolean bookOpen;

    private ArsNouveauClient() {}

    public static void register()
    {
        OpenFromInventory.addOpener(new ArsNouveauClient());
        NeoForge.EVENT_BUS.addListener(ArsNouveauClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(ArsNouveauClient::onLoggingOut);
    }

    @Override
    public boolean canOpen(ItemStack stack, AbstractContainerScreen<?> screen)
    {
        return ClientConfig.RIGHT_CLICK_OPENS_SPELL_BOOKS.get() && IntegratedUI.isArsNouveauEnabled()
            && CompatGuard.test(CompatGuard.ARS_NOUVEAU, () -> ArsNouveauCompat.isSpellBook(stack));
    }

    @Override
    public void open(int inventorySlot)
    {
        PacketDistributor.sendToServer(new OpenSpellBookPayload(inventorySlot));
    }

    @Override
    public boolean continuesInto(Screen next)
    {
        return isBookScreen(next);
    }

    /**
     * The server swapped the book into the given hand (if needed), so it can be opened like a held one. The server
     * closed the open menu without closing its screen, which the book's screen now replaces.
     */
    public static void onBookReady(InteractionHand hand)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if(player != null)
        {
            player.containerMenu = player.inventoryMenu;
        }
        bookOpen = true;
        GuiSpellBook.open(hand);
    }

    /** Once the book's screens are left, has the server put the book back. */
    private static void onClientTick(ClientTickEvent.Post event)
    {
        if(bookOpen && !isBookScreen(Minecraft.getInstance().screen))
        {
            bookOpen = false;
            PacketDistributor.sendToServer(CloseSpellBookPayload.INSTANCE);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        bookOpen = false;
    }

    private static boolean isBookScreen(@Nullable Screen screen)
    {
        return screen != null && screen.getClass().getName().startsWith(ARS_SCREEN_PACKAGE);
    }
}
