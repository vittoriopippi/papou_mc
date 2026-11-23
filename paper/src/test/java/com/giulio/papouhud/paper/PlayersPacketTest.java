package com.giulio.papouhud.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The plugin must write exactly the bytes the mod reads: the mod's PlayersPayloadTest checks the
 * same file (src/test/resources/players-v1.hex). The sample must stay identical to the one there.
 */
class PlayersPacketTest {
	static List<PlayersPacket.Entry> sample() {
		PlayersPacket.Armor empty = PlayersPacket.Armor.EMPTY;
		return List.of(
				new PlayersPacket.Entry(UUID.fromString("6f2a3b1c-0d4e-4f5a-8b6c-7d8e9fa0b1c2"), "Giulio",
						"minecraft:the_nether", -123.5, 64.0, 2048.25, 13.5f, 20.0f, 15, List.of(
								new PlayersPacket.Armor("minecraft:diamond_helmet", 10, 363, true, -1),
								empty,
								new PlayersPacket.Armor("minecraft:leather_leggings", 3, 75, false, 0xA06540),
								empty)),
				new PlayersPacket.Entry(UUID.fromString("00000000-0000-0000-0009-01fea8752b01"), ".OutLeader942794",
						"minecraft:overworld", 10_000_000.125, -59.0, -300_000.75, 0.0f, 40.0f, 300, List.of(
								new PlayersPacket.Armor("minecraft:netherite_helmet", 400, 407, false, -1),
								new PlayersPacket.Armor("minecraft:elytra", 431, 432, true, -1),
								empty,
								new PlayersPacket.Armor("minecraft:golden_boots", 0, 91, false, -1))));
	}

	@Test
	void writesTheSameBytesAsTheMod() throws IOException {
		String expected = Files.readString(Path.of(System.getProperty("papouhud.golden"))).strip();
		assertEquals(expected, HexFormat.of().formatHex(PlayersPacket.write(sample())));
	}

	@Test
	void arrowPointsWhereTheFriendIs() {
		// Facing south (yaw 0): a friend further south is ahead, one to the west (-x) is on the right.
		assertEquals("↑", arrow(0, 0, 0, 50));
		assertEquals("→", arrow(0, 0, -50, 0));
		assertEquals("←", arrow(0, 0, 50, 0));
		assertEquals("↓", arrow(0, 0, 0, -50));
		// Facing west (yaw 90): a friend to the north (-z) is on the right.
		assertEquals("→", arrow(90, 0, 0, -50));
		assertEquals("↗", arrow(0, 0, -50, 50));
	}

	private static String arrow(float yaw, double fromX, double toX, double toZ) {
		String text = FriendBars.distanceText(50, yaw, toX - fromX, toZ);
		return text.substring(text.lastIndexOf(' ') + 1);
	}
}
