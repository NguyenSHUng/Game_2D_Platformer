package com.hung.save;

/**
 * One saved run, as written to its mode's save file (JSON, see SaveManager). Plain data: GameScreen fills it
 * from the World when saving and applies it after loading the level.
 *
 * Bump {@link #CURRENT_VERSION} when a change makes older files unreadable; older versions are then
 * upgraded (or refused as incompatible) in SaveManager.
 */
public class SaveData {

    public static final int CURRENT_VERSION = 1;

    /** Saved at a checkpoint (position = the checkpoint). */
    public static final String REASON_CHECKPOINT = "CHECKPOINT";
    /** Saved when leaving the game (back to the menu, quitting, closing the window): where the player stood. */
    public static final String REASON_QUIT = "QUIT";
    /** The player reached the exit. */
    public static final String REASON_COMPLETE = "COMPLETE";

    public int version = CURRENT_VERSION;
    /** GameMode name (HARD…). */
    public String mode;
    /** The level played, and its shape when saved (grid size, rooms, checkpoints) to spot a re-exported map. */
    public String mapPath;
    public String mapSignature;
    /** Wall-clock time of the save, epoch milliseconds. */
    public long savedAt;
    /** Seconds of play in this run (paused time not counted). */
    public float playTime;
    /** REASON_CHECKPOINT, REASON_QUIT or REASON_COMPLETE. */
    public String reason;
    public boolean isComplete;

    /** Where the player comes back: feet point, world units. */
    public float x;
    public float y;
    /** The respawn point (last checkpoint touched, else the level spawn), world units. */
    public float respawnX;
    public float respawnY;
    /** 0-based index of the last checkpoint touched; -1 = none yet. */
    public int checkpointIndex = -1;
    public int checkpointCount;

    public int health;
    public int maxHealth;
    /** Arsenal slot in hand, and the ammo left in each weapon's magazine (same order as weaponIds). */
    public int weaponSlot;
    public String[] weaponIds;
    public int[] ammo;

    /** Explored rooms (Room.index) for the minimap, the room the player was in and its id (for the menu). */
    public int[] visitedRooms;
    public int[] knownRooms;
    public int roomCount;
    public String roomId;
}
