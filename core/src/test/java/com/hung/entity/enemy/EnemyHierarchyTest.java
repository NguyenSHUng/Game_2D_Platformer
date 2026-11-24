package com.hung.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.entity.Entity;
import com.hung.entity.GameCharacter;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What each character class adds or overrides, and the hunt the subclasses share. */
class EnemyHierarchyTest {

    private static final float TILE = 16f;
    private static final float ATTACK_RANGE = 20f;

    private static World floor() {
        TileGrid grid = new TileGrid(40, 6, TILE);
        for (int x = 0; x < 40; x++) grid.set(x, 0, TileType.SOLID);
        return new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
    }

    /** Gives an enemy what the hunt reads: a body on the floor, a walk speed and an attack. */
    private static <T extends Enemy> T equip(T enemy, float x, boolean isGrounded) {
        enemy.getTransform().position.set(x, TILE);
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = 30f;
        movement.isGrounded = isGrounded;
        enemy.setMovement(movement);
        ColliderComponent collider = new ColliderComponent();
        collider.width = 10f;
        collider.height = 20f;
        enemy.setCollider(collider);
        enemy.setAttack(new AttackComponent());
        enemy.setHealth(new HealthComponent(40));
        return enemy;
    }

    private static Player playerAt(float x) {
        Player player = new Player();
        player.getTransform().position.set(x, TILE);
        return player;
    }

    @Test
    void charactersOnOppositeSidesAreHostile() {
        Player player = new Player();
        Enemy crawler = new CrawlerEnemy(ATTACK_RANGE);
        Enemy ghoul = new MeleeEnemy(ATTACK_RANGE);

        assertTrue(player.isHostileTo(crawler));
        assertTrue(ghoul.isHostileTo(player));
        assertFalse(crawler.isHostileTo(ghoul));
        assertFalse(GameCharacter.areHostile(player, new Entity()));
    }

    @Test
    void onlyThePlayerBreaksWalls() {
        assertTrue(new Player().canBreakWalls());
        assertFalse(new CrawlerEnemy(ATTACK_RANGE).canBreakWalls());
        assertFalse(new MeleeEnemy(ATTACK_RANGE).canBreakWalls());
    }

    @Test
    void flyerStrikesInTheAirGroundHunterDoesNot() {
        assertFalse(equip(new CrawlerEnemy(ATTACK_RANGE), 0f, false).canStrike());
        assertTrue(equip(new CrawlerEnemy(ATTACK_RANGE), 0f, true).canStrike());
        assertTrue(equip(new FlyingEnemy(ATTACK_RANGE, TILE + FlyingEnemy.CRUISE_HEIGHT), 0f, false).canStrike());
    }

    @Test
    void hunterInRangeFacesAndAttacksThenWaitsForCooldown() {
        World world = floor();
        CrawlerEnemy crawler = equip(new CrawlerEnemy(ATTACK_RANGE), 200f, true);
        Player player = playerAt(210f);

        crawler.updateBehaviour(0.016f, world, player);
        assertTrue(crawler.isEngaged());
        assertTrue(crawler.getTransform().isFacingRight);
        assertTrue(crawler.getAttack().isRequested);

        crawler.getAttack().isRequested = false;
        crawler.updateBehaviour(0.016f, world, player);
        assertFalse(crawler.getAttack().isRequested);
    }

    @Test
    void hunterChasesAPlayerOutsideAttackRange() {
        CrawlerEnemy crawler = equip(new CrawlerEnemy(ATTACK_RANGE), 200f, true);

        crawler.updateBehaviour(0.016f, floor(), playerAt(100f));

        assertEquals(-30f * 1.6f * EnemySkill.EASY.chaseScale(), crawler.getTransform().velocity.x, 1e-4);
        assertFalse(crawler.getAttack().isRequested);
    }

    @Test
    void meleeChasesAttacksAndKeepsItsDistance() {
        World world = floor();
        MeleeEnemy ghoul = equip(new MeleeEnemy(ATTACK_RANGE), 200f, true);

        ghoul.updateBehaviour(0.016f, world, playerAt(400f));
        assertFalse(ghoul.isEngaged());

        ghoul.updateBehaviour(0.016f, world, playerAt(260f));
        assertEquals(30f * 2.2f * EnemySkill.EASY.chaseScale(), ghoul.getTransform().velocity.x, 1e-4);
        assertFalse(ghoul.getAttack().isRequested);

        ghoul.updateBehaviour(0.016f, world, playerAt(214f));
        assertTrue(ghoul.getAttack().isRequested);

        // On cooldown inside its hold distance (75% of the range): waits instead of bumping in
        ghoul.getAttack().isRequested = false;
        ghoul.getTransform().velocity.x = 30f;
        ghoul.updateBehaviour(0.016f, world, playerAt(214f));
        assertFalse(ghoul.getAttack().isRequested);
        assertEquals(0f, ghoul.getTransform().velocity.x, 1e-6);
    }

    @Test
    void hunterKeepsFacingItsTargetDuringTheSwing() {
        World world = floor();
        MeleeEnemy ghoul = equip(new MeleeEnemy(ATTACK_RANGE), 200f, true);
        ghoul.updateBehaviour(0.016f, world, playerAt(186f));
        ghoul.getAttack().isRequested = false;
        ghoul.getAttack().isAttacking = true;

        ghoul.getTransform().isFacingRight = true; // what PatrolSystem does before the next frame
        ghoul.updateBehaviour(0.016f, world, playerAt(186f));

        assertFalse(ghoul.getTransform().isFacingRight);
    }

    @Test
    void meleeStopsAtALedgeWhileChasing() {
        World world = floor();
        world.getLevel().getGrid().set(13, 0, TileType.EMPTY);
        MeleeEnemy ghoul = equip(new MeleeEnemy(ATTACK_RANGE), 203f, true);

        ghoul.updateBehaviour(0.016f, world, playerAt(260f));

        assertEquals(0f, ghoul.getTransform().velocity.x, 1e-6);
    }

    @Test
    void deadOrHurtEnemiesDoNotThink() {
        World world = floor();
        CrawlerEnemy hurt = equip(new CrawlerEnemy(ATTACK_RANGE), 200f, true);
        hurt.getHealth().hurtTime = 0.3f;
        CrawlerEnemy dead = equip(new CrawlerEnemy(ATTACK_RANGE), 200f, true);
        dead.getHealth().applyDamage(40);

        hurt.updateBehaviour(0.016f, world, playerAt(210f));
        dead.updateBehaviour(0.016f, world, playerAt(210f));

        assertFalse(hurt.getAttack().isRequested);
        assertFalse(dead.getAttack().isRequested);
    }
}
