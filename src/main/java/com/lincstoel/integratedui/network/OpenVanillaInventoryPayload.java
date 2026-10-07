package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server to client: the backpack inventory couldn't be opened (no backpack equipped), so open the vanilla
 * inventory screen that the client skipped when it asked for the backpack inventory.
 */
public record OpenVanillaInventoryPayload() implements CustomPacketPayload
{
    public static final OpenVanillaInventoryPayload INSTANCE = new OpenVanillaInventoryPayload();
    public static final Type<OpenVanillaInventoryPayload> TYPE = new Type<>(IntegratedUI.rl("open_vanilla_inventory"));
    public static final StreamCodec<ByteBuf, OpenVanillaInventoryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenVanillaInventoryPayload payload, IPayloadContext context)
    {
        if(FMLEnvironment.dist.isClient())
        {
            com.lincstoel.integratedui.client.ClientEvents.openVanillaInventory();
        }
    }
}
