package com.lincstoel.integratedui.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Per-player options. What the inventory menus hold (and which mods they integrate with) is decided by the server,
 * see {@link ServerConfig}.
 * <p>
 * Every option has a translation key ({@code integrated_ui.configuration.<name>}), so config screens such as
 * Configured's show proper names.
 */
public final class ClientConfig
{
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REPLACE_INVENTORY_SCREEN;
    public static final ModConfigSpec.IntValue MAX_VISIBLE_ROWS;
    public static final ModConfigSpec.BooleanValue EQUIPMENT_PANEL_OPEN;
    public static final ModConfigSpec.BooleanValue CONTAINER_BAND_OPEN;
    public static final ModConfigSpec.BooleanValue RIGHT_CLICK_OPENS_SPELL_BOOKS;
    public static final ModConfigSpec.BooleanValue RIGHT_CLICK_OPENS_CONTAINERS;
    public static final ModConfigSpec.BooleanValue RIGHT_CLICK_OPENS_FILTERS;
    public static final ModConfigSpec.BooleanValue JEI;
    public static final ModConfigSpec.BooleanValue AETHER;
    public static final ModConfigSpec.BooleanValue FIELD_GUIDE;
    public static final ModConfigSpec.BooleanValue FTB_QUESTS;
    public static final ModConfigSpec.BooleanValue OVERRIDE_QUARK_SHULKER_BOXES;

    static
    {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REPLACE_INVENTORY_SCREEN = builder
            .comment("Replace the player inventory screen with the integrated inventory, which shows a band of backpack or",
                "container item slots above the main inventory and an equipment slot panel on its left. What they show is",
                "set in the server config (integrated_ui-server.toml).")
            .translation(key("replaceInventoryScreen"))
            .define("replaceInventoryScreen", true);
        MAX_VISIBLE_ROWS = builder
            .comment("The maximum number of backpack, shulker box or sack rows shown at once. Larger ones can be scrolled.",
                "Fewer rows are shown if the screen is too short.")
            .translation(key("maxVisibleRows"))
            .defineInRange("maxVisibleRows", 3, 1, 11);
        EQUIPMENT_PANEL_OPEN = builder
            .comment("Whether the Curios/Accessories slot panel on the left of the inventory is open. Changes whenever you open",
                "or close it, so it stays the way you left it.")
            .translation(key("equipmentPanelOpen"))
            .define("equipmentPanelOpen", true);
        CONTAINER_BAND_OPEN = builder
            .comment("Whether the band of backpack or container item slots above the main inventory is open. Changes whenever you",
                "open or close it, so it stays the way you left it.")
            .translation(key("containerBandOpen"))
            .define("containerBandOpen", true);
        RIGHT_CLICK_OPENS_SPELL_BOOKS = builder
            .comment("With Ars Nouveau installed, right-clicking a spell book in your inventory with an empty cursor opens",
                "its spell crafting screen, as Quark does for shulker boxes. Works in any container screen.")
            .translation(key("rightClickOpensSpellBooks"))
            .define("rightClickOpensSpellBooks", true);
        RIGHT_CLICK_OPENS_CONTAINERS = builder
            .comment("Right-clicking a container item in your inventory with an empty cursor opens it in its own screen, as Quark",
                "does for shulker boxes. Works in any container screen (a chest's, say); closing it returns to that screen",
                "(your inventory, or the chest). Applies to items in the integrated_ui:opens_own_screen tag (shulker boxes and",
                "Supplementaries sacks by default). See also overrideQuarkShulkerBoxes.")
            .translation(key("rightClickOpensContainers"))
            .define("rightClickOpensContainers", true);
        RIGHT_CLICK_OPENS_FILTERS = builder
            .comment("With Create installed, right-clicking a filter (list, attribute or package filter) in your inventory with",
                "an empty cursor opens its screen. Works in any container screen.")
            .translation(key("rightClickOpensFilters"))
            .define("rightClickOpensFilters", true);

        builder.comment("Integration with client-side mods. Turning one off makes Integrated UI act as if that mod wasn't",
            "installed. Each has no effect if the mod isn't installed. Other integrations are in the server config.")
            .translation(key("compat")).push("compat");
        JEI = builder
            .comment("JEI: keep JEI's overlays clear of the side panels, let its \"+\" button fill the crafting grid, and hide",
                "the recipe book button (as JEI replaces it). Also adds a button that shows/hides JEI.")
            .translation(key("jei"))
            .define("jei", true);
        AETHER = builder
            .comment("The Aether: add the Aether's accessories button to the integrated inventory.")
            .translation(key("aether"))
            .define("aether", true);
        FIELD_GUIDE = builder
            .comment("Field Guide: add a button that opens the Field Guide below the equipment panel's buttons.")
            .translation(key("fieldGuide"))
            .define("fieldGuide", true);
        FTB_QUESTS = builder
            .comment("FTB Quests: add a button that opens the quest book below the equipment panel's buttons.")
            .translation(key("ftbQuests"))
            .define("ftbQuests", true);
        OVERRIDE_QUARK_SHULKER_BOXES = builder
            .comment("Quark: open shulker boxes right-clicked in the inventory in Integrated UI's screen in place of Quark's held",
                "shulker box screen, so the inventory doesn't flicker on opening and closing them (and the cursor stays put).",
                "When off, Quark opens them itself, if its allowOpeningShulkerBoxes option is on.")
            .translation(key("overrideQuarkShulkerBoxes"))
            .define("overrideQuarkShulkerBoxes", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ClientConfig() {}

    static String key(String name)
    {
        return "integrated_ui.configuration." + name;
    }

    /** The value, or its default while the config isn't loaded yet. */
    public static boolean get(ModConfigSpec.BooleanValue value)
    {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
