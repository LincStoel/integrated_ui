package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.config.ServerConfig;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import com.lincstoel.integratedui.menu.OpenData;
import com.lincstoel.integratedui.menu.ShulkerInventoryMenu;
import com.lincstoel.integratedui.network.OpenInventoryPayload;
import com.lincstoel.integratedui.network.RenamePayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * The band inserted into the survival inventory between the crafting area and the main inventory, as Quark does for
 * its backpack, showing a backpack's (Backpacked's or Quark's) or a container item's (such as a shulker box's) slots with a header of buttons. Shared by both inventory
 * screens, which forward layout, rendering and input to it; with an empty band it takes no space at all. Unless
 * noted otherwise, coordinates passed in are absolute screen coordinates and those kept here are relative to the
 * inventory window.
 */
public class ContainerBand
{
    public static final ResourceLocation INVENTORY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final Component PREVIOUS = Component.translatable("integrated_ui.gui.previous");
    private static final Component NEXT = Component.translatable("integrated_ui.gui.next");
    private static final Component RENAME = Component.translatable("integrated_ui.gui.rename");
    private static final ResourceLocation ICON_COLLAPSE = IntegratedUI.rl("band_collapse");
    private static final ResourceLocation ICON_EXPAND = IntegratedUI.rl("band_expand");

    // Vanilla inventory texture geometry
    public static final int VANILLA_WIDTH = 176;
    public static final int VANILLA_HEIGHT = 166;
    /** The band is inserted at this y, between the armor/crafting area and the main inventory. */
    public static final int SPLIT_Y = 80;

    // Band geometry
    private static final int BAND_X = 4;
    private static final int BAND_WIDTH = VANILLA_WIDTH - 8;
    private static final int BAND_HEADER = 15;
    private static final int BAND_BOTTOM = 5;
    /** Space left below the band for Quark's sort button, which sits just above the main inventory. */
    private static final int SORT_BUTTON_GAP = 10;
    private static final int HEADER_Y = SPLIT_Y + 2;
    private static final int HEADER_RIGHT = BAND_X + BAND_WIDTH - 4;
    private static final int TITLE_X = BAND_X + 5;
    private static final int SCROLLBAR_SIZE = 4;
    private static final int H_SCROLLBAR_SPACE = 6;
    private static final int V_SCROLLBAR_WIDTH = 11;
    private static final int MAX_VISIBLE_COLUMNS = 9;
    private static final int BUTTON_SIZE = 12;
    private static final int RENAME_SIZE = 10;
    private static final int TOGGLE_HEIGHT = 9;
    /** The collapsed band: just a divider line, showing there is a band to open. */
    private static final int COLLAPSED_HEIGHT = 4;

    private final AbstractContainerScreen<?> screen;
    private final IntegratedInventoryMenu menu;
    private final OpenData data;
    private final Style style;
    private final Runnable onScroll;
    private final List<Positioned> widgets = new ArrayList<>();
    @Nullable
    private AbstractWidget extraButton;
    @Nullable
    private Button previous;
    @Nullable
    private Button next;
    @Nullable
    private EditBox renameBox;

    /** Whether there is a band and it is open (not collapsed to a divider). Set by {@link #layout}. */
    private boolean expanded;
    private int visibleColumns;
    private int visibleRows;
    private int height;
    /** Space between the band and the main inventory. */
    private int gap;
    private int gridX;
    private int gridY;
    private int scrollColumn;
    private int scrollRow;
    private ScrollDrag scrollDrag = ScrollDrag.NONE;
    /** The position text's width when the header was last laid out. */
    private int lastPositionWidth;

    /** {@code onScroll} is called whenever the grid scrolls, which moves the slots. */
    public ContainerBand(AbstractContainerScreen<?> screen, IntegratedInventoryMenu menu, Style style, Runnable onScroll)
    {
        this.screen = screen;
        this.menu = menu;
        this.data = menu.getData();
        this.style = style;
        this.onScroll = onScroll;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Layout

    /** Lays the band out for the given screen height, moving the inventory slots below it down. */
    public void layout(int screenHeight)
    {
        this.expanded = this.data.hasBand() && ClientConfig.get(ClientConfig.CONTAINER_BAND_OPEN);
        if(this.expanded)
        {
            this.gap = sortButtonGap();
            this.visibleColumns = Math.min(this.data.columns(), MAX_VISIBLE_COLUMNS);
            int rowsThatFit = Math.max(1, (screenHeight - VANILLA_HEIGHT - BAND_HEADER - BAND_BOTTOM - H_SCROLLBAR_SPACE - this.gap - 4) / 18);
            this.visibleRows = Mth.clamp(Math.min(this.data.rows(), ClientConfig.MAX_VISIBLE_ROWS.get()), 1, rowsThatFit);
            this.height = BAND_HEADER + this.visibleRows * 18 + (this.hasHorizontalScroll() ? H_SCROLLBAR_SPACE : 0) + BAND_BOTTOM;
        }
        else if(this.data.hasBand())
        {
            // Collapsed: no slots shown, only the divider
            this.visibleColumns = 0;
            this.visibleRows = 0;
            this.height = COLLAPSED_HEIGHT;
            this.gap = sortButtonGap();
        }
        else
        {
            // Empty band: the inventory is laid out exactly like vanilla's
            this.visibleColumns = 0;
            this.visibleRows = 0;
            this.height = 0;
            this.gap = 0;
        }
        this.gridX = 8 + (MAX_VISIBLE_COLUMNS - this.visibleColumns) * 9;
        this.gridY = SPLIT_Y + BAND_HEADER + 1;

        this.menu.offsetInventorySlots(this.getHeight());
        this.scrollColumn = Mth.clamp(this.scrollColumn, 0, this.maxScrollColumn());
        this.scrollRow = Mth.clamp(this.scrollRow, 0, this.maxScrollRow());
        this.applyScroll();
    }

    private static int sortButtonGap()
    {
        return IntegratedUI.isQuarkEnabled() && CompatGuard.test(CompatGuard.QUARK, QuarkClientCompat::showsSortButton) ? SORT_BUTTON_GAP : 0;
    }

    /** The height the band, and the gap below it, add to the inventory window. */
    public int getHeight()
    {
        return this.height + this.gap;
    }

    private boolean hasHorizontalScroll()
    {
        return this.data.columns() > this.visibleColumns;
    }

    private boolean hasVerticalScroll()
    {
        return this.data.rows() > this.visibleRows;
    }

    private int maxScrollColumn()
    {
        return Math.max(0, this.data.columns() - this.visibleColumns);
    }

    private int maxScrollRow()
    {
        return Math.max(0, this.data.rows() - this.visibleRows);
    }

    private void applyScroll()
    {
        this.menu.scrollBackpack(this.gridX, this.gridY, this.visibleColumns, this.visibleRows, this.scrollColumn, this.scrollRow);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Header widgets

    /**
     * Creates the button that opens and collapses the band, just above the header's right end, and, while the band is
     * open, the header's previous/next and rename buttons (the latter unless renaming is turned off). {@code
     * extraButton}, if given, goes at the right end of the header. {@code onRename} handles the rename button; if
     * null, the title is edited in place instead.
     */
    public void init(Consumer<AbstractWidget> adder, @Nullable AbstractWidget extraButton, @Nullable Button.OnPress onRename)
    {
        this.widgets.clear();
        this.extraButton = extraButton;
        this.previous = null;
        this.next = null;
        this.renameBox = null;
        if(!this.data.hasBand())
        {
            return;
        }

        SpriteButton toggle = new SpriteButton(BUTTON_SIZE, TOGGLE_HEIGHT, () -> this.expanded ? ICON_COLLAPSE : ICON_EXPAND, button -> this.toggle());
        ButtonLabels.set(toggle, Component.translatable(this.expanded ? "integrated_ui.gui.hide_band" : "integrated_ui.gui.show_band"));
        this.add(adder, toggle, () -> HEADER_RIGHT - BUTTON_SIZE, () -> SPLIT_Y - TOGGLE_HEIGHT - 1);
        this.syncShown();
        if(!this.expanded)
        {
            this.extraButton = null;
            return;
        }

        if(extraButton != null)
        {
            this.add(adder, extraButton, () -> HEADER_RIGHT - BUTTON_SIZE, () -> HEADER_Y);
        }
        int navRight = HEADER_RIGHT - (extraButton != null ? BUTTON_SIZE + 2 : 0);
        this.next = new SpriteButton(BUTTON_SIZE, BUTTON_SIZE, this.style::next, button -> switchTo(Navigation.NEXT));
        ButtonLabels.set(this.next, NEXT);
        this.add(adder, this.next, () -> navRight - BUTTON_SIZE, () -> HEADER_Y);
        this.previous = new SpriteButton(BUTTON_SIZE, BUTTON_SIZE, this.style::previous, button -> switchTo(Navigation.PREVIOUS));
        ButtonLabels.set(this.previous, PREVIOUS);
        this.add(adder, this.previous, () -> navRight - BUTTON_SIZE * 2, () -> HEADER_Y);
        this.updateNavigation();

        if(!ServerConfig.get(ServerConfig.RENAMING))
        {
            return;
        }
        SpriteButton rename = new SpriteButton(RENAME_SIZE, RENAME_SIZE, this.style::rename, onRename != null ? onRename : button -> this.startRename());
        ButtonLabels.set(rename, RENAME);
        this.add(adder, rename, this::renameButtonX, () -> HEADER_Y + 1);

        if(onRename == null)
        {
            Font font = Minecraft.getInstance().font;
            this.renameBox = new EditBox(font, 0, 0, 0, font.lineHeight, RENAME);
            this.renameBox.setBordered(false);
            this.renameBox.setMaxLength(RenamePayload.MAX_LENGTH);
            this.renameBox.setTextColor(this.style.textColor());
            this.renameBox.setTextShadow(false);
            this.renameBox.setVisible(false);
            this.add(adder, this.renameBox, () -> TITLE_X, () -> SPLIT_Y + 5);
        }
    }

    /** Opens or collapses the band, remembering it in the config, and lays the screen out again for the new height. */
    private void toggle()
    {
        ClientConfig.CONTAINER_BAND_OPEN.set(!this.expanded);
        ClientConfig.CONTAINER_BAND_OPEN.save();
        // Deferred: this runs from the button's click, while the screen goes through the widgets it is about to replace
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> {
            if(mc.screen == this.screen)
            {
                this.screen.init(mc, this.screen.width, this.screen.height);
            }
        });
    }

    /**
     * Tells the server whether the band is open, so nothing is quick-moved into it while it is hidden (see {@link
     * IntegratedInventoryMenu#bandButton}).
     */
    private void syncShown()
    {
        Minecraft mc = Minecraft.getInstance();
        int button = IntegratedInventoryMenu.bandButton(this.expanded);
        if(mc.player != null && mc.gameMode != null && this.menu.clickMenuButton(mc.player, button))
        {
            mc.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
        }
    }

    private void add(Consumer<AbstractWidget> adder, AbstractWidget widget, IntSupplier x, IntSupplier y)
    {
        adder.accept(widget);
        this.widgets.add(new Positioned(widget, x, y));
    }

    /** Moves the header widgets to the inventory window's current position. */
    public void positionWidgets()
    {
        int left = this.screen.getGuiLeft();
        int top = this.screen.getGuiTop();
        for(Positioned positioned : this.widgets)
        {
            positioned.widget.setPosition(left + positioned.x.getAsInt(), top + positioned.y.getAsInt());
        }
        if(this.renameBox != null)
        {
            this.renameBox.setWidth(this.titleWidth());
        }
    }

    public void tick()
    {
        int positionWidth = this.positionWidth();
        if(this.updateNavigation() || positionWidth != this.lastPositionWidth)
        {
            this.lastPositionWidth = positionWidth;
            this.positionWidgets();
        }
    }

    /** Shows the previous/next buttons when there is more than one thing to show. Returns true if that changed. */
    private boolean updateNavigation()
    {
        if(this.previous == null || this.next == null)
        {
            return false;
        }
        boolean shown = this.isNavigationShown();
        boolean changed = this.previous.visible != shown;
        this.previous.visible = shown;
        this.next.visible = shown;
        this.previous.active = this.menu.hasPrevious();
        this.next.active = this.menu.hasNext();
        return changed;
    }

    private boolean isNavigationShown()
    {
        return this.menu.hasPrevious() || this.menu.hasNext();
    }

    /** Width of the previous/next and extra buttons at the right of the header, and the position left of them. */
    private int headerButtonsWidth()
    {
        int width = this.extraButton != null ? BUTTON_SIZE : 0;
        if(this.isNavigationShown())
        {
            width += (width > 0 ? 2 : 0) + BUTTON_SIZE * 2;
            int position = this.positionWidth();
            if(position > 0)
            {
                width += 3 + position;
            }
        }
        return width;
    }

    /** Where what the band shows is among all it can show, such as "2/5", while there is more than one. */
    @Nullable
    private String positionText()
    {
        int count = this.menu.getTargetCount();
        return count > 1 && this.menu.getTargetPosition() > 0 ? this.menu.getTargetPosition() + "/" + count : null;
    }

    private int positionWidth()
    {
        String text = this.positionText();
        return text != null ? Minecraft.getInstance().font.width(text) : 0;
    }

    /** Right edge of the position text: just left of the previous button. */
    private int positionRight()
    {
        return HEADER_RIGHT - (this.extraButton != null ? BUTTON_SIZE + 2 : 0) - BUTTON_SIZE * 2 - 3;
    }

    private int renameButtonX()
    {
        return HEADER_RIGHT - this.headerButtonsWidth() - 2 - RENAME_SIZE;
    }

    private int titleWidth()
    {
        return this.renameButtonX() - 4 - TITLE_X;
    }

    private static void switchTo(Navigation navigation)
    {
        PacketDistributor.sendToServer(new OpenInventoryPayload(navigation));
    }

    /** Shows the previous or next thing (from the keys), if there is one. Returns true if it switched. */
    public boolean navigate(Navigation navigation)
    {
        boolean possible = navigation == Navigation.PREVIOUS ? this.menu.hasPrevious() : this.menu.hasNext();
        if(!this.expanded || this.isRenaming() || !possible)
        {
            return false;
        }
        switchTo(navigation);
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Renaming in place

    public boolean isRenaming()
    {
        return this.renameBox != null && this.renameBox.isVisible();
    }

    private void startRename()
    {
        if(this.renameBox == null)
        {
            return;
        }
        this.renameBox.setVisible(true);
        this.renameBox.setValue(this.screen.getTitle().getString());
        this.renameBox.moveCursorToEnd(false);
        // Deferred: this runs from the rename button's click, and the screen focuses the clicked button afterwards
        EditBox box = this.renameBox;
        Minecraft.getInstance().tell(() -> {
            if(box.isVisible())
            {
                this.screen.setFocused(box);
            }
        });
    }

    private void stopRename(boolean confirm)
    {
        if(this.renameBox == null || !this.renameBox.isVisible())
        {
            return;
        }
        if(confirm && !this.renameBox.getValue().equals(this.screen.getTitle().getString()))
        {
            PacketDistributor.sendToServer(new RenamePayload(this.renameBox.getValue()));
        }
        this.renameBox.setVisible(false);
        this.renameBox.setFocused(false);
        this.screen.setFocused(null);
    }

    /** While renaming, takes every key (so typing doesn't close the screen or swap items). */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(!this.isRenaming())
        {
            return false;
        }
        switch(keyCode)
        {
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> this.stopRename(true);
            case InputConstants.KEY_ESCAPE -> this.stopRename(false);
            default -> this.renameBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rendering

    /** Draws what sits behind the inventory window's edge: the vertical scrollbar's tab. */
    public void renderBehind(GuiGraphics graphics)
    {
        if(this.expanded && this.hasVerticalScroll())
        {
            int left = this.screen.getGuiLeft();
            int top = this.screen.getGuiTop();
            Rect2i tab = this.verticalScrollTab();
            this.style.drawBackground(graphics, left + tab.getX(), top + tab.getY(), tab.getWidth(), tab.getHeight());
            EquipmentPanel.renderScrollbar(graphics, this.style.scrollbar(), left + tab.getX() + 4, top + this.gridY - 1, SCROLLBAR_SIZE, this.visibleRows * 18, false,
                this.scrollRow, this.maxScrollRow(), this.visibleRows, this.data.rows());
        }
    }

    /** Draws the vanilla inventory, split open at {@link #SPLIT_Y}, with the band in the gap. */
    public void render(GuiGraphics graphics)
    {
        int left = this.screen.getGuiLeft();
        int top = this.screen.getGuiTop();
        // The gap is filled by stretching a plain row of the texture
        graphics.blit(INVENTORY_TEXTURE, left, top, 0, 0, VANILLA_WIDTH, SPLIT_Y);
        graphics.blit(INVENTORY_TEXTURE, left, top + SPLIT_Y, VANILLA_WIDTH, this.getHeight(), 0, SPLIT_Y + 1, VANILLA_WIDTH, 1, 256, 256);
        graphics.blit(INVENTORY_TEXTURE, left, top + SPLIT_Y + this.getHeight(), 0, SPLIT_Y, VANILLA_WIDTH, VANILLA_HEIGHT - SPLIT_Y);
        if(!this.expanded)
        {
            if(this.data.hasBand())
            {
                // An engraved line across the inventory, where the band would be
                graphics.fill(left + 7, top + SPLIT_Y + 1, left + VANILLA_WIDTH - 7, top + SPLIT_Y + 2, 0xFF8B8B8B);
                graphics.fill(left + 7, top + SPLIT_Y + 2, left + VANILLA_WIDTH - 7, top + SPLIT_Y + 3, 0xFFFFFFFF);
            }
            return;
        }

        this.style.drawBackground(graphics, left + BAND_X, top + SPLIT_Y, BAND_WIDTH, this.height - 1);
        int slotsWidth = this.visibleColumns * 18;
        int slotsHeight = this.visibleRows * 18;
        for(int row = 0; row < this.visibleRows; row++)
        {
            int columns = this.usableColumns(row);
            if(columns > 0)
            {
                this.style.drawSlots(graphics, left + this.gridX - 1, top + this.gridY - 1 + row * 18, columns * 18, 18);
            }
        }
        int checkersWidth = this.gridX - 1 - (BAND_X + 4) - 2;
        if(checkersWidth > 0 && this.style.checkers() != null)
        {
            blitTinted(graphics, this.style.checkers(), this.style.tint(), left + BAND_X + 4, top + this.gridY - 1, checkersWidth, slotsHeight);
            blitTinted(graphics, this.style.checkers(), this.style.tint(), left + this.gridX - 1 + slotsWidth + 2, top + this.gridY - 1, checkersWidth, slotsHeight);
        }
        if(this.hasHorizontalScroll())
        {
            int trackY = top + this.gridY - 1 + slotsHeight + 2;
            EquipmentPanel.renderScrollbar(graphics, this.style.scrollbar(), left + this.gridX - 1, trackY, slotsWidth, SCROLLBAR_SIZE, true,
                this.scrollColumn, this.maxScrollColumn(), this.visibleColumns, this.data.columns());
        }
    }

    /** How many slots there are in the given visible row, from the left: a container item's grid can end in a partial row. */
    private int usableColumns(int row)
    {
        int first = (this.scrollRow + row) * this.data.columns() + this.scrollColumn;
        return Mth.clamp(this.menu.getUsableBandSize() - first, 0, this.visibleColumns);
    }

    private static void blitTinted(GuiGraphics graphics, ResourceLocation sprite, int tint, int x, int y, int width, int height)
    {
        if(tint == 0xFFFFFF)
        {
            graphics.blitSprite(sprite, x, y, width, height);
            return;
        }
        graphics.setColor((tint >> 16 & 0xFF) / 255.0F, (tint >> 8 & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F, 1.0F);
        graphics.blitSprite(sprite, x, y, width, height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Draws the band's title, and a shade over the slots while they are locked. Called from the screen's
     * renderLabels, so coordinates are relative to the inventory window and this is drawn over the items.
     */
    public void renderLabels(GuiGraphics graphics, Font font)
    {
        if(!this.expanded)
        {
            return;
        }
        if(!this.isRenaming())
        {
            graphics.drawString(font, this.trimmedTitle(font, this.titleWidth()), TITLE_X, SPLIT_Y + 5, this.style.textColor(), false);
        }
        String position = this.positionText();
        if(position != null && this.isNavigationShown())
        {
            graphics.drawString(font, position, this.positionRight() - font.width(position), SPLIT_Y + 5, this.style.textColor(), false);
        }
        if(this.menu instanceof ShulkerInventoryMenu shulkerMenu && shulkerMenu.isLocked())
        {
            for(int row = 0; row < this.visibleRows; row++)
            {
                int y = this.gridY - 1 + row * 18;
                graphics.fill(RenderType.guiOverlay(), this.gridX - 1, y, this.gridX - 1 + this.usableColumns(row) * 18, y + 18, 0x80000000);
            }
        }
    }

    private FormattedCharSequence trimmedTitle(Font font, int maxWidth)
    {
        Component title = this.screen.getTitle();
        if(font.width(title) <= maxWidth)
        {
            return title.getVisualOrderText();
        }
        FormattedText trimmed = font.substrByWidth(title, maxWidth - font.width("..."));
        return Language.getInstance().getVisualOrder(FormattedText.composite(trimmed, FormattedText.of("...")));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Input

    /**
     * Starts dragging a scrollbar, or ends renaming when clicking elsewhere (and nothing more: renaming reopens the
     * menu, so a slot clicked meanwhile would be clicked in the menu being replaced). Returns true if the click was used.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(this.isRenaming() && !this.renameBox.isMouseOver(mouseX, mouseY))
        {
            this.stopRename(true);
            return true;
        }
        return button == 0 && this.startScrollDrag(mouseX, mouseY);
    }

    public boolean mouseDragged(double mouseX, double mouseY)
    {
        if(this.scrollDrag == ScrollDrag.NONE)
        {
            return false;
        }
        this.updateScrollDrag(mouseX, mouseY);
        return true;
    }

    /** Ends a scrollbar drag. Returns true if there was one. */
    public boolean mouseReleased()
    {
        boolean wasDragging = this.scrollDrag != ScrollDrag.NONE;
        this.scrollDrag = ScrollDrag.NONE;
        return wasDragging;
    }

    /** {@code step} is -1 to scroll up and 1 to scroll down. Returns true if the grid scrolled. */
    public boolean mouseScrolled(double mouseX, double mouseY, int step, boolean horizontal)
    {
        if(step == 0 || !this.expanded || !this.isOver(mouseX, mouseY))
        {
            return false;
        }
        if(this.hasVerticalScroll() && !horizontal)
        {
            this.setScroll(this.scrollColumn, this.scrollRow + step);
            return true;
        }
        if(this.hasHorizontalScroll())
        {
            this.setScroll(this.scrollColumn + step, this.scrollRow);
            return true;
        }
        return false;
    }

    private boolean isOver(double mouseX, double mouseY)
    {
        double x = mouseX - this.screen.getGuiLeft();
        double y = mouseY - this.screen.getGuiTop();
        boolean inBand = x >= BAND_X && x < BAND_X + BAND_WIDTH && y >= SPLIT_Y && y < SPLIT_Y + this.height;
        return inBand || this.isOverScrollTab(x, y);
    }

    /** How far the band sticks out past the window's right edge (and the 2 pixels vanilla leaves there): its scroll tab. */
    public int getRightOverhang()
    {
        if(!this.expanded || !this.hasVerticalScroll())
        {
            return 0;
        }
        Rect2i tab = this.verticalScrollTab();
        return Math.max(0, tab.getX() + tab.getWidth() + 1 - (VANILLA_WIDTH + 2));
    }

    /** Whether the mouse is over the part of the band drawn outside the inventory window. */
    public boolean containsOutside(double mouseX, double mouseY)
    {
        return this.isOverScrollTab(mouseX - this.screen.getGuiLeft(), mouseY - this.screen.getGuiTop());
    }

    private boolean isOverScrollTab(double x, double y)
    {
        return this.expanded && this.hasVerticalScroll() && contains(this.verticalScrollTab(), x, y);
    }

    private static boolean contains(Rect2i rect, double x, double y)
    {
        return x >= rect.getX() && x < rect.getX() + rect.getWidth() && y >= rect.getY() && y < rect.getY() + rect.getHeight();
    }

    /** The tab holding the vertical scrollbar, attached to the right edge of the inventory. */
    private Rect2i verticalScrollTab()
    {
        return new Rect2i(VANILLA_WIDTH - 4, this.gridY - 5, V_SCROLLBAR_WIDTH + 1, this.visibleRows * 18 + 8);
    }

    private boolean startScrollDrag(double mouseX, double mouseY)
    {
        if(!this.expanded)
        {
            return false;
        }
        double x = mouseX - this.screen.getGuiLeft();
        double y = mouseY - this.screen.getGuiTop();
        if(this.hasVerticalScroll())
        {
            Rect2i tab = this.verticalScrollTab();
            if(x >= tab.getX() + 2 && x < tab.getX() + tab.getWidth() - 2 && y >= this.gridY - 1 && y < this.gridY - 1 + this.visibleRows * 18)
            {
                this.scrollDrag = ScrollDrag.VERTICAL;
                this.updateScrollDrag(mouseX, mouseY);
                return true;
            }
        }
        if(this.hasHorizontalScroll())
        {
            int trackY = this.gridY - 1 + this.visibleRows * 18 + 2;
            if(x >= this.gridX - 1 && x < this.gridX - 1 + this.visibleColumns * 18 && y >= trackY - 1 && y < trackY + SCROLLBAR_SIZE + 1)
            {
                this.scrollDrag = ScrollDrag.HORIZONTAL;
                this.updateScrollDrag(mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private void updateScrollDrag(double mouseX, double mouseY)
    {
        double x = mouseX - this.screen.getGuiLeft();
        double y = mouseY - this.screen.getGuiTop();
        if(this.scrollDrag == ScrollDrag.VERTICAL)
        {
            double progress = (y - (this.gridY - 1)) / (this.visibleRows * 18.0);
            this.setScroll(this.scrollColumn, (int) Math.round(progress * this.maxScrollRow()));
        }
        else if(this.scrollDrag == ScrollDrag.HORIZONTAL)
        {
            double progress = (x - (this.gridX - 1)) / (this.visibleColumns * 18.0);
            this.setScroll((int) Math.round(progress * this.maxScrollColumn()), this.scrollRow);
        }
    }

    private void setScroll(int column, int row)
    {
        column = Mth.clamp(column, 0, this.maxScrollColumn());
        row = Mth.clamp(row, 0, this.maxScrollRow());
        if(column != this.scrollColumn || row != this.scrollRow)
        {
            this.scrollColumn = column;
            this.scrollRow = row;
            this.applyScroll();
            this.onScroll.run();
        }
    }

    /** Adds the screen areas the band draws on outside the inventory window, for JEI exclusion. */
    public void addExtraAreas(List<Rect2i> areas)
    {
        if(this.expanded && this.hasVerticalScroll())
        {
            Rect2i tab = this.verticalScrollTab();
            areas.add(new Rect2i(this.screen.getGuiLeft() + tab.getX(), this.screen.getGuiTop() + tab.getY(), tab.getWidth(), tab.getHeight()));
        }
    }

    private enum ScrollDrag
    {
        NONE, VERTICAL, HORIZONTAL
    }

    private record Positioned(AbstractWidget widget, IntSupplier x, IntSupplier y) {}

    /** Draws a band's background and slots some other way than from (tinted) GUI sprites. */
    public interface Painter
    {
        void background(GuiGraphics graphics, int x, int y, int width, int height);

        /** Draws a row (or block) of slots, {@code width} and {@code height} being multiples of 18. */
        void slots(GuiGraphics graphics, int x, int y, int width, int height);
    }

    /**
     * How the band is drawn. The tints (RGB) multiply the background and slot sprites, unless a {@code painter} draws
     * those instead; text is drawn in {@code textColor}, and the scrollbars in {@code scrollbar}.
     */
    public record Style(@Nullable ResourceLocation background, @Nullable ResourceLocation slot, @Nullable ResourceLocation checkers,
                        ResourceLocation previous, ResourceLocation next, ResourceLocation rename,
                        int tint, int slotTint, int textColor, EquipmentPanel.ScrollbarColors scrollbar, @Nullable Painter painter)
    {
        /** Drawn from GUI sprites. */
        public Style(ResourceLocation background, ResourceLocation slot, @Nullable ResourceLocation checkers,
                     ResourceLocation previous, ResourceLocation next, ResourceLocation rename,
                     int tint, int slotTint, int textColor, EquipmentPanel.ScrollbarColors scrollbar)
        {
            this(background, slot, checkers, previous, next, rename, tint, slotTint, textColor, scrollbar, null);
        }

        void drawBackground(GuiGraphics graphics, int x, int y, int width, int height)
        {
            if(this.painter != null)
            {
                this.painter.background(graphics, x, y, width, height);
            }
            else if(this.background != null)
            {
                blitTinted(graphics, this.background, this.tint, x, y, width, height);
            }
        }

        void drawSlots(GuiGraphics graphics, int x, int y, int width, int height)
        {
            if(this.painter != null)
            {
                this.painter.slots(graphics, x, y, width, height);
            }
            else if(this.slot != null)
            {
                blitTinted(graphics, this.slot, this.slotTint, x, y, width, height);
            }
        }

        /** The grey of the band background sprite, which the tint multiplies. */
        private static final int BACKGROUND_GREY = 0xC6;

        /** Drawn in a container item's colour (RGB), such as a shulker box's (see ContainerItems#getTint). */
        public static Style containerItem(int color)
        {
            return tinted(color);
        }

        /** Drawn like Quark's own backpack screen, from Quark's texture: leather slots inside a stitched frame. */
        public static final Style QUARK_BACKPACK = new Style(null, null, null,
            IntegratedUI.rl("arrow_left"), IntegratedUI.rl("arrow_right"), IntegratedUI.rl("rename"),
            0xFFFFFF, 0xFFFFFF, 0xF3E4CD, EquipmentPanel.ScrollbarColors.LEATHER, new QuarkLeatherPainter());

        private static Style tinted(int color)
        {
            int tint = lighten(color, 0.45F);
            int slotTint = lighten(color, 0.6F);
            float luminance = (0.299F * (tint >> 16 & 0xFF) + 0.587F * (tint >> 8 & 0xFF) + 0.114F * (tint & 0xFF)) / 255.0F;
            int textColor = luminance * BACKGROUND_GREY / 255.0F > 0.45F ? 0x404040 : 0xF0F0F0;
            return new Style(IntegratedUI.rl("shulker_box/band"), IntegratedUI.rl("shulker_box/slot"), null,
                IntegratedUI.rl("arrow_left"), IntegratedUI.rl("arrow_right"), IntegratedUI.rl("rename"),
                tint, slotTint, textColor, EquipmentPanel.ScrollbarColors.GREY.tinted(tint));
        }

        /** Mixes {@code color} with white by {@code amount}, so dark dyes still leave the band readable. */
        private static int lighten(int color, float amount)
        {
            int r = (int) Mth.lerp(amount, color >> 16 & 0xFF, 255);
            int g = (int) Mth.lerp(amount, color >> 8 & 0xFF, 255);
            int b = (int) Mth.lerp(amount, color & 0xFF, 255);
            return r << 16 | g << 8 | b;
        }
    }
}
