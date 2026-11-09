package com.giulio.papouhud;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Lato server (anche il server integrato di chi ospita in singleplayer/Essential): a chi ha aperta
 * una fornace, un affumicatoio o un altoforno manda l'XP accumulata, solo se ha Papou HUD e solo
 * quando il valore cambia.
 */
final class FurnaceXpSync {
	private static final int INTERVAL_TICKS = 5;

	/** Ultimo valore inviato a ogni giocatore: id del menu nei 32 bit alti, XP nei 32 bassi. */
	private static final Map<UUID, Long> LAST_SENT = new HashMap<>();
	private static int tickCounter;

	private FurnaceXpSync() {
	}

	static void tick(MinecraftServer server) {
		if (++tickCounter < INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		Map<UUID, Long> stillOpen = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.containerMenu instanceof AbstractFurnaceMenu menu)
					|| !(menu.container instanceof AbstractFurnaceBlockEntity furnace)
					|| !ServerPlayNetworking.canSend(player, FurnaceXpPayload.TYPE)) {
				continue;
			}

			float xp = storedXp(furnace, player.level());
			long key = ((long) menu.containerId << 32) | (Float.floatToIntBits(xp) & 0xFFFFFFFFL);
			Long previous = LAST_SENT.get(player.getUUID());
			if (previous == null || previous != key) {
				ServerPlayNetworking.send(player, new FurnaceXpPayload(menu.containerId, xp));
			}
			stillOpen.put(player.getUUID(), key);
		}
		LAST_SENT.clear();
		LAST_SENT.putAll(stillOpen);
	}

	/** Somma, per ogni ricetta cotta, volte x XP della ricetta (come fa il gioco quando si ritira l'output). */
	static float storedXp(AbstractFurnaceBlockEntity furnace, ServerLevel level) {
		RecipeManager recipes = level.recipeAccess();
		float total = 0.0f;
		for (Reference2IntMap.Entry<ResourceKey<Recipe<?>>> entry : furnace.recipesUsed.reference2IntEntrySet()) {
			int count = entry.getIntValue();
			total += recipes.byKey(entry.getKey())
					.map(holder -> holder.value() instanceof AbstractCookingRecipe cooking ? cooking.experience() * count : 0.0f)
					.orElse(0.0f);
		}
		return total;
	}
}
