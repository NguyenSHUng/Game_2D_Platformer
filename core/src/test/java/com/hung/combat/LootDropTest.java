package com.hung.combat;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.system.PickupSystem;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where a chest's item lands (safe floor, inside the room, a clear arc) and how it gets there. */
class LootDropTest {

    private static final float TILE = 16f;
    private static final float FLOOR = 2 * TILE;
    /** Center of column 12: tiles 10–14 are 160–240. */
    private static final float CHEST = 12 * TILE + 8f;
    private static final float STEP = 1f / 60f;

    private final TileGrid grid = new TileGrid(40, 12, TILE);
    private final Room room = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 640f, 192f));

    LootDropTest() {
        for (int x = 0; x < 40; x++) grid.set(x, 1, TileType.SOLID);
    }

    private float landing() {
        return LootDrop.landingX(grid, room, CHEST, FLOOR);
    }

    @Test
    void openFloorTakesTheFirstSpot() {
        assertEquals(CHEST + 20f, landing(), 1e-4);
    }

    /** Item bodies are 10 wide: +20 covers x 215–225 (columns 13–14), −20 x 175–185 (10–11), +12 207–217 (12–13), −12 183–193 (11–12). */
    @Test
    void wallsSpikesToxicAndGapsAreSkipped() {
        grid.set(13, 2, TileType.SOLID);             // a wall at +20, +12 and +28
        grid.set(14, 2, TileType.SOLID);
        assertEquals(CHEST - 20f, landing(), 1e-4);

        grid.set(10, 2, TileType.SPIKE);             // spikes at −20 and −28
        assertEquals(CHEST - 12f, landing(), 1e-4);

        grid.set(11, 1, TileType.EMPTY);             // no floor under −12
        assertEquals(CHEST, landing(), 1e-4);        // nothing else fits: the chest's own spot

        grid.set(11, 1, TileType.SOLID);
        grid.set(11, 2, TileType.TOXIC);             // a toxic pool at −12
        assertEquals(CHEST, landing(), 1e-4);
    }

    @Test
    void neverOutsideItsRoom() {
        Room narrow = new Room("room_n", 1, "STANDARD", "region_1", true, new Rectangle(0f, 0f, CHEST + 15f, 192f));
        assertEquals(CHEST - 20f, LootDrop.landingX(grid, narrow, CHEST, FLOOR), 1e-4);
    }

    @Test
    void aLowCeilingOnTheWayIsAvoided() {
        grid.set(13, 3, TileType.SOLID);             // just above the right-hand arc
        grid.set(13, 4, TileType.SOLID);
        assertEquals(CHEST - 20f, landing(), 1e-4);
        assertFalse(LootDrop.isArcClear(grid, CHEST, FLOOR + LootDrop.LAUNCH_HEIGHT, CHEST + 20f, FLOOR));
    }

    @Test
    void anItemMustStandOnSupport() {
        assertTrue(LootDrop.isSafeSpot(grid, room, CHEST + 20f, FLOOR));
        assertFalse(LootDrop.isSafeSpot(grid, room, CHEST + 20f, FLOOR + TILE));
    }

    // ------------------------------------------------------------------ the flight, played by PickupSystem

    private static World world(TileGrid grid) {
        World world = new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
        Player player = new Player();
        player.getTransform().position.set(400f, FLOOR);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(50);
        player.setHealth(health);
        world.setPlayer(player);
        return world;
    }

    @Test
    void itArcsLandsBouncesOnceAndLiesStill() {
        World world = world(grid);
        Entity item = LootDrop.throwFromChest(PickupType.HEALTH_PACK, grid, room, CHEST, FLOOR);
        world.addEntity(item);
        PickupSystem system = new PickupSystem(world, new EventBus());
        float peak = item.getTransform().position.y;
        int touches = 0;
        boolean wasDown = false;
        float t = 0f;
        while (item.getPickup().isFlying && t < 3f) {
            system.update(STEP);
            t += STEP;
            peak = Math.max(peak, item.getTransform().position.y);
            boolean isDown = item.getTransform().position.y == FLOOR;
            if (isDown && !wasDown) touches++;
            wasDown = isDown;
        }
        assertFalse(item.getPickup().isFlying);
        assertEquals(new Vector2(CHEST + 20f, FLOOR), item.getTransform().position);
        assertEquals(0f, item.getTransform().velocity.len(), 1e-6);
        assertTrue(peak > FLOOR + LootDrop.LAUNCH_HEIGHT, "it rises above the lid");
        assertEquals(2, touches, "first landing and the end of the one bounce");
        assertEquals(LootDrop.FLIGHT_SECONDS, firstLanding(), 2 * STEP);
        assertTrue(t < 1f);
    }

    /** Seconds until the item first reaches the floor. */
    private float firstLanding() {
        World world = world(grid);
        Entity item = LootDrop.throwFromChest(PickupType.HEALTH_PACK, grid, room, CHEST, FLOOR);
        world.addEntity(item);
        PickupSystem system = new PickupSystem(world, new EventBus());
        float t = 0f;
        while (!item.getPickup().hasBounced && t < 3f) {
            system.update(STEP);
            t += STEP;
        }
        return t;
    }

    @Test
    void itCannotBePickedMidAirOnlyOnceItLies() {
        World world = world(grid);
        Entity item = LootDrop.throwFromChest(PickupType.HEALTH_PACK, grid, room, CHEST, FLOOR);
        world.addEntity(item);
        world.getPlayer().getTransform().position.set(CHEST + 20f, FLOOR);
        PickupSystem system = new PickupSystem(world, new EventBus());
        // The arc passes through the player's body, but only a lying item can be picked
        while (item.getPickup().isFlying) {
            system.update(STEP);
            if (item.getPickup().isFlying) assertEquals(50, world.getPlayer().getHealth().getHealth());
        }
        system.update(STEP);
        assertEquals(75, world.getPlayer().getHealth().getHealth());
        assertFalse(world.getEntities().contains(item, true));
    }
}
