package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: open the integrated inventory showing what was last shown in the band ({@link Navigation#CURRENT},
 * or nothing if there is nothing to show), or switch to the previous/next backpack or shulker box.
 */
public record OpenInventoryPayload(Navigation navigation) implements CustomPacketPayload
{
    public static final Type<OpenInventoryPayload> TYPE = new Type<>(IntegratedUI.rl("open_inventory"));
    public static final StreamCodec<ByteBuf, OpenInventoryPayload> STREAM_CODEC =
        Navigation.STREAM_CODEC.map(OpenInventoryPayload::new, OpenInventoryPayload::navigation);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenInventoryPayload payload, IPayloadContext context)
    {
        if(context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator())
        {
            IntegratedInventoryMenu.open(player, payload.navigation);
        }
    }
}
