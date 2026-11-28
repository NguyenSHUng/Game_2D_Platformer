package com.hung.ai.tactic;

/**
 * What a tactic can make a hunting enemy do. The enemy carries each action out its own way
 * (a ground hunter jumps or stops at ledges, a flyer flies), always within its speed limit.
 */
public interface TacticActions {

    void face(boolean isRight);

    /** Starts an attack and its cooldown, standing still. */
    void strike();

    /** Closes in, direction −1 left / +1 right. */
    void approach(float direction);

    /** Backs off, slower than a chase; stands still instead where the step isn't safe. */
    void retreat(float direction);

    /** Stands still. */
    void hold();
}
