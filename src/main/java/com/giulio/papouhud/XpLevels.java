package com.giulio.papouhud;

/** Livelli di esperienza, con le stesse regole del gioco (Player.getXpNeededForNextLevel e giveExperiencePoints). */
final class XpLevels {
	private XpLevels() {
	}

	/** Punti necessari per passare da {@code level} a {@code level + 1}. */
	static int pointsForNextLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		if (level >= 15) {
			return 37 + (level - 15) * 5;
		}
		return 7 + level * 2;
	}

	/**
	 * Il livello (parte intera) e l'avanzamento verso il successivo (parte decimale, da 0 a 1) dopo aver
	 * ricevuto {@code points} punti partendo da {@code level} con la barra a {@code progress} (da 0 a 1).
	 */
	static double levelAfter(int level, float progress, int points) {
		double into = progress * pointsForNextLevel(level) + Math.max(0, points);
		int current = level;
		while (into >= pointsForNextLevel(current)) {
			into -= pointsForNextLevel(current);
			current++;
		}
		return current + into / pointsForNextLevel(current);
	}
}
