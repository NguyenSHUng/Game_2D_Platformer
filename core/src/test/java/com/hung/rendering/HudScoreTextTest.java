package com.hung.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The HUD's score line. */
class HudScoreTextTest {

    @Test
    void scoreLine() {
        StringBuilder out = new StringBuilder();
        HudRenderer.appendScore(out, 1250);
        assertEquals("SCORE 1250", out.toString());
    }
}
