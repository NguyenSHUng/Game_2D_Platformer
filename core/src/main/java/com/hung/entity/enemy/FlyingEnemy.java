package com.hung.entity.enemy;

import com.badlogic.gdx.math.MathUtils;
import com.hung.animation.AnimationFrames;
import com.hung.animation.AnimationType;
import com.hung.component.AnimationComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Flying hunter (flyer_wasp_01): no gravity. Cruises above its floor while
 * patrolling, hovers just above the player while hunting and sinks to the player's body during a
 * dive. Ledges don't stop it, it may strike in the air, and its wreck drops to the floor after DEATH.
 */
public class FlyingEnemy extends HunterEnemy {

    /** Patrols this high above its floor. */
    public static final float CRUISE_HEIGHT = 40f;
    /** Hunts this high above the player's head. */
    private static final float HUNT_HEIGHT = 18f;
    private static final float AGGRO_RANGE = 180f;
    private static final float AGGRO_HEIGHT = 112f;
    private static final float ATTACK_COOLDOWN = 1.4f;
    private static final float CHASE_SPEED_SCALE = 1.4f;
    /** Altitude correction: vertical speed per unit of height error, per second. */
    private static final float ALTITUDE_GAIN = 4f;
    /** Vertical speed limits, world units per second (cruising / diving). */
    private static final float MAX_CLIMB_SPEED = 45f;
    private static final float MAX_DIVE_SPEED = 90f;
    /** After DEATH the wreck rests on the frame's bottom row (the generator's floor line). */
    private static final float CRASH_FOOT_OFFSET = 0f;

    private final float homeFloorY;
    private boolean hasCrashed;

    /** @param spawnY bottom of the body at spawn, {@link #CRUISE_HEIGHT} above its floor */
    public FlyingEnemy(float attackRange, float spawnY) {
        super(attackRange);
        this.homeFloorY = spawnY - CRUISE_HEIGHT;
    }

    public boolean hasCrashed() {
        return hasCrashed;
    }

    @Override
    protected float aggroRange() {
        return AGGRO_RANGE;
    }

    @Override
    protected float aggroHeight() {
        return AGGRO_HEIGHT;
    }

    @Override
    protected float attackCooldown() {
        return ATTACK_COOLDOWN;
    }

    @Override
    protected float chaseSpeedScale() {
        return CHASE_SPEED_SCALE;
    }

    @Override
    protected float floorY() {
        return homeFloorY;
    }

    /** Dives to the player's height. */
    @Override
    protected boolean canReachHeight(Entity target, float dy) {
        return true;
    }

    @Override
    protected boolean canStrike() {
        return true;
    }

    @Override
    protected void chase(World world, float direction) {
        getTransform().velocity.x = direction * chaseSpeed();
    }

    /** Ledges and floor hazards don't matter in the air (its room still does). */
    @Override
    protected boolean canBackOff(World world, float direction) {
        return true;
    }

    /** Keeps flying (no standing still while attacking) and steers to the height of this moment. */
    @Override
    protected void afterThink(Entity target, boolean isAttacking) {
        float targetY = homeFloorY + CRUISE_HEIGHT;
        if (isEngaged()) {
            float playerY = target.getTransform().position.y;
            targetY = isAttacking
                ? playerY + bodyHeight(target) * 0.5f - bodyHeight(this) * 0.5f
                : playerY + HUNT_HEIGHT + bodyHeight(target);
        }
        float limit = isAttacking ? MAX_DIVE_SPEED : MAX_CLIMB_SPEED;
        TransformComponent transform = getTransform();
        transform.velocity.y = MathUtils.clamp((targetY - transform.position.y) * ALTITUDE_GAIN, -limit, limit);
    }

    @Override
    protected void whileHurt() {
        super.whileHurt();
        getTransform().velocity.y = 0f;
    }

    /** Hangs in the air while DEATH plays, then the wreck falls: gravity on, feet on the frame's floor row. */
    @Override
    protected void whileDead() {
        AnimationComponent animation = getAnimation();
        MovementComponent movement = getMovement();
        HealthComponent health = getHealth();
        if (hasCrashed || animation == null || movement == null) return;
        getTransform().velocity.setZero();
        if (health.deadTime < AnimationFrames.duration(animation, AnimationType.DEATH)) return;
        // Same picture, new reference row: shift the feet down by the frame's floor offset.
        getTransform().position.y -= (animation.footOffset - CRASH_FOOT_OFFSET) * animation.drawScale;
        animation.footOffset = CRASH_FOOT_OFFSET;
        movement.isFlying = false;
        hasCrashed = true;
    }
}
