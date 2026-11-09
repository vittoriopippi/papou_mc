package com.giulio.papouhud;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Parte comune (client e server): registra il pacchetto dell'XP delle fornaci e l'invio lato server.
 * Gira anche nel server integrato di chi ospita il mondo, quindi basta che la mod ce l'abbiano i giocatori.
 */
public class PapouHudCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(FurnaceXpPayload.TYPE, FurnaceXpPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(FurnaceXpSync::tick);
	}
}
