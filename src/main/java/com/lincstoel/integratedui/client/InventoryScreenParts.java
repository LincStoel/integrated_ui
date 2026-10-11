package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.compat.mousetweaks.MouseTweaksCompat;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.Navigation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Everything the two integrated inventory screens have in common: the band, the equipment panel and the recipe book,
 * and the input, layout and rendering that tie them together, as vanilla's {@link InventoryScreen} does. The plain
 * screen and Backpacked's (which has to extend Backpacked's own screen) each forward to this.
 */
public final class InventoryScreenParts
{
    private static final Component CRAFTING_TITLE = Component.translatable("container.crafting");
    private static final ResourceLocation EFFECT_BACKGROUND_LARGE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_large");
    private static final ResourceLocation EFFECT_BACKGROUND_SMALL = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_small");

    private final AbstractContainerScreen<?> screen;
    private final IntegratedInventoryMenu menu;
    private final RecipeBookComponent recipeBook = new RecipeBookComponent();
    private final EquipmentPanel panel;
    private final ContainerBand band;
    private final IntConsumer setLeftPos;
    @Nullable
    private ImageButton recipeButton;
    private boolean widthTooNarrow;
    private boolean recipeButtonClicked;
    /** Where status effects were last drawn by {@link #renderEffects} (absolute coords), for JEI exclusion. */
    @Nullable
    private Rect2i effectsArea;

    /**
     * {@code onBandScroll} is called whenever the band's grid scrolls (which moves its slots). {@code setLeftPos}
     * moves the screen's window, as the recipe book does when it opens.
     */
    public InventoryScreenParts(AbstractContainerScreen<?> screen, IntegratedInventoryMenu menu, ContainerBand.Style bandStyle, Runnable onBandScroll,
                                IntConsumer setLeftPos)
    {
        this.screen = screen;
        this.menu = menu;
        this.panel = new EquipmentPanel(screen, menu);
        this.band = new ContainerBand(screen, menu, bandStyle, onBandScroll);
        this.setLeftPos = setLeftPos;
    }

    public ContainerBand band()
    {
        return this.band;
    }

    public RecipeBookComponent recipeBook()
    {
        return this.recipeBook;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Layout and widgets

    /**
     * Lays the band out for the screen's height, and returns the height of the inventory window around it. Call
     * first thing in the screen's init(), and set the window height (and the inventory label below it) from this.
     */
    public int layoutBand()
    {
        this.band.layout(this.screen.height);
        return ContainerBand.VANILLA_HEIGHT + this.band.getHeight();
    }

    /**
     * Sets up the panel, the recipe book and all the widgets. Call from the screen's init(), after super.init().
     * {@code headerButton}, if given, goes at the right of the band header; {@code onRename} handles the band's
     * rename button, which renames in place if it is null.
     */
    public void init(Consumer<AbstractWidget> addWidget, Consumer<RecipeBookComponent> addListener, @Nullable AbstractWidget headerButton,
                     @Nullable Button.OnPress onRename)
    {
        Minecraft mc = Minecraft.getInstance();
        this.panel.layout();
        this.widthTooNarrow = this.screen.width < 379;
        this.recipeBook.init(this.screen.width, this.screen.height, mc, this.widthTooNarrow, this.menu);
        // JEI takes the recipe book's place: keep it closed and drop its button
        if(IntegratedUI.isJeiEnabled() && this.recipeBook.isVisible())
        {
            this.recipeBook.toggleVisibility();
        }
        this.setLeftPos.accept(this.recipeBook.updateScreenPosition(this.screen.width, this.screen.getXSize()));
        this.recipeButton = null;
        if(!IntegratedUI.isJeiEnabled())
        {
            this.recipeButton = new ImageButton(0, 0, 20, 18, RecipeBookComponent.RECIPE_BUTTON_SPRITES, button -> {
                this.recipeBook.toggleVisibility();
                this.setLeftPos.accept(this.recipeBook.updateScreenPosition(this.screen.width, this.screen.getXSize()));
                this.positionWidgets();
                this.recipeButtonClicked = true;
            });
            addWidget.accept(this.recipeButton);
            addListener.accept(this.recipeBook);
        }
        this.band.init(addWidget, headerButton, onRename);
        this.panel.init(addWidget);
        this.positionWidgets();
    }

    /** Moves every widget to the window's current position. */
    public void positionWidgets()
    {
        if(this.recipeButton != null)
        {
            this.recipeButton.setPosition(this.screen.getGuiLeft() + 104, this.screen.getGuiTop() + 61);
        }
        this.band.positionWidgets();
        // The open recipe book covers where the panel goes
        this.panel.setCovered(this.recipeBook.isVisible());
        this.panel.positionWidgets();
    }

    public void tick()
    {
        this.recipeBook.tick();
        this.band.tick();
        this.panel.tick();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rendering

    /** Renders the screen ({@code renderScreen} being its super.render) with the recipe book around it. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, Runnable renderScreen)
    {
        if(this.recipeBook.isVisible() && this.widthTooNarrow)
        {
            this.screen.renderBackground(graphics, mouseX, mouseY, partialTick);
            this.recipeBook.render(graphics, mouseX, mouseY, partialTick);
        }
        else
        {
            renderScreen.run();
            this.recipeBook.render(graphics, mouseX, mouseY, partialTick);
            this.recipeBook.renderGhostRecipe(graphics, this.screen.getGuiLeft(), this.screen.getGuiTop(), false, partialTick);
        }
    }

    /** Renders the recipe book's tooltip. Call last. */
    public void renderRecipeBookTooltip(GuiGraphics graphics, int mouseX, int mouseY)
    {
        this.recipeBook.renderTooltip(graphics, this.screen.getGuiLeft(), this.screen.getGuiTop(), mouseX, mouseY);
    }

    /** Names a hovered empty equipment slot. Returns true if it drew a tooltip, in place of the screen's own. */
    public boolean renderTooltip(GuiGraphics graphics, Font font, @Nullable Slot hoveredSlot, int mouseX, int mouseY)
    {
        return this.panel.renderTooltip(graphics, font, hoveredSlot, mouseX, mouseY);
    }

    public void renderBg(GuiGraphics graphics, int mouseX, int mouseY)
    {
        int left = this.screen.getGuiLeft();
        int top = this.screen.getGuiTop();
        this.panel.render(graphics);
        this.band.renderBehind(graphics);
        this.band.render(graphics);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left + 26, top + 8, left + 75, top + 78, 30, 0.0625F, mouseX, mouseY,
            Minecraft.getInstance().player);
    }

    public void renderLabels(GuiGraphics graphics, Font font)
    {
        graphics.drawString(font, CRAFTING_TITLE, 97, 8, 0x404040, false);
        this.band.renderLabels(graphics, font);
    }

    /**
     * How far right of vanilla's spot status effects are drawn: past the band's vertical scroll tab, which sticks out
     * of the window's right edge. Applied through {@link ScreenEvent.RenderInventoryMobEffects}, see ClientEvents.
     */
    public int getEffectsOffset()
    {
        return this.band.getRightOverhang();
    }

    /**
     * Draws the player's status effects right of the window, as {@link net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen}
     * does, for the screen that can't extend it (Backpacked's). Effects with their own renderer are drawn with their
     * plain icon, as those renderers only take that kind of screen.
     */
    public void renderEffects(GuiGraphics graphics, int mouseX, int mouseY)
    {
        this.effectsArea = null;
        Minecraft mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null)
        {
            return;
        }
        int x = this.screen.getGuiLeft() + this.screen.getXSize() + 2;
        int space = this.screen.width - x;
        List<MobEffectInstance> effects = mc.player.getActiveEffects().stream().filter(ClientHooks::shouldRenderEffect).sorted().toList();
        if(effects.isEmpty() || space < 32)
        {
            return;
        }
        ScreenEvent.RenderInventoryMobEffects event = ClientHooks.onScreenPotionSize(this.screen, space, space < 120, x);
        if(event.isCanceled())
        {
            return;
        }
        boolean large = !event.isCompact();
        x = event.getHorizontalOffset();
        int step = effects.size() > 5 ? 132 / (effects.size() - 1) : 33;
        int top = this.screen.getGuiTop();
        Font font = mc.font;
        MobEffectInstance hovered = null;
        for(int i = 0; i < effects.size(); i++)
        {
            MobEffectInstance effect = effects.get(i);
            int y = top + i * step;
            graphics.blitSprite(large ? EFFECT_BACKGROUND_LARGE : EFFECT_BACKGROUND_SMALL, x, y, large ? 120 : 32, 32);
            TextureAtlasSprite icon = mc.getMobEffectTextures().get(effect.getEffect());
            graphics.blit(x + (large ? 6 : 7), y + 7, 0, 18, 18, icon);
            if(large)
            {
                graphics.drawString(font, getEffectName(effect), x + 28, y + 6, 0xFFFFFF);
                graphics.drawString(font, MobEffectUtil.formatDuration(effect, 1.0F, mc.level.tickRateManager().tickrate()), x + 28, y + 16, 0x7F7F7F);
            }
            else if(mouseX >= x && mouseX <= x + 33 && mouseY >= y && mouseY <= y + step)
            {
                hovered = effect;
            }
        }
        this.effectsArea = new Rect2i(x, top, large ? 120 : 32, (effects.size() - 1) * step + 32);
        if(hovered != null)
        {
            graphics.renderTooltip(font, List.of(getEffectName(hovered), MobEffectUtil.formatDuration(hovered, 1.0F, mc.level.tickRateManager().tickrate())),
                Optional.empty(), mouseX, mouseY);
        }
    }

    private static Component getEffectName(MobEffectInstance effect)
    {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        if(effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9)
        {
            name.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
        }
        return name;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Input. Each takes the screen's own (super) handling, used where none of the parts takes the input.

    /** Whether slots can't be hovered, as the recipe book covers the whole window. */
    public boolean blocksHovering()
    {
        return this.widthTooNarrow && this.recipeBook.isVisible();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, BooleanSupplier screenClick)
    {
        if(this.recipeBook.mouseClicked(mouseX, mouseY, button))
        {
            this.screen.setFocused(this.recipeBook);
            return true;
        }
        if(this.widthTooNarrow && this.recipeBook.isVisible())
        {
            return false;
        }
        if(this.band.mouseClicked(mouseX, mouseY, button) || (button == 0 && this.panel.mouseClicked(mouseX, mouseY)))
        {
            return true;
        }
        return screenClick.getAsBoolean();
    }

    public boolean mouseDragged(double mouseX, double mouseY, BooleanSupplier screenDrag)
    {
        return this.band.mouseDragged(mouseX, mouseY) || this.panel.mouseDragged(mouseY) || screenDrag.getAsBoolean();
    }

    public boolean mouseReleased(BooleanSupplier screenRelease)
    {
        boolean wasDragging = this.band.mouseReleased();
        if(this.panel.mouseReleased() || wasDragging)
        {
            return true;
        }
        if(this.recipeButtonClicked)
        {
            this.recipeButtonClicked = false;
            return true;
        }
        return screenRelease.getAsBoolean();
    }

    /**
     * Scrolls the band or panel under the mouse. Over a slot, Mouse Tweaks' scroll wheel feature (moving items) wins
     * when it is on: the scroll is left unhandled for it, and the band and panel scroll from their edges instead.
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, boolean horizontal, BooleanSupplier screenScroll)
    {
        Slot hovered = this.screen.getSlotUnderMouse();
        if(hovered != null && !isSlotHidden(hovered) && IntegratedUI.isMouseTweaksLoaded() && MouseTweaksCompat.isWheelTweakOn())
        {
            return screenScroll.getAsBoolean();
        }
        int step = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        return this.band.mouseScrolled(mouseX, mouseY, step, horizontal) || this.panel.mouseScrolled(mouseX, mouseY, step) || screenScroll.getAsBoolean();
    }

    /** Typing while renaming, then the previous/next keys, then the screen's own keys. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers, BooleanSupplier screenKey)
    {
        if(this.band.keyPressed(keyCode, scanCode, modifiers))
        {
            return true;
        }
        if(ModKeys.matches(ModKeys.PREVIOUS, keyCode, scanCode) && this.band.navigate(Navigation.PREVIOUS))
        {
            return true;
        }
        if(ModKeys.matches(ModKeys.NEXT, keyCode, scanCode) && this.band.navigate(Navigation.NEXT))
        {
            return true;
        }
        return screenKey.getAsBoolean();
    }

    /** Whether a click is outside everything drawn: window, panel, band tab and recipe book. */
    public boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button)
    {
        boolean outsideMain = mouseX < guiLeft || mouseY < guiTop || mouseX >= guiLeft + this.screen.getXSize() || mouseY >= guiTop + this.screen.getYSize();
        return this.recipeBook.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, this.screen.getXSize(), this.screen.getYSize(), button)
            && outsideMain && !this.panel.contains(mouseX, mouseY) && !this.band.containsOutside(mouseX, mouseY);
    }

    /** Call when a slot is clicked, so the recipe book can drop its ghost recipe. */
    public void slotClicked(@Nullable Slot slot)
    {
        this.recipeBook.slotClicked(slot);
    }

    public void recipesUpdated()
    {
        this.recipeBook.recipesUpdated();
    }

    // ---------------------------------------------------------------------------------------------------------
    // For compat

    /** Screen areas outside the main window that this screen draws on (absolute coords), for JEI exclusion. */
    public List<Rect2i> getExtraAreas()
    {
        List<Rect2i> areas = new ArrayList<>();
        Rect2i panelArea = this.panel.getExtraArea();
        if(panelArea != null)
        {
            areas.add(panelArea);
        }
        this.band.addExtraAreas(areas);
        if(this.effectsArea != null)
        {
            areas.add(this.effectsArea);
        }
        return areas;
    }

    /** Whether the slot is out of sight: scrolled out of the band, in the collapsed panel, or never shown. */
    public static boolean isSlotHidden(Slot slot)
    {
        return !slot.isActive() || slot.x == IntegratedInventoryMenu.HIDDEN;
    }

    /**
     * The slots Mouse Tweaks may move items between, in the order it fills them: the player's inventory, then the
     * band (so items scrolled out of the inventory go there before the crafting grid), the crafting area and the
     * equipment slots. Hidden slots are left out, so nothing goes where it can't be seen.
     */
    public List<Slot> getMouseTweaksSlots()
    {
        List<Slot> slots = new ArrayList<>();
        List<Slot> all = this.menu.slots;
        addVisible(slots, all, 5, IntegratedInventoryMenu.BACKPACK_START);
        addVisible(slots, all, IntegratedInventoryMenu.BACKPACK_START, this.menu.getBackpackEnd());
        addVisible(slots, all, IntegratedInventoryMenu.RESULT_SLOT, 5);
        addVisible(slots, all, this.menu.getEquipmentStart(), all.size());
        return slots;
    }

    private static void addVisible(List<Slot> into, List<Slot> slots, int start, int end)
    {
        for(int i = start; i < end; i++)
        {
            if(!isSlotHidden(slots.get(i)))
            {
                into.add(slots.get(i));
            }
        }
    }

    /** The unhidden slot under the mouse, by the screen's own hover test, or null. */
    @Nullable
    public Slot findSlot(double mouseX, double mouseY, SlotHoverTest test)
    {
        for(Slot slot : this.menu.slots)
        {
            if(!isSlotHidden(slot) && test.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY))
            {
                return slot;
            }
        }
        return null;
    }

    /** A screen's (protected) isHovering. */
    @FunctionalInterface
    public interface SlotHoverTest
    {
        boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY);
    }
}
