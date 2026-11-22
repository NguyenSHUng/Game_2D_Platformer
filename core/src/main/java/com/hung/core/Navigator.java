package com.hung.core;

import com.hung.save.SaveData;

/**
 * Moves between the main menu and a run (MyGame implements it). Screens call it from their menus;
 * the switch happens after the current frame, so the calling screen can finish drawing it.
 */
public interface Navigator {

    /** Starts a fresh run of this mode, saving into this slot (0-based). */
    void startNewGame(int slot, GameMode mode);

    /** Continues the run saved in this slot. */
    void continueGame(int slot, SaveData save);

    void returnToMainMenu();

    void quit();
}
