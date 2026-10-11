package com.lincstoel.integratedui.compat;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.accessories.AccessoriesBackend;
import com.lincstoel.integratedui.compat.curios.CuriosBackend;
import com.lincstoel.integratedui.config.ServerConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.entity.player.Player;

/**
 * Picks the equipment slot provider. Accessories wins when present: with the Accessories Compatibility Layer
 * installed, the Curios API is redirected into Accessories, so Accessories already holds the Curios slots.
 * <p>
 * The server chooses from its config on every call, so a change applies from the next time the inventory is opened.
 * It sends its choice along with the menu (see {@link EquipmentLayout}), and the client uses that one.
 */
public final class Equipment
{
    private static EquipmentBackend accessories;
    private static EquipmentBackend curios;

    private Equipment() {}

    /** Server side: the equipment slots to give the player's menu. */
    public static EquipmentLayout collect(Player player)
    {
        Kind kind = currentKind();
        return new EquipmentLayout(kind, get(kind).collectGroups(player));
    }

    /** Server side: the backend in use. */
    public static EquipmentBackend get()
    {
        return get(currentKind());
    }

    /** The given backend, or none if its mod isn't installed (on this side). Calls into it are guarded, see {@link GuardedBackend}. */
    public static EquipmentBackend get(Kind kind)
    {
        return switch(kind)
        {
            case ACCESSORIES -> {
                if(!IntegratedUI.isAccessoriesLoaded())
                {
                    yield EquipmentBackend.NONE;
                }
                if(accessories == null)
                {
                    accessories = CompatGuard.get(CompatGuard.ACCESSORIES, () -> new GuardedBackend(CompatGuard.ACCESSORIES, new AccessoriesBackend()), EquipmentBackend.NONE);
                }
                yield accessories;
            }
            case CURIOS -> {
                if(!IntegratedUI.isCuriosLoaded())
                {
                    yield EquipmentBackend.NONE;
                }
                if(curios == null)
                {
                    curios = CompatGuard.get(CompatGuard.CURIOS, () -> new GuardedBackend(CompatGuard.CURIOS, new CuriosBackend()), EquipmentBackend.NONE);
                }
                yield curios;
            }
            case NONE -> EquipmentBackend.NONE;
        };
    }

    /** Server side: which backend is in use. */
    public static Kind currentKind()
    {
        if(!ServerConfig.get(ServerConfig.EQUIPMENT_SLOTS))
        {
            return Kind.NONE;
        }
        if(IntegratedUI.isAccessoriesEnabled())
        {
            return Kind.ACCESSORIES;
        }
        return IntegratedUI.isCuriosEnabled() ? Kind.CURIOS : Kind.NONE;
    }

    /** The equipment slot providers. */
    public enum Kind
    {
        NONE, CURIOS, ACCESSORIES;

        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(Kind::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Kind::ordinal);
    }
}
