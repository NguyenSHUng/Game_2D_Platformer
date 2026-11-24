package com.hung.core;

import com.hung.entity.enemy.EnemySkill;
import com.hung.util.Constants;

/**
 * Game modes the player picks for a new game. Each has its own bunker, longer and
 * with more and stronger enemies from EASY to HARD ({@link EnemySkill}). A locked
 * mode is shown but can't be started or continued (its save is kept).
 */
public enum GameMode {
    EASY("Easy", Constants.EASY_MAP_PATH, EnemySkill.EASY, "Short, for a first run", false),
    MEDIUM("Medium", Constants.MEDIUM_MAP_PATH, EnemySkill.MEDIUM, "Longer, enemies jump and hunt in packs", false),
    HARD("Hard", Constants.HARD_MAP_PATH, EnemySkill.HARD, "Longest, toughest enemies", true);

    public final String label;
    public final String mapPath;
    /** Strength and behaviour of this mode's enemies. */
    public final EnemySkill enemySkill;
    public final String description;
    private final boolean isLocked;

    GameMode(String label, String mapPath, EnemySkill enemySkill, String description, boolean isLocked) {
        this.label = label;
        this.mapPath = mapPath;
        this.enemySkill = enemySkill;
        this.description = description;
        this.isLocked = isLocked;
    }

    public boolean isAvailable() {
        return !isLocked;
    }

    /** The mode with this name; null when the name is unknown (an old or edited save). */
    public static GameMode fromName(String name) {
        if (name == null) return null;
        for (GameMode mode : values()) if (mode.name().equals(name)) return mode;
        return null;
    }
}
