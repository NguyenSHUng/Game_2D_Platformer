package com.hung.combat.system;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.ChestTier;
import com.hung.combat.ItemArt;
import com.hung.combat.LootDrop;
import com.hung.combat.LootTable;
import com.hung.combat.PickupType;
import com.hung.combat.WeaponEffect;
import com.hung.component.ChestComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.entity.EntityFactory;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import com.hung.world.system.ChestSystem;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** With loot art: chests play their opening and stay open on its last frame; items kick up dust and flash. */
class LootArtTest {

    private static final float FLOOR = 32f;
    private static final float STEP = 1f / 60f;

    private static TextureRegion region(int size) {
        return new TextureRegion() {
            @Override public int getRegionWidth() { return size; }
            @Override public int getRegionHeight() { return size; }
        };
    }

    private static WeaponEffect strip(int frames, int size) {
        Array<TextureRegion> keyFrames = new Array<>(true, frames, TextureRegion[]::new);
        for (int i = 0; i < frames; i++) keyFrames.add(region(size));
        return new WeaponEffect(new Animation<>(0.1f, keyFrames, Animation.PlayMode.NORMAL), 0f, 0f);
    }

    private static ItemArt art() {
        Map<PickupType, WeaponEffect> items = new EnumMap<>(PickupType.class);
        for (PickupType type : PickupType.values()) items.put(type, strip(1, 12));
        Map<ChestTier, WeaponEffect> chests = new EnumMap<>(ChestTier.class);
        chests.put(ChestTier.WOOD, strip(4, 16));
        chests.put(ChestTier.GOLD, strip(4, 16));
        return new ItemArt(items, chests, strip(4, 9), strip(4, 11));
    }

    private static World world() {
        TileGrid grid = new TileGrid(40, 12, 16f);
        for (int x = 0; x < 40; x++) grid.set(x, 1, TileType.SOLID);
        World world = new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
        Player player = new Player();
        player.getTransform().position.set(400f, FLOOR);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        HealthComponent health = new HealthComponent(100);
        health.applyDamage(50);
        player.setHealth(health);
        player.setInteract(new InteractComponent());
        world.setPlayer(player);
        return world;
    }

    private static int effects(World world) {
        int count = 0;
        for (Entity entity : world.getEntities()) if (entity.getEffect() != null) count++;
        return count;
    }

    @Test
    void aThrownItemKicksUpDustOnceAndFlashesWhenPicked() {
        World world = world();
        ItemArt art = art();
        Entity item = LootDrop.throwFromChest(PickupType.HEALTH_PACK, world.getLevel().getGrid(), null, 200f, FLOOR);
        world.addEntity(item);
        PickupSystem pickups = new PickupSystem(world, new EventBus(), art);
        while (item.getPickup().isFlying) pickups.update(STEP);
        assertEquals(1, effects(world));
        for (Entity entity : world.getEntities()) {
            if (entity.getEffect() != null) assertSame(art.landing().animation, entity.getSprite().animation);
        }

        world.getPlayer().getTransform().position.set(item.getTransform().position);
        pickups.update(STEP);
        assertEquals(2, effects(world));
    }

    @Test
    void withoutArtNoEffects() {
        World world = world();
        Entity item = LootDrop.throwFromChest(PickupType.HEALTH_PACK, world.getLevel().getGrid(), null, 200f, FLOOR);
        world.addEntity(item);
        PickupSystem pickups = new PickupSystem(world, new EventBus());
        while (item.getPickup().isFlying) pickups.update(STEP);
        assertEquals(0, effects(world));
        assertNull(ItemArt.NONE.item(PickupType.HEALTH_PACK));
        assertNull(ItemArt.NONE.chestOpening(ChestTier.GOLD));
    }

    @Test
    void aChestPlaysItsOpeningAndHoldsTheLastFrame() {
        World world = world();
        Animation<TextureRegion> opening = art().chestOpening(ChestTier.WOOD).animation;
        Entity chest = EntityFactory.createChest("map#28", ChestTier.WOOD, null, region(16), opening, 200f, FLOOR);
        world.addEntity(chest);
        world.getPlayer().getTransform().position.x = 188f;
        world.getPlayer().getInteract().isRequested = true;
        ChestSystem chests = new ChestSystem(world, LootTable.DEFAULT);
        chests.update(STEP);
        assertSame(opening, chest.getSprite().animation);
        assertEquals(0f, chest.getSprite().stateTime, 1e-6);

        chests.update(ChestSystem.OPEN_SECONDS);
        assertEquals(ChestComponent.State.OPENED, chest.getChest().state);
        assertSame(opening, chest.getSprite().animation);
        assertEquals(opening.getAnimationDuration(), chest.getSprite().stateTime, 1e-6);
        assertSame(opening.getKeyFrames()[3], chest.getSprite().animation.getKeyFrame(chest.getSprite().stateTime + 5f));
        assertEquals(Color.WHITE, chest.getSprite().tint);
    }
}
