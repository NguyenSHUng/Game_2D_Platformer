package com.hung.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import com.hung.core.GameMode;

import java.util.Locale;

/**
 * The saves: one JSON file per game mode under the player's home folder (~/.mygame/saves/easy.json …),
 * outside the game's assets and the repository. Reading never throws: a missing file is EMPTY,
 * a file that doesn't parse is CORRUPT, one from a newer version or of another mode is
 * INCOMPATIBLE — the menu then only offers to start over. Writes go to a temporary file first, so a
 * crash mid-write never destroys the previous save.
 */
public class SaveManager {

    private static final String TAG = "SaveManager";
    private static final String FOLDER = ".mygame/saves/";
    private static final String TEMP_SUFFIX = ".tmp";
    /** Files of the three save slots the game had until 2026-11-24, and where they go once imported. */
    private static final String[] OLD_SLOT_FILES = {"slot_1.json", "slot_2.json", "slot_3.json"};
    private static final String OLD_SLOT_FOLDER = "old_slots/";

    public enum Status { EMPTY, OK, CORRUPT, INCOMPATIBLE }

    /** The save of one mode: its data when OK (or INCOMPATIBLE but parsed), else why it can't be played. */
    public static class Save {
        public final GameMode mode;
        public final Status status;
        public final SaveData data;
        public final String problem;

        Save(GameMode mode, Status status, SaveData data, String problem) {
            this.mode = mode;
            this.status = status;
            this.data = data;
            this.problem = problem;
        }

        public boolean isPlayable() { return status == Status.OK; }
        public boolean isUsed() { return status != Status.EMPTY; }
        /** An unfinished run of a mode that isn't locked. */
        public boolean isContinuable() { return isPlayable() && !data.isComplete && mode.isAvailable(); }
    }

    private final Json json;
    private final FileHandle folder;

    public SaveManager() {
        this(Gdx.files.external(FOLDER));
    }

    /** Saves in `folder` (tests pass their own). */
    public SaveManager(FileHandle folder) {
        this.folder = folder;
        json = new Json(JsonWriter.OutputType.json);
        json.setIgnoreUnknownFields(true);
        json.setUsePrototypes(false);
        importOldSlots();
    }

    public Save read(GameMode mode) {
        FileHandle file = file(mode);
        if (!file.exists()) return new Save(mode, Status.EMPTY, null, null);
        SaveData data = parse(file);
        if (data == null) return new Save(mode, Status.CORRUPT, null, "Unreadable save");
        if (data.version > SaveData.CURRENT_VERSION) {
            return new Save(mode, Status.INCOMPATIBLE, data, "Saved by a newer version");
        }
        if (GameMode.fromName(data.mode) != mode) return new Save(mode, Status.INCOMPATIBLE, data, "Save of another mode");
        return new Save(mode, Status.OK, data, null);
    }

    /** The most recently saved run that can be continued; null = none. */
    public Save latestContinuable() {
        Save latest = null;
        for (GameMode mode : GameMode.values()) {
            Save save = read(mode);
            if (save.isContinuable() && (latest == null || save.data.savedAt > latest.data.savedAt)) latest = save;
        }
        return latest;
    }

    /** Writes a mode's save; false (and the old file kept) when it couldn't be written. */
    public boolean write(GameMode mode, SaveData data) {
        FileHandle file = file(mode);
        FileHandle temp = file.sibling(file.name() + TEMP_SUFFIX);
        try {
            temp.writeString(json.prettyPrint(json.toJson(data, SaveData.class)), false, "UTF-8");
            temp.moveTo(file);
            return true;
        } catch (RuntimeException e) {
            error("Could not write the " + mode + " save: " + e.getMessage());
            return false;
        }
    }

    /** The newest old slot of each mode becomes that mode's save; the slot files are kept in old_slots/. */
    private void importOldSlots() {
        for (String name : OLD_SLOT_FILES) {
            FileHandle old = folder.child(name);
            if (!old.exists()) continue;
            SaveData data = parse(old);
            GameMode mode = data != null ? GameMode.fromName("NORMAL".equals(data.mode) ? GameMode.MEDIUM.name() : data.mode) : null;
            boolean isImported = true;
            if (mode != null) {
                Save current = read(mode);
                data.mode = mode.name();
                data.mapPath = mode.mapPath;
                boolean isNewer = !current.isUsed() || (current.data != null && data.savedAt > current.data.savedAt);
                if (isNewer) isImported = write(mode, data);
            }
            if (!isImported) continue;
            try {
                old.moveTo(folder.child(OLD_SLOT_FOLDER + name));
            } catch (RuntimeException e) {
                error("Could not move " + name + ": " + e.getMessage());
            }
        }
    }

    /** The save in a file; null when it can't be read. */
    private SaveData parse(FileHandle file) {
        try {
            return json.fromJson(SaveData.class, file);
        } catch (RuntimeException e) {
            error(file.name() + " is unreadable: " + e.getMessage());
            return null;
        }
    }

    private FileHandle file(GameMode mode) {
        return folder.child(mode.name().toLowerCase(Locale.ROOT) + ".json");
    }

    /** Seconds → h:mm:ss. */
    public static String formatTime(float seconds) {
        int total = Math.max(0, (int) seconds);
        return String.format("%d:%02d:%02d", total / 3600, total / 60 % 60, total % 60);
    }

    // Gdx.app is absent in unit tests
    private static void error(String message) {
        if (Gdx.app != null) Gdx.app.error(TAG, message);
    }
}
