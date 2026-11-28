package com.hung.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.entity.player.Player;
import com.hung.physics.CollisionSystem;
import com.hung.physics.PhysicsSystem;
import com.hung.world.Level;
import com.hung.world.PhysicsSettings;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hunters stay in their home room (they see, call and chase only there), and a wide body holds
 * outside contact while the target is still in reach.
 */
class EnemyRoomTest {

    private static final float TILE = 16f;
    private static final float STEP = 1f / 60f;
    /** Collider widths and attack ranges EnemyFactory gives the real enemies and the player. */
    private static final float PLAYER_WIDTH = 7f;
    private static final float CRAWLER_WIDTH = 34.2f;
    private static final float CRAWLER_RANGE = 22.72f;
    private static final float GHOUL_WIDTH = 16.8f;
    private static final float GHOUL_RANGE = 19.36f;
    private static final float WASP_WIDTH = 19.8f;
    private static final float WASP_RANGE = 12.64f;

    private final TileGrid grid = new TileGrid(60, 12, TILE);
    private final World world;
    private final Room roomA = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 200f, 192f));
    private final Room roomB = new Room("room_b", 1, "STANDARD", "region_1", true, new Rectangle(200f, 0f, 200f, 192f));

    EnemyRoomTest() {
        for (int x = 0; x < 60; x++) grid.set(x, 1, TileType.SOLID);
        Level level = new Level("test", null, grid, new PhysicsSettings(TILE, 6f, 3.5f, 6f), new Vector2(), null, new Array<Rectangle>());
        level.addRoom(roomA);
        level.addRoom(roomB);
        world = new World(level);
    }

    private <T extends HunterEnemy> T place(T enemy, float x, float width, EnemySkill skill, Room home) {
        enemy.setSkill(skill);
        enemy.setHomeRoom(home);
        boolean isFlying = enemy instanceof FlyingEnemy;
        enemy.getTransform().position.set(x, isFlying ? 2 * TILE + FlyingEnemy.CRUISE_HEIGHT : 2 * TILE);
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = 18f;
        movement.isGrounded = !isFlying;
        movement.isFlying = isFlying;
        enemy.setMovement(movement);
        ColliderComponent collider = new ColliderComponent();
        collider.width = width;
        collider.height = 20f;
        enemy.setCollider(collider);
        enemy.setAttack(new AttackComponent());
        enemy.setHealth(new HealthComponent(30));
        enemy.setPatrol(new PatrolComponent());
        world.addEntity(enemy);
        return enemy;
    }

    private MeleeEnemy ghoul(float x, Room home) {
        return place(new MeleeEnemy(20f), x, 12f, EnemySkill.MEDIUM, home);
    }

    private static Player playerAt(float x) {
        Player player = new Player();
        player.getTransform().position.set(x, 2 * TILE);
        ColliderComponent collider = new ColliderComponent();
        collider.width = PLAYER_WIDTH;
        collider.height = 23f;
        player.setCollider(collider);
        return player;
    }

    /** Swings once in reach, then reports the speed it moves at while its attack recovers. */
    private float speedOnCooldown(HunterEnemy enemy, Player player) {
        enemy.updateBehaviour(STEP, world, player);
        assertTrue(enemy.getAttack().isRequested);
        enemy.getAttack().isRequested = false;
        enemy.updateBehaviour(STEP, world, player);
        return enemy.getTransform().velocity.x;
    }

    // ------------------------------------------------------------------ home room

    @Test
    void huntsOnlyAPlayerInItsRoom() {
        MeleeEnemy ghoul = ghoul(150f, roomA);
        ghoul.updateBehaviour(STEP, world, playerAt(230f));
        assertFalse(ghoul.isEngaged());

        ghoul.updateBehaviour(STEP, world, playerAt(190f));
        assertTrue(ghoul.isEngaged());
    }

    @Test
    void forgetsThePlayerWhoLeavesTheRoom() {
        MeleeEnemy ghoul = ghoul(150f, roomA);
        ghoul.updateBehaviour(STEP, world, playerAt(190f));
        assertTrue(ghoul.isEngaged());

        ghoul.updateBehaviour(STEP, world, playerAt(230f));
        assertFalse(ghoul.isEngaged());
    }

    @Test
    void aHitFromAnotherRoomDoesNotSendItHunting() {
        MeleeEnemy ghoul = ghoul(150f, roomA);
        Player player = playerAt(260f);
        ghoul.getHealth().hurtTime = 0.2f;
        ghoul.updateBehaviour(STEP, world, player);
        ghoul.getHealth().hurtTime = 0f;
        ghoul.updateBehaviour(STEP, world, player);
        assertFalse(ghoul.isEngaged());
    }

    @Test
    void callsOnlyHuntersOfItsRoom() {
        MeleeEnemy caller = ghoul(150f, roomA);
        MeleeEnemy neighbour = ghoul(210f, roomB);
        Player player = playerAt(180f);
        caller.updateBehaviour(STEP, world, player);
        neighbour.updateBehaviour(STEP, world, player);
        assertTrue(caller.isEngaged());
        assertFalse(neighbour.isEngaged());
    }

    @Test
    void doesNotStepOutOfItsRoom() {
        // Tiny reach: it keeps closing in on a player standing in the doorway
        MeleeEnemy atEdge = place(new MeleeEnemy(2f), 195f, 10f, EnemySkill.MEDIUM, roomA);
        atEdge.updateBehaviour(STEP, world, playerAt(199.5f));
        assertTrue(atEdge.isEngaged());
        assertEquals(0f, atEdge.getTransform().velocity.x, 1e-6);

        MeleeEnemy inside = place(new MeleeEnemy(2f), 190f, 10f, EnemySkill.MEDIUM, roomA);
        inside.updateBehaviour(STEP, world, playerAt(199.5f));
        assertTrue(inside.getTransform().velocity.x > 0f);
    }

    @Test
    void walksBackIntoItsRoomAfterBeingPushedOut() {
        MeleeEnemy ghoul = ghoul(230f, roomA);
        ghoul.updateBehaviour(STEP, world, playerAt(100f));
        assertTrue(ghoul.getTransform().velocity.x < 0f);
    }

    @Test
    void patrolsInsideItsRoomAfterAJump() {
        for (int x = 14; x < 16; x++) grid.set(x, 1, TileType.EMPTY);
        Room wide = new Room("room_w", 2, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 300f, 192f));
        MeleeEnemy ghoul = ghoul(200f, wide);
        Player player = playerAt(290f);
        PhysicsSystem physics = new PhysicsSystem(world, new CollisionSystem(world));
        for (float t = 0f; t < 4f; t += STEP) {
            ghoul.updateBehaviour(STEP, world, player);
            ghoul.getAttack().isRequested = false;
            physics.update(STEP);
        }
        assertTrue(ghoul.getTransform().position.x > 16 * TILE);
        assertTrue(ghoul.getPatrol().maxX <= 300f - 6f);
        assertTrue(ghoul.getPatrol().maxX > ghoul.getPatrol().minX);
    }

    @Test
    void withoutRoomsNothingIsLimited() {
        MeleeEnemy ghoul = ghoul(150f, null);
        ghoul.updateBehaviour(STEP, world, playerAt(230f));
        assertTrue(ghoul.isEngaged());
    }

    @Test
    void homeRoomComesFromTheSpawnRoomElseThePoint() {
        Level level = world.getLevel();
        assertSame(roomB, level.findRoom("room_b", 50f, 2 * TILE));
        assertSame(roomA, level.findRoom(null, 50f, 2 * TILE));
        assertSame(roomA, level.findRoom("room_missing", 50f, 2 * TILE));
        assertNull(level.findRoom(null, 900f, 2 * TILE));
    }

    // ------------------------------------------------------------------ hold distance

    @Test
    void crawlerHoldsOutsideBodyContact() {
        Player player = playerAt(222f);
        CrawlerEnemy crawler = place(new CrawlerEnemy(CRAWLER_RANGE), 200f, CRAWLER_WIDTH, EnemySkill.EASY, null);
        assertEquals(0f, speedOnCooldown(crawler, player), 1e-6);

        CrawlerEnemy farther = place(new CrawlerEnemy(CRAWLER_RANGE), 200f, CRAWLER_WIDTH, EnemySkill.EASY, null);
        farther.updateBehaviour(STEP, world, playerAt(230f));
        assertTrue(farther.getTransform().velocity.x > 0f);
    }

    @Test
    void crawlerNeverStandsOnThePlayer() {
        CrawlerEnemy crawler = place(new CrawlerEnemy(CRAWLER_RANGE), 200f, CRAWLER_WIDTH, EnemySkill.EASY, null);
        Player player = playerAt(300f);
        PhysicsSystem physics = new PhysicsSystem(world, new CollisionSystem(world));
        float contact = (CRAWLER_WIDTH + PLAYER_WIDTH) / 2f;
        for (float t = 0f; t < 3f; t += STEP) {
            crawler.updateBehaviour(STEP, world, player);
            crawler.getAttack().isRequested = false;
            physics.update(STEP);
            assertTrue(Math.abs(player.getTransform().position.x - crawler.getTransform().position.x) > contact);
        }
    }

    @Test
    void ghoulAndWaspKeepTheirHoldDistance() {
        // Ghoul: 75 % of its reach (14.52) is already outside contact (13.9)
        MeleeEnemy ghoul = place(new MeleeEnemy(GHOUL_RANGE), 200f, GHOUL_WIDTH, EnemySkill.EASY, null);
        assertEquals(0f, speedOnCooldown(ghoul, playerAt(214f)), 1e-6);
        MeleeEnemy closing = place(new MeleeEnemy(GHOUL_RANGE), 200f, GHOUL_WIDTH, EnemySkill.EASY, null);
        assertTrue(speedOnCooldown(closing, playerAt(215f)) > 0f);

        // Wasp: contact (15.4) is beyond its reach (12.64), so it keeps 60 % of the reach (7.58)
        FlyingEnemy wasp = place(new FlyingEnemy(WASP_RANGE, 2 * TILE + FlyingEnemy.CRUISE_HEIGHT), 200f, WASP_WIDTH, EnemySkill.EASY, null);
        assertTrue(speedOnCooldown(wasp, playerAt(210f)) > 0f);
    }
}
