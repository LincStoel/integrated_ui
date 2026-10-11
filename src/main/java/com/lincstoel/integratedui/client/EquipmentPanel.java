package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.compat.EquipmentBackend;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * The collapsible panel of Curios/Accessories slots on the left of the inventory, with its toggle buttons. Shared
 * by both inventory screens; the screen forwards layout, rendering and input to it. Coordinates passed in are
 * absolute screen coordinates unless noted otherwise.
 */
public class EquipmentPanel
{
    private static final ResourceLocation INVENTORY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private static final ResourceLocation UNUSED_SLOTS_SHOWN = ResourceLocation.fromNamespaceAndPath("accessories", "widget/unused_slots_shown");
    private static final ResourceLocation UNUSED_SLOTS_HIDDEN = ResourceLocation.fromNamespaceAndPath("accessories", "widget/unused_slots_hidden");
    private static final ResourceLocation ACCESSORIES_ICON = ResourceLocation.fromNamespaceAndPath("accessories", "textures/gui/accessories_open_icon.png");
    private static final ResourceLocation ACCESSORIES_ICON_HOVERED = ResourceLocation.fromNamespaceAndPath("accessories", "textures/gui/accessories_open_icon_hovered.png");
    private static final WidgetSprites CURIOS_ICON = new WidgetSprites(ResourceLocation.fromNamespaceAndPath("curios", "button_small"),
        ResourceLocation.fromNamespaceAndPath("curios", "button_small_highlighted"));
    private static final ResourceLocation ICON_COLLAPSE = IntegratedUI.rl("arrow_right");
    private static final ResourceLocation ICON_EXPAND = IntegratedUI.rl("arrow_left");

    private static final int PADDING = 7;
    /** Top of the panel, relative to the inventory window. */
    private static final int TOP = 4;
    private static final int MAX_COLUMNS = 3;
    private static final int SCROLLBAR_SIZE = 4;

    private final AbstractContainerScreen<?> screen;
    private final IntegratedInventoryMenu menu;
    private final EquipmentBackend equipment;
    private final List<Positioned> widgets = new ArrayList<>();
    private int columns = 1;
    private int rows;
    private int width;
    /** Left edge, relative to the inventory window. */
    private int x;
    private int scroll;
    private boolean scrollDragging;
    private int refreshTimer;
    /** The equipment slots currently shown in the panel (e.g. excluding unused Accessories slots). */
    private List<Slot> slots = List.of();
    @Nullable
    private AbstractWidget unusedSlotsButton;
    /** While the open recipe book covers the panel's place, the panel and its buttons are hidden. */
    private boolean covered;

    public EquipmentPanel(AbstractContainerScreen<?> screen, IntegratedInventoryMenu menu)
    {
        this.screen = screen;
        this.menu = menu;
        this.equipment = Equipment.get(menu.getData().equipment().backend());
    }

    private LocalPlayer player()
    {
        return Minecraft.getInstance().player;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Layout

    /** Lays the panel out for the screen's current window height. */
    public void layout()
    {
        this.refreshSlots();
        this.layoutSlots();
    }

    /** Re-evaluates which equipment slots are shown. Returns true if that changed. */
    private boolean refreshSlots()
    {
        Predicate<Slot> filter = this.equipment.createVisibilityFilter(this.player());
        List<Slot> visible = new ArrayList<>();
        for(int i = this.menu.getEquipmentStart(); i < this.menu.getEquipmentEnd(); i++)
        {
            Slot slot = this.menu.slots.get(i);
            if(filter.test(slot))
            {
                visible.add(slot);
            }
        }
        if(visible.equals(this.slots))
        {
            return false;
        }
        this.slots = visible;
        return true;
    }

    private void layoutSlots()
    {
        int count = this.slots.size();
        if(count > 0)
        {
            int rowsFit = Math.max(1, (this.screen.getYSize() - TOP - PADDING * 2) / 18);
            this.columns = Mth.clamp(Mth.positiveCeilDiv(count, rowsFit), 1, MAX_COLUMNS);
            this.rows = Math.min(rowsFit, Mth.positiveCeilDiv(count, this.columns));
            this.width = PADDING * 2 + this.columns * 18 + (this.hasScroll() ? SCROLLBAR_SIZE + 3 : 0);
            this.x = -this.width + 3;
        }
        else
        {
            this.columns = 1;
            this.rows = 0;
            this.width = 0;
            this.x = 0;
        }
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
        this.applySlotPositions();
    }

    private int equipmentCount()
    {
        return this.menu.getEquipmentEnd() - this.menu.getEquipmentStart();
    }

    /** Whether the panel is open, as the player left it (its buttons exist whenever the player has any equipment slots). */
    private static boolean isOpen()
    {
        return ClientConfig.get(ClientConfig.EQUIPMENT_PANEL_OPEN);
    }

    /** Whether the panel is open and not covered by the recipe book. */
    private boolean isShown()
    {
        return this.equipmentCount() > 0 && isOpen() && !this.covered;
    }

    /** Hides the panel while the recipe book is open (vanilla's book sits where the panel goes), or shows it again. */
    public void setCovered(boolean covered)
    {
        if(this.covered != covered)
        {
            this.covered = covered;
            this.layoutSlots();
            this.syncShown();
        }
    }

    /**
     * Tells the server whether the panel's slots can be seen, so shift-clicks only equip into slots the player sees
     * (see {@link IntegratedInventoryMenu#equipmentButton}).
     */
    private void syncShown()
    {
        Minecraft mc = Minecraft.getInstance();
        int button = IntegratedInventoryMenu.equipmentButton(this.isShown());
        if(mc.player != null && mc.gameMode != null && this.menu.clickMenuButton(mc.player, button))
        {
            mc.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
        }
    }

    /** Whether the panel is open and has slots to draw. */
    private boolean isDrawn()
    {
        return this.isShown() && !this.slots.isEmpty();
    }

    /** Left edge of whatever is drawn left of the inventory (relative coords). */
    private int left()
    {
        return this.isDrawn() ? this.x : 0;
    }

    private boolean hasScroll()
    {
        return Mth.positiveCeilDiv(this.slots.size(), this.columns) > this.rows;
    }

    private int maxScroll()
    {
        if(this.slots.isEmpty())
        {
            return 0;
        }
        return Math.max(0, Mth.positiveCeilDiv(this.slots.size(), this.columns) - this.rows);
    }

    private int height()
    {
        return PADDING * 2 + this.rows * 18;
    }

    private void applySlotPositions()
    {
        boolean shown = this.isShown();
        for(int i = this.menu.getEquipmentStart(); i < this.menu.getEquipmentEnd(); i++)
        {
            Slot slot = this.menu.slots.get(i);
            slot.x = IntegratedInventoryMenu.HIDDEN;
            slot.y = IntegratedInventoryMenu.HIDDEN;
        }
        for(int i = 0; i < this.slots.size(); i++)
        {
            Slot slot = this.slots.get(i);
            int row = i / Math.max(1, this.columns) - this.scroll;
            int column = i % Math.max(1, this.columns);
            if(shown && row >= 0 && row < this.rows)
            {
                slot.x = this.x + PADDING + 1 + column * 18;
                slot.y = TOP + PADDING + 1 + row * 18;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Widgets

    /** Creates the panel's buttons: show/hide toggle, then show/hide unused slots, then the Accessories screen. */
    public void init(Consumer<AbstractWidget> adder)
    {
        this.widgets.clear();
        this.unusedSlotsButton = null;
        if(this.equipmentCount() > 0)
        {
            SpriteButton toggle = new SpriteButton(12, 12, () -> this.isShown() ? ICON_COLLAPSE : ICON_EXPAND, button -> {
                ClientConfig.EQUIPMENT_PANEL_OPEN.set(!isOpen());
                ClientConfig.EQUIPMENT_PANEL_OPEN.save();
                this.layoutSlots();
                this.positionWidgets();
                this.syncShown();
                ButtonLabels.set(button, this.createToggleLabel());
            });
            ButtonLabels.set(toggle, this.createToggleLabel());
            this.add(adder, toggle, () -> this.left() - 13, () -> TOP + 2);

            if(this.equipment.hasUnusedSlots())
            {
                SpriteButton unused = new SpriteButton(12, 12, () -> this.equipment.areUnusedSlotsShown(this.player()) ? UNUSED_SLOTS_SHOWN : UNUSED_SLOTS_HIDDEN, button -> {
                    this.equipment.setUnusedSlotsShown(this.player(), !this.equipment.areUnusedSlotsShown(this.player()));
                    this.layout();
                    this.positionWidgets();
                    ButtonLabels.set(button, this.createUnusedSlotsLabel());
                });
                ButtonLabels.set(unused, this.createUnusedSlotsLabel());
                this.add(adder, unused, () -> this.left() - 13, () -> TOP + 2 + 14);
                this.unusedSlotsButton = unused;
            }
        }
        if(this.equipment.hasFullScreen())
        {
            boolean curios = this.menu.getData().equipment().backend() == Equipment.Kind.CURIOS;
            Component name = Component.translatable(curios ? "integrated_ui.gui.open_curios" : "integrated_ui.gui.open_accessories");
            AbstractWidget fullScreen = curios ? new ImageButton(8, 8, CURIOS_ICON, button -> this.equipment.openFullScreen(), name)
                : new TextureButton(8, 8, ACCESSORIES_ICON, ACCESSORIES_ICON_HOVERED, button -> this.equipment.openFullScreen());
            ButtonLabels.set(fullScreen, name);
            this.add(adder, fullScreen, () -> this.left() - 11, this::fullScreenButtonY);
        }
        this.syncShown();
    }

    private void add(Consumer<AbstractWidget> adder, AbstractWidget widget, IntSupplier x, IntSupplier y)
    {
        adder.accept(widget);
        this.widgets.add(new Positioned(widget, x, y));
    }

    /** Moves the panel's buttons to match the panel and the inventory window's current position. */
    public void positionWidgets()
    {
        for(Positioned positioned : this.widgets)
        {
            positioned.widget.visible = !this.covered;
            positioned.widget.setPosition(this.screen.getGuiLeft() + positioned.x.getAsInt(), this.screen.getGuiTop() + positioned.y.getAsInt());
        }
        if(this.unusedSlotsButton != null)
        {
            this.unusedSlotsButton.visible = this.isShown();
        }
    }

    /** Below the panel toggle, and below the unused-slots toggle while that is shown. */
    private int fullScreenButtonY()
    {
        int y = TOP + 2;
        if(this.equipmentCount() > 0)
        {
            y += 14;
        }
        if(this.unusedSlotsButton != null && this.unusedSlotsButton.visible)
        {
            y += 14;
        }
        return y + 2;
    }

    private Component createToggleLabel()
    {
        String key = this.isShown() ? "integrated_ui.gui.hide_panel" : "integrated_ui.gui.show_panel";
        return Component.translatable(key, this.equipment.getPanelName());
    }

    private Component createUnusedSlotsLabel()
    {
        boolean shown = this.equipment.areUnusedSlotsShown(this.player());
        return Component.translatable(shown ? "integrated_ui.gui.hide_unused_slots" : "integrated_ui.gui.show_unused_slots");
    }

    public void tick()
    {
        // Which Accessories slots count as used depends on the items carried, so re-check periodically
        if(this.refreshTimer-- <= 0)
        {
            this.refreshTimer = 10;
            if(this.refreshSlots())
            {
                this.layoutSlots();
                this.positionWidgets();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rendering

    public void render(GuiGraphics graphics)
    {
        if(!this.isDrawn())
        {
            return;
        }
        int left = this.screen.getGuiLeft();
        int top = this.screen.getGuiTop();
        int x = left + this.x;
        int y = top + TOP;
        renderBackground(graphics, x, y, this.width, this.height());
        for(Slot slot : this.slots)
        {
            if(slot.x != IntegratedInventoryMenu.HIDDEN)
            {
                graphics.blitSprite(SLOT, left + slot.x - 1, top + slot.y - 1, 18, 18);
            }
        }
        if(this.hasScroll())
        {
            int trackX = x + PADDING + this.columns * 18 + 2;
            int trackY = y + PADDING;
            renderScrollbar(graphics, trackX, trackY, SCROLLBAR_SIZE, this.rows * 18, false,
                this.scroll, this.maxScroll(), this.rows, Mth.positiveCeilDiv(this.slots.size(), this.columns));
        }
    }

    /** Draws a window background of any size, nine-sliced from the vanilla inventory texture's frame. */
    private static void renderBackground(GuiGraphics graphics, int x, int y, int width, int height)
    {
        int b = 4;
        int right = x + width - b;
        int bottom = y + height - b;
        graphics.fill(x + b, y + b, right, bottom, 0xFFC6C6C6);
        graphics.blit(INVENTORY_TEXTURE, x, y, 0, 0, b, b);
        graphics.blit(INVENTORY_TEXTURE, right, y, 176 - b, 0, b, b);
        graphics.blit(INVENTORY_TEXTURE, x, bottom, 0, 166 - b, b, b);
        graphics.blit(INVENTORY_TEXTURE, right, bottom, 176 - b, 166 - b, b, b);
        graphics.blit(INVENTORY_TEXTURE, x + b, y, width - b * 2, b, 8, 0, 1, b, 256, 256);
        graphics.blit(INVENTORY_TEXTURE, x + b, bottom, width - b * 2, b, 8, 166 - b, 1, b, 256, 256);
        graphics.blit(INVENTORY_TEXTURE, x, y + b, b, height - b * 2, 0, 8, b, 1, 256, 256);
        graphics.blit(INVENTORY_TEXTURE, right, y + b, b, height - b * 2, 176 - b, 8, b, 1, 256, 256);
    }

    /** A plain scrollbar track and thumb, shared with the band so both look the same. */
    public static void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height, boolean horizontal, int value, int max, int visible, int total)
    {
        renderScrollbar(graphics, ScrollbarColors.GREY, x, y, width, height, horizontal, value, max, visible, total);
    }

    public static void renderScrollbar(GuiGraphics graphics, ScrollbarColors colors, int x, int y, int width, int height, boolean horizontal, int value, int max, int visible, int total)
    {
        graphics.fill(x, y, x + width, y + height, colors.track());
        int length = horizontal ? width : height;
        int thumbLength = Math.max(6, length * visible / Math.max(1, total));
        int thumbOffset = max > 0 ? (length - thumbLength) * value / max : 0;
        if(horizontal)
        {
            graphics.fill(x + thumbOffset, y, x + thumbOffset + thumbLength, y + height, colors.shadow());
            graphics.fill(x + thumbOffset, y, x + thumbOffset + thumbLength - 1, y + height - 1, colors.thumb());
        }
        else
        {
            graphics.fill(x, y + thumbOffset, x + width, y + thumbOffset + thumbLength, colors.shadow());
            graphics.fill(x, y + thumbOffset, x + width - 1, y + thumbOffset + thumbLength - 1, colors.thumb());
        }
    }

    /** The ARGB colours of a scrollbar's track, its thumb, and the thumb's bottom-right shadow. */
    public record ScrollbarColors(int track, int thumb, int shadow)
    {
        /** Matches Backpacked's brown backpack sprites. */
        public static final ScrollbarColors BROWN = new ScrollbarColors(0xFF8B7B66, 0xFFF3E4CD, 0xFF5B4B3B);
        /** Matches the Quark backpack's leather. */
        public static final ScrollbarColors LEATHER = new ScrollbarColors(0xFF722C12, 0xFFC65C35, 0xFF542716);
        /** Matches the vanilla inventory's greys. */
        public static final ScrollbarColors GREY = new ScrollbarColors(0xFF8B8B8B, 0xFFE8E8E8, 0xFF555555);

        /** These colours multiplied by an RGB {@code tint}, as the band's sprites are. */
        public ScrollbarColors tinted(int tint)
        {
            return new ScrollbarColors(multiply(this.track, tint), multiply(this.thumb, tint), multiply(this.shadow, tint));
        }

        private static int multiply(int color, int tint)
        {
            int r = (color >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
            int g = (color >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
            int b = (color & 0xFF) * (tint & 0xFF) / 255;
            return color & 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    /** Names empty equipment slots on hover. Returns true if a tooltip was drawn. */
    public boolean renderTooltip(GuiGraphics graphics, Font font, @Nullable Slot hoveredSlot, int mouseX, int mouseY)
    {
        if(this.menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem() && this.menu.isEquipmentSlot(hoveredSlot.index))
        {
            Component name = this.equipment.getSlotName(hoveredSlot);
            if(name != null)
            {
                graphics.renderTooltip(font, name, mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Input

    /** Whether the mouse is over the drawn panel. */
    public boolean contains(double mouseX, double mouseY)
    {
        double x = mouseX - this.screen.getGuiLeft();
        double y = mouseY - this.screen.getGuiTop();
        return this.isDrawn() && x >= this.x && x < this.x + this.width && y >= TOP && y < TOP + this.height();
    }

    /** {@code step} is -1 to scroll up and 1 to scroll down. Returns true if the panel scrolled. */
    public boolean mouseScrolled(double mouseX, double mouseY, int step)
    {
        if(step != 0 && this.hasScroll() && this.contains(mouseX, mouseY))
        {
            this.setScroll(this.scroll + step);
            return true;
        }
        return false;
    }

    /** Starts dragging the scrollbar if it was clicked. */
    public boolean mouseClicked(double mouseX, double mouseY)
    {
        double x = mouseX - this.screen.getGuiLeft();
        double y = mouseY - this.screen.getGuiTop();
        if(this.isDrawn() && this.hasScroll())
        {
            int trackX = this.x + PADDING + this.columns * 18 + 1;
            if(x >= trackX && x < trackX + SCROLLBAR_SIZE + 2 && y >= TOP + PADDING && y < TOP + PADDING + this.rows * 18)
            {
                this.scrollDragging = true;
                this.mouseDragged(mouseY);
                return true;
            }
        }
        return false;
    }

    /** Returns true while the scrollbar is being dragged. */
    public boolean mouseDragged(double mouseY)
    {
        if(!this.scrollDragging)
        {
            return false;
        }
        double y = mouseY - this.screen.getGuiTop();
        double progress = (y - (TOP + PADDING)) / (this.rows * 18.0);
        this.setScroll((int) Math.round(progress * this.maxScroll()));
        return true;
    }

    /** Ends a scrollbar drag. Returns true if one was in progress. */
    public boolean mouseReleased()
    {
        boolean wasDragging = this.scrollDragging;
        this.scrollDragging = false;
        return wasDragging;
    }

    private void setScroll(int scroll)
    {
        scroll = Mth.clamp(scroll, 0, this.maxScroll());
        if(scroll != this.scroll)
        {
            this.scroll = scroll;
            this.applySlotPositions();
        }
    }

    /** The area covered by the panel and its buttons (absolute coords), for JEI exclusion, or null if none. */
    @Nullable
    public Rect2i getExtraArea()
    {
        if(this.equipmentCount() == 0 || this.covered)
        {
            return null;
        }
        int x = this.left() - 14;
        int width = this.isDrawn() ? this.width + 14 : 14;
        int height = Math.max(this.isDrawn() ? this.height() : 0, 44);
        return new Rect2i(this.screen.getGuiLeft() + x, this.screen.getGuiTop() + TOP, width, height);
    }

    private record Positioned(AbstractWidget widget, IntSupplier x, IntSupplier y) {}
}
