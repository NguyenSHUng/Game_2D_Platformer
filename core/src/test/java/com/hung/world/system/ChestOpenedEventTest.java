package com.hung.world.system;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.combat.ChestTier;
import com.hung.combat.LootTable;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.EntityFactory;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import com.hung.world.event.ChestOpenedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An opened chest is reported once, when its item comes out. */
class ChestOpenedEventTest {

    @Test
    void reportedOnceWhenTheItemComesOut() {
        TileGrid grid = new TileGrid(40, 12, 16f);
        for (int x = 0; x < 40; x++) grid.set(x, 1, TileType.SOLID);
        World world = new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
        Player player = new Player();
        player.getTransform().position.set(185f, 32f);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        player.setHealth(new HealthComponent(100));
        player.setInteract(new InteractComponent());
        world.setPlayer(player);
        TextureRegion tile = new TextureRegion() {
            @Override public int getRegionWidth() { return 16; }
            @Override public int getRegionHeight() { return 16; }
        };
        world.addEntity(EntityFactory.createChest("map#30", ChestTier.GOLD, null, tile, 200f, 32f));
        EventBus events = new EventBus();
        List<String> opened = new ArrayList<>();
        events.subscribe(ChestOpenedEvent.class, event -> opened.add(event.chestId + " " + event.tier));
        ChestSystem chests = new ChestSystem(world, LootTable.DEFAULT, events);

        player.getInteract().isRequested = true;
        chests.update(0.016f);
        assertEquals(0, opened.size());
        chests.update(ChestSystem.OPEN_SECONDS);
        for (int i = 0; i < 3; i++) {
            player.getInteract().isRequested = true;
            chests.update(1f);
        }
        assertEquals(List.of("map#30 GOLD"), opened);
    }
}
