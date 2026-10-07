package com.hung.component;

import com.badlogic.gdx.math.Vector2;

/**
 * Where an entity is and how it is moving, in a plain 2D side view.
 * World units are map pixels; y points up (LibGDX convention).
 */
public class TransformComponent {

    /** Feet point: bottom-center of the entity, in world units. */
    public final Vector2 position = new Vector2();

    /** World units per second; y is positive upwards. */
    public final Vector2 velocity = new Vector2();

    public boolean isFacingRight = true;
}
