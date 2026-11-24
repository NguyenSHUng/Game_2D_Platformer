package com.hung.save;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.hung.core.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveManagerTest {

    @TempDir
    File dir;
    private FileHandle folder;
    private SaveManager saves;

    @BeforeEach
    void setUp() {
        folder = new FileHandle(dir);
        saves = new SaveManager(folder);
    }

    private static SaveData data(GameMode mode, long savedAt, boolean isComplete) {
        SaveData data = new SaveData();
        data.mode = mode.name();
        data.mapPath = mode.mapPath;
        data.savedAt = savedAt;
        data.isComplete = isComplete;
        data.health = 50;
        data.maxHealth = 100;
        data.weaponIds = new String[] {"combat_knife", "service_pistol"};
        data.ammo = new int[] {0, 7};
        data.visitedRooms = new int[] {0, 3};
        return data;
    }

    private static String fixture() throws IOException {
        try (InputStream in = SaveManagerTest.class.getResourceAsStream("/saves/slot_v1_quit.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> keys(String json) {
        Set<String> keys = new TreeSet<>();
        for (JsonValue v = new JsonReader().parse(json).child; v != null; v = v.next) keys.add(v.name);
        return keys;
    }

    @Test
    void missingFileIsEmpty() {
        assertEquals(SaveManager.Status.EMPTY, saves.read(GameMode.EASY).status);
    }

    @Test
    void eachModeHasItsOwnSave() {
        assertTrue(saves.write(GameMode.MEDIUM, data(GameMode.MEDIUM, 5L, false)));
        SaveManager.Save save = saves.read(GameMode.MEDIUM);
        assertEquals(SaveManager.Status.OK, save.status);
        assertEquals(50, save.data.health);
        assertEquals(7, save.data.ammo[1]);
        assertEquals("service_pistol", save.data.weaponIds[1]);
        assertEquals(3, save.data.visitedRooms[1]);
        assertEquals(SaveManager.Status.EMPTY, saves.read(GameMode.HARD).status);
    }

    @Test
    void unreadableFileIsCorrupt() {
        folder.child("easy.json").writeString("{ not json", false);
        SaveManager.Save save = saves.read(GameMode.EASY);
        assertEquals(SaveManager.Status.CORRUPT, save.status);
        assertNull(save.data);
    }

    @Test
    void newerVersionOrAnotherModeIsIncompatible() {
        SaveData newer = data(GameMode.EASY, 1L, false);
        newer.version = SaveData.CURRENT_VERSION + 1;
        saves.write(GameMode.EASY, newer);
        assertEquals(SaveManager.Status.INCOMPATIBLE, saves.read(GameMode.EASY).status);

        saves.write(GameMode.HARD, data(GameMode.EASY, 1L, false));
        assertEquals(SaveManager.Status.INCOMPATIBLE, saves.read(GameMode.HARD).status);
    }

    @Test
    void latestContinuableSkipsCompletedRuns() {
        saves.write(GameMode.EASY, data(GameMode.EASY, 100L, false));
        saves.write(GameMode.MEDIUM, data(GameMode.MEDIUM, 300L, true));
        assertEquals(GameMode.EASY, saves.latestContinuable().mode);

        saves.write(GameMode.MEDIUM, data(GameMode.MEDIUM, 200L, false));
        assertEquals(GameMode.MEDIUM, saves.latestContinuable().mode);
    }

    @Test
    void lockedModeKeepsItsSaveButCannotContinue() {
        saves.write(GameMode.HARD, data(GameMode.HARD, 900L, false));
        assertEquals(SaveManager.Status.OK, saves.read(GameMode.HARD).status);
        assertEquals(GameMode.HARD.isAvailable(), saves.read(GameMode.HARD).isContinuable());
    }

    /** The newest old slot of a mode is imported, every slot file is kept aside, NORMAL means MEDIUM. */
    @Test
    void oldSlotsBecomeModeSaves() throws IOException {
        SaveManager writer = new SaveManager(folder);
        String[] names = {"slot_1.json", "slot_2.json"};
        for (int i = 0; i < 2; i++) {
            SaveData old = data(GameMode.MEDIUM, 10L + i, false);
            old.mode = "NORMAL";
            old.mapPath = "maps/bunker_482931/map_normal.tmx";
            writer.write(GameMode.MEDIUM, old);
            folder.child("medium.json").moveTo(folder.child(names[i]));
        }
        folder.child("slot_3.json").writeString(fixture(), false);

        saves = new SaveManager(folder);

        assertEquals(11L, saves.read(GameMode.MEDIUM).data.savedAt);
        assertEquals(GameMode.MEDIUM.mapPath, saves.read(GameMode.MEDIUM).data.mapPath);
        assertEquals(SaveManager.Status.OK, saves.read(GameMode.HARD).status);
        assertEquals(121.63326f, saves.read(GameMode.HARD).data.x, 1e-4f);
        for (int i = 1; i <= 3; i++) {
            assertFalse(folder.child("slot_" + i + ".json").exists());
            assertTrue(folder.child("old_slots/slot_" + i + ".json").exists());
        }
    }

    @Test
    void anOlderSlotNeverReplacesANewerSave() throws IOException {
        saves.write(GameMode.HARD, data(GameMode.HARD, Long.MAX_VALUE, false));
        folder.child("slot_1.json").writeString(fixture(), false);

        assertEquals(50, new SaveManager(folder).read(GameMode.HARD).data.health);
    }

    @Test
    void readsASaveWrittenByAnEarlierRelease() throws IOException {
        folder.child("hard.json").writeString(fixture(), false);
        SaveManager.Save save = saves.read(GameMode.HARD);
        assertEquals(SaveManager.Status.OK, save.status);
        assertEquals("QUIT", save.data.reason);
        assertEquals("1000x100/42r/7c", save.data.mapSignature);
        assertEquals(3, save.data.weaponIds.length);
        assertNotNull(save.data.knownRooms);
    }

    @Test
    void writesTheSameTopLevelFieldsAsEarlierReleases() throws IOException {
        SaveData data = data(GameMode.HARD, 1L, false);
        data.knownRooms = new int[] {0};
        data.roomId = "room_1";
        data.mapSignature = "1000x100/42r/7c";
        data.reason = SaveData.REASON_QUIT;
        saves.write(GameMode.HARD, data);
        assertEquals(keys(fixture()), keys(folder.child("hard.json").readString()));
    }
}
