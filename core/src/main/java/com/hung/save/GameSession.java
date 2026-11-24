package com.hung.save;

import com.hung.core.GameMode;

/**
 * The run being played: its mode (which also names its save), the time played so far and, when
 * continuing, the save it started from (null for a new game).
 */
public class GameSession {

    public final GameMode mode;
    /** The save this run continues; null = new game. */
    public final SaveData loaded;
    /** Seconds played in this run, including the time before the save it continues. */
    public float playTime;
    public boolean isComplete;

    public GameSession(GameMode mode, SaveData loaded) {
        this.mode = mode;
        this.loaded = loaded;
        this.playTime = loaded != null ? loaded.playTime : 0f;
        this.isComplete = loaded != null && loaded.isComplete;
    }
}
