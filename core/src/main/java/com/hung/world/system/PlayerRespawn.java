package com.hung.world.system;

import com.hung.component.AttackComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Puts the player back at the world's respawn point (last checkpoint, else the spawn),
 * standing still, with a moment of invulnerability. Health is left to the caller.
 */
public final class PlayerRespawn {

    /** Seconds the player can't be hurt after coming back. */
    private static final float RESPAWN_INVULNERABILITY = 1.5f;

    private PlayerRespawn() {
    }

    public static void respawn(World world, Entity player) {
        player.getTransform().position.set(world.getRespawnPoint());
        player.getTransform().velocity.setZero();

        MovementComponent movement = player.getMovement();
        if (movement != null) {
            movement.isClimbing = false;
            movement.isCrouching = false;
            movement.isGrounded = false;
            movement.jumpBufferTime = 0f;
        }
        AttackComponent attack = player.getAttack();
        if (attack != null) {
            attack.isAttacking = false;
            attack.isRequested = false;
        }
        HealthComponent health = player.getHealth();
        if (health != null) {
            health.hurtTime = 0f;
            health.invulnerableTime = RESPAWN_INVULNERABILITY;
        }
    }
}
