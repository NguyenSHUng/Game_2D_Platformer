package com.hung.entity.player;

import com.badlogic.gdx.utils.Array;
import com.hung.animation.definition.AnimationDefinition;

/**
 * Contents of a player character JSON file (e.g. sprites/players/&lt;id&gt;/&lt;id&gt;.json, written by
 * build_asset/build_character's tools/export-game.mjs). Extra fields in the file are ignored.
 * Walk / jump speeds are not here: they come from the map's physics contract.
 */
public class CharacterDefinition {
    /** Unique id; names the folder of this character's attachment profiles (attachments/&lt;id&gt;/). */
    public String id;
    public String name;
    public int frameWidth;
    public int frameHeight;
    public float drawScale = 1f;
    public float footOffset;
    /** Collision box in sprite pixels (scaled by drawScale); 0 = derived from the frame size. */
    public float colliderWidth;
    public float colliderHeight;
    /**
     * SEPARATE: the strips are the body without a weapon (plus front-hand strips) and the weapon is a
     * separate sprite held at handFrames — the game equips Constants.DEFAULT_PLAYER_WEAPON when the map
     * names none. BAKED (or missing): the weapon is drawn into the strips.
     */
    public String weaponLayer;
    public Array<AnimationDefinition> animations;
}
