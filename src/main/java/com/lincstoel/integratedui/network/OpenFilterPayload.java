package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.create.CreateCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: open the Create filter in the given slot of the player's inventory. */
public record OpenFilterPayload(int slot) implements CustomPacketPayload
{
    public static final Type<OpenFilterPayload> TYPE = new Type<>(IntegratedUI.rl("open_filter"));
    public static final StreamCodec<ByteBuf, OpenFilterPayload> STREAM_CODEC =
        ByteBufCodecs.VAR_INT.map(OpenFilterPayload::new, OpenFilterPayload::slot);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenFilterPayload payload, IPayloadContext context)
    {
        if(IntegratedUI.isCreateEnabled() && context.player() instanceof ServerPlayer player)
        {
            CompatGuard.run(CompatGuard.CREATE, () -> CreateCompat.openFilter(player, payload.slot));
        }
    }
}
