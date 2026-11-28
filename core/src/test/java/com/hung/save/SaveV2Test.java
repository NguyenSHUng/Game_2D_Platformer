package com.hung.save;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.ChestTier;
import com.hung.combat.LootTable;
import com.hung.combat.PickupType;
import com.hung.combat.Weapon;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.ArsenalComponent;
import com.hung.component.ChestComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.component.WeaponComponent;
import com.hung.core.GameMode;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.entity.enemy.MeleeEnemy;
import com.hung.entity.player.Player;
import com.hung.score.ScoreKeeper;
import com.hung.score.ScoreRules;
import com.hung.ui.minimap.MinimapState;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import com.hung.world.event.ChestOpenedEvent;
import com.hung.world.event.EnemyKilledEvent;
import com.hung.world.system.ChestSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Version 2 saves: reserves, score and statistics, scored ids, opened chests and lying items come back
 * on Continue without scoring or dropping anything twice; a version 1 save keeps the fresh world's
 * values; a re-exported map keeps only the totals, health and weapons.
 */
class SaveV2Test {

    private static final float FLOOR = 32f;

    @TempDir
    File dir;
    private SaveManager saves;
    private final Room roomA = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 320f, 192f));
    private final Room roomB = new Room("room_b", 1, "STANDARD", "region_1", true, new Rectangle(320f, 0f, 320f, 192f));

    @BeforeEach
    void setUp() {
        saves = new SaveManager(new FileHandle(dir));
    }

    // ------------------------------------------------------------------ a small world like the game's

    private World world(Room... rooms) {
        TileGrid grid = new TileGrid(40, 12, 16f);
        for (int x = 0; x < 40; x++) grid.set(x, 1, TileType.SOLID);
        Level level = new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>());
        for (Room room : rooms) level.addRoom(room);
        World world = new World(level);
        world.setPlayer(player());
        for (String id : new String[] {"map#57", "map#68", "map#70"}) {
            world.addEntity(EntityFactory.createChest(id, ChestTier.WOOD, roomA, tile(), 100f + 40f * world.getEntities().size, FLOOR));
        }
        return world;
    }

    private static TextureRegion tile() {
        return new TextureRegion() {
            @Override public int getRegionWidth() { return 16; }
            @Override public int getRegionHeight() { return 16; }
        };
    }

    private static WeaponComponent weapon(String id, String stance, int ammo, int reserve, int maxReserve) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.id = id;
        definition.pivot = new Vector2();
        definition.stance = stance;
        if (stance != null) definition.fire = new FireDefinition();
        WeaponComponent component = new WeaponComponent();
        component.equipped = new Weapon(definition, new TextureRegion());
        component.ammo = ammo;
        component.reserve = reserve;
        component.maxReserve = maxReserve;
        return component;
    }

    /** A fresh player: full health, full magazines, starting reserves (36 / 60). */
    private static Player player() {
        Player player = new Player();
        player.getTransform().position.set(60f, FLOOR);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        player.setHealth(new HealthComponent(100));
        player.setInteract(new InteractComponent());
        ArsenalComponent arsenal = new ArsenalComponent();
        arsenal.slots.add(weapon("combat_knife", null, 0, 0, 0));
        arsenal.slots.add(weapon("service_pistol", "PISTOL", 12, 36, 72));
        arsenal.slots.add(weapon("assault_rifle", "RIFLE", 30, 60, 150));
        player.setArsenal(arsenal);
        return player;
    }

    private static Entity chest(World world, String id) {
        for (Entity entity : world.getEntities()) if (entity.getChest() != null && entity.getChest().id.equals(id)) return entity;
        throw new IllegalArgumentException(id);
    }

    private static List<Entity> items(World world) {
        List<Entity> items = new ArrayList<>();
        for (Entity entity : world.getEntities()) if (entity.getPickup() != null) items.add(entity);
        return items;
    }

    private static int reserve(World world, int slot) {
        return world.getPlayer().getArsenal().slots.get(slot).reserve;
    }

    /** A run in progress: hurt, ammo used, two enemies down (one room cleared), a chest opened, two items lying. */
    private World playedWorld(GameSession session) {
        World world = world(roomA, roomB);
        Player player = (Player) world.getPlayer();
        player.getHealth().applyDamage(36);
        player.getArsenal().slots.get(1).ammo = 7;
        player.getArsenal().slots.get(1).reserve = 48;
        player.getArsenal().slots.get(2).ammo = 21;
        player.getArsenal().slots.get(2).reserve = 90;

        EventBus events = new EventBus();
        new ScoreKeeper(world, session.stats, ScoreRules.DEFAULT, events).subscribe();
        for (String id : new String[] {"map#3", "map#4"}) {
            MeleeEnemy enemy = new MeleeEnemy(20f);
            enemy.setSpawnId(id);
            enemy.setHomeRoom(roomB);
            enemy.setHealth(new HealthComponent(30));
            enemy.getHealth().applyDamage(30);
            world.addEntity(enemy);
            events.publish(new EnemyKilledEvent().set(enemy));
        }
        ChestSystem.markOpened(chest(world, "map#57"));
        events.publish(new ChestOpenedEvent().set("map#57", ChestTier.WOOD));

        world.addEntity(EntityFactory.createPickup(PickupType.RIFLE_AMMO, 150f, FLOOR));
        Entity flying = EntityFactory.createPickup(PickupType.HEALTH_PACK, 100f, FLOOR + 12f);
        flying.getPickup().isFlying = true;
        flying.getPickup().landingX = 120f;
        flying.getPickup().landingY = FLOOR;
        world.addEntity(flying);
        return world;
    }

    private static String fixture(String path) throws IOException {
        try (InputStream in = SaveV2Test.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void aRunComesBackThroughTheFile() {
        GameSession session = new GameSession(GameMode.MEDIUM, null);
        World world = playedWorld(session);
        assertEquals(100 + 100 + 250 + 150, session.stats.getScore());
        assertTrue(saves.write(GameMode.MEDIUM, SaveSnapshot.capture(world, session, new MinimapState(2), -1, SaveData.REASON_QUIT, 1L)));

        SaveManager.Save save = saves.read(GameMode.MEDIUM);
        assertEquals(SaveManager.Status.OK, save.status);
        assertEquals(2, save.data.version);
        GameSession continued = new GameSession(GameMode.MEDIUM, save.data);
        World fresh = world(roomA, roomB);
        assertTrue(SaveSnapshot.apply(save.data, fresh, new MinimapState(2), continued.stats));

        assertEquals(64, fresh.getPlayer().getHealth().getHealth());
        assertEquals(7, fresh.getPlayer().getArsenal().slots.get(1).ammo);
        assertEquals(48, reserve(fresh, 1));
        assertEquals(21, fresh.getPlayer().getArsenal().slots.get(2).ammo);
        assertEquals(90, reserve(fresh, 2));

        assertEquals(600, continued.stats.getScore());
        assertEquals(2, continued.stats.getKills());
        assertEquals(1, continued.stats.getChestsOpened());
        assertEquals(1, continued.stats.getRoomsCleared());
        assertEquals(Set.of("map#3", "map#4"), continued.stats.getDefeatedEnemies());
        assertEquals(Set.of("map#57"), continued.stats.getOpenedChests());
        assertEquals(Set.of("room_b"), continued.stats.getClearedRooms());

        assertEquals(ChestComponent.State.OPENED, chest(fresh, "map#57").getChest().state);
        assertNotEquals(Color.WHITE, chest(fresh, "map#57").getSprite().tint);
        assertEquals(ChestComponent.State.CLOSED, chest(fresh, "map#68").getChest().state);

        List<Entity> items = items(fresh);
        assertEquals(2, items.size());
        for (Entity item : items) {
            assertFalse(item.getPickup().isFlying);
            assertEquals(FLOOR, item.getTransform().position.y, 1e-4);
        }
        assertTrue(items.stream().anyMatch(i -> i.getPickup().type == PickupType.RIFLE_AMMO && i.getTransform().position.x == 150f));
        assertTrue(items.stream().anyMatch(i -> i.getPickup().type == PickupType.HEALTH_PACK && i.getTransform().position.x == 120f));
    }

    @Test
    void continuingScoresAndDropsNothingTwice() {
        GameSession session = new GameSession(GameMode.MEDIUM, null);
        SaveData data = SaveSnapshot.capture(playedWorld(session), session, new MinimapState(2), -1, SaveData.REASON_QUIT, 1L);
        GameSession continued = new GameSession(GameMode.MEDIUM, data);
        World fresh = world(roomA, roomB);
        SaveSnapshot.apply(data, fresh, new MinimapState(2), continued.stats);
        EventBus events = new EventBus();
        new ScoreKeeper(fresh, continued.stats, ScoreRules.DEFAULT, events).subscribe();

        // The two enemies come back and are defeated again; the room is emptied again
        for (String id : new String[] {"map#3", "map#4"}) {
            MeleeEnemy enemy = new MeleeEnemy(20f);
            enemy.setSpawnId(id);
            enemy.setHomeRoom(roomB);
            enemy.setHealth(new HealthComponent(30));
            enemy.getHealth().applyDamage(30);
            fresh.addEntity(enemy);
            events.publish(new EnemyKilledEvent().set(enemy));
        }
        // The opened chest can't be opened again
        Entity opened = chest(fresh, "map#57");
        fresh.getPlayer().getTransform().position.x = opened.getTransform().position.x - 12f;
        fresh.getPlayer().getInteract().isRequested = true;
        ChestSystem chests = new ChestSystem(fresh, LootTable.DEFAULT, events);
        chests.update(0.016f);
        chests.update(1f);

        assertEquals(600, continued.stats.getScore());
        assertEquals(2, continued.stats.getKills());
        assertEquals(2, items(fresh).size());
        assertEquals(ChestComponent.State.OPENED, opened.getChest().state);
    }

    @Test
    void aVersion1SaveKeepsTheFreshWorldsValues() throws IOException {
        new FileHandle(dir).child("hard.json").writeString(fixture("/saves/slot_v1_quit.json"), false);
        SaveManager.Save save = saves.read(GameMode.HARD);
        assertEquals(SaveManager.Status.OK, save.status);
        GameSession continued = new GameSession(GameMode.HARD, save.data);
        World fresh = world(roomA, roomB);
        fresh.getPlayer().getHealth().applyDamage(50);

        SaveSnapshot.apply(save.data, fresh, new MinimapState(2), continued.stats);
        assertEquals(100, fresh.getPlayer().getHealth().getHealth());
        assertEquals(12, fresh.getPlayer().getArsenal().slots.get(1).ammo);
        assertEquals(29, fresh.getPlayer().getArsenal().slots.get(2).ammo);
        assertEquals(36, reserve(fresh, 1));
        assertEquals(60, reserve(fresh, 2));
        assertEquals(0, continued.stats.getScore());
        assertTrue(continued.stats.getDefeatedEnemies().isEmpty());
        assertEquals(ChestComponent.State.CLOSED, chest(fresh, "map#57").getChest().state);
        assertTrue(items(fresh).isEmpty());
    }

    @Test
    void aReExportedMapKeepsTheTotalsHealthAndWeaponsOnly() {
        GameSession session = new GameSession(GameMode.MEDIUM, null);
        SaveData data = SaveSnapshot.capture(playedWorld(session), session, new MinimapState(2), -1, SaveData.REASON_QUIT, 1L);
        GameSession continued = new GameSession(GameMode.MEDIUM, data);
        World other = world(roomA);   // one room fewer: another signature

        assertFalse(SaveSnapshot.apply(data, other, new MinimapState(1), continued.stats));
        assertEquals(64, other.getPlayer().getHealth().getHealth());
        assertEquals(7, other.getPlayer().getArsenal().slots.get(1).ammo);
        assertEquals(48, reserve(other, 1));
        assertEquals(600, continued.stats.getScore());
        assertEquals(2, continued.stats.getKills());
        assertEquals(1, continued.stats.getChestsOpened());
        assertEquals(1, continued.stats.getRoomsCleared());
        assertTrue(continued.stats.getDefeatedEnemies().isEmpty());
        assertTrue(continued.stats.getOpenedChests().isEmpty());
        assertTrue(continued.stats.getClearedRooms().isEmpty());
        assertEquals(ChestComponent.State.CLOSED, chest(other, "map#57").getChest().state);
        assertTrue(items(other).isEmpty());
    }

    @Test
    void reservesStayWithinTheirMaxAndUnknownItemsAreSkipped() {
        SaveData data = new SaveData();
        data.mapSignature = SaveSnapshot.signature(world(roomA, roomB).getLevel());
        data.weaponIds = new String[] {"combat_knife", "service_pistol", "assault_rifle"};
        data.ammo = new int[] {0, 12, 30};
        data.reserve = new int[] {0, 999, -5};
        SaveData.PickupData unknown = new SaveData.PickupData();
        unknown.type = "SHIELD";
        SaveData.PickupData pistol = new SaveData.PickupData();
        pistol.type = "PISTOL_AMMO";
        pistol.x = 200f;
        pistol.y = FLOOR;
        data.pickups = new SaveData.PickupData[] {unknown, null, pistol};
        data.openedChests = new String[] {null, "map#68"};
        World fresh = world(roomA, roomB);

        SaveSnapshot.apply(data, fresh, new MinimapState(2), new GameSession(GameMode.MEDIUM, data).stats);
        assertEquals(72, reserve(fresh, 1));
        assertEquals(0, reserve(fresh, 2));
        assertEquals(1, items(fresh).size());
        assertEquals(PickupType.PISTOL_AMMO, items(fresh).get(0).getPickup().type);
        assertEquals(ChestComponent.State.OPENED, chest(fresh, "map#68").getChest().state);
    }

    @Test
    void theVersion2FixtureReads() throws IOException {
        new FileHandle(dir).child("medium.json").writeString(fixture("/saves/medium_v2_quit.json"), false);
        SaveManager.Save save = saves.read(GameMode.MEDIUM);
        assertEquals(SaveManager.Status.OK, save.status);
        assertEquals(1700, save.data.score);
        assertEquals(48, save.data.reserve[1]);
        assertEquals(2, save.data.openedChests.length);
        assertEquals("RIFLE_AMMO", save.data.pickups[0].type);
        assertEquals(1180f, save.data.pickups[0].x, 1e-4);
    }
}
