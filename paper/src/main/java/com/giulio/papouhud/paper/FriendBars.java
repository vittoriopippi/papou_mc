package com.giulio.papouhud.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Per chi non ha la mod (giocatori Bedrock/Xbox via Geyser, o Java senza Papou HUD): una barra in
 * alto per ogni amico, con cuori, armatura, distanza e una freccia verso di lui, oppure la
 * dimensione in cui si trova. La barra stessa si svuota con la sua vita. {@code /papou} le
 * nasconde o le rimostra (scelta salvata nei dati del giocatore).
 */
final class FriendBars implements Listener, CommandExecutor {
	private static final int MAX_BARS = 4;
	/** La mod registra il suo canale subito dopo l'ingresso: prima di mostrare le barre aspettiamo un po'. */
	private static final long JOIN_GRACE_MILLIS = 5000;
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private final NamespacedKey hiddenKey;
	private final NamespacedKey hintKey;
	private final boolean includeSelf;
	/** Solo per i test: barre anche a chi ha la mod. */
	private final boolean forAll = Boolean.getBoolean("papouhud.debug.barsForAll");

	/** Chi guarda -> (amico -> barra), nell'ordine in cui sono apparse. */
	private final Map<UUID, Map<UUID, BossBar>> shown = new HashMap<>();
	private final Map<UUID, Long> joinedAt = new HashMap<>();

	FriendBars(Plugin plugin, boolean includeSelf) {
		this.hiddenKey = new NamespacedKey(plugin, "bars_hidden");
		this.hintKey = new NamespacedKey(plugin, "bars_hint_shown");
		this.includeSelf = includeSelf;
	}

	void update(Collection<? extends Player> online) {
		for (Player viewer : online) {
			List<Player> friends = wantsBars(viewer) ? friendsOf(viewer, online) : List.of();
			Map<UUID, BossBar> bars = shown.computeIfAbsent(viewer.getUniqueId(), id -> new LinkedHashMap<>());
			Set<UUID> keep = new HashSet<>();
			for (Player friend : friends) {
				keep.add(friend.getUniqueId());
				PlayersPacket.Entry data = PlayersPacket.Entry.of(friend);
				Component title = title(viewer, friend, data);
				float progress = (float) Math.clamp(data.health() / Math.max(1.0f, data.maxHealth()), 0.0, 1.0);
				BossBar.Color color = progress > 0.5f ? BossBar.Color.GREEN
						: progress > 0.25f ? BossBar.Color.YELLOW : BossBar.Color.RED;
				BossBar bar = bars.get(friend.getUniqueId());
				if (bar == null) {
					bar = BossBar.bossBar(title, progress, color, BossBar.Overlay.NOTCHED_10);
					bars.put(friend.getUniqueId(), bar);
					viewer.showBossBar(bar);
				} else {
					// Adventure manda l'aggiornamento al client solo se il valore cambia.
					bar.name(title);
					bar.progress(progress);
					bar.color(color);
				}
			}
			for (Iterator<Map.Entry<UUID, BossBar>> it = bars.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<UUID, BossBar> entry = it.next();
				if (!keep.contains(entry.getKey())) {
					viewer.hideBossBar(entry.getValue());
					it.remove();
				}
			}
			if (!friends.isEmpty()) {
				showHintOnce(viewer);
			}
		}
	}

	void hideAll(Collection<? extends Player> online) {
		for (Player viewer : online) {
			Map<UUID, BossBar> bars = shown.remove(viewer.getUniqueId());
			if (bars != null) {
				bars.values().forEach(viewer::hideBossBar);
			}
		}
		shown.clear();
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		joinedAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		joinedAt.remove(player.getUniqueId());
		Map<UUID, BossBar> bars = shown.remove(player.getUniqueId());
		if (bars != null) {
			bars.values().forEach(player::hideBossBar);
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage("Only players can use /papou.");
			return true;
		}
		boolean it = italian(player);
		if (hasMod(player) && !forAll) {
			player.sendMessage(Component.text(it
					? "Hai Papou HUD: i tuoi amici sono gia' nei pannelli in alto a sinistra."
					: "You have Papou HUD: your friends are already in the panels at the top left.",
					NamedTextColor.YELLOW));
			return true;
		}
		boolean hide = !isHidden(player);
		player.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BOOLEAN, hide);
		player.sendMessage(Component.text(hide
				? (it ? "Barre degli amici nascoste. Scrivi /papou per rimostrarle."
						: "Friend bars hidden. Type /papou to show them again.")
				: (it ? "Barre degli amici attive. Scrivi /papou per nasconderle."
						: "Friend bars shown. Type /papou to hide them."),
				NamedTextColor.YELLOW));
		return true;
	}

	private boolean wantsBars(Player viewer) {
		if (isHidden(viewer)) {
			return false;
		}
		if (forAll) {
			return true;
		}
		long joined = joinedAt.getOrDefault(viewer.getUniqueId(), 0L);
		return !hasMod(viewer) && System.currentTimeMillis() - joined >= JOIN_GRACE_MILLIS;
	}

	private boolean isHidden(Player player) {
		return player.getPersistentDataContainer().getOrDefault(hiddenKey, PersistentDataType.BOOLEAN, false);
	}

	static boolean hasMod(Player player) {
		return player.getListeningPluginChannels().contains(PlayersPacket.CHANNEL);
	}

	/** Prima chi e' nello stesso mondo, dal piu' vicino; poi gli altri per nome. */
	private List<Player> friendsOf(Player viewer, Collection<? extends Player> online) {
		Location here = viewer.getLocation();
		List<Player> friends = new ArrayList<>();
		for (Player other : online) {
			if (includeSelf || other != viewer) {
				friends.add(other);
			}
		}
		friends.sort(Comparator
				.comparing((Player p) -> p.getWorld() != viewer.getWorld())
				.thenComparingDouble(p -> p.getWorld() == viewer.getWorld() ? p.getLocation().distanceSquared(here) : 0)
				.thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
		return friends.size() > MAX_BARS ? friends.subList(0, MAX_BARS) : friends;
	}

	/** "Giulio  ❤ 9.5  armor 15  120 m ↗" oppure "... nel Nether". */
	private static Component title(Player viewer, Player friend, PlayersPacket.Entry data) {
		boolean it = italian(viewer);
		double hearts = Math.ceil(data.health()) / 2.0;
		String heartsText = hearts == Math.floor(hearts)
				? String.valueOf((long) hearts) : String.format(Locale.ROOT, "%.1f", hearts);

		Component where;
		if (friend.getWorld() == viewer.getWorld()) {
			where = Component.text(distance(viewer, friend), NamedTextColor.YELLOW);
		} else {
			where = Component.text(dimensionName(data.dimension(), it), NamedTextColor.GOLD);
		}
		return Component.text()
				.append(Component.text(friend.getName(), NamedTextColor.WHITE))
				.append(Component.text("  ❤ ", NamedTextColor.RED))
				.append(Component.text(heartsText, NamedTextColor.WHITE))
				.append(Component.text(it ? "  armatura " : "  armor ", NamedTextColor.GRAY))
				.append(Component.text(data.armorValue(), NamedTextColor.WHITE))
				.append(Component.text("  "))
				.append(where)
				.build();
	}

	static String distance(Player viewer, Player friend) {
		Location from = viewer.getLocation();
		Location to = friend.getLocation();
		return distanceText(from.distance(to), from.getYaw(), to.getX() - from.getX(), to.getZ() - from.getZ());
	}

	/** Distanza e freccia rispetto a dove guarda chi legge (su = davanti): "120 m ↗". */
	static String distanceText(double meters, float yaw, double dx, double dz) {
		String text = meters < 1000
				? Math.round(meters) + " m"
				: String.format(Locale.ROOT, "%.1f km", meters / 1000.0);
		if (dx * dx + dz * dz < 4.0) {
			return text;
		}
		// Yaw di Minecraft: 0 = sud, 90 = ovest; cresce girando a destra.
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double relative = ((targetYaw - yaw) % 360.0 + 540.0) % 360.0 - 180.0;
		int index = Math.floorMod((int) Math.round(relative / 45.0), 8);
		return text + " " + ARROWS[index];
	}

	static String dimensionName(String dimension, boolean italian) {
		return switch (dimension) {
			case "minecraft:overworld" -> italian ? "nell'Overworld" : "in the Overworld";
			case "minecraft:the_nether" -> italian ? "nel Nether" : "in the Nether";
			case "minecraft:the_end" -> italian ? "nell'End" : "in the End";
			default -> "in " + dimension.substring(dimension.indexOf(':') + 1);
		};
	}

	private void showHintOnce(Player viewer) {
		if (viewer.getPersistentDataContainer().getOrDefault(hintKey, PersistentDataType.BOOLEAN, false)) {
			return;
		}
		viewer.getPersistentDataContainer().set(hintKey, PersistentDataType.BOOLEAN, true);
		viewer.sendMessage(Component.text(italian(viewer)
				? "[Papou] Le barre in alto mostrano i tuoi amici: cuori, armatura e quanto sono lontani "
						+ "(la freccia indica dove sono). Scrivi /papou per nasconderle o rimostrarle."
				: "[Papou] The bars at the top show your friends: hearts, armor and how far away they are "
						+ "(the arrow points to them). Type /papou to hide or show them.",
				NamedTextColor.YELLOW));
	}

	private static boolean italian(Player player) {
		return player.locale().getLanguage().equals("it");
	}
}
