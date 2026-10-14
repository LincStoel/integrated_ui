package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.aether.AetherClient;
import com.lincstoel.integratedui.compat.backpacked.BackpackedClient;
import com.lincstoel.integratedui.compat.mousetweaks.MouseTweaksScreens;
import com.lincstoel.integratedui.compat.quark.QuarkCompat;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.ModMenus;
import com.lincstoel.integratedui.menu.Navigation;
import com.lincstoel.integratedui.menu.PortableChestMenu;
import com.lincstoel.integratedui.menu.PortableShulkerBoxMenu;
import com.lincstoel.integratedui.network.OpenInventoryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Swaps the vanilla inventory screen for the integrated inventory, the same way Quark does for its backpack: the
 * vanilla screen is cancelled and the server is asked to open our menu instead. (The server reopens it by itself
 * when something turns up to show in the band, see ServerEvents.)
 */
@EventBusSubscriber(modid = IntegratedUI.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents
{
    /** The vanilla inventory screen we already asked the server to replace. */
    @Nullable
    private static Screen handledScreen;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event)
    {
        if(shouldReplace(event.getNewScreen()))
        {
            event.setCanceled(true);
            // The Aether's accessories screen goes back to the inventory and then tells the server, which closes
            // whatever menu is open: ask only after that, or the server would close our menu straight away
            if(IntegratedUI.isAetherLoaded() && CompatGuard.test(CompatGuard.AETHER, () -> AetherClient.isAccessoriesScreen(event.getCurrentScreen())))
            {
                Minecraft.getInstance().tell(ClientEvents::requestInventory);
            }
            else
            {
                requestInventory();
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        // Don't keep the last world's screen (and its player and level) alive
        handledScreen = null;
    }

    /**
     * Covers a vanilla inventory that was opened while it couldn't be replaced, and switches to the creative inventory
     * when the player goes into creative with the integrated inventory open, as vanilla's inventory screen does.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if(screen instanceof IntegratedScreen && mc.player != null && mc.gameMode != null && mc.gameMode.hasInfiniteItems())
        {
            openCreativeInventory(mc, mc.player);
        }
        else if(screen != null && screen != handledScreen && shouldReplace(screen))
        {
            handledScreen = screen;
            requestInventory();
        }
    }

    /**
     * Closes our menu on the server and goes straight to the creative inventory, which runs on the player's own
     * inventory menu. Not through {@link LocalPlayer#closeContainer}, so the mouse isn't grabbed for a tick in between.
     */
    private static void openCreativeInventory(Minecraft mc, LocalPlayer player)
    {
        player.connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
        player.containerMenu = player.inventoryMenu;
        mc.setScreen(new CreativeModeInventoryScreen(player, player.connection.enabledFeatures(), mc.options.operatorItemsTab().get()));
    }

    public static void requestInventory()
    {
        PacketDistributor.sendToServer(new OpenInventoryPayload(Navigation.CURRENT));
    }

    private static boolean shouldReplace(@Nullable Screen screen)
    {
        return screen != null && screen.getClass() == InventoryScreen.class && replacesInventory();
    }

    /**
     * Whether the player's inventory is shown as the integrated inventory. If so, it also takes the place of Quark's
     * backpack screen, showing the worn backpack in the band. With the Quark integration turned off, a worn Quark
     * backpack is left to Quark's own screen instead.
     */
    public static boolean replacesInventory()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        return ClientConfig.REPLACE_INVENTORY_SCREEN.get() && player != null && !player.isSpectator() && !player.getAbilities().instabuild
            && !(IntegratedUI.isQuarkLoaded() && !IntegratedUI.isQuarkEnabled() && CompatGuard.test(CompatGuard.QUARK, () -> QuarkCompat.isWearingQuarkBackpack(player)));
    }

    /** Status effects are drawn right of the window: move them past the band's scroll tab, which sticks out there. */
    @SubscribeEvent
    public static void onRenderMobEffects(ScreenEvent.RenderInventoryMobEffects event)
    {
        if(event.getScreen() instanceof IntegratedScreen screen)
        {
            int offset = screen.parts().getEffectsOffset();
            if(offset > 0)
            {
                event.addHorizontalOffset(offset);
                int space = event.getAvailableSpace() - offset;
                if(space < 32)
                {
                    event.setCanceled(true);
                }
                else if(space < 120)
                {
                    event.setCompact(true);
                }
            }
        }
    }

    /**
     * With a backpack, Backpacked's screen; otherwise (always, without Backpacked) the plain one. With Mouse Tweaks,
     * versions of either that tell it which slots it may use.
     */
    private static AbstractContainerScreen<IntegratedInventoryMenu> createScreen(IntegratedInventoryMenu menu, Inventory inventory, Component title)
    {
        if(menu.getData().hasBackpack())
        {
            AbstractContainerScreen<IntegratedInventoryMenu> screen = CompatGuard.get(CompatGuard.BACKPACKED, () -> BackpackedClient.createScreen(menu, inventory, title), null);
            if(screen != null)
            {
                return screen;
            }
            // Backpacked changed under us: the backpack still shows, without Backpacked's slot unlocking
        }
        if(IntegratedUI.isMouseTweaksLoaded())
        {
            return MouseTweaksScreens.create(menu, inventory, title);
        }
        return new IntegratedInventoryScreen(menu, inventory, title);
    }

    @EventBusSubscriber(modid = IntegratedUI.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus
    {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event)
        {
            ModKeys.register(event);
        }

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event)
        {
            event.register(ModMenus.INTEGRATED_INVENTORY.get(), ClientEvents::createScreen);
            // The vanilla screens are typed to the vanilla menus, so the menu types' own menus are cast back
            event.<ShulkerBoxMenu, PortableShulkerBoxScreen>register(ModMenus.PORTABLE_SHULKER_BOX.get(),
                (menu, inventory, title) -> new PortableShulkerBoxScreen((PortableShulkerBoxMenu) menu, inventory, title));
            event.<ChestMenu, PortableChestScreen>register(ModMenus.PORTABLE_CHEST.get(),
                (menu, inventory, title) -> new PortableChestScreen((PortableChestMenu) menu, inventory, title));
        }
    }
}
