package com.giulio.papouhud;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import net.minecraft.world.waypoints.Waypoint;
import net.minecraft.world.waypoints.WaypointStyleAsset;
import net.minecraft.world.waypoints.WaypointStyleAssets;

/**
 * Dopo ogni morte: un teschio sulla locator bar (la barra che indica gli altri giocatori) punta al
 * luogo della morte, con messaggi in chat. Una morte = un teschio di un altro colore, fino a 6.
 *
 * <p>Il teschio dura quanto gli oggetti caduti. Nel gioco un oggetto a terra sparisce dopo 5 minuti
 * (6000 tick) passati in un chunk "simulato", cioe' entro la distanza di simulazione del server da un
 * giocatore (in chunk, diagonali comprese): lontano da tutti, o a gioco in pausa, il suo tempo si
 * ferma. Qui il conto avanza solo quando il giocatore (o un altro giocatore che il server con Papou HUD
 * ci fa conoscere, vedi RemotePlayers) e' abbastanza vicino, e conta anche l'attesa sulla schermata di
 * morte, quando il giocatore morto e' ancora li'. Il teschio sparisce anche quando gli oggetti non ci
 * sono piu': se si e' a pochi blocchi dal punto e per 2 secondi non si vede nessun oggetto li' intorno.
 *
 * <p>Tutto lato client: funziona su qualsiasi server. I teschi sono salvati per server o mondo in
 * config/papouhud-deaths.json, cosi' restano anche uscendo dal gioco (gli oggetti restano anche loro).
 */
final class DeathMarker {
	static final int DURATION_TICKS = 5 * 60 * 20;
	static final int MESSAGE_INTERVAL_TICKS = 60 * 20;
	/** A meno di questi blocchi dal punto si controlla se gli oggetti ci sono ancora. */
	static final double COLLECT_CHECK_RANGE = 6.0;
	/** Raggio, in blocchi, intorno al punto in cui cercare gli oggetti caduti. */
	static final double ITEM_SEARCH_RADIUS = 8.0;
	/** Per quanti tick di fila non si deve vedere nessun oggetto prima di dire che sono stati raccolti. */
	static final int COLLECT_CONFIRM_TICKS = 40;
	/** Ogni quanto salvare il tempo trascorso mentre i teschi invecchiano. */
	private static final int SAVE_INTERVAL_TICKS = 10 * 20;

	/**
	 * Colori dei teschi, nell'ordine in cui vengono usati. La locator bar usa il colore come ARGB
	 * completo (senza il canale alfa il teschio sarebbe trasparente) e lo moltiplica con lo sprite,
	 * che e' bianco con il contorno nero.
	 */
	enum Color {
		RED(0xFFFF5555, ChatFormatting.RED),
		YELLOW(0xFFFFFF55, ChatFormatting.YELLOW),
		AQUA(0xFF55FFFF, ChatFormatting.AQUA),
		PURPLE(0xFFFF55FF, ChatFormatting.LIGHT_PURPLE),
		GREEN(0xFF55FF55, ChatFormatting.GREEN),
		ORANGE(0xFFFFAA00, ChatFormatting.GOLD);

		final int argb;
		final ChatFormatting chat;

		Color(int argb, ChatFormatting chat) {
			this.argb = argb;
			this.chat = chat;
		}

		/** "☠ rosso" nel colore del teschio. */
		Component label() {
			return Component.literal("☠ ").append(Component.translatable("papouhud.death.color." + name().toLowerCase()))
					.withStyle(chat);
		}

		UUID waypointId() {
			return UUID.nameUUIDFromBytes(("papouhud:death_marker:" + name()).getBytes(StandardCharsets.UTF_8));
		}
	}

	static final int MAX_MARKERS = Color.values().length;

	/** Un punto di morte. */
	static final class Marker {
		final GlobalPos pos;
		final Color color;
		/** Tick passati con il punto simulato: a {@link #DURATION_TICKS} gli oggetti sono spariti. */
		int agedTicks;
		/** Tick di fila in cui, da vicino, non si e' visto nessun oggetto intorno al punto. */
		int emptyTicks;
		final TrackedWaypoint waypoint;

		Marker(GlobalPos pos, Color color, int agedTicks) {
			this.pos = pos;
			this.color = color;
			this.agedTicks = agedTicks;
			Waypoint.Icon icon = new Waypoint.Icon();
			icon.style = STYLE;
			icon.color = Optional.of(color.argb);
			this.waypoint = TrackedWaypoint.setPosition(color.waypointId(), icon, pos.pos());
		}
	}

	/** assets/papouhud/waypoint_style/death.json -> sprite hud/locator_bar_dot/death.png */
	private static final ResourceKey<WaypointStyleAsset> STYLE =
			ResourceKey.create(WaypointStyleAssets.ROOT_ID, Identifier.fromNamespaceAndPath("papouhud", "death"));

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("papouhud-deaths.json");
	private static final Type FILE_TYPE = new TypeToken<Map<String, List<SavedMarker>>>() { }.getType();

	/** Teschi attivi, dal piu' vecchio (letti anche dal game test). */
	static final List<Marker> markers = new ArrayList<>();

	private static LocalPlayer lastPlayer;
	/** Tick passati sulla schermata di morte (il giocatore morto tiene il punto simulato). */
	private static int deadTicks;
	/** Server o mondo dei teschi caricati, per salvarli al posto giusto; null fuori dal gioco. */
	private static String worldKey;
	private static int ticksSinceSave;
	/** Il gestore dei waypoint della connessione in cui sono mostrati i teschi in {@link #shown}. */
	private static ClientWaypointManager shownIn;
	private static final Set<Marker> shown = new HashSet<>();

	private DeathMarker() {
	}

	static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player != lastPlayer) {
			// Alla rinascita (e al cambio di dimensione) il gioco crea un nuovo LocalPlayer: se il
			// precedente era morto, e' una morte.
			LocalPlayer previous = lastPlayer;
			lastPlayer = player;
			if (player == null) {
				leaveWorld();
				return;
			}
			if (previous == null) {
				enterWorld(mc, player);
			} else if (previous.isDeadOrDying() && PapouConfig.get().deathMarkerEnabled) {
				add(player, previous);
			}
			deadTicks = 0;
		}
		if (player == null) {
			return;
		}

		if (!mc.isPaused()) {
			if (player.isDeadOrDying()) {
				deadTicks++;
			}
			age(mc, player);
		}
		sync(mc, player);
	}

	private static void add(LocalPlayer player, LocalPlayer previous) {
		GlobalPos pos = player.getLastDeathLocation()
				.orElseGet(() -> GlobalPos.of(previous.level().dimension(), previous.blockPosition()));
		if (markers.size() >= MAX_MARKERS) {
			Marker oldest = markers.get(0);
			remove(oldest);
			player.sendSystemMessage(message(Component.translatable("papouhud.death.replaced", oldest.color.label())));
		}
		Color color = freeColor();
		Marker marker = new Marker(pos, color, Math.min(deadTicks, DURATION_TICKS - 1));
		markers.add(marker);
		save();

		player.sendSystemMessage(message(Component.translatable("papouhud.death.died",
				coordinates(pos.pos()), dimensionName(pos.dimension()), color.label())));
	}

	private static Color freeColor() {
		for (Color color : Color.values()) {
			if (markers.stream().noneMatch(marker -> marker.color == color)) {
				return color;
			}
		}
		return Color.RED; // Non succede: c'e' sempre un colore libero dopo aver tolto il piu' vecchio.
	}

	/** Fa invecchiare i teschi i cui oggetti stanno invecchiando, e toglie quelli finiti o raccolti. */
	private static void age(Minecraft mc, LocalPlayer player) {
		if (markers.isEmpty()) {
			return;
		}
		int simulation = simulationDistance(mc);
		boolean changed = false;
		boolean aging = false;
		for (Iterator<Marker> it = markers.iterator(); it.hasNext(); ) {
			Marker marker = it.next();
			if (isSimulated(player, marker.pos, simulation)) {
				aging = true;
				marker.agedTicks++;
				if (marker.agedTicks >= DURATION_TICKS) {
					it.remove();
					untrack(marker);
					changed = true;
					player.sendSystemMessage(message(Component.translatable("papouhud.death.expired", marker.color.label())));
					continue;
				}
				if (marker.agedTicks % MESSAGE_INTERVAL_TICKS == 0) {
					remind(player, marker);
				}
			}

			if (!player.isDeadOrDying() && itemsGone(player, marker)) {
				if (++marker.emptyTicks >= COLLECT_CONFIRM_TICKS) {
					it.remove();
					untrack(marker);
					changed = true;
					player.sendSystemMessage(message(Component.translatable("papouhud.death.collected", marker.color.label())));
				}
			} else {
				marker.emptyTicks = 0;
			}
		}
		if (changed || (aging && ++ticksSinceSave >= SAVE_INTERVAL_TICKS)) {
			save();
		}
	}

	/**
	 * Il server fa invecchiare gli oggetti nei chunk entro la distanza di simulazione da un giocatore
	 * (distanza in chunk contando le diagonali, come i ticket dei giocatori sul server).
	 */
	static boolean withinSimulation(int fromBlockX, int fromBlockZ, int toBlockX, int toBlockZ, int simulationDistance) {
		int dx = Math.abs((fromBlockX >> 4) - (toBlockX >> 4));
		int dz = Math.abs((fromBlockZ >> 4) - (toBlockZ >> 4));
		return Math.max(dx, dz) <= simulationDistance;
	}

	private static boolean isSimulated(LocalPlayer player, GlobalPos pos, int simulation) {
		BlockPos target = pos.pos();
		if (player.level().dimension() == pos.dimension()
				&& withinSimulation(player.getBlockX(), player.getBlockZ(), target.getX(), target.getZ(), simulation)) {
			return true;
		}
		String dimension = pos.dimension().identifier().toString();
		for (PlayersPayload.Entry other : RemotePlayers.current()) {
			if (other.dimension().equals(dimension) && withinSimulation(
					(int) Math.floor(other.x()), (int) Math.floor(other.z()), target.getX(), target.getZ(), simulation)) {
				return true;
			}
		}
		return false;
	}

	private static int simulationDistance(Minecraft mc) {
		int fromServer = mc.level != null ? mc.level.getServerSimulationDistance() : 0;
		return fromServer > 0 ? fromServer : mc.options.simulationDistance().get();
	}

	/** Da vicino, nessun oggetto a terra intorno al punto: raccolti (o distrutti, es. nella lava). */
	private static boolean itemsGone(LocalPlayer player, Marker marker) {
		if (player.level().dimension() != marker.pos.dimension()) {
			return false;
		}
		Vec3 center = Vec3.atCenterOf(marker.pos.pos());
		if (player.position().distanceTo(center) > COLLECT_CHECK_RANGE || !player.level().isLoaded(marker.pos.pos())) {
			return false;
		}
		AABB area = new AABB(center, center).inflate(ITEM_SEARCH_RADIUS);
		return player.level().getEntitiesOfClass(ItemEntity.class, area).isEmpty();
	}

	private static void remind(LocalPlayer player, Marker marker) {
		int minutesLeft = (DURATION_TICKS - marker.agedTicks + MESSAGE_INTERVAL_TICKS - 1) / MESSAGE_INTERVAL_TICKS;
		BlockPos pos = marker.pos.pos();
		Component text;
		if (player.level().dimension() == marker.pos.dimension()) {
			int distance = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(pos)));
			text = Component.translatable("papouhud.death.reminder", marker.color.label(), distance, coordinates(pos), minutesLeft);
		} else {
			text = Component.translatable("papouhud.death.reminder_other_dimension", marker.color.label(),
					dimensionName(marker.pos.dimension()), coordinates(pos), minutesLeft);
		}
		player.sendSystemMessage(message(text));
	}

	/** Mostra ogni teschio solo nella sua dimensione (altrove la direzione non avrebbe senso), e non da morti. */
	private static void sync(Minecraft mc, LocalPlayer player) {
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return;
		}
		ClientWaypointManager manager = connection.getWaypointManager();
		if (shownIn != manager) {
			// Nuova connessione: i waypoint della vecchia non esistono piu'.
			shown.clear();
			shownIn = manager;
		}
		for (Marker marker : markers) {
			boolean show = player.level().dimension() == marker.pos.dimension() && !player.isDeadOrDying();
			if (show && shown.add(marker)) {
				manager.trackWaypoint(marker.waypoint);
			} else if (!show && shown.remove(marker)) {
				manager.untrackWaypoint(marker.waypoint);
			}
		}
	}

	private static void remove(Marker marker) {
		markers.remove(marker);
		untrack(marker);
		save();
	}

	private static void untrack(Marker marker) {
		if (shown.remove(marker) && shownIn != null) {
			shownIn.untrackWaypoint(marker.waypoint);
		}
	}

	/** Entrata in un mondo: ricarica i teschi lasciati li' e li ricorda in chat. */
	private static void enterWorld(Minecraft mc, LocalPlayer player) {
		markers.clear();
		shown.clear();
		shownIn = null;
		worldKey = worldKey(mc);
		if (worldKey == null) {
			return;
		}
		List<SavedMarker> saved = readAll().getOrDefault(worldKey, List.of());
		for (SavedMarker entry : saved) {
			Marker marker = entry.toMarker();
			if (marker != null && markers.size() < MAX_MARKERS
					&& markers.stream().noneMatch(other -> other.color == marker.color)) {
				markers.add(marker);
			}
		}
		if (!markers.isEmpty()) {
			MutableComponent list = Component.empty();
			for (int i = 0; i < markers.size(); i++) {
				Marker marker = markers.get(i);
				if (i > 0) {
					list.append(Component.literal(", "));
				}
				list.append(Component.translatable("papouhud.death.active_entry", marker.color.label(),
						coordinates(marker.pos.pos()), dimensionName(marker.pos.dimension())));
			}
			player.sendSystemMessage(message(Component.translatable("papouhud.death.active", list)));
		}
	}

	/** Uscita dal mondo: salva i teschi; la connessione (e i suoi waypoint) non esiste piu'. */
	private static void leaveWorld() {
		save();
		markers.clear();
		shown.clear();
		shownIn = null;
		worldKey = null;
		deadTicks = 0;
	}

	/** "server:<indirizzo>" in multiplayer, "world:<cartella>" in singleplayer. */
	private static String worldKey(Minecraft mc) {
		MinecraftServer integrated = mc.getSingleplayerServer();
		if (integrated != null) {
			Path folder = integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			return "world:" + (folder != null ? folder : integrated.getWorldData().getLevelName());
		}
		ServerData server = mc.getCurrentServer();
		return server != null ? "server:" + server.ip : null;
	}

	private static void save() {
		ticksSinceSave = 0;
		if (worldKey == null) {
			return;
		}
		Map<String, List<SavedMarker>> all = readAll();
		if (markers.isEmpty()) {
			all.remove(worldKey);
		} else {
			all.put(worldKey, markers.stream().map(SavedMarker::of).toList());
		}
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(all, FILE_TYPE, writer);
			}
		} catch (IOException e) {
			// Se non si riesce a salvare, i teschi restano finche' non si esce dal mondo.
		}
	}

	private static Map<String, List<SavedMarker>> readAll() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				Map<String, List<SavedMarker>> all = GSON.fromJson(reader, FILE_TYPE);
				if (all != null) {
					return new HashMap<>(all);
				}
			} catch (IOException | RuntimeException e) {
				// File illeggibile o rovinato: si riparte senza teschi salvati.
			}
		}
		return new HashMap<>();
	}

	/** Come un teschio e' scritto in config/papouhud-deaths.json. */
	private record SavedMarker(String dimension, int x, int y, int z, String color, int agedTicks) {
		static SavedMarker of(Marker marker) {
			BlockPos pos = marker.pos.pos();
			return new SavedMarker(marker.pos.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ(),
					marker.color.name(), marker.agedTicks);
		}

		Marker toMarker() {
			Identifier id = dimension == null ? null : Identifier.tryParse(dimension);
			if (id == null || color == null) {
				return null;
			}
			Color parsed;
			try {
				parsed = Color.valueOf(color);
			} catch (IllegalArgumentException e) {
				return null;
			}
			ResourceKey<Level> level = ResourceKey.create(Registries.DIMENSION, id);
			int aged = Math.max(0, Math.min(agedTicks, DURATION_TICKS - 1));
			return new Marker(GlobalPos.of(level, new BlockPos(x, y, z)), parsed, aged);
		}
	}

	private static Component message(Component text) {
		// Il teschio colorato e' gia' nel testo (Color.label), non serve ripeterlo davanti.
		return text.copy().withStyle(ChatFormatting.GRAY);
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
