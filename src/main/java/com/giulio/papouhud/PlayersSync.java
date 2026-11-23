package com.giulio.papouhud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Lato server (server dedicato con la mod, o il server integrato di chi ospita): due volte al
 * secondo manda a chi ha Papou HUD vita, armatura e posizione di tutti gli altri giocatori online.
 * Il client sa da solo solo dei giocatori entro la distanza di caricamento; con questo i pannelli
 * restano anche per chi e' lontano o in un'altra dimensione.
 */
final class PlayersSync {
	static final int INTERVAL_TICKS = 10;
	/**
	 * Solo per i test: con -Dpapouhud.debug.includeSelf=true manda a ognuno anche i propri dati,
	 * cosi' basta un giocatore per provarlo. Letta a ogni invio, il test la accende e la spegne.
	 */
	static final String INCLUDE_SELF_PROPERTY = "papouhud.debug.includeSelf";

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Chi ha gia' ricevuto un elenco vuoto: non serve ripeterlo ogni mezzo secondo. */
	private static final Set<UUID> SENT_EMPTY = new HashSet<>();
	private static int tickCounter;

	private PlayersSync() {
	}

	static void tick(MinecraftServer server) {
		if (++tickCounter < INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		List<ServerPlayer> online = server.getPlayerList().getPlayers();
		boolean includeSelf = Boolean.getBoolean(INCLUDE_SELF_PROPERTY);
		List<PlayersPayload.Entry> all = null;
		for (ServerPlayer receiver : online) {
			if (!ServerPlayNetworking.canSend(receiver, PlayersPayload.TYPE)) {
				continue;
			}
			if (all == null) {
				all = new ArrayList<>(online.size());
				for (ServerPlayer player : online) {
					all.add(entry(player));
				}
			}
			List<PlayersPayload.Entry> others = new ArrayList<>(all.size());
			for (PlayersPayload.Entry entry : all) {
				if (includeSelf || !entry.uuid().equals(receiver.getUUID())) {
					others.add(entry);
				}
			}
			// Un elenco vuoto basta mandarlo una volta (svuota i pannelli di chi e' appena uscito).
			boolean send = true;
			if (others.isEmpty()) {
				send = SENT_EMPTY.add(receiver.getUUID());
			} else {
				SENT_EMPTY.remove(receiver.getUUID());
			}
			if (send) {
				ServerPlayNetworking.send(receiver, new PlayersPayload(others));
			}
		}
		if (SENT_EMPTY.size() > online.size()) {
			SENT_EMPTY.retainAll(online.stream().map(ServerPlayer::getUUID).toList());
		}
	}

	private static PlayersPayload.Entry entry(ServerPlayer player) {
		List<PlayersPayload.Armor> armor = new ArrayList<>(ARMOR_SLOTS.length);
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			armor.add(PlayersPayload.Armor.of(player.getItemBySlot(slot)));
		}
		return new PlayersPayload.Entry(player.getUUID(), player.getName().getString(),
				player.level().dimension().identifier().toString(),
				player.getX(), player.getY(), player.getZ(),
				player.getHealth(), player.getMaxHealth(), player.getArmorValue(), armor);
	}
}
