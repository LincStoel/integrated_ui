package com.lincstoel.integratedui.client;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.menu.IntegratedInventoryMenu;
import com.lincstoel.integratedui.menu.OpenData;
import com.lincstoel.integratedui.menu.ShulkerInventoryMenu;
import com.lincstoel.integratedui.network.OpenShulkerBoxPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The survival inventory with the equipment panel and, when the player carries one, a container item such as a shulker box in the band (see
 * {@link ContainerBand}), or a worn Quark backpack: used whenever no Backpacked backpack is shown, including whenever Backpacked isn't installed.
 * Otherwise behaves like vanilla's {@link InventoryScreen}; most of the work is shared with Backpacked's screen in {@link InventoryScreenParts}.
 */
public class IntegratedInventoryScreen extends EffectRenderingInventoryScreen<IntegratedInventoryMenu> implements RecipeUpdateListener, IntegratedScreen
{
    private static final ResourceLocation BUTTON_SPRITE = IntegratedUI.rl("button");

    protected final InventoryScreenParts parts;

    public IntegratedInventoryScreen(IntegratedInventoryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title);
        this.parts = new InventoryScreenParts(this, menu, createStyle(menu.getData()), () -> {}, left -> this.leftPos = left);
    }

    private static ContainerBand.Style createStyle(OpenData data)
    {
        if(data.hasQuarkBackpack())
        {
            return ContainerBand.Style.QUARK_BACKPACK;
        }
        return ContainerBand.Style.containerItem(data.tint());
    }

    @Override
    public InventoryScreenParts parts()
    {
        return this.parts;
    }

    @Override
    protected void init()
    {
        this.imageHeight = this.parts.layoutBand();
        this.inventoryLabelY = this.imageHeight - 94;
        super.init();
        // The button to open the container item's own screen sits at the right of the band header
        ItemButton open = null;
        if(this.menu instanceof ShulkerInventoryMenu shulkerMenu && shulkerMenu.opensOwnScreen())
        {
            open = new ItemButton(12, () -> BUTTON_SPRITE, this.menu.getData()::icon, button -> {
                PortableScreens.noteOpenedFrom(this);
                PacketDistributor.sendToServer(OpenShulkerBoxPayload.INSTANCE);
            });
            ButtonLabels.set(open, Component.translatable("integrated_ui.gui.open_container", this.menu.getData().title()));
        }
        this.parts.init(this::addRenderableWidget, this::addWidget, open, null);
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        this.parts.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.parts.render(graphics, mouseX, mouseY, partialTick, () -> super.render(graphics, mouseX, mouseY, partialTick));
        this.renderTooltip(graphics, mouseX, mouseY);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        return this.parts.keyPressed(keyCode, scanCode, modifiers, () -> super.keyPressed(keyCode, scanCode, modifiers));
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY)
    {
        return !this.parts.blocksHovering() && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
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
