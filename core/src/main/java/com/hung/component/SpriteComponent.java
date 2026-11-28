package com.hung.component;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * A simple animated sprite placed by an origin point (effects, projectiles, shell casings),
 * unlike characters which stand on their feet. Drawn by RenderingSystem, advanced by EffectSystem.
 */
public class SpriteComponent {

    public Animation<TextureRegion> animation;
    public float stateTime;

    /** Point of the frame placed on the entity position, in frame pixels (top-left origin). */
    public float originX;
    public float originY;

    /** World units per sprite pixel. */
    public float scale = 1f;

    /** Degrees counter-clockwise (art faces right); flipX mirrors it to face left. */
    public float rotation;
    public boolean flipX;

    /** Colour the frame is multiplied by (white = as drawn). */
    public final Color tint = new Color(Color.WHITE);
}
