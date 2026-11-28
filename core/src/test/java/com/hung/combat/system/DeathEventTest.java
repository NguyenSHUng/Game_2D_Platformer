package com.hung.combat.system;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.Damage;
import com.hung.component.HealthComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.enemy.MeleeEnemy;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.World;
import com.hung.world.event.EnemyKilledEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** HealthSystem reports each enemy's death once, on the frame it dies, and never the player's. */
class DeathEventTest {

    @Test
    void anEnemysDeathIsReportedOnce() {
        World world = new World(new Level("test", null, new TileGrid(10, 10, 16f), null, new Vector2(), null, new Array<Rectangle>()));
        EventBus events = new EventBus();
        List<Object> deaths = new ArrayList<>();
        events.subscribe(EnemyKilledEvent.class, event -> deaths.add(event.enemy));
        MeleeEnemy enemy = new MeleeEnemy(20f);
        enemy.setHealth(new HealthComponent(30));
        world.addEntity(enemy);
        Player player = new Player();
        player.setHealth(new HealthComponent(100));
        world.setPlayer(player);
        HealthSystem health = new HealthSystem(world, events);

        Damage.apply(enemy, 10, 0f);
        health.update(0.016f);
        assertEquals(0, deaths.size());

        Damage.apply(enemy, 30, 0f);
        for (int i = 0; i < 30; i++) health.update(0.016f);
        assertEquals(1, deaths.size());
        assertSame(enemy, deaths.get(0));

        Damage.apply(player, 100, 0f);
        for (int i = 0; i < 30; i++) health.update(0.016f);
        assertEquals(1, deaths.size());
    }
}
