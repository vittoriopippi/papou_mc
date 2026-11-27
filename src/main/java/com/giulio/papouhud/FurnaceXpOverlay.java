package com.giulio.papouhud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Sulla riga del titolo di fornace/affumicatoio/altoforno, a destra, l'XP accumulata (es. "15.4 XP") e,
 * sulla riga sotto, il livello a cui arriveresti prendendola ("→ lv 23"), scritti come il titolo stesso.
 * Su una riga sola il testo finirebbe sopra il titolo, che e' centrato. Passandoci sopra col mouse
 * compare una spiegazione.
 *
 * <p>Il gioco da' la parte intera dell'XP piu' 1 punto con probabilita' pari ai decimali: il livello e'
 * calcolato sulla parte intera. Se addosso o in mano c'e' un oggetto danneggiato con Riparazione
 * (Mending), l'XP serve prima a ripararlo: il livello raggiunto sara' piu' basso, e il tooltip lo dice.
 */
final class FurnaceXpOverlay {
	/**
	 * Sulla riga del titolo ("Fornace"), allineato a destra con lo stesso margine di 8 px della
	 * scritta "Inventario" a sinistra. Coordinate della GUI della fornace (176x166).
	 */
	private static final int GUI_WIDTH = 176;
	private static final int RIGHT_MARGIN = 8;
	private static final int TEXT_Y = 6;
	/** Seconda riga (il livello): tra il titolo e lo slot del risultato, che inizia a y = 35. */
	private static final int LEVEL_Y = TEXT_Y + 11;
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

	/** Un oggetto addosso o in mano, danneggiato, con Riparazione (Mending): l'XP raccolta va prima a lui. */
	static boolean hasDamagedMendingGear(LocalPlayer player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamaged()
					&& EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP)) {
				return true;
			}
		}
		return false;
	}

	private static void draw(AbstractFurnaceScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		float stored = xp;
		if (screen.getMenu().containerId != containerId || stored <= 0.0f) {
			return; // Niente dati dal server (server senza la mod), un'altra fornace, o niente da raccogliere.
		}

		Font font = Minecraft.getInstance().font;
		LocalPlayer player = Minecraft.getInstance().player;
		double after = player == null ? -1
				: XpLevels.levelAfter(player.experienceLevel, player.experienceProgress, (int) Math.floor(stored));
		Component text = Component.translatable("papouhud.furnace.xp", format(stored));
		int right = screen.leftPos + GUI_WIDTH - RIGHT_MARGIN;
		int width = font.width(text);
		int top = screen.topPos + TEXT_Y;
		graphics.text(font, text, right - width, top, LABEL_COLOR, false);
		int bottom = top + 10;
		if (after >= 0) {
			Component level = Component.translatable("papouhud.furnace.level", (int) after);
			int levelWidth = font.width(level);
			graphics.text(font, level, right - levelWidth, screen.topPos + LEVEL_Y, LABEL_COLOR, false);
			width = Math.max(width, levelWidth);
			bottom = screen.topPos + LEVEL_Y + 10;
		}
		int left = right - width;

		if (mouseX >= left - 1 && mouseX < right + 1 && mouseY >= top - 2 && mouseY < bottom) {
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("papouhud.furnace.tooltip.title"));
			lines.add(Component.translatable("papouhud.furnace.tooltip.points", format(stored)).withStyle(ChatFormatting.GRAY));
			if (after >= 0) {
				int percent = (int) Math.floor((after - Math.floor(after)) * 100.0);
				lines.add(Component.translatable("papouhud.furnace.tooltip.level",
						player.experienceLevel, (int) after, percent).withStyle(ChatFormatting.GRAY));
				if (hasDamagedMendingGear(player)) {
					lines.add(Component.translatable("papouhud.furnace.tooltip.mending").withStyle(ChatFormatting.YELLOW));
				}
			}
			lines.add(Component.translatable("papouhud.furnace.tooltip.hint").withStyle(ChatFormatting.GRAY));
			graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
		}
	}
}
