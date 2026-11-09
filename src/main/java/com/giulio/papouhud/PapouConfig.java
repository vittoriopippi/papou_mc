package com.giulio.papouhud;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/** Impostazioni di Papou, salvate in config/papou.json. Tasto -1 = nessun tasto. */
public final class PapouConfig {
	public static final int NO_KEY = -1;
	private static final int CURRENT_VERSION = 2;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("papou.json");

	public boolean panelsEnabled = true;
	public boolean compassEnabled = true;
	public boolean coordsEnabled = true;

	/** All'avvio controlla su GitHub se c'e' una nuova versione. */
	public boolean checkForUpdates = true;

	/** Dopo la morte, teschio sulla locator bar e promemoria in chat per 5 minuti. */
	public boolean deathMarkerEnabled = true;

	/** Versione del formato: se il file salvato e' piu' vecchio, le scorciatoie tornano ai valori predefiniti. */
	public int configVersion;

	public int keyPanels = 72; // H
	public int keyCompass = 78; // N
	public int keyCoords = 74; // J
	public int keyMenu = 77; // M

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
