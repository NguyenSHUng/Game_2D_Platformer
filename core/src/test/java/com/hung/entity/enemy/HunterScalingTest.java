package com.hung.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.ai.PatrolSystem;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.core.GameMode;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden values of the numbers a hunter scales every frame: chase speed and its cap, seconds
 * between attacks and notice range, per enemy class and mode. Patrol speed is not scaled.
 */
class HunterScalingTest {

    private static final float TILE = 16f;
    private static final float ATTACK_RANGE = 20f;
    private static final float MAX_CHASE_SPEED = 84f;
    private static final float EDGE = 0.05f;

    private static World floor() {
        TileGrid grid = new TileGrid(80, 30, TILE);
        for (int x = 0; x < 80; x++) grid.set(x, 0, TileType.SOLID);
        return new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
    }

    /** A hunter of the class with its feet at (x, TILE); a flyer has its floor there. */
    private static HunterEnemy hunter(String type, String mode, float moveSpeed) {
        HunterEnemy enemy = switch (type) {
            case "MELEE" -> new MeleeEnemy(ATTACK_RANGE);
            case "CRAWLER" -> new CrawlerEnemy(ATTACK_RANGE);
            case "FLYING" -> new FlyingEnemy(ATTACK_RANGE, TILE + FlyingEnemy.CRUISE_HEIGHT);
            default -> throw new IllegalArgumentException(type);
        };
        enemy.setSkill(GameMode.valueOf(mode).enemySkill);
        boolean isFlying = enemy instanceof FlyingEnemy;
        enemy.getTransform().position.set(200f, isFlying ? TILE + FlyingEnemy.CRUISE_HEIGHT : TILE);
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = moveSpeed;
        movement.isGrounded = !isFlying;
        movement.isFlying = isFlying;
        enemy.setMovement(movement);
        ColliderComponent collider = new ColliderComponent();
        collider.width = 10f;
        collider.height = 20f;
        enemy.setCollider(collider);
        enemy.setAttack(new AttackComponent());
        enemy.setHealth(new HealthComponent(30));
        PatrolComponent patrol = new PatrolComponent();
        patrol.minX = 0f;
        patrol.maxX = 1000f;
        enemy.setPatrol(patrol);
        return enemy;
    }

    private static Player playerAt(float x, float y) {
        Player player = new Player();
        player.getTransform().position.set(x, y);
        return player;
    }

    /**
     * Chase speed = min(84, moveSpeed × class scale × chaseScale). The walk speeds are the ones
     * EnemyFactory gives the real enemies (EnemyStatScalingTest.moveSpeedOfRealEnemies).
     */
    @ParameterizedTest(name = "{0} walking {2} on {1} chases at {3}")
    @CsvSource({
        "MELEE,   EASY,   18,     43.56",    "MELEE,   MEDIUM, 18,     51.48",    "MELEE,   HARD, 18,     59.4",
        "MELEE,   EASY,   19.776, 47.85792", "MELEE,   MEDIUM, 19.776, 56.55936", "MELEE,   HARD, 19.776, 65.2608",
        "CRAWLER, EASY,   41.268, 72.63168", "CRAWLER, MEDIUM, 41.268, 84",       "CRAWLER, HARD, 41.268, 84",
        "FLYING,  EASY,   31.2,   48.048",   "FLYING,  MEDIUM, 31.2,   56.784",   "FLYING,  HARD, 31.2,   65.52",
    })
    void chaseSpeedPerClassAndMode(String type, String mode, float moveSpeed, float expected) {
        assertEquals(expected, hunter(type, mode, moveSpeed).chaseSpeed(), 1e-3);
    }

    @ParameterizedTest(name = "{0} on {1} never chases faster than 84")
    @CsvSource({
        "MELEE, EASY", "MELEE, MEDIUM", "MELEE, HARD",
        "CRAWLER, EASY", "CRAWLER, MEDIUM", "CRAWLER, HARD",
        "FLYING, EASY", "FLYING, MEDIUM", "FLYING, HARD",
    })
    void chaseSpeedIsCapped(String type, String mode) {
        assertEquals(MAX_CHASE_SPEED, hunter(type, mode, 500f).chaseSpeed(), 1e-6);
    }

    /** The velocity a chase sets is the capped speed too, not only chaseSpeed(). */
    @Test
    void aChasingCrawlerMovesAtTheCap() {
        HunterEnemy crawler = hunter("CRAWLER", "HARD", 41.268f);
        crawler.updateBehaviour(0.016f, floor(), playerAt(60f, TILE));
        assertEquals(-MAX_CHASE_SPEED, crawler.getTransform().velocity.x, 1e-4);
    }

    /** Seconds between attacks = class cooldown × cooldownScale. */
    @ParameterizedTest(name = "{0} on {1} attacks every {2} s")
    @CsvSource({
        "MELEE,   EASY, 1.14",  "MELEE,   MEDIUM, 0.96", "MELEE,   HARD, 0.78",
        "CRAWLER, EASY, 0.855", "CRAWLER, MEDIUM, 0.72", "CRAWLER, HARD, 0.585",
        "FLYING,  EASY, 1.33",  "FLYING,  MEDIUM, 1.12", "FLYING,  HARD, 0.91",
    })
    void attackCooldownPerClassAndMode(String type, String mode, float seconds) {
        World world = floor();
        HunterEnemy enemy = hunter(type, mode, 20f);
        Player player = playerAt(210f, TILE);
        AttackComponent attack = enemy.getAttack();

        enemy.updateBehaviour(0.001f, world, player);
        assertTrue(attack.isRequested);
        attack.isRequested = false;

        enemy.updateBehaviour(seconds - 0.002f, world, player);
        assertFalse(attack.isRequested);
        enemy.updateBehaviour(0.004f, world, player);
        assertTrue(attack.isRequested);
    }

    /** Notice range = class range × aggroScale, horizontally; class height × aggroScale, vertically. */
    @ParameterizedTest(name = "{0} on {1} notices {2} across, {3} up")
    @CsvSource({
        "MELEE,   EASY, 138, 55.2",  "MELEE,   MEDIUM, 156, 62.4",  "MELEE,   HARD, 180, 72",
        "CRAWLER, EASY, 184, 55.2",  "CRAWLER, MEDIUM, 208, 62.4",  "CRAWLER, HARD, 240, 72",
        "FLYING,  EASY, 207, 128.8", "FLYING,  MEDIUM, 234, 145.6", "FLYING,  HARD, 270, 168",
    })
    void noticeRangePerClassAndMode(String type, String mode, float across, float up) {
        World world = floor();
        assertTrue(engages(world, type, mode, across - EDGE, 0f));
        assertFalse(engages(world, type, mode, across + EDGE, 0f));
        assertTrue(engages(world, type, mode, 0f, up - EDGE));
        assertFalse(engages(world, type, mode, 0f, up + EDGE));
    }

    /** A fresh hunter (no memory) sees a player dx to its right, dy above its floor. */
    private static boolean engages(World world, String type, String mode, float dx, float dy) {
        HunterEnemy enemy = hunter(type, mode, 20f);
        enemy.updateBehaviour(0.001f, world, playerAt(200f + dx, TILE + dy));
        return enemy.isEngaged();
    }

    /** PatrolSystem walks every enemy at its plain moveSpeed, whatever the mode. */
    @ParameterizedTest
    @EnumSource(GameMode.class)
    void patrolSpeedIsNotScaled(GameMode mode) {
        for (String type : new String[] {"MELEE", "CRAWLER", "FLYING"}) {
            World world = floor();
            HunterEnemy enemy = hunter(type, mode.name(), 18f);
            world.addEntity(enemy);
            new PatrolSystem(world).update();
            assertEquals(18f, Math.abs(enemy.getTransform().velocity.x), 1e-6, type);
        }
    }
}
