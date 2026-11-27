package com.hung.animation;

/**
 * Animation states an entity can show. Names match "type" in character JSON files.
 * Enemy generator states map onto these (its MOVE is WALK here).
 * A character may have several animation sets (stances): the default one and, for example,
 * the two-handed PISTOL stance; each set maps these same states to its own frames.
 */
public enum AnimationType {
    IDLE,
    WALK,
    JUMP,
    ATTACK,
    DEFEND,
    HIT,
    DEATH,
    CROUCH,
    SHOOT
}
