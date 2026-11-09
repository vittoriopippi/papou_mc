package com.giulio.papouhud;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;

/**
 * Dopo la morte: un teschio sulla locator bar (la barra che indica gli altri giocatori) punta al
 * luogo della morte per 5 minuti, con un messaggio in chat alla rinascita e poi ogni minuto.
 * Tutto lato client: funziona su qualsiasi server. Il tempo scorre come quello di sparizione degli
 * oggetti a terra, quindi si ferma quando il gioco e' in pausa in singleplayer.
 */
final class DeathMarker {
	static final int DURATION_TICKS = 5 * 60 * 20;
	static final int MESSAGE_INTERVAL_TICKS = 60 * 20;

	private static final UUID WAYPOINT_ID =
			UUID.nameUUIDFromBytes("papouhud:death_marker".getBytes(StandardCharsets.UTF_8));
	/** assets/papouhud/waypoint_style/death.json -> sprite hud/locator_bar_dot/death.png */
	private static final ResourceKey<WaypointStyleAsset> STYLE =
			ResourceKey.create(WaypointStyleAssets.ROOT_ID, Identifier.fromNamespaceAndPath("papouhud", "death"));
	/**
	 * Bianco opaco = lo sprite resta con i suoi colori. La locator bar usa questo valore come ARGB
	 * completo: senza il canale alfa (0xFFFFFF) il teschio sarebbe del tutto trasparente.
	 */
	private static final int TINT = 0xFFFFFFFF;

	/** Stato corrente, letto anche dal game test. {@code deathPos == null}: nessun segnalino attivo. */
	static GlobalPos deathPos;
	static int elapsedTicks;

	private static LocalPlayer lastPlayer;
	private static TrackedWaypoint waypoint;
	/** Il gestore dei waypoint della connessione in cui il teschio e' mostrato, o null se nascosto. */
	private static ClientWaypointManager shownIn;

	private DeathMarker() {
	}

	static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player != lastPlayer) {
			// Alla rinascita il gioco crea un nuovo LocalPlayer: se il precedente era morto, e' una morte.
			LocalPlayer previous = lastPlayer;
			lastPlayer = player;
			if (player == null) {
				forget();
				return;
			}
			if (previous != null && previous.isDeadOrDying() && PapouConfig.get().deathMarkerEnabled) {
				start(player, previous);
			}
		}
		if (player == null || deathPos == null) {
			return;
		}

		if (!mc.isPaused()) {
			elapsedTicks++;
			if (elapsedTicks >= DURATION_TICKS) {
				player.sendSystemMessage(message(Component.translatable("papouhud.death.expired")));
				hide();
				deathPos = null;
				return;
			}
			if (elapsedTicks % MESSAGE_INTERVAL_TICKS == 0) {
				remind(player);
			}
		}
		sync(mc, player);
	}

	private static void start(LocalPlayer player, LocalPlayer previous) {
		hide();
		Optional<GlobalPos> lastDeath = player.getLastDeathLocation();
		deathPos = lastDeath.orElseGet(() -> GlobalPos.of(previous.level().dimension(), previous.blockPosition()));
		elapsedTicks = 0;

		Waypoint.Icon icon = new Waypoint.Icon();
		icon.style = STYLE;
		icon.color = Optional.of(TINT);
		waypoint = TrackedWaypoint.setPosition(WAYPOINT_ID, icon, deathPos.pos());

		player.sendSystemMessage(message(Component.translatable("papouhud.death.died",
				coordinates(deathPos.pos()), dimensionName(deathPos.dimension()), DURATION_TICKS / MESSAGE_INTERVAL_TICKS)));
	}

	private static void remind(LocalPlayer player) {
		int minutesLeft = (DURATION_TICKS - elapsedTicks + MESSAGE_INTERVAL_TICKS - 1) / MESSAGE_INTERVAL_TICKS;
		BlockPos pos = deathPos.pos();
		Component text;
		if (player.level().dimension() == deathPos.dimension()) {
			int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(pos)));
			text = Component.translatable("papouhud.death.reminder", distance, coordinates(pos), minutesLeft);
		} else {
			text = Component.translatable("papouhud.death.reminder_other_dimension",
					dimensionName(deathPos.dimension()), coordinates(pos), minutesLeft);
		}
		player.sendSystemMessage(message(text));
	}

	/** Mostra il teschio solo nella dimensione in cui si e' morti (altrove la direzione non avrebbe senso). */
	private static void sync(Minecraft mc, LocalPlayer player) {
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return;
		}
		ClientWaypointManager manager = connection.getWaypointManager();
		boolean show = player.level().dimension() == deathPos.dimension() && !player.isDeadOrDying();
		if (show && shownIn != manager) {
			hide();
			manager.trackWaypoint(waypoint);
			shownIn = manager;
		} else if (!show) {
			hide();
		}
	}

	private static void hide() {
		if (shownIn != null && waypoint != null) {
			shownIn.untrackWaypoint(waypoint);
		}
		shownIn = null;
	}

	/** Uscita dal mondo: la connessione (e i suoi waypoint) non esiste piu'. */
	private static void forget() {
		shownIn = null;
		waypoint = null;
		deathPos = null;
	}

	private static Component message(Component text) {
		return Component.literal("☠ ").append(text).withStyle(ChatFormatting.GRAY);
	}

	private static String coordinates(BlockPos pos) {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
	}

	private static Component dimensionName(ResourceKey<Level> dimension) {
		Identifier id = dimension.identifier();
		return Component.translatableWithFallback(
				"papouhud.dimension." + id.getNamespace() + "." + id.getPath(), id.toString());
	}
}
