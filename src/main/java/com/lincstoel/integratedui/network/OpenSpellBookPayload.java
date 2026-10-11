package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.arsnouveau.ArsNouveauCompat;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: open the Ars Nouveau spell book in the given slot of the player's inventory. */
public record OpenSpellBookPayload(int slot) implements CustomPacketPayload
{
    public static final Type<OpenSpellBookPayload> TYPE = new Type<>(IntegratedUI.rl("open_spell_book"));
    public static final StreamCodec<ByteBuf, OpenSpellBookPayload> STREAM_CODEC =
        ByteBufCodecs.VAR_INT.map(OpenSpellBookPayload::new, OpenSpellBookPayload::slot);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenSpellBookPayload payload, IPayloadContext context)
    {
        if(IntegratedUI.isArsNouveauEnabled() && context.player() instanceof ServerPlayer player)
        {
            CompatGuard.run(CompatGuard.ARS_NOUVEAU, () -> ArsNouveauCompat.openSpellBook(player, payload.slot));
        }
    }
}
