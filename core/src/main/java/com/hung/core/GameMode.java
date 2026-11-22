package com.hung.core;

import com.hung.util.Constants;

/**
 * Game modes the player picks for a new game. Each mode will get its own map (difficulty of the map
 * generator); for now only HARD has one: the bunker currently exported into the game. A mode without
 * a map shows as "Coming soon" and can't be started.
 */
public enum GameMode {
    EASY("Easy", null, "Coming soon"),
    NORMAL("Normal", null, "Coming soon"),
    HARD("Hard", Constants.START_MAP_PATH, "The bunker as generated: full enemy density, traps and the Ravager");

    public final String label;
    /** The level this mode plays; null = not available yet. */
    public final String mapPath;
    public final String description;

    GameMode(String label, String mapPath, String description) {
        this.label = label;
        this.mapPath = mapPath;
        this.description = description;
    }

    public boolean isAvailable() {
        return mapPath != null;
    }

    /** The mode with this name; null when the name is unknown (an old or edited save). */
    public static GameMode fromName(String name) {
        if (name == null) return null;
        for (GameMode mode : values()) if (mode.name().equals(name)) return mode;
        return null;
    }
}
