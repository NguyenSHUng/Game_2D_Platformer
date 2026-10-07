package com.hung.combat;

import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** A loaded effect strip: its animation and origin (frame pixels, top-left origin). */
public class WeaponEffect {

    public final Animation<TextureRegion> animation;
    public final float originX;
    public final float originY;
    /** Seconds until a non-looping effect has played. */
    public final float duration;

    public WeaponEffect(Animation<TextureRegion> animation, float originX, float originY) {
        this.animation = animation;
        this.originX = originX;
        this.originY = originY;
        this.duration = animation.getAnimationDuration();
    }
}
