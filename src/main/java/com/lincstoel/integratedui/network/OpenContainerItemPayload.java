package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.ContainerItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: open the container item in the given slot of the player's inventory in its own screen (see
 * {@link ContainerItems#openOwnScreen}), from any open menu that shows that slot.
 */
public record OpenContainerItemPayload(int slot) implements CustomPacketPayload
{
    public static final Type<OpenContainerItemPayload> TYPE = new Type<>(IntegratedUI.rl("open_container_item"));
    public static final StreamCodec<ByteBuf, OpenContainerItemPayload> STREAM_CODEC =
        ByteBufCodecs.VAR_INT.map(OpenContainerItemPayload::new, OpenContainerItemPayload::slot);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenContainerItemPayload payload, IPayloadContext context)
    {
        if(!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        Inventory inventory = player.getInventory();
        int slot = payload.slot;
        if(!player.isAlive() || player.isSpectator() || !player.containerMenu.getCarried().isEmpty() || slot < 0
            || slot >= inventory.getContainerSize() || !showsSlot(player, slot) || !ContainerItems.opensOwnScreen(inventory.getItem(slot)))
        {
            return;
        }
        ContainerItems.openOwnScreen(player, slot);
    }

    /** Whether the open menu shows the given slot of the player's inventory, so the player could have clicked it. */
    private static boolean showsSlot(ServerPlayer player, int index)
    {
        for(Slot slot : player.containerMenu.slots)
        {
            if(slot.container == player.getInventory() && slot.getContainerSlot() == index)
            {
                return slot.mayPickup(player);
            }
        }
        return false;
    }
}
