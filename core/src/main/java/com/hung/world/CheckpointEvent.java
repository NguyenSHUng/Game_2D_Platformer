package com.hung.world;

import com.badlogic.gdx.math.Vector2;

/**
 * Published on the EventBus when the player touches a checkpoint that wasn't already the respawn
 * point (HazardSystem). The game saves progress on it. The publisher reuses one instance, so
 * listeners copy what they keep.
 */
public class CheckpointEvent {

    /** 0-based index among the level's checkpoints. */
    public int index;
    public int count;
    /** The new respawn point (feet), world units. */
    public final Vector2 respawn = new Vector2();

    public CheckpointEvent set(int index, int count, Vector2 respawn) {
        this.index = index;
        this.count = count;
        this.respawn.set(respawn);
        return this;
    }
}
