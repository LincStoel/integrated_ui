package com.lincstoel.integratedui.network;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.BackpackInventoryMenu;
import com.mrcrayfish.backpacked.BackpackHelper;
import com.mrcrayfish.backpacked.common.Navigate;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: rename the backpack shown in the open backpack inventory. Mirrors Backpacked's own rename
 * handling, which only accepts renames while its own backpack screen is open.
 */
public record RenameBackpackPayload(String name) implements CustomPacketPayload
{
    public static final int MAX_LENGTH = 50;
    public static final Type<RenameBackpackPayload> TYPE = new Type<>(IntegratedUI.rl("rename_backpack"));
    public static final StreamCodec<ByteBuf, RenameBackpackPayload> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(256).map(RenameBackpackPayload::new, RenameBackpackPayload::name);

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(RenameBackpackPayload payload, IPayloadContext context)
    {
        if(!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof BackpackInventoryMenu menu))
        {
            return;
        }
        ItemStack stack = BackpackHelper.getBackpackStack(player, menu.getBackpackIndex());
        String name = StringUtil.filterText(payload.name);
        if(stack.isEmpty() || name.length() > MAX_LENGTH)
        {
            return;
        }
        if(StringUtil.isBlank(name))
        {
            stack.remove(DataComponents.CUSTOM_NAME);
        }
        else
        {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
        // Reopen so the new name is shown
        BackpackInventoryMenu.open(player, Navigate.CURRENT);
    }
}
