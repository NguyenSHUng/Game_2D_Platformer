package com.hung.world;

/**
 * Movement numbers shared with the map generator. The generator only builds jumps
 * the player can make with these values, so the game must use the same ones:
 * they come from the map's "physics.*" properties (in tiles) and are converted
 * to world units here.
 *
 * Flat jump: air time T = jumpDistance / moveSpeed, apex jumpHeight at T / 2,
 * so gravity = 8 · jumpHeight / T² and jumpSpeed = 4 · jumpHeight / T.
 */
public class PhysicsSettings {

    /** Defaults match the generator's default player (1 × 2 tiles). */
    public static final float DEFAULT_MOVE_SPEED_TILES = 6f;
    public static final float DEFAULT_JUMP_HEIGHT_TILES = 3.5f;
    public static final float DEFAULT_JUMP_DISTANCE_TILES = 6f;

    /**
     * Ladder speed in tiles per second. Not part of the generator's contract: ladders connect
     * floors whatever the speed, so the game picks it.
     */
    private static final float CLIMB_SPEED_TILES = 4f;

    /** Fastest fall, as a multiple of the jump speed. */
    private static final float MAX_FALL_SPEED_FACTOR = 1.6f;

    /** Downward acceleration, world units per second squared. */
    public final float gravity;
    public final float maxFallSpeed;
    /** Player walk speed and initial jump speed, world units per second. */
    public final float playerMoveSpeed;
    public final float playerJumpSpeed;
    public final float playerClimbSpeed;

    public PhysicsSettings(float tileSize, float moveSpeedTiles, float jumpHeightTiles, float jumpDistanceTiles) {
        float airTime = jumpDistanceTiles / moveSpeedTiles;
        this.gravity = 8f * jumpHeightTiles / (airTime * airTime) * tileSize;
        this.playerJumpSpeed = 4f * jumpHeightTiles / airTime * tileSize;
        this.playerMoveSpeed = moveSpeedTiles * tileSize;
        this.playerClimbSpeed = CLIMB_SPEED_TILES * tileSize;
        this.maxFallSpeed = playerJumpSpeed * MAX_FALL_SPEED_FACTOR;
    }
}
