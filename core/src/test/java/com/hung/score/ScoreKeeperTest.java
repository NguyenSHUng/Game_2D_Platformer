package com.hung.score;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.ChestTier;
import com.hung.combat.Damage;
import com.hung.combat.system.HealthSystem;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.enemy.MeleeEnemy;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.World;
import com.hung.world.event.ChestOpenedEvent;
import com.hung.world.event.EnemyKilledEvent;
import com.hung.world.event.ExitReachedEvent;
import com.hung.world.event.RoomClearedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every scoring event counts once, with the points of ScoreRules. */
class ScoreKeeperTest {

    private final Room roomA = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 200f, 160f));
    private final Room roomB = new Room("room_b", 1, "STANDARD", "region_1", true, new Rectangle(200f, 0f, 200f, 160f));
    private World world;
    private EventBus events;
    private RunStats stats;
    private final List<String> clearedRooms = new ArrayList<>();
    private final EnemyKilledEvent killed = new EnemyKilledEvent();

    @BeforeEach
    void setUp() {
        world = new World(new Level("test", null, new TileGrid(30, 10, 16f), null, new Vector2(), null, new Array<Rectangle>()));
        events = new EventBus();
        stats = new RunStats();
        new ScoreKeeper(world, stats, ScoreRules.DEFAULT, events).subscribe();
        events.subscribe(RoomClearedEvent.class, event -> clearedRooms.add(event.room.id + " +" + event.points));
    }

    private MeleeEnemy enemy(String spawnId, Room room) {
        MeleeEnemy enemy = new MeleeEnemy(20f);
        enemy.setSpawnId(spawnId);
        enemy.setHomeRoom(room);
        enemy.setHealth(new HealthComponent(30));
        ColliderComponent body = new ColliderComponent();
        body.width = 16f;
        body.height = 23f;
        enemy.setCollider(body);
        world.addEntity(enemy);
        return enemy;
    }

    private void kill(MeleeEnemy enemy) {
        enemy.getHealth().applyDamage(100);
        events.publish(killed.set(enemy));
    }

    @Test
    void theAgreedPoints() {
        assertEquals(new ScoreRules(100, 150, 300, 250, 1000), ScoreRules.DEFAULT);
        assertEquals(150, ScoreRules.DEFAULT.chest(ChestTier.WOOD));
        assertEquals(300, ScoreRules.DEFAULT.chest(ChestTier.GOLD));
    }

    @Test
    void aKillScoresAndClearingTheRoomScoresOnce() {
        MeleeEnemy first = enemy("map#3", roomA);
        MeleeEnemy second = enemy("map#4", roomA);
        enemy("map#9", roomB);

        kill(first);
        assertEquals(100, stats.getScore());
        assertEquals(1, stats.getKills());
        assertTrue(clearedRooms.isEmpty());

        kill(second);
        assertEquals(100 + 100 + 250, stats.getScore());
        assertEquals(2, stats.getKills());
        assertEquals(1, stats.getRoomsCleared());
        assertEquals(List.of("room_a +250"), clearedRooms);
    }

    @Test
    void anEnemyBackOnContinueScoresNothingTheSecondTime() {
        kill(enemy("map#3", roomA));
        MeleeEnemy again = enemy("map#3", roomA);
        kill(again);
        assertEquals(100 + 250, stats.getScore());
        assertEquals(1, stats.getKills());
        assertEquals(List.of("room_a +250"), clearedRooms);
    }

    @Test
    void enemiesWithoutARoomOrASpawnId() {
        kill(enemy("map#5", null));
        assertEquals(100, stats.getScore());
        assertEquals(0, stats.getRoomsCleared());

        kill(enemy(null, roomB));
        assertEquals(100 + 250, stats.getScore());
        assertEquals(1, stats.getKills());
    }

    @Test
    void chestsScoreByTierOnce() {
        ChestOpenedEvent opened = new ChestOpenedEvent();
        events.publish(opened.set("map#28", ChestTier.WOOD));
        events.publish(opened.set("map#30", ChestTier.GOLD));
        events.publish(opened.set("map#28", ChestTier.WOOD));
        assertEquals(150 + 300, stats.getScore());
        assertEquals(2, stats.getChestsOpened());
    }

    @Test
    void completingTheMapScoresOnce() {
        ExitReachedEvent exit = new ExitReachedEvent();
        events.publish(exit.set("map"));
        events.publish(exit.set("map"));
        assertEquals(1000, stats.getScore());
        assertTrue(stats.isMapComplete());
    }

    @Test
    void theRulesDecideThePoints() {
        RunStats custom = new RunStats();
        EventBus bus = new EventBus();
        new ScoreKeeper(world, custom, new ScoreRules(7, 1, 2, 3, 4), bus).subscribe();
        MeleeEnemy enemy = enemy("map#1", roomB);
        enemy.getHealth().applyDamage(100);
        bus.publish(new EnemyKilledEvent().set(enemy));
        bus.publish(new ChestOpenedEvent().set("c", ChestTier.GOLD));
        bus.publish(new ExitReachedEvent().set("map"));
        assertEquals(7 + 3 + 2 + 4, custom.getScore());
    }

    /** The whole path: blows through Damage, deaths reported by HealthSystem, score kept by ScoreKeeper. */
    @Test
    void killingTheRoomThroughTheRealSystems() {
        MeleeEnemy first = enemy("map#3", roomA);
        MeleeEnemy second = enemy("map#4", roomA);
        HealthSystem health = new HealthSystem(world, events);
        Damage.apply(first, 30, 0f);
        for (int i = 0; i < 10; i++) health.update(0.016f);
        Damage.apply(second, 30, 0f);
        for (int i = 0; i < 10; i++) health.update(0.016f);
        assertEquals(100 + 100 + 250, stats.getScore());
        assertEquals(List.of("room_a +250"), clearedRooms);
    }
}
