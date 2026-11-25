package com.hung.entity.enemy;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * The parts of an enemy generator export (export/enemies/&lt;id&gt;/character.json) the game uses.
 * The file holds more (palette, events…); unknown fields are ignored when loading.
 * Pixel coordinates are top-left origin, y down, as in the file.
 *
 * Two generations of files share this format:
 *  - SMALL_MELEE: drawScale 0.5 since the 2026-11-24 restyle, ground line = anchors.feet, no durabilityClass;
 *  - CRAWLER / FLYING (phase 2): drawScale 0.5 (the player's pixel density), anchors
 *    footAnchor / pivot, per-frame attack hitboxes, ATTACK_1, and a qualitative
 *    gameplay block (classes only — the game turns them into numbers, see EnemyFactory).
 */
public class EnemyDefinition {
    public String id;
    public String name;
    /** Which enemy class plays it (EnemyFactory); any other name in the file is a loading error. */
    public Archetype archetype;
    public String category;
    /** Sprite pixels → world units; 1 for phase 1 files (field absent). */
    public float drawScale = 1f;
    public Anchors anchors;
    public SpriteSheet spriteSheet;
    /** Keyed by animation name: idle, move, attack (attack_1), defend, hit, death. */
    public ObjectMap<String, Animation> animations;
    /** Phase 2 only: role and qualitative classes, plus values measured from the animations. */
    public Gameplay gameplay;

    public enum Archetype { SMALL_MELEE, CRAWLER, FLYING }

    public static class Point {
        public int x;
        public int y;
    }

    public static class Anchors {
        /** Lowest body pixel; the ground line (phase 1). */
        public Point feet;
        /** Ground contact point (phase 2 ground archetypes). */
        public Point footAnchor;
        /** Placement point: between the feet on the ground, or the hover point of a flyer. */
        public Point pivot;
    }

    public static class SpriteSheet {
        /** Image file next to character.json. */
        public String image;
        public int frameWidth;
        public int frameHeight;
    }

    public static class Animation {
        /** IDLE, MOVE, ATTACK, ATTACK_1, DEFEND, HIT or DEATH. */
        public String state;
        public boolean loop;
        public Array<Frame> frames;
        /** Only on MOVE: speed that keeps the planted feet from sliding (or a flyer's air speed). */
        public Move move;
    }

    public static class Frame {
        public int x;
        public int y;
        public int width;
        public int height;
        /** Milliseconds this frame stays on screen. */
        public int duration;
        /** Opaque pixel bounds inside the frame. */
        public Bounds bounds;
        /** The attack lands on this frame (phase 2: with a hitbox in frame pixels). */
        public boolean attackActive;
        public Bounds hitbox;
    }

    public static class Bounds {
        public int x;
        public int y;
        public int width;
        public int height;
    }

    public static class Move {
        /** Sprite pixels per second (ground enemies). */
        public float groundSpeed;
        /** Sprite pixels per second (flyers). */
        public float airSpeed;
    }

    public static class Gameplay {
        /** LOW / MEDIUM / HIGH. */
        public String durabilityClass;
        public String damageClass;
        /** Per attack state (ATTACK, ATTACK_1): measured reach and timing. */
        public ObjectMap<String, AttackInfo> attacks;
    }

    public static class AttackInfo {
        public Reach reach;
    }

    public static class Reach {
        public float world;
    }
}
