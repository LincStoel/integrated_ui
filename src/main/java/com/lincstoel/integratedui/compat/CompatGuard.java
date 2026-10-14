package com.lincstoel.integratedui.compat;

import com.lincstoel.integratedui.IntegratedUI;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Keeps another mod's update from crashing the game. Calls into a mod's internals (anything outside its API) go
 * through here; if one no longer links because the mod changed a class, method or field, that mod's integration is
 * turned off for the rest of the session with one logged error, and Integrated UI carries on as if the mod weren't
 * installed (see the isXEnabled checks in IntegratedUI).
 */
public final class CompatGuard
{
    public static final String BACKPACKED = "backpacked";
    public static final String QUARK = "quark";
    public static final String CURIOS = "curios";
    public static final String ACCESSORIES = "accessories";
    public static final String SUPPLEMENTARIES = "supplementaries";
    public static final String ARS_NOUVEAU = "ars_nouveau";
    public static final String CREATE = "create";
    public static final String AETHER = "aether";
    public static final String FIELD_GUIDE = "fieldguide";
    public static final String FTB_QUESTS = "ftbquests";
    public static final String JEI = "jei";

    private static final Set<String> BROKEN = ConcurrentHashMap.newKeySet();

    private CompatGuard() {}

    /** Whether the integration with the mod still works (it may not be installed at all). */
    public static boolean isWorking(String modId)
    {
        return !BROKEN.contains(modId);
    }

    /** Turns the integration with the mod off, as something it relies on is missing. */
    public static void markBroken(String modId, Throwable cause)
    {
        if(BROKEN.add(modId))
        {
            IntegratedUI.LOGGER.error("Turned off the {} integration: the installed version of {} doesn't have what Integrated UI expects. "
                + "Integrated UI keeps working without it; look for an update of either mod.", modId, modId, cause);
        }
    }

    /** Runs {@code call}, unless the integration is off or the call fails to link, which turns it off. */
    public static void run(String modId, Runnable call)
    {
        if(isWorking(modId))
        {
            try
            {
                call.run();
            }
            catch(LinkageError e)
            {
                markBroken(modId, e);
            }
        }
    }

    /** {@code call}'s result, or {@code fallback} if the integration is off or the call fails to link (which turns it off). */
    public static <T> T get(String modId, Supplier<T> call, T fallback)
    {
        if(!isWorking(modId))
        {
            return fallback;
        }
        try
        {
            return call.get();
        }
        catch(LinkageError e)
        {
            markBroken(modId, e);
            return fallback;
        }
    }

    /** As {@link #get}, for a condition: false if the integration is off or the call fails to link. */
    public static boolean test(String modId, BooleanSupplier call)
    {
        if(!isWorking(modId))
        {
            return false;
        }
        try
        {
            return call.getAsBoolean();
        }
        catch(LinkageError e)
        {
            markBroken(modId, e);
            return false;
        }
    }
}
