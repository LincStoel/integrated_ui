package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.compat.EquipmentLayout;
import com.lincstoel.integratedui.compat.dieselgenerators.TrackLayersBags;
import com.lincstoel.integratedui.compat.quark.SeedPouches;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Bundles, and items like them: items holding a list of items, which the band's Bundles page (see
 * {@link BundleInventoryMenu}) has a slot for that takes a chosen item out, or puts the held item in. Each
 * {@link BundleKind} knows one way of keeping the contents:
 * <ul>
 *     <li>vanilla's {@link DataComponents#BUNDLE_CONTENTS} component (recognised, like container items, by the
 *     component being part of the item's defaults), which takes in vanilla bundles and any mod's item built the same
 *     way;</li>
 *     <li>Quark's seed pouch and Create Diesel Generators' track layers bag, which each hold one item in bulk;</li>
 *     <li>items in {@link #ITEM_HANDLER_BUNDLES} (such as Ars Elemental's bags), used through their item handler.</li>
 * </ul>
 * Items in {@link ContainerItems#BAND_EXCLUDED} are left out. Works on both sides.
 */
public final class Bundles
{
    /** The most bundles the page is sent with, which no inventory comes near. */
    static final int MAX_BUNDLES = 1024;
    /**
     * Items shown on the Bundles page through their item handler (NeoForge's item capability), such as Ars Elemental's
     * bags. Datapacks can add to it.
     */
    public static final TagKey<Item> ITEM_HANDLER_BUNDLES = TagKey.create(Registries.ITEM, IntegratedUI.rl("item_handler_bundles"));
    /** What Ars Elemental's bags refuse (their own screen's slots check it, but their item handler doesn't). */
    private static final TagKey<Item> ARS_ELEMENTAL_REFUSED = TagKey.create(Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("ars_elemental", "blacklist_bag_item"));

    /** The kinds, tried in order. */
    private static final List<BundleKind> KINDS = createKinds();

    private Bundles() {}

    private static List<BundleKind> createKinds()
    {
        List<BundleKind> kinds = new ArrayList<>();
        kinds.add(new ComponentBundles());
        if(IntegratedUI.isQuarkLoaded())
        {
            addGuarded(kinds, CompatGuard.QUARK, IntegratedUI::isQuarkEnabled, SeedPouches::new);
        }
        if(IntegratedUI.isDieselGeneratorsLoaded())
        {
            addGuarded(kinds, CompatGuard.DIESEL_GENERATORS, IntegratedUI::isDieselGeneratorsEnabled, TrackLayersBags::new);
        }
        kinds.add(new ItemHandlerBundles());
        return List.copyOf(kinds);
    }

    /** Adds a mod's kind, used only while {@code enabled}, with its calls going through {@link CompatGuard}. */
    private static void addGuarded(List<BundleKind> kinds, String modId, BooleanSupplier enabled, Supplier<BundleKind> factory)
    {
        BundleKind kind = CompatGuard.get(modId, factory, null);
        if(kind != null)
        {
            kinds.add(new GuardedKind(modId, enabled, kind));
        }
    }

    /** The kind of bundle the stack is, or null if it isn't one the Bundles page shows. */
    @Nullable
    public static BundleKind getKind(ItemStack stack)
    {
        if(stack.getCount() != 1 || stack.is(ContainerItems.BAND_EXCLUDED))
        {
            return null;
        }
        for(BundleKind kind : KINDS)
        {
            if(kind.matches(stack))
            {
                return kind;
            }
        }
        return null;
    }

    /** Whether the stack is a single bundle (or bundle-like item) the Bundles page shows. */
    public static boolean isBundle(ItemStack stack)
    {
        return getKind(stack) != null;
    }

    /** The bundle's contents, in the order the page lists them; empty if it has none. Not to be changed. */
    public static List<ItemStack> getContents(ItemStack bundle)
    {
        BundleKind kind = getKind(bundle);
        return kind != null ? kind.getContents(bundle) : List.of();
    }

    /** The stack at the given index of the bundle's contents, or empty. Not to be changed. */
    public static ItemStack getItem(ItemStack bundle, int index)
    {
        List<ItemStack> contents = getContents(bundle);
        return index >= 0 && index < contents.size() ? contents.get(index) : ItemStack.EMPTY;
    }

    /** The contents to show in the bundle's tooltip in place of its own, or null (see {@link BundleKind#getTooltipContents}). */
    @Nullable
    public static BundleContents getTooltipContents(ItemStack bundle)
    {
        BundleKind kind = getKind(bundle);
        return kind != null ? kind.getTooltipContents(bundle) : null;
    }

    /** A copy of the bundle with nothing in it, as the band's tab shows it. */
    public static ItemStack emptied(ItemStack bundle)
    {
        BundleKind kind = getKind(bundle);
        ItemStack copy = bundle.copyWithCount(1);
        return kind != null ? kind.emptied(copy) : copy;
    }

    /**
     * Server side: the bundles the player carries, in the order the page shows them: the main inventory, hotbar and
     * offhand as they appear on screen, then the Curios/Accessories slots.
     */
    public static List<Found> collect(Player player)
    {
        List<Found> found = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for(int index : ShulkerContainer.INVENTORY_ORDER)
        {
            ItemStack stack = inventory.getItem(index);
            if(isBundle(stack))
            {
                found.add(new Found(new Location(false, index), stack));
            }
        }
        EquipmentLayout layout = Equipment.collect(player);
        Equipment.get(layout.backend()).forEachStack(player, layout.groups(), (stack, position) -> {
            if(isBundle(stack))
            {
                found.add(new Found(new Location(true, position), stack));
            }
        });
        return found.size() > MAX_BUNDLES ? found.subList(0, MAX_BUNDLES) : found;
    }

    /**
     * Takes up to {@code amount} of the stack at {@code index} out of the bundle, playing the bundle's sound. Returns
     * what was taken, or empty if there was nothing there.
     */
    public static ItemStack take(Player player, ItemStack bundle, int index, int amount)
    {
        BundleKind kind = getKind(bundle);
        if(kind == null || index < 0 || amount <= 0)
        {
            return ItemStack.EMPTY;
        }
        ItemStack taken = kind.take(bundle, index, amount);
        if(!taken.isEmpty())
        {
            playSound(player, SoundEvents.BUNDLE_REMOVE_ONE);
        }
        return taken;
    }

    /**
     * Puts what fits of {@code stack} into the bundle, shrinking {@code stack}: the way the bundle's own item does it
     * when a stack is right-clicked onto it (so a mod's bundle keeps its own rules), else as its kind does.
     * {@code source} is the slot the bundle is in, and {@code stackAccess} holds {@code stack} (the cursor, or a
     * stand-in for it). Returns the index the stack is listed at in the bundle afterwards, or -1 if nothing went in.
     */
    public static int insert(Player player, ItemStack bundle, ItemStack stack, Slot source, SlotAccess stackAccess)
    {
        BundleKind kind = getKind(bundle);
        if(kind == null || stack.isEmpty() || !stack.canFitInsideContainerItems())
        {
            return -1;
        }
        ItemStack inserting = stack.copyWithCount(1);
        int before = stack.getCount();
        if(!bundle.overrideOtherStackedOnMe(stack, source, ClickAction.SECONDARY, player, stackAccess) && kind.insert(bundle, stack))
        {
            playSound(player, SoundEvents.BUNDLE_INSERT);
        }
        if(stack.getCount() == before)
        {
            return -1;
        }
        List<ItemStack> contents = kind.getContents(bundle);
        for(int i = 0; i < contents.size(); i++)
        {
            if(ItemStack.isSameItemSameComponents(contents.get(i), inserting))
            {
                return i;
            }
        }
        return 0;
    }

    /** As the bundle plays it: heard by the player through their own prediction, and by everyone else from the server. */
    private static void playSound(Player player, SoundEvent sound)
    {
        player.playSound(sound, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    /** Vanilla's bundle contents component: the item put in last comes first. */
    private static final class ComponentBundles implements BundleKind
    {
        @Override
        public boolean matches(ItemStack stack)
        {
            return stack.getItem().components().has(DataComponents.BUNDLE_CONTENTS);
        }

        private static BundleContents get(ItemStack bundle)
        {
            return bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        }

        @Override
        public List<ItemStack> getContents(ItemStack bundle)
        {
            BundleContents contents = get(bundle);
            List<ItemStack> items = new ArrayList<>(contents.size());
            contents.items().forEach(items::add);
            return items;
        }

        @Override
        public ItemStack take(ItemStack bundle, int index, int amount)
        {
            BundleContents contents = get(bundle);
            if(index >= contents.size())
            {
                return ItemStack.EMPTY;
            }
            List<ItemStack> items = new ArrayList<>(contents.size());
            contents.itemCopyStream().forEach(items::add);
            ItemStack stored = items.get(index);
            ItemStack taken = stored.split(amount);
            if(stored.isEmpty())
            {
                items.remove(index);
            }
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
            return taken;
        }

        @Override
        public boolean insert(ItemStack bundle, ItemStack stack)
        {
            BundleContents.Mutable contents = new BundleContents.Mutable(get(bundle));
            if(contents.tryInsert(stack) <= 0)
            {
                return false;
            }
            bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            return true;
        }

        @Override
        public ItemStack emptied(ItemStack bundle)
        {
            bundle.remove(DataComponents.BUNDLE_CONTENTS);
            return bundle;
        }
    }

    /**
     * Items in {@link #ITEM_HANDLER_BUNDLES}, used through their item handler: lists the stacks in its slots, skipping
     * empty ones, and puts items in as a hopper does, topping up stacks first.
     */
    private static final class ItemHandlerBundles implements BundleKind
    {
        @Override
        public boolean matches(ItemStack stack)
        {
            return stack.is(ITEM_HANDLER_BUNDLES);
        }

        @Nullable
        private static IItemHandler getHandler(ItemStack bundle)
        {
            return bundle.getCapability(Capabilities.ItemHandler.ITEM);
        }

        @Override
        public List<ItemStack> getContents(ItemStack bundle)
        {
            IItemHandler handler = getHandler(bundle);
            if(handler == null)
            {
                return List.of();
            }
            List<ItemStack> items = new ArrayList<>();
            for(int slot = 0; slot < handler.getSlots(); slot++)
            {
                ItemStack stack = handler.getStackInSlot(slot);
                if(!stack.isEmpty())
                {
                    items.add(stack);
                }
            }
            return items;
        }

        @Override
        public ItemStack take(ItemStack bundle, int index, int amount)
        {
            IItemHandler handler = getHandler(bundle);
            if(handler == null)
            {
                return ItemStack.EMPTY;
            }
            int found = 0;
            for(int slot = 0; slot < handler.getSlots(); slot++)
            {
                if(!handler.getStackInSlot(slot).isEmpty() && found++ == index)
                {
                    return handler.extractItem(slot, amount, false);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean insert(ItemStack bundle, ItemStack stack)
        {
            IItemHandler handler = getHandler(bundle);
            if(handler == null || stack.is(ARS_ELEMENTAL_REFUSED))
            {
                return false;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
            int moved = stack.getCount() - rest.getCount();
            stack.shrink(moved);
            return moved > 0;
        }

        @Override
        public ItemStack emptied(ItemStack bundle)
        {
            // Where a handler keeps its contents is up to it; the usual place is the container component
            bundle.remove(DataComponents.CONTAINER);
            return bundle;
        }

        @Nullable
        @Override
        public BundleContents getTooltipContents(ItemStack bundle)
        {
            List<ItemStack> contents = this.getContents(bundle);
            return contents.isEmpty() ? null : new BundleContents(contents);
        }
    }

    /** A mod's kind, used only while its integration is on, with every call going through {@link CompatGuard}. */
    private record GuardedKind(String modId, BooleanSupplier enabled, BundleKind kind) implements BundleKind
    {
        @Override
        public boolean matches(ItemStack stack)
        {
            return this.enabled.getAsBoolean() && CompatGuard.test(this.modId, () -> this.kind.matches(stack));
        }

        @Override
        public List<ItemStack> getContents(ItemStack bundle)
        {
            return CompatGuard.get(this.modId, () -> this.kind.getContents(bundle), List.of());
        }

        @Override
        public ItemStack take(ItemStack bundle, int index, int amount)
        {
            return CompatGuard.get(this.modId, () -> this.kind.take(bundle, index, amount), ItemStack.EMPTY);
        }

        @Override
        public boolean insert(ItemStack bundle, ItemStack stack)
        {
            return CompatGuard.test(this.modId, () -> this.kind.insert(bundle, stack));
        }

        @Override
        public ItemStack emptied(ItemStack bundle)
        {
            return CompatGuard.get(this.modId, () -> this.kind.emptied(bundle), bundle);
        }

        @Nullable
        @Override
        public BundleContents getTooltipContents(ItemStack bundle)
        {
            return CompatGuard.get(this.modId, () -> this.kind.getTooltipContents(bundle), null);
        }
    }

    /** A bundle found on the player, and where it is. */
    public record Found(Location location, ItemStack stack) {}

    /**
     * Where a bundle is: in the player's inventory (by inventory index), or in the {@code index}th equipment slot of
     * the menu (see {@link com.lincstoel.integratedui.compat.EquipmentBackend#forEachStack}).
     */
    public record Location(boolean equipment, int index)
    {
        public static final StreamCodec<ByteBuf, Location> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, Location::equipment,
            ByteBufCodecs.VAR_INT, Location::index,
            Location::new);
        public static final StreamCodec<ByteBuf, List<Location>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BUNDLES));

        /** The index of the menu's slot holding this place, or -1 if it has none. */
        public int toMenuSlot(IntegratedInventoryMenu menu)
        {
            if(this.equipment)
            {
                int slot = menu.getEquipmentStart() + this.index;
                return this.index >= 0 && slot < menu.getEquipmentEnd() ? slot : -1;
            }
            if(this.index >= 0 && this.index < Inventory.getSelectionSize())
            {
                return IntegratedInventoryMenu.HOTBAR_START + this.index;
            }
            if(this.index < Inventory.INVENTORY_SIZE)
            {
                return this.index;
            }
            return this.index == Inventory.SLOT_OFFHAND ? IntegratedInventoryMenu.OFFHAND_SLOT : -1;
        }
    }
}
