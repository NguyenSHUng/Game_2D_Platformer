package com.hung.ai.tactic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** HitAndRunTactic's states and the actions it picks, without an enemy or LibGDX. */
class HitAndRunTacticTest {

    private static final float RANGE = 20f;
    private static final float HOLD = 15f;
    private static final float STEP = 1f / 60f;
    private static final float FAR_ENOUGH = RANGE * HitAndRunTactic.RETREAT_RANGE_RATIO;

    private final HitAndRunTactic tactic = new HitAndRunTactic();

    /** A frame with the target dx away, an attack ready or not, and a safe or blocked way back. */
    private static HuntContext frame(float dx, boolean isReady, boolean canBackOff) {
        return new HuntContext(dx, RANGE, HOLD, Math.abs(dx) <= RANGE, isReady, true, canBackOff, STEP);
    }

    private List<String> decide(HuntContext context) {
        RecordingActions actions = new RecordingActions();
        tactic.decide(context, actions);
        return actions.calls;
    }

    private void strikeFrom(float dx) {
        assertEquals(List.of(dx > 0f ? "face R" : "face L", "strike"), decide(frame(dx, true, true)));
        assertEquals(HitAndRunTactic.State.ATTACK, tactic.state());
    }

    @Test
    void approachesLikeDirectChase() {
        assertEquals(List.of("face R", "approach 1.0"), decide(frame(60f, true, true)));
        assertEquals(List.of("face L", "hold"), decide(frame(-10f, false, true)));
        assertEquals(HitAndRunTactic.State.APPROACH, tactic.state());
    }

    @Test
    void backsOffAwayFromTheTargetOnceTheSwingIsOver() {
        strikeFrom(10f);
        assertEquals(List.of("face R", "retreat -1.0"), decide(frame(10f, false, true)));
        assertEquals(HitAndRunTactic.State.RETREAT, tactic.state());

        tactic.onTargetLost();
        strikeFrom(-10f);
        assertEquals(List.of("face L", "retreat 1.0"), decide(frame(-10f, false, true)));
    }

    @Test
    void recoversOnceFarEnough() {
        strikeFrom(10f);
        decide(frame(20f, false, true));
        assertEquals(List.of("face R", "hold"), decide(frame(FAR_ENOUGH, false, true)));
        assertEquals(HitAndRunTactic.State.RECOVER, tactic.state());
    }

    @Test
    void recoversWhereBackingOffIsUnsafe() {
        strikeFrom(10f);
        assertEquals(List.of("face R", "hold"), decide(frame(10f, false, false)));
        assertEquals(HitAndRunTactic.State.RECOVER, tactic.state());
    }

    @Test
    void stopsBackingOffAfterItsTimeLimit() {
        strikeFrom(10f);
        int frames = 0;
        while (tactic.state() != HitAndRunTactic.State.RECOVER && frames < 600) {
            decide(frame(10f, false, true));
            frames++;
        }
        assertEquals(HitAndRunTactic.State.RECOVER, tactic.state());
        assertEquals(HitAndRunTactic.MAX_RETREAT_SECONDS, (frames - 1) * STEP, 2 * STEP);
    }

    @Test
    void waitsForTheCooldownThenComesAgain() {
        strikeFrom(10f);
        decide(frame(FAR_ENOUGH, false, true));
        assertEquals(List.of("face R", "hold"), decide(frame(FAR_ENOUGH, false, true)));
        assertEquals(HitAndRunTactic.State.RECOVER, tactic.state());

        assertEquals(List.of("face R", "approach 1.0"), decide(frame(FAR_ENOUGH, true, true)));
        assertEquals(HitAndRunTactic.State.APPROACH, tactic.state());
    }

    @Test
    void countersATargetThatComesIntoReachWhileRecovering() {
        strikeFrom(10f);
        decide(frame(10f, false, false));
        assertEquals(HitAndRunTactic.State.RECOVER, tactic.state());
        strikeFrom(12f);
    }

    @Test
    void aHitSendsItBackingOff() {
        decide(frame(60f, true, true));
        tactic.onHurt();
        assertEquals(HitAndRunTactic.State.RETREAT, tactic.state());
        assertEquals(List.of("face R", "retreat -1.0"), decide(frame(30f, false, true)));
    }

    @Test
    void losingTheTargetStartsOver() {
        strikeFrom(10f);
        decide(frame(10f, false, true));
        tactic.onTargetLost();
        assertEquals(HitAndRunTactic.State.APPROACH, tactic.state());
        assertEquals(List.of("face R", "approach 1.0"), decide(frame(60f, false, true)));
    }
}
