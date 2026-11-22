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
 * PISTOL (both hands on a pistol). The per-stance maps below always belong to the active set;
 * {@link #useStance} is the only place that switches them, all together.
 */
public class AnimationComponent {

    /** Every stance this entity has, by name; always contains {@link AnimationSet#DEFAULT}. */
    public final ObjectMap<String, AnimationSet> stances = new ObjectMap<>();

    private String stance = AnimationSet.DEFAULT;

    private EnumMap<AnimationType, Animation<TextureRegion>> animations;

    /**
     * Weapon hand position per animation: one anchor for all frames, or one per authored frame.
     * No entry means no weapon is drawn in that animation.
     */
    private EnumMap<AnimationType, HandAnchor[]> handAnchors;

    /**
     * Per-frame durations are played by repeating frames; this maps a key frame index back to
     * its authored frame (for hand anchors and attack boxes). No entry = one key frame per frame.
     */
    private EnumMap<AnimationType, int[]> keyFrameSources;

    /** Front-hand strips drawn over the held weapon, same timing as {@link #animations}; optional. */
    private EnumMap<AnimationType, Animation<TextureRegion>> overlays;

    /** Attack box per authored frame (sprite pixels, top-left origin); null where the frame doesn't hit. */
    private EnumMap<AnimationType, Rectangle[]> attackBoxes;

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
        activate(set);
    }

    /**
     * Makes the named stance's animations active (unknown or null: the default set). Keeps the
     * current state when the new set has it, else IDLE; the animation restarts so frames of the old
     * set don't leak.
     */
    public void useStance(String name) {
        AnimationSet set = name != null ? stances.get(name) : null;
        stance = set != null ? name : AnimationSet.DEFAULT;
        activate(set != null ? set : stances.get(AnimationSet.DEFAULT));
        if (!animations.containsKey(currentType)) currentType = AnimationType.IDLE;
        stateTime = 0f;
    }

    private void activate(AnimationSet set) {
        animations = set.animations;
        handAnchors = set.handAnchors;
        keyFrameSources = set.keyFrameSources;
        overlays = set.overlays;
        attackBoxes = set.attackBoxes;
    }

    /** Name of the active stance. */
    public String getStance() { return stance; }
    public EnumMap<AnimationType, Animation<TextureRegion>> getAnimations() { return animations; }
    public EnumMap<AnimationType, HandAnchor[]> getHandAnchors() { return handAnchors; }
    public EnumMap<AnimationType, int[]> getKeyFrameSources() { return keyFrameSources; }
    public EnumMap<AnimationType, Animation<TextureRegion>> getOverlays() { return overlays; }
    public EnumMap<AnimationType, Rectangle[]> getAttackBoxes() { return attackBoxes; }
}
