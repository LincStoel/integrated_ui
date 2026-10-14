package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.menu.ContainerItems;
import com.lincstoel.integratedui.network.OpenContainerItemPayload;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opens container items from the inventory, as Quark does for shulker boxes: right-clicking one shows it in its own
 * screen (see {@link ContainerItems#openOwnScreen}), with its own slot locked (see {@link OpenFromInventory}). Only
 * items in {@link ContainerItems#OPENS_OWN_SCREEN} (shulker boxes and sacks by default) are opened. In the inventory,
 * shulker boxes are opened this way in place of Quark's held shulker box screen (which flickers on opening and
 * closing), unless {@link ClientConfig#OVERRIDE_QUARK_SHULKER_BOXES} leaves them to Quark.
 */
public final class ContainerItemClient implements OpenFromInventory.Opener
{
    private ContainerItemClient() {}

    public static void register()
    {
        OpenFromInventory.addOpener(new ContainerItemClient());
    }

    @Override
    public boolean canOpen(ItemStack stack, AbstractContainerScreen<?> screen)
    {
        return ClientConfig.RIGHT_CLICK_OPENS_CONTAINERS.get() && ContainerItems.opensOwnScreen(stack) && !leftToQuark(stack, screen);
    }

    /** Whether Quark should open the stack itself: it opens shulker boxes, but only from the inventory. */
    private static boolean leftToQuark(ItemStack stack, AbstractContainerScreen<?> screen)
    {
        return !ClientConfig.get(ClientConfig.OVERRIDE_QUARK_SHULKER_BOXES) && screen.getMenu() instanceof InventoryMenu
            && IntegratedUI.isQuarkLoaded() && CompatGuard.test(CompatGuard.QUARK, () -> QuarkClientCompat.opensShulkerBox(stack));
    }

    @Override
    public void open(int inventorySlot)
    {
        PacketDistributor.sendToServer(new OpenContainerItemPayload(inventorySlot));
    }
}
