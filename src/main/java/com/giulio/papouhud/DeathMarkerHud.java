package com.giulio.papouhud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * In alto a destra, ogni teschio di {@link DeathMarker} come un effetto negativo: il riquadro degli
 * effetti con il teschio del suo colore e, sotto, il tempo che resta ai suoi oggetti ("4:32").
 * Sta nella riga degli effetti negativi del gioco, a sinistra di quelli che il giocatore ha gia'.
 * Quando nessuno e' vicino agli oggetti il conto e' fermo: teschio sbiadito e tempo grigio.
 * Segue la dimensione dell'HUD di Papou (PapouConfig.hudScale).
 */
final class DeathMarkerHud {
	/** Come gli effetti del gioco: riquadri da 24 px, uno ogni 25; i negativi nella seconda riga (y = 1 + 26). */
	private static final int SIZE = 24;
	private static final int STEP = 25;
	private static final int ROW_Y = 1 + 26;
	private static final int ICON = 18;
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/effect_background");
	/** Lo stesso teschio della locator bar (9x9, bianco con il contorno nero), qui ingrandito e colorato. */
	private static final Identifier SKULL = Identifier.fromNamespaceAndPath("papouhud", "hud/locator_bar_dot/death");

	private static final int TIME_RUNNING = 0xFFFFFFFF;
	private static final int TIME_LAST_SECONDS = 0xFFFF5555;
	private static final int TIME_PAUSED = 0xFF9A9A9A;
	/** Ultimi secondi: il tempo diventa rosso. */
	private static final int LAST_SECONDS_TICKS = 30 * 20;

	private DeathMarkerHud() {
	}

	static void register() {
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MOB_EFFECTS,
				Identifier.fromNamespaceAndPath("papouhud", "death_markers"),
				DeathMarkerHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || DeathMarker.markers.isEmpty()) {
			return;
		}
		// Come gli effetti del gioco: nascosti quando una schermata li mostra gia' (es. l'inventario).
		Screen screen = mc.gui.screen();
		if (screen != null && screen.showsActiveEffects()) {
			return;
		}

		// Dopo le icone degli effetti negativi (e neutri) che il gioco sta gia' disegnando.
		int vanillaIcons = 0;
		for (MobEffectInstance effect : mc.player.getActiveEffects()) {
			if (effect.showIcon() && !effect.getEffect().value().isBeneficial()) {
				vanillaIcons++;
			}
		}
		int rowStart = graphics.guiWidth() - STEP * vanillaIcons;
		int rowY = ROW_Y + (mc.isDemo() ? 15 : 0);

		float scale = PapouConfig.get().hudScaleFactor();
		graphics.pose().pushMatrix();
		graphics.pose().translate(rowStart, rowY);
		graphics.pose().scale(scale, scale);
		int index = 0;
		for (DeathMarker.Marker marker : DeathMarker.markers) {
			index++;
			int x = -STEP * index;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, 0, SIZE, SIZE);
			int tint = marker.running ? marker.color.argb : (marker.color.argb & 0x00FFFFFF) | 0x80000000;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SKULL, x + (SIZE - ICON) / 2, (SIZE - ICON) / 2, ICON, ICON, tint);

			String time = DeathMarker.timeLeft(marker);
			int color = !marker.running ? TIME_PAUSED
					: DeathMarker.DURATION_TICKS - marker.agedTicks <= LAST_SECONDS_TICKS ? TIME_LAST_SECONDS : TIME_RUNNING;
			graphics.text(mc.font, time, x + SIZE / 2 - mc.font.width(time) / 2, SIZE + 1, color, true);
		}
		graphics.pose().popMatrix();
	}
}
