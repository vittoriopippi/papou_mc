package com.giulio.papouhud.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;

/**
 * Come FurnaceXpSync nella mod: a chi ha Papou HUD e ha aperta una fornace, un affumicatoio o un
 * altoforno manda l'XP accumulata (il gioco non la manda mai ai giocatori), solo quando cambia.
 *
 * <p>Il pacchetto dice anche a quale finestra si riferisce (l'id del menu, come nel gioco). Paper non
 * ha un metodo per leggerlo, ma sul server, che usa i nomi di Mojang, i campi sono pubblici:
 * {@code ServerPlayer.containerMenu.containerId}. Se un giorno non ci fossero piu', la funzione si
 * spegne con un avviso nel log e il resto del plugin continua a funzionare.
 */
final class FurnaceXp {
	static final String CHANNEL = "papouhud:furnace_xp";

	private final Logger logger;
	/** Ultimo valore inviato a ogni giocatore: id del menu nei 32 bit alti, XP nei 32 bassi. */
	private final Map<UUID, Long> lastSent = new HashMap<>();
	private Method getHandle;
	private Field containerMenu;
	private Field containerId;
	private boolean broken;

	FurnaceXp(Logger logger) {
		this.logger = logger;
	}

	/** Da chiamare ogni pochi tick. Il {@code sender} manda i byte al giocatore sul canale {@link #CHANNEL}. */
	void tick(Iterable<? extends Player> online, Sender sender) {
		if (broken) {
			return;
		}
		Map<UUID, Long> stillOpen = new HashMap<>();
		for (Player player : online) {
			if (!player.getListeningPluginChannels().contains(CHANNEL)) {
				continue;
			}
			Inventory top = player.getOpenInventory().getTopInventory();
			if (!(top instanceof FurnaceInventory furnaceInventory)
					|| !(furnaceInventory.getHolder(false) instanceof Furnace furnace)) {
				continue;
			}
			int windowId = windowId(player);
			if (windowId < 0) {
				return;
			}
			float xp = storedXp(furnace.getRecipesUsed());
			long key = ((long) windowId << 32) | (Float.floatToIntBits(xp) & 0xFFFFFFFFL);
			Long previous = lastSent.get(player.getUniqueId());
			if (previous == null || previous != key) {
				sender.send(player, write(windowId, xp));
			}
			stillOpen.put(player.getUniqueId(), key);
		}
		lastSent.clear();
		lastSent.putAll(stillOpen);
	}

	interface Sender {
		void send(Player player, byte[] data);
	}

	/** Somma, per ogni ricetta cotta, volte x XP della ricetta (come fa il gioco quando si ritira l'output). */
	static float storedXp(Map<CookingRecipe<?>, Integer> recipesUsed) {
		float total = 0.0f;
		for (Map.Entry<CookingRecipe<?>, Integer> entry : recipesUsed.entrySet()) {
			total += entry.getKey().getExperience() * entry.getValue();
		}
		return total;
	}

	/** Gli stessi byte di FurnaceXpPayload nella mod: id del menu (VarInt), poi l'XP (float). */
	static byte[] write(int windowId, float xp) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(9);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			PlayersPacket.writeVarInt(out, windowId);
			out.writeFloat(xp);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	/** L'id della finestra aperta, o -1 (e funzione spenta) se il server non lo lascia leggere. */
	private int windowId(Player player) {
		try {
			if (getHandle == null) {
				getHandle = player.getClass().getMethod("getHandle");
			}
			Object handle = getHandle.invoke(player);
			if (containerMenu == null) {
				containerMenu = handle.getClass().getField("containerMenu");
			}
			Object menu = containerMenu.get(handle);
			if (containerId == null) {
				containerId = menu.getClass().getField("containerId");
			}
			return containerId.getInt(menu);
		} catch (ReflectiveOperationException | RuntimeException e) {
			broken = true;
			logger.warning("Furnace XP turned off: can't read the open window's id on this server (" + e + ")");
			return -1;
		}
	}
}
