package com.hung.animation;

import com.badlogic.gdx.math.Rectangle;

/**
 * One frame of an animation in a character JSON file (player generator export).
 * Coordinates are sprite pixels, top-left origin, as authored.
 */
public class FrameDefinition {
    /** Milliseconds on screen; 0 = use the animation's frameDuration. */
    public int duration;
    /** ATTACK_START, HIT, RECOVER, FOOTSTEP, HURT, BODY_FALL or null. */
    public String event;
    /** Present on HIT frames: the area the attack reaches. */
    public Rectangle attackBox;
}
