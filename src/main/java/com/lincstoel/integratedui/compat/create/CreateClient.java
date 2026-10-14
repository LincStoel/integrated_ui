package com.lincstoel.integratedui.compat.create;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.client.OpenFromInventory;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.network.OpenFilterPayload;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of opening a filter from the inventory, see {@link CreateCompat}: right-clicking a Create filter opens
 * its screen (see {@link OpenFromInventory}). Only used when Create is installed.
 */
public final class CreateClient implements OpenFromInventory.Opener
{
    private CreateClient() {}

    public static void register()
    {
        OpenFromInventory.addOpener(new CreateClient());
    }

    @Override
    public boolean canOpen(ItemStack stack, AbstractContainerScreen<?> screen)
    {
        // Not from a filter's own screen, where right-clicking the inventory picks up items to filter by
        return ClientConfig.RIGHT_CLICK_OPENS_FILTERS.get() && IntegratedUI.isCreateEnabled()
            && CompatGuard.test(CompatGuard.CREATE, () -> CreateCompat.isFilter(stack) && !CreateCompat.isFilterMenu(screen.getMenu()));
    }

    @Override
    public void open(int inventorySlot)
    {
        PacketDistributor.sendToServer(new OpenFilterPayload(inventorySlot));
    }
}
