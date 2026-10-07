package com.lincstoel.integratedui.compat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * A run of equipment slots of one type (e.g. three "ring" slots). The server sends the exact list it built its
 * menu from, so the client always creates the same number of slots in the same order.
 */
public record EquipmentGroup(String id, int size)
{
    public static final StreamCodec<ByteBuf, EquipmentGroup> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, EquipmentGroup::id,
        ByteBufCodecs.VAR_INT, EquipmentGroup::size,
        EquipmentGroup::new
    );

    public static final StreamCodec<ByteBuf, List<EquipmentGroup>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());
}
