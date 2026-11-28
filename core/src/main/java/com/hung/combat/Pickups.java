package com.hung.combat;

import com.hung.component.ArsenalComponent;
import com.hung.component.HealthComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;

/**
 * What picking up an item does to the one who touches it. Each rule returns how much it gave;
 * 0 means the item was of no use (full, dead) and must stay on the ground.
 */
public final class Pickups {

    /** Hit points a health pack restores (a quarter of the player's 100). */
    public static final int HEALTH_PACK_HEAL = 25;

    private Pickups() {
    }

    /** The rule of this kind of item: hit points healed or rounds added; 0 = of no use, leave it. */
    public static int apply(Entity target, PickupType type) {
        return type == PickupType.HEALTH_PACK ? applyHealthPack(target) : applyAmmo(target, type.ammo);
    }

    /** Heals up to {@link #HEALTH_PACK_HEAL}, never past max health; 0 when dead, full or without health. */
    public static int applyHealthPack(Entity target) {
        HealthComponent health = target.getHealth();
        return health != null ? health.heal(HEALTH_PACK_HEAL) : 0;
    }

    /**
     * Adds the type's {@link AmmoType#pickupAmount} to the reserve of the carried firearm using it, never
     * past its max reserve; 0 when dead, full or carrying no such firearm.
     */
    public static int applyAmmo(Entity target, AmmoType type) {
        HealthComponent health = target.getHealth();
        ArsenalComponent arsenal = target.getArsenal();
        if ((health != null && health.isDead()) || arsenal == null) return 0;
        for (WeaponComponent slot : arsenal.slots) {
            if (slot.equipped == null || !slot.equipped.isFirearm() || AmmoType.forStance(slot.equipped.getStance()) != type) continue;
            int added = Math.max(0, Math.min(type.pickupAmount, slot.maxReserve - slot.reserve));
            slot.reserve += added;
            return added;
        }
        return 0;
    }
}
