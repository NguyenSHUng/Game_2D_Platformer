package com.hung.ai;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.ColliderComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.entity.Entity;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Turning at ledges: the behaviour the shared ledge probe must keep. */
class PatrolSystemTest {

    private static final float TILE = 16f;

    /** Floor of SOLID tiles on row 0 from column `from` to `to` (inclusive); the walker stands on row 1. */
    private static World floor(int from, int to) {
        TileGrid grid = new TileGrid(20, 6, TILE);
        for (int x = from; x <= to; x++) grid.set(x, 0, TileType.SOLID);
        return new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
    }

    private static Entity walker(float x, int direction) {
        Entity entity = new Entity();
        entity.getTransform().position.set(x, TILE);
        MovementComponent movement = new MovementComponent();
        movement.isGrounded = true;
        movement.moveSpeed = 30f;
        entity.setMovement(movement);
        ColliderComponent collider = new ColliderComponent();
        collider.width = 10f;
        collider.height = 20f;
        entity.setCollider(collider);
        PatrolComponent patrol = new PatrolComponent();
        patrol.minX = 0f;
        patrol.maxX = 1000f;
        patrol.direction = direction;
        entity.setPatrol(patrol);
        return entity;
    }

    @Test
    void keepsWalkingWhileThereIsFloorAhead() {
        World world = floor(0, 10);
        Entity entity = walker(5 * TILE, 1);
        world.addEntity(entity);
        new PatrolSystem(world).update();
        assertEquals(1, entity.getPatrol().direction);
        assertEquals(30f, entity.getTransform().velocity.x);
    }

    @Test
    void turnsBeforeSteppingOffALedge() {
        World world = floor(0, 5);
        // Feet near the right edge of column 5: the probe (half width + 1) lands over column 6, empty
        Entity entity = walker(6 * TILE - 3f, 1);
        world.addEntity(entity);
        new PatrolSystem(world).update();
        assertEquals(-1, entity.getPatrol().direction);
        assertEquals(-30f, entity.getTransform().velocity.x);
    }

    @Test
    void flyersIgnoreLedges() {
        World world = floor(0, 5);
        Entity entity = walker(6 * TILE - 3f, 1);
        entity.getMovement().isGrounded = false;
        entity.getMovement().isFlying = true;
        world.addEntity(entity);
        new PatrolSystem(world).update();
        assertEquals(1, entity.getPatrol().direction);
    }
}
