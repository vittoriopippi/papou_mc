package com.giulio.papouhud;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Takes the README screenshots (run with {@code ./gradlew runClientGameTest}). They are saved in
 * the run directory's {@code screenshots} folder: friend panels + compass + coordinates, friends far
 * away, the settings menu, furnace XP, the death marker and the update popup. Along the way it checks
 * that the integrated server sends the player list with the right armor.
 *
 * <p>Skipped when {@code PAPOUHUD_TEST_SERVER} is set: then only {@link ServerGameTest} runs.
 */
public class ScreenshotGameTest implements FabricClientGameTest {
	private static final int WIDTH = 1280;
	private static final int HEIGHT = 720;
	/** Compass heading of the camera: roughly north-east, so the strip shows several letters. */
	private static final float YAW = -145.0f;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("PAPOUHUD_TEST_SERVER") != null) {
			return;
		}
		// Resize the real window so the GUI is laid out for 1280x720, not just rendered at that size.
		context.getInput().resizeWindow(WIDTH, HEIGHT);
		context.waitTicks(2);

		context.runOnClient(mc -> {
			PapouConfig config = PapouConfig.get();
			config.panelsEnabled = true;
			config.compassEnabled = true;
			config.coordsEnabled = true;
		});

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();

			// A few trees in front of the camera so the background is not an empty plain.
			world.getServer().runCommand("time set 1000");
			world.getServer().runCommand("tp @a 0 -60 0 " + YAW + " 8");
			world.getConnection().waitForClientboundPackets();
			for (String offset : new String[] {"9 -60 -12", "4 -60 -15", "13 -60 -6", "-2 -60 -13"}) {
				world.getServer().runCommand("place feature minecraft:oak " + offset);
			}
			world.getConnection().waitForChunksRender();

			serverSendsPlayers(context, world);

			context.runOnClient(mc -> addFriends(mc.level, mc.player));
			// Let command feedback fade out of the chat.
			context.waitTicks(240);
			context.takeScreenshot(options("hud"));

			// The shortcuts, with their default keys: H hides the friend panels and shows them again,
			// M opens the menu. (Keys are SDL scancodes since 26.3: GLFW's M, 77, is End there.)
			tapKey(context, InputConstants.KEY_H);
			context.waitFor(mc -> !PapouConfig.get().panelsEnabled, 20);
			tapKey(context, InputConstants.KEY_H);
			context.waitFor(mc -> PapouConfig.get().panelsEnabled, 20);
			tapKey(context, InputConstants.KEY_M);
			context.waitForScreen(PapouConfigScreen.class);
			// Opening a pausing screen autosaves: wait for the "Saving world" notice to go away.
			context.waitTicks(80);
			context.takeScreenshot(options("settings"));
			context.setScreen(() -> null);

			farFriends(context);

			furnaceXp(context, world);
			deathMarker(context, world);
		}

		context.waitForScreen(TitleScreen.class);
		UpdateChecker.Release release = new UpdateChecker.Release("1.2.0",
				"https://github.com/" + UpdateChecker.REPO + "/releases/tag/v1.2.0", "papouhud-1.2.0.jar",
				UpdateChecker.DOWNLOAD_PREFIX + "v1.2.0/papouhud-1.2.0.jar");
		context.setScreen(() -> UpdateChecker.createPrompt(
				net.minecraft.client.Minecraft.getInstance(), release, new TitleScreen(), true));
		context.waitTicks(5);
		context.takeScreenshot(options("update-popup"));
		context.setScreen(TitleScreen::new);
	}

	/**
	 * A furnace that has smelted 12 raw iron (0.7 XP each) and cooked 20 beef (0.35 XP each) holds
	 * 15.4 XP: the server must send exactly that to the client, which shows it on the title row.
	 */
	private static void furnaceXp(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos pos = new BlockPos(0, -60, 3);
		world.getServer().runCommand("setblock 0 -60 3 minecraft:furnace");
		world.getServer().runOnServer(server -> {
			AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) server.overworld().getBlockEntity(pos);
			furnace.recipesUsed.addTo(recipe("iron_ingot_from_smelting_raw_iron"), 12);
			furnace.recipesUsed.addTo(recipe("cooked_beef"), 20);
			furnace.setItem(0, new ItemStack(Items.RAW_IRON, 5));
			furnace.setItem(1, new ItemStack(Items.COAL, 8));
			furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 12));
			server.getPlayerList().getPlayers().getFirst().openMenu(furnace);
		});

		context.waitForScreen(AbstractFurnaceScreen.class);
		context.waitFor(client -> client.player.containerMenu.containerId == FurnaceXpOverlay.containerId);
		float received = context.computeOnClient(client -> FurnaceXpOverlay.xp);
		if (Math.abs(received - 15.4f) > 0.001f) {
			throw new AssertionError("Expected 15.4 XP from the server, got " + received);
		}

		// Mouse away from the badge, then over it to show the tooltip.
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(5);
		context.takeScreenshot(options("furnace-xp"));

		int[] badge = context.computeOnClient(client -> {
			AbstractFurnaceScreen<?> screen = (AbstractFurnaceScreen<?>) client.gui.screen();
			int scale = client.getWindow().getGuiScale();
			// Inside the badge, which ends 8 px from the right edge on the title row.
			return new int[] {(screen.leftPos + 160) * scale, (screen.topPos + 10) * scale};
		});
		context.getInput().setCursorPos(badge[0], badge[1]);
		context.waitTicks(5);
		context.takeScreenshot(options("furnace-xp-tooltip"));
		context.setScreen(() -> null);
	}

	/**
	 * Die at a known spot, respawn, and check that the skull waypoint points there; capture the
	 * locator bar with the chat reminders, then fast-forward past the 5 minutes and check it is gone.
	 */
	private static void deathMarker(ClientGameTestContext context, TestSingleplayerContext world) {
		BlockPos deathSpot = new BlockPos(25, -60, -10);
		world.getServer().runCommand("tp @a 25 -60 -10");
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(5);
		world.getServer().runCommand("kill @a");
		context.waitForScreen(DeathScreen.class);
		context.runOnClient(mc -> mc.player.respawn());
		context.waitFor(mc -> mc.player != null && mc.player.isAlive() && DeathMarker.deathPos != null);
		context.setScreen(() -> null);

		GlobalPos recorded = context.computeOnClient(mc -> DeathMarker.deathPos);
		if (!recorded.pos().equals(deathSpot) || recorded.dimension() != Level.OVERWORLD) {
			throw new AssertionError("Death marker at " + recorded + ", expected " + deathSpot + " in the overworld");
		}
		context.waitTicks(2);
		if (!context.computeOnClient(mc -> mc.getConnection().getWaypointManager().hasWaypoints())) {
			throw new AssertionError("Death waypoint is not on the locator bar");
		}

		// After respawning, vanilla shows the XP bar for 100 ticks before the locator bar comes back.
		// Look towards the death spot so the skull sits in the middle of the locator bar, then
		// jump to the 1-minute reminder so the chat shows both messages. Friend panels are hidden
		// for this shot so they don't cover the chat.
		context.getInput().lookAt(deathSpot.above());
		context.runOnClient(mc -> PapouConfig.get().panelsEnabled = false);
		context.waitTicks(110);
		context.runOnClient(mc -> DeathMarker.elapsedTicks = DeathMarker.MESSAGE_INTERVAL_TICKS - 1);
		context.waitTicks(5);
		context.takeScreenshot(options("death-marker"));
		context.runOnClient(mc -> PapouConfig.get().panelsEnabled = true);

		context.runOnClient(mc -> DeathMarker.elapsedTicks = DeathMarker.DURATION_TICKS - 1);
		context.waitTicks(3);
		boolean stillThere = context.computeOnClient(
				mc -> DeathMarker.deathPos != null || mc.getConnection().getWaypointManager().hasWaypoints());
		if (stillThere) {
			throw new AssertionError("Death marker still shown after 5 minutes");
		}
	}

	/**
	 * The integrated server sends the player list ({@link PlayersSync}). With one player it would be
	 * empty, so the test switch puts us in our own list; the armor must come through intact.
	 */
	private static void serverSendsPlayers(ClientGameTestContext context, TestSingleplayerContext world) {
		System.setProperty(PlayersSync.INCLUDE_SELF_PROPERTY, "true");
		try {
			world.getServer().runCommand("item replace entity @a armor.head with "
					+ "minecraft:diamond_helmet[damage=100,enchantments={protection:1}]");
			world.getServer().runCommand("item replace entity @a armor.legs with "
					+ "minecraft:leather_leggings[dyed_color=10511680]");
			context.waitFor(mc -> {
				PlayersPayload.Entry me = self(mc);
				return me != null && me.armor().get(0).itemId().equals("minecraft:diamond_helmet")
						&& me.armor().get(2).itemId().equals("minecraft:leather_leggings");
			}, 200);
			context.runOnClient(mc -> checkSelf(mc, self(mc)));
		} finally {
			System.clearProperty(PlayersSync.INCLUDE_SELF_PROPERTY);
			world.getServer().runCommand("item replace entity @a armor.head with minecraft:air");
			world.getServer().runCommand("item replace entity @a armor.legs with minecraft:air");
		}
		// Without the switch the list is empty again: the server sends that once.
		context.waitFor(mc -> RemotePlayers.current().isEmpty(), 100);
	}

	static PlayersPayload.Entry self(net.minecraft.client.Minecraft mc) {
		if (mc.player == null) {
			return null;
		}
		return RemotePlayers.current().stream()
				.filter(entry -> entry.uuid().equals(mc.player.getUUID()))
				.findFirst().orElse(null);
	}

	/** Our own entry, as the server sent it, wearing the diamond helmet and leather leggings above. */
	static void checkSelf(net.minecraft.client.Minecraft mc, PlayersPayload.Entry me) {
		expect(me.name().equals(mc.player.getName().getString()), "name " + me.name());
		expect(me.dimension().equals("minecraft:overworld"), "dimension " + me.dimension());
		expect(me.maxHealth() == 20.0f && me.health() > 0.0f, "health " + me.health() + "/" + me.maxHealth());
		expect(mc.player.position().distanceTo(new Vec3(me.x(), me.y(), me.z())) < 2.0, "position");
		expect(me.armorValue() == 5, "armor points " + me.armorValue() + ", expected 3 + 2");

		PlayersPayload.Armor helmet = me.armor().get(0);
		expect(helmet.damage() == 100 && helmet.maxDamage() == 363 && helmet.glint(), "helmet " + helmet);
		expect(me.armor().get(1).equals(PlayersPayload.Armor.EMPTY), "chestplate " + me.armor().get(1));
		expect(me.armor().get(2).dyeColor() == 10511680, "leggings " + me.armor().get(2));

		ItemStack rebuilt = helmet.toStack();
		expect(rebuilt.is(Items.DIAMOND_HELMET) && rebuilt.getDamageValue() == 100 && rebuilt.hasFoil(),
				"rebuilt helmet " + rebuilt);
	}

	private static void expect(boolean ok, String what) {
		if (!ok) {
			throw new AssertionError("Player list from the server: unexpected " + what);
		}
	}

	/**
	 * Friends the client can't see, as the server sends them: one in the Nether and one 1.3 km away.
	 * Papou stays nearby, so the shot has both kinds.
	 */
	private static void farFriends(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.level.removeEntity(900_001, Entity.RemovalReason.DISCARDED);
			mc.level.removeEntity(900_002, Entity.RemovalReason.DISCARDED);
			Vec3 me = mc.player.position();
			RemotePlayers.set(List.of(
					farFriend("Giulio", "minecraft:the_nether", me, 16.0f, 18, List.of(
							new PlayersPayload.Armor("minecraft:netherite_helmet", 40, 407, true, -1),
							new PlayersPayload.Armor("minecraft:netherite_chestplate", 120, 592, true, -1),
							new PlayersPayload.Armor("minecraft:diamond_leggings", 300, 495, false, -1),
							PlayersPayload.Armor.EMPTY)),
					farFriend("Steve", "minecraft:overworld", me.add(880, 14, -980), 9.0f, 3, List.of(
							new PlayersPayload.Armor("minecraft:iron_helmet", 90, 165, false, -1),
							PlayersPayload.Armor.EMPTY,
							PlayersPayload.Armor.EMPTY,
							new PlayersPayload.Armor("minecraft:leather_boots", 10, 65, false, 0xB02E26)))));
		});
		context.waitTicks(3);
		context.takeScreenshot(options("far-friends"));
		context.runOnClient(mc -> RemotePlayers.set(List.of()));
	}

	private static PlayersPayload.Entry farFriend(String name, String dimension, Vec3 pos, float health, int armorValue,
			List<PlayersPayload.Armor> armor) {
		return new PlayersPayload.Entry(UUID.nameUUIDFromBytes(name.getBytes()), name, dimension,
				pos.x, pos.y, pos.z, health, 20.0f, armorValue, armor);
	}

	private static ResourceKey<Recipe<?>> recipe(String id) {
		return ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(id));
	}

	/**
	 * Holds a key for a couple of ticks, like a real press. The shortcuts are polled once per tick, and
	 * {@code pressKey} releases the key before the next tick runs.
	 */
	private static void tapKey(ClientGameTestContext context, int key) {
		context.getInput().holdKey(key);
		context.waitTicks(2);
		context.getInput().releaseKey(key);
		context.waitTick();
	}

	private static TestScreenshotOptions options(String name) {
		TestScreenshotOptions options = TestScreenshotOptions.of(name);
		options.disableCounterPrefix();
		return options;
	}

	/** Client-only players standing in front of the camera, so the friend panels have something to show. */
	private static void addFriends(ClientLevel level, LocalPlayer me) {
		Vec3 forward = Vec3.directionFromRotation(0.0f, me.getYRot());
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 base = me.position().add(forward.scale(5.0));

		addFriend(level, me, 0, "Papou", base.add(right.scale(-1.8)), 20.0f,
				new Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
				new float[] {0.1f, 0.35f, 0.2f, 0.6f});
		addFriend(level, me, 1, "Steve", base, 13.0f,
				new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, null, Items.GOLDEN_BOOTS},
				new float[] {0.5f, 0.15f, 0.0f, 0.8f});
		addFriend(level, me, 2, "Alex", base.add(right.scale(1.8)), 7.0f,
				new Item[] {Items.LEATHER_HELMET, null, null, null},
				new float[] {0.7f, 0.0f, 0.0f, 0.0f});
	}

	private static void addFriend(ClientLevel level, LocalPlayer me, int index, String name, Vec3 pos, float health,
			Item[] armor, float[] wear) {
		RemotePlayer friend = new RemotePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
		friend.setId(900_000 + index);
		float facing = me.getYRot() + 180.0f + (index - 1) * 12.0f;
		friend.snapTo(pos.x, me.getY(), pos.z, facing, 0.0f);
		friend.setYHeadRot(facing);
		friend.setYBodyRot(facing);

		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		for (int i = 0; i < slots.length; i++) {
			if (armor[i] == null) {
				continue;
			}
			ItemStack stack = new ItemStack(armor[i]);
			stack.setDamageValue(Math.round(stack.getMaxDamage() * wear[i]));
			friend.setItemSlot(slots[i], stack);
		}

		friend.setHealth(health);
		level.addEntity(friend);
	}
}
