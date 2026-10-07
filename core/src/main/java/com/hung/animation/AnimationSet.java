package com.hung.animation;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

import java.util.EnumMap;

/**
 * The animations of one stance (the default set, or e.g. PISTOL: two hands on a pistol).
 * AnimationComponent points at the active set; switching weapons switches sets.
 */
public class AnimationSet {

    public static final String DEFAULT = "DEFAULT";

    public final EnumMap<AnimationType, Animation<TextureRegion>> animations = new EnumMap<>(AnimationType.class);
    public final EnumMap<AnimationType, HandAnchor[]> handAnchors = new EnumMap<>(AnimationType.class);
    public final EnumMap<AnimationType, Animation<TextureRegion>> overlays = new EnumMap<>(AnimationType.class);
    public final EnumMap<AnimationType, int[]> keyFrameSources = new EnumMap<>(AnimationType.class);
    public final EnumMap<AnimationType, Rectangle[]> attackBoxes = new EnumMap<>(AnimationType.class);

    /** Fills every state this set lacks with the fallback set's (HIT / DEATH shared across stances). */
    public void inheritMissing(AnimationSet fallback) {
        for (AnimationType type : fallback.animations.keySet()) {
            if (animations.containsKey(type)) continue;
            animations.put(type, fallback.animations.get(type));
            copy(fallback.handAnchors, handAnchors, type);
            copy(fallback.overlays, overlays, type);
            copy(fallback.keyFrameSources, keyFrameSources, type);
            copy(fallback.attackBoxes, attackBoxes, type);
        }
    }

    private static <T> void copy(EnumMap<AnimationType, T> from, EnumMap<AnimationType, T> to, AnimationType type) {
        T value = from.get(type);
        if (value != null) to.put(type, value);
    }
}
