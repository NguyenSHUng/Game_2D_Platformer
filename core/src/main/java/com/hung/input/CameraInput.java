package com.hung.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.entity.Entity;
import com.hung.rendering.CameraController;
import com.hung.world.Level;
import com.hung.world.World;

/**
 * Free camera for looking around a level (no player, or F2 in game):
 * WASD / arrows pan, Shift pans faster, Q / E or the mouse wheel zoom,
 * Home jumps back to the player spawn, Tab jumps to the next enemy.
 * While following the player, -/= (or the mouse wheel) set the gameplay zoom and 0 resets it.
 */
public class CameraInput extends InputAdapter {

    private static final int[] LEFT_KEYS = {Input.Keys.A, Input.Keys.LEFT};
    private static final int[] RIGHT_KEYS = {Input.Keys.D, Input.Keys.RIGHT};
    private static final int[] UP_KEYS = {Input.Keys.W, Input.Keys.UP};
    private static final int[] DOWN_KEYS = {Input.Keys.S, Input.Keys.DOWN};
    private static final int[] FAST_KEYS = {Input.Keys.SHIFT_LEFT, Input.Keys.SHIFT_RIGHT};
    private static final int ZOOM_IN_KEY = Input.Keys.Q;
    private static final int ZOOM_OUT_KEY = Input.Keys.E;
    private static final int RESET_KEY = Input.Keys.HOME;
    private static final int NEXT_ENEMY_KEY = Input.Keys.TAB;
    private static final int[] FOLLOW_ZOOM_IN_KEYS = {Input.Keys.EQUALS, Input.Keys.PLUS, Input.Keys.NUMPAD_ADD};
    private static final int[] FOLLOW_ZOOM_OUT_KEYS = {Input.Keys.MINUS, Input.Keys.NUMPAD_SUBTRACT};
    private static final int FOLLOW_ZOOM_RESET_KEY = Input.Keys.NUM_0;

    /** World units per second at zoom 1 (scaled by zoom so it feels the same at any zoom). */
    private static final float PAN_SPEED = 400f;
    private static final float FAST_MULTIPLIER = 3f;
    /** Zoom factor per second while a zoom key is held, and per mouse wheel notch. */
    private static final float KEY_ZOOM_RATE = 2f;
    private static final float WHEEL_ZOOM_STEP = 1.15f;

    // Reused every frame to avoid allocating in the game loop
    private final Vector2 pan = new Vector2();
    private float pendingWheel;
    private int enemyCursor = -1;

    /** Call once per frame with the real frame time. */
    public void update(float delta, CameraController camera, World world) {
        Level level = world.getLevel();
        if (Gdx.input.isKeyJustPressed(RESET_KEY)) {
            camera.centerOn(level.getPlayerSpawn().x, level.getPlayerSpawn().y);
        }
        if (Gdx.input.isKeyJustPressed(NEXT_ENEMY_KEY)) {
            focusNextEnemy(camera, world.getEntities());
        }

        pan.setZero();
        if (isAnyKeyPressed(LEFT_KEYS)) pan.x -= 1;
        if (isAnyKeyPressed(RIGHT_KEYS)) pan.x += 1;
        if (isAnyKeyPressed(UP_KEYS)) pan.y += 1;
        if (isAnyKeyPressed(DOWN_KEYS)) pan.y -= 1;
        float speed = PAN_SPEED * camera.getZoom() * (isAnyKeyPressed(FAST_KEYS) ? FAST_MULTIPLIER : 1f);
        camera.pan(pan.x * speed * delta, pan.y * speed * delta);

        if (Gdx.input.isKeyPressed(ZOOM_IN_KEY)) camera.zoomBy((float) Math.pow(KEY_ZOOM_RATE, -delta));
        if (Gdx.input.isKeyPressed(ZOOM_OUT_KEY)) camera.zoomBy((float) Math.pow(KEY_ZOOM_RATE, delta));
        if (pendingWheel != 0f) {
            camera.zoomBy((float) Math.pow(WHEEL_ZOOM_STEP, pendingWheel));
            pendingWheel = 0f;
        }
        camera.clampTo(level);
    }

    /** Gameplay zoom while the camera follows the player (call once per frame instead of update). */
    public void updateFollowZoom(float delta, CameraController camera) {
        if (isAnyKeyPressed(FOLLOW_ZOOM_IN_KEYS)) camera.adjustFollowZoom((float) Math.pow(KEY_ZOOM_RATE, -delta));
        if (isAnyKeyPressed(FOLLOW_ZOOM_OUT_KEYS)) camera.adjustFollowZoom((float) Math.pow(KEY_ZOOM_RATE, delta));
        if (Gdx.input.isKeyJustPressed(FOLLOW_ZOOM_RESET_KEY)) camera.resetFollowZoom();
        if (pendingWheel != 0f) {
            camera.adjustFollowZoom((float) Math.pow(WHEEL_ZOOM_STEP, pendingWheel));
            pendingWheel = 0f;
        }
    }

    /** Enemies are the entities with a patrol; cycles through them in map order. */
    private void focusNextEnemy(CameraController camera, Array<Entity> entities) {
        for (int step = 1; step <= entities.size; step++) {
            int index = (enemyCursor + step) % entities.size;
            if (entities.get(index).getPatrol() == null) continue;
            enemyCursor = index;
            Vector2 feet = entities.get(index).getTransform().position;
            camera.centerOn(feet.x, feet.y);
            return;
        }
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        pendingWheel += amountY;
        return true;
    }

    private static boolean isAnyKeyPressed(int[] keys) {
        for (int key : keys) {
            if (Gdx.input.isKeyPressed(key)) return true;
        }
        return false;
    }
}
