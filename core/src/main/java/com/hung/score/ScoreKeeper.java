package com.hung.score;

import com.badlogic.gdx.utils.Array;
import com.hung.component.HealthComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.entity.enemy.Enemy;
import com.hung.world.Room;
import com.hung.world.World;
import com.hung.world.event.ChestOpenedEvent;
import com.hung.world.event.EnemyKilledEvent;
import com.hung.world.event.ExitReachedEvent;
import com.hung.world.event.RoomClearedEvent;

/**
 * Turns game events into score, each at most once (RunStats keeps the ids):
 *  - an enemy defeated (EnemyKilledEvent), by its spawn id; enemies not placed by a map have none and score nothing;
 *  - its room cleared, when that was the room's last living enemy (published as RoomClearedEvent);
 *  - a chest opened (ChestOpenedEvent), by its chest id and tier;
 *  - the map completed (ExitReachedEvent).
 * Points come from ScoreRules. Subscribe it before listeners that save or show the score.
 */
public class ScoreKeeper {

    private final World world;
    private final RunStats stats;
    private final ScoreRules rules;
    private final EventBus events;
    private final RoomClearedEvent cleared = new RoomClearedEvent();

    public ScoreKeeper(World world, RunStats stats, ScoreRules rules, EventBus events) {
        this.world = world;
        this.stats = stats;
        this.rules = rules;
        this.events = events;
    }

    public void subscribe() {
        events.subscribe(EnemyKilledEvent.class, event -> onEnemyKilled(event.enemy));
        events.subscribe(ChestOpenedEvent.class, event -> stats.addChest(event.chestId, rules.chest(event.tier)));
        events.subscribe(ExitReachedEvent.class, event -> stats.completeMap(rules.mapComplete()));
    }

    private void onEnemyKilled(Enemy enemy) {
        stats.addKill(enemy.getSpawnId(), rules.enemyKill());
        Room room = enemy.getHomeRoom();
        if (room == null || hasLivingEnemy(room)) return;
        if (stats.addRoom(room.id, rules.roomClear())) events.publish(cleared.set(room, rules.roomClear()));
    }

    private boolean hasLivingEnemy(Room room) {
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            if (!(entities.get(i) instanceof Enemy enemy) || enemy.getHomeRoom() != room) continue;
            HealthComponent health = enemy.getHealth();
            if (health == null || !health.isDead()) return true;
        }
        return false;
    }
}
