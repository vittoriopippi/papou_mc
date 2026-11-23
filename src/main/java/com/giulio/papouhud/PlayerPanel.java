package com.giulio.papouhud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Pannello di un giocatore: nome con distanza (o dimensione), i 4 pezzi di armatura con barra di
 * durabilita', i punti armatura e i cuori. Disegnato con gli sprite originali dell'HUD di Minecraft.
 *
 * <p>I dati vengono dall'entita' quando il giocatore e' vicino (il client la conosce gia'),
 * altrimenti dall'elenco mandato dal server ({@link RemotePlayers}).
 */
final class PlayerPanel {
	private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
	private static final Identifier ARMOR_EMPTY = Identifier.withDefaultNamespace("hud/armor_empty");
	private static final Identifier ARMOR_HALF = Identifier.withDefaultNamespace("hud/armor_half");
	private static final Identifier ARMOR_FULL = Identifier.withDefaultNamespace("hud/armor_full");

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Sagome piene e opache (vedi assets/papouhud/textures/gui/sprites/armor_missing). */
	private static final Identifier[] EMPTY_SLOT_SPRITES = {
			Identifier.fromNamespaceAndPath("papouhud", "armor_missing/helmet"),
			Identifier.fromNamespaceAndPath("papouhud", "armor_missing/chestplate"),
			Identifier.fromNamespaceAndPath("papouhud", "armor_missing/leggings"),
			Identifier.fromNamespaceAndPath("papouhud", "armor_missing/boots")};

	private static final int PAD = 4;
	private static final int SPRITE = 9;
	private static final int STRIDE = 8;
	private static final int PER_ROW = 10;
	private static final int ICON = 12;
	private static final int ICON_GAP = 4;
	private static final float ICON_SCALE = ICON / 16.0f;
	private static final int NAME_HEIGHT = 9;
	private static final int LOCATION_GAP = 5;
	private static final int LOCATION_COLOR = 0xFFAAAAAA;

	/** Quello che serve per disegnare un pannello, dall'entita' o dai dati del server. */
	record Data(String name, Component location, float health, float maxHealth, int armorValue, ItemStack[] armor) {
		static Data of(Minecraft mc, Player player) {
			ItemStack[] armor = new ItemStack[ARMOR_SLOTS.length];
			for (int i = 0; i < ARMOR_SLOTS.length; i++) {
				armor[i] = player.getItemBySlot(ARMOR_SLOTS[i]);
			}
			return new Data(player.getName().getString(), distance(mc.player.position(), player.position()),
					player.getHealth(), player.getMaxHealth(), player.getArmorValue(), armor);
		}

		static Data of(Minecraft mc, PlayersPayload.Entry entry) {
			ItemStack[] armor = new ItemStack[ARMOR_SLOTS.length];
			for (int i = 0; i < ARMOR_SLOTS.length; i++) {
				armor[i] = i < entry.armor().size() ? entry.armor().get(i).toStack() : ItemStack.EMPTY;
			}
			Component location = entry.dimension().equals(mc.level.dimension().identifier().toString())
					? distance(mc.player.position(), new Vec3(entry.x(), entry.y(), entry.z()))
					: dimensionName(entry.dimension());
			return new Data(entry.name(), location, entry.health(), entry.maxHealth(), entry.armorValue(), armor);
		}
	}

	private PlayerPanel() {
	}

	/**
	 * I pannelli da mostrare, nell'ordine del server (ordine di ingresso), poi gli eventuali giocatori
	 * vicini che il server non ha elencato (server senza Papou HUD: solo questi).
	 */
	static List<Data> collect(Minecraft mc) {
		List<Data> panels = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		seen.add(mc.player.getUUID());
		for (PlayersPayload.Entry entry : RemotePlayers.current()) {
			if (!seen.add(entry.uuid())) {
				continue;
			}
			// Se e' vicino il client lo vede gia': i dati dell'entita' sono piu' freschi.
			Player loaded = mc.level.getPlayerByUUID(entry.uuid());
			panels.add(loaded != null ? Data.of(mc, loaded) : Data.of(mc, entry));
		}
		for (Player other : mc.level.players()) {
			if (seen.add(other.getUUID())) {
				panels.add(Data.of(mc, other));
			}
		}
		return panels;
	}

	static Component distance(Vec3 from, Vec3 to) {
		double meters = from.distanceTo(to);
		String text = meters < 1000
				? Math.round(meters) + " m"
				: String.format(Locale.ROOT, "%.1f km", meters / 1000.0);
		return Component.literal(text);
	}

	static Component dimensionName(String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return Component.literal(dimension);
		}
		return Component.translatableWithFallback(
				"papouhud.panel.dimension." + id.getNamespace() + "." + id.getPath(), id.getPath());
	}

	static int heartRows(float maxHealth) {
		int hearts = Math.max(1, (int) Math.ceil(maxHealth / 2.0f));
		return (hearts + PER_ROW - 1) / PER_ROW;
	}

	static int height(Data data) {
		// padding, nome, spazio, icone + barra, spazio, punti armatura, spazio, cuori, padding
		return PAD + NAME_HEIGHT + 2 + ICON + 3 + 3 + SPRITE + 2 + heartRows(data.maxHealth()) * SPRITE + PAD;
	}

	static void draw(GuiGraphicsExtractor graphics, Minecraft mc, Data data, int x, int y) {
		int cx = x + PAD;
		int cy = y + PAD;
		graphics.text(mc.font, data.name(), cx, cy, 0xFFFFFFFF, true);
		if (data.location() != null) {
			graphics.text(mc.font, data.location(), cx + mc.font.width(data.name()) + LOCATION_GAP, cy,
					LOCATION_COLOR, true);
		}
		cy += NAME_HEIGHT + 2;

		// Pezzi di armatura: icona (o riquadro vuoto) + barra durabilita' sotto.
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			int ix = cx + i * (ICON + ICON_GAP);
			ItemStack stack = data.armor()[i];
			if (stack.isEmpty()) {
				drawEmptySlot(graphics, ix, cy, i);
				continue;
			}
			graphics.pose().pushMatrix();
			graphics.pose().translate(ix, cy);
			graphics.pose().scale(ICON_SCALE, ICON_SCALE);
			graphics.item(stack, 0, 0);
			graphics.pose().popMatrix();

			if (stack.isDamageableItem()) {
				int filled = Math.max(1, Math.round(stack.getBarWidth() / 13.0f * ICON));
				graphics.fill(ix, cy + ICON + 1, ix + ICON, cy + ICON + 3, 0xFF202020);
				graphics.fill(ix, cy + ICON + 1, ix + filled, cy + ICON + 2, 0xFF000000 | stack.getBarColor());
			}
		}
		cy += ICON + 3 + 3;

		// Punti armatura (10 icone, ogni icona = 2 punti).
		int armor = data.armorValue();
		for (int i = 0; i < PER_ROW; i++) {
			Identifier sprite = armor >= 2 * i + 2 ? ARMOR_FULL : armor >= 2 * i + 1 ? ARMOR_HALF : ARMOR_EMPTY;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, cx + i * STRIDE, cy, SPRITE, SPRITE);
		}
		cy += SPRITE + 2;

		// Cuori, 10 per riga.
		float health = data.health();
		int hearts = Math.max(1, (int) Math.ceil(data.maxHealth() / 2.0f));
		for (int i = 0; i < hearts; i++) {
			int hx = cx + (i % PER_ROW) * STRIDE;
			int hy = cy + (i / PER_ROW) * SPRITE;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, hx, hy, SPRITE, SPRITE);
			if (health >= 2 * i + 2) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, hx, hy, SPRITE, SPRITE);
			} else if (health >= 2 * i + 1) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, hx, hy, SPRITE, SPRITE);
			}
		}
	}

	/** Sagoma piena e scura del pezzo di armatura non indossato, come un elemento da sbloccare. */
	private static void drawEmptySlot(GuiGraphicsExtractor graphics, int x, int y, int slotIndex) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SLOT_SPRITES[slotIndex], x, y, ICON, ICON);
	}
}
