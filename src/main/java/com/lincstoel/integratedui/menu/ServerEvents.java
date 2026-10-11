package com.lincstoel.integratedui.menu;

import com.lincstoel.integratedui.IntegratedUI;
import com.lincstoel.integratedui.network.ContainerItemsPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

/** Keeps an open integrated inventory in step with what the band can show, and drops what is no longer needed. */
@EventBusSubscriber(modid = IntegratedUI.MOD_ID)
public final class ServerEvents
{
    private ServerEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event)
    {
        if(event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof IntegratedInventoryMenu menu
            && menu.isUpdateDue(player) && menu.updateTargets(player))
        {
            IntegratedInventoryMenu.open(player, Navigation.CURRENT);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        BandTargets.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ContainerItems.findKeepingContents(event.getServer().overworld());
        ContainerItems.logDetected();
    }

    /** On joining, and after datapacks (and so loot tables) reload: tells clients which items keep their contents. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event)
    {
        if(event.getPlayer() == null)
        {
            ContainerItems.findKeepingContents(event.getPlayerList().getServer().overworld());
        }
        ContainerItemsPayload payload = new ContainerItemsPayload(List.copyOf(ContainerItems.getKeepingContents()));
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event)
    {
        ContainerItems.clearItemInfo();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        ContainerItems.clearProbes();
        ContainerItems.clearItemInfo();
        ContainerItems.setKeepingContents(Set.of());
        BandTargets.forgetAll();
    }
}
