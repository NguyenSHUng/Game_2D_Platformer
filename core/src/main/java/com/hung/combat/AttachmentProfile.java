package com.hung.combat;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.hung.animation.HandAnchor;

/**
 * Contents of an attachment profile JSON (attachments/&lt;characterId&gt;/&lt;weaponId&gt;.json):
 * how one character holds one weapon. Overrides the character's default hand anchors and the
 * weapon's default size and pivot. See docs/character-weapon-system.md, section 12.
 */
public class AttachmentProfile {
    public String character;
    public String weapon;
    /** Weapon pixel size in character pixels (1 = same size); null = the weapon's own drawScale. */
    public Float weaponScale;
    /** Grip point in weapon pixels, top-left origin; null = the weapon's own pivot. */
    public Vector2 pivot;
    /** Keyed by AnimationType name; animations not listed use the character's default anchors. */
    public ObjectMap<String, AnimationAttachment> animations;

    public static class AnimationAttachment {
        /** Anchors detected by the Image_Processing tool: 1 entry or one per frame. Null = character defaults. */
        public Array<HandAnchor> auto;
        /** Manual corrections added on top of the base anchors: 1 entry or one per frame; optional. */
        public Array<Adjust> adjust;
    }

    public static class Adjust {
        public float dx;
        public float dy;
        public float rotation;
    }
}
