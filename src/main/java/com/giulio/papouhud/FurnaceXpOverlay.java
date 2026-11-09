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
import net.minecraft.network.chat.Component;

/**
 * Sulla riga del titolo di fornace/affumicatoio/altoforno, a destra, l'XP accumulata (es. "15.4 XP"),
 * scritta come il titolo stesso. Passandoci sopra col mouse compare una spiegazione.
 */
final class FurnaceXpOverlay {
	/**
	 * Sulla riga del titolo ("Fornace"), allineato a destra con lo stesso margine di 8 px della
	 * scritta "Inventario" a sinistra. Coordinate della GUI della fornace (176x166).
	 */
	private static final int GUI_WIDTH = 176;
	private static final int RIGHT_MARGIN = 8;
	private static final int TEXT_Y = 6;
	/** Stesso grigio scuro, senza ombra, dei titoli dei contenitori. */
	private static final int LABEL_COLOR = 0xFF404040;

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
		// e quelli degli oggetti finiscono sopra il testo.
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
		Component text = Component.translatable("papouhud.furnace.xp", format(stored));
		int width = font.width(text);
		int left = screen.leftPos + GUI_WIDTH - RIGHT_MARGIN - width;
		int top = screen.topPos + TEXT_Y;
		graphics.text(font, text, left, top, LABEL_COLOR, false);

		if (mouseX >= left - 1 && mouseX < left + width + 1 && mouseY >= top - 2 && mouseY < top + 10) {
			graphics.setComponentTooltipForNextFrame(font, List.of(
					Component.translatable("papouhud.furnace.tooltip.title"),
					Component.translatable("papouhud.furnace.tooltip.points", format(stored)).withStyle(ChatFormatting.GRAY),
					Component.translatable("papouhud.furnace.tooltip.hint").withStyle(ChatFormatting.GRAY)),
					mouseX, mouseY);
		}
	}
}
