package com.hung.component;

/**
 * Hit points and hurt / death state. HealthSystem advances the timers and removes
 * dead entities once their DEATH animation has played.
 */
public class HealthComponent {

    public int maxHealth;
    public int health;

    /** Seconds left of the hurt reaction (HIT animation); 0 = not hurt. Controls are locked meanwhile. */
    public float hurtTime;

    /** Seconds of invulnerability granted by each hit (0 = none). */
    public float invulnerability;

    /** Seconds left of the current invulnerability; hits are ignored while above 0. */
    public float invulnerableTime;

    public boolean isDead;
    /** 0 = full knockback when hit, 1 = none (a MINI_BOSS barely moves). */
    public float knockbackResistance;

    /** Seconds since death. */
    public float deadTime;
}
