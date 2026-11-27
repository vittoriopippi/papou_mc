package com.giulio.papouhud;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;

/**
 * Lato client: riempie le schede dell'inventario creativo appena si entra in un server.
 *
 * <p>Il gioco le riempie solo quando si apre l'inventario creativo, quindi in sopravvivenza su un
 * server restano vuote. Inventory Sorter, sui server che non hanno la sua parte server (come
 * Paper), ordina dal client, e con "Sort by: Category" usa proprio l'ordine di queste schede: con le
 * schede vuote ogni oggetto finisce a pari merito e l'ordine diventa solo alfabetico.
 *
 * <p>In singleplayer non serve: le schede le prepara il server integrato.
 */
final class CreativeTabs {
	private static final Logger LOGGER = LogUtils.getLogger();

	private CreativeTabs() {
	}

	static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!client.hasSingleplayerServer()) {
				build(handler);
			}
		});
	}

	/** Come fa l'inventario creativo quando si apre (senza la scheda per operatori). */
	static void build(ClientPacketListener handler) {
		try {
			CreativeModeTabs.tryRebuildTabContents(handler.enabledFeatures(), false, handler.registryAccess());
		} catch (RuntimeException e) {
			LOGGER.warn("Papou HUD: couldn't fill the creative tabs", e);
		}
	}
}
