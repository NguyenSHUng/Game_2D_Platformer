package com.hung.world.event;

import com.hung.entity.enemy.Enemy;

/**
 * Published on the EventBus once per enemy, on the frame it dies (HealthSystem). The publisher reuses
 * one instance, so listeners copy what they keep.
 */
public class EnemyKilledEvent {

    public Enemy enemy;

    public EnemyKilledEvent set(Enemy enemy) {
        this.enemy = enemy;
        return this;
    }
}
