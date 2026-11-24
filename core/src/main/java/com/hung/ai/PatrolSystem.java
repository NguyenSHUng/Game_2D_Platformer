package com.hung.ai;

import com.badlogic.gdx.utils.Array;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.TileGrid;
import com.hung.world.World;

/**
 * Simple enemy AI: walk back and forth inside the patrol range, turning at its ends,
 * at walls and before stepping off a ledge (flyers: at the ends and walls only). Hurt or dead
 * enemies are left to HealthSystem; EnemyBrainSystem overrides this while an enemy hunts.
 * Reads: PatrolComponent, MovementComponent, ColliderComponent. Writes: TransformComponent, PatrolComponent.
 */
public class PatrolSystem {

    private final World world;

    public PatrolSystem(World world) {
        this.world = world;
    }

    public void update() {

        TileGrid grid = world.getLevel().getGrid();
        Array<Entity> entities = world.getEntities();
        
        for (int i = 0; i < entities.size; i++) {
        
            Entity entity = entities.get(i);
            PatrolComponent patrol = entity.getPatrol();
            MovementComponent movement = entity.getMovement();
            ColliderComponent collider = entity.getCollider();

            if (patrol == null || movement == null || collider == null) continue;
            
            HealthComponent health = entity.getHealth();
            
            if (health != null && (health.isDead() || health.hurtTime > 0f)) continue;

            TransformComponent transform = entity.getTransform();
            boolean canMove = movement.isGrounded || movement.isFlying;
            
            if (canMove && shouldTurn(grid, transform, collider, movement, patrol)) {
                patrol.direction = -patrol.direction;
            }
            
            transform.velocity.x = canMove ? patrol.direction * movement.moveSpeed : 0f;
            transform.isFacingRight = patrol.direction > 0;
        }
    }

    private static boolean shouldTurn(
        TileGrid grid, 
        TransformComponent transform, 
        ColliderComponent collider,
        MovementComponent movement, 
        PatrolComponent patrol
    ) {

        float x = transform.position.x;

        if (patrol.direction > 0) {
            if (x >= patrol.maxX) {
                return true;
            }
        }

        if (patrol.direction < 0) {
            if (x <= patrol.minX) {
                return true;
            }
        }

        if (movement.isBlocked) return true;
        if (movement.isFlying) return false;

        return !grid.hasFloorAhead(x, transform.position.y, collider.width / 2f, patrol.direction);
    }
}
