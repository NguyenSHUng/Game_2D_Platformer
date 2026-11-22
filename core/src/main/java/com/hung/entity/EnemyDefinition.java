package com.hung.entity;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * The parts of an enemy generator export (export/enemies/&lt;id&gt;/character.json) the game uses.
 * The file holds more (palette, events…); unknown fields are ignored when loading.
 * Pixel coordinates are top-left origin, y down, as in the file.
 *
 * Two generations of files share this format:
 *  - SMALL_MELEE (phase 1): drawn 1:1, ground line = anchors.feet, no gameplay block;
 *  - CRAWLER / FLYING / MINI_BOSS (phase 2): drawScale 0.5 (the player's pixel density), anchors
 *    footAnchor / pivot, per-frame attack hitboxes, ATTACK_1 / ATTACK_2, and a qualitative
 *    gameplay block (classes only — the game turns them into numbers, see EnemyFactory).
 */
public class EnemyDefinition {
    public String id;
    public String name;
    public String archetype;
    public String category;
    public Size logicalSize;
    /** Sprite pixels → world units; 1 for phase 1 files (field absent). */
    public float drawScale = 1f;
    public Anchors anchors;
    public SpriteSheet spriteSheet;
    /** Keyed by animation name: idle, move, attack (attack_1, attack_2), defend, hit, death. */
    public ObjectMap<String, Animation> animations;
    /** Phase 2 only: role and qualitative classes, plus values measured from the animations. */
    public Gameplay gameplay;
    /** Effects the animations spawn (EFFECT events), by name; MINI_BOSS only so far. */
    public ObjectMap<String, Effect> effects;

    public static class Size {
        public int width;
        public int height;
    }

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
        /** IDLE, MOVE, ATTACK, ATTACK_1, ATTACK_2, DEFEND, HIT or DEATH. */
        public String state;
        public boolean loop;
        public Array<Frame> frames;
        /** Only on MOVE: speed that keeps the planted feet from sliding (or a flyer's air speed). */
        public Move move;
        /** Frame events (FOOTSTEP, ATTACK_START, EFFECT…); the game uses the EFFECT ones. */
        public Array<Event> events;
    }

    public static class Event {
        /** Authored frame the event fires on. */
        public int frame;
        /** EFFECT, FOOTSTEP, ATTACK_START, ATTACK_END. */
        public String type;
        /** EFFECT: name of the effect in {@link EnemyDefinition#effects}. */
        public String name;
        /** EFFECT: spawn point in frame pixels (top-left origin, sprite facing right). */
        public int x;
        public int y;
    }

    /** One effect strip next to character.json: a single row of equal frames, art facing right. */
    public static class Effect {
        /** Image file, relative to character.json (effects/&lt;name&gt;.png). */
        public String image;
        public int frameWidth;
        public int frameHeight;
        public int frameCount = 1;
        /** Milliseconds per frame. */
        public int frameDuration;
        /** The point of the frame placed on the spawn point (frame pixels, top-left origin). */
        public Point origin;
        /** SYMMETRIC (never mirrored) or FLIP_WITH_FACING (mirrored around the origin when facing left). */
        public String mirror;
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
        /** COMMON_THREAT, POSITIONAL_THREAT or MAJOR_ENCOUNTER. */
        public String role;
        /** GROUND or AIR. */
        public String locomotion;
        /** LOW / MEDIUM / HIGH. */
        public String durabilityClass;
        public String damageClass;
        public String speedClass;
        /** Per attack state (ATTACK, ATTACK_1, ATTACK_2): measured reach and timing. */
        public ObjectMap<String, AttackInfo> attacks;
    }

    public static class AttackInfo {
        public Reach reach;
        public int startupMs;
        public int activeMs;
        public int recoveryMs;
    }

    public static class Reach {
        public float px;
        public float world;
    }
}
