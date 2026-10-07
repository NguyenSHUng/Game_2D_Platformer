package com.hung.animation;

/**
 * Where a character's weapon hand is in one animation frame.
 * Coordinates are frame pixels with a top-left origin (as read in an image editor).
 */
public class HandAnchor {
    public float x;
    public float y;
    /** Weapon angle in this pose, degrees counter-clockwise, as seen facing right. */
    public float rotation;
}
