package com.lincstoel.integratedui.compat.quark;

import com.lincstoel.integratedui.menu.BundleKind;
import net.minecraft.world.item.ItemStack;
import org.violetmoon.quark.content.tools.item.SeedPouchItem;

import java.util.List;

/**
 * Quark's seed pouch on the Bundles page: it holds one kind of seed (or fertilizer) in bulk, listed as a single stack
 * of the whole amount. Putting items in is left to the pouch itself (see Bundles.insert), which knows what it takes.
 * Only used when Quark is installed.
 */
public final class SeedPouches implements BundleKind
{
    @Override
    public boolean matches(ItemStack stack)
    {
        return stack.getItem() instanceof SeedPouchItem;
    }

    @Override
    public List<ItemStack> getContents(ItemStack bundle)
    {
        SeedPouchItem.PouchContents contents = SeedPouchItem.getContents(bundle);
        return contents.isEmpty() ? List.of() : List.of(contents.getContents().copyWithCount(contents.getCount()));
    }

    @Override
    public ItemStack take(ItemStack bundle, int index, int amount)
    {
        if(index != 0)
        {
            return ItemStack.EMPTY;
        }
        return SeedPouchItem.mutateContents(bundle, contents -> contents.isEmpty() ? ItemStack.EMPTY : contents.split(amount));
    }

    @Override
    public boolean insert(ItemStack bundle, ItemStack stack)
    {
        return SeedPouchItem.mutateContents(bundle, contents -> contents.absorb(stack));
    }

    @Override
    public ItemStack emptied(ItemStack bundle)
    {
        return new SeedPouchItem.PouchContents().writeToStack(bundle);
    }
}
