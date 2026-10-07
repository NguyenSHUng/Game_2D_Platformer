package com.hung.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.hung.component.ColliderComponent;
import com.hung.component.DoorComponent;
import com.hung.component.PatrolComponent;
import com.hung.component.TransformComponent;
import com.hung.entity.Entity;
import com.hung.physics.Hitboxes;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;

/**
 * F3 overlay: collision tiles on screen (solid edges, one-way tops, ladders, hazards,
 * breakable walls), entity boxes and feet points, active attack boxes, enemy patrol ranges,
 * checkpoints, spawn and exit, the current room's camera bounds and door triggers
 * (with a line to where each door drops the player).
 */
public class DebugRenderer implements Disposable {

    private static final Color SOLID_COLOR = new Color(1f, 0.25f, 0.25f, 1f);
    private static final Color PLATFORM_COLOR = Color.CYAN;
    private static final Color LADDER_COLOR = Color.LIME;
    private static final Color HAZARD_COLOR = Color.MAGENTA;
    private static final Color BREAKABLE_COLOR = Color.GOLD;
    private static final Color COLLIDER_COLOR = Color.YELLOW;
    private static final Color PATROL_COLOR = Color.ORANGE;
    private static final Color SPAWN_COLOR = Color.GREEN;
    private static final Color EXIT_COLOR = Color.GOLD;
    private static final Color ATTACK_COLOR = Color.RED;
    private static final Color CHECKPOINT_COLOR = Color.SKY;
    private static final Color ROOM_BOUNDS_COLOR = Color.CYAN;
    private static final Color DOOR_COLOR = Color.ORANGE;
    private static final float FEET_MARK_SIZE = 4f;
    private static final float HAZARD_INSET = 0.2f;
    private static final float LADDER_INSET = 0.25f;
    private static final float SPAWN_BOX_TILES = 2f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    // Reused every frame to avoid allocating in the game loop
    private final Rectangle box = new Rectangle();
    private final Vector2 center = new Vector2();

    public void render(OrthographicCamera camera, World world) {
        Level level = world.getLevel();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawTiles(camera, level.getGrid());
        drawEntities(world.getEntities());
        drawMarker(level.getPlayerSpawn(), level.getGrid().getTileSize(), SPAWN_COLOR);
        drawMarker(level.getExit(), level.getGrid().getTileSize(), EXIT_COLOR);
        shapes.setColor(CHECKPOINT_COLOR);
        for (Rectangle checkpoint : level.getCheckpoints()) {
            shapes.rect(checkpoint.x, checkpoint.y, checkpoint.width, checkpoint.height);
        }
        Room room = world.getRoomTransition().currentRoom;
        if (room != null) {
            shapes.setColor(ROOM_BOUNDS_COLOR);
            shapes.rect(room.bounds.x, room.bounds.y, room.bounds.width, room.bounds.height);
        }
        shapes.end();
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }

    /** Only the tiles inside the camera view. */
    private void drawTiles(OrthographicCamera camera, TileGrid grid) {
        float size = grid.getTileSize();
        float halfWidth = camera.viewportWidth * camera.zoom / 2f;
        float halfHeight = camera.viewportHeight * camera.zoom / 2f;
        int fromX = Math.max(0, grid.toTile(camera.position.x - halfWidth));
        int toX = Math.min(grid.getWidth() - 1, grid.toTile(camera.position.x + halfWidth));
        int fromY = Math.max(0, grid.toTile(camera.position.y - halfHeight));
        int toY = Math.min(grid.getHeight() - 1, grid.toTile(camera.position.y + halfHeight));

        for (int ty = fromY; ty <= toY; ty++) {
            for (int tx = fromX; tx <= toX; tx++) {
                TileType type = grid.get(tx, ty);
                float x = tx * size;
                float y = ty * size;
                if (type == TileType.SOLID) {
                    shapes.setColor(SOLID_COLOR);
                    if (!grid.get(tx, ty + 1).isSolid()) shapes.line(x, y + size, x + size, y + size);
                    if (!grid.get(tx, ty - 1).isSolid()) shapes.line(x, y, x + size, y);
                    if (!grid.get(tx - 1, ty).isSolid()) shapes.line(x, y, x, y + size);
                    if (!grid.get(tx + 1, ty).isSolid()) shapes.line(x + size, y, x + size, y + size);
                } else if (type == TileType.PLATFORM) {
                    shapes.setColor(PLATFORM_COLOR);
                    shapes.line(x, y + size, x + size, y + size);
                } else if (type == TileType.LADDER) {
                    shapes.setColor(LADDER_COLOR);
                    shapes.rect(x + size * LADDER_INSET, y, size * (1f - 2f * LADDER_INSET), size);
                } else if (type.isHazard()) {
                    shapes.setColor(HAZARD_COLOR);
                    shapes.rect(x + size * HAZARD_INSET, y, size * (1f - 2f * HAZARD_INSET), size * (1f - HAZARD_INSET));
                } else if (type == TileType.BREAKABLE) {
                    shapes.setColor(BREAKABLE_COLOR);
                    shapes.rect(x, y, size, size);
                }
            }
        }
    }

    private void drawEntities(Array<Entity> entities) {
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            TransformComponent transform = entity.getTransform();
            Vector2 feet = transform.position;

            ColliderComponent collider = entity.getCollider();
            if (collider != null) {
                shapes.setColor(COLLIDER_COLOR);
                Hitboxes.body(entity, box);
                shapes.rect(box.x, box.y, box.width, box.height);
            }
            drawAttackBox(entity);
            drawDoor(entity.getDoor());
            PatrolComponent patrol = entity.getPatrol();
            if (patrol != null) {
                shapes.setColor(PATROL_COLOR);
                shapes.line(patrol.minX, feet.y, patrol.maxX, feet.y);
                shapes.line(patrol.minX, feet.y - FEET_MARK_SIZE, patrol.minX, feet.y + FEET_MARK_SIZE);
                shapes.line(patrol.maxX, feet.y - FEET_MARK_SIZE, patrol.maxX, feet.y + FEET_MARK_SIZE);
            }
            shapes.setColor(COLLIDER_COLOR);
            shapes.line(feet.x - FEET_MARK_SIZE, feet.y, feet.x + FEET_MARK_SIZE, feet.y);
            shapes.line(feet.x, feet.y - FEET_MARK_SIZE, feet.x, feet.y + FEET_MARK_SIZE);
        }
    }

    /** Door trigger and a line to its arrival point in the target room. */
    private void drawDoor(DoorComponent door) {
        if (door == null) return;
        shapes.setColor(DOOR_COLOR);
        shapes.rect(door.trigger.x, door.trigger.y, door.trigger.width, door.trigger.height);
        door.trigger.getCenter(center);
        shapes.line(center.x, center.y, door.spawn.x, door.spawn.y);
    }

    /** The current frame's attack box while an ATTACK animation is playing. */
    private void drawAttackBox(Entity entity) {
        if (!Hitboxes.attack(entity, box)) return;
        shapes.setColor(ATTACK_COLOR);
        shapes.rect(box.x, box.y, box.width, box.height);
    }

    /** A 1 × 2 tile box standing on the given feet point. */
    private void drawMarker(Vector2 feet, float tileSize, Color color) {
        if (feet == null) return;
        shapes.setColor(color);
        shapes.rect(MathUtils.round(feet.x - tileSize / 2f), feet.y, tileSize, tileSize * SPAWN_BOX_TILES);
    }
}
