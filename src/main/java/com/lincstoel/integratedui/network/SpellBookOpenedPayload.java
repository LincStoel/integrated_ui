package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.arsnouveau.ArsNouveauClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the spell book asked for by {@link OpenSpellBookPayload} is now in the given hand, so open its
 * screen.
 */
public record SpellBookOpenedPayload(InteractionHand hand) implements CustomPacketPayload
{
    public static final Type<SpellBookOpenedPayload> TYPE = new Type<>(IntegratedUI.rl("spell_book_opened"));
    public static final StreamCodec<ByteBuf, SpellBookOpenedPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(
        mainHand -> new SpellBookOpenedPayload(mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND),
        payload -> payload.hand == InteractionHand.MAIN_HAND);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(SpellBookOpenedPayload payload, IPayloadContext context)
    {
        if(IntegratedUI.isArsNouveauEnabled())
        {
            CompatGuard.run(CompatGuard.ARS_NOUVEAU, () -> ArsNouveauClient.onBookReady(payload.hand));
        }
    }
}
