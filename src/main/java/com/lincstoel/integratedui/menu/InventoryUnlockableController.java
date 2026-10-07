package com.lincstoel.integratedui.menu;

import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.common.CostModel;
import com.mrcrayfish.backpacked.common.PaymentItem;
import com.mrcrayfish.backpacked.common.backpack.UnlockableSlots;
import com.mrcrayfish.backpacked.core.ModDataComponents;
import com.mrcrayfish.backpacked.inventory.BackpackInventory;
import com.mrcrayfish.backpacked.inventory.container.UnlockableController;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Mirrors Backpacked's own backpack slot controller (BackpackContainerMenu.BackpackUnlockableController), so
 * unlocking from the inventory screen costs and stores exactly the same as unlocking from Backpacked's screen.
 * On the client the container is a plain SimpleContainer, so only the cached slots are used there.
 */
public class InventoryUnlockableController extends UnlockableController
{
    private final Container backpack;
    private final List<Container> paymentContainers;

    public InventoryUnlockableController(Container backpack, UnlockableSlots slots, List<Container> paymentContainers)
    {
        super(slots);
        this.backpack = backpack;
        this.paymentContainers = paymentContainers;
    }

    private ItemStack getBackpackStack()
    {
        return this.backpack instanceof BackpackInventory inventory ? inventory.getBackpackStack() : ItemStack.EMPTY;
    }

    @Override
    public Optional<UnlockableSlots> getSlots(Player player)
    {
        ItemStack stack = this.getBackpackStack();
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable(stack.get(ModDataComponents.UNLOCKABLE_SLOTS.get()));
    }

    @Override
    public void setSlots(Player player, UnlockableSlots slots)
    {
        ItemStack stack = this.getBackpackStack();
        if(!stack.isEmpty())
        {
            stack.set(ModDataComponents.UNLOCKABLE_SLOTS.get(), slots);
            this.backpack.setChanged();
        }
    }

    @Override
    public CostModel getCostModel()
    {
        return Config.BACKPACK.inventory.slots.unlockCost;
    }

    @Override
    public PaymentItem getPaymentItem()
    {
        return Config.getInventoryPaymentItem();
    }

    @Override
    public List<Container> getPaymentContainers()
    {
        return this.paymentContainers;
    }

    @Override
    public boolean allowsUnlockToken()
    {
        return Config.BACKPACK.inventory.slots.allowUnlockingUsingUnlockToken.get();
    }
}
