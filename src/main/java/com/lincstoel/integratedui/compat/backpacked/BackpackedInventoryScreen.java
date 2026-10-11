package com.lincstoel.integratedui.compat.backpacked;

import com.lincstoel.integratedui.client.ButtonLabels;
import com.lincstoel.integratedui.client.ContainerBand;
import com.lincstoel.integratedui.client.EquipmentPanel;
import com.lincstoel.integratedui.client.IntegratedScreen;
import com.lincstoel.integratedui.client.InventoryScreenParts;
import com.lincstoel.integratedui.menu.OpenData;
import com.lincstoel.integratedui.network.RenamePayload;
import com.mrcrayfish.backpacked.Config;
import com.mrcrayfish.backpacked.client.gui.screen.inventory.UnlockableContainerScreen;
import com.mrcrayfish.backpacked.client.gui.screen.widget.popup.TextInputMenu;
import com.mrcrayfish.backpacked.common.UnlockableSlotMode;
import com.mrcrayfish.backpacked.inventory.container.slot.UnlockableSlot;
import com.mrcrayfish.backpacked.network.Network;
import com.mrcrayfish.backpacked.network.message.MessageOpenBackpack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The survival inventory with a Backpacked backpack in the band (see {@link ContainerBand}). Extends Backpacked's
 * {@link UnlockableContainerScreen}, which provides the lock overlay, hold-to-unlock, drag selection, cost tooltips
 * and unlock particles for the backpack slots. Only used while a backpack is shown; otherwise the plain
 * IntegratedInventoryScreen is. Most of the work is shared with that one in {@link InventoryScreenParts}.
 */
public class BackpackedInventoryScreen extends UnlockableContainerScreen<BackpackedInventoryMenu> implements RecipeUpdateListener, IntegratedScreen
{
    /** Drawn with Backpacked's own sprites, so locked slots look identical to its screen. */
    private static final ContainerBand.Style STYLE = new ContainerBand.Style(backpacked("backpack/label"), backpacked("backpack/slot"),
        backpacked("backpack/checkers"), backpacked("backpack/previous"), backpacked("backpack/next"), backpacked("backpack/rename"),
        0xFFFFFF, 0xFFFFFF, 0x404040, EquipmentPanel.ScrollbarColors.BROWN);

    private static final Component OPEN_BACKPACK = Component.translatable("integrated_ui.gui.open_backpack");

    protected final InventoryScreenParts parts;
    private final OpenData data;
    private int lockModeTimer;

    public BackpackedInventoryScreen(BackpackedInventoryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.data = menu.getData();
        this.parts = new InventoryScreenParts(this, menu, STYLE, () -> this.selectedSlots.clear(), left -> this.leftPos = left);
        this.imageWidth = ContainerBand.VANILLA_WIDTH;
    }

    private static ResourceLocation backpacked(String path)
    {
        return ResourceLocation.fromNamespaceAndPath("backpacked", path);
    }

    @Override
    public InventoryScreenParts parts()
    {
        return this.parts;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Init and widgets

    @Override
    protected void init()
    {
        this.imageHeight = this.parts.layoutBand();
        this.inventoryLabelY = this.imageHeight - 94;
        super.init();
        // The button to open the full Backpacked screen sits at the right of the band header
        Button open = new BackpackButton(this.data::icon, button -> this.openFullBackpackScreen());
        ButtonLabels.set(open, OPEN_BACKPACK);
        this.parts.init(this::addRenderableWidget, this::addWidget, open, this::openRenamePopup);
        this.updateLockedSlotVisibility();
    }

    private void openRenamePopup(Button button)
    {
        new TextInputMenu(this, this.title.getString(), RenamePayload.MAX_LENGTH,
            name -> PacketDistributor.sendToServer(new RenamePayload(name))).show(button);
    }

    private void openFullBackpackScreen()
    {
        BackpackedClient.noteFullScreenOpened();
        Network.PLAY.sendToServer(new MessageOpenBackpack(this.data.backpackIndex()));
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
        if(this.lockModeTimer-- <= 0)
        {
            this.updateLockedSlotVisibility();
            this.lockModeTimer = 5;
        }
        this.parts.tick();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.parts.render(graphics, mouseX, mouseY, partialTick, () -> {
            super.render(graphics, mouseX, mouseY, partialTick);
            this.parts.renderEffects(graphics, mouseX, mouseY);
        });
        this.parts.renderRecipeBookTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY)
    {
        if(!this.parts.renderTooltip(graphics, this.font, this.hoveredSlot, mouseX, mouseY))
        {
            super.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        this.parts.renderBg(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY)
    {
        this.parts.renderLabels(graphics, this.font);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Input. While one of Backpacked's popups (such as the rename box) is open, it takes the input.

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY)
    {
        return !this.parts.blocksHovering() && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if(this.hasPopupMenu())
        {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return this.parts.keyPressed(keyCode, scanCode, modifiers, () -> super.keyPressed(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(this.hasPopupMenu())
        {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return this.parts.mouseClicked(mouseX, mouseY, button, () -> super.mouseClicked(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY)
    {
        return this.parts.mouseDragged(mouseX, mouseY, () -> super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return this.parts.mouseReleased(() -> super.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if(this.hasPopupMenu())
        {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return this.parts.mouseScrolled(mouseX, mouseY, scrollY, hasShiftDown(), () -> super.mouseScrolled(mouseX, mouseY, scrollX, scrollY));
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button)
    {
        return this.parts.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType type)
    {
        super.slotClicked(slot, slotId, button, type);
        this.parts.slotClicked(slot);
    }

    @Override
    public void recipesUpdated()
    {
        this.parts.recipesUpdated();
    }

    @Override
    public RecipeBookComponent getRecipeBookComponent()
    {
        return this.parts.recipeBook();
    }
}
