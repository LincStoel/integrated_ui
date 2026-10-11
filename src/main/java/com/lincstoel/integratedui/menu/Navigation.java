package com.lincstoel.integratedui.menu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * Which backpack to show when opening the integrated inventory: the selected one, or the previous/next equipped
 * one. Mirrors Backpacked's own Navigate, which can't be used here since Backpacked is optional.
 */
public enum Navigation
{
    CURRENT, PREVIOUS, NEXT;

    /** Sent by the client, so an unknown value is read as {@link #CURRENT} rather than failing. */
    public static final StreamCodec<ByteBuf, Navigation> STREAM_CODEC = ByteBufCodecs.idMapper(
        ByIdMap.continuous(Navigation::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Navigation::ordinal);
}
