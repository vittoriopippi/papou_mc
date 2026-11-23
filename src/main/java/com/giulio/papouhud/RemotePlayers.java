package com.giulio.papouhud;

import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;

/**
 * Lato client: l'ultimo elenco di giocatori mandato dal server (vedi {@link PlayersSync} e il
 * plugin Paper). Vuoto su server senza Papou HUD: li' i pannelli mostrano solo chi e' vicino.
 */
final class RemotePlayers {
	/** Il server ne manda uno ogni mezzo secondo: se tace da 3 secondi, l'elenco non vale piu'. */
	private static final long STALE_MILLIS = 3000;

	private static List<PlayersPayload.Entry> latest = List.of();
	private static long receivedAt;

	private RemotePlayers() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlayersPayload.TYPE, (payload, context) -> {
			if (payload.version() == PlayersPayload.VERSION) {
				set(payload.players());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> latest = List.of());
	}

	static void set(List<PlayersPayload.Entry> players) {
		latest = List.copyOf(players);
		receivedAt = Util.getMillis();
	}

	static List<PlayersPayload.Entry> current() {
		return Util.getMillis() - receivedAt < STALE_MILLIS ? latest : List.of();
	}
}
