package com.lincstoel.integratedui.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Keys that work inside the integrated inventory: switching what the band shows. */
public final class ModKeys
{
    private static final String CATEGORY = "key.categories.integrated_ui";

    public static final KeyMapping PREVIOUS = new KeyMapping("key.integrated_ui.previous", KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_UP, CATEGORY);
    public static final KeyMapping NEXT = new KeyMapping("key.integrated_ui.next", KeyConflictContext.GUI,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PAGE_DOWN, CATEGORY);

    private ModKeys() {}

    public static void register(RegisterKeyMappingsEvent event)
    {
        event.register(PREVIOUS);
        event.register(NEXT);
    }

    /** Whether the key pressed in a screen is bound to {@code mapping}. */
    public static boolean matches(KeyMapping mapping, int keyCode, int scanCode)
    {
        return mapping.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode));
    }
}
