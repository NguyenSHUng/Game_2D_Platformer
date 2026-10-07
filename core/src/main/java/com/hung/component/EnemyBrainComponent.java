package com.hung.component;

import com.hung.animation.AnimationType;

/**
 * Hunting behaviour of an enemy (data only; EnemyBrainSystem acts on it). Without a brain an
 * enemy just patrols. With one it notices the player in range, turns, chases and attacks.
 */
public class EnemyBrainComponent {
    /** Notices the player this far away horizontally, world units. */
    public float aggroRange;
    /** …and at most this far above / below its own floor. */
    public float aggroHeight;
    /** Starts an attack when the player is this close horizontally. */
    public float attackRange;
    /** Seconds between two attacks. */
    public float attackCooldown;
    /** Seconds left before the next attack may start. */
    public float cooldown;
    /** Chase speed relative to the patrol speed. */
    public float chaseSpeedScale = 1f;
    /** Attack animations used in turn (MINI_BOSS: ATTACK then ATTACK_2). */
    public AnimationType[] attacks = {AnimationType.ATTACK};
    public int nextAttack;
    /** Hunting the player this frame. */
    public boolean isEngaged;

    // Flyers
    /** Hovers instead of walking; dives at the player. */
    public boolean isFlying;
    /** Floor height under the spawn, world units. */
    public float homeFloorY;
    /** Patrol altitude above that floor (body bottom), world units. */
    public float cruiseHeight;
    /** While hunting, keeps its body bottom this far above the player's feet. */
    public float huntHeight;
    /** Foot offset that puts the frame's ground row on the floor (the wreck after DEATH). */
    public float crashFootOffset;
    /** The wreck has dropped to the floor. */
    public boolean hasCrashed;
}
