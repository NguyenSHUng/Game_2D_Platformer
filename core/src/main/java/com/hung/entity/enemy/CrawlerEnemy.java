package com.hung.entity.enemy;

/** Fast ground hunter (crawler_01): short notice range, quick attacks, chases at 1.6× its walk speed. */
public class CrawlerEnemy extends HunterEnemy {

    private static final float AGGRO_RANGE = 160f;
    private static final float ATTACK_COOLDOWN = 0.9f;
    private static final float CHASE_SPEED_SCALE = 1.6f;

    public CrawlerEnemy(float attackRange) {
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
}
