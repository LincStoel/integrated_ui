package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.compat.EquipmentGroup;
import com.mrcrayfish.backpacked.common.backpack.UnlockableSlots;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Everything the client needs to build an identical {@link BackpackInventoryMenu}.
 */
public record OpenData(int backpackIndex, int columns, int rows, UnlockableSlots slots, boolean hasPrevious,
                       boolean hasNext, Component title, ItemStack backpack, List<EquipmentGroup> equipment)
{
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeVarInt(data.backpackIndex);
            buf.writeVarInt(data.columns);
            buf.writeVarInt(data.rows);
            UnlockableSlots.STREAM_CODEC.encode(buf, data.slots);
            buf.writeBoolean(data.hasPrevious);
            buf.writeBoolean(data.hasNext);
            ComponentSerialization.STREAM_CODEC.encode(buf, data.title);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, data.backpack);
            EquipmentGroup.LIST_STREAM_CODEC.encode(buf, data.equipment);
        },
        buf -> new OpenData(
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            UnlockableSlots.STREAM_CODEC.decode(buf),
            buf.readBoolean(),
            buf.readBoolean(),
            ComponentSerialization.STREAM_CODEC.decode(buf),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
            EquipmentGroup.LIST_STREAM_CODEC.decode(buf)
        )
    );

    public int backpackSize()
    {
        return this.columns * this.rows;
    }

    public int equipmentSize()
    {
        return this.equipment.stream().mapToInt(EquipmentGroup::size).sum();
    }
}
