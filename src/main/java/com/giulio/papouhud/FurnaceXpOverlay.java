package com.giulio.papouhud;

import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Sotto lo slot di output di fornace/affumicatoio/altoforno: una sfera di esperienza pulsante come
 * quelle a terra, seguita dal valore scritto come il livello nella barra dell'esperienza.
 * Passandoci sopra col mouse compare una spiegazione.
 */
final class FurnaceXpOverlay {
	private static final Identifier ORB_TEXTURE =
			Identifier.withDefaultNamespace("textures/entity/experience/experience_orb.png");
	/**
	 * Fotogramma della sfera usato: il secondo (16x16, parte visibile 10x10 da 3,3 a 12,12), alto
	 * quanto il testo con il bordo. Quelli piu' grandi arrivano a 16 px e non stanno nella riga.
	 */
	private static final int ORB_ICON = 1;
	private static final int ORB_FRAME = 16;
	private static final int ORB_INSET = 3;
	private static final int ORB_VISIBLE = 10;

	/** Centro dello slot di output e riga libera sotto di esso, nelle coordinate della GUI (176x166). */
	private static final int OUTPUT_CENTER_X = 124;
	private static final int TEXT_Y = 60;
	private static final int GAP = 2;
	private static final int XP_GREEN = 0xFF80FF20;
	private static final int OUTLINE = 0xFF000000;

	/** Ultimo valore ricevuto (letti anche dal game test degli screenshot). */
	static volatile int containerId = -1;
	static volatile float xp;

	private FurnaceXpOverlay() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FurnaceXpPayload.TYPE, (payload, context) -> {
			containerId = payload.containerId();
			xp = payload.xp();
		});

		// afterForeground (non afterExtract): arriva prima dei tooltip, cosi' il nostro viene mostrato
		// e quelli degli oggetti finiscono sopra il riquadro.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractFurnaceScreen<?> furnaceScreen) {
				ScreenEvents.afterForeground(screen).register((s, graphics, mouseX, mouseY, delta) ->
						draw(furnaceScreen, graphics, mouseX, mouseY));
			}
		});
	}

	/** "15.4", oppure "15" se intero; senza decimali da 100 in su. */
	static String format(float value) {
		float rounded = Math.round(value * 10.0f) / 10.0f;
		if (rounded >= 100.0f || rounded == Math.floor(rounded)) {
			return Integer.toString(Math.round(rounded));
		}
		return String.format(Locale.ROOT, "%.1f", rounded);
	}

	private static void draw(AbstractFurnaceScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		float stored = xp;
		if (screen.getMenu().containerId != containerId || stored <= 0.0f) {
			return; // Niente dati dal server (server senza la mod), un'altra fornace, o niente da raccogliere.
		}

		Font font = Minecraft.getInstance().font;
		String number = format(stored);
		int width = ORB_VISIBLE + GAP + font.width(number);
		int left = screen.leftPos + OUTPUT_CENTER_X - width / 2;
		int top = screen.topPos + TEXT_Y;

		// Parte visibile della sfera da (left, top - 1): alta come il testo (8 px) piu' il bordo.
		drawOrb(graphics, left - ORB_INSET, top - 1 - ORB_INSET);
		drawOutlined(graphics, font, number, left + ORB_VISIBLE + GAP, top);

		if (mouseX >= left - 1 && mouseX < left + width + 1 && mouseY >= top - 2 && mouseY < top + 10) {
			graphics.setComponentTooltipForNextFrame(font, List.of(
					Component.translatable("papouhud.furnace.tooltip.title").withStyle(ChatFormatting.GREEN),
					Component.translatable("papouhud.furnace.tooltip.points", format(stored)).withStyle(ChatFormatting.GRAY),
					Component.translatable("papouhud.furnace.tooltip.hint").withStyle(ChatFormatting.DARK_GRAY)),
					mouseX, mouseY);
		}
	}

	/** La sfera con la stessa tinta pulsante giallo-verde delle sfere a terra. */
	private static void drawOrb(GuiGraphicsExtractor graphics, int x, int y) {
		float time = (Util.getMillis() % 100_000L) / 100.0f;
		int red = (int) ((Mth.sin(time) + 1.0f) * 0.5f * 255.0f);
		int blue = (int) ((Mth.sin(time + 4.1887903f) + 1.0f) * 0.1f * 255.0f);
		int tint = 0xFF000000 | red << 16 | 0xFF00 | blue;

		graphics.blit(RenderPipelines.GUI_TEXTURED, ORB_TEXTURE, x, y,
				(ORB_ICON % 4) * ORB_FRAME, (ORB_ICON / 4) * ORB_FRAME, ORB_FRAME, ORB_FRAME, 64, 64, tint);
	}

	/** Testo verde con bordo nero, come il numero del livello sopra la barra dell'esperienza. */
	private static void drawOutlined(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
		graphics.text(font, text, x + 1, y, OUTLINE, false);
		graphics.text(font, text, x - 1, y, OUTLINE, false);
		graphics.text(font, text, x, y + 1, OUTLINE, false);
		graphics.text(font, text, x, y - 1, OUTLINE, false);
		graphics.text(font, text, x, y, XP_GREEN, false);
	}
}
