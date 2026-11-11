package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.config.RegistryIdList;
import com.lincstoel.integratedui.config.ServerConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.common.util.FriendlyByteBufUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Mounts for the band's Mount page: whatever the player rides that has a screen of its own for its equipment and
 * inventory, which is what the inventory key opens while riding it. Rather than knowing each kind of mount, the
 * mount's own menu is built (as its {@link Recipe} says) and its slots other than the player's inventory are taken
 * over by {@link MountInventoryMenu}, so they behave just as in the mount's own screen. The recipe is found from
 * how the mount opens that screen:
 * <ul>
 * <li>Mounts that keep their inventory as a {@link ContainerEntity} (chest boats, Deep Aether's aerwhale) are menu
 * providers themselves, used directly.</li>
 * <li>Any other {@link HasCustomInventoryScreen} is asked to open its screen while that is being captured (see
 * ServerPlayerMixin): vanilla's horses (and every mod's mount built on {@link AbstractHorse}) open vanilla's horse
 * screen, which is captured as a {@link Recipe.Horse}; other mods' mounts (such as Altered Aether's aerwhale) open a
 * menu of their own, captured as a {@link Recipe.Menu}. Nothing is opened, sent or posted while capturing.</li>
 * </ul>
 * Server side, except for {@link #readShadow} and {@link #liftSlots}.
 */
public final class Mounts
{
    /** Mounts never shown in the band (while the mount list is a blacklist). Datapacks can add to it. */
    public static final TagKey<EntityType<?>> MOUNT_EXCLUDED = TagKey.create(Registries.ENTITY_TYPE, IntegratedUI.rl("mount_excluded"));

    private static final RegistryIdList<EntityType<?>> MOUNT_LIST = new RegistryIdList<>(BuiltInRegistries.ENTITY_TYPE, ServerConfig.MOUNT_LIST);
    /** The most ticks a probe of whether a mount can be shown is kept for (see {@link #findShowable}). */
    private static final int PROBE_TICKS = 20;
    private static final int KIND_HORSE = 0;
    private static final int KIND_MENU = 1;

    /** By player UUID: the last check of whether their vehicle can be shown. */
    private static final Map<UUID, Probe> PROBES = new HashMap<>();
    /** Mounts whose screen couldn't be captured because it threw, logged only once each. */
    private static final Set<EntityType<?>> FAILED = ConcurrentHashMap.newKeySet();

    /** While capturing: whose mount's screen is being captured, and what it opened. */
    @Nullable
    private static ServerPlayer capturing;
    @Nullable
    private static Recipe captured;
    /** While opening a mount's own screen from the band, so it replaces the inventory without closing its screen first. */
    private static boolean seamless;

    private Mounts() {}

    /** Whether the config lets the band show this kind of mount. */
    public static boolean isAllowed(EntityType<?> type)
    {
        if(!ServerConfig.get(ServerConfig.MOUNTS_IN_BAND))
        {
            return false;
        }
        boolean listed = MOUNT_LIST.matches(type);
        if(ServerConfig.get(ServerConfig.MOUNT_LIST_IS_WHITELIST))
        {
            return listed;
        }
        return !listed && !type.is(MOUNT_EXCLUDED);
    }

    /**
     * The player's vehicle, if the band can show it: it is allowed, and its own menu has any slots the player can use.
     * Checked at most once every {@link #PROBE_TICKS} for the same vehicle, as building that menu isn't free.
     */
    @Nullable
    public static Entity findShowable(ServerPlayer player)
    {
        Entity vehicle = player.getVehicle();
        if(vehicle == null || !isAllowed(vehicle.getType()))
        {
            PROBES.remove(player.getUUID());
            return null;
        }
        int now = player.server.getTickCount();
        Probe probe = PROBES.get(player.getUUID());
        if(probe == null || probe.vehicle != vehicle || now - probe.tick >= PROBE_TICKS || now < probe.tick)
        {
            probe = new Probe(vehicle, isShowable(player, vehicle), now);
            PROBES.put(player.getUUID(), probe);
        }
        return probe.showable ? vehicle : null;
    }

    private static boolean isShowable(ServerPlayer player, Entity vehicle)
    {
        Recipe recipe = capture(player, vehicle);
        if(recipe == null)
        {
            return false;
        }
        if(recipe instanceof Recipe.Menu menu && menu.provider() == vehicle && vehicle instanceof ContainerEntity)
        {
            // Without building its menu, which would have it tell everything nearby that it was opened and closed
            return true;
        }
        AbstractContainerMenu shadow = createShadow(recipe, player);
        if(shadow == null)
        {
            return false;
        }
        boolean usable = liftSlots(shadow).stream().anyMatch(Slot::isActive);
        shadow.removed(player);
        return usable;
    }

    /** Forgets the player's last check, so the next one looks again. */
    static void forget(Player player)
    {
        PROBES.remove(player.getUUID());
    }

    static void forgetAll()
    {
        PROBES.clear();
    }

    /**
     * How the vehicle's own menu is built, or null if it has none that can be. Asks it to open its screen while that is
     * being captured, unless it is a {@link ContainerEntity}.
     */
    @Nullable
    static Recipe capture(ServerPlayer player, Entity vehicle)
    {
        if(vehicle instanceof ContainerEntity container)
        {
            return container.getContainerSize() > 0 ? new Recipe.Menu(container, null) : null;
        }
        if(!(vehicle instanceof HasCustomInventoryScreen screen) || capturing != null)
        {
            return null;
        }
        capturing = player;
        captured = null;
        try
        {
            screen.openCustomInventoryScreen(player);
        }
        catch(RuntimeException e)
        {
            if(FAILED.add(vehicle.getType()))
            {
                IntegratedUI.LOGGER.warn("Couldn't capture the inventory screen of mount {}; it isn't shown in the band",
                    BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()), e);
            }
            captured = null;
        }
        finally
        {
            capturing = null;
        }
        Recipe recipe = captured;
        captured = null;
        return recipe;
    }

    /** From ServerPlayerMixin, as a horse screen opens: whether it was captured, and so isn't to be opened. */
    public static boolean captureHorse(ServerPlayer player, AbstractHorse horse, Container container)
    {
        if(capturing != player)
        {
            return false;
        }
        if(captured == null)
        {
            captured = new Recipe.Horse(horse, container, horse.getInventoryColumns());
        }
        return true;
    }

    /** From ServerPlayerMixin, as a menu opens: whether it was captured, and so isn't to be opened. */
    public static boolean captureMenu(ServerPlayer player, MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraData)
    {
        if(capturing != player)
        {
            return false;
        }
        if(captured == null)
        {
            captured = new Recipe.Menu(provider, extraData);
        }
        return true;
    }

    /** From ServerPlayerMixin, as a menu opens: while opening a mount's own screen from the band, opens it seamlessly. */
    public static MenuProvider wrapSeamless(MenuProvider provider)
    {
        return seamless && provider != null && provider.shouldTriggerClientSideContainerClosingOnOpen() ? new SeamlessProvider(provider) : provider;
    }

    /**
     * Opens the vehicle's own screen, as the inventory key would, straight over the open screen (see {@link
     * SuspendedMenus#open}, which the caller wraps this in).
     */
    static void openOwnScreen(ServerPlayer player, Entity vehicle)
    {
        seamless = true;
        try
        {
            if(vehicle instanceof HasCustomInventoryScreen screen)
            {
                screen.openCustomInventoryScreen(player);
            }
            else if(vehicle instanceof ContainerEntity container)
            {
                player.openMenu(container);
            }
        }
        finally
        {
            seamless = false;
        }
    }

    /**
     * The inventory key, pressed while riding: returns true if it was dealt with as vanilla does, by opening the
     * mount's own screen, because the band can't show the mount. Otherwise the integrated inventory is to be opened.
     */
    public static boolean handleInventoryKey(ServerPlayer player)
    {
        Entity vehicle = player.getVehicle();
        if(!(vehicle instanceof HasCustomInventoryScreen screen))
        {
            return false;
        }
        boolean allowed = isAllowed(vehicle.getType());
        if(allowed && (capture(player, vehicle) != null || vehicle instanceof AbstractHorse))
        {
            // Shown in the band, or a horse that isn't tamed, which has no screen to open: the inventory is better than nothing
            return false;
        }
        screen.openCustomInventoryScreen(player);
        return true;
    }

    /** Builds the mount's own menu, which is never opened: only its slots are used. Null if it can't be. */
    @Nullable
    static AbstractContainerMenu createShadow(Recipe recipe, ServerPlayer player)
    {
        try
        {
            return recipe.createShadow(0, player.getInventory(), player);
        }
        catch(RuntimeException e)
        {
            IntegratedUI.LOGGER.warn("Couldn't build the menu of mount {}", recipe, e);
            return null;
        }
    }

    /** The menu's slots other than the player's inventory, in order: the mount's. */
    public static List<Slot> liftSlots(AbstractContainerMenu menu)
    {
        List<Slot> lifted = new ArrayList<>();
        for(Slot slot : menu.slots)
        {
            if(!(slot.container instanceof Inventory))
            {
                lifted.add(slot);
            }
        }
        return lifted;
    }

    /**
     * Which of the lifted slots are storage (rather than equipment, such as a saddle). A horse's are known: its saddle
     * and armor come first. Otherwise storage is the largest block of slots in adjacent columns, 18 pixels apart,
     * spanning at least two columns, as the mount's own screen lays them out; any slots outside it are equipment.
     */
    static boolean[] findStorage(List<Slot> lifted, boolean horse)
    {
        boolean[] storage = new boolean[lifted.size()];
        if(horse)
        {
            for(int i = 2; i < storage.length; i++)
            {
                storage[i] = true;
            }
            return storage;
        }
        List<Integer> xs = lifted.stream().map(slot -> slot.x).distinct().sorted().toList();
        int bestStart = -1;
        int bestEnd = -1;
        int bestCount = 0;
        for(int start = 0; start < xs.size(); )
        {
            int end = start + 1;
            while(end < xs.size() && xs.get(end) - xs.get(end - 1) == 18)
            {
                end++;
            }
            int from = xs.get(start);
            int to = xs.get(end - 1);
            int count = (int) lifted.stream().filter(slot -> slot.x >= from && slot.x <= to).count();
            if(end - start >= 2 && count > bestCount)
            {
                bestStart = from;
                bestEnd = to;
                bestCount = count;
            }
            start = end;
        }
        for(int i = 0; i < storage.length; i++)
        {
            storage[i] = bestCount > 0 && lifted.get(i).x >= bestStart && lifted.get(i).x <= bestEnd;
        }
        return storage;
    }

    /** How many columns the mount's own screen shows its storage in. */
    static int countStorageColumns(List<Slot> lifted, boolean[] storage)
    {
        return (int) java.util.stream.IntStream.range(0, lifted.size()).filter(i -> storage[i]).map(i -> lifted.get(i).x).distinct().count();
    }

    /**
     * Client side: builds the mount's own menu as the server described it (see {@link Recipe#writeClient}), as its
     * own screen would have when opened. Null if it can't be, such as when the mount isn't loaded on the client.
     */
    @Nullable
    public static AbstractContainerMenu readShadow(int windowId, Inventory inventory, @Nullable Entity vehicle, RegistryFriendlyByteBuf buf)
    {
        int kind = buf.readVarInt();
        if(kind == KIND_HORSE)
        {
            int columns = buf.readVarInt();
            // As ClientPacketListener.handleHorseScreenOpen does
            return vehicle instanceof AbstractHorse horse
                ? new HorseInventoryMenu(windowId, inventory, new SimpleContainer(AbstractHorse.getInventorySize(columns)), horse, columns) : null;
        }
        ResourceLocation typeId = buf.readResourceLocation();
        byte[] bytes = buf.readByteArray();
        MenuType<?> type = BuiltInRegistries.MENU.get(typeId);
        if(type == null)
        {
            return null;
        }
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), buf.registryAccess(), buf.getConnectionType());
        try
        {
            return type.create(windowId, inventory, data);
        }
        catch(RuntimeException e)
        {
            IntegratedUI.LOGGER.warn("Couldn't build the menu of mount {} ({}) on the client", vehicle, typeId, e);
            return null;
        }
        finally
        {
            data.release();
        }
    }

    private record Probe(Entity vehicle, boolean showable, int tick) {}

    /** How a mount's own menu is built: on the server, and from what is sent, on the client. */
    sealed interface Recipe
    {
        AbstractContainerMenu createShadow(int windowId, Inventory inventory, Player player);

        /**
         * Works out what the client needs to build the same menu (see {@link #readShadow}), given the server's.
         * Throws if it can't.
         */
        Consumer<RegistryFriendlyByteBuf> clientData(AbstractContainerMenu shadow, RegistryAccess registryAccess);

        /** Vanilla's horse screen, opened by {@link AbstractHorse}s (horses, donkeys, llamas, camels and mods' mounts built on them). */
        record Horse(AbstractHorse horse, Container container, int columns) implements Recipe
        {
            @Override
            public AbstractContainerMenu createShadow(int windowId, Inventory inventory, Player player)
            {
                return new HorseInventoryMenu(windowId, inventory, this.container, this.horse, this.columns);
            }

            @Override
            public Consumer<RegistryFriendlyByteBuf> clientData(AbstractContainerMenu shadow, RegistryAccess registryAccess)
            {
                return buf -> {
                    buf.writeVarInt(KIND_HORSE);
                    buf.writeVarInt(this.columns);
                };
            }
        }

        /** A menu of the mount's own type, opened through a menu provider (with any extra data it sends the client). */
        record Menu(MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extraData) implements Recipe
        {
            @Override
            public AbstractContainerMenu createShadow(int windowId, Inventory inventory, Player player)
            {
                return this.provider.createMenu(windowId, inventory, player);
            }

            @Override
            public Consumer<RegistryFriendlyByteBuf> clientData(AbstractContainerMenu shadow, RegistryAccess registryAccess)
            {
                ResourceLocation typeId = BuiltInRegistries.MENU.getKey(shadow.getType());
                if(typeId == null)
                {
                    throw new IllegalStateException("Unregistered menu type " + shadow.getType());
                }
                // As ServerPlayer.openMenu sends it
                byte[] bytes = FriendlyByteBufUtil.writeCustomData(buf -> {
                    this.provider.writeClientSideData(shadow, buf);
                    if(this.extraData != null)
                    {
                        this.extraData.accept(buf);
                    }
                }, registryAccess);
                return buf -> {
                    buf.writeVarInt(KIND_MENU);
                    buf.writeResourceLocation(typeId);
                    buf.writeByteArray(bytes);
                };
            }
        }
    }

    /** A mount's menu provider that opens its screen without closing the open one first (see SeamlessMenuProvider). */
    private record SeamlessProvider(MenuProvider provider) implements MenuProvider
    {
        @Override
        public Component getDisplayName()
        {
            return this.provider.getDisplayName();
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
        {
            return this.provider.createMenu(containerId, inventory, player);
        }

        @Override
        public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer)
        {
            this.provider.writeClientSideData(menu, buffer);
        }

        @Override
        public boolean shouldTriggerClientSideContainerClosingOnOpen()
        {
            return false;
        }
    }
}
