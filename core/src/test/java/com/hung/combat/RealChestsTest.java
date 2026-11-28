package com.hung.combat;

import com.badlogic.gdx.math.Rectangle;
import com.hung.core.GameMode;
import com.hung.testutil.TmxTestMap;
import com.hung.world.Room;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 24 chests of the three maps, as WorldLoader builds them: each has its chest tile in "props",
 * stands in a room, and throws its item to a safe spot of that room. Their loot is listed in
 * build/playtest/chests.txt.
 */
class RealChestsTest {

    private static final Path REPORT = Path.of("build", "playtest", "chests.txt");

    @Test
    void everyChestHasItsTileARoomAndASafeLandingSpot() throws Exception {
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "%-38s %-5s %-16s %-9s %-12s %s%n",
            "chest id", "tier", "room", "region", "item", "lands at"));
        int chests = 0;
        int[] perMode = new int[GameMode.values().length];
        for (GameMode mode : GameMode.values()) {
            TmxTestMap map = new TmxTestMap(mode.mapPath);
            float tile = map.grid.getTileSize();
            for (TmxTestMap.MapObject object : map.objectsOf("CHEST")) {
                Rectangle rect = object.rect();
                int column = (int) ((rect.x + rect.width / 2f) / tile);
                int row = (int) ((rect.y + 1f) / tile);
                String id = mode.mapPath + "#" + object.id();
                assertNotEquals(0, map.cell("props", column, row), id + " has no chest tile under it");

                float x = column * tile + tile / 2f;
                float y = row * tile;
                Room room = map.level.findRoomAt(x, y + 1f);
                assertNotNull(room, id + " stands in no room");
                float landing = LootDrop.landingX(map.grid, room, x, y);
                assertTrue(LootDrop.isSafeSpot(map.grid, room, landing, y), id + " lands on an unsafe spot at " + landing);
                if (landing != x) {
                    assertTrue(LootDrop.isArcClear(map.grid, x, y + LootDrop.LAUNCH_HEIGHT, landing, y), id + ": arc blocked");
                }
                ChestTier tier = ChestTier.fromName(object.properties().get("tier"));
                report.append(String.format(Locale.ROOT, "%-38s %-5s %-16s %-9s %-12s %+.0f%n", id, tier, room.id, room.regionId,
                    LootTable.DEFAULT.pick(id, tier), landing - x));
                chests++;
                perMode[mode.ordinal()]++;
            }
        }
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
        assertEquals(24, chests);
        assertEquals(4, perMode[GameMode.EASY.ordinal()]);
        assertEquals(10, perMode[GameMode.MEDIUM.ordinal()]);
        assertEquals(10, perMode[GameMode.HARD.ordinal()]);
    }
}
