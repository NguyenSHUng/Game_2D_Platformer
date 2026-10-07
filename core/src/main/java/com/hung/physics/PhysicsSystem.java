package com.hung.physics;

import com.badlogic.gdx.utils.Array;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.PhysicsSettings;
import com.hung.world.World;

/**
 * Applies gravity to entities that move (not while on a ladder or flying), then moves every entity by its velocity.
 * Entities with a collider move through the CollisionSystem so the tile map stops them.
 * Reads/Writes: TransformComponent, MovementComponent.
 */
public class PhysicsSystem {

    private final World world;
    private final CollisionSystem collisionSystem;

    public PhysicsSystem(World world, CollisionSystem collisionSystem) {
        this.world = world;
        this.collisionSystem = collisionSystem;
    }

    public void update(float delta) {
        PhysicsSettings physics = world.getLevel().getPhysics();
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            if (entity.getProjectile() != null) continue; // ProjectileSystem moves bullets in collision-checked steps
            TransformComponent transform = entity.getTransform();
            MovementComponent movement = entity.getMovement();
            if (movement != null && !movement.isClimbing && !movement.isFlying) {
                transform.velocity.y = Math.max(transform.velocity.y - physics.gravity * delta, -physics.maxFallSpeed);
            }

            if (entity.getCollider() != null) {
                collisionSystem.move(entity, transform.velocity.x * delta, transform.velocity.y * delta);
            } else {
                transform.position.mulAdd(transform.velocity, delta);
            }
        }
    }
}
