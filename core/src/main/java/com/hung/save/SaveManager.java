package com.hung.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.hung.core.GameMode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Predicate;

/**
 * The save slots: one JSON file per slot under the player's home folder (~/.mygame/saves/slot_N.json),
 * outside the game's assets and the repository. Reading never throws: a missing file is an EMPTY slot,
 * a file that doesn't parse is CORRUPT, one from a newer version / unknown mode / missing map is
 * INCOMPATIBLE — the menu shows why and lets the player delete or overwrite it. Writes go to a
 * temporary file first, so a crash mid-write never destroys the previous save.
 */
public class SaveManager {

    private static final String TAG = "SaveManager";
    public static final int SLOT_COUNT = 3;
    private static final String FOLDER = ".mygame/saves/";
    private static final String TEMP_SUFFIX = ".tmp";

    public enum Status { EMPTY, OK, CORRUPT, INCOMPATIBLE }

    /** What a slot holds: its data when OK (or INCOMPATIBLE but parsed), else why it can't be played. */
    public static class Slot {
        /** 0-based. */
        public final int index;
        public final Status status;
        public final SaveData data;
        public final String problem;

        Slot(int index, Status status, SaveData data, String problem) {
            this.index = index;
            this.status = status;
            this.data = data;
            this.problem = problem;
        }

        public boolean isPlayable() { return status == Status.OK; }
        public boolean isUsed() { return status != Status.EMPTY; }
    }

    private final Json json;
    private final FileHandle folder;
    private final Predicate<String> isMapInstalled;

    public SaveManager() {
        this(Gdx.files.external(FOLDER), path -> Gdx.files.internal(path).exists());
    }

    /** Slots in `folder`; isMapInstalled tells whether a save's map still exists (tests pass their own). */
    public SaveManager(FileHandle folder, Predicate<String> isMapInstalled) {
        this.folder = folder;
        this.isMapInstalled = isMapInstalled;
        json = new Json(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        json.setUsePrototypes(false);
    }

    public Slot read(int index) {
        FileHandle file = file(index);
        if (!file.exists()) return new Slot(index, Status.EMPTY, null, null);
        SaveData data;
        try {
            data = json.fromJson(SaveData.class, file);
        } catch (RuntimeException e) {
            error("Slot " + (index + 1) + " is unreadable: " + e.getMessage());
            return new Slot(index, Status.CORRUPT, null, "Unreadable save file");
        }
        if (data == null) return new Slot(index, Status.CORRUPT, null, "Empty save file");
        if (data.version > SaveData.CURRENT_VERSION) {
            return new Slot(index, Status.INCOMPATIBLE, data, "Saved by a newer version of the game");
        }
        GameMode mode = GameMode.fromName(data.mode);
        if (mode == null || !mode.isAvailable()) return new Slot(index, Status.INCOMPATIBLE, data, "Unknown game mode");
        if (data.mapPath == null || !isMapInstalled.test(data.mapPath)) {
            return new Slot(index, Status.INCOMPATIBLE, data, "Its map is no longer installed");
        }
        return new Slot(index, Status.OK, data, null);
    }

    public Array<Slot> readAll() {
        Array<Slot> slots = new Array<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) slots.add(read(i));
        return slots;
    }

    /** The most recently saved slot that can be continued (playable, run not complete); null = none. */
    public Slot latestContinuable() {
        Slot latest = null;
        for (Slot slot : readAll()) {
            if (!slot.isPlayable() || slot.data.isComplete) continue;
            if (latest == null || slot.data.savedAt > latest.data.savedAt) latest = slot;
        }
        return latest;
    }

    /** Writes a slot; false (and the old file kept) when it couldn't be written. */
    public boolean write(int index, SaveData data) {
        FileHandle file = file(index);
        FileHandle temp = file.sibling(file.name() + TEMP_SUFFIX);
        try {
            temp.writeString(json.prettyPrint(json.toJson(data, SaveData.class)), false, "UTF-8");
            temp.moveTo(file);
            return true;
        } catch (RuntimeException e) {
            error("Could not write slot " + (index + 1) + ": " + e.getMessage());
            return false;
        }
    }

    public void delete(int index) {
        file(index).delete();
        if (Gdx.app != null) Gdx.app.log(TAG, "Deleted slot " + (index + 1));
    }

    /** One line about a slot for the menus: "Hard · room_6 · 4/12 rooms · 0:12:34 · 2026-11-21 14:05". */
    public static String describe(Slot slot) {
        if (slot.status == Status.EMPTY) return "Empty";
        if (slot.status == Status.CORRUPT) return slot.problem + " - delete or overwrite it";
        SaveData data = slot.data;
        GameMode mode = GameMode.fromName(data.mode);
        StringBuilder text = new StringBuilder();
        text.append(mode != null ? mode.label : data.mode);
        if (data.isComplete) text.append(" - COMPLETE");
        else if (data.roomId != null) text.append(" - ").append(data.roomId);
        if (data.roomCount > 0 && data.visitedRooms != null) {
            text.append(" - ").append(data.visitedRooms.length).append('/').append(data.roomCount).append(" rooms");
        }
        text.append(" - ").append(formatTime(data.playTime));
        text.append(" - ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(data.savedAt)));
        if (slot.status == Status.INCOMPATIBLE) text.append(" (").append(slot.problem).append(')');
        return text.toString();
    }

    /** Seconds → h:mm:ss. */
    public static String formatTime(float seconds) {
        int total = Math.max(0, (int) seconds);
        return String.format("%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60);
    }

    private FileHandle file(int index) {
        return folder.child("slot_" + (index + 1) + ".json");
    }

    // Gdx.app is absent in unit tests
    private static void error(String message) {
        if (Gdx.app != null) Gdx.app.error(TAG, message);
    }
}
