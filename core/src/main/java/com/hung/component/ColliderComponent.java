package com.hung.component;

/**
 * Axis-aligned collision box against the tile map, centered on the feet point:
 * spans x ± width / 2 and y .. y + height (crouchHeight while crouching).
 */
public class ColliderComponent {

    /** Box width in world units. */
    public float width;

    /** Box height in world units. */
    public float height;

    /** Box height while crouching; 0 = same as height. */
    public float crouchHeight;
}
