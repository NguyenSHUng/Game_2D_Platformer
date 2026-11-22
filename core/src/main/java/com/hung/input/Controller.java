package com.hung.input;

import com.badlogic.gdx.math.Vector2;

/**
 * Source of the player's intent (KeyboardController). Controllers only report what is wanted;
 * InputSystem decides what actually happens. Enemies have no controller: PatrolSystem and
 * EnemyBrainSystem drive them through their components.
 */
public interface Controller {

    /**
     * Desired movement, each axis in -1..1. X is left/right; Y is up/down
     * (climb ladders, crouch).
     */
    Vector2 getMovementDirection();

    /** True only on the frame a jump is requested. */
    boolean isJumpPressed();

    /** True only on the frame an attack is requested (melee swing, or a shot with a firearm). */
    boolean isAttackPressed();

    /** True while the attack button is held (full-auto firearms keep firing). */
    boolean isAttackHeld();

    /** True only on the frame a reload is requested. */
    boolean isReloadPressed();

    /** Weapon slot picked this frame (0-based), or -1. */
    int getWeaponSlotPressed();

    /** True only on the frame the next weapon is requested. */
    boolean isNextWeaponPressed();
}
