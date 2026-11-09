package com.giulio.papouhud;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * All'avvio controlla su GitHub se esiste una release piu' recente e, la prima volta che si arriva
 * al menu principale, mostra un solo popup che propone di aggiornare.
 */
final class UpdateChecker {
	static final String MOD_ID = "papouhud";
	static final String REPO = "vittoriopippi/papou_mc";
	private static final String LATEST_RELEASE_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
	/** Gli asset scaricabili devono stare sotto questo indirizzo, mai altrove. */
	static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";

	/** Release piu' recente di quella installata; scritta dal thread di controllo, letta dal thread del gioco. */
	record Release(String version, String pageUrl, String jarName, String jarUrl) {
	}

	private static volatile Release available;
	private static boolean prompted;

	private UpdateChecker() {
	}

	static HttpClient httpClient() {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	static String currentVersion() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("0.0.0");
	}

	/** Avvia il controllo in background. Non fa nulla in ambiente di sviluppo o se disattivato. */
	static void start() {
		SelfUpdater.cleanUpLeftovers();
		if (FabricLoader.getInstance().isDevelopmentEnvironment() || !PapouConfig.get().checkForUpdates) {
			return;
		}

		Thread thread = new Thread(UpdateChecker::check, "Papou HUD update check");
		thread.setDaemon(true);
		thread.start();
	}

	private static void check() {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_API))
					.timeout(Duration.ofSeconds(15))
					.header("Accept", "application/vnd.github+json")
					.header("User-Agent", "papouhud/" + currentVersion())
					.GET()
					.build();
			HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return;
			}

			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			String tag = json.get("tag_name").getAsString();
			String latest = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
			if (Version.parse(latest).compareTo(Version.parse(currentVersion())) <= 0) {
				return;
			}

			String jarName = null;
			String jarUrl = null;
			for (JsonElement element : json.getAsJsonArray("assets")) {
				JsonObject asset = element.getAsJsonObject();
				String name = asset.get("name").getAsString();
				String url = asset.get("browser_download_url").getAsString();
				if (name.endsWith(".jar") && !name.endsWith("-sources.jar") && url.startsWith(DOWNLOAD_PREFIX)
						&& !name.contains("/") && !name.contains("\\")) {
					jarName = name;
					jarUrl = url;
					break;
				}
			}

			available = new Release(latest, json.get("html_url").getAsString(), jarName, jarUrl);
		} catch (Exception e) {
			// Nessuna rete, GitHub non raggiungibile o risposta inattesa: niente popup.
		}
	}

	/** Chiamato a ogni tick del client: mostra il popup una sola volta, sopra il menu principale. */
	static void tick(Minecraft mc) {
		Release release = available;
		if (prompted || release == null || !(mc.gui.screen() instanceof TitleScreen titleScreen)) {
			return;
		}
		prompted = true;
		mc.gui.setScreen(createPrompt(mc, release, titleScreen, release.jarUrl() != null && SelfUpdater.ownJar() != null));
	}

	/** Il popup di aggiornamento; {@code canInstall} = scarica e installa, altrimenti apre la pagina della release. */
	static ConfirmScreen createPrompt(Minecraft mc, Release release, Screen parent, boolean canInstall) {
		Component title = Component.translatable("papouhud.update.title");
		Component message = Component.translatable(
				canInstall ? "papouhud.update.message" : "papouhud.update.message_manual",
				release.version(), currentVersion());

		return new ConfirmScreen(accepted -> {
			mc.gui.setScreen(parent);
			if (!accepted) {
				return;
			}
			if (canInstall) {
				SelfUpdater.downloadAndSchedule(mc, release);
			} else {
				Util.getPlatform().openUri(release.pageUrl());
			}
		}, title, message,
				Component.translatable(canInstall ? "papouhud.update.yes" : "papouhud.update.open_page"),
				Component.translatable("papouhud.update.no"));
	}
}
