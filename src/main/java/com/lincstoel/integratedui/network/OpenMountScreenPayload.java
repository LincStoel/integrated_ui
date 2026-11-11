package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.MountInventoryMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: open the mount shown on the Mount page in its own screen, from the band header. */
public record OpenMountScreenPayload() implements CustomPacketPayload
{
    public static final OpenMountScreenPayload INSTANCE = new OpenMountScreenPayload();
    public static final Type<OpenMountScreenPayload> TYPE = new Type<>(IntegratedUI.rl("open_mount_screen"));
    public static final StreamCodec<ByteBuf, OpenMountScreenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenMountScreenPayload payload, IPayloadContext context)
    {
        if(context.player() instanceof ServerPlayer player && player.containerMenu instanceof MountInventoryMenu menu)
        {
            menu.openOwnScreen(player);
        }
    }
}
