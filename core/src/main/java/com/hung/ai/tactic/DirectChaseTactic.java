package com.hung.ai.tactic;

/** Faces the target, strikes when it can, else runs at it until the hold distance, else waits. */
public class DirectChaseTactic implements HuntTactic {

    @Override
    public void decide(HuntContext context, TacticActions actions) {
        float dx = context.dx();
        actions.face(dx > 0f);
        if (context.isInReach() && context.canStrikeNow()) {
            actions.strike();
        } else if (Math.abs(dx) > context.holdDistance() && context.canMoveTowardTarget()) {
            actions.approach(Math.signum(dx));
        } else {
            actions.hold();
        }
    }
}
