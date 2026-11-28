package com.hung.input;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.InteractComponent;
import com.hung.component.MovementComponent;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The interact key reaches the player's InteractComponent only while the player is in control. */
class InteractInputTest {

    /** A controller whose only input is the interact key. */
    private static final class InteractOnly implements Controller {
        boolean isInteract;
        private final Vector2 still = new Vector2();

        @Override public Vector2 getMovementDirection() { return still; }
        @Override public boolean isJumpPressed() { return false; }
        @Override public boolean isAttackPressed() { return false; }
        @Override public boolean isAttackHeld() { return false; }
        @Override public boolean isReloadPressed() { return false; }
        @Override public int getWeaponSlotPressed() { return -1; }
        @Override public boolean isNextWeaponPressed() { return false; }
        @Override public boolean isInteractPressed() { return isInteract; }
    }

    private final InteractOnly controller = new InteractOnly();
    private World world;
    private Player player;
    private InputSystem input;

    @BeforeEach
    void setUp() {
        TileGrid grid = new TileGrid(20, 10, 16f);
        for (int x = 0; x < 20; x++) grid.set(x, 1, TileType.SOLID);
        world = new World(new Level("test", null, grid, null, new Vector2(), null, new Array<Rectangle>()));
        player = new Player();
        player.getTransform().position.set(100f, 32f);
        MovementComponent movement = new MovementComponent();
        movement.isGrounded = true;
        movement.moveSpeed = 96f;
        player.setMovement(movement);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);
        player.setHealth(new HealthComponent(100));
        player.setInteract(new InteractComponent());
        player.setController(controller);
        world.setPlayer(player);
        input = new InputSystem(world);
    }

    @Test
    void theInteractKeyAsksToInteract() {
        input.update(0.016f);
        assertFalse(player.getInteract().isRequested);
        controller.isInteract = true;
        input.update(0.016f);
        assertTrue(player.getInteract().isRequested);
    }

    @Test
    void notWhileTheCameraIsFree() {
        player.getInteract().isRequested = true;
        controller.isInteract = true;
        input.setEnabled(false);
        input.update(0.016f);
        assertFalse(player.getInteract().isRequested);
    }

    @Test
    void notWhileHurtOrDead() {
        controller.isInteract = true;
        player.getHealth().hurtTime = 0.3f;
        input.update(0.016f);
        assertFalse(player.getInteract().isRequested);

        player.getHealth().hurtTime = 0f;
        player.getHealth().applyDamage(100);
        input.update(0.016f);
        assertFalse(player.getInteract().isRequested);
    }
}
