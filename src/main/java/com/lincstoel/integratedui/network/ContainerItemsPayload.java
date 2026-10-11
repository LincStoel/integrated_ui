package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.ContainerItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Set;

/**
 * Server to client: which items keep their contents when broken (see {@link ContainerItems#isContainerItem}), as only
 * the server has the loot tables that tell. Sent on joining, and again whenever datapacks are reloaded.
 */
public record ContainerItemsPayload(List<Item> items) implements CustomPacketPayload
{
    public static final Type<ContainerItemsPayload> TYPE = new Type<>(IntegratedUI.rl("container_items"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerItemsPayload> STREAM_CODEC =
        ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list()).map(ContainerItemsPayload::new, ContainerItemsPayload::items);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(ContainerItemsPayload payload, IPayloadContext context)
    {
        ContainerItems.setKeepingContents(Set.copyOf(payload.items));
    }
}
