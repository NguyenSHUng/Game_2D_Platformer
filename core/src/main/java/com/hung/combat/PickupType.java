package com.hung.combat;

/** Items the player picks up by touching them; what each does is in {@link Pickups#apply}. */
public enum PickupType {
    /** Restores {@link Pickups#HEALTH_PACK_HEAL} hit points. */
    HEALTH_PACK(null),
    /** Adds pistol rounds to the reserve. */
    PISTOL_AMMO(AmmoType.PISTOL),
    /** Adds rifle rounds to the reserve. */
    RIFLE_AMMO(AmmoType.RIFLE);

    /** Ammunition it gives; null for the health pack. */
    public final AmmoType ammo;

    PickupType(AmmoType ammo) {
        this.ammo = ammo;
    }
}
