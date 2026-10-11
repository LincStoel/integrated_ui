package com.lincstoel.integratedui.compat.curios;

import com.lincstoel.integratedui.IntegratedUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.common.network.client.CPacketOpenCurios;

/** Client-only Curios calls, kept separate so the common backend never links client classes. */
final class CuriosClientAccess
{
    private CuriosClientAccess() {}

    /**
     * Opens Curios' own screen (with its cosmetic slots and render toggles), as the button Curios adds to the vanilla
     * inventory does: the cursor stack goes along. Curios' packet isn't part of its API, so if it is missing (a newer
     * Curios), this does nothing.
     */
    static boolean openScreen()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if(player == null)
        {
            return false;
        }
        try
        {
            ItemStack carried = player.containerMenu.getCarried();
            player.containerMenu.setCarried(ItemStack.EMPTY);
            PacketDistributor.sendToServer(new CPacketOpenCurios(carried));
            return true;
        }
        catch(LinkageError e)
        {
            IntegratedUI.LOGGER.error("Couldn't open the Curios screen; this Curios version may not be supported", e);
            return false;
        }
    }
}
