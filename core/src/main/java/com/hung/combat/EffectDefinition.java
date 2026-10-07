package com.hung.combat;

import com.badlogic.gdx.math.Vector2;

/**
 * One effect strip of a firearm (muzzle flash, tracer, casing, impact) from the weapon JSON:
 * a single row of equal frames, art facing right, origin in frame pixels (top-left origin).
 */
public class EffectDefinition {
    /** Asset path of the strip. */
    public String texture;
    public int frameWidth;
    public int frameHeight;
    public int frameCount = 1;
    /** Milliseconds per frame. */
    public int[] durations;
    /** The point placed on the spawn position (e.g. the muzzle side of the flash). */
    public Vector2 origin;
    public boolean loop;
}
