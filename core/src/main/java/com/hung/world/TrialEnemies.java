package com.hung.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.entity.EnemyDefinition;
import com.hung.entity.EnemyFactory;
import com.hung.util.Constants;

/**
 * Trial placement of the enemy generator's phase 2 archetypes (CRAWLER, FLYING, MINI_BOSS) until
 * the map generator places them itself: one of each listed definition goes into the rooms just
 * after the player's start room, in route order, each in the first room that has a floor stretch
 * wide and tall enough for it (the MINI_BOSS needs a high ceiling, a flyer room to hover).
 * Turned off by emptying Constants.TRIAL_ENEMIES.
 */
public final class TrialEnemies {

    private static final String TAG = "TrialEnemies";
    /** Free tiles kept above an enemy's head. */
    private static final int HEADROOM_TILES = 1;
    /** Floor tiles kept free on each side of the enemy's body. */
    private static final int SIDE_TILES = 2;

    private TrialEnemies() {
    }

    /** The listed definitions whose files exist. */
    public static Array<String> paths() {
        Array<String> paths = new Array<>();
        for (String path : Constants.TRIAL_ENEMIES) {
            if (Gdx.files.internal(path).exists()) paths.add(path);
            else Gdx.app.log(TAG, "No enemy at " + path + "; skipped");
        }
        return paths;
    }

    /**
     * One spawn per definition (paths and definitions in the same order); a definition that finds
     * no room gets no spawn. Ground enemies stand on the floor, flyers hover above it.
     */
    public static Array<EnemySpawn> place(Level level, Array<String> paths, Array<EnemyDefinition> definitions) {
        Array<EnemySpawn> spawns = new Array<>();
        Array<Room> rooms = level.getRooms();
        if (rooms.size == 0) return spawns;
        Room start = RoomLoader.roomAtOrNearest(level, level.getPlayerSpawn());
        boolean[] isUsed = new boolean[rooms.size];
        isUsed[start.index] = true;
        for (int d = 0; d < definitions.size; d++) {
            EnemyDefinition definition = definitions.get(d);
            EnemySpawn spawn = null;
            for (int r = start.index + 1; r < rooms.size && spawn == null; r++) {
                if (isUsed[r]) continue;
                spawn = findSpawn(level, rooms.get(r), definition);
                if (spawn != null) isUsed[r] = true;
            }
            if (spawn == null) {
                Gdx.app.log(TAG, definition.id + ": no room fits; not placed");
                continue;
            }
            spawn.name = "trial_" + definition.id;
            spawn.definitionPath = paths.get(d);
            spawns.add(spawn);
            Gdx.app.log(TAG, definition.id + " (" + definition.archetype + ") in " + level.findRoomAt(spawn.x, spawn.y + 1f).id
                + " at " + new Vector2(spawn.x, spawn.y));
        }
        return spawns;
    }

    /** First floor stretch in the room (searched from its centre outward) that fits the enemy. */
    private static EnemySpawn findSpawn(Level level, Room room, EnemyDefinition definition) {
        TileGrid grid = level.getGrid();
        float tile = grid.getTileSize();
        float scale = definition.drawScale > 0f ? definition.drawScale : 1f;
        EnemyDefinition.Bounds idle = definition.animations.get("idle").frames.first().bounds;
        boolean isFlyer = EnemyFactory.isFlyer(definition);
        float bodyHeight = idle.height * scale + (isFlyer ? EnemyFactory.FLYER_CRUISE_HEIGHT : 0f);
        int clearTiles = MathUtils.ceil(bodyHeight / tile) + HEADROOM_TILES;
        int widthTiles = MathUtils.ceil(idle.width * scale / tile) + 2 * SIDE_TILES;

        Rectangle b = room.bounds;
        int left = grid.toTile(b.x) + 1;
        int right = grid.toTile(b.x + b.width) - 2;
        int top = grid.toTile(b.y + b.height) - 1;
        int bottom = grid.toTile(b.y);
        int centre = (left + right) / 2;
        for (int offset = 0; offset <= (right - left) / 2 + 1; offset++) {
            for (int column : new int[] {centre - offset, centre + offset}) {
                if (column < left || column > right) continue;
                for (int row = top; row >= bottom; row--) {
                    if (!isFloor(grid, column, row, clearTiles)) continue;
                    int from = column;
                    int to = column;
                    while (from - 1 >= left && isFloor(grid, from - 1, row, clearTiles)) from--;
                    while (to + 1 <= right && isFloor(grid, to + 1, row, clearTiles)) to++;
                    if (to - from + 1 < widthTiles) break;
                    EnemySpawn spawn = new EnemySpawn();
                    spawn.x = ((from + to + 1) / 2f) * tile;
                    spawn.y = (row + 1) * tile + (isFlyer ? EnemyFactory.FLYER_CRUISE_HEIGHT : 0f);
                    spawn.patrolMinX = from * tile;
                    spawn.patrolMaxX = (to + 1) * tile;
                    spawn.isFacingRight = false; // facing the player, who arrives from the left
                    return spawn;
                }
            }
        }
        return null;
    }

    /** A tile to stand on with clearTiles free (no wall, no hazard) above it. */
    private static boolean isFloor(TileGrid grid, int column, int row, int clearTiles) {
        TileType floor = grid.get(column, row);
        if (!floor.isSolid() && floor != TileType.PLATFORM) return false;
        for (int k = 1; k <= clearTiles; k++) {
            TileType above = grid.get(column, row + k);
            if (above.isSolid() || above.isHazard() || above == TileType.PLATFORM) return false;
        }
        return true;
    }
}
