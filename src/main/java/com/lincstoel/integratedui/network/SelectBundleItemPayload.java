package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.BundleInventoryMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: selects an item in the bundle in one of the Bundles page's slots (see {@link BundleInventoryMenu}),
 * which clicking that slot then takes out. {@code slot} is the page's slot, from 0, and {@code item} an index into the
 * bundle's contents.
 */
public record SelectBundleItemPayload(int containerId, int slot, int item) implements CustomPacketPayload
{
    public static final Type<SelectBundleItemPayload> TYPE = new Type<>(IntegratedUI.rl("select_bundle_item"));
    public static final StreamCodec<ByteBuf, SelectBundleItemPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SelectBundleItemPayload::containerId,
        ByteBufCodecs.VAR_INT, SelectBundleItemPayload::slot,
        ByteBufCodecs.VAR_INT, SelectBundleItemPayload::item,
        SelectBundleItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(SelectBundleItemPayload payload, IPayloadContext context)
    {
        if(context.player().containerMenu instanceof BundleInventoryMenu menu && menu.containerId == payload.containerId)
        {
            menu.select(payload.slot, payload.item);
        }
    }
}
