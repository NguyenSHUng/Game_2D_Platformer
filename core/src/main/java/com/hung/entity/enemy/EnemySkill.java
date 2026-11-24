package com.hung.entity.enemy;

/**
 * How strong and how clever the enemies of a game mode are (GameMode.enemySkill). The scales multiply
 * the numbers of each enemy class; the rest switches behaviours of {@link HunterEnemy}.
 *
 * @param healthScale         hit points
 * @param damageScale         attack and contact damage
 * @param aggroScale          notice range, horizontal and vertical
 * @param chaseScale          chase speed
 * @param cooldownScale       seconds between attacks (smaller = more attacks)
 * @param knockbackResistance share of knockback ignored, 0–1
 * @param memorySeconds       keeps hunting this long after losing sight of the player or being hit from afar
 * @param alertRange          wakes other hunters this close when it notices the player, world units; 0 = hunts alone
 * @param aimLeadSeconds      attacks when the player is about to be in reach this far ahead
 * @param canJump             ground hunters jump gaps and steps in their way, and onto low platforms
 */
public record EnemySkill(float healthScale, float damageScale, float aggroScale, float chaseScale, float cooldownScale,
                         float knockbackResistance, float memorySeconds, float alertRange, float aimLeadSeconds,
                         boolean canJump) {

    public static final EnemySkill EASY = new EnemySkill(1f, 1f, 1.15f, 1.1f, 0.95f, 0f, 1.5f, 0f, 0f, false);
    public static final EnemySkill MEDIUM = new EnemySkill(1.25f, 1.25f, 1.3f, 1.3f, 0.8f, 0.2f, 3f, 96f, 0.12f, true);
    public static final EnemySkill HARD = new EnemySkill(1.6f, 1.5f, 1.5f, 1.5f, 0.65f, 0.4f, 5f, 160f, 0.2f, true);

    public int health(int base) {
        return Math.round(base * healthScale);
    }

    public int damage(int base) {
        return Math.round(base * damageScale);
    }
}
