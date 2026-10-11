package com.lincstoel.integratedui.client;

/**
 * An integrated inventory screen (the plain one, or Backpacked's), which forwards most of its work to its
 * {@link InventoryScreenParts}. Lets compat code (JEI, Mouse Tweaks, the effect list) treat both alike.
 */
public interface IntegratedScreen
{
    InventoryScreenParts parts();
}
