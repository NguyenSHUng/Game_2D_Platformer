package com.hung.component;

/**
 * Hit points and hurt / death state. HealthSystem advances the timers and removes
 * dead entities once their DEATH animation has played.
 *
 * Hit points only change through the methods below, which keep 0 ≤ health ≤ maxHealth and
 * isDead ⇔ health == 0. The reaction timers stay plain fields: systems count them down.
 */
public class HealthComponent {

    private final int maxHealth;
    private int health;
    private boolean isDead;

    /** Seconds left of the hurt reaction (HIT animation); 0 = not hurt. Controls are locked meanwhile. */
    public float hurtTime;

    /** Seconds of invulnerability granted by each hit (0 = none). */
    public float invulnerability;

    /** Seconds left of the current invulnerability; hits are ignored while above 0. */
    public float invulnerableTime;

    /** 0 = full knockback when hit, 1 = none. */
    public float knockbackResistance;

    /** Seconds since death. */
    public float deadTime;

    /** Starts at full health. */
    public HealthComponent(int maxHealth) {
        if (maxHealth < 1) throw new IllegalArgumentException("maxHealth must be at least 1, was " + maxHealth);
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public boolean isDead() { return isDead; }

    /** Removes hit points (never below 0); true when this leaves the entity dead. */
    public boolean applyDamage(int amount) {
        health = Math.max(0, health - Math.max(0, amount));
        if (health == 0) isDead = true;
        return isDead;
    }

    /** Back to full health and alive (respawn). */
    public void restoreFull() {
        health = maxHealth;
        isDead = false;
    }

    /** Sets hit points within 0..maxHealth (a loaded save); 0 means dead. */
    public void setHealthClamped(int value) {
        health = Math.max(0, Math.min(maxHealth, value));
        isDead = health == 0;
    }
}
