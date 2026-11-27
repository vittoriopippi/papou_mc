package com.giulio.papouhud.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

/** The plugin must write the bytes the mod reads: the mod's FurnaceXpPayloadTest checks the same file. */
class FurnaceXpTest {
	@Test
	void writesTheSameBytesAsTheMod() throws IOException {
		String expected = Files.readString(Path.of(System.getProperty("papouhud.golden.furnace"))).strip();
		assertEquals(expected, HexFormat.of().formatHex(FurnaceXp.write(3, 15.4f)));
	}
}
