package com.hung.world.system;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.ChestTier;
import com.hung.combat.LootTable;
import com.hung.combat.PickupType;
import com.hung.component.ChestComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chest opens once with the interact key, plays its opening, then throws out exactly one item. */
class ChestSystemTest {

    private static final float TILE = 16f;
    private static final float FLOOR = 2 * TILE;
    private static final float CHEST_X = 12 * TILE + 8f;

    private World world;
    private Player player;
    private ChestSystem chests;
    private final Room room = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 640f, 192f));

    @BeforeEach
    void setUp() {
        TileGrid grid = new TileGrid(40, 12, TILE);
        for (int x = 0; x < 40; x++) grid.set(x, 1, TileType.SOLID);
        world = new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
        player = new Player();
        player.getTransform().position.set(CHEST_X - 15f, FLOOR);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        player.setHealth(new HealthComponent(100));
        player.setInteract(new InteractComponent());
        world.setPlayer(player);
        chests = new ChestSystem(world, LootTable.DEFAULT);
    }

    private Entity chest(String id, float x) {
        Entity chest = EntityFactory.createChest(id, ChestTier.WOOD, room, tile(), x, FLOOR);
        world.addEntity(chest);
        return chest;
    }

    /** A 16 × 16 map tile without a texture (headless). */
    private static TextureRegion tile() {
        return new TextureRegion() {
            @Override public int getRegionWidth() { return 16; }
            @Override public int getRegionHeight() { return 16; }
        };
    }

    private void pressE() {
        player.getInteract().isRequested = true;
    }

    private List<Entity> items() {
        List<Entity> items = new ArrayList<>();
        for (Entity entity : world.getEntities()) if (entity.getPickup() != null) items.add(entity);
        return items;
    }

    @Test
    void aChestIsTheMapTileWithABody() {
        Entity chest = chest("map#28", CHEST_X);
        assertEquals(ChestComponent.State.CLOSED, chest.getChest().state);
        assertSame(room, chest.getChest().room);
        assertEquals(16f, chest.getCollider().width, 1e-6);
        // The origin pixel's centre sits on the feet point: the 16 px tile covers x ± 8, y … y + 16
        assertEquals(7.5f, chest.getSprite().originX, 1e-6);
        assertEquals(15.5f, chest.getSprite().originY, 1e-6);
    }

    @Test
    void theInteractKeyOpensItThenItDropsOneItem() {
        Entity chest = chest("map#28", CHEST_X);
        pressE();
        chests.update(0.016f);
        assertEquals(ChestComponent.State.OPENING, chest.getChest().state);
        assertFalse(player.getInteract().isRequested);
        assertTrue(items().isEmpty());

        chests.update(ChestSystem.OPEN_SECONDS - 0.05f);
        assertTrue(items().isEmpty());
        chests.update(0.06f);
        assertEquals(ChestComponent.State.OPENED, chest.getChest().state);
        assertEquals(1, items().size());
        Entity item = items().get(0);
        assertSame(LootTable.DEFAULT.pick("map#28", ChestTier.WOOD), item.getPickup().type);
        assertTrue(item.getPickup().isFlying);
        assertNotEquals(Color.WHITE, chest.getSprite().tint);
    }

    @Test
    void itOpensOnlyOnce() {
        Entity chest = chest("map#28", CHEST_X);
        pressE();
        chests.update(0.016f);
        chests.update(1f);
        for (int i = 0; i < 5; i++) {
            pressE();
            chests.update(1f);
        }
        assertEquals(ChestComponent.State.OPENED, chest.getChest().state);
        assertEquals(1, items().size());
    }

    @Test
    void tooFarAwayNothingHappensAndThePressIsNotKept() {
        Entity chest = chest("map#28", CHEST_X);
        player.getTransform().position.x = CHEST_X - 8f - ChestSystem.REACH - 3.5f - 1f;
        pressE();
        chests.update(0.016f);
        assertEquals(ChestComponent.State.CLOSED, chest.getChest().state);
        assertFalse(player.getInteract().isRequested);

        player.getTransform().position.x = CHEST_X - 15f;
        chests.update(0.016f);
        assertEquals(ChestComponent.State.CLOSED, chest.getChest().state);
    }

    @Test
    void withinReachOnEitherSide() {
        Entity chest = chest("map#28", CHEST_X);
        player.getTransform().position.x = CHEST_X + 8f + ChestSystem.REACH + 3.5f - 1f;
        pressE();
        chests.update(0.016f);
        assertEquals(ChestComponent.State.OPENING, chest.getChest().state);
    }

    @Test
    void aChestOnAnotherFloorIsOutOfReach() {
        Entity chest = chest("map#28", CHEST_X);
        player.getTransform().position.set(CHEST_X, FLOOR + 40f);
        pressE();
        chests.update(0.016f);
        assertEquals(ChestComponent.State.CLOSED, chest.getChest().state);
    }

    @Test
    void theDeadOpenNothing() {
        Entity chest = chest("map#28", CHEST_X);
        player.getHealth().applyDamage(100);
        pressE();
        chests.update(0.016f);
        assertEquals(ChestComponent.State.CLOSED, chest.getChest().state);
    }

    @Test
    void onePressOpensOneChest() {
        Entity first = chest("map#28", CHEST_X);
        Entity second = chest("map#30", CHEST_X - 20f);
        pressE();
        chests.update(0.016f);
        int opening = (first.getChest().state == ChestComponent.State.OPENING ? 1 : 0)
            + (second.getChest().state == ChestComponent.State.OPENING ? 1 : 0);
        assertEquals(1, opening);
        pressE();
        chests.update(0.016f);
        assertEquals(ChestComponent.State.OPENING, first.getChest().state);
        assertEquals(ChestComponent.State.OPENING, second.getChest().state);
    }

    @Test
    void withoutAPlayerOrItsInteractNothingHappens() {
        Entity chest = chest("map#28", CHEST_X);
        player.setInteract(null);
        chests.update(0.016f);
        assertEquals(ChestComponent.State.CLOSED, chest.getChest().state);
        world.removeEntity(player);
        chests.update(0.016f);
        assertEquals(ChestComponent.State.CLOSED, chest.getChest().state);
    }

    @Test
    void theItemGoesWhereLootTableAndRoomSay() {
        chest("map#66", CHEST_X);
        pressE();
        chests.update(0.016f);
        chests.update(1f);
        Entity item = items().get(0);
        assertEquals(CHEST_X + 20f, item.getPickup().landingX, 1e-4);
        assertEquals(FLOOR, item.getPickup().landingY, 1e-4);
        assertSame(LootTable.DEFAULT.pick("map#66", ChestTier.WOOD), item.getPickup().type);
        assertTrue(item.getPickup().type == PickupType.HEALTH_PACK || item.getPickup().type.ammo != null);
    }
}
