package com.hung.ai.tactic;

/**
 * How a hunting enemy fights: what it does on a frame where it is engaged and not mid-swing.
 * The enemy keeps sight, memory, jumping and its own way of moving; the tactic only picks actions.
 * Tactics may keep state, so every enemy has its own instance.
 */
public interface HuntTactic {

    void decide(HuntContext context, TacticActions actions);

    /** The enemy was hit: decide() isn't called while it reels. */
    default void onHurt() {
    }

    /** The enemy stopped hunting (target gone, out of its room, forgotten). */
    default void onTargetLost() {
    }
}
