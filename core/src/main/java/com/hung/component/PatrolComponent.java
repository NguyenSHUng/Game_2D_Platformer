package com.hung.component;

/**
 * Walk back and forth between two x positions (from the map's enemy spawn).
 */
public class PatrolComponent {

    /** Left end of the patrol, world units. */
    public float minX;

    /** Right end of the patrol, world units. */
    public float maxX;

    /** +1 walking right, -1 walking left. */
    public int direction = 1;
}
