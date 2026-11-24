package com.hung.combat.definition;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Contents of a weapon JSON file (e.g. sprites/weapons/gun/compact-military_v2.json).
 * Weapon art must face right; left-facing is done by flipping.
 */
public class WeaponDefinition {
    /** Unique id; names attachment profiles (attachments/&lt;characterId&gt;/&lt;id&gt;.json). */
    public String id;
    public String name;
    public String texture;
    /** World units per weapon pixel; render size = texture size * drawScale. */
    public float drawScale = 1f;
    /** Grip point in weapon pixels, top-left origin. Sits on the hand; rotation happens around it. */
    public Vector2 pivot;
    /** Base rotation in degrees, to correct art that isn't perfectly horizontal. */
    public float rotation;
    /** Point of a blade in weapon pixels, top-left origin; optional. Melee attacks reach at least this far. */
    public Vector2 tipAnchor;
    /** Firearms: where shots leave (just past the bore) and where casings fly out, weapon pixels. */
    public Vector2 muzzleAnchor;
    public Vector2 ejectionAnchor;
    /** MELEE, PISTOL… */
    public String category;
    /** Animation set the holder plays (PISTOL = the *_PISTOL animations); null = the default set. */
    public String stance;
    /** Firing data; null for melee weapons. */
    public FireDefinition fire;
    /** Effect strips by name: muzzleFlash, tracer, casing, impact. */
    public ObjectMap<String, EffectDefinition> effects;
}
