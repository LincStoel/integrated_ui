package com.lincstoel.integratedui.gametest;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.ContainerItems;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import com.lincstoel.integratedui.menu.ShulkerContainer;
import com.lincstoel.integratedui.menu.ShulkerInventoryMenu;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.LockCode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
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
        menu.clicked(IntegratedInventoryMenu.BACKPACK_START, 0, ClickType.SWAP, player);
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
        helper.assertTrue(menu.getSlot(IntegratedInventoryMenu.BACKPACK_START).getItem().getCount() == 54, "The rest didn't go into the band");
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
}
