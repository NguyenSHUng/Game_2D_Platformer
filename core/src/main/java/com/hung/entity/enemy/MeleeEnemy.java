package com.hung.entity.enemy;

/**
 * Common ground hunter (small_melee_*): patrols its stretch, notices the player at close range,
 * chases slower than the crawler and swipes from claw's length instead of bumping into the player.
 */
public class MeleeEnemy extends HunterEnemy {

    private static final float AGGRO_RANGE = 120f;
    private static final float ATTACK_COOLDOWN = 1.2f;
    private static final float CHASE_SPEED_SCALE = 2.2f;
    /** Keeps it outside contact distance (both half bodies: 10.5 units) while its attack recovers. */
    private static final float HOLD_RATIO = 0.75f;

    public MeleeEnemy(float attackRange) {
        super(attackRange);
    }

    @Override
    protected float aggroRange() {
        return AGGRO_RANGE;
    }

    @Override
    protected float attackCooldown() {
        return ATTACK_COOLDOWN;
    }

    @Override
    protected float chaseSpeedScale() {
        return CHASE_SPEED_SCALE;
    }

    @Override
    protected float holdRatio() {
        return HOLD_RATIO;
    }
}
