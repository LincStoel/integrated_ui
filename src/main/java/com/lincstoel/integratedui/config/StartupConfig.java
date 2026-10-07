package com.lincstoel.integratedui.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.lincstoel.integratedui.IntegratedUI;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Options that must be known before Quark's modules are configured. Quark (via Zeta) applies its module
 * toggles while mods are still being constructed, so this file is read lazily on first access instead of
 * going through a NeoForge config spec, which would only be loaded once this mod is constructed.
 */
public final class StartupConfig
{
    private static final String FILE_NAME = IntegratedUI.MOD_ID + "-startup.toml";
    private static final String DISABLE_QUARK_BACKPACK = "disableQuarkBackpack";

    private static volatile Boolean disableQuarkBackpack;

    private StartupConfig() {}

    public static boolean disableQuarkBackpack()
    {
        Boolean value = disableQuarkBackpack;
        if(value == null)
        {
            value = load();
        }
        return value;
    }

    private static synchronized boolean load()
    {
        if(disableQuarkBackpack != null)
        {
            return disableQuarkBackpack;
        }
        boolean value = true;
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        try(CommentedFileConfig config = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build())
        {
            config.load();
            if(config.get(DISABLE_QUARK_BACKPACK) instanceof Boolean b)
            {
                value = b;
            }
            else
            {
                config.set(DISABLE_QUARK_BACKPACK, true);
                config.setComment(DISABLE_QUARK_BACKPACK, """
                     Disables Quark's own backpack (the Oddities "Backpack" module), since Backpacked backpacks now
                     appear in the inventory screen instead. This overrides the setting in Quark's config.
                     Set to false to let Quark's config decide.""");
                config.save();
            }
        }
        catch(Exception e)
        {
            IntegratedUI.LOGGER.error("Failed to read {}, using defaults", FILE_NAME, e);
        }
        disableQuarkBackpack = value;
        return value;
    }
}
