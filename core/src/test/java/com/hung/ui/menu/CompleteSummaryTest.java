package com.hung.ui.menu;

import com.hung.score.RunStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** The end-of-run page lists the run's score and statistics. */
class CompleteSummaryTest {

    @Test
    void scoreKillsChestsAndRooms() {
        assertArrayEquals(new String[] {"Score  0", "Enemies defeated  0", "Chests opened  0 / 10", "Rooms cleared  0"},
            PauseMenu.summary(new RunStats(), 10));
    }
}
