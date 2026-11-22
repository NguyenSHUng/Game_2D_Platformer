package com.hung.save;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveManagerTest {

    private static final String MAP = "maps/bunker_482931/map.tmx";

    @TempDir
    File dir;
    private FileHandle folder;
    private SaveManager saves;

    @BeforeEach
    void setUp() {
        folder = new FileHandle(dir);
        saves = new SaveManager(folder, MAP::equals);
    }

    private static SaveData data(long savedAt, boolean isComplete) {
        SaveData data = new SaveData();
        data.mode = "HARD";
        data.mapPath = MAP;
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
    void missingFileIsAnEmptySlot() {
        assertEquals(SaveManager.Status.EMPTY, saves.read(0).status);
    }

    @Test
    void writeThenReadRoundTrips() {
        assertTrue(saves.write(1, data(5L, false)));
        SaveManager.Slot slot = saves.read(1);
        assertEquals(SaveManager.Status.OK, slot.status);
        assertEquals(50, slot.data.health);
        assertEquals(7, slot.data.ammo[1]);
        assertEquals("service_pistol", slot.data.weaponIds[1]);
        assertEquals(3, slot.data.visitedRooms[1]);
    }

    @Test
    void unreadableFileIsCorrupt() {
        folder.child("slot_1.json").writeString("{ not json", false);
        SaveManager.Slot slot = saves.read(0);
        assertEquals(SaveManager.Status.CORRUPT, slot.status);
        assertNull(slot.data);
    }

    @Test
    void newerVersionUnknownModeOrMissingMapIsIncompatible() {
        SaveData newer = data(1L, false);
        newer.version = SaveData.CURRENT_VERSION + 1;
        saves.write(0, newer);
        assertEquals(SaveManager.Status.INCOMPATIBLE, saves.read(0).status);

        SaveData mode = data(1L, false);
        mode.mode = "NIGHTMARE";
        saves.write(1, mode);
        assertEquals(SaveManager.Status.INCOMPATIBLE, saves.read(1).status);

        SaveData map = data(1L, false);
        map.mapPath = "maps/gone/map.tmx";
        saves.write(2, map);
        assertEquals(SaveManager.Status.INCOMPATIBLE, saves.read(2).status);
    }

    @Test
    void latestContinuableSkipsCompletedRuns() {
        saves.write(0, data(100L, false));
        saves.write(1, data(300L, true));
        saves.write(2, data(200L, false));
        assertEquals(2, saves.latestContinuable().index);
    }

    @Test
    void deleteEmptiesTheSlot() {
        saves.write(0, data(1L, false));
        saves.delete(0);
        assertEquals(SaveManager.Status.EMPTY, saves.read(0).status);
    }

    @Test
    void readsASaveWrittenByTheCurrentRelease() throws IOException {
        folder.child("slot_1.json").writeString(fixture(), false);
        SaveManager.Slot slot = saves.read(0);
        assertEquals(SaveManager.Status.OK, slot.status);
        SaveData data = slot.data;
        assertEquals("QUIT", data.reason);
        assertEquals(121.63326f, data.x, 1e-4f);
        assertEquals(100, data.health);
        assertEquals("1000x100/42r/7c", data.mapSignature);
        assertEquals(3, data.weaponIds.length);
        assertNotNull(data.knownRooms);
    }

    @Test
    void writesTheSameTopLevelFieldsAsTheCurrentRelease() throws IOException {
        SaveData data = data(1L, false);
        data.knownRooms = new int[] {0};
        data.roomId = "room_1";
        data.mapSignature = "1000x100/42r/7c";
        data.reason = SaveData.REASON_QUIT;
        saves.write(0, data);
        assertEquals(keys(fixture()), keys(folder.child("slot_1.json").readString()));
    }
}
