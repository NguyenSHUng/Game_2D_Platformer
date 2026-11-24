package com.hung.combat;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;

/**
 * A loaded weapon: its image and default size/pivot from the weapon JSON.
 * Shared by every entity holding it; how a specific holder sizes and grips it
 * is resolved into that holder's WeaponComponent.
 */
public class Weapon {

    private final String id;
    private final String name;
    private final TextureRegion region;
    private final float drawScale;
    private final Vector2 pivot;
    private final float rotation;
    private final Vector2 tipAnchor;
    private final Vector2 muzzleAnchor;
    private final Vector2 ejectionAnchor;
    private final String stance;
    private final FireDefinition fire;
    private final ObjectMap<String, WeaponEffect> effects = new ObjectMap<>();

    public Weapon(WeaponDefinition definition, Texture texture) {
        this(definition, new TextureRegion(texture));
    }

    /** From an already-made region (also lets headless tests build weapons without GL). */
    public Weapon(WeaponDefinition definition, TextureRegion region) {
        this.id = definition.id;
        this.name = definition.name;
        this.region = region;
        this.drawScale = definition.drawScale;
        this.pivot = new Vector2(definition.pivot);
        this.rotation = definition.rotation;
        this.tipAnchor = definition.tipAnchor != null ? new Vector2(definition.tipAnchor) : null;
        this.muzzleAnchor = definition.muzzleAnchor != null ? new Vector2(definition.muzzleAnchor) : null;
        this.ejectionAnchor = definition.ejectionAnchor != null ? new Vector2(definition.ejectionAnchor) : null;
        this.stance = definition.stance;
        this.fire = definition.fire;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public TextureRegion getRegion() { return region; }
    /** Default world units per weapon pixel. */
    public float getDrawScale() { return drawScale; }
    /** Default grip point in weapon pixels, top-left origin. */
    public Vector2 getPivot() { return pivot; }
    public float getRotation() { return rotation; }
    /** Blade tip in weapon pixels (top-left origin), or null. */
    public Vector2 getTipAnchor() { return tipAnchor; }
    /** Firearm muzzle / ejection port in weapon pixels (top-left origin), or null. */
    public Vector2 getMuzzleAnchor() { return muzzleAnchor; }
    public Vector2 getEjectionAnchor() { return ejectionAnchor; }
    /** Animation set of the holder; null = default. */
    public String getStance() { return stance; }
    /** Firing data, or null for melee weapons. */
    public FireDefinition getFire() { return fire; }
    public boolean isFirearm() { return fire != null; }
    /** Loaded effect by name (muzzleFlash, tracer, casing, impact), or null. */
    public WeaponEffect getEffect(String name) { return effects.get(name); }
    public void putEffect(String name, WeaponEffect effect) { effects.put(name, effect); }
}
