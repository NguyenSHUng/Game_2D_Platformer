package com.hung.combat;

import com.hung.component.PickupComponent;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;

/**
 * How an item leaves an opened chest: it is thrown from the chest's lid in an arc to a safe spot on
 * the chest's floor (floor under it, no wall, spike or toxic pool, inside the room, nothing solid on
 * the way), lands, bounces once and lies still (PickupSystem flies it).
 */
public final class LootDrop {

    /** Seconds from the lid to the first touch of the floor. */
    public static final float FLIGHT_SECONDS = 0.45f;
    /** Pulls the item down while it flies, world units per second squared. */
    public static final float GRAVITY = 520f;
    /** Share of the landing speed kept by the one bounce. */
    public static final float BOUNCE = 0.35f;
    /** The item leaves this high above the chest's floor (its lid). */
    static final float LAUNCH_HEIGHT = 12f;
    /** Landing spots tried in this order, offsets from the chest, world units; the chest itself last. */
    private static final float[] OFFSETS = {20f, -20f, 12f, -12f, 28f, -28f};
    private static final int ARC_SAMPLES = 12;

    private LootDrop() {
    }

    /** A pickup of the type, thrown from the chest standing at (chestX, floorY); add it to the world. */
    public static Entity throwFromChest(PickupType type, TileGrid grid, Room room, float chestX, float floorY) {
        float landingX = landingX(grid, room, chestX, floorY);
        Entity item = EntityFactory.createPickup(type, chestX, floorY + LAUNCH_HEIGHT);
        launch(item, landingX, floorY);
        return item;
    }

    /** The first safe spot with a clear arc; the chest's own spot (it stands on safe floor) when none is. */
    static float landingX(TileGrid grid, Room room, float chestX, float floorY) {
        for (float offset : OFFSETS) {
            float x = chestX + offset;
            if (isSafeSpot(grid, room, x, floorY) && isArcClear(grid, chestX, floorY + LAUNCH_HEIGHT, x, floorY)) return x;
        }
        return chestX;
    }

    /** Sets the velocity that carries the item from where it is to (toX, toY) in FLIGHT_SECONDS. */
    static void launch(Entity item, float toX, float toY) {
        PickupComponent pickup = item.getPickup();
        pickup.isFlying = true;
        pickup.hasBounced = false;
        pickup.landingX = toX;
        pickup.landingY = toY;
        float fromX = item.getTransform().position.x;
        float fromY = item.getTransform().position.y;
        float t = FLIGHT_SECONDS;
        item.getTransform().velocity.set((toX - fromX) / t, (toY - fromY + 0.5f * GRAVITY * t * t) / t);
    }

    /** An item's body fits at x on this floor: support under all of it, nothing solid or harmful in it, inside the room. */
    static boolean isSafeSpot(TileGrid grid, Room room, float x, float floorY) {
        float half = EntityFactory.PICKUP_SIZE / 2f;
        float size = EntityFactory.PICKUP_SIZE;
        if (room != null && !(room.bounds.contains(x - half, floorY + 1f) && room.bounds.contains(x + half, floorY + size))) {
            return false;
        }
        int floorRow = grid.toTile(floorY) - 1;
        for (float edge : new float[] {x - half + 0.5f, x, x + half - 0.5f}) {
            int column = grid.toTile(edge);
            if (!grid.isSupport(column, floorRow)) return false;
            for (int row = grid.toTile(floorY + 0.5f); row <= grid.toTile(floorY + size - 0.5f); row++) {
                TileType tile = grid.get(column, row);
                if (tile.isSolid() || tile.isHazard()) return false;
            }
        }
        return true;
    }

    /** Nothing solid where the item's center or top passes along the arc. */
    static boolean isArcClear(TileGrid grid, float fromX, float fromY, float toX, float toY) {
        float t = FLIGHT_SECONDS;
        float vx = (toX - fromX) / t;
        float vy = (toY - fromY + 0.5f * GRAVITY * t * t) / t;
        for (int i = 1; i <= ARC_SAMPLES; i++) {
            float s = t * i / ARC_SAMPLES;
            float x = fromX + vx * s;
            float y = fromY + vy * s - 0.5f * GRAVITY * s * s;
            float mid = y + EntityFactory.PICKUP_SIZE / 2f;
            float top = y + EntityFactory.PICKUP_SIZE - 0.5f;
            if (grid.get(grid.toTile(x), grid.toTile(mid)).isSolid() || grid.get(grid.toTile(x), grid.toTile(top)).isSolid()) {
                return false;
            }
        }
        return true;
    }
}
