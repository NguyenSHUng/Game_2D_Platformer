package com.hung.combat;

/**
 * Kinds of ammunition, one per firearm stance, with the reserve numbers (rounds outside the
 * magazine). The weapon files only know the magazine; these numbers are the game's own.
 */
public enum AmmoType {
    PISTOL("PISTOL", 36, 72, 24),
    RIFLE("RIFLE", 60, 150, 30);

    /** Stance of the firearms that use it ({@link Weapon#getStance()}). */
    public final String stance;
    /** Rounds in reserve when the weapon is handed out (the magazine starts full on top). */
    public final int initialReserve;
    /** The reserve never holds more. */
    public final int maxReserve;
    /** Rounds an ammo pickup of this kind adds to the reserve. */
    public final int pickupAmount;

    AmmoType(String stance, int initialReserve, int maxReserve, int pickupAmount) {
        this.stance = stance;
        this.initialReserve = initialReserve;
        this.maxReserve = maxReserve;
        this.pickupAmount = pickupAmount;
    }

    /** The ammunition of a firearm with this stance; null for melee and unknown stances. */
    public static AmmoType forStance(String stance) {
        for (AmmoType type : values()) if (type.stance.equals(stance)) return type;
        return null;
    }
}
