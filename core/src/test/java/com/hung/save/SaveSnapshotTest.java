package com.hung.save;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.Weapon;
import com.hung.combat.WeaponDefinition;
import com.hung.component.ArsenalComponent;
import com.hung.component.HealthComponent;
import com.hung.component.WeaponComponent;
import com.hung.core.GameMode;
import com.hung.entity.Entity;
import com.hung.ui.MinimapState;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.RoomTransition;
import com.hung.world.TileGrid;
import com.hung.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSnapshotTest {

    private World world;
    private Entity player;
    private MinimapState exploration;
    private GameSession session;

    private static World world(int rooms, int checkpoints) {
        Array<Rectangle> marks = new Array<>();
        for (int i = 0; i < checkpoints; i++) marks.add(new Rectangle(i * 100f, 0f, 16f, 32f));
        Level level = new Level("test", null, new TileGrid(40, 20, 16f), null, new Vector2(10f, 20f), null, marks);
        for (int i = 0; i < rooms; i++) level.addRoom(new Room("room_" + (i + 1), i, "NORMAL", "r", true, new Rectangle()));
        return new World(level);
    }

    private static WeaponComponent weapon(String id, int ammo) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.id = id;
        definition.pivot = new Vector2();
        WeaponComponent component = new WeaponComponent();
        component.equipped = new Weapon(definition, new TextureRegion());
        component.ammo = ammo;
        return component;
    }

    private static Entity player(World world) {
        Entity player = new Entity();
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(28);
        player.setHealth(health);
        ArsenalComponent arsenal = new ArsenalComponent();
        arsenal.slots.add(weapon("combat_knife", 0));
        arsenal.slots.add(weapon("service_pistol", 7));
        arsenal.current = 1;
        player.setArsenal(arsenal);
        player.getTransform().position.set(300f, 64f);
        world.setPlayer(player);
        return player;
    }

    @BeforeEach
    void setUp() {
        world = world(4, 3);
        player = player(world);
        world.setRespawnPoint(100f, 32f);
        exploration = new MinimapState(4);
        exploration.restore(new int[] {0, 1}, new int[] {0, 1, 2});
        session = new GameSession(0, GameMode.HARD, null);
        session.playTime = 42f;
    }

    @Test
    void quitSaveRecordsWhereThePlayerStands() {
        SaveData data = SaveSnapshot.capture(world, session, exploration, 1, SaveData.REASON_QUIT, 99L);
        assertEquals(300f, data.x);
        assertEquals(64f, data.y);
        assertEquals(100f, data.respawnX);
        assertEquals("HARD", data.mode);
        assertEquals(GameMode.HARD.mapPath, data.mapPath);
        assertEquals("40x20/4r/3c", data.mapSignature);
        assertEquals(99L, data.savedAt);
        assertEquals(42f, data.playTime);
        assertEquals(1, data.checkpointIndex);
        assertEquals(3, data.checkpointCount);
        assertEquals(72, data.health);
        assertEquals(100, data.maxHealth);
        assertEquals(1, data.weaponSlot);
        assertArrayEquals(new String[] {"combat_knife", "service_pistol"}, data.weaponIds);
        assertArrayEquals(new int[] {0, 7}, data.ammo);
        assertArrayEquals(new int[] {0, 1}, data.visitedRooms);
        assertArrayEquals(new int[] {0, 1, 2}, data.knownRooms);
        assertEquals(4, data.roomCount);
    }

    @Test
    void checkpointSaveUsesTheRespawnPoint() {
        SaveData data = SaveSnapshot.capture(world, session, exploration, 0, SaveData.REASON_CHECKPOINT, 1L);
        assertEquals(100f, data.x);
        assertEquals(32f, data.y);
    }

    @Test
    void unsafeSpotsFallBackToTheRespawnPoint() {
        world.getRoomTransition().phase = RoomTransition.Phase.FADING_OUT;
        assertEquals(100f, SaveSnapshot.capture(world, session, exploration, 0, SaveData.REASON_QUIT, 1L).x);

        world.getRoomTransition().phase = RoomTransition.Phase.NONE;
        player.getHealth().applyDamage(1000);
        SaveData dead = SaveSnapshot.capture(world, session, exploration, 0, SaveData.REASON_QUIT, 1L);
        assertEquals(100f, dead.x);
        assertEquals(100, dead.health);
    }

    @Test
    void noPlayerNoSave() {
        World empty = world(1, 1);
        assertNull(SaveSnapshot.capture(empty, session, new MinimapState(1), -1, SaveData.REASON_QUIT, 1L));
    }

    @Test
    void applyOnTheSameMapRestoresEverything() {
        SaveData data = SaveSnapshot.capture(world, session, exploration, 2, SaveData.REASON_QUIT, 1L);

        World fresh = world(4, 3);
        Entity freshPlayer = player(fresh);
        freshPlayer.getHealth().restoreFull();
        freshPlayer.getArsenal().current = 0;
        freshPlayer.getArsenal().slots.get(1).ammo = 12;
        MinimapState freshExploration = new MinimapState(4);

        assertTrue(SaveSnapshot.apply(data, fresh, freshExploration));
        assertEquals(new Vector2(300f, 64f), freshPlayer.getTransform().position);
        assertEquals(new Vector2(100f, 32f), fresh.getRespawnPoint());
        assertEquals(72, freshPlayer.getHealth().getHealth());
        assertEquals(7, freshPlayer.getArsenal().slots.get(1).ammo);
        assertEquals(1, freshPlayer.getArsenal().requestedSlot);
        assertTrue(freshExploration.isVisited(1));
        assertTrue(freshExploration.isKnown(2));
    }

    @Test
    void applyOnAReExportedMapKeepsOnlyHealthAndWeapons() {
        SaveData data = SaveSnapshot.capture(world, session, exploration, 2, SaveData.REASON_QUIT, 1L);

        World other = world(5, 3);
        Entity otherPlayer = player(other);
        otherPlayer.getHealth().restoreFull();
        otherPlayer.getTransform().position.set(10f, 20f);
        MinimapState otherExploration = new MinimapState(5);

        assertFalse(SaveSnapshot.apply(data, other, otherExploration));
        assertEquals(new Vector2(10f, 20f), otherPlayer.getTransform().position);
        assertEquals(72, otherPlayer.getHealth().getHealth());
        assertFalse(otherExploration.isVisited(0));
    }
}
