package com.hung.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.ai.tactic.HitAndRunTactic;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real hunters running HitAndRunTactic: backing off safely, within speed limits, on foot and in the air. */
class HitAndRunEnemyTest {

    private static final float TILE = 16f;
    private static final float STEP = 1f / 60f;
    private static final float FLOOR_Y = 2 * TILE;
    private static final float MAX_CHASE_SPEED = 84f;
    private static final float SWING_SECONDS = 0.35f;

    private final TileGrid grid = new TileGrid(60, 12, TILE);
    private final World world;

    HitAndRunEnemyTest() {
        for (int x = 0; x < 60; x++) grid.set(x, 1, TileType.SOLID);
        world = new World(new Level("test", null, grid, new PhysicsSettings(TILE, 6f, 3.5f, 6f), new Vector2(), null, new Array<Rectangle>()));
    }

    /** Sizes and reaches EnemyFactory gives the real ghoul, crawler and wasp, running HitAndRun. */
    private HunterEnemy hunter(String type, EnemySkill skill, float x) {
        HunterEnemy enemy;
        float width;
        float walk;
        switch (type) {
            case "MELEE" -> { enemy = new MeleeEnemy(19.36f); width = 16.8f; walk = 18f; }
            case "CRAWLER" -> { enemy = new CrawlerEnemy(22.72f); width = 34.2f; walk = 41.268f; }
            default -> { enemy = new FlyingEnemy(12.64f, FLOOR_Y + FlyingEnemy.CRUISE_HEIGHT); width = 19.8f; walk = 31.2f; }
        }
        boolean isFlying = enemy instanceof FlyingEnemy;
        enemy.setSkill(skill);
        enemy.setTactic(new HitAndRunTactic());
        enemy.getTransform().position.set(x, isFlying ? FLOOR_Y + FlyingEnemy.CRUISE_HEIGHT : FLOOR_Y);
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = walk;
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

    private static Player playerAt(float x) {
        Player player = new Player();
        player.getTransform().position.set(x, FLOOR_Y);
        ColliderComponent collider = new ColliderComponent();
        collider.width = 7f;
        collider.height = 23f;
        player.setCollider(collider);
        return player;
    }

    /** Strikes once in reach, plays the swing out, then returns the first frame after it. */
    private void strikeAndFinishSwing(HunterEnemy enemy, Player player) {
        enemy.updateBehaviour(STEP, world, player);
        assertTrue(enemy.getAttack().isRequested);
        enemy.getAttack().isRequested = false;
        enemy.getAttack().isAttacking = true;
        enemy.updateBehaviour(SWING_SECONDS, world, player);
        enemy.getAttack().isAttacking = false;
        enemy.updateBehaviour(STEP, world, player);
    }

    @Test
    void groundHuntersBackOffAfterTheSwingBelowTheChaseCap() {
        for (EnemySkill skill : new EnemySkill[] {EnemySkill.EASY, EnemySkill.MEDIUM, EnemySkill.HARD}) {
            HunterEnemy ghoul = hunter("MELEE", skill, 200f);
            strikeAndFinishSwing(ghoul, playerAt(214f));
            assertEquals(-ghoul.chaseSpeed() * 0.7f, ghoul.getTransform().velocity.x, 1e-4);
            assertTrue(ghoul.getTransform().isFacingRight);

            HunterEnemy crawler = hunter("CRAWLER", skill, 200f);
            strikeAndFinishSwing(crawler, playerAt(222f));
            assertTrue(Math.abs(crawler.getTransform().velocity.x) <= MAX_CHASE_SPEED * 0.7f + 1e-4);
            assertTrue(crawler.getTransform().velocity.x < 0f);
        }
    }

    @Test
    void crawlerOnHardBacksOffAt70PercentOfTheCap() {
        HunterEnemy crawler = hunter("CRAWLER", EnemySkill.HARD, 200f);
        strikeAndFinishSwing(crawler, playerAt(222f));
        assertEquals(-MAX_CHASE_SPEED * 0.7f, crawler.getTransform().velocity.x, 1e-4);
    }

    @Test
    void neverBacksOffALedge() {
        for (int x = 0; x < 12; x++) grid.set(x, 1, TileType.EMPTY);
        HunterEnemy ghoul = hunter("MELEE", EnemySkill.MEDIUM, 12 * TILE + 9f);
        Player player = playerAt(12 * TILE + 23f);
        strikeAndFinishSwing(ghoul, player);
        assertEquals(0f, ghoul.getTransform().velocity.x, 1e-6);
        runWithPhysics(ghoul, player, 2f);
        assertEquals(FLOOR_Y, ghoul.getTransform().position.y, 0.1f);
    }

    /** The enemy itself refuses an unsafe step back, even from a tactic that doesn't check canBackOff. */
    @Test
    void aCarelessTacticStillCannotBackOffALedge() {
        for (int x = 0; x < 12; x++) grid.set(x, 1, TileType.EMPTY);
        HunterEnemy ghoul = hunter("MELEE", EnemySkill.MEDIUM, 12 * TILE + 9f);
        ghoul.setTactic((context, actions) -> actions.retreat(-Math.signum(context.dx())));
        ghoul.updateBehaviour(STEP, world, playerAt(12 * TILE + 40f));
        assertEquals(0f, ghoul.getTransform().velocity.x, 1e-6);

        HunterEnemy safe = hunter("MELEE", EnemySkill.MEDIUM, 30 * TILE);
        safe.setTactic((context, actions) -> actions.retreat(-Math.signum(context.dx())));
        safe.updateBehaviour(STEP, world, playerAt(30 * TILE + 40f));
        assertTrue(safe.getTransform().velocity.x < 0f);
    }

    @Test
    void neverBacksOffOntoSpikes() {
        grid.set(11, 2, TileType.SPIKE);
        HunterEnemy ghoul = hunter("MELEE", EnemySkill.MEDIUM, 12 * TILE + 9f);
        strikeAndFinishSwing(ghoul, playerAt(12 * TILE + 23f));
        assertEquals(0f, ghoul.getTransform().velocity.x, 1e-6);
    }

    @Test
    void neverBacksOutOfItsRoom() {
        // Its back (200 − 8.4 − 1) would cross the room's left edge
        Room room = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(192f, 0f, 200f, 192f));
        world.getLevel().addRoom(room);
        HunterEnemy ghoul = hunter("MELEE", EnemySkill.MEDIUM, 200f);
        ghoul.setHomeRoom(room);
        strikeAndFinishSwing(ghoul, playerAt(214f));
        assertEquals(0f, ghoul.getTransform().velocity.x, 1e-6);
    }

    @Test
    void flyerBacksOffInTheAir() {
        HunterEnemy wasp = hunter("FLYING", EnemySkill.MEDIUM, 200f);
        strikeAndFinishSwing(wasp, playerAt(208f));
        assertEquals(-wasp.chaseSpeed() * 0.7f, wasp.getTransform().velocity.x, 1e-4);
        assertTrue(wasp.getMovement().isFlying);
    }

    /** Closer than its retreat distance (1.8 × 19.36 = 34.8) when hit: backs off once the reaction is over. */
    @Test
    void aHitMakesItBackOffOnceItRecovers() {
        HunterEnemy ghoul = hunter("MELEE", EnemySkill.MEDIUM, 200f);
        Player player = playerAt(225f);
        ghoul.updateBehaviour(STEP, world, player);
        assertTrue(ghoul.getTransform().velocity.x > 0f);

        ghoul.getHealth().hurtTime = 0.2f;
        ghoul.updateBehaviour(STEP, world, player);
        ghoul.getHealth().hurtTime = 0f;
        ghoul.updateBehaviour(STEP, world, player);
        assertTrue(ghoul.getTransform().velocity.x < 0f);
    }

    /** Hit beyond its retreat distance with its attack ready: nothing to back off from, it closes in again. */
    @Test
    void alreadyFarEnoughWhenHitItComesOn() {
        HunterEnemy ghoul = hunter("MELEE", EnemySkill.MEDIUM, 200f);
        Player player = playerAt(260f);
        ghoul.updateBehaviour(STEP, world, player);
        ghoul.getHealth().hurtTime = 0.2f;
        ghoul.updateBehaviour(STEP, world, player);
        ghoul.getHealth().hurtTime = 0f;
        ghoul.updateBehaviour(STEP, world, player);
        assertEquals(ghoul.chaseSpeed(), ghoul.getTransform().velocity.x, 1e-4);
    }

    @Test
    void deadEnemiesDoNothing() {
        HunterEnemy ghoul = hunter("MELEE", EnemySkill.MEDIUM, 200f);
        ghoul.getHealth().applyDamage(30);
        ghoul.updateBehaviour(STEP, world, playerAt(214f));
        assertFalse(ghoul.getAttack().isRequested);
        assertFalse(ghoul.isEngaged());
    }

    /** A whole fight: it strikes, backs off, waits, and strikes again, never faster than the cap. */
    @Test
    void strikesBacksOffAndComesAgain() {
        for (String type : new String[] {"MELEE", "CRAWLER", "FLYING"}) {
            HunterEnemy enemy = hunter(type, EnemySkill.MEDIUM, 200f);
            Player player = playerAt(260f);
            int strikes = 0;
            boolean hasBackedOff = false;
            float swing = 0f;
            PhysicsSystem physics = new PhysicsSystem(world, new CollisionSystem(world));
            AttackComponent attack = enemy.getAttack();
            for (float t = 0f; t < 6f; t += STEP) {
                enemy.updateBehaviour(STEP, world, player);
                float vx = enemy.getTransform().velocity.x;
                assertTrue(Math.abs(vx) <= MAX_CHASE_SPEED + 1e-4, type);
                if (vx < 0f && player.getTransform().position.x > enemy.getTransform().position.x) hasBackedOff = true;
                physics.update(STEP);
                if (attack.isRequested) {
                    attack.isRequested = false;
                    attack.isAttacking = true;
                    swing = SWING_SECONDS;
                    strikes++;
                }
                if (attack.isAttacking && (swing -= STEP) <= 0f) attack.isAttacking = false;
            }
            assertTrue(strikes >= 2, type + " struck " + strikes + " times");
            assertTrue(hasBackedOff, type);
            world.removeEntity(enemy);
        }
    }

    private void runWithPhysics(HunterEnemy enemy, Player player, float seconds) {
        PhysicsSystem physics = new PhysicsSystem(world, new CollisionSystem(world));
        for (float t = 0f; t < seconds; t += STEP) {
            enemy.updateBehaviour(STEP, world, player);
            enemy.getAttack().isRequested = false;
            physics.update(STEP);
        }
    }
}
