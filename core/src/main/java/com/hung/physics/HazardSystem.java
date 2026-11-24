package com.hung.physics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.component.HealthComponent;
import com.hung.core.event.EventBus;
import com.hung.entity.Entity;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.World;
import com.hung.world.event.CheckpointEvent;
import com.hung.world.event.ExitReachedEvent;
import com.hung.world.system.PlayerRespawn;

/**
 * What the level does to the player:
 *  - touching a checkpoint makes it the respawn point (CheckpointEvent: the game saves);
 *  - touching a SPIKE / TOXIC tile or falling out of the map sends the player back to the
 *    respawn point (no damage yet: trap damage tiers are planned, build_asset/build_map/PROGRESS.md §6);
 *  - reaching the exit is reported once (ExitReachedEvent: the run is complete).
 * Reads: ColliderComponent, TileGrid, Level. Writes: World respawn point, player position. Publishes on the EventBus.
 */
public class HazardSystem {

    private static final String TAG = "HazardSystem";
    /** Hazard tiles hurt only in their lower part, inset like the debug overlay draws them (fraction of a tile). */
    private static final float HAZARD_INSET = 0.2f;
    /** The exit marker is this many tiles tall. */
    private static final float EXIT_HEIGHT_TILES = 2f;
    /** Falling this far below the map counts as out of the level, world units. */
    private static final float FALL_OUT_MARGIN = 64f;

    private final World world;
    private final EventBus events;
    private final CheckpointEvent checkpointEvent = new CheckpointEvent();
    private final ExitReachedEvent exitEvent = new ExitReachedEvent();
    private boolean hasReachedExit;
    // Reused every frame to avoid allocating in the game loop
    private final Rectangle body = new Rectangle();
    private final Rectangle area = new Rectangle();

    public HazardSystem(World world, EventBus events) {
        this.world = world;
        this.events = events;
    }

    public void update() {
        Entity player = world.getPlayer();
        if (player == null || player.getCollider() == null) return;
        HealthComponent health = player.getHealth();
        if (health != null && health.isDead()) return;

        Level level = world.getLevel();
        Hitboxes.body(player, body);
        updateCheckpoint(level);
        updateExit(level);

        if (body.y < -FALL_OUT_MARGIN || isTouchingHazard(level.getGrid())) {
            PlayerRespawn.respawn(world, player);
        }
    }

    private void updateCheckpoint(Level level) {
        Array<Rectangle> checkpoints = level.getCheckpoints();
        Vector2 respawn = world.getRespawnPoint();
        for (int i = 0; i < checkpoints.size; i++) {
            Rectangle checkpoint = checkpoints.get(i);
            if (!checkpoint.overlaps(body)) continue;
            float x = checkpoint.x + checkpoint.width / 2f;
            if (respawn.x != x || respawn.y != checkpoint.y) {
                world.setRespawnPoint(x, checkpoint.y);
                Gdx.app.log(TAG, "Checkpoint " + (i + 1) + "/" + checkpoints.size + " at " + respawn);
                events.publish(checkpointEvent.set(i, checkpoints.size, respawn));
            }
            return;
        }
    }

    private void updateExit(Level level) {
        Vector2 exit = level.getExit();
        if (exit == null || hasReachedExit) return;
        float tileSize = level.getGrid().getTileSize();
        area.set(exit.x - tileSize / 2f, exit.y, tileSize, tileSize * EXIT_HEIGHT_TILES);
        if (area.overlaps(body)) {
            hasReachedExit = true;
            Gdx.app.log(TAG, "Reached the exit of " + level.getName());
            events.publish(exitEvent.set(level.getName()));
        }
    }

    private boolean isTouchingHazard(TileGrid grid) {
        float tileSize = grid.getTileSize();
        int toColumn = grid.toTile(body.x + body.width);
        int toRow = grid.toTile(body.y + body.height);
        for (int row = grid.toTile(body.y); row <= toRow; row++) {
            for (int column = grid.toTile(body.x); column <= toColumn; column++) {
                if (!grid.get(column, row).isHazard()) continue;
                area.set((column + HAZARD_INSET) * tileSize, row * tileSize,
                    tileSize * (1f - 2f * HAZARD_INSET), tileSize * (1f - HAZARD_INSET));
                if (area.overlaps(body)) return true;
            }
        }
        return false;
    }
}
