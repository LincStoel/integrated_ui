package com.lincstoel.integratedui.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BundleContents;

/** A bundle's contents in its tooltip, with one of them marked as selected (on the band's Bundles page). */
public record SelectedBundleTooltip(BundleContents contents, int selected) implements TooltipComponent {}
