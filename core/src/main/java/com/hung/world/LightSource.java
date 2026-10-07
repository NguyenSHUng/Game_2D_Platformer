package com.hung.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * A point light of the level (the map's "lights" layer, written by the map generator):
 * lamps, monitors, fires, toxic tanks… Render-only: lights never change gameplay.
 */
public class LightSource {

    /** How the brightness changes over time; same formulas as the map generator's preview. */
    public enum Flicker { NONE, SOFT, PULSE, STRONG }

    public float x;
    public float y;
    /** Reach in world units (map pixels). */
    public float radius;
    public final Color color = new Color(Color.WHITE);
    public float intensity = 1f;
    public Flicker flicker = Flicker.NONE;
    /** 0..1 offset so neighbouring lights don't flicker in sync. */
    public float phase;
    /** lamp, monitor, fire, toxic, tank, electric, exit (informational). */
    public String kind;

    /** Brightness multiplier at time t (seconds). */
    public float flickerAt(float t) {
        switch (flicker) {
            case SOFT:
                return 0.9f + 0.1f * MathUtils.sin(t * 3f + phase * 20f);
            case PULSE:
                return 0.7f + 0.3f * MathUtils.sin(t * 2f + phase * 10f);
            case STRONG: {
                float n = hash((int) Math.floor(t * 12f + phase * 100f));
                return n < 0.18f ? 0.12f : 0.8f + 0.2f * n;
            }
            default:
                return 1f;
        }
    }

    public static Flicker parseFlicker(String name) {
        if (name == null) return Flicker.NONE;
        switch (name) {
            case "soft": return Flicker.SOFT;
            case "pulse": return Flicker.PULSE;
            case "strong": return Flicker.STRONG;
            default: return Flicker.NONE;
        }
    }

    /** Integer → 0..1, the generator's hash (so a broken light stutters the same way). */
    private static float hash(int i) {
        int h = (i ^ 0x9e3779b9) * 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return (h & 0xffffffffL) / 4294967296f;
    }
}
