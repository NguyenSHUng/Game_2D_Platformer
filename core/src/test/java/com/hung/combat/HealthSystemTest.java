package com.hung.combat;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthSystemTest {

    private static World world() {
        Level level = new Level("test", null, new TileGrid(10, 10, 16f), null, new Vector2(), null, new Array<Rectangle>());
        return new World(level);
    }

    @Test
    void timersCountDownToZero() {
        World world = world();
        Entity enemy = new Entity();
        HealthComponent health = new HealthComponent(10);
        health.hurtTime = 0.2f;
        health.invulnerableTime = 0.1f;
        enemy.setHealth(health);
        world.addEntity(enemy);

        new HealthSystem(world).update(0.15f);
        assertEquals(0.05f, health.hurtTime, 1e-5f);
        assertEquals(0f, health.invulnerableTime);
    }

    @Test
    void deadEnemyIsRemovedAfterItLingers() {
        World world = world();
        Entity enemy = new Entity();
        HealthComponent health = new HealthComponent(10);
        enemy.setHealth(health);
        world.addEntity(enemy);
        Damage.apply(enemy, 10, 0f);

        HealthSystem system = new HealthSystem(world);
        system.update(1f);
        assertTrue(world.getEntities().contains(enemy, true));
        system.update(0.6f);
        assertFalse(world.getEntities().contains(enemy, true));
    }
}
