package com.lincstoel.integratedui.compat.dieselgenerators;

import com.jesz.createdieselgenerators.CDGDataComponents;
import com.jesz.createdieselgenerators.content.track_layers_bag.TrackLayersBagItem;
import com.jesz.createdieselgenerators.content.track_layers_bag.TrackLayersBagItemDataComponent;
import com.lincstoel.integratedui.menu.BundleKind;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Create Diesel Generators' track layers bag on the Bundles page: it holds one kind of track in bulk, listed as a
 * single stack of the whole amount. Putting tracks in is left to the bag itself (see Bundles.insert), which only takes
 * tracks. Only used when Create Diesel Generators is installed.
 */
public final class TrackLayersBags implements BundleKind
{
    @Override
    public boolean matches(ItemStack stack)
    {
        return stack.getItem() instanceof TrackLayersBagItem;
    }

    @Override
    public List<ItemStack> getContents(ItemStack bundle)
    {
        ItemStack tracks = TrackLayersBagItem.getTracks(bundle);
        return tracks.isEmpty() ? List.of() : List.of(tracks);
    }

    @Override
    public ItemStack take(ItemStack bundle, int index, int amount)
    {
        ItemStack tracks = TrackLayersBagItem.getTracks(bundle);
        if(index != 0 || tracks.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, tracks.getCount());
        int left = tracks.getCount() - taken;
        if(left > 0)
        {
            bundle.set(CDGDataComponents.TRACKS, new TrackLayersBagItemDataComponent(tracks.copyWithCount(left)));
        }
        else
        {
            bundle.remove(CDGDataComponents.TRACKS);
        }
        return tracks.copyWithCount(taken);
    }

    /** The bag takes tracks through its own item (see the class comment), so there is nothing to do here. */
    @Override
    public boolean insert(ItemStack bundle, ItemStack stack)
    {
        return false;
    }

    @Override
    public ItemStack emptied(ItemStack bundle)
    {
        bundle.remove(CDGDataComponents.TRACKS);
        return bundle;
    }
}
