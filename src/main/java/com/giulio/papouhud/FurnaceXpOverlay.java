package com.giulio.papouhud;

import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.network.chat.Component;

/** Mostra sotto lo slot di output di fornace/affumicatoio/altoforno l'XP accumulata ricevuta dal server. */
final class FurnaceXpOverlay {
	/** Centro dello slot di output e riga libera sotto di esso, nelle coordinate della GUI (176x166). */
	private static final int OUTPUT_CENTER_X = 124;
	private static final int TEXT_Y = 58;
	private static final int XP_GREEN = 0xFF80FF20;

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

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof AbstractFurnaceScreen<?> furnaceScreen) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
						draw(furnaceScreen, graphics));
			}
		});
	}

	private static void draw(AbstractFurnaceScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (screen.getMenu().containerId != containerId) {
			return; // Nessun dato dal server (server senza la mod) o di un'altra fornace.
		}
		Minecraft mc = Minecraft.getInstance();
		Component text = Component.translatable("papouhud.furnace.xp", String.format(Locale.ROOT, "%.1f", xp));
		int x = screen.leftPos + OUTPUT_CENTER_X - mc.font.width(text) / 2;
		graphics.text(mc.font, text, x, screen.topPos + TEXT_Y, XP_GREEN, true);
	}
}
