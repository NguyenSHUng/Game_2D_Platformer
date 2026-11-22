package com.hung.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.hung.animation.AnimationSet;
import com.hung.animation.AnimationType;
import com.hung.combat.ContactDamageSystem;
import com.hung.combat.FireDefinition;
import com.hung.combat.Weapon;
import com.hung.component.AnimationComponent;
import com.hung.component.ArsenalComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.world.PhysicsSettings;
import com.hung.world.World;

import java.util.Locale;

/**
 * Character sheet (I / F1, the game pauses meanwhile): the player's stats on the left — health,
 * movement in tiles, body box, defence, position — and one card per carried weapon on the right
 * with its sprite (pixel-scaled), its numbers and comparison bars. The weapon in hand is highlighted.
 * Screen-space, drawn after the HUD.
 */
public class StatsPanel implements Disposable {

    private static final float PANEL_WIDTH = 980f;
    private static final float PANEL_HEIGHT = 480f;
    private static final float PAD = 18f;
    private static final float LINE = 19f;
    private static final float PLAYER_COLUMN = 290f;
    private static final float CARD_GAP = 10f;
    private static final float ICON_SCALE = 4f;
    private static final float ICON_BOX = 140f;
    private static final float BAR_WIDTH = 110f;
    /** Card columns, from the text start: label, value, bar, side notes. */
    private static final float VALUE_X = 75f;
    private static final float BAR_X = 140f;
    private static final float SIDE_X = 270f;
    private static final float BAR_HEIGHT = 6f;
    private static final float HP_BAR_WIDTH = 240f;

    /** Bar maximums for comparing weapons. */
    private static final float MAX_DAMAGE = 20f;
    private static final float MAX_DPS = 120f;
    private static final float MAX_RANGE_TILES = 40f;
    private static final float MAX_MAGAZINE = 30f;

    private static final Color SHADE = new Color(0f, 0f, 0f, 0.55f);
    private static final Color PANEL = new Color(0.08f, 0.09f, 0.08f, 0.94f);
    private static final Color CARD = new Color(0.13f, 0.14f, 0.12f, 1f);
    private static final Color CARD_SELECTED = new Color(0.20f, 0.24f, 0.15f, 1f);
    private static final Color BORDER = new Color(0.62f, 0.69f, 0.44f, 1f);
    private static final Color BAR_BACK = new Color(0.22f, 0.22f, 0.2f, 1f);
    private static final Color BAR_FILL = new Color(0.78f, 0.64f, 0.32f, 1f);
    private static final Color HP_FILL = new Color(0.75f, 0.16f, 0.12f, 1f);
    private static final Color TITLE = new Color(0.85f, 0.9f, 0.68f, 1f);
    private static final Color TEXT = new Color(0.86f, 0.87f, 0.82f, 1f);
    private static final Color MUTED = new Color(0.58f, 0.61f, 0.56f, 1f);

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();

    public void resize(int width, int height) {
        camera.setToOrtho(false, width, height);
    }

    public void render(Batch batch, World world) {
        Entity player = world.getPlayer();
        tileSize = world.getLevel().getGrid().getTileSize();
        float width = Math.min(PANEL_WIDTH, camera.viewportWidth - 2 * PAD);
        float height = Math.min(PANEL_HEIGHT, camera.viewportHeight - 2 * PAD);
        float x0 = (camera.viewportWidth - width) / 2f;
        float y0 = (camera.viewportHeight - height) / 2f;
        float top = y0 + height - PAD;
        float cardsX = x0 + PAD + PLAYER_COLUMN;
        float cardsWidth = width - PLAYER_COLUMN - 2 * PAD;

        ArsenalComponent arsenal = player != null ? player.getArsenal() : null;
        int slots = arsenal != null ? arsenal.slots.size : 0;
        float cardHeight = slots > 0 ? (height - 2 * PAD - LINE * 1.5f - CARD_GAP * (slots - 1)) / slots : 0f;

        // Backgrounds, cards, bars (shapes) — translucent over the paused game
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SHADE);
        shapes.rect(0, 0, camera.viewportWidth, camera.viewportHeight);
        shapes.setColor(PANEL);
        shapes.rect(x0, y0, width, height);
        if (player != null) drawPlayerBars(player, x0 + PAD, top);
        for (int i = 0; i < slots; i++) {
            float cardTop = top - LINE * 1.5f - i * (cardHeight + CARD_GAP);
            shapes.setColor(i == arsenal.current ? CARD_SELECTED : CARD);
            shapes.rect(cardsX, cardTop - cardHeight, cardsWidth, cardHeight);
            drawWeaponBars(player, arsenal.slots.get(i), cardsX + ICON_BOX + PAD, cardTop);
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(x0, y0, width, height);
        if (arsenal != null && slots > 0) {
            float cardTop = top - LINE * 1.5f - arsenal.current * (cardHeight + CARD_GAP);
            shapes.rect(cardsX, cardTop - cardHeight, cardsWidth, cardHeight);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Text and weapon sprites
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(TITLE);
        font.draw(batch, "PLAYER", x0 + PAD, top);
        font.draw(batch, "WEAPONS", cardsX, top);
        font.setColor(MUTED);
        font.draw(batch, "I / F1 close   1 / 2 / 3 select", x0 + width - PAD - 210f, top);
        if (player != null) drawPlayerText(batch, world, player, x0 + PAD, top - LINE * 1.5f);
        for (int i = 0; i < slots; i++) {
            float cardTop = top - LINE * 1.5f - i * (cardHeight + CARD_GAP);
            drawWeaponCard(batch, player, arsenal.slots.get(i), i, i == arsenal.current, cardsX, cardTop, cardHeight);
        }
        batch.end();
    }

    // ---------------------------------------------------------------- player

    private void drawPlayerBars(Entity player, float x, float top) {
        HealthComponent health = player.getHealth();
        if (health == null) return;
        float y = top - LINE * 2.6f;
        shapes.setColor(BAR_BACK);
        shapes.rect(x, y, HP_BAR_WIDTH, BAR_HEIGHT * 1.5f);
        shapes.setColor(HP_FILL);
        shapes.rect(x, y, HP_BAR_WIDTH * health.getHealth() / Math.max(1f, health.getMaxHealth()), BAR_HEIGHT * 1.5f);
    }

    private void drawPlayerText(Batch batch, World world, Entity player, float x, float y) {
        HealthComponent health = player.getHealth();
        MovementComponent movement = player.getMovement();
        ColliderComponent collider = player.getCollider();
        AnimationComponent animation = player.getAnimation();
        AttackComponent attack = player.getAttack();
        PhysicsSettings physics = world.getLevel().getPhysics();
        float tile = world.getLevel().getGrid().getTileSize();

        if (health != null) line(batch, x, y, "Health", String.format(Locale.ROOT, "%d / %d", health.getHealth(), health.getMaxHealth()));
        y -= LINE * 1.6f;
        y = section(batch, x, y, "MOVEMENT");
        if (movement != null) {
            float airTime = 2f * movement.jumpSpeed / physics.gravity;
            float jumpHeight = movement.jumpSpeed * movement.jumpSpeed / (2f * physics.gravity);
            y = line(batch, x, y, "Walk speed", tiles(movement.moveSpeed / tile) + " / s");
            y = line(batch, x, y, "Jump height", tiles(jumpHeight / tile));
            y = line(batch, x, y, "Jump distance", tiles(movement.moveSpeed * airTime / tile));
            y = line(batch, x, y, "Air time", String.format(Locale.ROOT, "%.2f s", airTime));
            y = line(batch, x, y, "Climb speed", tiles(movement.climbSpeed / tile) + " / s");
        }
        y -= LINE * 0.4f;
        y = section(batch, x, y, "BODY & DEFENCE");
        if (collider != null) {
            y = line(batch, x, y, "Hit box", String.format(Locale.ROOT, "%.1f x %.1f u", collider.width, collider.height));
            if (collider.crouchHeight > 0f) y = line(batch, x, y, "Crouched", String.format(Locale.ROOT, "%.1f u tall", collider.crouchHeight));
        }
        if (health != null) y = line(batch, x, y, "Invulnerable", String.format(Locale.ROOT, "%.1f s after a hit", health.invulnerability));
        y = line(batch, x, y, "Enemy touch", "-" + ContactDamageSystem.ENEMY_CONTACT_DAMAGE + " HP");
        if (attack != null) y = line(batch, x, y, "Melee", attack.damage + " dmg per swing");
        y -= LINE * 0.4f;
        y = section(batch, x, y, "STATE");
        Vector2 feet = player.getTransform().position;
        Vector2 respawn = world.getRespawnPoint();
        y = line(batch, x, y, "Position", String.format(Locale.ROOT, "tile %d, %d", (int) (feet.x / tile), (int) (feet.y / tile)));
        y = line(batch, x, y, "Respawn", String.format(Locale.ROOT, "tile %d, %d", (int) (respawn.x / tile), (int) (respawn.y / tile)));
        if (animation != null) line(batch, x, y, "Stance", AnimationSet.DEFAULT.equals(animation.getStance()) ? "default" : animation.getStance().toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- weapons

    /** Per-weapon numbers: damage, damage per second, fire rate / speed, range, magazine. */
    private static final class WeaponNumbers {
        float damage;
        float dps;
        float rangeTiles;
        int magazine;
    }

    private final WeaponNumbers numbers = new WeaponNumbers();
    /** World units per tile of the current level (set each render). */
    private float tileSize = 16f;

    private WeaponNumbers numbersOf(Entity player, WeaponComponent slot) {
        Weapon weapon = slot.equipped;
        WeaponNumbers n = numbers;
        if (weapon.isFirearm()) {
            FireDefinition fire = weapon.getFire();
            n.damage = fire.damage;
            n.dps = fire.damage * fire.roundsPerSecond;
            n.rangeTiles = fire.rangeTiles;
            n.magazine = fire.magazine;
        } else {
            AttackComponent attack = player.getAttack();
            float swing = swingSeconds(player);
            n.damage = attack != null ? attack.damage : 0f;
            n.dps = swing > 0f ? n.damage / swing : 0f;
            n.rangeTiles = reachUnits(slot) / tileSize;
            n.magazine = 0;
        }
        return n;
    }

    private void drawWeaponBars(Entity player, WeaponComponent slot, float x, float cardTop) {
        if (slot.equipped == null) return;
        WeaponNumbers n = numbersOf(player, slot);
        float y = cardTop - LINE * 2.2f;
        bar(x, y, n.damage / MAX_DAMAGE);
        bar(x, y - LINE, n.dps / MAX_DPS);
        bar(x, y - 2 * LINE, n.rangeTiles / MAX_RANGE_TILES);
        if (n.magazine > 0) bar(x, y - 3 * LINE, n.magazine / MAX_MAGAZINE);
    }

    private void bar(float x, float y, float fraction) {
        shapes.setColor(BAR_BACK);
        shapes.rect(x + BAR_X, y - BAR_HEIGHT - 4f, BAR_WIDTH, BAR_HEIGHT);
        shapes.setColor(BAR_FILL);
        shapes.rect(x + BAR_X, y - BAR_HEIGHT - 4f, BAR_WIDTH * Math.max(0f, Math.min(1f, fraction)), BAR_HEIGHT);
    }

    private void drawWeaponCard(Batch batch, Entity player, WeaponComponent slot, int index, boolean isInHand,
                                float x, float cardTop, float cardHeight) {
        Weapon weapon = slot.equipped;
        if (weapon == null) return;
        WeaponNumbers n = numbersOf(player, slot);

        // Sprite, pixel-scaled and centred in its box
        TextureRegion region = weapon.getRegion();
        float scale = Math.min(ICON_SCALE, (ICON_BOX - PAD) / region.getRegionWidth());
        float w = region.getRegionWidth() * scale;
        float h = region.getRegionHeight() * scale;
        batch.setColor(Color.WHITE);
        batch.draw(region, x + (ICON_BOX - w) / 2f, cardTop - cardHeight / 2f - h / 2f, w, h);

        float tx = x + ICON_BOX + PAD;
        float y = cardTop - LINE * 0.6f;
        font.setColor(isInHand ? TITLE : TEXT);
        font.draw(batch, "[" + (index + 1) + "] " + weapon.getName() + (isInHand ? "   - in hand" : ""), tx, y);
        font.setColor(MUTED);
        font.draw(batch, describe(weapon, slot), tx + SIDE_X, y);
        y -= LINE * 1.2f;
        y = statLine(batch, tx, y, "Damage", String.format(Locale.ROOT, "%.0f", n.damage));
        y = statLine(batch, tx, y, "DPS", String.format(Locale.ROOT, "%.0f", n.dps));
        y = statLine(batch, tx, y, weapon.isFirearm() ? "Range" : "Reach", tiles(n.rangeTiles));
        if (weapon.isFirearm()) {
            FireDefinition fire = weapon.getFire();
            statLine(batch, tx, y, "Magazine", slot.ammo + " / " + fire.magazine);
            float side = tx + SIDE_X;
            float sy = cardTop - LINE * 1.8f;
            font.setColor(TEXT);
            font.draw(batch, String.format(Locale.ROOT, "%s, %.0f rounds / s", fire.isAutomatic() ? "Full-auto" : "Semi-auto", fire.roundsPerSecond), side, sy);
            font.draw(batch, String.format(Locale.ROOT, "Reload %.1f s", fire.reloadSeconds), side, sy - LINE);
            font.draw(batch, String.format(Locale.ROOT, "Bullet %.0f tiles / s", fire.projectileSpeedTiles), side, sy - 2 * LINE);
            font.draw(batch, String.format(Locale.ROOT, "Spread +/-%.1f deg", fire.spreadDegrees), side, sy - 3 * LINE);
        } else {
            font.setColor(TEXT);
            font.draw(batch, String.format(Locale.ROOT, "Swing %.2f s", swingSeconds(player)), tx + SIDE_X, cardTop - LINE * 1.8f);
            font.draw(batch, "Breaks cracked walls", tx + SIDE_X, cardTop - LINE * 2.8f);
        }
    }

    private static String describe(Weapon weapon, WeaponComponent slot) {
        if (!weapon.isFirearm()) return "melee, one hand";
        String hands = "two hands";
        if (slot.reloadTime > 0f) return hands + " - RELOADING";
        return hands;
    }

    private float statLine(Batch batch, float x, float y, String label, String value) {
        font.setColor(MUTED);
        font.draw(batch, label, x, y);
        font.setColor(TEXT);
        font.draw(batch, value, x + VALUE_X, y);
        return y - LINE;
    }

    // ---------------------------------------------------------------- helpers

    private float section(Batch batch, float x, float y, String title) {
        font.setColor(TITLE);
        font.draw(batch, title, x, y);
        return y - LINE;
    }

    private float line(Batch batch, float x, float y, String label, String value) {
        font.setColor(MUTED);
        font.draw(batch, label, x, y);
        font.setColor(TEXT);
        font.draw(batch, value, x + 120f, y);
        return y - LINE;
    }

    private static String tiles(float value) {
        return String.format(Locale.ROOT, "%.1f tiles", value);
    }

    /** Length of the knife swing (ATTACK of the default set), seconds. */
    private static float swingSeconds(Entity player) {
        AnimationComponent animation = player.getAnimation();
        if (animation == null) return 0f;
        AnimationSet base = animation.stances.get(AnimationSet.DEFAULT);
        return base.animations.containsKey(AnimationType.ATTACK) ? base.animations.get(AnimationType.ATTACK).getAnimationDuration() : 0f;
    }

    /** How far the blade tip is from the hand, world units. */
    private static float reachUnits(WeaponComponent slot) {
        Vector2 tip = slot.equipped.getTipAnchor();
        if (tip == null) return 0f;
        TextureRegion region = slot.equipped.getRegion();
        float scale = slot.width / region.getRegionWidth();
        Vector2 pivot = slot.equipped.getPivot();
        return Math.abs(tip.x - pivot.x) * scale;
    }

    @Override
    public void dispose() {
        shapes.dispose();
        font.dispose();
    }
}
