package com.giulio.papouhud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class XpLevelsTest {
	@Test
	void pointsPerLevelMatchTheGame() {
		assertEquals(7, XpLevels.pointsForNextLevel(0));
		assertEquals(35, XpLevels.pointsForNextLevel(14));
		assertEquals(37, XpLevels.pointsForNextLevel(15));
		assertEquals(107, XpLevels.pointsForNextLevel(29));
		assertEquals(112, XpLevels.pointsForNextLevel(30));
		assertEquals(121, XpLevels.pointsForNextLevel(31));
	}

	@Test
	void levelAfterAddsPointsAcrossLevels() {
		// From 0: level 15 takes 315 points in total, level 16 takes 352.
		assertEquals(15.0, XpLevels.levelAfter(0, 0.0f, 315), 1e-9);
		assertEquals(16.0, XpLevels.levelAfter(0, 0.0f, 352), 1e-9);
		// From 30: 1395 points to get there, 1507 for level 31.
		assertEquals(31.0, XpLevels.levelAfter(30, 0.0f, 112), 1e-9);
		// Half of level 10 (13.5 of 27 points) + 20 points = 33.5 -> level 11 with 6.5 of 29.
		assertEquals(11.0 + 6.5 / 29.0, XpLevels.levelAfter(10, 0.5f, 20), 1e-6);
		assertEquals(10.5, XpLevels.levelAfter(10, 0.5f, 0), 1e-9);
	}
}
