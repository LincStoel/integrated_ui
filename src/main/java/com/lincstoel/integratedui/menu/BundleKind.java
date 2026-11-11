package com.lincstoel.integratedui.menu;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One way an item can keep its contents that the Bundles page knows how to use (see {@link Bundles}): vanilla's
 * bundle contents component, an item handler, or a mod's own component. Works on both sides.
 */
public interface BundleKind
{
    /** Whether the stack is an item of this kind. Only asked of single stacks. */
    boolean matches(ItemStack stack);

    /**
     * What the bundle holds, in the order the page lists it. A stack may be larger than its own maximum stack size, for
     * items holding one item in bulk (such as a seed pouch). Not to be changed.
     */
    List<ItemStack> getContents(ItemStack bundle);

    /** Takes up to {@code amount} of the stack at {@code index} out of the bundle. Returns what was taken, or empty. */
    ItemStack take(ItemStack bundle, int index, int amount);

    /**
     * Puts what fits of {@code stack} into the bundle, shrinking it. Only used when the bundle's item didn't take it
     * itself (see {@link Bundles#insert}). Returns whether anything went in.
     */
    boolean insert(ItemStack bundle, ItemStack stack);

    /** The bundle with its contents removed, as the band's tab shows it. */
    ItemStack emptied(ItemStack bundle);

    /**
     * The contents to show in the bundle's tooltip, with the page's selection marked, when its own tooltip doesn't
     * show them as a bundle's does; null to leave its tooltip as it is.
     */
    @Nullable
    default BundleContents getTooltipContents(ItemStack bundle)
    {
        return null;
    }
}
