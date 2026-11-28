package com.hung.combat;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.hung.combat.system.PickupSystem;
import com.hung.component.ArsenalComponent;
import com.hung.component.ChestComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.component.WeaponComponent;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.core.GameMode;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.entity.player.Player;
import com.hung.score.RunStats;
import com.hung.score.ScoreKeeper;
import com.hung.score.ScoreRules;
import com.hung.testutil.TmxTestMap;
import com.hung.world.Room;
import com.hung.world.World;
import com.hung.world.event.PickupCollectedEvent;
import com.hung.world.system.ChestSystem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scripted playtest of the loot on the three real maps: the player walks up to every chest, presses
 * the interact key, waits for the item to land, steps onto it and picks it up, with the real chest,
 * pickup and score systems. Checked: every chest opens once, every item lands safely in its room and
 * can be picked. The loot each map hands out and its best possible score go to build/playtest/loot.txt.
 */
class LootPlaytestTest {

    private static final Path REPORT = Path.of("build", "playtest", "loot.txt");
    private static final float STEP = 1f / 60f;

    @Test
    void everyChestOpensDropsAndIsPickedOnTheRealMaps() throws Exception {
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT,
            "%-7s %6s %5s %5s %7s %7s %7s %5s %8s %8s %8s %9s%n", "mode", "chests", "wood", "gold", "health",
            "pistol", "rifle", "HP", "rounds P", "rounds R", "enemies", "max score"));
        for (GameMode mode : GameMode.values()) report.append(play(mode));
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report.toString(), StandardCharsets.UTF_8);
    }

    private String play(GameMode mode) throws Exception {
        TmxTestMap map = new TmxTestMap(mode.mapPath);
        World world = new World(map.level);
        Player player = player();
        world.setPlayer(player);
        EventBus events = new EventBus();
        RunStats stats = new RunStats();
        new ScoreKeeper(world, stats, ScoreRules.DEFAULT, events).subscribe();
        Map<PickupType, Integer> given = new EnumMap<>(PickupType.class);
        events.subscribe(PickupCollectedEvent.class, event -> given.merge(event.type, event.amount, Integer::sum));
        ChestSystem chests = new ChestSystem(world, LootTable.DEFAULT, events);
        PickupSystem pickups = new PickupSystem(world, events);

        float tile = map.grid.getTileSize();
        int wood = 0;
        int gold = 0;
        Map<PickupType, Integer> items = new EnumMap<>(PickupType.class);
        for (TmxTestMap.MapObject object : map.objectsOf("CHEST")) {
            Rectangle rect = object.rect();
            float x = (int) ((rect.x + rect.width / 2f) / tile) * tile + tile / 2f;
            float y = (int) ((rect.y + 1f) / tile) * tile;
            ChestTier tier = ChestTier.fromName(object.properties().get("tier"));
            if (tier == ChestTier.GOLD) gold++; else wood++;
            Room room = map.level.findRoomAt(x, y + 1f);
            Entity chest = EntityFactory.createChest(mode.mapPath + "#" + object.id(), tier, room, tile(), x, y);
            world.addEntity(chest);

            // Walk up, press E, wait for the item to come out and land
            player.getTransform().position.set(x - 14f, y);
            player.getInteract().isRequested = true;
            Entity item = null;
            for (float t = 0f; t < 2f; t += STEP) {
                chests.update(STEP);
                pickups.update(STEP);
                if (item == null) item = newestItem(world);
            }
            String name = mode + " " + object.name();
            assertEquals(ChestComponent.State.OPENED, chest.getChest().state, name);
            assertNotNull(item, name + " dropped nothing");
            assertFalse(item.getPickup().isFlying, name + " item still flying");
            Vector2 at = item.getTransform().position;
            assertTrue(LootDrop.isSafeSpot(map.grid, room, at.x, at.y), name + " item on an unsafe spot");
            items.merge(item.getPickup().type, 1, Integer::sum);

            // Hurt and out of reserve rounds, so every item is of use: step onto it
            needEverything(player);
            player.getTransform().position.set(at);
            pickups.update(STEP);
            assertFalse(world.getEntities().contains(item, true), name + " item not picked up");

            // Opening it again gives nothing
            player.getTransform().position.set(x - 14f, y);
            player.getInteract().isRequested = true;
            for (float t = 0f; t < 1f; t += STEP) chests.update(STEP);
            assertTrue(newestItem(world) == null, name + " dropped twice");
        }
        assertEquals(wood + gold, stats.getChestsOpened());
        assertEquals(wood * ScoreRules.DEFAULT.woodChest() + gold * ScoreRules.DEFAULT.goldChest(), stats.getScore());

        int enemies = map.objectsOf("ENEMY").size();
        Set<String> roomsWithEnemies = new HashSet<>();
        for (TmxTestMap.MapObject enemy : map.objectsOf("ENEMY")) roomsWithEnemies.add(enemy.properties().get("room"));
        int maxScore = stats.getScore() + enemies * ScoreRules.DEFAULT.enemyKill()
            + roomsWithEnemies.size() * ScoreRules.DEFAULT.roomClear() + ScoreRules.DEFAULT.mapComplete();
        return String.format(Locale.ROOT, "%-7s %6d %5d %5d %7d %7d %7d %5d %8d %8d %8d %9d%n", mode, wood + gold, wood, gold,
            items.getOrDefault(PickupType.HEALTH_PACK, 0), items.getOrDefault(PickupType.PISTOL_AMMO, 0),
            items.getOrDefault(PickupType.RIFLE_AMMO, 0), given.getOrDefault(PickupType.HEALTH_PACK, 0),
            given.getOrDefault(PickupType.PISTOL_AMMO, 0), given.getOrDefault(PickupType.RIFLE_AMMO, 0),
            enemies, maxScore);
    }

    private static Entity newestItem(World world) {
        for (int i = world.getEntities().size - 1; i >= 0; i--) {
            if (world.getEntities().get(i).getPickup() != null) return world.getEntities().get(i);
        }
        return null;
    }

    private static void needEverything(Player player) {
        player.getHealth().setHealthClamped(40);
        for (WeaponComponent slot : player.getArsenal().slots) slot.reserve = 0;
    }

    private static Player player() {
        Player player = new Player();
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        player.setHealth(new HealthComponent(100));
        player.setInteract(new InteractComponent());
        ArsenalComponent arsenal = new ArsenalComponent();
        arsenal.slots.add(gun("PISTOL", AmmoType.PISTOL.maxReserve));
        arsenal.slots.add(gun("RIFLE", AmmoType.RIFLE.maxReserve));
        player.setArsenal(arsenal);
        return player;
    }

    private static WeaponComponent gun(String stance, int maxReserve) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.id = stance.toLowerCase();
        definition.pivot = new Vector2();
        definition.stance = stance;
        definition.fire = new FireDefinition();
        WeaponComponent slot = new WeaponComponent();
        slot.equipped = new Weapon(definition, new TextureRegion());
        slot.maxReserve = maxReserve;
        return slot;
    }

    private static TextureRegion tile() {
        return new TextureRegion() {
            @Override public int getRegionWidth() { return 16; }
            @Override public int getRegionHeight() { return 16; }
        };
    }
}
