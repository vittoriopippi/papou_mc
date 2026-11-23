package com.giulio.papouhud;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Parte comune (client e server): registra i pacchetti (XP delle fornaci, elenco dei giocatori) e
 * il loro invio lato server. Gira anche nel server integrato di chi ospita il mondo, quindi basta
 * che la mod ce l'abbiano i giocatori; su un server dedicato va messa anche nella sua cartella mods.
 */
public class PapouHudCommon implements ModInitializer {
	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(FurnaceXpPayload.TYPE, FurnaceXpPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayersPayload.TYPE, PlayersPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(FurnaceXpSync::tick);
		ServerTickEvents.END_SERVER_TICK.register(PlayersSync::tick);
	}
}
