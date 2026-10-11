package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.ShulkerInventoryMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: open the container item shown in the open integrated inventory's band in its own screen. */
public record OpenShulkerBoxPayload() implements CustomPacketPayload
{
    public static final OpenShulkerBoxPayload INSTANCE = new OpenShulkerBoxPayload();
    public static final Type<OpenShulkerBoxPayload> TYPE = new Type<>(IntegratedUI.rl("open_shulker_box"));
    public static final StreamCodec<ByteBuf, OpenShulkerBoxPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenShulkerBoxPayload payload, IPayloadContext context)
    {
        if(context.player() instanceof ServerPlayer player && player.containerMenu instanceof ShulkerInventoryMenu menu)
        {
            menu.openOwnScreen(player);
        }
    }
}
