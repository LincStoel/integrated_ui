package com.lincstoel.integratedui.compat.supplementaries;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.SeamlessMenuProvider;
import com.lincstoel.integratedui.menu.ShulkerContainer;
import net.mehvahdjukaar.supplementaries.common.items.SackItem;
import net.mehvahdjukaar.supplementaries.reg.ModSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Shows a Supplementaries sack from the inventory in Supplementaries' sack screen, with the sack's own slot locked;
 * closing the screen returns to the inventory. Sacks are otherwise handled as any other container item (see
 * ContainerItems), which opens them here. Only used when Supplementaries is installed.
 */
public final class SupplementariesCompat
{
    /** Only registered with Supplementaries installed, since the menu builds on Supplementaries' sack menu. */
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, IntegratedUI.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<PortableSackMenu>> PORTABLE_SACK =
        MENUS.register("portable_sack", () -> IMenuTypeExtension.create(PortableSackMenu::fromNetwork));

    private SupplementariesCompat() {}

    public static void register(IEventBus modBus)
    {
        MENUS.register(modBus);
        modBus.addListener(SupplementariesCompat::onModifyDefaultComponents);
    }

    /**
     * Gives every sack an empty container by default, as Supplementaries' own sack has: other mods' sacks (such as
     * Supplementaries Squared's dyed ones) leave it out, so they wouldn't be recognised as container items.
     */
    private static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event)
    {
        event.modifyMatching(item -> item instanceof SackItem && !item.components().has(DataComponents.CONTAINER),
            builder -> builder.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }

    /** Whether the stack is a single, unlooted sack. */
    public static boolean isSack(ItemStack stack)
    {
        return stack.getCount() == 1 && stack.getItem() instanceof SackItem && !stack.has(DataComponents.CONTAINER_LOOT);
    }

    /**
     * Shows the sack in the given slot of the player's inventory in Supplementaries' sack screen, with {@code size}
     * usable slots (see ContainerItems).
     */
    public static void showSack(ServerPlayer player, ItemStack stack, int slot, int size)
    {
        ShulkerContainer sack = new ShulkerContainer(player, stack, size, size);
        player.openMenu(new SeamlessMenuProvider((id, playerInventory, p) -> new PortableSackMenu(id, playerInventory, sack, size), stack.getHoverName()), buf -> {
            buf.writeVarInt(size);
            buf.writeVarInt(slot);
        });
        playSound(player, 0.95F);
    }

    static void playSound(Player player, float pitch)
    {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SACK_OPEN.get(), SoundSource.PLAYERS, 0.5F,
            player.level().random.nextFloat() * 0.1F + pitch);
    }
}
