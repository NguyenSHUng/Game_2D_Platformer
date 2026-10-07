package com.hung.animation;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Chooses which animation each entity plays and advances its time. Priority:
 * DEATH > HIT > ATTACK > SHOOT (standing still after a shot) > climbing (IDLE until there is a CLIMB
 * animation) > CROUCH > JUMP > WALK > IDLE. The states come from the active stance's set (holding the
 * pistol: the two-handed *_PISTOL animations), so walking or jumping keeps the gun aimed.
 *  - JUMP frames follow the vertical speed (launch → apex → fall) instead of time.
 *  - WALK plays faster when the entity moves faster than its stride speed (capped,
 *    until there is a RUN animation), so the feet slide less.
 * Reads: TransformComponent, MovementComponent, AttackComponent, HealthComponent. Writes: AnimationComponent.
 */
public class AnimationSystem {

    /** Horizontal speed below this counts as standing still (world units per second). */
    private static final float WALK_THRESHOLD = 1f;
    /** WALK playback rate limits relative to the authored timing. */
    private static final float MIN_WALK_PLAYBACK = 0.5f;
    private static final float MAX_WALK_PLAYBACK = 2.5f;
    /** Keeps the JUMP phase just below 1 so the last frame is picked, not past the end. */
    private static final float MAX_JUMP_PHASE = 0.999f;

    private final World world;

    public AnimationSystem(World world) {
        this.world = world;
    }

    public void update(float delta) {
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            AnimationComponent animation = entity.getAnimation();
            if (animation == null) continue;

            AnimationType nextType = resolveType(entity);
            if (!animation.animations.containsKey(nextType)) {
                nextType = AnimationType.IDLE;
            }

            if (nextType != animation.currentType) {
                animation.currentType = nextType;
                animation.stateTime = 0f;
            } else {
                animation.stateTime += delta * playbackRate(entity, animation);
            }
            if (nextType == AnimationType.JUMP) followJumpArc(entity, animation);
        }
    }

    private static AnimationType resolveType(Entity entity) {
        HealthComponent health = entity.getHealth();
        if (health != null && health.isDead) return AnimationType.DEATH;
        if (health != null && health.hurtTime > 0f) return AnimationType.HIT;
        AttackComponent attack = entity.getAttack();
        if (attack != null && attack.isAttacking) return attack.animation;

        MovementComponent movement = entity.getMovement();
        WeaponComponent weapon = entity.getWeapon();
        boolean isStill = Math.abs(entity.getTransform().velocity.x) <= WALK_THRESHOLD;
        boolean isStanding = movement == null || (movement.isGrounded && !movement.isClimbing && !movement.isCrouching);
        if (weapon != null && weapon.shootTime > 0f && isStill && isStanding) return AnimationType.SHOOT;
        if (movement != null && !movement.isFlying) {
            if (movement.isClimbing) return AnimationType.IDLE;
            if (movement.isCrouching) return AnimationType.CROUCH;
            if (!movement.isGrounded) return AnimationType.JUMP;
        }
        if (Math.abs(entity.getTransform().velocity.x) > WALK_THRESHOLD) return AnimationType.WALK;
        return AnimationType.IDLE;
    }

    private static float playbackRate(Entity entity, AnimationComponent animation) {
        if (animation.currentType != AnimationType.WALK || animation.strideSpeed <= 0f) return 1f;
        float rate = Math.abs(entity.getTransform().velocity.x) / animation.strideSpeed;
        return MathUtils.clamp(rate, MIN_WALK_PLAYBACK, MAX_WALK_PLAYBACK);
    }

    /** Picks the JUMP frame from the vertical speed: +jumpSpeed = launch, 0 = mid, -jumpSpeed = fall. */
    private static void followJumpArc(Entity entity, AnimationComponent animation) {
        MovementComponent movement = entity.getMovement();
        if (movement == null || movement.jumpSpeed <= 0f) return;
        float phase = (movement.jumpSpeed - entity.getTransform().velocity.y) / (2f * movement.jumpSpeed);
        animation.stateTime = MathUtils.clamp(phase, 0f, MAX_JUMP_PHASE)
            * AnimationFrames.duration(animation, AnimationType.JUMP);
    }
}
