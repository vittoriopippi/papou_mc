package com.giulio.papouhud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * The papouhud:furnace_xp bytes must match src/test/resources/furnace-xp-v1.hex, which the Paper
 * plugin's FurnaceXpTest checks too: window 3, 15.4 XP.
 */
class FurnaceXpPayloadTest {
	private static final Path GOLDEN = Path.of("src/test/resources/furnace-xp-v1.hex");

	@Test
	void writesTheSharedBytes() throws IOException {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		FurnaceXpPayload.CODEC.encode(buf, new FurnaceXpPayload(3, 15.4f));
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		assertEquals(Files.readString(GOLDEN).strip(), HexFormat.of().formatHex(bytes));
	}

	@Test
	void readsTheSharedBytes() throws IOException {
		byte[] bytes = HexFormat.of().parseHex(Files.readString(GOLDEN).strip());
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
		FurnaceXpPayload payload = FurnaceXpPayload.CODEC.decode(buf);
		assertEquals(3, payload.containerId());
		assertEquals(15.4f, payload.xp());
	}
}
