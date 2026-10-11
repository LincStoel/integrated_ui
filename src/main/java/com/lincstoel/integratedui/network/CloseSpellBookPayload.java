package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.arsnouveau.ArsNouveauCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the spell book opened from the inventory was closed, so put it back where it came from. */
public record CloseSpellBookPayload() implements CustomPacketPayload
{
    public static final CloseSpellBookPayload INSTANCE = new CloseSpellBookPayload();
    public static final Type<CloseSpellBookPayload> TYPE = new Type<>(IntegratedUI.rl("close_spell_book"));
    public static final StreamCodec<ByteBuf, CloseSpellBookPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(CloseSpellBookPayload payload, IPayloadContext context)
    {
        if(IntegratedUI.isArsNouveauEnabled() && context.player() instanceof ServerPlayer player)
        {
            ArsNouveauCompat.restoreSwap(player);
        }
    }
}
