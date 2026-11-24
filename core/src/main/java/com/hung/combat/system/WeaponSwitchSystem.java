package com.hung.combat.system;

import com.badlogic.gdx.utils.Array;
import com.hung.component.ArsenalComponent;
import com.hung.component.AttackComponent;
import com.hung.component.HealthComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Switches the weapon in hand when the arsenal asks for another slot (1 / 2 / Q): the entity's
 * WeaponComponent becomes that slot and its animations switch to the weapon's stance
 * (pistol = two-handed set). Not during a swing, a hurt reaction or death; a reload in
 * progress is cancelled (the magazine keeps its rounds).
 * Reads/Writes: ArsenalComponent, WeaponComponent, AnimationComponent.
 */
public class WeaponSwitchSystem {

    private final World world;

    public WeaponSwitchSystem(World world) {
        this.world = world;
    }

    public void update() {
        Array<Entity> entities = world.getEntities();
        for (int i = 0; i < entities.size; i++) {
            Entity entity = entities.get(i);
            ArsenalComponent arsenal = entity.getArsenal();
            if (arsenal == null) continue;

            int target = -1;
            if (arsenal.requestedSlot >= 0 && arsenal.requestedSlot < arsenal.slots.size && arsenal.requestedSlot != arsenal.current) {
                target = arsenal.requestedSlot;
            } else if (arsenal.isNextRequested && arsenal.slots.size > 1) {
                target = (arsenal.current + 1) % arsenal.slots.size;
            }
            arsenal.requestedSlot = -1;
            arsenal.isNextRequested = false;
            if (target < 0 || isBusy(entity)) continue;

            WeaponComponent previous = arsenal.slots.get(arsenal.current);
            previous.isFireRequested = false;
            previous.reloadTime = 0f;
            previous.shootTime = 0f;
            arsenal.current = target;
            WeaponComponent next = arsenal.slots.get(target);
            entity.setWeapon(next);
            if (entity.getAnimation() != null) entity.getAnimation().useStance(next.equipped.getStance());
        }
    }

    private static boolean isBusy(Entity entity) {
        AttackComponent attack = entity.getAttack();
        HealthComponent health = entity.getHealth();
        return (attack != null && attack.isAttacking) || (health != null && (health.isDead() || health.hurtTime > 0f));
    }
}
