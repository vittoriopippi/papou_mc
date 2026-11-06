package com.giulio.papouhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Pannello di un giocatore: nome, i 4 pezzi di armatura con barra di durabilita',
 * i punti armatura e i cuori. Disegnato con gli sprite originali dell'HUD di Minecraft.
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

	private PlayerPanel() {
	}

	static int heartRows(Player player) {
		int hearts = Math.max(1, (int) Math.ceil(player.getMaxHealth() / 2.0f));
		return (hearts + PER_ROW - 1) / PER_ROW;
	}

	static int height(Player player) {
		// padding, nome, spazio, icone + barra, spazio, punti armatura, spazio, cuori, padding
		return PAD + NAME_HEIGHT + 2 + ICON + 3 + 3 + SPRITE + 2 + heartRows(player) * SPRITE + PAD;
	}

	static void draw(GuiGraphicsExtractor graphics, Minecraft mc, Player player, int x, int y) {
		String name = player.getName().getString();

		int cx = x + PAD;
		int cy = y + PAD;
		graphics.text(mc.font, name, cx, cy, 0xFFFFFFFF, true);
		cy += NAME_HEIGHT + 2;

		// Pezzi di armatura: icona (o riquadro vuoto) + barra durabilita' sotto.
		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			int ix = cx + i * (ICON + ICON_GAP);
			ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[i]);
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
		int armor = player.getArmorValue();
		for (int i = 0; i < PER_ROW; i++) {
			Identifier sprite = armor >= 2 * i + 2 ? ARMOR_FULL : armor >= 2 * i + 1 ? ARMOR_HALF : ARMOR_EMPTY;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, cx + i * STRIDE, cy, SPRITE, SPRITE);
		}
		cy += SPRITE + 2;

		// Cuori, 10 per riga.
		float health = player.getHealth();
		int hearts = Math.max(1, (int) Math.ceil(player.getMaxHealth() / 2.0f));
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
