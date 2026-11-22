package com.hung.world;

/**
 * Published once on the EventBus when the player reaches the level's exit (HazardSystem):
 * the run is complete.
 */
public class ExitReachedEvent {

    /** Name of the level that was completed. */
    public String levelName;

    public ExitReachedEvent set(String levelName) {
        this.levelName = levelName;
        return this;
    }
}
