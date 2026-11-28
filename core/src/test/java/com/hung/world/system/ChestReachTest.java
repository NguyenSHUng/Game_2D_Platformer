package com.hung.world.system;

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
import com.hung.rendering.InteractionPrompt;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** With two chests in reach, E opens the nearest one: the same chest the HUD prompt shows. */
class ChestReachTest {

    private static final float TILE = 16f;
    private static final float FLOOR = 2 * TILE;
    private static final float LEFT_X = 12 * TILE + 8f;
    private static final float RIGHT_X = LEFT_X + 24f;

    private final Room room = new Room("room_a", 0, "STANDARD", "region_1", true, new Rectangle(0f, 0f, 640f, 192f));

    private World world(float playerX) {
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

    private Entity chest(World world, String id, float x) {
        Entity chest = EntityFactory.createChest(id, ChestTier.WOOD, room, tile(), x, FLOOR);
        world.addEntity(chest);
        return chest;
    }

    private static TextureRegion tile() {
        return new TextureRegion() {
            @Override public int getRegionWidth() { return 16; }
            @Override public int getRegionHeight() { return 16; }
        };
    }

    /** Presses E once, runs one ChestSystem frame, returns the chest that started opening (or null). */
    private static Entity pressE(World world, Entity... candidates) {
        world.getPlayer().getInteract().isRequested = true;
        new ChestSystem(world, LootTable.DEFAULT).update(0.016f);
        Entity opening = null;
        for (Entity chest : candidates) {
            if (chest.getChest().state == ChestComponent.State.OPENING) {
                assertNull(opening, "one chest per press");
                opening = chest;
            }
        }
        return opening;
    }

    @Test
    void bothChestsAreInReachFromBetweenThem() {
        for (float playerX : new float[] {LEFT_X + 9f, RIGHT_X - 9f}) {
            World world = world(playerX);
            Entity left = chest(world, "map#1", LEFT_X);
            Entity right = chest(world, "map#2", RIGHT_X);
            ChestReach reach = new ChestReach();
            right.getChest().state = ChestComponent.State.OPENED;
            assertSame(left, reach.nearestClosedChest(world), "left alone in reach at x " + playerX);
            right.getChest().state = ChestComponent.State.CLOSED;
            left.getChest().state = ChestComponent.State.OPENED;
            assertSame(right, reach.nearestClosedChest(world), "right alone in reach at x " + playerX);
        }
    }

    @Test
    void eOpensTheNearestChestWhicheverWasAddedFirst() {
        // Player nearer the right chest; the left one comes first in the entity list
        World world = world(RIGHT_X - 9f);
        Entity left = chest(world, "map#1", LEFT_X);
        Entity right = chest(world, "map#2", RIGHT_X);
        assertSame(right, pressE(world, left, right));

        // Mirrored: nearer the left chest, the right one comes first in the list
        world = world(LEFT_X + 9f);
        right = chest(world, "map#2", RIGHT_X);
        left = chest(world, "map#1", LEFT_X);
        assertSame(left, pressE(world, left, right));
    }

    @Test
    void ePicksTheChestThePromptShows() {
        InteractionPrompt prompt = new InteractionPrompt();
        int checked = 0;
        for (float playerX = LEFT_X - 30f; playerX <= RIGHT_X + 30f; playerX += 1f) {
            for (boolean leftFirst : new boolean[] {true, false}) {
                World world = world(playerX);
                Entity left = leftFirst ? chest(world, "map#1", LEFT_X) : null;
                Entity right = chest(world, "map#2", RIGHT_X);
                if (!leftFirst) left = chest(world, "map#1", LEFT_X);
                Entity shown = prompt.chestInReach(world);
                assertSame(shown, pressE(world, left, right), "player x " + playerX + ", left first " + leftFirst);
                if (shown != null) checked++;
            }
        }
        assertEquals(true, checked > 20, "the sweep crossed chests in reach: " + checked);
    }
}
