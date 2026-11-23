package com.giulio.papouhud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * The papouhud:players bytes must match src/test/resources/players-v1.hex, which the Paper plugin's
 * PlayersPacketTest checks too: that is what keeps the mod and the plugin compatible.
 * The sample here and the one in PlayersPacketTest must stay identical.
 */
class PlayersPayloadTest {
	private static final Path GOLDEN = Path.of("src/test/resources/players-v1.hex");

	static PlayersPayload sample() {
		PlayersPayload.Armor empty = PlayersPayload.Armor.EMPTY;
		return new PlayersPayload(List.of(
				new PlayersPayload.Entry(UUID.fromString("6f2a3b1c-0d4e-4f5a-8b6c-7d8e9fa0b1c2"), "Giulio",
						"minecraft:the_nether", -123.5, 64.0, 2048.25, 13.5f, 20.0f, 15, List.of(
								new PlayersPayload.Armor("minecraft:diamond_helmet", 10, 363, true, -1),
								empty,
								new PlayersPayload.Armor("minecraft:leather_leggings", 3, 75, false, 0xA06540),
								empty)),
				new PlayersPayload.Entry(UUID.fromString("00000000-0000-0000-0009-01fea8752b01"), ".OutLeader942794",
						"minecraft:overworld", 10_000_000.125, -59.0, -300_000.75, 0.0f, 40.0f, 300, List.of(
								new PlayersPayload.Armor("minecraft:netherite_helmet", 400, 407, false, -1),
								new PlayersPayload.Armor("minecraft:elytra", 431, 432, true, -1),
								empty,
								new PlayersPayload.Armor("minecraft:golden_boots", 0, 91, false, -1)))));
	}

	@Test
	void writesTheSharedBytes() throws IOException {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		PlayersPayload.CODEC.encode(buf, sample());
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		String hex = HexFormat.of().formatHex(bytes);
		if (!Files.exists(GOLDEN)) {
			Files.writeString(GOLDEN, hex + "\n");
			fail("Wrote " + GOLDEN + " (it was missing): check it in and run the tests again");
		}
		assertEquals(Files.readString(GOLDEN).strip(), hex);
	}

	@Test
	void readsTheSharedBytes() throws IOException {
		byte[] bytes = HexFormat.of().parseHex(Files.readString(GOLDEN).strip());
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
		PlayersPayload payload = PlayersPayload.CODEC.decode(buf);
		assertEquals(sample(), payload);
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void skipsAFutureVersion() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarInt(PlayersPayload.VERSION + 1);
		buf.writeBytes(new byte[] {1, 2, 3, 4, 5});
		PlayersPayload payload = PlayersPayload.CODEC.decode(buf);
		assertEquals(PlayersPayload.VERSION + 1, payload.version());
		assertTrue(payload.players().isEmpty());
		assertEquals(0, buf.readableBytes());
	}
}
