package com.hung.component;

import com.hung.animation.AnimationType;
import com.hung.animation.HandAnchor;
import com.hung.combat.Weapon;

import java.util.EnumMap;

/**
 * A weapon an entity holds and how this entity holds it. Filled by WeaponFactory.createWeaponSlot;
 * the weapon is drawn at the hand anchor of the current animation frame.
 * Firearms also keep their runtime state here (one component per weapon, so the magazine
 * survives switching weapons). See docs/character-weapon-system.md.
 */
public class WeaponComponent {

    /** Weapon in hand; null means unarmed. */
    public Weapon equipped;

    /** Extra rotation in degrees on top of the pose, for future aiming. Positive = up when facing either way. */
    public float aimRotation;

    /** World size of the weapon as held by this entity. */
    public float width;
    public float height;

    /** Grip point in world units, measured from the weapon's bottom-left corner. */
    public float originX;
    public float originY;

    /** Hand anchors from this pair's attachment profile; animations not present use the character's own. */
    public final EnumMap<AnimationType, HandAnchor[]> handAnchors = new EnumMap<>(AnimationType.class);

    // ---------------------------------------------------------------- firearm state (unused for melee)

    /** Rounds in the magazine. */
    public int ammo;
    /** Rounds carried outside the magazine; a reload moves them in. */
    public int reserve;
    /** The reserve never holds more (pickups stop there). */
    public int maxReserve;
    /** Seconds until the next shot is allowed. */
    public float cooldown;
    /** Seconds left of a reload; 0 = not reloading. */
    public float reloadTime;
    /** Seconds left of the SHOOT pose after a shot. */
    public float shootTime;
    /** Set by input on the frame the trigger is pulled / reload is pressed. */
    public boolean isFireRequested;
    public boolean isReloadRequested;
}
