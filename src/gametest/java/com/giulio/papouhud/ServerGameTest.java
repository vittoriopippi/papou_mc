package com.giulio.papouhud;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Joins a real server and checks what it sends: a Fabric server with the mod, or a Paper server
 * with the plugin. Runs only when {@code PAPOUHUD_TEST_SERVER=host:port} is set:
 * <pre>
 * PAPOUHUD_TEST_SERVER=127.0.0.1:25590 PAPOUHUD_TEST_FRIEND=Giulio PAPOUHUD_TEST_EXPECT_BARS=1 \
 *     ./gradlew runClientGameTest
 * </pre>
 * <ul>
 *   <li>{@code PAPOUHUD_TEST_FRIEND=<name>}: another player on the server (e.g. a bot), wearing a
 *       diamond helmet (damage 100, Protection) and leather leggings dyed 10511680. The panel for
 *       them must appear with that armor.</li>
 *   <li>Without it the test player is alone, so the server must run with
 *       {@code -Dpapouhud.debug.includeSelf=true} (it puts the player in its own list), and the
 *       armor above must be put on the test player, e.g. through RCON once it has joined.</li>
 *   <li>{@code PAPOUHUD_TEST_EXPECT_BARS=1} (Paper started with {@code -Dpapouhud.debug.barsForAll=true}):
 *       also waits for the friend's bar and takes {@code friend-bars} with the mod's own HUD hidden,
 *       i.e. what a player without the mod sees; then checks that {@code /papou} hides and shows them.</li>
 * </ul>
 * Screenshot of the panels: {@code PAPOUHUD_TEST_SHOT} (default {@code server}).
 */
public class ServerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		String address = System.getenv("PAPOUHUD_TEST_SERVER");
		if (address == null || address.isBlank()) {
			return;
		}
		String friend = System.getenv("PAPOUHUD_TEST_FRIEND");
		boolean expectBars = "1".equals(System.getenv("PAPOUHUD_TEST_EXPECT_BARS"));
		String shot = System.getenv().getOrDefault("PAPOUHUD_TEST_SHOT", "server");

		context.getInput().resizeWindow(1280, 720);
		context.waitTicks(2);
		context.runOnClient(mc -> ConnectScreen.startConnecting(new TitleScreen(), mc,
				ServerAddress.parseString(address), new ServerData("Papou test", address, ServerData.Type.OTHER),
				false, null));
		context.waitFor(mc -> mc.player != null && mc.level != null && mc.gui.screen() == null, 20 * 120);

		// The armor is put on from outside once everyone is in: wait for it to show up.
		context.waitFor(mc -> {
			PlayersPayload.Entry entry = target(mc, friend);
			return entry != null && entry.armor().get(0).itemId().equals("minecraft:diamond_helmet")
					&& entry.armor().get(2).itemId().equals("minecraft:leather_leggings");
		}, 20 * 120);
		if (friend == null) {
			context.runOnClient(mc -> ScreenshotGameTest.checkSelf(mc, ScreenshotGameTest.self(mc)));
		} else {
			context.runOnClient(mc -> checkFriend(target(mc, friend)));
			// Far away (the bot is sent 120+ blocks off): the panel comes from the server's data.
			context.waitFor(mc -> mc.level.getPlayerByUUID(target(mc, friend).uuid()) == null, 20 * 60);
		}

		if (expectBars) {
			String name = friend != null ? friend : context.computeOnClient(mc -> mc.player.getName().getString());
			context.waitFor(mc -> bars(mc).values().stream().anyMatch(
					bar -> bar.getName().getString().startsWith(name) && bar.getName().getString().contains("❤")),
					20 * 30);
			context.runOnClient(mc -> showHud(false));
			// Let the join toasts and the chat fade, so the bar is all there is.
			context.waitTicks(220);
			context.takeScreenshot(options("friend-bars"));
			context.runOnClient(mc -> showHud(true));

			// /papou hides the bars, a second /papou brings them back.
			context.runOnClient(mc -> mc.player.connection.sendCommand("papou"));
			context.waitFor(mc -> bars(mc).isEmpty(), 20 * 10);
			context.runOnClient(mc -> mc.player.connection.sendCommand("papou"));
			context.waitFor(mc -> !bars(mc).isEmpty(), 20 * 10);
		}
		context.waitTicks(20);
		context.takeScreenshot(options(shot));

		context.runOnClient(mc -> mc.disconnect(new TitleScreen(), false));
		context.waitForScreen(TitleScreen.class);
	}

	private static PlayersPayload.Entry target(Minecraft mc, String friend) {
		if (friend == null) {
			return ScreenshotGameTest.self(mc);
		}
		return RemotePlayers.current().stream()
				.filter(entry -> entry.name().equals(friend))
				.findFirst().orElse(null);
	}

	private static void checkFriend(PlayersPayload.Entry friend) {
		PlayersPayload.Armor helmet = friend.armor().get(0);
		boolean ok = friend.dimension().equals("minecraft:overworld")
				&& friend.maxHealth() == 20.0f && friend.health() > 0.0f
				&& friend.armorValue() == 5
				&& helmet.damage() == 100 && helmet.maxDamage() == 363 && helmet.glint()
				&& friend.armor().get(1).equals(PlayersPayload.Armor.EMPTY)
				&& friend.armor().get(2).dyeColor() == 10511680;
		if (!ok) {
			throw new AssertionError("Friend's entry from the server: " + friend);
		}
	}

	private static void showHud(boolean show) {
		PapouConfig config = PapouConfig.get();
		config.panelsEnabled = show;
		config.compassEnabled = show;
		config.coordsEnabled = show;
	}

	private static TestScreenshotOptions options(String name) {
		TestScreenshotOptions options = TestScreenshotOptions.of(name);
		options.disableCounterPrefix();
		return options;
	}

	/** The boss bars the client is showing (the overlay keeps them in a private map). */
	@SuppressWarnings("unchecked")
	private static Map<UUID, LerpingBossEvent> bars(Minecraft mc) {
		try {
			Field events = BossHealthOverlay.class.getDeclaredField("events");
			events.setAccessible(true);
			return (Map<UUID, LerpingBossEvent>) events.get(mc.gui.hud.getBossOverlay());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Can't read the boss bars", e);
		}
	}
}
