package com.lincstoel.integratedui.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork
{
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(OpenBackpackInventoryPayload.TYPE, OpenBackpackInventoryPayload.STREAM_CODEC, OpenBackpackInventoryPayload::handle);
        registrar.playToServer(RenameBackpackPayload.TYPE, RenameBackpackPayload.STREAM_CODEC, RenameBackpackPayload::handle);
        registrar.playToClient(OpenVanillaInventoryPayload.TYPE, OpenVanillaInventoryPayload.STREAM_CODEC, OpenVanillaInventoryPayload::handle);
    }
}
