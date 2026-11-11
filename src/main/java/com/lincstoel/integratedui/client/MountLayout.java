package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.MountInventoryMenu;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.BitSet;

/**
 * Where the Mount page puts its slots, which unlike the band's other pages don't scroll: the equipment (saddle, armor)
 * in a column on the left as in vanilla's horse screen, then a portrait of the mount, then its storage, right-aligned
 * in as many of its own screen's columns as fit. Only slots in use are placed; the others are hidden. Coordinates are
 * relative to the inventory window, and slot coordinates are those of the item inside it (its background is a pixel
 * further out).
 */
record MountLayout(int[] xs, int[] ys, BitSet active, int portraitX, int portraitY, int portraitWidth, int contentHeight)
{
    private static final int EQUIPMENT_X = 8;
    /** The right edge of the slot backgrounds, as in the inventory below. */
    private static final int RIGHT = 169;
    private static final int PORTRAIT_WIDTH = 32;
    private static final int GAP = 2;
    private static final int EQUIPMENT_PER_COLUMN = 3;
    private static final int MIN_ROWS = 2;

    private static final ResourceLocation SADDLE_SLOT = ResourceLocation.withDefaultNamespace("container/horse/saddle_slot");
    private static final ResourceLocation ARMOR_SLOT = ResourceLocation.withDefaultNamespace("container/horse/armor_slot");
    private static final ResourceLocation LLAMA_ARMOR_SLOT = ResourceLocation.withDefaultNamespace("container/horse/llama_armor_slot");
    private static final ItemStack SADDLE = new ItemStack(Items.SADDLE);
    private static final ItemStack HORSE_ARMOR = new ItemStack(Items.IRON_HORSE_ARMOR);
    private static final ItemStack CARPET = new ItemStack(Items.WHITE_CARPET);

    /** Lays the page out below {@code top}, the y of the first row of slots. */
    static MountLayout compute(MountInventoryMenu menu, int top)
    {
        int count = menu.getMountSlotCount();
        int[] xs = new int[count];
        int[] ys = new int[count];
        BitSet active = activeSlots(menu);
        int equipment = 0;
        int storage = 0;
        for(int i = 0; i < count; i++)
        {
            xs[i] = IntegratedInventoryMenu.HIDDEN;
            ys[i] = IntegratedInventoryMenu.HIDDEN;
            if(active.get(i))
            {
                if(menu.isStorage(i))
                {
                    storage++;
                }
                else
                {
                    equipment++;
                }
            }
        }

        int equipmentColumns = Mth.positiveCeilDiv(equipment, EQUIPMENT_PER_COLUMN);
        int equipmentRows = Math.min(equipment, EQUIPMENT_PER_COLUMN);
        int portraitX = equipmentColumns > 0 ? EQUIPMENT_X - 1 + equipmentColumns * 18 + GAP : EQUIPMENT_X - 1;
        int storageLeft = portraitX + PORTRAIT_WIDTH + GAP;
        int fitting = Math.max(1, (RIGHT - storageLeft) / 18);
        int sourceColumns = menu.getStorageColumns() > 0 ? menu.getStorageColumns() : fitting;
        int storageColumns = Math.max(1, Math.min(Math.min(sourceColumns, fitting), storage));
        int storageRows = Mth.positiveCeilDiv(storage, storageColumns);
        int firstStorageX = RIGHT - 18 + 1 - (storageColumns - 1) * 18;

        int placedEquipment = 0;
        int placedStorage = 0;
        for(int i = 0; i < count; i++)
        {
            if(!active.get(i))
            {
                continue;
            }
            if(menu.isStorage(i))
            {
                xs[i] = firstStorageX + placedStorage % storageColumns * 18;
                ys[i] = top + placedStorage / storageColumns * 18;
                placedStorage++;
            }
            else
            {
                xs[i] = EQUIPMENT_X + placedEquipment / EQUIPMENT_PER_COLUMN * 18;
                ys[i] = top + placedEquipment % EQUIPMENT_PER_COLUMN * 18;
                placedEquipment++;
            }
        }
        int rows = Math.max(MIN_ROWS, Math.max(equipmentRows, storageRows));
        return new MountLayout(xs, ys, active, portraitX, top - 1, PORTRAIT_WIDTH, rows * 18);
    }

    /** Which of the page's slots are in use, which decides the layout. */
    static BitSet activeSlots(MountInventoryMenu menu)
    {
        BitSet active = new BitSet();
        for(int i = 0; i < menu.getMountSlotCount(); i++)
        {
            active.set(i, menu.getMountSlot(i).isActive());
        }
        return active;
    }

    /** Moves the page's slots to their places, or hidden: everything is hidden while {@code shown} is false. */
    void apply(MountInventoryMenu menu, boolean shown)
    {
        for(int i = 0; i < this.xs.length; i++)
        {
            Slot slot = menu.getMountSlot(i);
            slot.x = shown ? this.xs[i] : IntegratedInventoryMenu.HIDDEN;
            slot.y = shown ? this.ys[i] : IntegratedInventoryMenu.HIDDEN;
        }
    }

    /**
     * The sprite of an empty equipment slot, as vanilla's horse screen draws it: whatever the slot takes (a saddle,
     * horse armor or a llama's carpet), whichever mount it belongs to. Null for any other slot.
     */
    @Nullable
    static ResourceLocation equipmentSprite(Slot slot)
    {
        if(slot.mayPlace(SADDLE))
        {
            return SADDLE_SLOT;
        }
        if(slot.mayPlace(HORSE_ARMOR))
        {
            return ARMOR_SLOT;
        }
        if(slot.mayPlace(CARPET))
        {
            return LLAMA_ARMOR_SLOT;
        }
        return null;
    }

    /**
     * Draws the mount inside the box (absolute coordinates), sized to fit it whatever its size: following the mouse
     * as the player's own portrait does, or turned three quarters towards the viewer if it isn't a living thing (such
     * as a boat).
     */
    static void renderPortrait(GuiGraphics graphics, Entity entity, int x1, int y1, int x2, int y2, int mouseX, int mouseY)
    {
        float size = Math.max(entity.getBbWidth(), entity.getBbHeight());
        int scale = (int) Mth.clamp(0.7F * Math.min(x2 - x1, y2 - y1) / Math.max(size, 0.1F), 2.0F, 40.0F);
        if(entity instanceof LivingEntity living)
        {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, scale, 0.25F, mouseX, mouseY, living);
            return;
        }
        graphics.enableScissor(x1, y1, x2, y2);
        graphics.pose().pushPose();
        // As InventoryScreen.renderEntityInInventory does, which only takes living things
        graphics.pose().translate((x1 + x2) / 2.0F, (y1 + y2) / 2.0F + entity.getBbHeight() / 2.0F * scale, 50.0F);
        graphics.pose().scale(scale, scale, -scale);
        graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(-20.0)).rotateY((float) Math.toRadians(135.0)));
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, graphics.pose(), graphics.bufferSource(), 0xF000F0));
        graphics.flush();
        dispatcher.setRenderShadow(true);
        graphics.pose().popPose();
        Lighting.setupFor3DItems();
        graphics.disableScissor();
    }
}
