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
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The behaviours a mode's EnemySkill switches on: memory, calling others, jumping, aiming ahead. */
class EnemySkillTest {

    private static final float TILE = 16f;
    private static final float STEP = 1f / 60f;

    private final TileGrid grid = new TileGrid(60, 12, TILE);
    private final World world;

    EnemySkillTest() {
        for (int x = 0; x < 60; x++) grid.set(x, 1, TileType.SOLID);
        world = new World(new Level("test", null, grid, new PhysicsSettings(TILE, 6f, 3.5f, 6f), new Vector2(), null, new Array<Rectangle>()));
    }

    private MeleeEnemy ghoul(float x, EnemySkill skill) {
        MeleeEnemy ghoul = new MeleeEnemy(20f);
        ghoul.setSkill(skill);
        ghoul.getTransform().position.set(x, 2 * TILE);
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = 16f;
        movement.isGrounded = true;
        ghoul.setMovement(movement);
        ColliderComponent collider = new ColliderComponent();
        collider.width = 12f;
        collider.height = 23f;
        ghoul.setCollider(collider);
        ghoul.setAttack(new AttackComponent());
        ghoul.setHealth(new HealthComponent(30));
        ghoul.setPatrol(new PatrolComponent());
        world.addEntity(ghoul);
        return ghoul;
    }

    private static Player playerAt(float x, float y) {
        Player player = new Player();
        player.getTransform().position.set(x, y);
        return player;
    }

    /** Runs brain + physics for the given time. */
    private void run(MeleeEnemy enemy, Player player, float seconds) {
        PhysicsSystem physics = new PhysicsSystem(world, new CollisionSystem(world));
        for (float t = 0f; t < seconds; t += STEP) {
            enemy.updateBehaviour(STEP, world, player);
            enemy.getAttack().isRequested = false;
            physics.update(STEP);
        }
    }

    @Test
    void remembersThePlayerForAWhile() {
        MeleeEnemy ghoul = ghoul(200f, EnemySkill.MEDIUM);
        ghoul.updateBehaviour(STEP, world, playerAt(300f, 2 * TILE));
        assertTrue(ghoul.isEngaged());

        ghoul.updateBehaviour(1f, world, playerAt(400f, 2 * TILE));
        assertTrue(ghoul.isEngaged());
        ghoul.updateBehaviour(2.5f, world, playerAt(400f, 2 * TILE));
        assertFalse(ghoul.isEngaged());
    }

    @Test
    void aShotFromAfarProvokesIt() {
        MeleeEnemy ghoul = ghoul(200f, EnemySkill.EASY);
        Player player = playerAt(400f, 2 * TILE);
        ghoul.updateBehaviour(STEP, world, player);
        assertFalse(ghoul.isEngaged());

        ghoul.getHealth().hurtTime = 0.2f;
        ghoul.updateBehaviour(STEP, world, player);
        ghoul.getHealth().hurtTime = 0f;
        ghoul.updateBehaviour(STEP, world, player);
        assertTrue(ghoul.isEngaged());
    }

    @Test
    void onlyPackHuntersCallTheOthers() {
        MeleeEnemy loner = ghoul(300f, EnemySkill.EASY);
        MeleeEnemy loneNeighbour = ghoul(220f, EnemySkill.EASY);
        Player player = playerAt(400f, 2 * TILE);
        loner.updateBehaviour(STEP, world, player);
        loneNeighbour.updateBehaviour(STEP, world, player);
        assertTrue(loner.isEngaged());
        assertFalse(loneNeighbour.isEngaged());

        MeleeEnemy caller = ghoul(300f, EnemySkill.MEDIUM);
        MeleeEnemy neighbour = ghoul(220f, EnemySkill.MEDIUM);
        caller.updateBehaviour(STEP, world, player);
        neighbour.updateBehaviour(STEP, world, player);
        assertTrue(neighbour.isEngaged());
    }

    @Test
    void jumpsAGapOnlyWhenItCan() {
        for (int x = 14; x < 16; x++) grid.set(x, 1, TileType.EMPTY);
        MeleeEnemy easy = ghoul(200f, EnemySkill.EASY);
        run(easy, playerAt(300f, 2 * TILE), 4f);
        assertTrue(easy.getTransform().position.x < 14 * TILE);
        world.removeEntity(easy);

        MeleeEnemy medium = ghoul(200f, EnemySkill.MEDIUM);
        run(medium, playerAt(300f, 2 * TILE), 4f);
        assertTrue(medium.getTransform().position.x > 16 * TILE);
        assertEquals(2 * TILE, medium.getTransform().position.y, 0.1f);
    }

    @Test
    void jumpsASpikeAndAStep() {
        grid.set(15, 2, TileType.SPIKE);
        grid.set(20, 2, TileType.SOLID);
        grid.set(21, 2, TileType.SOLID);
        MeleeEnemy ghoul = ghoul(200f, EnemySkill.HARD);
        run(ghoul, playerAt(345f, 3 * TILE), 6f);
        assertEquals(3 * TILE, ghoul.getTransform().position.y, 0.1f);
    }

    @Test
    void jumpsOntoALowPlatformUnderThePlayer() {
        for (int x = 10; x < 18; x++) grid.set(x, 3, TileType.PLATFORM);
        MeleeEnemy ghoul = ghoul(220f, EnemySkill.MEDIUM);
        run(ghoul, playerAt(225f, 4 * TILE), 1.5f);
        assertEquals(4 * TILE, ghoul.getTransform().position.y, 0.1f);
        assertTrue(ghoul.getPatrol().maxX > ghoul.getPatrol().minX);
    }

    @Test
    void doesNotSwingAtAPlayerFarAbove() {
        MeleeEnemy ghoul = ghoul(200f, EnemySkill.EASY);
        ghoul.updateBehaviour(STEP, world, playerAt(205f, 2 * TILE + 40f));
        assertTrue(ghoul.isEngaged());
        assertFalse(ghoul.getAttack().isRequested);
    }

    @Test
    void aimsAheadOfAPlayerRunningIn() {
        Player player = playerAt(230f, 2 * TILE);
        player.getTransform().velocity.x = -96f;
        MeleeEnemy easy = ghoul(200f, EnemySkill.EASY);
        easy.updateBehaviour(STEP, world, player);
        assertFalse(easy.getAttack().isRequested);

        MeleeEnemy hard = ghoul(200f, EnemySkill.HARD);
        hard.updateBehaviour(STEP, world, player);
        assertTrue(hard.getAttack().isRequested);
    }

    @Test
    void scalesDamageAndKnockback() {
        MeleeEnemy hard = ghoul(200f, EnemySkill.HARD);
        assertEquals(15, hard.getContactDamage());
        assertEquals(0.4f, hard.getKnockbackResistance(), 1e-6);
        assertEquals(48, EnemySkill.HARD.health(30));
        assertEquals(12, EnemySkill.HARD.damage(8));
    }
}
