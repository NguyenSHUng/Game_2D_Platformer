package com.hung.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;

/**
 * Player controller: A/D or left/right to walk, W/S or up/down to climb ladders (S/down also
 * crouches), Space to jump, J or the left mouse button to attack / shoot (hold for full-auto),
 * R to reload, 1 / 2 / 3 to pick a weapon and Q for the next one.
 * Each action accepts several keys so bindings can be changed in one place.
 */
public class KeyboardController implements Controller {

    private static final int[] MOVE_LEFT_KEYS = {Input.Keys.A, Input.Keys.LEFT};
    private static final int[] MOVE_RIGHT_KEYS = {Input.Keys.D, Input.Keys.RIGHT};
    private static final int[] MOVE_UP_KEYS = {Input.Keys.W, Input.Keys.UP};
    private static final int[] MOVE_DOWN_KEYS = {Input.Keys.S, Input.Keys.DOWN};
    private static final int[] JUMP_KEYS = {Input.Keys.SPACE};
    private static final int[] ATTACK_KEYS = {Input.Keys.J};
    private static final int ATTACK_BUTTON = Input.Buttons.LEFT;
    private static final int[] RELOAD_KEYS = {Input.Keys.R};
    private static final int[] NEXT_WEAPON_KEYS = {Input.Keys.Q};
    /** Index = weapon slot. */
    private static final int[] WEAPON_SLOT_KEYS = {Input.Keys.NUM_1, Input.Keys.NUM_2, Input.Keys.NUM_3};

    // Reused every frame to avoid allocating in the game loop
    private final Vector2 movementDirection = new Vector2();

    @Override
    public Vector2 getMovementDirection() {
        movementDirection.setZero();
        if (isAnyKeyPressed(MOVE_LEFT_KEYS)) movementDirection.x -= 1;
        if (isAnyKeyPressed(MOVE_RIGHT_KEYS)) movementDirection.x += 1;
        if (isAnyKeyPressed(MOVE_UP_KEYS)) movementDirection.y += 1;
        if (isAnyKeyPressed(MOVE_DOWN_KEYS)) movementDirection.y -= 1;
        return movementDirection;
    }

    @Override
    public boolean isJumpPressed() {
        return isAnyKeyJustPressed(JUMP_KEYS);
    }

    @Override
    public boolean isAttackPressed() {
        return isAnyKeyJustPressed(ATTACK_KEYS) || Gdx.input.isButtonJustPressed(ATTACK_BUTTON);
    }

    @Override
    public boolean isAttackHeld() {
        return isAnyKeyPressed(ATTACK_KEYS) || Gdx.input.isButtonPressed(ATTACK_BUTTON);
    }

    @Override
    public boolean isReloadPressed() {
        return isAnyKeyJustPressed(RELOAD_KEYS);
    }

    @Override
    public int getWeaponSlotPressed() {
        for (int slot = 0; slot < WEAPON_SLOT_KEYS.length; slot++) {
            if (Gdx.input.isKeyJustPressed(WEAPON_SLOT_KEYS[slot])) return slot;
        }
        return -1;
    }

    @Override
    public boolean isNextWeaponPressed() {
        return isAnyKeyJustPressed(NEXT_WEAPON_KEYS);
    }

    private static boolean isAnyKeyJustPressed(int[] keys) {
        for (int key : keys) {
            if (Gdx.input.isKeyJustPressed(key)) return true;
        }
        return false;
    }

    private static boolean isAnyKeyPressed(int[] keys) {
        for (int key : keys) {
            if (Gdx.input.isKeyPressed(key)) return true;
        }
        return false;
    }
}
