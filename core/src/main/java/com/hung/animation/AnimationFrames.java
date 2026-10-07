package com.hung.animation;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.hung.component.AnimationComponent;
import com.hung.component.WeaponComponent;

/**
 * Read-only helpers over an AnimationComponent shared by the systems that look at the
 * current frame (rendering, attacks, health).
 */
public final class AnimationFrames {

    private AnimationFrames() {
    }

    /** Key frame index of the current animation (what is drawn). */
    public static int keyFrameIndex(AnimationComponent animation) {
        return animation.animations.get(animation.currentType).getKeyFrameIndex(animation.stateTime);
    }

    /** Authored frame index of the current animation (for hand anchors, attack boxes). */
    public static int authoredFrame(AnimationComponent animation) {
        int keyFrame = keyFrameIndex(animation);
        int[] sources = animation.keyFrameSources.get(animation.currentType);
        return sources != null ? sources[keyFrame] : keyFrame;
    }

    /**
     * Weapon hand of the current frame: the attachment profile's anchors for this animation,
     * else the character's own; one shared anchor or one per authored frame. Null = no hand.
     */
    public static HandAnchor handAnchor(WeaponComponent weapon, AnimationComponent animation) {
        HandAnchor[] anchors = weapon != null ? weapon.handAnchors.get(animation.currentType) : null;
        if (anchors == null) anchors = animation.handAnchors.get(animation.currentType);
        if (anchors == null || anchors.length == 0) return null;
        return anchors.length == 1 ? anchors[0] : anchors[Math.min(authoredFrame(animation), anchors.length - 1)];
    }

    /**
     * Makes the named stance's animations active (falls back to the default set). Keeps the
     * current state; the animation restarts so frames of the old set don't leak.
     */
    public static void useStance(AnimationComponent animation, String stance) {
        AnimationSet set = animation.stances.get(stance != null ? stance : AnimationSet.DEFAULT);
        if (set == null) set = animation.stances.get(AnimationSet.DEFAULT);
        animation.stance = stance != null && animation.stances.containsKey(stance) ? stance : AnimationSet.DEFAULT;
        animation.animations = set.animations;
        animation.handAnchors = set.handAnchors;
        animation.keyFrameSources = set.keyFrameSources;
        animation.overlays = set.overlays;
        animation.attackBoxes = set.attackBoxes;
        if (!animation.animations.containsKey(animation.currentType)) animation.currentType = AnimationType.IDLE;
        animation.stateTime = 0f;
    }

    /** True once a non-looping animation has shown its last frame for its full time. */
    public static boolean isFinished(AnimationComponent animation, AnimationType type) {
        Animation<TextureRegion> frames = animation.animations.get(type);
        return frames == null || (animation.currentType == type && frames.isAnimationFinished(animation.stateTime));
    }

    /** Length of an animation in seconds; 0 when the entity doesn't have it. */
    public static float duration(AnimationComponent animation, AnimationType type) {
        Animation<TextureRegion> frames = animation.animations.get(type);
        return frames == null ? 0f : frames.getAnimationDuration();
    }
}
