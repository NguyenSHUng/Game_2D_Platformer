package com.hung.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.hung.component.HealthComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.world.World;

/**
 * Screen-space overlay drawn after the world: the player's health bar and weapon, the score, the
 * "E - Open Chest" prompt while a closed chest is in reach (InteractionPrompt), and the free camera's keys.
 * Uses its own pixel camera so it doesn't move or zoom with the game camera.
 */
public class HudRenderer implements Disposable {

    private static final float MARGIN = 16f;
    private static final float BAR_WIDTH = 200f;
    private static final float BAR_HEIGHT = 14f;
    private static final float BAR_BORDER = 2f;
    private static final float TEXT_GAP = 8f;
    /** The interaction prompt sits centred this high above the bottom edge. */
    private static final float PROMPT_Y = 72f;
    private static final Color BAR_BACK_COLOR = new Color(0.08f, 0.08f, 0.08f, 0.85f);
    private static final Color BAR_FILL_COLOR = new Color(0.75f, 0.16f, 0.12f, 1f);
    private static final Color TEXT_COLOR = new Color(0.85f, 0.85f, 0.8f, 1f);
    private static final String RELOADING = "  RELOADING";
    private static final String NO_AMMO = "  NO AMMO";
    private static final String FREE_CAMERA_HINT =
        "FREE CAMERA  WASD pan  Shift fast  Q/E zoom  Tab next enemy  Home spawn  F2 back to player";
    private static final String NO_PLAYER_HINT =
        "NO PLAYER  WASD pan  Shift fast  Q/E zoom  Tab next enemy  Home spawn  F3 debug";

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    // Reused every frame to avoid allocating in the game loop
    private final StringBuilder healthText = new StringBuilder();
    private final StringBuilder weaponText = new StringBuilder();
    private final StringBuilder scoreText = new StringBuilder();
    private final InteractionPrompt prompt = new InteractionPrompt();

    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    public void render(Batch batch, World world, int score, boolean isCameraFree) {
        Entity player = world.getPlayer();
        HealthComponent health = player != null ? player.getHealth() : null;
        float top = camera.viewportHeight - MARGIN;

        if (health != null) {
            float fill = health.getMaxHealth() > 0 ? (float) health.getHealth() / health.getMaxHealth() : 0f;
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.setProjectionMatrix(camera.combined);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(BAR_BACK_COLOR);
            shapes.rect(MARGIN - BAR_BORDER, top - BAR_HEIGHT - BAR_BORDER,
                BAR_WIDTH + 2f * BAR_BORDER, BAR_HEIGHT + 2f * BAR_BORDER);
            shapes.setColor(BAR_FILL_COLOR);
            shapes.rect(MARGIN, top - BAR_HEIGHT, BAR_WIDTH * fill, BAR_HEIGHT);
            shapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(TEXT_COLOR);
        if (health != null) {
            healthText.setLength(0);
            healthText.append("HP ").append(health.getHealth()).append(" / ").append(health.getMaxHealth());
            font.draw(batch, healthText, MARGIN + BAR_WIDTH + TEXT_GAP, top);
        }
        WeaponComponent weapon = player != null ? player.getWeapon() : null;
        if (weapon != null && weapon.equipped != null) {
            weaponText.setLength(0);
            appendWeapon(weaponText, weapon);
            font.draw(batch, weaponText, MARGIN, top - BAR_HEIGHT - TEXT_GAP);
        }
        if (player != null) {
            scoreText.setLength(0);
            appendScore(scoreText, score);
            font.draw(batch, scoreText, 0f, top, camera.viewportWidth - MARGIN, Align.right, false);
        }
        // Not with the free camera: there E zooms and the player gets no input
        if (!isCameraFree && prompt.chestInReach(world) != null) {
            font.draw(batch, InteractionPrompt.OPEN_CHEST, 0f, PROMPT_Y, camera.viewportWidth, Align.center, false);
        }
        String hint = player == null ? NO_PLAYER_HINT : isCameraFree ? FREE_CAMERA_HINT : null;
        if (hint != null) font.draw(batch, hint, MARGIN, MARGIN + font.getCapHeight());
        batch.end();
    }

    /** "SCORE 1250" (top right). */
    static void appendScore(StringBuilder out, int score) {
        out.append("SCORE ").append(score);
    }

    /** "magazine / reserve" of a firearm slot: the HUD and the character sheet both show these two numbers. */
    static void appendAmmo(StringBuilder out, WeaponComponent weapon) {
        out.append(weapon.ammo).append(" / ").append(weapon.reserve);
    }

    /** "Name", and for a firearm "  magazine / reserve" plus RELOADING or NO AMMO (magazine and reserve empty). */
    static void appendWeapon(StringBuilder out, WeaponComponent weapon) {
        out.append(weapon.equipped.getName());
        if (!weapon.equipped.isFirearm()) return;
        out.append("  ");
        appendAmmo(out, weapon);
        if (weapon.reloadTime > 0f) out.append(RELOADING);
        else if (weapon.ammo == 0 && weapon.reserve == 0) out.append(NO_AMMO);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        font.dispose();
    }
}
