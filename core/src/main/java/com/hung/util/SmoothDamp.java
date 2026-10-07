package com.hung.util;

import com.badlogic.gdx.math.MathUtils;

/**
 * One value eased toward a moving target by a critically damped spring: it accelerates and
 * then settles without overshooting, frame-rate independent (the same formula as Unity's
 * Mathf.SmoothDamp, from Game Programming Gems 4 §1.10). Holds its own velocity.
 */
public class SmoothDamp {

    /** Smooth times shorter than this would make the spring numerically stiff. */
    private static final float MIN_SMOOTH_TIME = 0.0001f;
    /** Coefficients of the polynomial approximation of exp(-x) used by the spring. */
    private static final float EXP_COEFFICIENT_2 = 0.48f;
    private static final float EXP_COEFFICIENT_3 = 0.235f;

    public float value;
    /** Units per second, updated by {@link #update}. */
    public float velocity;

    /** Jump straight to a value and stop. */
    public void reset(float value) {
        this.value = value;
        this.velocity = 0f;
    }

    /**
     * Moves toward the target and returns the new value.
     *
     * @param smoothTime roughly the seconds it takes to reach the target
     * @param maxSpeed   speed cap, units per second
     */
    public float update(float target, float smoothTime, float maxSpeed, float delta) {
        if (delta <= 0f) return value;
        float time = Math.max(MIN_SMOOTH_TIME, smoothTime);
        float omega = 2f / time;
        float x = omega * delta;
        float decay = 1f / (1f + x + EXP_COEFFICIENT_2 * x * x + EXP_COEFFICIENT_3 * x * x * x);

        float maxChange = maxSpeed * time;
        float change = MathUtils.clamp(value - target, -maxChange, maxChange);
        float clampedTarget = value - change;
        float temp = (velocity + omega * change) * delta;
        velocity = (velocity - omega * temp) * decay;
        float next = clampedTarget + (change + temp) * decay;

        // Never overshoot the real target
        if ((target - value > 0f) == (next > target)) {
            next = target;
            velocity = 0f;
        }
        value = next;
        return next;
    }
}
