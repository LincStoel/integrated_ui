package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.config.StartupConfig;
import com.lincstoel.integratedui.menu.ModMenus;
import com.lincstoel.integratedui.network.OpenBackpackInventoryPayload;
import com.mrcrayfish.backpacked.common.Navigate;
import com.mrcrayfish.backpacked.core.ModSyncedDataKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.violetmoon.quark.addons.oddities.module.BackpackModule;

/**
 * Swaps the vanilla inventory screen for the backpack inventory, the same way Quark does for its backpack:
 * the vanilla screen is cancelled and the server is asked to open the backpack menu instead.
 */
@EventBusSubscriber(modid = IntegratedUI.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents
{
    /** The vanilla inventory screen we already asked the server to replace (or were told to keep). */
    @Nullable
    private static Screen handledScreen;
    private static boolean allowNextInventoryScreen;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event)
    {
        if(allowNextInventoryScreen)
        {
            allowNextInventoryScreen = false;
            return;
        }
        if(shouldReplace(event.getNewScreen()))
        {
            event.setCanceled(true);
            requestBackpackInventory();
        }
    }

    /** Covers equipping a backpack while the vanilla inventory is already open. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event)
    {
        Screen screen = Minecraft.getInstance().screen;
        if(screen != null && screen != handledScreen && shouldReplace(screen))
        {
            handledScreen = screen;
            requestBackpackInventory();
        }
    }

    private static void requestBackpackInventory()
    {
        PacketDistributor.sendToServer(new OpenBackpackInventoryPayload(Navigate.CURRENT));
    }

    private static boolean shouldReplace(@Nullable Screen screen)
    {
        if(screen == null || screen.getClass() != InventoryScreen.class || !ClientConfig.REPLACE_INVENTORY_SCREEN.get())
        {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null || player.isSpectator() || player.getAbilities().instabuild)
        {
            return false;
        }
        // If Quark's backpack is allowed and worn, Quark's own screen takes precedence
        if(!StartupConfig.disableQuarkBackpack() && BackpackModule.isEntityWearingBackpack(player))
        {
            return false;
        }
        return hasBackpackEquipped(player);
    }

    /**
     * Backpacked doesn't sync the equipped backpacks to the client, but it does sync the worn backpack's
     * cosmetic properties (for rendering), which the server sets whenever at least one backpack is equipped and
     * clears when none are. The server re-checks when opening and falls back to the vanilla inventory.
     */
    private static boolean hasBackpackEquipped(LocalPlayer player)
    {
        return ModSyncedDataKeys.COSMETIC_PROPERTIES.getValue(player).isPresent();
    }

    /** The server couldn't open the backpack inventory, so show the vanilla one the player asked for. */
    public static void openVanillaInventory()
    {
        Minecraft mc = Minecraft.getInstance();
        if(mc.player != null && mc.screen == null)
        {
            InventoryScreen screen = new InventoryScreen(mc.player);
            handledScreen = screen;
            allowNextInventoryScreen = true;
            mc.setScreen(screen);
        }
    }

    @EventBusSubscriber(modid = IntegratedUI.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus
    {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event)
        {
            event.register(ModMenus.BACKPACK_INVENTORY.get(), BackpackInventoryScreen::new);
        }
    }
}
