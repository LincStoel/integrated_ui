package com.lincstoel.integratedui.gametest;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.config.ServerConfig;
import com.lincstoel.integratedui.menu.MountInventoryMenu;
import com.lincstoel.integratedui.menu.Mounts;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.inventory.HorseInventoryMenu;
import com.lincstoel.integratedui.menu.BandTargets;
import com.lincstoel.integratedui.menu.BundleInventoryMenu;
import com.lincstoel.integratedui.menu.Bundles;
import com.lincstoel.integratedui.menu.ContainerItems;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import com.lincstoel.integratedui.menu.ShulkerContainer;
import com.lincstoel.integratedui.menu.ShulkerInventoryMenu;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The item handling that could lose or duplicate items: container items shown in the band, quick-moves and the
 * crafting grid when switching what the band shows. Run with the runGameTestServer task.
 */
@GameTestHolder(IntegratedUI.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IntegratedUITests
{
    /** data/integrated_ui/structure/empty.nbt (tests are prefixed with the mod id) */
    private static final String EMPTY = "empty";

    private IntegratedUITests() {}

    /**
     * A survival player in the test's level, like vanilla's mock player, but whose (fake) connection takes NeoForge's
     * payloads, which opening our menus sends.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper)
    {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /** A shulker box holding one diamond, so losing it (or its contents) shows. */
    private static ItemStack filledBox()
    {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        return box;
    }

    private static int countDiamondsInBoxes(Inventory inventory)
    {
        int diamonds = 0;
        for(int i = 0; i < inventory.getContainerSize(); i++)
        {
            ItemStack stack = inventory.getItem(i);
            if(stack.is(Items.SHULKER_BOX))
            {
                ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
                diamonds += contents.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
            }
        }
        return diamonds;
    }

    private static ShulkerInventoryMenu openWithBand(GameTestHelper helper, ServerPlayer player)
    {
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        if(!(player.containerMenu instanceof ShulkerInventoryMenu menu))
        {
            throw new GameTestAssertException("Expected the band to show the shulker box, got " + player.containerMenu);
        }
        return menu;
    }

    @GameTest(template = EMPTY)
    public static void shiftClickingShownBoxKeepsIt(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(9, filledBox());
        ShulkerInventoryMenu menu = openWithBand(helper, player);
        // Main inventory slot 9 is menu slot 9
        menu.clicked(9, 0, ClickType.QUICK_MOVE, player);
        menu.broadcastChanges();
        helper.assertTrue(countDiamondsInBoxes(player.getInventory()) == 1, "The shown shulker box (or its diamond) was lost");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void hotbarSwappingShownBoxIntoBandIsRefused(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, filledBox());
        ShulkerInventoryMenu menu = openWithBand(helper, player);
        menu.clicked(menu.getBackpackStart(), 0, ClickType.SWAP, player);
        menu.broadcastChanges();
        helper.assertTrue(player.getInventory().getItem(0).is(Items.SHULKER_BOX), "The shown shulker box left its hotbar slot");
        helper.assertTrue(countDiamondsInBoxes(player.getInventory()) == 1, "The shown shulker box (or its diamond) was lost");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftingGridSurvivesSwitchingAndEmptiesOnClose(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(9, filledBox());
        player.getInventory().setItem(10, new ItemStack(Items.RED_SHULKER_BOX));
        openWithBand(helper, player);
        player.containerMenu.getSlot(1).set(new ItemStack(Items.STICK, 3));
        IntegratedInventoryMenu.open(player, Navigation.NEXT);
        helper.assertTrue(player.containerMenu.getSlot(1).getItem().is(Items.STICK), "The crafting grid wasn't carried over");
        player.closeContainer();
        helper.assertTrue(player.getInventory().countItem(Items.STICK) == 3, "Closing didn't give the crafting grid back");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void quickMoveSpillsIntoBand(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.SHULKER_BOX));
        inventory.setItem(1, new ItemStack(Items.DIRT, 54));
        for(int i = 2; i < Inventory.INVENTORY_SIZE; i++)
        {
            inventory.setItem(i, new ItemStack(Items.STONE, 64));
        }
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DIRT, 64));
        ShulkerInventoryMenu menu = openWithBand(helper, player);
        menu.quickMoveStack(player, IntegratedInventoryMenu.OFFHAND_SLOT);
        menu.broadcastChanges();
        helper.assertTrue(inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty(), "Dirt was left in the offhand");
        helper.assertTrue(menu.getSlot(menu.getBackpackStart()).getItem().getCount() == 54, "The rest didn't go into the band");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void quickMoveFromMainInventoryPrefersHotbar(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.SHULKER_BOX));
        inventory.setItem(9, new ItemStack(Items.DIRT, 64));
        ShulkerInventoryMenu menu = openWithBand(helper, player);
        // Main inventory slot 9 is menu slot 9
        menu.quickMoveStack(player, 9);
        menu.broadcastChanges();
        helper.assertTrue(inventory.getItem(1).is(Items.DIRT) && inventory.getItem(1).getCount() == 64, "The dirt didn't go to the hotbar");
        helper.assertTrue(menu.getSlot(menu.getBackpackStart()).getItem().isEmpty(), "The dirt went into the band");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void quickMoveSkipsCollapsedBand(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(Items.SHULKER_BOX));
        for(int i = 9; i < 36; i++)
        {
            inventory.setItem(i, new ItemStack(Items.STONE, 64));
        }
        inventory.setItem(1, new ItemStack(Items.DIRT, 64));
        ShulkerInventoryMenu menu = openWithBand(helper, player);
        menu.clickMenuButton(player, IntegratedInventoryMenu.bandButton(false));
        // Hotbar slot 1 is menu slot 37; the main inventory is full, so only the band could take it
        menu.quickMoveStack(player, 37);
        menu.broadcastChanges();
        helper.assertTrue(inventory.getItem(1).is(Items.DIRT) && inventory.getItem(1).getCount() == 64, "The dirt left the hotbar");
        helper.assertTrue(menu.getSlot(menu.getBackpackStart()).getItem().isEmpty(), "The dirt went into the collapsed band");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void onlyItemsKeepingTheirContentsCount(GameTestHelper helper)
    {
        helper.assertTrue(ContainerItems.isContainerItem(new ItemStack(Items.SHULKER_BOX)), "A shulker box keeps its contents when broken");
        helper.assertTrue(ContainerItems.isContainerItem(new ItemStack(Items.BLUE_SHULKER_BOX)), "A dyed shulker box keeps its contents when broken");
        for(ItemStack spills : List.of(new ItemStack(Items.CHEST), new ItemStack(Items.BARREL), new ItemStack(Items.FURNACE),
            new ItemStack(Items.HOPPER), new ItemStack(Items.CAMPFIRE), new ItemStack(Items.CHISELED_BOOKSHELF)))
        {
            helper.assertFalse(ContainerItems.isContainerItem(spills), spills.getHoverName().getString() + " spills its contents when broken");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lockedBoxIsLeftAlone(GameTestHelper helper)
    {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        helper.assertTrue(ContainerItems.isContainerItem(box), "A plain shulker box should be a container item");
        box.set(DataComponents.LOCK, new LockCode("key"));
        helper.assertFalse(ContainerItems.isContainerItem(box), "A locked shulker box was shown");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void inaccessibleContainerGivesItemsBack(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        player.getInventory().setItem(9, box);
        ShulkerContainer container = new ShulkerContainer(player, box, 27, 27);
        player.getInventory().setItem(9, ItemStack.EMPTY);
        container.setItem(0, new ItemStack(Items.EMERALD, 5));
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 5, "Items put into a gone container were lost");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------
    // The Bundles page

    /** A bundle holding the given stacks, the first being the one put in last (as a bundle lists them). */
    private static ItemStack bundleWith(ItemStack... items)
    {
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(items)));
        return bundle;
    }

    private static BundleInventoryMenu openBundles(ServerPlayer player)
    {
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        if(!(player.containerMenu instanceof BundleInventoryMenu menu))
        {
            throw new GameTestAssertException("Expected the band to show the Bundles page, got " + player.containerMenu);
        }
        return menu;
    }

    private static int count(ItemStack bundle, Item item)
    {
        return Bundles.getContents(bundle).stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @GameTest(template = EMPTY)
    public static void bundlesPageHasASlotPerBundleAndComesLast(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, bundleWith());
        inventory.setItem(9, bundleWith(new ItemStack(Items.DIAMOND)));
        inventory.setItem(Inventory.SLOT_OFFHAND, bundleWith());
        inventory.setItem(10, new ItemStack(Items.SHULKER_BOX));
        List<BandTargets.BandTarget> targets = BandTargets.collect(player);
        helper.assertTrue(targets.getLast() instanceof BandTargets.BandTarget.BundlePage, "The Bundles page isn't last");
        // The shulker box shows first; previous wraps around to the last
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        IntegratedInventoryMenu.open(player, Navigation.PREVIOUS);
        if(!(player.containerMenu instanceof BundleInventoryMenu menu))
        {
            throw new GameTestAssertException("Expected the Bundles page, got " + player.containerMenu);
        }
        helper.assertTrue(menu.getUsableBandSize() == 3, "Expected 3 bundle slots, got " + menu.getUsableBandSize());
        // In screen order: main inventory, hotbar, offhand
        helper.assertTrue(menu.getBundle(0) == inventory.getItem(9), "The first slot isn't the main inventory's bundle");
        helper.assertTrue(menu.getBundle(1) == inventory.getItem(0), "The second slot isn't the hotbar's bundle");
        helper.assertTrue(menu.getBundle(2) == inventory.getItem(Inventory.SLOT_OFFHAND), "The third slot isn't the offhand's bundle");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void noBundlesNoPage(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(9, new ItemStack(Items.SHULKER_BOX));
        helper.assertFalse(BandTargets.collect(player).stream().anyMatch(target -> target instanceof BandTargets.BandTarget.BundlePage),
            "The Bundles page is there without bundles");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void clickingTakesTheSelectedStack(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, bundleWith(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 5), new ItemStack(Items.STICK, 2)));
        BundleInventoryMenu menu = openBundles(player);
        menu.select(0, 1);
        menu.clicked(menu.getBackpackStart(), 0, ClickType.PICKUP, player);
        ItemStack bundle = player.getInventory().getItem(0);
        helper.assertTrue(menu.getCarried().is(Items.EMERALD) && menu.getCarried().getCount() == 5, "The emeralds aren't on the cursor");
        helper.assertTrue(count(bundle, Items.EMERALD) == 0 && count(bundle, Items.DIAMOND) == 3 && count(bundle, Items.STICK) == 2,
            "The bundle didn't lose just the emeralds");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rightClickingTakesHalf(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, bundleWith(new ItemStack(Items.EMERALD, 5)));
        BundleInventoryMenu menu = openBundles(player);
        menu.clicked(menu.getBackpackStart(), 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 3, "Expected 3 emeralds on the cursor, got " + menu.getCarried().getCount());
        helper.assertTrue(count(player.getInventory().getItem(0), Items.EMERALD) == 2, "Expected 2 emeralds left in the bundle");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void clickingWithAStackPutsItIn(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, bundleWith());
        BundleInventoryMenu menu = openBundles(player);
        menu.setCarried(new ItemStack(Items.DIRT, 10));
        menu.clicked(menu.getBackpackStart(), 1, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getCount() == 9, "Right-clicking didn't put just one in");
        menu.clicked(menu.getBackpackStart(), 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "Left-clicking didn't put the rest in");
        helper.assertTrue(count(player.getInventory().getItem(0), Items.DIRT) == 10, "The bundle doesn't hold the 10 dirt");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void shiftClickingMovesTheSelectedStackToTheInventory(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, bundleWith(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 5)));
        BundleInventoryMenu menu = openBundles(player);
        menu.select(0, 1);
        menu.clicked(menu.getBackpackStart(), 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 5, "The emeralds didn't go to the inventory");
        helper.assertTrue(count(player.getInventory().getItem(0), Items.EMERALD) == 0, "The emeralds are still in the bundle");
        helper.assertTrue(menu.getCarried().isEmpty(), "Something was left on the cursor");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void quickMoveNeverGoesIntoTheBundlesPage(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.setItem(0, bundleWith());
        for(int i = 9; i < 36; i++)
        {
            inventory.setItem(i, new ItemStack(Items.STONE, 64));
        }
        inventory.setItem(1, new ItemStack(Items.DIRT, 64));
        BundleInventoryMenu menu = openBundles(player);
        // Hotbar slot 1 is menu slot 37; the main inventory is full
        menu.quickMoveStack(player, 37);
        helper.assertTrue(inventory.getItem(1).is(Items.DIRT) && inventory.getItem(1).getCount() == 64, "The dirt left the hotbar");
        helper.assertTrue(count(inventory.getItem(0), Items.DIRT) == 0, "The dirt went into the bundle");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void movingABundleRedoesThePage(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Inventory inventory = player.getInventory();
        inventory.setItem(9, bundleWith());
        BundleInventoryMenu menu = openBundles(player);
        helper.assertFalse(menu.updateTargets(player), "The page was redone with nothing changed");
        inventory.setItem(10, inventory.removeItemNoUpdate(9));
        helper.assertTrue(menu.updateTargets(player), "The page wasn't redone for the moved bundle");
        BundleInventoryMenu reopened = openBundles(player);
        helper.assertTrue(reopened.getBundle(0) == inventory.getItem(10), "The redone page doesn't show the moved bundle");
        helper.succeed();
    }
    /** The item with the given id, if its mod is installed. */
    private static Optional<Item> modItem(String id)
    {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id));
    }

    /**
     * Puts two stacks of {@code item} into the one-item bag on the page by clicking, then takes them out again: one
     * stack per click, though the bag lists them as one larger stack.
     */
    private static void checkBulkBag(GameTestHelper helper, Item bagItem, Item item)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, new ItemStack(bagItem));
        BundleInventoryMenu menu = openBundles(player);
        int slot = menu.getBackpackStart();
        for(int i = 0; i < 2; i++)
        {
            menu.setCarried(new ItemStack(item, 64));
            menu.clicked(slot, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty(), "The bag didn't take the stack");
        }
        ItemStack bag = player.getInventory().getItem(0);
        helper.assertTrue(count(bag, item) == 128, "Expected 128 in the bag, got " + count(bag, item));
        menu.clicked(slot, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(item) && menu.getCarried().getCount() == 64, "Clicking didn't take one stack");
        helper.assertTrue(count(bag, item) == 64, "Expected 64 left in the bag, got " + count(bag, item));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void seedPouchesWorkOnTheBundlesPage(GameTestHelper helper)
    {
        modItem("quark:seed_pouch").ifPresentOrElse(pouch -> checkBulkBag(helper, pouch, Items.WHEAT_SEEDS), helper::succeed);
    }

    @GameTest(template = EMPTY)
    public static void trackLayersBagsWorkOnTheBundlesPage(GameTestHelper helper)
    {
        Optional<Item> track = modItem("create:track");
        modItem("createdieselgenerators:track_layers_bag").filter(bag -> track.isPresent())
            .ifPresentOrElse(bag -> checkBulkBag(helper, bag, track.get()), helper::succeed);
    }

    // ---------------------------------------------------------------------------------------------------------
    // The Mount page

    /** Seats the player on the vehicle, where they would be riding it (its menus check how far away they are). */
    private static void ride(GameTestHelper helper, ServerPlayer player, Entity vehicle)
    {
        helper.assertTrue(player.startRiding(vehicle, true), "The player couldn't ride " + vehicle);
        vehicle.positionRider(player);
    }

    private static <T extends AbstractHorse> T spawnTamed(GameTestHelper helper, EntityType<T> type)
    {
        T horse = helper.spawn(type, 1, 2, 1);
        horse.setTamed(true);
        return horse;
    }

    private static MountInventoryMenu openMount(ServerPlayer player)
    {
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        if(!(player.containerMenu instanceof MountInventoryMenu menu))
        {
            throw new GameTestAssertException("Expected the band to show the mount, got " + player.containerMenu);
        }
        return menu;
    }

    /** Runs what the server checks each tick for an open integrated inventory (see ServerEvents). */
    private static void serverTick(ServerPlayer player)
    {
        if(player.containerMenu instanceof IntegratedInventoryMenu menu && menu.isUpdateDue(player) && menu.updateTargets(player))
        {
            IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        }
    }

    private static int countStorage(MountInventoryMenu menu)
    {
        int storage = 0;
        for(int i = 0; i < menu.getMountSlotCount(); i++)
        {
            storage += menu.isStorage(i) ? 1 : 0;
        }
        return storage;
    }

    @GameTest(template = EMPTY)
    public static void horseComesFirstAndTakesItsSaddle(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, filledBox());
        Horse horse = spawnTamed(helper, EntityType.HORSE);
        ride(helper, player, horse);
        List<BandTargets.BandTarget> targets = BandTargets.collect(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "Looking for the mount opened a menu");
        helper.assertTrue(!targets.isEmpty() && targets.getFirst() instanceof BandTargets.BandTarget.Mount, "The mount isn't first in the band: " + targets);
        MountInventoryMenu menu = openMount(player);
        helper.assertTrue(menu.getMountSlotCount() == 2 && countStorage(menu) == 0, "Expected a saddle and an armor slot");
        player.getInventory().setItem(9, new ItemStack(Items.SADDLE));
        // Main inventory slot 9 is menu slot 9
        menu.quickMoveStack(player, 9);
        menu.broadcastChanges();
        helper.assertTrue(horse.isSaddled(), "Shift-clicking the saddle didn't saddle the horse");
        helper.assertTrue(player.getInventory().countItem(Items.SADDLE) == 0, "The saddle is still in the inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void dismountingLeavesTheMountPage(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, filledBox());
        Horse horse = spawnTamed(helper, EntityType.HORSE);
        ride(helper, player, horse);
        openMount(player);
        player.stopRiding();
        serverTick(player);
        helper.assertTrue(player.containerMenu instanceof ShulkerInventoryMenu, "Expected the shulker box after dismounting, got " + player.containerMenu);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void donkeyChestIsStorageAndRemovingItReopens(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Donkey donkey = spawnTamed(helper, EntityType.DONKEY);
        setChest(donkey, true);
        ride(helper, player, donkey);
        MountInventoryMenu menu = openMount(player);
        helper.assertTrue(menu.getMountSlotCount() == 17 && countStorage(menu) == 15, "Expected a saddle, armor and 15 chest slots, got "
            + menu.getMountSlotCount() + " with " + countStorage(menu) + " storage");
        setChest(donkey, false);
        serverTick(player);
        helper.assertTrue(player.containerMenu != menu, "The page wasn't reopened once the chest was gone");
        helper.assertTrue(player.containerMenu instanceof MountInventoryMenu reopened && countStorage(reopened) == 0,
            "Expected the page again without the chest, got " + player.containerMenu);
        helper.succeed();
    }

    /** Puts a chest on (or takes it off) a donkey as loading it does, which makes it a new inventory of the right size. */
    private static void setChest(AbstractChestedHorse horse, boolean chest)
    {
        CompoundTag tag = horse.saveWithoutId(new CompoundTag());
        tag.putBoolean("ChestedHorse", chest);
        horse.load(tag);
    }

    @GameTest(template = EMPTY)
    public static void llamaTakesCarpetButNoSaddle(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Llama llama = spawnTamed(helper, EntityType.LLAMA);
        ride(helper, player, llama);
        MountInventoryMenu menu = openMount(player);
        helper.assertTrue(!menu.getMountSlot(0).isActive(), "A llama has a saddle slot");
        helper.assertTrue(menu.getMountSlot(1).isActive() && menu.getMountSlot(1).mayPlace(new ItemStack(Items.RED_CARPET)), "A llama doesn't take carpet");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void camelHasASaddleButNoArmor(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Camel camel = helper.spawn(EntityType.CAMEL, 1, 2, 1);
        ride(helper, player, camel);
        MountInventoryMenu menu = openMount(player);
        helper.assertTrue(menu.getMountSlot(0).isActive() && !menu.getMountSlot(1).isActive(), "Expected only a saddle slot on a camel");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void chestBoatIsShownWithoutOpening(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Entity boat = helper.spawn(EntityType.CHEST_BOAT, 1, 2, 1);
        ride(helper, player, boat);
        List<BandTargets.BandTarget> targets = BandTargets.collect(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "Looking for the chest boat opened a menu");
        helper.assertTrue(!targets.isEmpty() && targets.getFirst() instanceof BandTargets.BandTarget.Mount, "The chest boat isn't in the band");
        MountInventoryMenu menu = openMount(player);
        helper.assertTrue(menu.getMountSlotCount() == 27 && countStorage(menu) == 27, "Expected the chest boat's 27 slots");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void untamedHorseIsNotShown(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Horse horse = helper.spawn(EntityType.HORSE, 1, 2, 1);
        ride(helper, player, horse);
        helper.assertTrue(BandTargets.collect(player).isEmpty(), "An untamed horse is in the band");
        helper.assertTrue(!Mounts.handleInventoryKey(player), "The inventory key didn't fall back to the inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void blacklistedMountKeepsItsOwnScreen(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        Horse horse = spawnTamed(helper, EntityType.HORSE);
        ride(helper, player, horse);
        List<? extends String> before = ServerConfig.MOUNT_LIST.get();
        ServerConfig.MOUNT_LIST.set(List.of("minecraft:horse"));
        try
        {
            helper.assertTrue(BandTargets.collect(player).isEmpty(), "A blacklisted horse is in the band");
            helper.assertTrue(Mounts.handleInventoryKey(player), "The inventory key didn't open the horse's own screen");
            helper.assertTrue(player.containerMenu instanceof HorseInventoryMenu, "Expected the horse's own menu, got " + player.containerMenu);
        }
        finally
        {
            ServerConfig.MOUNT_LIST.set(before);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bandRemembersLastPageAroundTheMount(GameTestHelper helper)
    {
        ServerPlayer player = mockPlayer(helper);
        player.getInventory().setItem(0, filledBox());
        Horse horse = spawnTamed(helper, EntityType.HORSE);
        ride(helper, player, horse);
        openMount(player);
        IntegratedInventoryMenu.open(player, Navigation.NEXT);
        helper.assertTrue(player.containerMenu instanceof ShulkerInventoryMenu, "Next from the mount isn't the shulker box");
        player.closeContainer();
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        helper.assertTrue(player.containerMenu instanceof ShulkerInventoryMenu, "Reopening didn't show the shulker box looked at last");
        IntegratedInventoryMenu.open(player, Navigation.NEXT);
        helper.assertTrue(player.containerMenu instanceof MountInventoryMenu, "Next from the last page didn't wrap around to the mount");
        player.closeContainer();
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        helper.assertTrue(player.containerMenu instanceof MountInventoryMenu, "Reopening didn't show the mount looked at last");
        player.closeContainer();
        player.stopRiding();
        IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        helper.assertTrue(player.containerMenu instanceof ShulkerInventoryMenu, "After dismounting, the shulker box looked at before wasn't shown");
        helper.succeed();
    }
}
