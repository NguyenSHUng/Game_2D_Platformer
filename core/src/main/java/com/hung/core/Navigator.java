package com.hung.core;

import com.hung.save.SaveData;

/**
 * Moves between the main menu and a run (MyGame implements it). Screens call it from their menus;
 * the switch happens after the current frame, so the calling screen can finish drawing it.
 */
public interface Navigator {

    /** Starts a fresh run of this mode, replacing its save. */
    void startNewGame(GameMode mode);

    /** Continues the run of this save. */
    void continueGame(SaveData save);

    void returnToMainMenu();

    void quit();
}
