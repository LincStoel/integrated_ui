package com.lincstoel.integratedui;

import com.lincstoel.integratedui.client.ContainerItemClient;
import com.lincstoel.integratedui.compat.aether.AetherClient;
import com.lincstoel.integratedui.compat.arsnouveau.ArsNouveauClient;
import com.lincstoel.integratedui.compat.arsnouveau.ArsNouveauCompat;
import com.lincstoel.integratedui.compat.CompatGuard;
import com.lincstoel.integratedui.compat.backpacked.BackpackedClient;
import com.lincstoel.integratedui.compat.curios.CuriosEvents;
import com.lincstoel.integratedui.compat.quark.QuarkClientCompat;
import com.lincstoel.integratedui.compat.supplementaries.SupplementariesClient;
import com.lincstoel.integratedui.compat.supplementaries.SupplementariesCompat;
import com.lincstoel.integratedui.config.ClientConfig;
import com.lincstoel.integratedui.config.ServerConfig;
import com.lincstoel.integratedui.menu.ModMenus;
import com.lincstoel.integratedui.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Shows backpacks (Backpacked, Quark), carried container items and any Curios/Accessories slots directly inside the
 * survival inventory screen. Each feature and each mod integration can be turned off in the config.
 * <p>
 * The approach (replacing the inventory menu with an {@link net.minecraft.world.inventory.InventoryMenu}
 * subclass that carries extra backpack slots) is inspired by Quark's backpack by Vazkii, WireSegal et al.
 */
@Mod(IntegratedUI.MOD_ID)
public class IntegratedUI
{
    public static final String MOD_ID = "integrated_ui";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Checked through the loading mod list, so it is also safe to call before mods are constructed. */
    private static final boolean BACKPACKED_LOADED = LoadingModList.get().getModFileById("backpacked") != null;
    private static final boolean QUARK_LOADED = LoadingModList.get().getModFileById("quark") != null;
    private static final boolean JEI_LOADED = LoadingModList.get().getModFileById("jei") != null;
    private static final boolean ARS_NOUVEAU_LOADED = LoadingModList.get().getModFileById("ars_nouveau") != null;
    private static final boolean SUPPLEMENTARIES_LOADED = LoadingModList.get().getModFileById("supplementaries") != null;
    private static final boolean AETHER_LOADED = LoadingModList.get().getModFileById("aether") != null;
    private static final boolean CURIOS_LOADED = LoadingModList.get().getModFileById("curios") != null;
    private static final boolean ACCESSORIES_LOADED = LoadingModList.get().getModFileById("accessories") != null;
    private static final boolean MOUSE_TWEAKS_LOADED = LoadingModList.get().getModFileById("mousetweaks") != null;

    public IntegratedUI(IEventBus modBus, ModContainer container)
    {
        ModMenus.REGISTER.register(modBus);
        modBus.addListener(ModNetwork::register);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        if(FMLEnvironment.dist.isClient())
        {
            ContainerItemClient.register();
            if(BACKPACKED_LOADED)
            {
                BackpackedClient.register();
            }
            if(QUARK_LOADED)
            {
                QuarkClientCompat.checkMixinTargets();
            }
        }
        if(CURIOS_LOADED)
        {
            NeoForge.EVENT_BUS.addListener(CuriosEvents::onSlotModifiersUpdated);
        }
        if(ARS_NOUVEAU_LOADED)
        {
            NeoForge.EVENT_BUS.addListener(ArsNouveauCompat::onLoggedOut);
            if(FMLEnvironment.dist.isClient())
            {
                ArsNouveauClient.register();
            }
        }
        if(SUPPLEMENTARIES_LOADED)
        {
            SupplementariesCompat.register(modBus);
            if(FMLEnvironment.dist.isClient())
            {
                SupplementariesClient.register(modBus);
            }
        }
        if(AETHER_LOADED && FMLEnvironment.dist.isClient())
        {
            AetherClient.register();
        }
    }

    // Whether a mod is installed: classes in its compat package may only be used when it is. Also used for
    // registration, and for staying out of the way of the mod while its integration is turned off.

    public static boolean isBackpackedLoaded()
    {
        return BACKPACKED_LOADED;
    }

    public static boolean isQuarkLoaded()
    {
        return QUARK_LOADED;
    }

    public static boolean isArsNouveauLoaded()
    {
        return ARS_NOUVEAU_LOADED;
    }

    public static boolean isSupplementariesLoaded()
    {
        return SUPPLEMENTARIES_LOADED;
    }

    public static boolean isAetherLoaded()
    {
        return AETHER_LOADED;
    }

    public static boolean isJeiLoaded()
    {
        return JEI_LOADED;
    }

    public static boolean isCuriosLoaded()
    {
        return CURIOS_LOADED;
    }

    public static boolean isAccessoriesLoaded()
    {
        return ACCESSORIES_LOADED;
    }

    public static boolean isMouseTweaksLoaded()
    {
        return MOUSE_TWEAKS_LOADED;
    }

    // Whether a mod is installed and its integration turned on in the config (see ServerConfig and ClientConfig), and
    // still working (see CompatGuard). Without it, this mod acts as if that mod wasn't installed.

    /** Whether equipped Backpacked backpacks are shown in the band. */
    public static boolean isBackpackedEnabled()
    {
        return BACKPACKED_LOADED && ServerConfig.get(ServerConfig.BACKPACKED) && CompatGuard.isWorking(CompatGuard.BACKPACKED);
    }

    /** Whether the worn Quark backpack is shown in the band, and our screens get Quark's inventory buttons. */
    public static boolean isQuarkEnabled()
    {
        return QUARK_LOADED && ServerConfig.get(ServerConfig.QUARK) && CompatGuard.isWorking(CompatGuard.QUARK);
    }

    /** Whether spell books can be opened from the inventory. */
    public static boolean isArsNouveauEnabled()
    {
        return ARS_NOUVEAU_LOADED && ServerConfig.get(ServerConfig.ARS_NOUVEAU) && CompatGuard.isWorking(CompatGuard.ARS_NOUVEAU);
    }

    /** Whether sacks open in Supplementaries' sack screen. */
    public static boolean isSupplementariesEnabled()
    {
        return SUPPLEMENTARIES_LOADED && ServerConfig.get(ServerConfig.SUPPLEMENTARIES) && CompatGuard.isWorking(CompatGuard.SUPPLEMENTARIES);
    }

    public static boolean isCuriosEnabled()
    {
        return CURIOS_LOADED && ServerConfig.get(ServerConfig.CURIOS) && CompatGuard.isWorking(CompatGuard.CURIOS);
    }

    public static boolean isAccessoriesEnabled()
    {
        return ACCESSORIES_LOADED && ServerConfig.get(ServerConfig.ACCESSORIES) && CompatGuard.isWorking(CompatGuard.ACCESSORIES);
    }

    /** Client side: whether our inventory screens get the Aether's accessories button. */
    public static boolean isAetherEnabled()
    {
        return AETHER_LOADED && ClientConfig.get(ClientConfig.AETHER) && CompatGuard.isWorking(CompatGuard.AETHER);
    }

    /** Client side: JEI replaces the recipe book, so our screens hide it when this is true. */
    public static boolean isJeiEnabled()
    {
        return JEI_LOADED && ClientConfig.get(ClientConfig.JEI);
    }

    public static ResourceLocation rl(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
