package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.compat.Equipment;
import com.lincstoel.integratedui.compat.EquipmentBackend;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.menu.BackpackInventoryMenu;
import com.lincstoel.integratedui.menu.OpenData;
import com.lincstoel.integratedui.network.OpenBackpackInventoryPayload;
import com.lincstoel.integratedui.network.RenameBackpackPayload;
import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.client.gui.MouseRestorer;
import com.mrcrayfish.backpacked.client.gui.screen.inventory.UnlockableContainerScreen;
import com.mrcrayfish.backpacked.client.gui.screen.widget.popup.TextInputMenu;
import com.mrcrayfish.backpacked.common.Navigate;
import com.mrcrayfish.backpacked.common.UnlockableSlotMode;
import com.mrcrayfish.backpacked.inventory.container.slot.UnlockableSlot;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageOpenBackpack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.locale.Language;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * The survival inventory with the backpack grid inserted between the crafting area and the main inventory, as
 * Quark does for its backpack. Extends Backpacked's {@link UnlockableContainerScreen}, which provides the lock
 * overlay, hold-to-unlock, drag selection, cost tooltips and unlock particles for the backpack slots.
 */
public class BackpackInventoryScreen extends UnlockableContainerScreen<BackpackInventoryMenu> implements RecipeUpdateListener
{
    private static final ResourceLocation INVENTORY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final ResourceLocation BAND_BACKGROUND = backpacked("backpack/label");
    private static final ResourceLocation BACKPACK_SLOT = backpacked("backpack/slot");
    private static final ResourceLocation CHECKERS = backpacked("backpack/checkers");
    private static final ResourceLocation PANEL_BACKGROUND = backpacked("backpack/inventory");
    private static final ResourceLocation PANEL_SLOT = backpacked("backpack/inventory_slot");
    private static final ResourceLocation ICON_PREVIOUS = backpacked("backpack/previous");
    private static final ResourceLocation ICON_NEXT = backpacked("backpack/next");
    private static final ResourceLocation ICON_RENAME = backpacked("backpack/rename");
    private static final ResourceLocation UNUSED_SLOTS_SHOWN = ResourceLocation.fromNamespaceAndPath("accessories", "widget/unused_slots_shown");
    private static final ResourceLocation UNUSED_SLOTS_HIDDEN = ResourceLocation.fromNamespaceAndPath("accessories", "widget/unused_slots_hidden");
    private static final ResourceLocation ACCESSORIES_ICON = ResourceLocation.fromNamespaceAndPath("accessories", "textures/gui/accessories_open_icon.png");
    private static final ResourceLocation ACCESSORIES_ICON_HOVERED = ResourceLocation.fromNamespaceAndPath("accessories", "textures/gui/accessories_open_icon_hovered.png");

    private static final Component CRAFTING_TITLE = Component.translatable("container.crafting");
    private static final Component OPEN_BACKPACK = Component.translatable("integrated_ui.gui.open_backpack");
    private static final Component PREVIOUS_BACKPACK = Component.translatable("integrated_ui.gui.previous_backpack");
    private static final Component NEXT_BACKPACK = Component.translatable("integrated_ui.gui.next_backpack");
    private static final Component RENAME = Component.translatable("backpacked.gui.rename");

    // Vanilla inventory texture geometry
    private static final int VANILLA_WIDTH = 176;
    private static final int VANILLA_HEIGHT = 166;
    /** The backpack band is inserted at this y, between the armor/crafting area and the main inventory. */
    private static final int SPLIT_Y = 80;

    // Backpack band geometry
    private static final int BAND_X = 4;
    private static final int BAND_WIDTH = VANILLA_WIDTH - 8;
    private static final int BAND_HEADER = 15;
    private static final int BAND_BOTTOM = 5;
    private static final int SCROLLBAR_SIZE = 4;
    private static final int H_SCROLLBAR_SPACE = 6;
    private static final int V_SCROLLBAR_WIDTH = 11;
    private static final int MAX_VISIBLE_COLUMNS = 9;

    // Equipment panel geometry
    private static final int PANEL_PADDING = 7;
    private static final int PANEL_TOP = 4;
    private static final int PANEL_MAX_COLUMNS = 3;

    /** Remembered for the session, so the panel stays the way the player left it. */
    @Nullable
    private static Boolean equipmentPanelOpen;

    private final RecipeBookComponent recipeBookComponent = new RecipeBookComponent();
    private final OpenData data;
    private final EquipmentBackend equipment = Equipment.get();
    private final List<Positioned> positionedWidgets = new ArrayList<>();
    private boolean widthTooNarrow;
    private boolean recipeButtonClicked;
    private int lockModeTimer;

    // Backpack grid layout and scroll state
    private int visibleColumns;
    private int visibleRows;
    private int bandHeight;
    private int gridX;
    private int gridY;
    private int scrollColumn;
    private int scrollRow;
    private ScrollDrag scrollDrag = ScrollDrag.NONE;

    // Equipment panel layout and scroll state
    private int panelColumns;
    private int panelRows;
    private int panelWidth;
    private int panelX;
    private int panelScroll;
    private boolean panelScrollDragging;
    private int panelRefreshTimer;
    /** The equipment slots currently shown in the panel (e.g. excluding unused Accessories slots). */
    private List<Slot> panelSlots = List.of();
    @Nullable
    private AbstractWidget unusedSlotsButton;

    public BackpackInventoryScreen(BackpackInventoryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.data = menu.getData();
        this.imageWidth = VANILLA_WIDTH;
        if(equipmentPanelOpen == null)
        {
            equipmentPanelOpen = ClientConfig.EQUIPMENT_PANEL_OPEN_BY_DEFAULT.get();
        }
    }

    private static ResourceLocation backpacked(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("backpacked", path);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Layout

    private void computeLayout()
    {
        this.visibleColumns = Math.min(this.data.columns(), MAX_VISIBLE_COLUMNS);
        int rowsThatFit = Math.max(1, (this.height - VANILLA_HEIGHT - BAND_HEADER - BAND_BOTTOM - H_SCROLLBAR_SPACE - 4) / 18);
        this.visibleRows = Mth.clamp(Math.min(this.data.rows(), ClientConfig.MAX_VISIBLE_ROWS.get()), 1, rowsThatFit);
        this.bandHeight = BAND_HEADER + this.visibleRows * 18 + (this.hasHorizontalScroll() ? H_SCROLLBAR_SPACE : 0) + BAND_BOTTOM;
        this.imageHeight = VANILLA_HEIGHT + this.bandHeight;
        this.gridX = 8 + (MAX_VISIBLE_COLUMNS - this.visibleColumns) * 9;
        this.gridY = SPLIT_Y + BAND_HEADER + 1;
        this.inventoryLabelY = this.imageHeight - 94;

        this.menu.offsetInventorySlots(this.bandHeight);
        this.scrollColumn = Mth.clamp(this.scrollColumn, 0, this.maxScrollColumn());
        this.scrollRow = Mth.clamp(this.scrollRow, 0, this.maxScrollRow());
        this.applyBackpackScroll();
        this.refreshPanelSlots();
        this.layoutPanel();
    }

    /** Re-evaluates which equipment slots are shown. Returns true if that changed. */
    private boolean refreshPanelSlots()
    {
        Predicate<Slot> filter = this.equipment.createVisibilityFilter(this.minecraft.player);
        List<Slot> visible = new ArrayList<>();
        for(int i = this.menu.getEquipmentStart(); i < this.menu.getEquipmentEnd(); i++)
        {
            Slot slot = this.menu.slots.get(i);
            if(filter.test(slot))
            {
                visible.add(slot);
            }
        }
        if(visible.equals(this.panelSlots))
        {
            return false;
        }
        this.panelSlots = visible;
        return true;
    }

    private void layoutPanel()
    {
        int count = this.panelSlots.size();
        if(count > 0)
        {
            int rowsFit = Math.max(1, (this.imageHeight - PANEL_TOP - PANEL_PADDING * 2) / 18);
            this.panelColumns = Mth.clamp(Mth.positiveCeilDiv(count, rowsFit), 1, PANEL_MAX_COLUMNS);
            this.panelRows = Math.min(rowsFit, Mth.positiveCeilDiv(count, this.panelColumns));
            this.panelWidth = PANEL_PADDING * 2 + this.panelColumns * 18 + (this.hasPanelScroll() ? SCROLLBAR_SIZE + 3 : 0);
            this.panelX = -this.panelWidth + 3;
        }
        else
        {
            this.panelColumns = 1;
            this.panelRows = 0;
            this.panelWidth = 0;
            this.panelX = 0;
        }
        this.panelScroll = Mth.clamp(this.panelScroll, 0, this.maxPanelScroll());
        this.applyPanelLayout();
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

    private void applyBackpackScroll()
    {
        this.menu.scrollBackpack(this.gridX, this.gridY, this.visibleColumns, this.visibleRows, this.scrollColumn, this.scrollRow);
    }

    private int equipmentCount()
    {
        return this.menu.getEquipmentEnd() - this.menu.getEquipmentStart();
    }

    /** Whether the panel is open (its buttons exist whenever the player has any equipment slots). */
    private boolean isPanelShown()
    {
        return this.equipmentCount() > 0 && Boolean.TRUE.equals(equipmentPanelOpen);
    }

    /** Whether the panel is open and has slots to draw. */
    private boolean isPanelDrawn()
    {
        return this.isPanelShown() && !this.panelSlots.isEmpty();
    }

    /** Left edge of whatever is drawn left of the inventory (relative coords). */
    private int panelLeft()
    {
        return this.isPanelDrawn() ? this.panelX : 0;
    }

    private boolean hasPanelScroll()
    {
        return Mth.positiveCeilDiv(this.panelSlots.size(), this.panelColumns) > this.panelRows;
    }

    private int maxPanelScroll()
    {
        if(this.panelSlots.isEmpty())
        {
            return 0;
        }
        return Math.max(0, Mth.positiveCeilDiv(this.panelSlots.size(), this.panelColumns) - this.panelRows);
    }

    private int panelHeight()
    {
        return PANEL_PADDING * 2 + this.panelRows * 18;
    }

    private void applyPanelLayout()
    {
        boolean shown = this.isPanelShown();
        for(int i = this.menu.getEquipmentStart(); i < this.menu.getEquipmentEnd(); i++)
        {
            Slot slot = this.menu.slots.get(i);
            slot.x = BackpackInventoryMenu.HIDDEN;
            slot.y = BackpackInventoryMenu.HIDDEN;
        }
        for(int i = 0; i < this.panelSlots.size(); i++)
        {
            Slot slot = this.panelSlots.get(i);
            int row = i / Math.max(1, this.panelColumns) - this.panelScroll;
            int column = i % Math.max(1, this.panelColumns);
            if(shown && row >= 0 && row < this.panelRows)
            {
                slot.x = this.panelX + PANEL_PADDING + 1 + column * 18;
                slot.y = PANEL_TOP + PANEL_PADDING + 1 + row * 18;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Init and widgets

    @Override
    protected void init()
    {
        // Keeps the cursor where it was when this screen replaces another (e.g. switching backpacks)
        MouseRestorer.loadCapturedPosition();
        this.computeLayout();
        super.init();
        this.widthTooNarrow = this.width < 379;
        this.recipeBookComponent.init(this.width, this.height, this.minecraft, this.widthTooNarrow, this.menu);
        this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
        this.positionedWidgets.clear();

        this.addPositioned(new ImageButton(0, 0, 20, 18, RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
            this.recipeBookComponent.toggleVisibility();
            this.leftPos = this.recipeBookComponent.updateScreenPosition(this.width, this.imageWidth);
            this.positionWidgets();
            this.recipeButtonClicked = true;
        }), 104, 61);
        this.addWidget(this.recipeBookComponent);

        // Band header: previous/next backpack and the button to open the full Backpacked screen
        int headerY = SPLIT_Y + 2;
        int right = BAND_X + BAND_WIDTH - 4;
        Button open = new BackpackButton(this::getBackpackStack, button -> this.openFullBackpackScreen());
        open.setTooltip(Tooltip.create(OPEN_BACKPACK));
        this.addPositioned(open, right - 12, headerY);
        if(this.data.hasPrevious() || this.data.hasNext())
        {
            SpriteButton next = new SpriteButton(12, 12, () -> ICON_NEXT, button -> this.switchBackpack(Navigate.NEXT));
            next.setTooltip(Tooltip.create(NEXT_BACKPACK));
            next.active = this.data.hasNext();
            this.addPositioned(next, right - 12 - 2 - 12, headerY);
            SpriteButton previous = new SpriteButton(12, 12, () -> ICON_PREVIOUS, button -> this.switchBackpack(Navigate.PREVIOUS));
            previous.setTooltip(Tooltip.create(PREVIOUS_BACKPACK));
            previous.active = this.data.hasPrevious();
            this.addPositioned(previous, right - 12 - 2 - 12 - 12, headerY);
        }
        SpriteButton rename = new SpriteButton(10, 10, () -> ICON_RENAME, this::openRenamePopup);
        rename.setTooltip(Tooltip.create(RENAME));
        this.addPositioned(rename, right - this.headerButtonsWidth() - 2 - 10, headerY + 1);

        // Equipment panel: show/hide toggle, then show/hide unused slots, then the Accessories screen button
        this.unusedSlotsButton = null;
        if(this.equipmentCount() > 0)
        {
            SpriteButton toggle = new SpriteButton(12, 12, () -> this.isPanelShown() ? ICON_NEXT : ICON_PREVIOUS, button -> {
                equipmentPanelOpen = !this.isPanelShown();
                this.layoutPanel();
                this.positionWidgets();
                button.setTooltip(this.createPanelToggleTooltip());
            });
            toggle.setTooltip(this.createPanelToggleTooltip());
            this.addPositioned(toggle, () -> this.panelLeft() - 13, () -> PANEL_TOP + 2);

            if(this.equipment.hasUnusedSlots())
            {
                SpriteButton unused = new SpriteButton(12, 12, () -> this.equipment.areUnusedSlotsShown(this.minecraft.player) ? UNUSED_SLOTS_SHOWN : UNUSED_SLOTS_HIDDEN, button -> {
                    this.equipment.setUnusedSlotsShown(this.minecraft.player, !this.equipment.areUnusedSlotsShown(this.minecraft.player));
                    this.refreshPanelLayout();
                    button.setTooltip(this.createUnusedSlotsTooltip());
                });
                unused.setTooltip(this.createUnusedSlotsTooltip());
                this.addPositioned(unused, () -> this.panelLeft() - 13, () -> PANEL_TOP + 2 + 14);
                this.unusedSlotsButton = unused;
            }
        }
        if(this.equipment.hasFullScreen())
        {
            TextureButton accessories = new TextureButton(8, 8, ACCESSORIES_ICON, ACCESSORIES_ICON_HOVERED, button -> this.equipment.openFullScreen());
            accessories.setTooltip(Tooltip.create(Component.translatable("integrated_ui.gui.open_accessories")));
            this.addPositioned(accessories, () -> this.panelLeft() - 11, this::accessoriesButtonY);
        }

        this.positionWidgets();
        this.updateLockedSlotVisibility();
    }

    /** Below the panel toggle, and below the unused-slots toggle while that is shown. */
    private int accessoriesButtonY()
    {
        int y = PANEL_TOP + 2;
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

    private Tooltip createUnusedSlotsTooltip()
    {
        boolean shown = this.equipment.areUnusedSlotsShown(this.minecraft.player);
        return Tooltip.create(Component.translatable(shown ? "integrated_ui.gui.hide_unused_slots" : "integrated_ui.gui.show_unused_slots"));
    }

    /** Width of the previous/next/open buttons at the right of the band header. */
    private int headerButtonsWidth()
    {
        return 12 + (this.data.hasPrevious() || this.data.hasNext() ? 2 + 12 + 12 : 0);
    }

    private void openRenamePopup(Button button)
    {
        new TextInputMenu(this, this.title.getString(), RenameBackpackPayload.MAX_LENGTH,
            name -> PacketDistributor.sendToServer(new RenameBackpackPayload(name))).show(button);
    }

    private void refreshPanelLayout()
    {
        this.refreshPanelSlots();
        this.layoutPanel();
        this.positionWidgets();
    }

    private Tooltip createPanelToggleTooltip()
    {
        String key = this.isPanelShown() ? "integrated_ui.gui.hide_panel" : "integrated_ui.gui.show_panel";
        return Tooltip.create(Component.translatable(key, this.equipment.getPanelName()));
    }

    private void addPositioned(AbstractWidget widget, int x, int y)
    {
        this.addPositioned(widget, () -> x, () -> y);
    }

    private void addPositioned(AbstractWidget widget, IntSupplier x, IntSupplier y)
    {
        this.addRenderableWidget(widget);
        this.positionedWidgets.add(new Positioned(widget, x, y));
    }

    private void positionWidgets()
    {
        if(this.unusedSlotsButton != null)
        {
            this.unusedSlotsButton.visible = this.isPanelShown();
        }
        for(Positioned positioned : this.positionedWidgets)
        {
            positioned.widget.setPosition(this.leftPos + positioned.x.getAsInt(), this.topPos + positioned.y.getAsInt());
        }
    }

    private ItemStack getBackpackStack()
    {
        return this.data.backpack();
    }

    private void openFullBackpackScreen()
    {
        Network.PLAY.sendToServer(new MessageOpenBackpack(this.data.backpackIndex()));
    }

    private void switchBackpack(Navigate navigate)
    {
        PacketDistributor.sendToServer(new OpenBackpackInventoryPayload(navigate));
    }

    /** Matches Backpacked's "unlockable slot mode" client option (show, hide, or show only when affordable). */
    private void updateLockedSlotVisibility()
    {
        if(Config.BACKPACK.inventory.slots.unlockAllSlots.get())
        {
            this.setHideLockedSlots(false);
            return;
        }
        UnlockableSlotMode mode = Config.CLIENT.unlockableSlotMode.get();
        switch(mode)
        {
            case ENABLED -> this.setHideLockedSlots(false);
            case DISABLED -> this.setHideLockedSlots(true);
            case PURCHASABLE -> this.setHideLockedSlots(this.minecraft == null || !this.menu.getSlotController().canAffordNextSlot(this.minecraft.player, Math.max(1, this.selectedSlots.size() + 1)));
        }
    }

    @Override
    protected void addSlotToSelected(UnlockableSlot slot)
    {
        super.addSlotToSelected(slot);
        this.updateLockedSlotVisibility();
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        this.recipeBookComponent.tick();
        if(this.lockModeTimer-- <= 0)
        {
            this.updateLockedSlotVisibility();
            this.lockModeTimer = 5;
        }
        // Which Accessories slots count as used depends on the items carried, so re-check periodically
        if(this.panelRefreshTimer-- <= 0)
        {
            this.panelRefreshTimer = 10;
            if(this.refreshPanelSlots())
            {
                this.layoutPanel();
                this.positionWidgets();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        if(this.recipeBookComponent.isVisible() && this.widthTooNarrow)
        {
            this.renderBackground(graphics, mouseX, mouseY, partialTick);
            this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
        }
        else
        {
            super.render(graphics, mouseX, mouseY, partialTick);
            this.recipeBookComponent.render(graphics, mouseX, mouseY, partialTick);
            this.recipeBookComponent.renderGhostRecipe(graphics, this.leftPos, this.topPos, false, partialTick);
        }
        this.recipeBookComponent.renderTooltip(graphics, this.leftPos, this.topPos, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY)
    {
        if(this.menu.getCarried().isEmpty() && this.hoveredSlot != null && !this.hoveredSlot.hasItem() && this.menu.isEquipmentSlot(this.hoveredSlot.index))
        {
            Component name = this.equipment.getSlotName(this.hoveredSlot);
            if(name != null)
            {
                graphics.renderTooltip(this.font, name, mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        int left = this.leftPos;
        int top = this.topPos;

        if(this.isPanelDrawn())
        {
            this.renderEquipmentPanel(graphics, left, top);
        }
        if(this.hasVerticalScroll())
        {
            this.renderVerticalScrollbar(graphics, left, top);
        }

        // Vanilla inventory, split open at SPLIT_Y with the gap filled by stretching a plain row of the texture
        graphics.blit(INVENTORY_TEXTURE, left, top, 0, 0, VANILLA_WIDTH, SPLIT_Y);
        graphics.blit(INVENTORY_TEXTURE, left, top + SPLIT_Y, VANILLA_WIDTH, this.bandHeight, 0, SPLIT_Y + 1, VANILLA_WIDTH, 1, 256, 256);
        graphics.blit(INVENTORY_TEXTURE, left, top + SPLIT_Y + this.bandHeight, 0, SPLIT_Y, VANILLA_WIDTH, VANILLA_HEIGHT - SPLIT_Y);

        // Backpack band, drawn with Backpacked's own sprites so locked slots look identical to its screen
        graphics.blitSprite(BAND_BACKGROUND, left + BAND_X, top + SPLIT_Y, BAND_WIDTH, this.bandHeight - 1);
        int slotsWidth = this.visibleColumns * 18;
        int slotsHeight = this.visibleRows * 18;
        graphics.blitSprite(BACKPACK_SLOT, left + this.gridX - 1, top + this.gridY - 1, slotsWidth, slotsHeight);
        int checkersWidth = this.gridX - 1 - (BAND_X + 4) - 2;
        if(checkersWidth > 0)
        {
            graphics.blitSprite(CHECKERS, left + BAND_X + 4, top + this.gridY - 1, checkersWidth, slotsHeight);
            graphics.blitSprite(CHECKERS, left + this.gridX - 1 + slotsWidth + 2, top + this.gridY - 1, checkersWidth, slotsHeight);
        }
        if(this.hasHorizontalScroll())
        {
            int trackY = top + this.gridY - 1 + slotsHeight + 2;
            this.renderScrollbar(graphics, left + this.gridX - 1, trackY, slotsWidth, SCROLLBAR_SIZE, true,
                this.scrollColumn, this.maxScrollColumn(), this.visibleColumns, this.data.columns());
        }

        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left + 26, top + 8, left + 75, top + 78, 30, 0.0625F, mouseX, mouseY, this.minecraft.player);
    }

    private void renderEquipmentPanel(GuiGraphics graphics, int left, int top)
    {
        int x = left + this.panelX;
        int y = top + PANEL_TOP;
        graphics.blitSprite(PANEL_BACKGROUND, x, y, this.panelWidth, this.panelHeight());
        for(Slot slot : this.panelSlots)
        {
            if(slot.x != BackpackInventoryMenu.HIDDEN)
            {
                graphics.blitSprite(PANEL_SLOT, left + slot.x - 1, top + slot.y - 1, 18, 18);
            }
        }
        if(this.hasPanelScroll())
        {
            int trackX = x + PANEL_PADDING + this.panelColumns * 18 + 2;
            int trackY = y + PANEL_PADDING;
            this.renderScrollbar(graphics, trackX, trackY, SCROLLBAR_SIZE, this.panelRows * 18, false,
                this.panelScroll, this.maxPanelScroll(), this.panelRows, Mth.positiveCeilDiv(this.panelSlots.size(), this.panelColumns));
        }
    }

    private void renderVerticalScrollbar(GuiGraphics graphics, int left, int top)
    {
        Rect2i tab = this.verticalScrollTab();
        graphics.blitSprite(BAND_BACKGROUND, left + tab.getX(), top + tab.getY(), tab.getWidth(), tab.getHeight());
        this.renderScrollbar(graphics, left + tab.getX() + 4, top + this.gridY - 1, SCROLLBAR_SIZE, this.visibleRows * 18, false,
            this.scrollRow, this.maxScrollRow(), this.visibleRows, this.data.rows());
    }

    /** The tab holding the vertical scrollbar, attached to the right edge of the inventory (relative coords). */
    private Rect2i verticalScrollTab()
    {
        return new Rect2i(VANILLA_WIDTH - 4, this.gridY - 5, V_SCROLLBAR_WIDTH + 1, this.visibleRows * 18 + 8);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int y, int width, int height, boolean horizontal, int value, int max, int visible, int total)
    {
        graphics.fill(x, y, x + width, y + height, 0xFF8B7B66);
        int length = horizontal ? width : height;
        int thumbLength = Math.max(6, length * visible / Math.max(1, total));
        int thumbOffset = max > 0 ? (length - thumbLength) * value / max : 0;
        if(horizontal)
        {
            graphics.fill(x + thumbOffset, y, x + thumbOffset + thumbLength, y + height, 0xFF5B4B3B);
            graphics.fill(x + thumbOffset, y, x + thumbOffset + thumbLength - 1, y + height - 1, 0xFFF3E4CD);
        }
        else
        {
            graphics.fill(x, y + thumbOffset, x + width, y + thumbOffset + thumbLength, 0xFF5B4B3B);
            graphics.fill(x, y + thumbOffset, x + width - 1, y + thumbOffset + thumbLength - 1, 0xFFF3E4CD);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        graphics.drawString(this.font, CRAFTING_TITLE, 97, 8, 0x404040, false);
        int maxTitleWidth = (BAND_X + BAND_WIDTH - 4 - this.headerButtonsWidth() - 2 - 10 - 4) - (BAND_X + 5);
        graphics.drawString(this.font, this.trimmedTitle(maxTitleWidth), BAND_X + 5, SPLIT_Y + 5, 0x404040, false);
    }

    private FormattedCharSequence trimmedTitle(int maxWidth)
    {
        if(this.font.width(this.title) <= maxWidth)
        {
            return this.title.getVisualOrderText();
        }
        FormattedText trimmed = this.font.substrByWidth(this.title, maxWidth - this.font.width("..."));
        return Language.getInstance().getVisualOrder(FormattedText.composite(trimmed, FormattedText.of("...")));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Input

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY)
    {
        return (!this.widthTooNarrow || !this.recipeBookComponent.isVisible()) && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(this.hasPopupMenu())
        {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if(this.recipeBookComponent.mouseClicked(mouseX, mouseY, button))
        {
            this.setFocused(this.recipeBookComponent);
            return true;
        }
        if(this.widthTooNarrow && this.recipeBookComponent.isVisible())
        {
            return false;
        }
        if(button == 0 && !this.hasPopupMenu() && this.startScrollDrag(mouseX, mouseY))
        {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        if(this.scrollDrag != ScrollDrag.NONE || this.panelScrollDragging)
        {
            this.updateScrollDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if(this.scrollDrag != ScrollDrag.NONE || this.panelScrollDragging)
        {
            this.scrollDrag = ScrollDrag.NONE;
            this.panelScrollDragging = false;
            return true;
        }
        if(this.recipeButtonClicked)
        {
            this.recipeButtonClicked = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if(!this.hasPopupMenu())
        {
            int step = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
            if(step != 0 && this.isOverBackpack(mouseX, mouseY))
            {
                if(this.hasVerticalScroll() && !hasShiftDown())
                {
                    this.setBackpackScroll(this.scrollColumn, this.scrollRow + step);
                    return true;
                }
                if(this.hasHorizontalScroll())
                {
                    this.setBackpackScroll(this.scrollColumn + step, this.scrollRow);
                    return true;
                }
            }
            if(step != 0 && this.isPanelDrawn() && this.hasPanelScroll() && this.isOverPanel(mouseX, mouseY))
            {
                this.setPanelScroll(this.panelScroll + step);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isOverBackpack(double mouseX, double mouseY)
    {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        boolean inBand = x >= BAND_X && x < BAND_X + BAND_WIDTH && y >= SPLIT_Y && y < SPLIT_Y + this.bandHeight;
        return inBand || (this.hasVerticalScroll() && contains(this.verticalScrollTab(), x, y));
    }

    private boolean isOverPanel(double mouseX, double mouseY)
    {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        return x >= this.panelX && x < this.panelX + this.panelWidth && y >= PANEL_TOP && y < PANEL_TOP + this.panelHeight();
    }

    private static boolean contains(Rect2i rect, double x, double y)
    {
        return x >= rect.getX() && x < rect.getX() + rect.getWidth() && y >= rect.getY() && y < rect.getY() + rect.getHeight();
    }

    private boolean startScrollDrag(double mouseX, double mouseY)
    {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
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
        if(this.isPanelDrawn() && this.hasPanelScroll())
        {
            int trackX = this.panelX + PANEL_PADDING + this.panelColumns * 18 + 1;
            if(x >= trackX && x < trackX + SCROLLBAR_SIZE + 2 && y >= PANEL_TOP + PANEL_PADDING && y < PANEL_TOP + PANEL_PADDING + this.panelRows * 18)
            {
                this.panelScrollDragging = true;
                this.updateScrollDrag(mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private void updateScrollDrag(double mouseX, double mouseY)
    {
        double x = mouseX - this.leftPos;
        double y = mouseY - this.topPos;
        if(this.scrollDrag == ScrollDrag.VERTICAL)
        {
            double progress = (y - (this.gridY - 1)) / (this.visibleRows * 18.0);
            this.setBackpackScroll(this.scrollColumn, (int) Math.round(progress * this.maxScrollRow()));
        }
        else if(this.scrollDrag == ScrollDrag.HORIZONTAL)
        {
            double progress = (x - (this.gridX - 1)) / (this.visibleColumns * 18.0);
            this.setBackpackScroll((int) Math.round(progress * this.maxScrollColumn()), this.scrollRow);
        }
        else if(this.panelScrollDragging)
        {
            double progress = (y - (PANEL_TOP + PANEL_PADDING)) / (this.panelRows * 18.0);
            this.setPanelScroll((int) Math.round(progress * this.maxPanelScroll()));
        }
    }

    private void setBackpackScroll(int column, int row)
    {
        column = Mth.clamp(column, 0, this.maxScrollColumn());
        row = Mth.clamp(row, 0, this.maxScrollRow());
        if(column != this.scrollColumn || row != this.scrollRow)
        {
            this.scrollColumn = column;
            this.scrollRow = row;
            this.selectedSlots.clear();
            this.applyBackpackScroll();
        }
    }

    private void setPanelScroll(int scroll)
    {
        scroll = Mth.clamp(scroll, 0, this.maxPanelScroll());
        if(scroll != this.panelScroll)
        {
            this.panelScroll = scroll;
            this.applyPanelLayout();
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button)
    {
        double x = mouseX - guiLeft;
        double y = mouseY - guiTop;
        boolean outsideMain = x < 0 || y < 0 || x >= this.imageWidth || y >= this.imageHeight;
        boolean outsideExtras = !(this.isPanelDrawn() && this.isOverPanel(mouseX, mouseY))
            && !(this.hasVerticalScroll() && contains(this.verticalScrollTab(), x, y));
        return this.recipeBookComponent.hasClickedOutside(mouseX, mouseY, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, button)
            && outsideMain && outsideExtras;
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType type)
    {
        super.slotClicked(slot, slotId, button, type);
        this.recipeBookComponent.slotClicked(slot);
    }

    @Override
    public void removed()
    {
        super.removed();
        MouseRestorer.capturePosition();
    }

    @Override
    public void recipesUpdated()
    {
        this.recipeBookComponent.recipesUpdated();
    }

    @Override
    public RecipeBookComponent getRecipeBookComponent()
    {
        return this.recipeBookComponent;
    }

    /** Screen areas outside the main window that this screen draws on (absolute coords), for JEI exclusion. */
    public List<Rect2i> getExtraAreas()
    {
        List<Rect2i> areas = new ArrayList<>();
        if(this.equipmentCount() > 0)
        {
            int x = this.panelLeft() - 14;
            int width = this.isPanelDrawn() ? this.panelWidth + 14 : 14;
            areas.add(new Rect2i(this.leftPos + x, this.topPos + PANEL_TOP, width, Math.max(this.isPanelDrawn() ? this.panelHeight() : 0, 44)));
        }
        if(this.hasVerticalScroll())
        {
            Rect2i tab = this.verticalScrollTab();
            areas.add(new Rect2i(this.leftPos + tab.getX(), this.topPos + tab.getY(), tab.getWidth(), tab.getHeight()));
        }
        return areas;
    }

    private enum ScrollDrag
    {
        NONE, VERTICAL, HORIZONTAL
    }

    private record Positioned(AbstractWidget widget, IntSupplier x, IntSupplier y) {}
}
