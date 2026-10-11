package com.lincstoel.integratedui.compat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The equipment slots of an integrated inventory menu: which backend provides them, and its slot groups. Built by
 * the server and sent to the client, which creates its slots from this alone (not from its own copy of the config).
 */
public record EquipmentLayout(Equipment.Kind backend, List<EquipmentGroup> groups)
{
    public static final EquipmentLayout NONE = new EquipmentLayout(Equipment.Kind.NONE, List.of());

    public static final StreamCodec<ByteBuf, EquipmentLayout> STREAM_CODEC = StreamCodec.composite(
        Equipment.Kind.STREAM_CODEC, EquipmentLayout::backend,
        EquipmentGroup.LIST_STREAM_CODEC, EquipmentLayout::groups,
        EquipmentLayout::new
    );

    /** The number of slots. */
    public int size()
    {
        return this.groups.stream().mapToInt(EquipmentGroup::size).sum();
    }
}
