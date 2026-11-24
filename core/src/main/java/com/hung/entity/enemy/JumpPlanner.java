package com.hung.entity.enemy;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.hung.component.ColliderComponent;
import com.hung.world.TileGrid;
import com.hung.world.TileType;

/**
 * Finds a jump for a ground hunter: the nearest safe place to stand past a gap, on a step or on a
 * low platform, and the launch velocity that lands there. Only looks at the tile grid.
 */
final class JumpPlanner {

    private static final int MAX_COLUMNS = 4;
    private static final int MAX_RISE = 2;
    private static final int MAX_DROP = 2;
    /** The arc's top clears the landing by this much, world units. */
    private static final float CLEARANCE = 8f;
    private static final float MAX_SPEED_X = 130f;
    private static final float EPSILON = 0.01f;

    private JumpPlanner() {
    }

    /**
     * A jump from the feet at (x, y) towards {@code direction} (-1 / 1); writes the launch velocity.
     * False when nothing safe is in reach.
     */
    static boolean across(TileGrid grid, float x, float y, ColliderComponent body, float direction, float gravity, Vector2 velocity) {
        int column = grid.toTile(x);
        int floor = grid.toTile(y - EPSILON);
        for (int step = 1; step <= MAX_COLUMNS; step++) {
            for (int rise = MAX_RISE; rise >= -MAX_DROP; rise--) {
                int landing = column + (int) direction * step;
                float landingX = (landing + 0.5f) * grid.getTileSize();
                if (!canStand(grid, landingX, floor + rise, body)) continue;
                // Level with the feet one column ahead is plain walking, not a jump
                if (step == 1 && rise == 0) continue;
                if (launch(landingX - x, rise * grid.getTileSize(), gravity, velocity) && isHeadFree(grid, x, y, body, velocity.y, gravity)) return true;
            }
        }
        return false;
    }

    /** A jump straight up through a one-way platform, at most MAX_RISE rows above the feet. */
    static boolean ontoPlatform(TileGrid grid, float x, float y, ColliderComponent body, float gravity, Vector2 velocity) {
        int column = grid.toTile(x);
        int floor = grid.toTile(y - EPSILON);
        for (int rise = 1; rise <= MAX_RISE; rise++) {
            if (grid.get(column, floor + rise) != TileType.PLATFORM || !canStand(grid, x, floor + rise, body)) continue;
            if (launch(0f, rise * grid.getTileSize(), gravity, velocity) && isHeadFree(grid, x, y, body, velocity.y, gravity)) return true;
        }
        return false;
    }

    /** Something to land on under the middle, and no wall or hazard where the body would be. */
    private static boolean canStand(TileGrid grid, float x, int floorRow, ColliderComponent body) {
        if (!grid.isSupport(grid.toTile(x), floorRow)) return false;
        int top = floorRow + MathUtils.ceil(body.height / grid.getTileSize());
        for (int row = floorRow + 1; row <= top; row++) {
            for (int column = grid.toTile(x - body.width / 2f); column <= grid.toTile(x + body.width / 2f); column++) {
                TileType type = grid.get(column, row);
                if (type.isSolid() || type.isHazard()) return false;
            }
        }
        return true;
    }

    private static boolean launch(float dx, float rise, float gravity, Vector2 velocity) {
        float up = (float) Math.sqrt(2f * gravity * (Math.max(0f, rise) + CLEARANCE));
        float airTime = (up + (float) Math.sqrt(up * up - 2f * gravity * rise)) / gravity;
        velocity.set(dx / airTime, up);
        return Math.abs(velocity.x) <= MAX_SPEED_X;
    }

    /** No ceiling in the way of the jump's top above the take-off. */
    private static boolean isHeadFree(TileGrid grid, float x, float y, ColliderComponent body, float up, float gravity) {
        int top = grid.toTile(y + body.height + up * up / (2f * gravity));
        for (int row = grid.toTile(y + EPSILON); row <= top; row++) {
            if (grid.get(grid.toTile(x), row).isSolid()) return false;
        }
        return true;
    }
}
