package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Mounts;
import com.lincstoel.integratedui.menu.Navigation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: open the integrated inventory showing what was last shown in the band ({@link Navigation#CURRENT},
 * or nothing if there is nothing to show), or switch to the previous/next backpack or shulker box. {@code
 * inventoryKey} is set when it is the inventory key pressed while riding something with a screen of its own, which
 * that screen still opens for if the band can't show it (see {@link Mounts#handleInventoryKey}).
 */
public record OpenInventoryPayload(Navigation navigation, boolean inventoryKey) implements CustomPacketPayload
{
    public static final Type<OpenInventoryPayload> TYPE = new Type<>(IntegratedUI.rl("open_inventory"));
    public static final StreamCodec<ByteBuf, OpenInventoryPayload> STREAM_CODEC = StreamCodec.composite(
        Navigation.STREAM_CODEC, OpenInventoryPayload::navigation,
        ByteBufCodecs.BOOL, OpenInventoryPayload::inventoryKey,
        OpenInventoryPayload::new);

    public OpenInventoryPayload(Navigation navigation)
    {
        this(navigation, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(OpenInventoryPayload payload, IPayloadContext context)
    {
        if(context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator())
        {
            if(payload.inventoryKey && Mounts.handleInventoryKey(player))
            {
                return;
            }
            IntegratedInventoryMenu.open(player, payload.navigation);
        }
    }
}
