package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.SuspendedMenus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the screen of an item opened from a container's screen closed, and the client went back to the
 * container's screen, so make its held menu the open one again (see {@link SuspendedMenus}).
 */
public record ReturnToMenuPayload() implements CustomPacketPayload
{
    public static final ReturnToMenuPayload INSTANCE = new ReturnToMenuPayload();
    public static final Type<ReturnToMenuPayload> TYPE = new Type<>(IntegratedUI.rl("return_to_menu"));
    public static final StreamCodec<ByteBuf, ReturnToMenuPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(ReturnToMenuPayload payload, IPayloadContext context)
    {
        if(context.player() instanceof ServerPlayer player)
        {
            SuspendedMenus.returnToHeld(player);
        }
    }
}
