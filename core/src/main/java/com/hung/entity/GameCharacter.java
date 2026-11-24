package com.hung.entity;

import com.hung.component.HealthComponent;

/**
 * Base class of everything that fights: the player and every enemy. Adds to {@link Entity} the
 * questions combat asks about a character (which side it is on, what it may do), answered by each
 * subclass. Other objects (doors, bullets, effects) stay plain entities.
 */
public abstract class GameCharacter extends Entity {

    /** The side a character fights on; characters on different sides hurt each other. */
    public enum Faction { PLAYER, ENEMY }

    public abstract Faction getFaction();

    public final boolean isHostileTo(GameCharacter other) {
        return other != this && other.getFaction() != getFaction();
    }

    /** True when both are characters on opposite sides; plain entities are never hostile. */
    public static boolean areHostile(Entity a, Entity b) {
        return a instanceof GameCharacter first && b instanceof GameCharacter second && first.isHostileTo(second);
    }

    /** Whether this character's melee attacks clear BREAKABLE tiles. */
    public boolean canBreakWalls() {
        return false;
    }

    public boolean isAlive() {
        HealthComponent health = getHealth();
        return health == null || !health.isDead();
    }
}
