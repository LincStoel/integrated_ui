package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.backpacked.BackpackedInventoryMenu;
import com.lincstoel.integratedui.compat.quark.QuarkBackpackMenu;
import com.lincstoel.integratedui.config.ServerConfig;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.ShulkerInventoryMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: rename the backpack, shulker box or sack shown in the open integrated inventory's band. A blank name
 * removes the custom name.
 */
public record RenamePayload(String name) implements CustomPacketPayload
{
    public static final int MAX_LENGTH = 50;
    public static final Type<RenamePayload> TYPE = new Type<>(IntegratedUI.rl("rename"));
    public static final StreamCodec<ByteBuf, RenamePayload> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(256).map(RenamePayload::new, RenamePayload::name);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(RenamePayload payload, IPayloadContext context)
    {
        if(!ServerConfig.get(ServerConfig.RENAMING) || !(context.player() instanceof ServerPlayer player)
            || !(player.containerMenu instanceof IntegratedInventoryMenu menu))
        {
            return;
        }
        String name = StringUtil.filterText(payload.name);
        if(name.length() > MAX_LENGTH)
        {
            return;
        }
        if(menu instanceof ShulkerInventoryMenu shulkerMenu)
        {
            shulkerMenu.rename(player, name);
        }
        // A backpack is only ever shown with Backpacked installed
        else if(menu.getData().hasBackpack())
        {
            BackpackedInventoryMenu.rename(player, menu.getBackpackIndex(), name);
        }
        // As is a Quark backpack with Quark
        else if(menu.getData().hasQuarkBackpack())
        {
            QuarkBackpackMenu.rename(player, name);
        }
    }
}
