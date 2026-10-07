package com.lincstoel.integratedui.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig
{
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REPLACE_INVENTORY_SCREEN;
    public static final ModConfigSpec.IntValue MAX_VISIBLE_ROWS;
    public static final ModConfigSpec.BooleanValue EQUIPMENT_PANEL_OPEN_BY_DEFAULT;

    static
    {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REPLACE_INVENTORY_SCREEN = builder
            .comment("Show the equipped Backpacked backpack in the player inventory screen.")
            .define("replaceInventoryScreen", true);
        MAX_VISIBLE_ROWS = builder
            .comment("The maximum number of backpack rows shown at once. Larger backpacks can be scrolled.",
                "Fewer rows are shown if the screen is too short.")
            .defineInRange("maxVisibleRows", 3, 1, 11);
        EQUIPMENT_PANEL_OPEN_BY_DEFAULT = builder
            .comment("Whether the Curios/Accessories slot panel on the left of the inventory starts open.")
            .define("equipmentPanelOpenByDefault", true);
        SPEC = builder.build();
    }

    private ClientConfig() {}
}
