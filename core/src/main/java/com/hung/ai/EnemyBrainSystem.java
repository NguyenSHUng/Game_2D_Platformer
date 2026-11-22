package com.hung.ai;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.hung.animation.AnimationFrames;
import com.hung.animation.AnimationType;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.EnemyBrainComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.TileGrid;
import com.hung.world.World;

/**
 * Enemies with a brain (CRAWLER, FLYING, MINI_BOSS) hunt the player. Runs after PatrolSystem
 * and overrides it while hunting:
 *  - the player within aggroRange (and near its floor) → turn to face them and chase
 *    (ground enemies stop at ledges and walls);
 *  - within attackRange and off cooldown → stand and attack (MINI_BOSS alternates its attacks);
 *    AttackSystem lands the hit from the animation's hitboxes;
 *  - flyers keep an altitude: cruise height over their floor while patrolling, just above the
 *    player while hunting, and sink to the player's body during a dive;
 *  - a dead flyer's wreck drops to the floor once its DEATH animation has played.
 * Reads: player TransformComponent / HealthComponent, EnemyBrainComponent. Writes: velocity, facing,
 * AttackComponent requests, EnemyBrainComponent timers.
 */
public class EnemyBrainSystem {

    /** Altitude correction: vertical speed per unit of height error, per second. */
    private static final float ALTITUDE_GAIN = 4f;
    /** Vertical speed limits of a flyer, world units per second (cruising / diving). */
    private static final float MAX_CLIMB_SPEED = 45f;
    private static final float MAX_DIVE_SPEED = 90f;
    /** Inside this share of the attack range the enemy stops closing in. */
    private static final float HOLD_RATIO = 0.6f;

    private final World world;

    public EnemyBrainSystem(World world) {
        this.world = world;
    }

    public void update(float delta) {
        Entity player = world.getPlayer();
        HealthComponent playerHealth = player != null ? player.getHealth() : null;
        boolean isPlayerAlive = player != null && (playerHealth == null || !playerHealth.isDead());
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity enemy = entities.get(i);
            EnemyBrainComponent brain = enemy.getBrain();
            if (brain == null) continue;
            HealthComponent health = enemy.getHealth();
            if (health != null && health.isDead()) {
                if (brain.isFlying) crashWhenDone(enemy, brain, health);
                continue;
            }
            brain.cooldown = Math.max(0f, brain.cooldown - delta);
            if (health != null && health.hurtTime > 0f) {
                if (brain.isFlying) enemy.getTransform().velocity.y = 0f;
                continue;
            }
            think(enemy, brain, isPlayerAlive ? player : null);
        }
    }

    private void think(Entity enemy, EnemyBrainComponent brain, Entity player) {
        TransformComponent transform = enemy.getTransform();
        AttackComponent attack = enemy.getAttack();
        float floorY = brain.isFlying ? brain.homeFloorY : transform.position.y;
        float dx = player != null ? player.getTransform().position.x - transform.position.x : 0f;
        float dy = player != null ? player.getTransform().position.y - floorY : 0f;
        brain.isEngaged = player != null && Math.abs(dx) <= brain.aggroRange && Math.abs(dy) <= brain.aggroHeight;
        boolean isAttacking = attack != null && attack.isAttacking;

        float targetY = floorY + brain.cruiseHeight;
        if (brain.isEngaged) {
            float playerY = player.getTransform().position.y;
            targetY = playerY + (isAttacking ? playerBodyMid(player) - bodyHeight(enemy) * 0.5f : brain.huntHeight + bodyHeight(player));
            if (!isAttacking) {
                transform.isFacingRight = dx > 0f;
                if (Math.abs(dx) <= brain.attackRange && brain.cooldown <= 0f && attack != null && canStrike(enemy, brain)) {
                    attack.animation = brain.attacks[brain.nextAttack % brain.attacks.length];
                    attack.isRequested = true;
                    brain.nextAttack++;
                    brain.cooldown = brain.attackCooldown;
                    transform.velocity.x = 0f;
                } else if (Math.abs(dx) > brain.attackRange * HOLD_RATIO) {
                    chase(enemy, brain, Math.signum(dx));
                } else {
                    transform.velocity.x = 0f;
                }
            }
        }
        // Standing still while the attack plays (the animation itself lunges / swings / dives).
        if (isAttacking && !brain.isFlying) transform.velocity.x = 0f;
        if (brain.isFlying) holdAltitude(transform, targetY, isAttacking);
    }

    /** Ground enemies attack only with their feet on the floor; flyers any time. */
    private static boolean canStrike(Entity enemy, EnemyBrainComponent brain) {
        MovementComponent movement = enemy.getMovement();
        return brain.isFlying || movement == null || movement.isGrounded;
    }

    private void chase(Entity enemy, EnemyBrainComponent brain, float direction) {
        TransformComponent transform = enemy.getTransform();
        MovementComponent movement = enemy.getMovement();
        float speed = movement != null ? movement.moveSpeed * brain.chaseSpeedScale : 0f;
        if (!brain.isFlying) {
            if (movement == null || !movement.isGrounded || isLedgeAhead(enemy, direction)) {
                transform.velocity.x = 0f;
                return;
            }
        }
        transform.velocity.x = direction * speed;
    }

    private boolean isLedgeAhead(Entity enemy, float direction) {
        TileGrid grid = world.getLevel().getGrid();
        TransformComponent transform = enemy.getTransform();
        ColliderComponent collider = enemy.getCollider();
        return !grid.hasFloorAhead(transform.position.x, transform.position.y, collider.width / 2f, direction);
    }

    private static void holdAltitude(TransformComponent transform, float targetY, boolean isDiving) {
        float limit = isDiving ? MAX_DIVE_SPEED : MAX_CLIMB_SPEED;
        transform.velocity.y = MathUtils.clamp((targetY - transform.position.y) * ALTITUDE_GAIN, -limit, limit);
    }

    /** After the DEATH animation the wreck falls: gravity on, frame floor row on the entity's feet. */
    private static void crashWhenDone(Entity enemy, EnemyBrainComponent brain, HealthComponent health) {
        AnimationComponent animation = enemy.getAnimation();
        MovementComponent movement = enemy.getMovement();
        if (brain.hasCrashed || animation == null || movement == null) return;
        // Hangs in the air (no gravity, no drift) while the DEATH animation shows the fall.
        enemy.getTransform().velocity.setZero();
        if (health.deadTime < AnimationFrames.duration(animation, AnimationType.DEATH)) return;
        // Same picture, new reference row: shift the feet down by the frame's floor offset.
        enemy.getTransform().position.y -= (animation.footOffset - brain.crashFootOffset) * animation.drawScale;
        animation.footOffset = brain.crashFootOffset;
        movement.isFlying = false;
        brain.hasCrashed = true;
    }

    private static float bodyHeight(Entity entity) {
        ColliderComponent collider = entity.getCollider();
        return collider != null ? collider.height : 0f;
    }

    private static float playerBodyMid(Entity player) {
        return bodyHeight(player) * 0.5f;
    }
}
