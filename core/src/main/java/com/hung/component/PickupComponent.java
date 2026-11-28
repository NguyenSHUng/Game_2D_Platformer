package com.hung.component;

import com.hung.combat.PickupType;

/**
 * An item in the world that the player picks up by touching it (PickupSystem). Its body is the
 * entity's ColliderComponent around the feet point. A dropped item first flies (velocity in its
 * transform) to its landing point, bounces once there, then lies still; it can't be picked mid-air.
 */
public class PickupComponent {

    public PickupType type;

    public boolean isFlying;
    public boolean hasBounced;
    /** Where it comes to rest (feet point), world units. */
    public float landingX;
    public float landingY;
}
