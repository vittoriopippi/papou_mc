package com.giulio.papouhud.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Papou HUD sui server Paper (Minecraft Cross), dove la mod Fabric non puo' girare:
 * <ul>
 *   <li>a chi ha la mod manda lo stesso pacchetto {@code papouhud:players} del server Fabric, due
 *       volte al secondo, cosi' i pannelli mostrano tutti, anche lontani o in un'altra dimensione;</li>
 *   <li>a tutti gli altri (Bedrock/Xbox via Geyser, Java senza mod) mostra le barre degli amici
 *       ({@link FriendBars}).</li>
 * </ul>
 */
public final class PapouHudPaper extends JavaPlugin {
	private static final long PACKET_INTERVAL_TICKS = 10;
	private static final long BAR_INTERVAL_TICKS = 5;

	/** Solo per i test: manda a ognuno anche i propri dati, cosi' basta un giocatore per provarlo. */
	private final boolean includeSelf = Boolean.getBoolean("papouhud.debug.includeSelf");
	/** Chi ha gia' ricevuto un elenco vuoto: non serve ripeterlo ogni mezzo secondo. */
	private final Set<UUID> sentEmpty = new HashSet<>();
	private FriendBars bars;

	@Override
	public void onEnable() {
		getServer().getMessenger().registerOutgoingPluginChannel(this, PlayersPacket.CHANNEL);

		bars = new FriendBars(this, includeSelf);
		getServer().getPluginManager().registerEvents(bars, this);
		PluginCommand command = getCommand("papou");
		if (command != null) {
			command.setExecutor(bars);
		}

		getServer().getScheduler().runTaskTimer(this, this::sendPlayers, PACKET_INTERVAL_TICKS, PACKET_INTERVAL_TICKS);
		getServer().getScheduler().runTaskTimer(this,
				() -> bars.update(getServer().getOnlinePlayers()), BAR_INTERVAL_TICKS, BAR_INTERVAL_TICKS);
	}

	@Override
	public void onDisable() {
		if (bars != null) {
			bars.hideAll(getServer().getOnlinePlayers());
		}
	}

	private void sendPlayers() {
		Collection<? extends Player> online = getServer().getOnlinePlayers();
		List<PlayersPacket.Entry> all = null;
		for (Player receiver : online) {
			if (!FriendBars.hasMod(receiver)) {
				continue;
			}
			if (all == null) {
				all = new ArrayList<>(online.size());
				for (Player player : online) {
					all.add(PlayersPacket.Entry.of(player));
				}
			}
			List<PlayersPacket.Entry> others = new ArrayList<>(all.size());
			for (PlayersPacket.Entry entry : all) {
				if (includeSelf || !entry.uuid().equals(receiver.getUniqueId())) {
					others.add(entry);
				}
			}
			// Un elenco vuoto basta mandarlo una volta (svuota i pannelli di chi e' appena uscito).
			boolean send = true;
			if (others.isEmpty()) {
				send = sentEmpty.add(receiver.getUniqueId());
			} else {
				sentEmpty.remove(receiver.getUniqueId());
			}
			if (send) {
				receiver.sendPluginMessage(this, PlayersPacket.CHANNEL, PlayersPacket.write(others));
			}
		}
		if (sentEmpty.size() > online.size()) {
			sentEmpty.retainAll(online.stream().map(Player::getUniqueId).toList());
		}
	}
}
