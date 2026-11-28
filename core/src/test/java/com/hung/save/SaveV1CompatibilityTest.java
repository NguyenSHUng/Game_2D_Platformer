package com.hung.save;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.Weapon;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.ArsenalComponent;
import com.hung.component.HealthComponent;
import com.hung.component.WeaponComponent;
import com.hung.core.GameMode;
import com.hung.entity.Entity;
import com.hung.ui.minimap.MinimapState;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a continued run gets back, through the save file itself: a save written by an earlier
 * release (v1) restores health, the weapon in hand and every magazine; a save written now and read
 * back restores the same. The baseline for the coming save version.
 */
class SaveV1CompatibilityTest {

    @TempDir
    File dir;
    private SaveManager saves;

    @BeforeEach
    void setUp() {
        saves = new SaveManager(new FileHandle(dir));
    }

    private static String fixture() throws IOException {
        try (InputStream in = SaveV1CompatibilityTest.class.getResourceAsStream("/saves/slot_v1_quit.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static World world(int rooms) {
        Level level = new Level("test", null, new TileGrid(40, 20, 16f), null, new Vector2(10f, 20f), null, new Array<Rectangle>());
        for (int i = 0; i < rooms; i++) level.addRoom(new Room("room_" + (i + 1), i, "STANDARD", "region_1", true, new Rectangle()));
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

    /** The player's three weapons with the given magazines, the knife in hand. */
    private static Entity player(World world, int health, int pistol, int rifle) {
        Entity player = new Entity();
        HealthComponent component = new HealthComponent(100);
        component.setHealthClamped(health);
        player.setHealth(component);
        ArsenalComponent arsenal = new ArsenalComponent();
        arsenal.slots.add(weapon("combat_knife", 0));
        arsenal.slots.add(weapon("service_pistol", pistol));
        arsenal.slots.add(weapon("assault_rifle", rifle));
        player.setArsenal(arsenal);
        player.getTransform().position.set(200f, 64f);
        world.setPlayer(player);
        return player;
    }

    @Test
    void anEarlierReleasesSaveRestoresHealthWeaponAndMagazines() throws IOException {
        new FileHandle(dir).child("hard.json").writeString(fixture(), false);
        SaveManager.Save save = saves.read(GameMode.HARD);
        assertEquals(SaveManager.Status.OK, save.status);

        World world = world(3);
        Entity player = player(world, 40, 1, 1);
        // The fixture comes from another map export: only health and weapons apply
        assertFalse(SaveSnapshot.apply(save.data, world, new MinimapState(3)));
        assertEquals(100, player.getHealth().getHealth());
        assertEquals(12, player.getArsenal().slots.get(1).ammo);
        assertEquals(29, player.getArsenal().slots.get(2).ammo);
        assertEquals(2, player.getArsenal().requestedSlot);
    }

    @Test
    void aSaveWrittenNowComesBackThroughTheFile() {
        World world = world(4);
        Entity player = player(world, 72, 7, 21);
        player.getArsenal().current = 2;
        MinimapState exploration = new MinimapState(4);
        exploration.restore(new int[] {0, 1}, new int[] {0, 1, 2});
        GameSession session = new GameSession(GameMode.MEDIUM, null);
        session.playTime = 42f;
        SaveData written = SaveSnapshot.capture(world, session, exploration, -1, SaveData.REASON_QUIT, 5L);
        assertTrue(saves.write(GameMode.MEDIUM, written));

        SaveManager.Save save = saves.read(GameMode.MEDIUM);
        assertEquals(SaveManager.Status.OK, save.status);
        assertEquals(SaveData.CURRENT_VERSION, save.data.version);
        World fresh = world(4);
        Entity freshPlayer = player(fresh, 100, 12, 30);
        MinimapState freshExploration = new MinimapState(4);
        assertTrue(SaveSnapshot.apply(save.data, fresh, freshExploration));
        assertEquals(72, freshPlayer.getHealth().getHealth());
        assertEquals(0, freshPlayer.getArsenal().slots.get(0).ammo);
        assertEquals(7, freshPlayer.getArsenal().slots.get(1).ammo);
        assertEquals(21, freshPlayer.getArsenal().slots.get(2).ammo);
        assertEquals(2, freshPlayer.getArsenal().requestedSlot);
        assertEquals(new Vector2(200f, 64f), freshPlayer.getTransform().position);
        assertTrue(freshExploration.isKnown(2));
        assertEquals(42f, save.data.playTime, 1e-4);
    }
}
