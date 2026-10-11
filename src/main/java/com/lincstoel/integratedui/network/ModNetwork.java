package com.lincstoel.integratedui.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork
{
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("9");
        registrar.playToServer(OpenInventoryPayload.TYPE, OpenInventoryPayload.STREAM_CODEC, OpenInventoryPayload::handle);
        registrar.playToServer(RenamePayload.TYPE, RenamePayload.STREAM_CODEC, RenamePayload::handle);
        registrar.playToServer(OpenShulkerBoxPayload.TYPE, OpenShulkerBoxPayload.STREAM_CODEC, OpenShulkerBoxPayload::handle);
        registrar.playToServer(OpenSpellBookPayload.TYPE, OpenSpellBookPayload.STREAM_CODEC, OpenSpellBookPayload::handle);
        registrar.playToServer(OpenContainerItemPayload.TYPE, OpenContainerItemPayload.STREAM_CODEC, OpenContainerItemPayload::handle);
        registrar.playToServer(CloseSpellBookPayload.TYPE, CloseSpellBookPayload.STREAM_CODEC, CloseSpellBookPayload::handle);
        registrar.playToClient(SpellBookOpenedPayload.TYPE, SpellBookOpenedPayload.STREAM_CODEC, SpellBookOpenedPayload::handle);
        registrar.playToClient(ContainerItemsPayload.TYPE, ContainerItemsPayload.STREAM_CODEC, ContainerItemsPayload::handle);
    }
}
