package com.hung.component;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ObjectMap;
import com.hung.animation.AnimationSet;
import com.hung.animation.AnimationType;
import com.hung.animation.HandAnchor;

import java.util.EnumMap;

/**
 * Animations an entity owns and which one is playing.
 * AnimationSystem picks the current type; RenderingSystem draws the current frame.
 *
 * A character can have several stances (animation sets): the default one and, for example,
 * PISTOL (both hands on a pistol). The maps below always point at the active set's maps;
 * {@link com.hung.animation.AnimationFrames#useStance} switches them.
 */
public class AnimationComponent {

    /** Every stance this entity has, by name; always contains {@link AnimationSet#DEFAULT}. */
    public final ObjectMap<String, AnimationSet> stances = new ObjectMap<>();

    /** Name of the active stance. */
    public String stance = AnimationSet.DEFAULT;

    public EnumMap<AnimationType, Animation<TextureRegion>> animations;

    /**
     * Weapon hand position per animation: one anchor for all frames, or one per authored frame.
     * No entry means no weapon is drawn in that animation.
     */
    public EnumMap<AnimationType, HandAnchor[]> handAnchors;

    /**
     * Per-frame durations are played by repeating frames; this maps a key frame index back to
     * its authored frame (for hand anchors and attack boxes). No entry = one key frame per frame.
     */
    public EnumMap<AnimationType, int[]> keyFrameSources;

    /** Front-hand strips drawn over the held weapon, same timing as {@link #animations}; optional. */
    public EnumMap<AnimationType, Animation<TextureRegion>> overlays;

    /** Attack box per authored frame (sprite pixels, top-left origin); null where the frame doesn't hit. */
    public EnumMap<AnimationType, Rectangle[]> attackBoxes;

    public AnimationType currentType = AnimationType.IDLE;

    /** Seconds since the current animation started. */
    public float stateTime;

    /** Scale from sprite pixels to world units. */
    public float drawScale = 1f;

    /** Transparent sprite pixels below the feet, so feet line up with the ground point. */
    public float footOffset;

    /** World units per second at which the WALK animation's feet don't slide; 0 = unknown. */
    public float strideSpeed;

    public AnimationComponent() {
        AnimationSet set = new AnimationSet();
        stances.put(AnimationSet.DEFAULT, set);
        animations = set.animations;
        handAnchors = set.handAnchors;
        keyFrameSources = set.keyFrameSources;
        overlays = set.overlays;
        attackBoxes = set.attackBoxes;
    }
}
