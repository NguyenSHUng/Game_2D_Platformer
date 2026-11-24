package com.hung.ai;

import com.badlogic.gdx.utils.Array;
import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.entity.enemy.Enemy;
import com.hung.world.World;

/**
 * Runs every enemy's own behaviour once per frame ({@link Enemy#updateBehaviour}): each enemy class
 * decides for itself how to hunt, strike, fly or fall. Runs after PatrolSystem, so a hunting
 * enemy overrides its patrol.
 */
public class EnemyBrainSystem {

    private final World world;

    public EnemyBrainSystem(World world) {
        this.world = world;
    }

    public void update(float delta) {
        Entity player = world.getPlayer();
        
        Entity target = null;

        if (player != null) {
            HealthComponent playerHealth = player.getHealth();

            if (playerHealth == null) {
                target = player;
            } else {
                if (!playerHealth.isDead()) {
                    target = player;
                }
            }
        }

        Array<Entity> entities = world.getEntities();

        for (int i=0; i < entities.size; i++) {
            
            Entity currentEntity = entities.get(i);
            
            if (currentEntity instanceof Enemy) {
                Enemy enemy = (Enemy) currentEntity;
                enemy.updateBehaviour(delta, world, target);
            }
        }
    }
}
