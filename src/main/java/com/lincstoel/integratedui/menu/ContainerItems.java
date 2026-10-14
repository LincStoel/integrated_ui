package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.supplementaries.SupplementariesCompat;
import com.lincstoel.integratedui.config.ServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/**
 * Container items: items that keep their contents in the {@link DataComponents#CONTAINER} component the way shulker
 * boxes do, recognised by the component being part of the item's defaults and by the item keeping its contents when
 * its block is broken (a chest has the component too, but spills its contents). This takes in vanilla's shulker boxes
 * and Supplementaries' sacks alike, and any other mod's item built the same way, without knowing about any of them.
 * <p>
 * A container item that places a block (as both of those do) is described by that block's block entity: its size,
 * and what it accepts. Its colour comes from the {@code c:dyed/<color>} convention tags, as shulker boxes are dyed.
 * Items in {@link #OPENS_OWN_SCREEN} can also be opened in a screen of their own.
 */
public final class ContainerItems
{
    /**
     * Container items left out of the band, such as Supplementaries' safe, whose contents are only for its owner.
     * Datapacks can add to it. Ignored while the config's whitelist is on.
     */
    public static final TagKey<Item> BAND_EXCLUDED = TagKey.create(Registries.ITEM, IntegratedUI.rl("band_excluded"));
    /** Container items that can be opened in a screen of their own (from the band, or by right-clicking them). */
    public static final TagKey<Item> OPENS_OWN_SCREEN = TagKey.create(Registries.ITEM, IntegratedUI.rl("opens_own_screen"));

    public static final int SHULKER_BOX_SIZE = 27;
    /** The most slots an item's container component can hold. */
    private static final int MAX_SIZE = 256;
    /** The largest container a vanilla chest screen can show. */
    private static final int MAX_CHEST_SIZE = 54;
    private static final int NEUTRAL_TINT = 0xA0A0A0;
    /** Hand-picked band colours for undyed container items, where the block's map colour doesn't suit. */
    private static final Map<ResourceLocation, Integer> UNDYED_TINTS = Map.of(
        ResourceLocation.withDefaultNamespace("shulker_box"), 0x9A6E9A,
        ResourceLocation.fromNamespaceAndPath("supplementaries", "sack"), 0xBA906A
    );
    private static final TagKey<Item>[] DYED_TAGS = createDyedTags();

    /** The items that keep their contents when broken, see {@link #keepsContents}. */
    private static volatile Set<Item> keepingContents = Set.of();

    /** The block entity each container item places, standing in for it; empty if it places none. */
    private static final Map<Item, Optional<BlockEntity>> PROBES = new ConcurrentHashMap<>();
    /** What sorting needs to know of each container item, looked up once (tags can change, so cleared when they do). */
    private static final Map<Item, ItemInfo> ITEM_INFO = new ConcurrentHashMap<>();

    /** The parsed config whitelist, and the raw list it was parsed from (replaced whenever the config reloads). */
    @Nullable
    private static volatile Whitelist whitelist;

    private ContainerItems() {}

    @SuppressWarnings("unchecked")
    private static TagKey<Item>[] createDyedTags()
    {
        TagKey<Item>[] tags = new TagKey[DyeColor.values().length];
        for(DyeColor color : DyeColor.values())
        {
            tags[color.getId()] = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dyed/" + color.getName()));
        }
        return tags;
    }

    /**
     * Whether the band can show this stack: a single, unlooted and unlocked container item, that is on the config's
     * whitelist when that is on, or else one that keeps its contents when broken (see {@link #keepsContents}), isn't in
     * {@link #BAND_EXCLUDED} and is larger than the config's smallest size. Works on both sides.
     */
    public static boolean isContainerItem(ItemStack stack)
    {
        if(stack.getCount() != 1 || !isContainerItemType(stack) || stack.has(DataComponents.CONTAINER_LOOT) || isLocked(stack))
        {
            return false;
        }
        if(ServerConfig.get(ServerConfig.CONTAINER_ITEM_WHITELIST_ENABLED))
        {
            return getWhitelist().matches(stack);
        }
        return keepsContents(stack.getItem()) && !stack.is(BAND_EXCLUDED)
            && getSize(stack) > ServerConfig.get(ServerConfig.IGNORE_CONTAINER_ITEMS_UP_TO_SLOTS);
    }

    /**
     * Whether the item keeps what is inside it when its block is broken, as a shulker box does (a chest spills its
     * contents instead): only such items are really meant to be carried full. Worked out by the server, see
     * {@link #findKeepingContents}, and sent to clients.
     */
    public static boolean keepsContents(Item item)
    {
        return keepingContents.contains(item);
    }

    /** Client side: the items that keep their contents, as sent by the server. */
    public static void setKeepingContents(Set<Item> items)
    {
        keepingContents = items;
    }

    /** The items that keep their contents, for sending to clients. */
    public static Set<Item> getKeepingContents()
    {
        return keepingContents;
    }

    /**
     * Server side, whenever loot tables (re)load: finds the container items that keep their contents when broken,
     * by breaking each one's block for real (in a block entity of its own, which is then thrown away): with an item
     * inside, does what it drops still hold it? Items that don't place a block have nothing to spill, so they count.
     * This follows each block's own loot table, so it covers any mod's blocks, and datapacks that change them.
     */
    public static void findKeepingContents(ServerLevel level)
    {
        Set<Item> found = new HashSet<>();
        for(Item item : BuiltInRegistries.ITEM)
        {
            if(item.components().has(DataComponents.CONTAINER) && keepsContentsWhenBroken(level, item))
            {
                found.add(item);
            }
        }
        keepingContents = Set.copyOf(found);
    }

    private static boolean keepsContentsWhenBroken(ServerLevel level, Item item)
    {
        if(!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof EntityBlock entityBlock))
        {
            return true;
        }
        try
        {
            BlockState state = blockItem.getBlock().defaultBlockState();
            BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, state);
            if(!(blockEntity instanceof Container container) || container.getContainerSize() == 0)
            {
                return false;
            }
            blockEntity.setLevel(level);
            container.setItem(0, new ItemStack(Items.STICK));
            List<ItemStack> drops = Block.getDrops(state, level, BlockPos.ZERO, blockEntity, null, new ItemStack(Items.NETHERITE_PICKAXE));
            return drops.stream().anyMatch(drop -> drop.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).stream()
                .anyMatch(inside -> inside.is(Items.STICK)));
        }
        catch(RuntimeException e)
        {
            IntegratedUI.LOGGER.debug("Couldn't check whether {} keeps its contents when broken", BuiltInRegistries.ITEM.getKey(item), e);
            return false;
        }
    }

    /** Whether the stack is a kind of container item at all, whatever its state and the config. */
    public static boolean isContainerItemType(ItemStack stack)
    {
        return stack.getItem().components().has(DataComponents.CONTAINER);
    }

    /** Whether the stack carries a vanilla lock, which opens only with its key: it is left alone, as a locked chest is. */
    private static boolean isLocked(ItemStack stack)
    {
        LockCode lock = stack.get(DataComponents.LOCK);
        return lock != null && !lock.equals(LockCode.NO_LOCK);
    }

    private static Whitelist getWhitelist()
    {
        List<? extends String> entries = ServerConfig.get(ServerConfig.CONTAINER_ITEM_WHITELIST);
        Whitelist current = whitelist;
        if(current == null || current.entries != entries)
        {
            current = Whitelist.parse(entries);
            whitelist = current;
        }
        return current;
    }

    /** The config whitelist: item ids, and item tags given with a leading {@code #}. */
    private record Whitelist(List<? extends String> entries, Set<ResourceLocation> items, List<TagKey<Item>> tags)
    {
        static Whitelist parse(List<? extends String> entries)
        {
            Set<ResourceLocation> items = new HashSet<>();
            List<TagKey<Item>> tags = new ArrayList<>();
            for(String entry : entries)
            {
                boolean tag = entry.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? entry.substring(1) : entry);
                if(id == null)
                {
                    continue;
                }
                if(tag)
                {
                    tags.add(TagKey.create(Registries.ITEM, id));
                }
                else
                {
                    items.add(id);
                }
            }
            return new Whitelist(entries, items, tags);
        }

        boolean matches(ItemStack stack)
        {
            if(this.items.contains(BuiltInRegistries.ITEM.getKey(stack.getItem())))
            {
                return true;
            }
            for(TagKey<Item> tag : this.tags)
            {
                if(stack.is(tag))
                {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Server side, once tags are loaded: logs every container item found and whether the band shows it, as they are
     * picked up without being listed anywhere.
     */
    public static void logDetected()
    {
        List<String> shown = new ArrayList<>();
        List<String> leftOut = new ArrayList<>();
        boolean whitelistOn = ServerConfig.get(ServerConfig.CONTAINER_ITEM_WHITELIST_ENABLED);
        int smallest = ServerConfig.get(ServerConfig.IGNORE_CONTAINER_ITEMS_UP_TO_SLOTS);
        for(Item item : BuiltInRegistries.ITEM)
        {
            ItemStack stack = new ItemStack(item);
            if(!isContainerItemType(stack))
            {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            int size = getSize(stack);
            if(isContainerItem(stack))
            {
                shown.add(id + " (" + size + (opensOwnScreen(stack) ? " slots, own screen)" : " slots)"));
            }
            else
            {
                String reason = whitelistOn ? "not whitelisted" : !keepsContents(item) ? "spills its contents when broken"
                    : stack.is(BAND_EXCLUDED) ? BAND_EXCLUDED.location() + " tag" : size <= smallest ? size + " slots" : "not shown";
                leftOut.add(id + " (" + reason + ")");
            }
        }
        IntegratedUI.LOGGER.info("Container items shown in the band: {}", shown.isEmpty() ? "none" : String.join(", ", shown));
        if(!leftOut.isEmpty())
        {
            IntegratedUI.LOGGER.info("Container items left out of the band: {}", String.join(", ", leftOut));
        }
    }

    /**
     * Whether the stack is a container item that can be opened in its own screen (and that is allowed in the config).
     * Works on both sides.
     */
    public static boolean opensOwnScreen(ItemStack stack)
    {
        return ServerConfig.get(ServerConfig.CONTAINER_ITEM_SCREENS) && isContainerItem(stack) && stack.is(OPENS_OWN_SCREEN);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Size and contents rules, from the placed block entity

    /**
     * Forgets the probes, which keep the level they were last used with (and with it the whole server): called when
     * the server stops.
     */
    public static void clearProbes()
    {
        PROBES.clear();
    }

    @Nullable
    private static BlockEntity probe(Item item)
    {
        return PROBES.computeIfAbsent(item, ContainerItems::createProbe).orElse(null);
    }

    private static Optional<BlockEntity> createProbe(Item item)
    {
        if(!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof EntityBlock entityBlock))
        {
            return Optional.empty();
        }
        try
        {
            BlockEntity blockEntity = entityBlock.newBlockEntity(BlockPos.ZERO, blockItem.getBlock().defaultBlockState());
            return blockEntity instanceof Container ? Optional.of(blockEntity) : Optional.empty();
        }
        catch(RuntimeException e)
        {
            IntegratedUI.LOGGER.warn("Couldn't create the block entity of container item {}", BuiltInRegistries.ITEM.getKey(item), e);
            return Optional.empty();
        }
    }

    /**
     * The number of slots the container item has: as many as its block entity, or a shulker box's if it has none.
     * Works on both sides.
     */
    public static int getSize(ItemStack stack)
    {
        int size = SHULKER_BOX_SIZE;
        if(probe(stack.getItem()) instanceof Container container)
        {
            try
            {
                size = container.getContainerSize();
            }
            catch(RuntimeException e)
            {
                IntegratedUI.LOGGER.warn("Couldn't get the size of container item {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), e);
            }
        }
        if(isSack(stack))
        {
            // As before: a sack holds at most a shulker box's worth
            size = Math.min(size, SHULKER_BOX_SIZE);
        }
        return Mth.clamp(size, 1, MAX_SIZE);
    }

    /** Server side: whether {@code item} may go in the given slot of the container item, as its block entity would allow. */
    public static boolean canPlace(Player player, ItemStack containerItem, int index, ItemStack item)
    {
        if(!item.canFitInsideContainerItems())
        {
            return false;
        }
        BlockEntity probe = probe(containerItem.getItem());
        if(!(probe instanceof Container container))
        {
            return true;
        }
        try
        {
            probe.setLevel(player.level());
            return container.canPlaceItem(index, item);
        }
        catch(RuntimeException e)
        {
            return true;
        }
    }

    /** The registry id of the container item's block entity type (or the item's own id), so alike items group together. */
    public static String getGroupKey(ItemStack stack)
    {
        return getItemInfo(stack).groupKey();
    }

    private static ItemInfo getItemInfo(ItemStack stack)
    {
        return ITEM_INFO.computeIfAbsent(stack.getItem(), item -> {
            BlockEntity probe = probe(item);
            ResourceLocation key = probe != null ? BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(probe.getType()) : null;
            DyeColor color = null;
            for(DyeColor dye : DyeColor.values())
            {
                if(stack.is(DYED_TAGS[dye.getId()]))
                {
                    color = dye;
                    break;
                }
            }
            return new ItemInfo((key != null ? key : BuiltInRegistries.ITEM.getKey(item)).toString(), color);
        });
    }

    /** Forgets what was looked up about each container item, once tags change (or the server stops). */
    public static void clearItemInfo()
    {
        ITEM_INFO.clear();
    }

    /** What the band's sorting needs to know of a kind of container item. */
    private record ItemInfo(String groupKey, @Nullable DyeColor color) {}

    // ---------------------------------------------------------------------------------------------------------
    // Colour

    /** The container item's dye colour, from the {@code c:dyed/<color>} tags, or null if it is undyed. */
    @Nullable
    public static DyeColor getDyeColor(ItemStack stack)
    {
        return getItemInfo(stack).color();
    }

    /** Sorts undyed container items first, then dyed ones in dye order. */
    public static int getColorOrder(ItemStack stack)
    {
        DyeColor color = getDyeColor(stack);
        return color == null ? -1 : color.getId();
    }

    /**
     * The colour (RGB) the band is drawn in: the dye colour, else a hand-picked colour for known undyed items, else
     * the colour of the block it places on maps.
     */
    public static int getTint(ItemStack stack)
    {
        DyeColor color = getDyeColor(stack);
        if(color != null)
        {
            return color.getTextureDiffuseColor() & 0xFFFFFF;
        }
        Integer undyed = UNDYED_TINTS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if(undyed != null)
        {
            return undyed;
        }
        if(stack.getItem() instanceof BlockItem blockItem)
        {
            MapColor mapColor = blockItem.getBlock().defaultMapColor();
            if(mapColor != MapColor.NONE)
            {
                return mapColor.col;
            }
        }
        return NEUTRAL_TINT;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Own screen

    private static boolean isSack(ItemStack stack)
    {
        return IntegratedUI.isSupplementariesEnabled() && CompatGuard.test(CompatGuard.SUPPLEMENTARIES, () -> SupplementariesCompat.isSack(stack));
    }

    /** Server side: which screen the container item opens in, if it can be opened on its own. */
    public static OwnScreen getOwnScreen(ItemStack stack, int size)
    {
        if(!opensOwnScreen(stack))
        {
            return OwnScreen.NONE;
        }
        if(isSack(stack))
        {
            return OwnScreen.SACK;
        }
        if(size == SHULKER_BOX_SIZE)
        {
            return OwnScreen.SHULKER_BOX;
        }
        return size <= MAX_CHEST_SIZE ? OwnScreen.CHEST : OwnScreen.NONE;
    }

    /**
     * Server side: opens the container item in the given slot of the player's inventory in its own screen, carrying
     * the cursor stack over. Does nothing if it can't be opened on its own.
     */
    public static void openOwnScreen(ServerPlayer player, int slot)
    {
        ItemStack stack = player.getInventory().getItem(slot);
        int size = getSize(stack);
        OwnScreen screen = getOwnScreen(stack, size);
        if(screen == OwnScreen.NONE)
        {
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        // Over a container's screen, its menu is held to return to when this one closes (see SuspendedMenus)
        SuspendedMenus.open(player, () -> {
            switch(screen)
            {
                case SACK -> CompatGuard.run(CompatGuard.SUPPLEMENTARIES, () -> SupplementariesCompat.showSack(player, stack, slot, size));
                case SHULKER_BOX -> {
                    ShulkerContainer container = new ShulkerContainer(player, stack, SHULKER_BOX_SIZE, SHULKER_BOX_SIZE);
                    player.openMenu(new SeamlessMenuProvider((id, inventory, p) -> new PortableShulkerBoxMenu(id, inventory, container), stack.getHoverName()),
                        buf -> buf.writeVarInt(container.getInventorySlot()));
                    playSound(player, SoundEvents.SHULKER_BOX_OPEN);
                }
                case CHEST -> {
                    int rows = Mth.positiveCeilDiv(size, ShulkerContainer.COLUMNS);
                    ShulkerContainer container = new ShulkerContainer(player, stack, rows * ShulkerContainer.COLUMNS, size);
                    player.openMenu(new SeamlessMenuProvider((id, inventory, p) -> new PortableChestMenu(id, inventory, container, rows, size), stack.getHoverName()),
                        buf -> {
                            buf.writeVarInt(rows);
                            buf.writeVarInt(size);
                            buf.writeVarInt(container.getInventorySlot());
                        });
                    playSound(player, SoundEvents.CHEST_OPEN);
                }
                case NONE -> {}
            }
        });
        player.containerMenu.setCarried(carried);
        player.containerMenu.broadcastChanges();
    }

    private static void playSound(Player player, SoundEvent sound)
    {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.5F,
            player.level().random.nextFloat() * 0.1F + 0.9F);
    }

    /** The screen a container item opens in on its own. */
    public enum OwnScreen
    {
        /** It can't be opened on its own. */
        NONE,
        /** Supplementaries' sack screen. */
        SACK,
        /** Vanilla's shulker box screen. */
        SHULKER_BOX,
        /** Vanilla's chest screen, with as many rows as it needs. */
        CHEST;

        private static final IntFunction<OwnScreen> BY_ID = ByIdMap.continuous(OwnScreen::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

        public static OwnScreen byId(int id)
        {
            return BY_ID.apply(id);
        }
    }
}
