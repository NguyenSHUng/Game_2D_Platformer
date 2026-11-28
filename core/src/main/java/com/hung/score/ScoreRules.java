package com.hung.score;

import com.hung.combat.ChestTier;

/** Points per scoring event; the only place score values live. */
public record ScoreRules(int enemyKill, int woodChest, int goldChest, int roomClear, int mapComplete) {

    public static final ScoreRules DEFAULT = new ScoreRules(100, 150, 300, 250, 1000);

    public int chest(ChestTier tier) {
        return tier == ChestTier.GOLD ? goldChest : woodChest;
    }
}
