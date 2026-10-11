package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.compat.EquipmentLayout;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.item.ItemStack;

/**
 * Everything the client needs to build an identical {@link IntegratedInventoryMenu}. The band between the crafting
 * area and the main inventory shows either nothing, a Backpacked backpack (only with Backpacked installed), a
 * container item such as a shulker box or Supplementaries sack carried in the inventory (see ContainerItems), or the
 * Quark backpack worn in the chest slot (only with Quark installed). With a backpack, Backpacked-specific data follows
 * this on the network (see BackpackedInventoryMenu); with a container item, the number of usable slots and whether it
 * opens in its own screen do (see ShulkerInventoryMenu).
 *
 * @param backpackIndex the shown backpack's Backpacked index, or -1 if no backpack is shown
 * @param tint          the colour (RGB) the band is drawn in, for a container item (see ContainerItems#getTint); -1 otherwise
 */
public record OpenData(Band band, int backpackIndex, int columns, int rows, int tint,
                       Component title, ItemStack icon, EquipmentLayout equipment)
{
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenData> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            Band.STREAM_CODEC.encode(buf, data.band);
            buf.writeVarInt(data.backpackIndex);
            buf.writeVarInt(data.columns);
            buf.writeVarInt(data.rows);
            buf.writeInt(data.tint);
            ComponentSerialization.STREAM_CODEC.encode(buf, data.title);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, data.icon);
            EquipmentLayout.STREAM_CODEC.encode(buf, data.equipment);
        },
        buf -> new OpenData(
            Band.STREAM_CODEC.decode(buf),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readInt(),
            ComponentSerialization.STREAM_CODEC.decode(buf),
            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
            EquipmentLayout.STREAM_CODEC.decode(buf)
        )
    );

    /** Just the inventory and equipment panel, with nothing to show in the band. */
    public static OpenData withoutBand(EquipmentLayout equipment)
    {
        return new OpenData(Band.NONE, -1, 0, 0, -1, Component.translatable("container.inventory"), ItemStack.EMPTY, equipment);
    }

    /** Whether anything is shown in the band; without it the menu is just the inventory and equipment panel. */
    public boolean hasBand()
    {
        return this.band != Band.NONE;
    }

    /** Whether a Backpacked backpack is shown, which implies Backpacked is installed. */
    public boolean hasBackpack()
    {
        return this.band == Band.BACKPACK;
    }

    /** Whether a container item, such as a shulker box or sack, is shown. */
    public boolean hasContainerItem()
    {
        return this.band == Band.CONTAINER_ITEM;
    }

    /** Whether the worn Quark backpack is shown, which implies Quark is installed. */
    public boolean hasQuarkBackpack()
    {
        return this.band == Band.QUARK_BACKPACK;
    }

    public int bandSize()
    {
        return this.columns * this.rows;
    }

    public int equipmentSize()
    {
        return this.equipment.size();
    }

    /** What the band between the crafting area and the main inventory shows. */
    public enum Band
    {
        NONE, BACKPACK, CONTAINER_ITEM, QUARK_BACKPACK;

        public static final StreamCodec<ByteBuf, Band> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(Band::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Band::ordinal);
    }
}
