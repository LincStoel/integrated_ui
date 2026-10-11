package com.lincstoel.integratedui.compat.aether;

import com.aetherteam.aether.AetherConfig;
import com.aetherteam.aether.client.event.hooks.GuiHooks;
import com.aetherteam.aether.client.gui.screen.inventory.AetherAccessoriesScreen;
import com.aetherteam.aether.network.packet.serverbound.OpenAccessoriesPacket;
import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gives our inventory screens the Aether's accessories button (the little cloud on the player portrait), which the
 * Aether only adds to the vanilla inventory. Only used when the Aether is installed.
 */
public final class AetherClient
{
    private AetherClient() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> CompatGuard.run(CompatGuard.AETHER, () -> onScreenInit(event)));
    }

    /** Whether the screen is the Aether's accessories screen, whose button back to the inventory needs care (see ClientEvents). */
    public static boolean isAccessoriesScreen(Screen screen)
    {
        return screen instanceof AetherAccessoriesScreen;
    }

    private static void onScreenInit(ScreenEvent.Init.Post event)
    {
        // Off when the Aether's button is disabled, or when it uses Accessories' default menu instead of its own
        if(IntegratedUI.isAetherEnabled() && event.getScreen() instanceof AbstractContainerScreen<?> screen
            && screen.getMenu() instanceof IntegratedInventoryMenu && GuiHooks.isAccessoryButtonEnabled())
        {
            event.addListener(new AccessoriesButton(screen));
        }
    }

    /** Opens the Aether's accessories menu, as its own button does from the vanilla inventory. */
    private static void openAccessories()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if(player != null)
        {
            ItemStack carried = player.containerMenu.getCarried();
            player.containerMenu.setCarried(ItemStack.EMPTY);
            PacketDistributor.sendToServer(new OpenAccessoriesPacket(carried));
        }
    }

    /**
     * The Aether's own button only knows where to sit on the vanilla inventory, so this one follows the screen itself
     * (which moves when the recipe book is toggled), at the position set in the Aether's client config.
     */
    private static class AccessoriesButton extends ImageButton
    {
        private final AbstractContainerScreen<?> screen;

        AccessoriesButton(AbstractContainerScreen<?> screen)
        {
            super(0, 0, 12, 8, AetherAccessoriesScreen.ACCESSORIES_BUTTON, button -> openAccessories());
            this.screen = screen;
            this.updatePosition();
        }

        private void updatePosition()
        {
            this.setPosition(this.screen.getGuiLeft() + AetherConfig.CLIENT.button_inventory_x.get(),
                this.screen.getGuiTop() + AetherConfig.CLIENT.button_inventory_y.get());
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            this.updatePosition();
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }
}
