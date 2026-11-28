package com.hung.input;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.ArsenalComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;

/**
 * Turns controller intent into movement (2D side view): walk, jump, crouch, drop through
 * one-way platforms, climb ladders, and attack requests.
 *  - A jump still works for a moment after walking off a ledge (coyote time) and a press
 *    just before landing is kept (jump buffer).
 *  - Ladders: up/down grabs a ladder at the body (or the top rung under the feet); on the
 *    ladder up/down climbs, left/right steps off, jump jumps off; climbing past the top
 *    rung stands on it.
 *  - Attack: a melee swing, or pulling the trigger when the weapon in hand is a firearm (held = full-auto
 *    for AUTO weapons); R reloads; 1 / 2 / 3 / Q ask the arsenal for another weapon (WeaponSwitchSystem switches);
 *    E asks to interact (InteractComponent, ChestSystem opens a chest).
 *  - Hurt or dead entities get no control; neither does anything while disabled (free camera).
 * Reads: Controller, MovementComponent, ColliderComponent, HealthComponent, TileGrid.
 * Writes: TransformComponent, MovementComponent, AttackComponent, WeaponComponent requests, InteractComponent.
 */
public class InputSystem {

    /** A jump still works this long after walking off a ledge, in seconds. */
    private static final float COYOTE_TIME = 0.08f;
    /** A jump pressed this long before landing still happens, in seconds. */
    private static final float JUMP_BUFFER_TIME = 0.1f;
    /** How far below a one-way platform's top the feet are put to drop through it. */
    private static final float DROP_THROUGH_DEPTH = 2f;
    /** Keeps tile probes inside the tile they test. */
    private static final float EPSILON = 0.01f;

    private final World world;
    private boolean isEnabled = true;

    public InputSystem(World world) {
        this.world = world;
    }

    /** Disabled = controlled entities stand still (used while the camera is free). */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void update(float delta) {
        TileGrid grid = world.getLevel().getGrid();
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            Controller controller = entity.getController();
            MovementComponent movement = entity.getMovement();
            if (controller == null || movement == null || entity.getCollider() == null) continue;

            movement.airTime = movement.isGrounded || movement.isClimbing ? 0f : movement.airTime + delta;
            movement.jumpBufferTime = Math.max(0f, movement.jumpBufferTime - delta);

            HealthComponent health = entity.getHealth();
            AttackComponent attack = entity.getAttack();
            boolean isIncapacitated = health != null && (health.isDead() || health.hurtTime > 0f);
            if (isIncapacitated || !isEnabled) {
                stand(entity, isIncapacitated);
                continue;
            }

            Vector2 direction = controller.getMovementDirection();
            if (controller.isJumpPressed()) movement.jumpBufferTime = JUMP_BUFFER_TIME;
            requestWeaponActions(entity, controller, attack, movement);

            if (movement.isClimbing || tryGrabLadder(grid, entity, direction)) {
                climb(grid, entity, direction);
            } else {
                walk(grid, entity, direction);
            }
        }
    }

    private static void requestWeaponActions(Entity entity, Controller controller, AttackComponent attack, MovementComponent movement) {
        WeaponComponent weapon = entity.getWeapon();
        boolean isFirearm = weapon != null && weapon.equipped != null && weapon.equipped.isFirearm();
        boolean isPressed = controller.isAttackPressed();
        if (!movement.isClimbing) {
            if (isFirearm) {
                // Full-auto keeps asking while the trigger is held; ShootingSystem paces it by the fire rate
                boolean isAutomatic = weapon.equipped.getFire().isAutomatic();
                if (isPressed || (isAutomatic && controller.isAttackHeld())) weapon.isFireRequested = true;
            } else if (isPressed && attack != null && !attack.isAttacking) {
                attack.isRequested = true;
            }
        }
        if (isFirearm && controller.isReloadPressed()) weapon.isReloadRequested = true;
        if (entity.getInteract() != null && controller.isInteractPressed()) entity.getInteract().isRequested = true;
        ArsenalComponent arsenal = entity.getArsenal();
        if (arsenal != null) {
            int slot = controller.getWeaponSlotPressed();
            if (slot >= 0) arsenal.requestedSlot = slot;
            if (controller.isNextWeaponPressed()) arsenal.isNextRequested = true;
        }
    }

    /** No control this frame: hurt / dead entities keep their knockback, others stop walking. */
    private static void stand(Entity entity, boolean isIncapacitated) {
        MovementComponent movement = entity.getMovement();
        TransformComponent transform = entity.getTransform();
        movement.jumpBufferTime = 0f;
        if (entity.getAttack() != null) entity.getAttack().isRequested = false;
        if (entity.getWeapon() != null) entity.getWeapon().isFireRequested = false;
        if (entity.getInteract() != null) entity.getInteract().isRequested = false;
        if (isIncapacitated) {
            movement.isCrouching = false;
            return;
        }
        transform.velocity.x = 0f;
        if (movement.isClimbing) transform.velocity.y = 0f;
    }

    private static void walk(TileGrid grid, Entity entity, Vector2 direction) {
        MovementComponent movement = entity.getMovement();
        TransformComponent transform = entity.getTransform();
        ColliderComponent collider = entity.getCollider();
        AttackComponent attack = entity.getAttack();
        boolean isAttacking = attack != null && attack.isAttacking;

        boolean wantsCrouch = direction.y < 0f && movement.isGrounded && collider.crouchHeight > 0f;
        if (wantsCrouch) {
            movement.isCrouching = true;
        } else if (movement.isCrouching && hasHeadroom(grid, transform, collider)) {
            movement.isCrouching = false;
        }

        if (!isAttacking) face(transform, direction.x);
        boolean isPlanted = movement.isCrouching || (isAttacking && movement.isGrounded);
        transform.velocity.x = isPlanted ? 0f : direction.x * movement.moveSpeed;

        boolean canJump = movement.jumpBufferTime > 0f && movement.airTime < COYOTE_TIME
            && !(isAttacking && movement.isGrounded);
        if (!canJump) return;
        movement.jumpBufferTime = 0f;
        if (movement.isCrouching && isOnOneWayPlatform(grid, transform, collider)) {
            // Down + jump on a one-way platform drops through it
            transform.position.y -= DROP_THROUGH_DEPTH;
            movement.isCrouching = false;
            movement.isGrounded = false;
            movement.airTime = COYOTE_TIME;
        } else if (!movement.isCrouching || hasHeadroom(grid, transform, collider)) {
            movement.isCrouching = false;
            transform.velocity.y = movement.jumpSpeed;
            movement.isGrounded = false;
            movement.airTime = COYOTE_TIME;
        }
    }

    /** Starts climbing when up/down points at a ladder; snaps to the ladder's column. */
    private static boolean tryGrabLadder(TileGrid grid, Entity entity, Vector2 direction) {
        MovementComponent movement = entity.getMovement();
        AttackComponent attack = entity.getAttack();
        if (movement.climbSpeed <= 0f || direction.y == 0f || (attack != null && attack.isAttacking)) return false;

        TransformComponent transform = entity.getTransform();
        float y = transform.position.y;
        int column = grid.toTile(transform.position.x);
        boolean isLadderAtBody = grid.get(column, grid.toTile(y + EPSILON)) == TileType.LADDER
            || grid.get(column, grid.toTile(y + entity.getCollider().height / 2f)) == TileType.LADDER;
        boolean isLadderBelowFeet = grid.get(column, grid.toTile(y - EPSILON)) == TileType.LADDER;
        // Down on the ground grabs only a ladder going down from here (else it's a crouch)
        boolean canGrab = direction.y > 0f || !movement.isGrounded ? isLadderAtBody : isLadderBelowFeet;
        if (!canGrab) return false;

        movement.isClimbing = true;
        movement.isCrouching = false;
        movement.isGrounded = false;
        transform.position.x = (column + 0.5f) * grid.getTileSize();
        transform.velocity.setZero();
        return true;
    }

    private static void climb(TileGrid grid, Entity entity, Vector2 direction) {
        MovementComponent movement = entity.getMovement();
        TransformComponent transform = entity.getTransform();
        int column = grid.toTile(transform.position.x);
        int feetRow = grid.toTile(transform.position.y + EPSILON);
        boolean isLadderAtFeet = grid.get(column, feetRow) == TileType.LADDER;
        boolean isLadderBelow = grid.get(column, grid.toTile(transform.position.y - EPSILON)) == TileType.LADDER;

        if (movement.jumpBufferTime > 0f) {
            movement.isClimbing = false;
            movement.jumpBufferTime = 0f;
            movement.airTime = COYOTE_TIME;
            face(transform, direction.x);
            transform.velocity.set(direction.x * movement.moveSpeed, movement.jumpSpeed);
            return;
        }
        if (direction.x != 0f && direction.y == 0f) {
            movement.isClimbing = false;
            face(transform, direction.x);
            transform.velocity.x = direction.x * movement.moveSpeed;
            return;
        }
        if (!isLadderAtFeet) {
            if (isLadderBelow && direction.y > 0f) {
                // Climbed past the top rung: stand on it
                transform.position.y = feetRow * grid.getTileSize();
                transform.velocity.setZero();
                movement.isClimbing = false;
                movement.isGrounded = true;
                return;
            }
            if (!isLadderBelow) {
                movement.isClimbing = false;
                return;
            }
        }
        if (movement.isGrounded && direction.y < 0f) {
            // Reached the floor at the ladder's foot
            movement.isClimbing = false;
            return;
        }
        transform.velocity.x = 0f;
        transform.velocity.y = direction.y * movement.climbSpeed;
    }

    private static void face(TransformComponent transform, float directionX) {
        if (directionX > 0f) {
            transform.isFacingRight = true;
        } else if (directionX < 0f) {
            transform.isFacingRight = false;
        }
    }

    /** True when standing up from a crouch wouldn't put the head inside a solid tile. */
    private static boolean hasHeadroom(TileGrid grid, TransformComponent transform, ColliderComponent collider) {
        float half = collider.width / 2f;
        int leftColumn = grid.toTile(transform.position.x - half + EPSILON);
        int rightColumn = grid.toTile(transform.position.x + half - EPSILON);
        int fromRow = grid.toTile(transform.position.y + collider.crouchHeight);
        int toRow = grid.toTile(transform.position.y + collider.height - EPSILON);
        for (int row = fromRow; row <= toRow; row++) {
            for (int column = leftColumn; column <= rightColumn; column++) {
                if (grid.get(column, row).isSolid()) return false;
            }
        }
        return true;
    }

    /** True when everything under the feet is a one-way platform (nothing solid, no ladder top). */
    private static boolean isOnOneWayPlatform(TileGrid grid, TransformComponent transform, ColliderComponent collider) {
        float half = collider.width / 2f;
        int row = grid.toTile(transform.position.y - EPSILON);
        boolean hasPlatform = false;
        for (int column = grid.toTile(transform.position.x - half + EPSILON);
             column <= grid.toTile(transform.position.x + half - EPSILON); column++) {
            TileType type = grid.get(column, row);
            if (type == TileType.PLATFORM) {
                hasPlatform = true;
            } else if (type != TileType.EMPTY && !type.isHazard()) {
                return false;
            }
        }
        return hasPlatform;
    }
}
