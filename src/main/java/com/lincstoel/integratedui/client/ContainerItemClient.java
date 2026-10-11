package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.menu.ContainerItems;
import com.lincstoel.integratedui.network.OpenContainerItemPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Opens container items from the inventory, as Quark does for shulker boxes: right-clicking one in the player's
 * inventory with an empty cursor shows it in its own screen (see {@link ContainerItems#openOwnScreen}), with its own
 * slot locked. This works in any container screen that shows the player's inventory; closing the item's screen
 * returns to the inventory if it was opened from there, or else to the world (see {@link PortableScreens}). Only items
 * in {@link ContainerItems#OPENS_OWN_SCREEN} (shulker boxes and sacks by default) are opened, and in the inventory
 * shulker boxes are left to Quark if it opens them itself.
 */
public final class ContainerItemClient
{
    private ContainerItemClient() {}

    public static void register()
    {
        NeoForge.EVENT_BUS.addListener(ContainerItemClient::onMouseClicked);
        NeoForge.EVENT_BUS.addListener(ContainerItemClient::onTooltip);
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
            PortableScreens.noteOpenedFrom(event.getScreen());
            PacketDistributor.sendToServer(new OpenContainerItemPayload(slot.getContainerSlot()));
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

    /**
     * The hovered slot, if it holds a container item in the player's inventory that a right-click would open. Works
     * in any container screen showing the player's inventory (a chest's, say), except the creative inventory.
     */
    @Nullable
    private static Slot getOpenableSlot(@Nullable Screen screen)
    {
        if(!(screen instanceof AbstractContainerScreen<?> containerScreen) || screen instanceof CreativeModeInventoryScreen
            || Screen.hasShiftDown() || !ClientConfig.RIGHT_CLICK_OPENS_CONTAINERS.get())
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
            || !canOpen(slot.getItem(), containerScreen.getMenu() instanceof InventoryMenu))
        {
            return null;
        }
        return slot;
    }

    private static boolean canOpen(ItemStack stack, boolean fromInventory)
    {
        // Quark opens shulker boxes itself, but only from the inventory
        return ContainerItems.opensOwnScreen(stack) && !(fromInventory && IntegratedUI.isQuarkLoaded()
            && CompatGuard.test(CompatGuard.QUARK, () -> QuarkClientCompat.opensShulkerBox(stack)));
    }
}
