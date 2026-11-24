package com.hung.component;

/**
 * Melee attack state. The attack animation's frames with an attack box decide when and
 * where the attack lands; see AttackSystem.
 */
public class AttackComponent {
    /** Damage dealt to each target the attack box touches. */
    public int damage;

    /** Set by input on the frame the attack button is pressed. */
    public boolean isRequested;

    /** Playing the ATTACK animation. */
    public boolean isAttacking;

    /** This swing already landed (one hit per swing). */
    public boolean hasHit;
}
