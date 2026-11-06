package com.giulio.papouhud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Menu di Papou: elementi attivi/disattivi e scorciatoie da tastiera. */
public class PapouConfigScreen extends Screen {
	private static final int ROW_HEIGHT = 24;
	private static final int TOGGLE_WIDTH = 80;
	private static final int KEY_WIDTH = 100;
	private static final int GLFW_KEY_ESCAPE = 256;
	private static final int GLFW_KEY_BACKSPACE = 259;
	private static final int GLFW_KEY_DELETE = 261;

	private final Screen parent;
	private final PapouConfig config = PapouConfig.get();

	private final String[] elementKeys = {
			"papouhud.element.panels", "papouhud.element.compass", "papouhud.element.coords", "papouhud.element.menu"};
	private final Button[] toggleButtons = new Button[3];
	private final Button[] keyButtons = new Button[4];
	/** Indice della scorciatoia in attesa di un tasto, -1 se nessuna. */
	private int listening = -1;

	public PapouConfigScreen(Screen parent) {
		super(Component.translatable("papouhud.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int top = this.height / 4;

		for (int i = 0; i < elementKeys.length; i++) {
			final int row = i;
			int y = top + i * ROW_HEIGHT;

			if (i < toggleButtons.length) {
				toggleButtons[i] = addRenderableWidget(Button.builder(Component.empty(), button -> {
					setEnabled(row, !isEnabled(row));
					config.save();
					refreshLabels();
				}).bounds(cx - 20, y, TOGGLE_WIDTH, 20).build());
			}

			keyButtons[i] = addRenderableWidget(Button.builder(Component.empty(), button -> {
				listening = listening == row ? -1 : row;
				refreshLabels();
			}).bounds(cx + 65, y, KEY_WIDTH, 20).build());
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
				.bounds(cx - 100, top + elementKeys.length * ROW_HEIGHT + 24, 200, 20).build());

		refreshLabels();
	}

	private boolean isEnabled(int index) {
		return switch (index) {
			case 0 -> config.panelsEnabled;
			case 1 -> config.compassEnabled;
			default -> config.coordsEnabled;
		};
	}

	private void setEnabled(int index, boolean value) {
		switch (index) {
			case 0 -> config.panelsEnabled = value;
			case 1 -> config.compassEnabled = value;
			default -> config.coordsEnabled = value;
		}
	}

	private int getKey(int index) {
		return switch (index) {
			case 0 -> config.keyPanels;
			case 1 -> config.keyCompass;
			case 2 -> config.keyCoords;
			default -> config.keyMenu;
		};
	}

	private void setKey(int index, int key) {
		switch (index) {
			case 0 -> config.keyPanels = key;
			case 1 -> config.keyCompass = key;
			case 2 -> config.keyCoords = key;
			default -> config.keyMenu = key;
		}
	}

	private void refreshLabels() {
		for (int i = 0; i < toggleButtons.length; i++) {
			toggleButtons[i].setMessage(Component.translatable(
					isEnabled(i) ? "papouhud.config.on" : "papouhud.config.off"));
		}
		for (int i = 0; i < keyButtons.length; i++) {
			Component label;
			if (listening == i) {
				label = Component.translatable("papouhud.config.press");
			} else if (getKey(i) == PapouConfig.NO_KEY) {
				label = Component.translatable("papouhud.config.none");
			} else {
				label = InputConstants.Type.KEYSYM.getOrCreate(getKey(i)).getDisplayName();
			}
			keyButtons[i].setMessage(label);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (listening >= 0) {
			int key = event.key();
			if (key == GLFW_KEY_DELETE || key == GLFW_KEY_BACKSPACE) {
				setKey(listening, PapouConfig.NO_KEY);
				config.save();
			} else if (key != GLFW_KEY_ESCAPE) {
				setKey(listening, key);
				config.save();
			}
			listening = -1;
			refreshLabels();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int cx = this.width / 2;
		int top = this.height / 4;

		graphics.centeredText(this.font, this.title, cx, top - 36, 0xFFFFFFFF);
		graphics.text(this.font, Component.translatable("papouhud.config.column_show"), cx - 20, top - 14, 0xFFAAAAAA, true);
		graphics.text(this.font, Component.translatable("papouhud.config.column_key"), cx + 65, top - 14, 0xFFAAAAAA, true);

		for (int i = 0; i < elementKeys.length; i++) {
			graphics.text(this.font, Component.translatable(elementKeys[i]),
					cx - 160, top + i * ROW_HEIGHT + 6, 0xFFFFFFFF, true);
		}

		graphics.centeredText(this.font, Component.translatable("papouhud.config.hint"),
				cx, top + elementKeys.length * ROW_HEIGHT + 52, 0xFF888888);
	}

	@Override
	public void onClose() {
		config.save();
		this.minecraft.gui.setScreen(parent);
	}
}
