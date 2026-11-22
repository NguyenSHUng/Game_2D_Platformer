package com.hung.component;

import com.hung.animation.AnimationType;
import com.hung.combat.WeaponEffect;

import java.util.EnumMap;

/**
 * Effects an enemy's animations spawn on given frames (from the EFFECT events of its
 * character.json): the MINI_BOSS roar, slam shockwave, claw slash, hit splash and death burst.
 * EnemyEffectSystem watches the authored frame and spawns each cue once when it is reached.
 */
public class EnemyEffectsComponent {

    /** Cues per animation, by authored frame; null where a frame spawns nothing. */
    public final EnumMap<AnimationType, Cue[][]> cues = new EnumMap<>(AnimationType.class);

    /** What was shown last update (to spot a new frame or a restarted animation). */
    public AnimationType lastType;
    public int lastFrame = -1;
    public float lastStateTime;

    /** One effect spawned on one frame. */
    public static class Cue {
        public final WeaponEffect effect;
        /** Spawn point in frame pixels (top-left origin, sprite facing right). */
        public final float x;
        public final float y;
        /** Directional art: mirrored with the enemy when it faces left. */
        public final boolean isMirrored;

        public Cue(WeaponEffect effect, float x, float y, boolean isMirrored) {
            this.effect = effect;
            this.x = x;
            this.y = y;
            this.isMirrored = isMirrored;
        }
    }
}
