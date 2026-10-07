package com.hung.combat;

import com.hung.animation.AnimationFrames;
import com.hung.animation.AnimationType;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;

/**
 * Applies one hit to an entity: hit points, hurt / death state, knockback and invulnerability.
 * Shared by every system that deals damage (AttackSystem, ContactDamageSystem).
 */
public final class Damage {

    /** Horizontal push away from the attacker, world units per second. */
    private static final float KNOCKBACK_SPEED = 90f;
    /** Small hop when hit on the ground, as a fraction of the target's jump speed. */
    private static final float KNOCKBACK_LIFT_RATIO = 0.35f;
    /** Hurt reaction length when the target has no HIT animation, in seconds. */
    private static final float DEFAULT_HURT_TIME = 0.3f;

    private Damage() {
    }

    /**
     * Hits the target for the given amount, pushing it away from sourceX.
     * Returns false when the hit is ignored (no health, already dead, invulnerable).
     */
    public static boolean apply(Entity target, int amount, float sourceX) {
        HealthComponent health = target.getHealth();
        if (health == null || health.isDead || health.invulnerableTime > 0f) return false;

        health.health = Math.max(0, health.health - amount);
        health.invulnerableTime = health.invulnerability;
        AnimationComponent animation = target.getAnimation();
        if (health.health == 0) {
            health.isDead = true;
            health.deadTime = 0f;
            health.hurtTime = 0f;
        } else {
            float hitDuration = animation != null ? AnimationFrames.duration(animation, AnimationType.HIT) : 0f;
            health.hurtTime = hitDuration > 0f ? hitDuration : DEFAULT_HURT_TIME;
            // A second hit during the reaction replays it from the start
            if (animation != null && animation.currentType == AnimationType.HIT) animation.stateTime = 0f;
        }

        AttackComponent attack = target.getAttack();
        if (attack != null) {
            attack.isAttacking = false;
            attack.isRequested = false;
        }

        TransformComponent transform = target.getTransform();
        float direction = transform.position.x >= sourceX ? 1f : -1f;
        float push = 1f - health.knockbackResistance;
        transform.velocity.x = direction * KNOCKBACK_SPEED * push;
        MovementComponent movement = target.getMovement();
        if (movement != null) {
            if (movement.isGrounded) transform.velocity.y = movement.jumpSpeed * KNOCKBACK_LIFT_RATIO * push;
            movement.isClimbing = false;
            movement.isCrouching = false;
        }
        return true;
    }
}
