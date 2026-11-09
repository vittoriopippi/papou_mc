package com.giulio.papouhud;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Takes the README screenshots (run with {@code ./gradlew runClientGameTest}). They are saved in
 * the run directory's {@code screenshots} folder: friend panels + compass + coordinates, the settings
 * menu and the update popup.
 */
public class ScreenshotGameTest implements FabricClientGameTest {
	private static final int WIDTH = 1280;
	private static final int HEIGHT = 720;
	/** Compass heading of the camera: roughly north-east, so the strip shows several letters. */
	private static final float YAW = -145.0f;

	@Override
	public void runTest(ClientGameTestContext context) {
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

			context.runOnClient(mc -> addFriends(mc.level, mc.player));
			// Let command feedback fade out of the chat.
			context.waitTicks(240);
			context.takeScreenshot(options("hud"));

			context.setScreen(() -> new PapouConfigScreen(null));
			// Opening a pausing screen autosaves: wait for the "Saving world" notice to go away.
			context.waitTicks(80);
			context.takeScreenshot(options("settings"));
			context.setScreen(() -> null);

			furnaceXp(context, world);
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
	 * 15.4 XP: the server must send exactly that to the client, which shows it under the output slot.
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

		context.waitTicks(5);
		context.takeScreenshot(options("furnace-xp"));
		context.setScreen(() -> null);
	}

	private static ResourceKey<Recipe<?>> recipe(String id) {
		return ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace(id));
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
