package com.lincstoel.integratedui.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link net.minecraft.world.SimpleMenuProvider} that replaces whatever menu is open without closing its screen
 * on the client first. Closing it would show the world for a moment, grab the mouse (turning the camera with it) and
 * recentre the cursor before the new screen arrives. Used for every menu we switch to while a screen is open.
 */
public record SeamlessMenuProvider(MenuConstructor constructor, Component title) implements MenuProvider
{
    @Override
    public Component getDisplayName()
    {
        return this.title;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
    {
        return this.constructor.createMenu(containerId, inventory, player);
    }

    @Override
    public boolean shouldTriggerClientSideContainerClosingOnOpen()
    {
        return false;
    }
}
