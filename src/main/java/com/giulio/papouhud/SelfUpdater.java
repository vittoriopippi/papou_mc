package com.giulio.papouhud;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Scarica la nuova versione accanto a quella attuale con un nome che Fabric ignora
 * ({@code .jar.download}) e la installa alla chiusura del gioco. In nessun momento ci sono due
 * jar di Papou HUD attivi nella cartella mods, quindi un aggiornamento fallito non blocca l'avvio.
 */
final class SelfUpdater {
	private static final String PENDING_NAME = "papouhud-update.jar.download";
	private static final String BACKUP_SUFFIX = ".old";
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(10000L);

	private static volatile boolean started;

	private SelfUpdater() {
	}

	/** Il jar da cui e' stata caricata la mod, o null se non e' un singolo file .jar (es. sviluppo). */
	static Path ownJar() {
		return FabricLoader.getInstance().getModContainer(UpdateChecker.MOD_ID).map(mod -> {
			ModOrigin origin = mod.getOrigin();
			if (origin.getKind() != ModOrigin.Kind.PATH) {
				return null;
			}
			List<Path> paths = origin.getPaths();
			if (paths.size() != 1) {
				return null;
			}
			Path path = paths.get(0).toAbsolutePath();
			return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar") ? path : null;
		}).orElse(null);
	}

	/** Rimuove download non installati e copie di sicurezza rimaste da un aggiornamento precedente. */
	static void cleanUpLeftovers() {
		Path jar = ownJar();
		if (jar == null) {
			return;
		}
		Path dir = jar.getParent();
		try {
			Files.deleteIfExists(dir.resolve(PENDING_NAME));
			try (DirectoryStream<Path> backups = Files.newDirectoryStream(dir, "papouhud*.jar" + BACKUP_SUFFIX)) {
				for (Path backup : backups) {
					Files.deleteIfExists(backup);
				}
			}
		} catch (IOException e) {
			// Non grave: ci si riprova al prossimo avvio.
		}
	}

	static void downloadAndSchedule(Minecraft mc, UpdateChecker.Release release) {
		Path current = ownJar();
		if (started || current == null || release.jarUrl() == null) {
			return;
		}
		started = true;

		Path pending = current.resolveSibling(PENDING_NAME);
		Path target = current.resolveSibling(release.jarName());
		toast(mc, Component.translatable("papouhud.update.downloading", release.version()), Component.empty());

		Thread thread = new Thread(() -> {
			try {
				download(release, pending);
				Runtime.getRuntime().addShutdownHook(
						new Thread(() -> install(current, pending, target), "Papou HUD update install"));
				toast(mc, Component.translatable("papouhud.update.downloaded", release.version()),
						Component.translatable("papouhud.update.restart"));
			} catch (Exception e) {
				try {
					Files.deleteIfExists(pending);
				} catch (IOException ignored) {
					// Verra' rimosso al prossimo avvio.
				}
				toast(mc, Component.translatable("papouhud.update.failed"),
						Component.translatable("papouhud.update.failed_hint"));
			}
		}, "Papou HUD update download");
		thread.setDaemon(true);
		thread.start();
	}

	private static void toast(Minecraft mc, Component title, Component message) {
		mc.execute(() -> SystemToast.addOrUpdate(mc.gui.toastManager(), TOAST, title, message));
	}

	private static void download(UpdateChecker.Release release, Path pending) throws Exception {
		Files.deleteIfExists(pending);
		HttpRequest request = HttpRequest.newBuilder(URI.create(release.jarUrl()))
				.timeout(Duration.ofMinutes(2))
				.header("User-Agent", "papouhud/" + UpdateChecker.currentVersion())
				.GET()
				.build();
		HttpResponse<Path> response = UpdateChecker.httpClient().send(request, HttpResponse.BodyHandlers.ofFile(
				pending, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		verify(pending, release.version());
	}

	/** Il file scaricato deve essere un jar di Papou HUD con la versione annunciata dalla release. */
	private static void verify(Path jar, String expectedVersion) throws IOException {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				throw new IOException("fabric.mod.json mancante");
			}
			try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (!UpdateChecker.MOD_ID.equals(json.get("id").getAsString())
						|| !expectedVersion.equals(json.get("version").getAsString())) {
					throw new IOException("jar inatteso");
				}
			}
		} catch (RuntimeException e) {
			throw new IOException(e);
		}
	}

	/** Alla chiusura: sostituisce il jar. Su Windows il jar in uso e' bloccato, quindi lo fa un processo a parte. */
	private static void install(Path current, Path pending, Path target) {
		if (!Files.isRegularFile(pending)) {
			return;
		}
		Path backup = current.resolveSibling(current.getFileName() + BACKUP_SUFFIX);
		try {
			Files.move(current, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			if (isWindows()) {
				launchWindowsHelper(current, backup, pending, target);
			}
			return;
		}
		try {
			Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			try {
				Files.move(backup, current, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// Resta la copia .old: si puo' rinominare a mano.
			}
			return;
		}
		try {
			Files.deleteIfExists(backup);
		} catch (IOException ignored) {
			// Verra' rimossa al prossimo avvio.
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
	}

	/**
	 * Avvia PowerShell nascosto: aspetta che il gioco si chiuda, poi sposta il vecchio jar in .old,
	 * mette al suo posto quello nuovo e, se qualcosa va storto, rimette il vecchio.
	 */
	private static void launchWindowsHelper(Path current, Path backup, Path pending, Path target) {
		String script = String.join("\n",
				"$old = " + psQuote(current),
				"$bak = " + psQuote(backup),
				"$new = " + psQuote(pending),
				"$dst = " + psQuote(target),
				"try { Wait-Process -Id " + ProcessHandle.current().pid() + " -Timeout 300 -ErrorAction Stop } catch { }",
				"$moved = $false",
				"for ($i = 0; $i -lt 30 -and -not $moved; $i++) {",
				"  try { Move-Item -LiteralPath $old -Destination $bak -Force -ErrorAction Stop; $moved = $true }",
				"  catch { Start-Sleep -Seconds 1 }",
				"}",
				"if ($moved) {",
				"  try {",
				"    Move-Item -LiteralPath $new -Destination $dst -Force -ErrorAction Stop",
				"    Remove-Item -LiteralPath $bak -Force -ErrorAction SilentlyContinue",
				"  } catch {",
				"    Move-Item -LiteralPath $bak -Destination $old -Force -ErrorAction SilentlyContinue",
				"  }",
				"}");
		String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		try {
			new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
					"-WindowStyle", "Hidden", "-EncodedCommand", encoded)
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
		} catch (IOException ignored) {
			// Niente PowerShell: il download resta li' e verra' rimosso al prossimo avvio.
		}
	}

	private static String psQuote(Path path) {
		return "'" + path.toAbsolutePath().toString().replace("'", "''") + "'";
	}
}
