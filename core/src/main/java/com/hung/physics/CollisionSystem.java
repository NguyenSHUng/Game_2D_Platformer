package com.hung.physics;

import com.hung.component.ColliderComponent;
import com.hung.component.MovementComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;

/**
 * Moves a collider box through the tile grid, one axis at a time:
 *  - horizontal: SOLID / BREAKABLE tiles stop it (sets isBlocked);
 *  - rising: SOLID tiles bonk the head, one-way tiles are passed through;
 *  - falling: lands on SOLID, PLATFORM or a ladder's top rung, but only when the
 *    feet cross the tile's top from above (sets isGrounded). While climbing a ladder's
 *    top rung doesn't stop it, so the player can climb down through it.
 * The box is the crouch box while crouching (Hitboxes.bodyHeight).
 * Same rules as the map generator's validator, so validated jumps work in game.
 */
public class CollisionSystem {

    /** Keeps boxes a hair away from tile edges so they don't count as overlapping. */
    private static final float EPSILON = 0.01f;

    private final World world;

    public CollisionSystem(World world) {
        this.world = world;
    }

    public void move(Entity entity, float dx, float dy) {
        TileGrid grid = world.getLevel().getGrid();
        TransformComponent transform = entity.getTransform();
        ColliderComponent collider = entity.getCollider();
        MovementComponent movement = entity.getMovement();

        float height = Hitboxes.bodyHeight(collider, movement);
        boolean isClimbing = movement != null && movement.isClimbing;
        boolean isBlocked = moveHorizontally(grid, transform, collider.width, height, dx);
        boolean isGrounded = moveVertically(grid, transform, collider.width, height, dy, isClimbing);
        if (movement != null) {
            movement.isBlocked = isBlocked;
            movement.isGrounded = isGrounded;
        }
    }

    private static boolean moveHorizontally(TileGrid grid, TransformComponent transform, float width, float height,
                                            float dx) {
        if (dx == 0f) return false;
        float tileSize = grid.getTileSize();
        float half = width / 2f;
        float newX = transform.position.x + dx;
        int column = grid.toTile(dx > 0 ? newX + half : newX - half);
        int bottomRow = grid.toTile(transform.position.y + EPSILON);
        int topRow = grid.toTile(transform.position.y + height - EPSILON);
        for (int row = bottomRow; row <= topRow; row++) {
            if (grid.get(column, row).isSolid()) {
                transform.position.x = dx > 0 ? column * tileSize - half - EPSILON : (column + 1) * tileSize + half + EPSILON;
                transform.velocity.x = 0f;
                return true;
            }
        }
        transform.position.x = newX;
        return false;
    }

    /** Returns true when the box ends standing on something. */
    private static boolean moveVertically(TileGrid grid, TransformComponent transform, float width, float height,
                                          float dy, boolean isClimbing) {
        float tileSize = grid.getTileSize();
        float half = width / 2f;
        int leftColumn = grid.toTile(transform.position.x - half + EPSILON);
        int rightColumn = grid.toTile(transform.position.x + half - EPSILON);

        if (dy > 0f) {
            float oldTop = transform.position.y + height;
            int fromRow = grid.toTile(oldTop - EPSILON);
            int toRow = grid.toTile(oldTop + dy);
            for (int row = fromRow + 1; row <= toRow; row++) {
                if (isRowSolid(grid, leftColumn, rightColumn, row)) {
                    transform.position.y = row * tileSize - height - EPSILON;
                    transform.velocity.y = 0f;
                    return false;
                }
            }
            transform.position.y += dy;
            return false;
        }

        // Falling (gravity keeps dy < 0 even while standing, so ground is re-found every frame)
        float oldBottom = transform.position.y;
        float newBottom = oldBottom + dy;
        for (int row = grid.toTile(oldBottom - EPSILON); row >= grid.toTile(newBottom); row--) {
            float rowTop = (row + 1) * tileSize;
            if (rowTop > oldBottom + EPSILON) continue;
            if (isRowSupport(grid, leftColumn, rightColumn, row, isClimbing)) {
                transform.position.y = rowTop;
                transform.velocity.y = 0f;
                return true;
            }
        }
        transform.position.y = newBottom;
        return false;
    }

    private static boolean isRowSolid(TileGrid grid, int fromColumn, int toColumn, int row) {
        for (int column = fromColumn; column <= toColumn; column++) {
            if (grid.get(column, row).isSolid()) return true;
        }
        return false;
    }

    /** While climbing, ladder top rungs don't hold the box so it can climb down through them. */
    private static boolean isRowSupport(TileGrid grid, int fromColumn, int toColumn, int row, boolean isClimbing) {
        for (int column = fromColumn; column <= toColumn; column++) {
            if (isClimbing) {
                TileType type = grid.get(column, row);
                if (type.isSolid() || type == TileType.PLATFORM) return true;
            } else if (grid.isSupport(column, row)) {
                return true;
            }
        }
        return false;
    }
}
