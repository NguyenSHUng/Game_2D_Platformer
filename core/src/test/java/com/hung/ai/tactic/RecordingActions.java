package com.hung.ai.tactic;

import java.util.ArrayList;
import java.util.List;

/** TacticActions that only write down what a tactic asked for, in order. */
final class RecordingActions implements TacticActions {

    final List<String> calls = new ArrayList<>();

    @Override public void face(boolean isRight) { calls.add(isRight ? "face R" : "face L"); }
    @Override public void strike() { calls.add("strike"); }
    @Override public void approach(float direction) { calls.add("approach " + direction); }
    @Override public void retreat(float direction) { calls.add("retreat " + direction); }
    @Override public void hold() { calls.add("hold"); }
}
