package com.hung.animation;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * One animation entry in a character JSON file.
 * The sheet is a single row of equally sized frames, read left to right; a bare file name
 * is resolved next to the character JSON (player generator exports), otherwise from the asset root.
 */
public class AnimationDefinition {
    public AnimationType type;
    /** Animation set this belongs to (e.g. PISTOL); null = the default set. */
    public String stance;
    public String sheet;
    /**
     * Optional strip of only the front hand, same frames as {@link #sheet}: drawn over a held
     * weapon so the fingers close around the grip (body → weapon → front hand).
     */
    public String handSheet;
    public int frameCount;
    /** Seconds per frame when {@link #frames} doesn't give per-frame durations. */
    public float frameDuration;
    public Animation.PlayMode playMode = Animation.PlayMode.LOOP;
    /** Hand anchor shared by every frame; optional. */
    public HandAnchor hand;
    /** One hand anchor per frame; optional, overrides {@link #hand}. */
    public Array<HandAnchor> handFrames;
    /** Per-frame timing, events and attack boxes (player generator export); optional. */
    public Array<FrameDefinition> frames;
    /** WALK only: stride data, so playback can follow the walk speed. */
    public Gait walk;
    /** Body box for this pose in sprite pixels (top-left origin), e.g. the lower CROUCH box; optional. */
    public Rectangle hurtbox;

    public static class Gait {
        /** Sprite pixels per second at which the planted foot doesn't slide. */
        public float groundSpeed;
    }
}
