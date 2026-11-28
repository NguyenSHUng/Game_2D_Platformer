package com.hung.ai.tactic;

/**
 * Strikes, backs off while its attack recovers, then comes again:
 * <pre>
 * APPROACH ─strike→ ATTACK ─swing over→ RETREAT ─far enough / blocked / too long→ RECOVER
 *    ▲                                    ▲                                         │
 *    └──────── cooldown over, target out of reach ──────────────────────────────────┤
 *                                         └── hit (any state)    in reach and ready ─┘→ ATTACK
 * </pre>
 * APPROACH closes in exactly like {@link DirectChaseTactic}. While backing off and recovering it keeps
 * facing the target. Losing the target starts over at APPROACH.
 */
public class HitAndRunTactic implements HuntTactic {

    /** Backs off until the target is this many attack ranges away. */
    static final float RETREAT_RANGE_RATIO = 1.8f;
    /** Gives up backing off after this many seconds (a wall behind, a target that follows). */
    static final float MAX_RETREAT_SECONDS = 1f;

    enum State { APPROACH, ATTACK, RETREAT, RECOVER }

    private State state = State.APPROACH;
    private float retreatTime;

    State state() {
        return state;
    }

    @Override
    public void decide(HuntContext context, TacticActions actions) {
        float dx = context.dx();
        actions.face(dx > 0f);
        // decide() is not called during the swing: the first call after a strike means it is over
        if (state == State.ATTACK) startRetreat();
        if (state == State.RETREAT) retreat(context, actions);
        if (state == State.RECOVER) recover(context, actions);
        if (state == State.APPROACH) approach(context, actions);
    }

    @Override
    public void onHurt() {
        startRetreat();
    }

    @Override
    public void onTargetLost() {
        state = State.APPROACH;
    }

    private void approach(HuntContext context, TacticActions actions) {
        float dx = context.dx();
        if (context.isInReach() && context.canStrikeNow()) {
            strike(actions);
        } else if (Math.abs(dx) > context.holdDistance() && context.canMoveTowardTarget()) {
            actions.approach(Math.signum(dx));
        } else {
            actions.hold();
        }
    }

    private void retreat(HuntContext context, TacticActions actions) {
        retreatTime += context.delta();
        boolean isFarEnough = Math.abs(context.dx()) >= context.attackRange() * RETREAT_RANGE_RATIO;
        if (isFarEnough || !context.canBackOff() || retreatTime > MAX_RETREAT_SECONDS) {
            state = State.RECOVER;
            return;
        }
        actions.retreat(-Math.signum(context.dx()));
    }

    private void recover(HuntContext context, TacticActions actions) {
        if (!context.canStrikeNow()) {
            actions.hold();
            return;
        }
        if (context.isInReach()) {
            strike(actions);
        } else {
            state = State.APPROACH;
        }
    }

    private void strike(TacticActions actions) {
        actions.strike();
        state = State.ATTACK;
    }

    private void startRetreat() {
        state = State.RETREAT;
        retreatTime = 0f;
    }
}
