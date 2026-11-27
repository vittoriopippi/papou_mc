package com.giulio.papouhud;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Impostazioni di Papou, salvate in config/papou.json. Tasto -1 = nessun tasto.
 * I tasti sono numerati come in {@link InputConstants}: da Minecraft 26.3 sono gli scancode di SDL
 * (M = 16), non piu' i codici di GLFW (M = 77, che per SDL e' il tasto Fine).
 */
public final class PapouConfig {
	public static final int NO_KEY = -1;
	/** Dimensione dell'HUD (pannelli, bussola, coordinate) in percento: da 50 a 150, a passi di 10. */
	public static final int MIN_HUD_SCALE = 50;
	public static final int MAX_HUD_SCALE = 150;
	public static final int HUD_SCALE_STEP = 10;
	/** 3 = tasti con i codici di SDL (Minecraft 26.3): quelli salvati prima erano codici GLFW. */
	private static final int CURRENT_VERSION = 3;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("papou.json");

	public boolean panelsEnabled = true;
	public boolean compassEnabled = true;
	public boolean coordsEnabled = true;

	/** All'avvio controlla su GitHub se c'e' una nuova versione. */
	public boolean checkForUpdates = true;

	/** Dopo la morte, teschio sulla locator bar e promemoria in chat per 5 minuti. */
	public boolean deathMarkerEnabled = true;

	/** Dimensione dell'HUD in percento (100 = normale). Cambia dal menu con - e +. */
	public int hudScale = 100;

	/** Versione del formato: se il file salvato e' piu' vecchio, le scorciatoie tornano ai valori predefiniti. */
	public int configVersion;

	public int keyPanels = InputConstants.KEY_H;
	public int keyCompass = InputConstants.KEY_N;
	public int keyCoords = InputConstants.KEY_J;
	public int keyMenu = InputConstants.KEY_M;

	private static PapouConfig instance;

	public static PapouConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static PapouConfig load() {
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				PapouConfig loaded = GSON.fromJson(reader, PapouConfig.class);
				if (loaded != null) {
					if (loaded.configVersion < CURRENT_VERSION) {
						PapouConfig defaults = new PapouConfig();
						loaded.keyPanels = defaults.keyPanels;
						loaded.keyCompass = defaults.keyCompass;
						loaded.keyCoords = defaults.keyCoords;
						loaded.keyMenu = defaults.keyMenu;
						loaded.configVersion = CURRENT_VERSION;
					}
					loaded.hudScale = clampHudScale(loaded.hudScale);
					return loaded;
				}
			} catch (IOException | RuntimeException e) {
				// File illeggibile o rovinato: si riparte dai valori predefiniti.
			}
		}
		PapouConfig fresh = new PapouConfig();
		fresh.configVersion = CURRENT_VERSION;
		return fresh;
	}

	static int clampHudScale(int percent) {
		return Math.max(MIN_HUD_SCALE, Math.min(MAX_HUD_SCALE, percent));
	}

	/** Fattore di scala dell'HUD (1 = normale). */
	public float hudScaleFactor() {
		return clampHudScale(hudScale) / 100.0f;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			// Se non si riesce a salvare, le impostazioni restano valide fino alla chiusura del gioco.
		}
	}
}
