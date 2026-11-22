package com.hung.component;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.hung.animation.AnimationSet;
import com.hung.animation.AnimationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AnimationComponentTest {

    private AnimationComponent animation;
    private AnimationSet pistol;

    private static Animation<TextureRegion> strip() {
        return new Animation<>(0.1f, new TextureRegion());
    }

    @BeforeEach
    void setUp() {
        animation = new AnimationComponent();
        animation.getAnimations().put(AnimationType.IDLE, strip());
        animation.getAnimations().put(AnimationType.WALK, strip());
        pistol = new AnimationSet();
        pistol.animations.put(AnimationType.IDLE, strip());
        pistol.attackBoxes.put(AnimationType.IDLE, new Rectangle[1]);
        animation.stances.put("PISTOL", pistol);
    }

    @Test
    void switchesEveryMapToTheStanceSetTogether() {
        animation.useStance("PISTOL");
        assertEquals("PISTOL", animation.getStance());
        assertSame(pistol.animations, animation.getAnimations());
        assertSame(pistol.handAnchors, animation.getHandAnchors());
        assertSame(pistol.keyFrameSources, animation.getKeyFrameSources());
        assertSame(pistol.overlays, animation.getOverlays());
        assertSame(pistol.attackBoxes, animation.getAttackBoxes());
    }

    @Test
    void fallsBackToIdleWhenTheNewSetLacksTheCurrentStateAndRestarts() {
        animation.currentType = AnimationType.WALK;
        animation.stateTime = 0.7f;
        animation.useStance("PISTOL");
        assertEquals(AnimationType.IDLE, animation.currentType);
        assertEquals(0f, animation.stateTime);
    }

    @Test
    void unknownOrNullStanceUsesTheDefaultSet() {
        AnimationSet defaults = animation.stances.get(AnimationSet.DEFAULT);
        animation.useStance("PISTOL");
        animation.useStance("RIFLE");
        assertEquals(AnimationSet.DEFAULT, animation.getStance());
        assertSame(defaults.animations, animation.getAnimations());
        animation.useStance(null);
        assertEquals(AnimationSet.DEFAULT, animation.getStance());
    }
}
