package com.hung.combat.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.hung.animation.AnimationFrames;
import com.hung.animation.AnimationType;
import com.hung.component.AnimationComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.World;
import com.hung.world.system.PlayerRespawn;

/**
 * Hurt and death over time:
 *  - counts down hurt and invulnerability timers and slows knockback to a stop;
 *  - a dead enemy plays DEATH, lies there a moment, then is removed;
 *  - a dead player plays DEATH, then comes back at the respawn point with full health.
 * Reads/Writes: HealthComponent, TransformComponent. Removes entities from the World.
 */
public class HealthSystem {

    private static final String TAG = "HealthSystem";
    /** How fast knockback slows down, world units per second squared. */
    private static final float KNOCKBACK_FRICTION = 400f;
    /** Seconds a dead enemy stays after its DEATH animation. */
    private static final float CORPSE_LINGER = 1.5f;
    /** Seconds after the player's DEATH animation before respawning. */
    private static final float PLAYER_RESPAWN_DELAY = 1f;

    private final World world;

    public HealthSystem(World world) {
        this.world = world;
    }

    public void update(float delta) {
        Array<Entity> entities = world.getEntities();
        // Backwards, so removing an entity doesn't skip the next one
        for (int i = entities.size - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            HealthComponent health = entity.getHealth();
            if (health == null) continue;

            health.invulnerableTime = Math.max(0f, health.invulnerableTime - delta);
            if (health.isDead()) {
                updateDead(entity, health, delta);
            } else if (health.hurtTime > 0f) {
                health.hurtTime = Math.max(0f, health.hurtTime - delta);
                slowDown(entity, delta);
            }
        }
    }

    private void updateDead(Entity entity, HealthComponent health, float delta) {
        health.deadTime += delta;
        MovementComponent movement = entity.getMovement();
        if (movement == null || movement.isGrounded) slowDown(entity, delta);

        AnimationComponent animation = entity.getAnimation();
        float deathDuration = animation != null ? AnimationFrames.duration(animation, AnimationType.DEATH) : 0f;
        if (entity == world.getPlayer()) {
            if (health.deadTime >= deathDuration + PLAYER_RESPAWN_DELAY) {
                health.restoreFull();
                health.deadTime = 0f;
                health.hurtTime = 0f;
                PlayerRespawn.respawn(world, entity);
                Gdx.app.log(TAG, "Player died; respawned at " + world.getRespawnPoint());
            }
        } else if (health.deadTime >= deathDuration + CORPSE_LINGER) {
            world.removeEntity(entity);
        }
    }

    private static void slowDown(Entity entity, float delta) {
        TransformComponent transform = entity.getTransform();
        float speed = Math.abs(transform.velocity.x);
        float slowed = Math.max(0f, speed - KNOCKBACK_FRICTION * delta);
        transform.velocity.x = Math.signum(transform.velocity.x) * slowed;
    }
}
