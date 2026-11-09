package com.giulio.papouhud;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Mod solo-client:
 *  - un pannello in alto a sinistra per ogni altro giocatore noto al client (entro la
 *    distanza di caricamento): nome, armatura, punti armatura e cuori;
 *  - una bussola in alto con direzione e punti cardinali, e sotto le coordinate XYZ;
 *  - un menu (tasto configurabile) per attivare/disattivare gli elementi e scegliere le scorciatoie;
 *  - all'avvio, un popup se su GitHub c'e' una versione piu' recente (vedi UpdateChecker).
 */
public class PapouHudClient implements ClientModInitializer {
	private static final int MARGIN = 4;
	private static final int PANEL_GAP = 3;

	private static final int COMPASS_HALF_WIDTH = 100;
	private static final float PIXELS_PER_DEGREE = 1.5f;
	private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	/** Stato precedente di ogni scorciatoia (pannelli, bussola, coordinate, menu) per rilevare il click. */
	private final boolean[] wasDown = new boolean[4];

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::handleHotkeys);
		ClientTickEvents.END_CLIENT_TICK.register(UpdateChecker::tick);
		UpdateChecker.start();

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("papouhud", "player_panels"),
				PapouHudClient::renderPanels);

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("papouhud", "compass"),
				PapouHudClient::renderCompass);

		HudElementRegistry.attachElementAfter(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath("papouhud", "coords"),
				PapouHudClient::renderCoords);
	}

	private void handleHotkeys(Minecraft mc) {
		PapouConfig config = PapouConfig.get();
		int[] keys = {config.keyPanels, config.keyCompass, config.keyCoords, config.keyMenu};
		boolean inGame = mc.player != null && mc.gui.screen() == null;

		for (int i = 0; i < keys.length; i++) {
			boolean down = inGame && keys[i] != PapouConfig.NO_KEY
					&& InputConstants.isKeyDown(mc.getWindow(), keys[i]);
			if (down && !wasDown[i]) {
				switch (i) {
					case 0 -> toggle(mc, config, "papouhud.element.panels", config.panelsEnabled = !config.panelsEnabled);
					case 1 -> toggle(mc, config, "papouhud.element.compass", config.compassEnabled = !config.compassEnabled);
					case 2 -> toggle(mc, config, "papouhud.element.coords", config.coordsEnabled = !config.coordsEnabled);
					default -> mc.gui.setScreen(new PapouConfigScreen(null));
				}
			}
			wasDown[i] = down;
		}
	}

	private static void toggle(Minecraft mc, PapouConfig config, String elementKey, boolean enabled) {
		config.save();
		mc.player.sendOverlayMessage(Component.translatable(
				enabled ? "message.papouhud.on" : "message.papouhud.off",
				Component.translatable(elementKey)));
	}

	private static void renderPanels(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!PapouConfig.get().panelsEnabled || mc.level == null || mc.player == null) {
			return;
		}

		int top = MARGIN;
		for (Player other : mc.level.players()) {
			if (other == mc.player) {
				continue;
			}

			PlayerPanel.draw(graphics, mc, other, MARGIN, top);
			top += PlayerPanel.height(other) + PANEL_GAP;
		}
	}

	private static void renderCompass(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!PapouConfig.get().compassEnabled || mc.level == null || mc.player == null) {
			return;
		}

		// In Minecraft yaw 0 = sud, 90 = ovest: la rotta bussola (N=0, E=90) e' yaw + 180.
		float bearing = Mth.wrapDegrees(mc.player.getYRot() + 180.0f);
		float normalized = (bearing + 360.0f) % 360.0f;

		int centerX = graphics.guiWidth() / 2;
		int top = MARGIN;

		// Tacche ogni 15 gradi, lettere ogni 45.
		for (int angle = 0; angle < 360; angle += 15) {
			float delta = Mth.wrapDegrees(angle - normalized);
			int x = centerX + Math.round(delta * PIXELS_PER_DEGREE);
			if (x < centerX - COMPASS_HALF_WIDTH + 2 || x > centerX + COMPASS_HALF_WIDTH - 2) {
				continue;
			}

			if (angle % 45 == 0) {
				String label = DIRECTIONS[angle / 45];
				int color = label.equals("N") ? 0xFFFF5555 : 0xFFFFFFFF;
				graphics.text(mc.font, label, x - mc.font.width(label) / 2, top + 3, color, true);
			} else {
				graphics.fill(x, top + 5, x + 1, top + 9, 0xFFAAAAAA);
			}
		}

		// Indicatore centrale e gradi correnti.
		graphics.fill(centerX, top + 12, centerX + 1, top + 20, 0xFFFFFF55);
		String degrees = Math.round(normalized) % 360 + "°";
		graphics.text(mc.font, degrees, centerX - mc.font.width(degrees) / 2, top + 22, 0xFFFFFF55, true);
	}

	private static void renderCoords(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		PapouConfig config = PapouConfig.get();
		if (!config.coordsEnabled || mc.level == null || mc.player == null) {
			return;
		}

		// Subito sotto i gradi della bussola; senza bussola sale in alto.
		int y = config.compassEnabled ? MARGIN + 33 : MARGIN;
		String text = "XYZ: " + mc.player.getBlockX() + " / " + mc.player.getBlockY() + " / " + mc.player.getBlockZ();
		graphics.text(mc.font, text, graphics.guiWidth() / 2 - mc.font.width(text) / 2, y, 0xFFFFFFFF, true);
	}
}
