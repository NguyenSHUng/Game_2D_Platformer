package com.hung.rendering;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.ChestTier;
import com.hung.combat.LootTable;
import com.hung.component.ChestComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import com.hung.world.system.ChestSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The "E - Open Chest" prompt: only beside a closed chest, gone once it opens or the player walks off,
 * the nearest chest wins, and it shows exactly where pressing E would open a chest.
 */
class InteractionPromptTest {

    private static final float TILE = 16f;
    private static final float FLOOR = 2 * TILE;
    private static final float CHEST_X = 12 * TILE + 8f;

    private final InteractionPrompt prompt = new InteractionPrompt();

    private static World world(float playerX) {
        TileGrid grid = new TileGrid(40, 12, TILE);
        for (int x = 0; x < 40; x++) grid.set(x, 1, TileType.SOLID);
        World world = new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
        Player player = new Player();
        player.getTransform().position.set(playerX, FLOOR);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        player.setHealth(new HealthComponent(100));
        player.setInteract(new InteractComponent());
        world.setPlayer(player);
        return world;
    }

    private static Entity chest(World world, String id, float x) {
        Entity chest = EntityFactory.createChest(id, ChestTier.WOOD, null, tile(), x, FLOOR);
        world.addEntity(chest);
        return chest;
    }

    private static TextureRegion tile() {
        return new TextureRegion() {
            @Override public int getRegionWidth() { return 16; }
            @Override public int getRegionHeight() { return 16; }
        };
    }

    @Test
    void showsBesideAClosedChestOnly() {
        World world = world(CHEST_X - 15f);
        assertNull(prompt.chestInReach(world), "no chest at all");
        Entity chest = chest(world, "map#1", CHEST_X);
        assertSame(chest, prompt.chestInReach(world));

        world.getPlayer().getTransform().position.x = CHEST_X - 60f;
        assertNull(prompt.chestInReach(world), "walked away");
    }

    @Test
    void hidesOnceTheChestStartsOpening() {
        World world = world(CHEST_X - 15f);
        Entity chest = chest(world, "map#1", CHEST_X);
        world.getPlayer().getInteract().isRequested = true;
        ChestSystem chests = new ChestSystem(world, LootTable.DEFAULT);
        chests.update(1f / 60f);
        assertEquals(ChestComponent.State.OPENING, chest.getChest().state);
        assertNull(prompt.chestInReach(world));
        chests.update(ChestSystem.OPEN_SECONDS);
        assertEquals(ChestComponent.State.OPENED, chest.getChest().state);
        assertNull(prompt.chestInReach(world));
    }

    @Test
    void theNearestChestWins() {
        World world = world(CHEST_X);
        chest(world, "map#far", CHEST_X - 20f);
        Entity near = chest(world, "map#near", CHEST_X + 6f);
        assertSame(near, prompt.chestInReach(world));

        // Whatever order the chests were added in
        World other = world(CHEST_X);
        Entity nearFirst = chest(other, "map#near", CHEST_X + 6f);
        chest(other, "map#far", CHEST_X - 20f);
        assertSame(nearFirst, prompt.chestInReach(other));
    }

    @Test
    void aDeadPlayerOrOneWithoutInteractGetsNoPrompt() {
        World world = world(CHEST_X - 15f);
        chest(world, "map#1", CHEST_X);
        world.getPlayer().getHealth().applyDamage(1000);
        assertNull(prompt.chestInReach(world));

        World other = world(CHEST_X - 15f);
        chest(other, "map#1", CHEST_X);
        other.getPlayer().setInteract(null);
        assertNull(prompt.chestInReach(other));
    }

    /** Walking past a chest step by step: the prompt shows exactly where E opens it. */
    @Test
    void agreesWithWhatTheInteractKeyDoes() {
        for (float x = CHEST_X - 50f; x <= CHEST_X + 50f; x += 0.5f) {
            World world = world(x);
            Entity chest = chest(world, "map#1", CHEST_X);
            boolean isShown = prompt.chestInReach(world) != null;
            world.getPlayer().getInteract().isRequested = true;
            new ChestSystem(world, LootTable.DEFAULT).update(1f / 60f);
            boolean opens = chest.getChest().state != ChestComponent.State.CLOSED;
            assertEquals(opens, isShown, "player at x=" + x);
        }
    }
}
