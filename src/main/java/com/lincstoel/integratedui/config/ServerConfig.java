package com.lincstoel.integratedui.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Options that change what the server puts in the integrated inventory's menus. A server config is kept per world
 * and synced to clients, so both sides always build the same slots.
 * <p>
 * The client reads these too (synced from the server it is on). Read them through {@link #get}, which falls back to
 * the default while no world is loaded.
 */
public final class ServerConfig
{
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue CONTAINER_ITEMS_IN_BAND;
    public static final ModConfigSpec.BooleanValue CONTAINER_ITEM_WHITELIST_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CONTAINER_ITEM_WHITELIST;
    public static final ModConfigSpec.IntValue IGNORE_CONTAINER_ITEMS_UP_TO_SLOTS;
    public static final ModConfigSpec.BooleanValue CONTAINER_ITEM_SCREENS;
    public static final ModConfigSpec.BooleanValue RENAMING;
    public static final ModConfigSpec.BooleanValue EQUIPMENT_SLOTS;

    public static final ModConfigSpec.BooleanValue BACKPACKED;
    public static final ModConfigSpec.BooleanValue QUARK;
    public static final ModConfigSpec.BooleanValue CURIOS;
    public static final ModConfigSpec.BooleanValue ACCESSORIES;
    public static final ModConfigSpec.BooleanValue SUPPLEMENTARIES;
    public static final ModConfigSpec.BooleanValue ARS_NOUVEAU;

    static
    {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Features of the integrated inventory. Changes apply the next time the inventory is opened.")
            .translation(ClientConfig.key("features")).push("features");
        CONTAINER_ITEMS_IN_BAND = builder
            .comment("Show container items carried in the inventory (shulker boxes, Supplementaries sacks and the like) in the",
                "band above the main inventory.")
            .translation(ClientConfig.key("containerItemsInBand"))
            .define("containerItemsInBand", true);
        CONTAINER_ITEM_WHITELIST_ENABLED = builder
            .comment("Only show the container items listed in containerItemWhitelist in the band, in place of every container",
                "item. The integrated_ui:band_excluded tag is ignored while this is on. Backpacked and Quark backpacks",
                "aren't affected.")
            .translation(ClientConfig.key("containerItemWhitelistEnabled"))
            .define("containerItemWhitelistEnabled", false);
        CONTAINER_ITEM_WHITELIST = builder
            .comment("The container items shown in the band while containerItemWhitelistEnabled is on: item ids",
                "(e.g. \"minecraft:shulker_box\") or item tags starting with # (e.g. \"#c:shulker_boxes\").")
            .translation(ClientConfig.key("containerItemWhitelist"))
            .defineListAllowEmpty("containerItemWhitelist", () -> List.of("#c:shulker_boxes"), () -> "",
                ServerConfig::isItemOrTagId);
        IGNORE_CONTAINER_ITEMS_UP_TO_SLOTS = builder
            .comment("Container items with this many slots or fewer are left out of the band, and aren't opened in a screen of",
                "their own, so small ones don't clutter it. 0 shows them all. Items on the whitelist are always shown.")
            .translation(ClientConfig.key("ignoreContainerItemsUpToSlots"))
            .defineInRange("ignoreContainerItemsUpToSlots", 1, 0, 256);
        CONTAINER_ITEM_SCREENS = builder
            .comment("Allow container items in the integrated_ui:opens_own_screen tag to be opened in a screen of their own,",
                "by right-clicking them in the inventory (or any container screen) or with the button in the band header.")
            .translation(ClientConfig.key("containerItemScreens"))
            .define("containerItemScreens", true);
        RENAMING = builder
            .comment("Show the rename button in the band header, for renaming the shown backpack or container item.")
            .translation(ClientConfig.key("renaming"))
            .define("renaming", true);
        EQUIPMENT_SLOTS = builder
            .comment("Show Curios/Accessories slots in a panel on the left of the inventory.")
            .translation(ClientConfig.key("equipmentSlots"))
            .define("equipmentSlots", true);
        builder.pop();

        builder.comment("Integration with other mods. Turning one off makes Integrated UI act as if that mod wasn't installed;",
            "the mod itself keeps working on its own. Each has no effect if the mod isn't installed.")
            .translation(ClientConfig.key("compat")).push("compat");
        BACKPACKED = builder
            .comment("Backpacked: show equipped backpacks in the band, with Backpacked's slot unlocking.")
            .translation(ClientConfig.key("backpacked"))
            .define("backpacked", true);
        QUARK = builder
            .comment("Quark: show the worn Quark backpack in the band in place of Quark's own backpack screen, and give the",
                "integrated inventory Quark's inventory buttons and keybinds.")
            .translation(ClientConfig.key("quark"))
            .define("quark", true);
        CURIOS = builder
            .comment("Curios: show Curios slots in the equipment panel (when Accessories isn't used instead).")
            .translation(ClientConfig.key("curios"))
            .define("curios", true);
        ACCESSORIES = builder
            .comment("Accessories: show Accessories slots in the equipment panel. Takes precedence over Curios.")
            .translation(ClientConfig.key("accessories"))
            .define("accessories", true);
        SUPPLEMENTARIES = builder
            .comment("Supplementaries: open sacks in Supplementaries' own sack screen. When off, sacks are shown like any",
                "other container item.")
            .translation(ClientConfig.key("supplementaries"))
            .define("supplementaries", true);
        ARS_NOUVEAU = builder
            .comment("Ars Nouveau: right-clicking a spell book in the inventory opens its spell crafting screen",
                "(see also rightClickOpensSpellBooks in the client config).")
            .translation(ClientConfig.key("arsNouveau"))
            .define("arsNouveau", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ServerConfig() {}

    private static boolean isItemOrTagId(Object value)
    {
        if(!(value instanceof String id))
        {
            return false;
        }
        return ResourceLocation.tryParse(id.startsWith("#") ? id.substring(1) : id) != null;
    }

    /** The value, or its default while the config isn't loaded (on the client, outside of a world). */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value)
    {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
