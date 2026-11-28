package com.hung.combat.system;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.ProjectileComponent;
import com.hung.entity.Entity;
import com.hung.entity.enemy.MeleeEnemy;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bullets stay in the room they were fired in: through a doorway they vanish instead of hitting the next room. */
class ProjectileRoomTest {

    private static final float STEP = 1f / 60f;
    private static final float Y = 40f;
    private static final int ENEMY_HEALTH = 30;

    private final Room roomA = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 200f, 160f));
    /** Shares the wall column at x 192–200 with room A, as neighbouring rooms on the maps do. */
    private final Room roomB = new Room("room_b", 1, "STANDARD", "region_1", true, new Rectangle(192f, 0f, 200f, 160f));

    /** An open level (no walls) so only the room decides where a bullet may go. */
    private World world(boolean hasRooms) {
        Level level = new Level("test", null, new TileGrid(40, 12, 16f), null, new Vector2(), null, new Array<Rectangle>());
        if (hasRooms) {
            level.addRoom(roomA);
            level.addRoom(roomB);
        }
        World world = new World(level);
        Player player = new Player();
        player.getTransform().position.set(100f, Y);
        world.setPlayer(player);
        return world;
    }

    private static MeleeEnemy enemyAt(World world, float x) {
        MeleeEnemy enemy = new MeleeEnemy(20f);
        enemy.getTransform().position.set(x, Y - 10f);
        ColliderComponent body = new ColliderComponent();
        body.width = 16f;
        body.height = 24f;
        enemy.setCollider(body);
        enemy.setHealth(new HealthComponent(ENEMY_HEALTH));
        world.addEntity(enemy);
        return enemy;
    }

    private static Entity bullet(World world, float x, Room room) {
        Entity bullet = new Entity();
        bullet.getTransform().position.set(x, Y);
        bullet.getTransform().velocity.set(400f, 0f);
        ProjectileComponent projectile = new ProjectileComponent();
        projectile.damage = 10;
        projectile.owner = world.getPlayer();
        projectile.rangeLeft = 1000f;
        projectile.room = room;
        bullet.setProjectile(projectile);
        world.addEntity(bullet);
        return bullet;
    }

    private static void fly(World world, float seconds) {
        ProjectileSystem projectiles = new ProjectileSystem(world);
        for (float t = 0f; t < seconds; t += STEP) projectiles.update(STEP);
    }

    @Test
    void doesNotHitAnEnemyInTheNextRoom() {
        World world = world(true);
        MeleeEnemy next = enemyAt(world, 260f);
        Entity bullet = bullet(world, 120f, roomA);
        fly(world, 1f);
        assertFalse(world.getEntities().contains(bullet, true));
        assertEquals(ENEMY_HEALTH, next.getHealth().getHealth());
    }

    @Test
    void vanishesAtTheRoomEdge() {
        World world = world(true);
        Entity bullet = bullet(world, 120f, roomA);
        ProjectileSystem projectiles = new ProjectileSystem(world);
        while (world.getEntities().contains(bullet, true)) {
            assertTrue(bullet.getTransform().position.x <= 200f + 2f);
            projectiles.update(STEP);
        }
        assertTrue(bullet.getTransform().position.x > 200f);
    }

    @Test
    void stillHitsAnEnemyInTheSameRoom() {
        World world = world(true);
        MeleeEnemy here = enemyAt(world, 170f);
        bullet(world, 120f, roomA);
        fly(world, 1f);
        assertEquals(ENEMY_HEALTH - 10, here.getHealth().getHealth());
    }

    @Test
    void withoutRoomsOnlyItsRangeLimitsIt() {
        World world = world(false);
        MeleeEnemy far = enemyAt(world, 260f);
        bullet(world, 120f, null);
        fly(world, 1f);
        assertEquals(ENEMY_HEALTH - 10, far.getHealth().getHealth());
    }

    @Test
    void thePlayersShotsBelongToTheRoomOnScreen() {
        World world = world(true);
        world.getRoomTransition().currentRoom = roomA;
        // At x 196 the muzzle is inside both rooms' bounds (the shared wall); the room on screen wins
        assertSame(roomA, ShootingSystem.roomOf(world, world.getPlayer(), new Vector2(196f, Y)));

        world.getRoomTransition().currentRoom = null;
        assertSame(roomA, ShootingSystem.roomOf(world, world.getPlayer(), new Vector2(100f, Y)));
        assertSame(roomB, ShootingSystem.roomOf(world, new Entity(), new Vector2(300f, Y)));
        assertNull(ShootingSystem.roomOf(world(false), new Entity(), new Vector2(100f, Y)));
    }
}
