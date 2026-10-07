package com.hung.component;

import com.badlogic.gdx.graphics.Color;

/** A short-lived entity (muzzle flash, impact, casing): removed when its time runs out. */
public class EffectComponent {

    /** Seconds left before removal. */
    public float timeLeft;

    /** Stop sliding / spinning once on the ground (shell casings). */
    public boolean settlesOnGround;

    /** Optional light the effect gives off (muzzle flash, impact), world units; 0 = none. */
    public float lightRadius;
    public final Color lightColor = new Color(Color.WHITE);
    /** The light fades out over this many seconds (the effect's own length). */
    public float lightDuration;
}
