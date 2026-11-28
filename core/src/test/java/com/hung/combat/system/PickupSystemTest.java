package com.hung.combat.system;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.PickupType;
import com.hung.combat.Pickups;
import com.hung.combat.Weapon;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.ArsenalComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.WeaponComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.entity.enemy.MeleeEnemy;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.World;
import com.hung.world.event.PickupCollectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Touching an item picks it up when it is of use, and only then; each pick is reported once. */
class PickupSystemTest {

    private static final float FLOOR = 32f;

    private World world;
    private Player player;
    private EventBus events;
    private PickupSystem pickups;
    private final List<String> reported = new ArrayList<>();

    private static WeaponComponent gun(String stance, int reserve, int maxReserve) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.id = stance.toLowerCase();
        definition.pivot = new Vector2();
        definition.stance = stance;
        definition.fire = new FireDefinition();
        WeaponComponent slot = new WeaponComponent();
        slot.equipped = new Weapon(definition, new TextureRegion());
        slot.ammo = 5;
        slot.reserve = reserve;
        slot.maxReserve = maxReserve;
        return slot;
    }

    @BeforeEach
    void setUp() {
        world = new World(new Level("test", null, new TileGrid(40, 12, 16f), null, new Vector2(), null, new Array<Rectangle>()));
        player = new Player();
        player.getTransform().position.set(100f, FLOOR);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(50);
        player.setHealth(health);
        ArsenalComponent arsenal = new ArsenalComponent();
        arsenal.slots.add(gun("PISTOL", 36, 72));
        arsenal.slots.add(gun("RIFLE", 60, 150));
        player.setArsenal(arsenal);
        world.setPlayer(player);
        events = new EventBus();
        events.subscribe(PickupCollectedEvent.class, event -> reported.add(event.type + " " + event.amount));
        pickups = new PickupSystem(world, events);
    }

    private Entity drop(PickupType type, float x) {
        Entity item = EntityFactory.createPickup(type, x, FLOOR);
        world.addEntity(item);
        return item;
    }

    private boolean isLying(Entity item) {
        return world.getEntities().contains(item, true);
    }

    @Test
    void aPickupIsAStillItemWithABody() {
        Entity item = EntityFactory.createPickup(PickupType.RIFLE_AMMO, 50f, FLOOR);
        assertEquals(PickupType.RIFLE_AMMO, item.getPickup().type);
        assertEquals(new Vector2(50f, FLOOR), item.getTransform().position);
        assertEquals(EntityFactory.PICKUP_SIZE, item.getCollider().width, 1e-6);
        assertNull(item.getMovement());
        assertNull(item.getSprite());
    }

    @Test
    void touchingAHealthPackHealsAndRemovesIt() {
        Entity item = drop(PickupType.HEALTH_PACK, 104f);
        pickups.update(0.016f);
        assertEquals(75, player.getHealth().getHealth());
        assertFalse(isLying(item));
        assertEquals(List.of("HEALTH_PACK 25"), reported);
    }

    @Test
    void ammoGoesToTheMatchingReserve() {
        drop(PickupType.PISTOL_AMMO, 100f);
        drop(PickupType.RIFLE_AMMO, 96f);
        pickups.update(0.016f);
        assertEquals(60, player.getArsenal().slots.get(0).reserve);
        assertEquals(90, player.getArsenal().slots.get(1).reserve);
        assertEquals(5, player.getArsenal().slots.get(0).ammo);
        assertEquals(2, reported.size());
        assertTrue(reported.contains("PISTOL_AMMO 24"));
        assertTrue(reported.contains("RIFLE_AMMO 30"));
    }

    @Test
    void anItemOfNoUseStaysWhereItLies() {
        player.getHealth().restoreFull();
        player.getArsenal().slots.get(1).reserve = 150;
        Entity health = drop(PickupType.HEALTH_PACK, 100f);
        Entity rifle = drop(PickupType.RIFLE_AMMO, 100f);
        pickups.update(0.016f);
        pickups.update(0.016f);
        assertTrue(isLying(health));
        assertTrue(isLying(rifle));
        assertTrue(reported.isEmpty());

        player.getHealth().applyDamage(10);
        pickups.update(0.016f);
        assertFalse(isLying(health));
        assertTrue(isLying(rifle));
        assertEquals(List.of("HEALTH_PACK 10"), reported);
    }

    @Test
    void aDeadPlayerPicksNothingUp() {
        player.getHealth().applyDamage(100);
        Entity health = drop(PickupType.HEALTH_PACK, 100f);
        Entity ammo = drop(PickupType.PISTOL_AMMO, 100f);
        pickups.update(0.016f);
        assertTrue(isLying(health));
        assertTrue(isLying(ammo));
        assertTrue(reported.isEmpty());
    }

    @Test
    void onlyTouchingCounts() {
        Entity beside = drop(PickupType.HEALTH_PACK, 100f + 3.5f + 5f + 0.5f);
        Entity above = EntityFactory.createPickup(PickupType.HEALTH_PACK, 100f, FLOOR + 24f);
        world.addEntity(above);
        pickups.update(0.016f);
        assertTrue(isLying(beside));
        assertTrue(isLying(above));

        player.getTransform().position.x = 106f;
        pickups.update(0.016f);
        assertFalse(isLying(beside));
    }

    @Test
    void enemiesDoNotPickItemsUp() {
        player.getTransform().position.x = 300f;
        MeleeEnemy enemy = new MeleeEnemy(20f);
        enemy.getTransform().position.set(100f, FLOOR);
        ColliderComponent body = new ColliderComponent();
        body.width = 16f;
        body.height = 23f;
        enemy.setCollider(body);
        HealthComponent health = new HealthComponent(30);
        health.applyDamage(20);
        enemy.setHealth(health);
        world.addEntity(enemy);
        Entity item = drop(PickupType.HEALTH_PACK, 100f);
        pickups.update(0.016f);
        assertTrue(isLying(item));
        assertEquals(10, enemy.getHealth().getHealth());
    }

    @Test
    void aWorldWithoutPlayerIsLeftAlone() {
        World empty = new World(new Level("test", null, new TileGrid(10, 10, 16f), null, new Vector2(), null, new Array<Rectangle>()));
        Entity item = EntityFactory.createPickup(PickupType.HEALTH_PACK, 0f, 0f);
        empty.addEntity(item);
        new PickupSystem(empty, events).update(0.016f);
        assertTrue(empty.getEntities().contains(item, true));
    }

    @Test
    void everyTypeHasItsRule() {
        assertEquals(25, Pickups.apply(player, PickupType.HEALTH_PACK));
        assertEquals(24, Pickups.apply(player, PickupType.PISTOL_AMMO));
        assertEquals(30, Pickups.apply(player, PickupType.RIFLE_AMMO));
        assertNull(PickupType.HEALTH_PACK.ammo);
        assertNotNull(PickupType.RIFLE_AMMO.ammo);
    }
}
