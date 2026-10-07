package com.hung.component;

/**
 * Movement stats and state for entities that walk, jump, climb and fall under gravity.
 */
public class MovementComponent {

    /** Horizontal walk speed in world units per second. */
    public float moveSpeed;

    /** Initial upward speed of a jump, in world units per second. */
    public float jumpSpeed;

    /** Speed along a ladder in world units per second; 0 = can't climb. */
    public float climbSpeed;

    /** Standing on something this frame (set by CollisionSystem). */
    public boolean isGrounded;

    /** Walked into a wall this frame (set by CollisionSystem). */
    public boolean isBlocked;

    /** Holding on to a ladder: no gravity, one-way tiles don't stop it. */
    public boolean isClimbing;

    /** Flying (FLYING enemies): no gravity; altitude is steered by EnemyBrainSystem. */
    public boolean isFlying;
    /** Crouching: doesn't walk, uses the collider's crouch height. */
    public boolean isCrouching;

    /** Seconds since the entity last stood on something (for late jumps off a ledge). */
    public float airTime;

    /** Seconds left in which an early jump press still counts when landing. */
    public float jumpBufferTime;
}
