package com.hung.entity.enemy;

import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.entity.GameCharacter;
import com.hung.world.World;

/**
 * Base class of every enemy. {@link #updateBehaviour} is a template method that EnemyBrainSystem
 * calls once per frame; subclasses fill in the steps (what to think while alive, what to do
 * while hurt or dead). Patrolling itself stays in PatrolSystem; a subclass overrides it while hunting.
 */
public abstract class Enemy extends GameCharacter {

    /** Damage to the player per touch (ContactDamageSystem); docs/04-GAME-DESIGN.md. */
    public static final int CONTACT_DAMAGE = 10;

    private EnemySkill skill = EnemySkill.EASY;

    @Override
    public final Faction getFaction() {
        return Faction.ENEMY;
    }

    public EnemySkill getSkill() {
        return skill;
    }

    public void setSkill(EnemySkill skill) {
        this.skill = skill;
    }

    public int getContactDamage() {
        return skill.damage(CONTACT_DAMAGE);
    }

    /** Share of knockback this enemy ignores (0 = full knockback, 1 = none). */
    public float getKnockbackResistance() {
        return skill.knockbackResistance();
    }

    /**
     * One frame of behaviour. The order is fixed here; subclasses only change the steps.
     *
     * @param target the player while alive, else null
     */
    public final void updateBehaviour(float delta, World world, Entity target) {
        HealthComponent health = getHealth();
        if (health != null && health.isDead()) {
            whileDead();
            return;
        }
        tick(delta);
        if (health != null && health.hurtTime > 0f) {
            whileHurt();
            return;
        }
        think(world, target);
    }

    /** Decides movement and attacks for this frame. */
    protected abstract void think(World world, Entity target);

    /** Counts down this enemy's own timers (alive frames only, also while hurt). */
    protected void tick(float delta) {
    }

    protected void whileHurt() {
    }

    protected void whileDead() {
    }
}
