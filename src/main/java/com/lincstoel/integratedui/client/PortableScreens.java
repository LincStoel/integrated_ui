package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Shared by the screens of container items opened from the player's inventory (shulker boxes, sacks and the like):
 * they grey out the item's own slot, and return to the inventory when closed, if that is where they were opened from.
 */
public final class PortableScreens
{
    /** Whether the open container item was opened from the player's inventory, so closing it goes back there. */
    private static boolean openedFromInventory = true;

    private PortableScreens() {}

    /**
     * Notes the screen a container item is being opened from. Closing the item returns to it if it is the player's
     * inventory (ours included); any other container (a chest, say) can't be reopened, so closing goes to the world.
     */
    public static void noteOpenedFrom(@Nullable Screen screen)
    {
        openedFromInventory = screen instanceof AbstractContainerScreen<?> containerScreen && containerScreen.getMenu() instanceof InventoryMenu;
    }

    /** Shades the open item's own slot, which is locked. Call after the slot is drawn. */
    public static void renderLockedSlot(GuiGraphics graphics, Slot slot)
    {
        // Above the item, as Quark does for the held shulker box
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x88000000);
        graphics.pose().popPose();
    }

    /**
     * Returns to the inventory (or the worn Quark backpack) in place of closing the screen, keeping the cursor where
     * it is. Returns false, doing nothing, if the screen should just close: it wasn't opened from the inventory, or
     * there is no player to return with.
     */
    public static boolean returnToInventory()
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if(player == null || !openedFromInventory)
        {
            return false;
        }
        if(ClientEvents.replacesInventory())
        {
            // The server swaps the integrated inventory in for this screen's menu, without closing the screen in between
            ClientEvents.requestInventory();
            return true;
        }
        double mouseX = mc.mouseHandler.xpos();
        double mouseY = mc.mouseHandler.ypos();
        player.closeContainer();
        if(IntegratedUI.isQuarkEnabled() && CompatGuard.test(CompatGuard.QUARK, () -> QuarkClientCompat.requestBackpackIfWorn(player)))
        {
            return true;
        }
        mc.setScreen(new InventoryScreen(player));
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mouseX, mouseY);
        return true;
    }
}
