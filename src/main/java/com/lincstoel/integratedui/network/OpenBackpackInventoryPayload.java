package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.BackpackInventoryMenu;
import com.mrcrayfish.backpacked.common.Navigate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: open the backpack inventory for the selected backpack ({@link Navigate#CURRENT}), or switch
 * to the previous/next equipped backpack.
 */
public record OpenBackpackInventoryPayload(Navigate navigate) implements CustomPacketPayload
{
    public static final Type<OpenBackpackInventoryPayload> TYPE = new Type<>(IntegratedUI.rl("open_backpack_inventory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackInventoryPayload> STREAM_CODEC =
        Navigate.STREAM_CODEC.map(OpenBackpackInventoryPayload::new, OpenBackpackInventoryPayload::navigate);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenBackpackInventoryPayload payload, IPayloadContext context)
    {
        if(context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator())
        {
            BackpackInventoryMenu.open(player, payload.navigate);
        }
    }
}
