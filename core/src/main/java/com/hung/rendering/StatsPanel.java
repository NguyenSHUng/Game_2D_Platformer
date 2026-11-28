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
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.hung.animation.AnimationSet;
import com.hung.animation.AnimationType;
import com.hung.combat.Weapon;
import com.hung.combat.definition.FireDefinition;
import com.hung.component.AnimationComponent;
import com.hung.component.ArsenalComponent;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.entity.enemy.Enemy;
import com.hung.world.PhysicsSettings;
import com.hung.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Character sheet (I / F1, the game pauses meanwhile).
 *  - Left: the player as cards — health (value and bar), movement, body and combat, location.
 *  - Right, top: the carried weapons as small rows (sprite, key, name); the one in hand is highlighted.
 *  - Right, below: the details of the weapon in hand only — damage, range / reach, magazine / reserve
 *    (firearms, from its WeaponComponent like the HUD) and reload / swing time.
 * Keys 1 / 2 / 3 change the weapon in hand (GameScreen runs the normal weapon switch), so the details follow.
 * Screen-space, drawn after the HUD.
 */
public class StatsPanel implements Disposable {

    private static final float PANEL_WIDTH = 900f;
    private static final float PANEL_HEIGHT = 440f;
    private static final float PAD = 18f;
    private static final float LINE = 19f;
    private static final float PLAYER_COLUMN = 300f;
    /** Player column cards: inner padding, gap between cards, title row, text height, health card and its bar. */
    private static final float CARD_PAD = 8f;
    private static final float GROUP_GAP = 8f;
    private static final float GROUP_TITLE = 22f;
    private static final float TEXT_HEIGHT = 14f;
    private static final float HEALTH_CARD = 50f;
    private static final float HP_BAR_HEIGHT = 10f;

    /** Weapon list rows. */
    private static final float ROW_HEIGHT = 38f;
    private static final float ROW_GAP = 6f;
    private static final float ROW_ICON_WIDTH = 64f;
    private static final float ROW_ICON_HEIGHT = 26f;
    /** Details card. */
    private static final float DETAIL_GAP = 14f;
    private static final float DETAIL_ICON_BOX = 150f;
    private static final float DETAIL_ICON_SCALE = 4f;
    private static final float DETAIL_LINE = 26f;
    private static final float VALUE_X = 110f;
    private static final float BAR_X = 200f;
    private static final float BAR_WIDTH = 120f;
    private static final float BAR_HEIGHT = 6f;

    /** Bar maximums for comparing weapons. */
    private static final float MAX_DAMAGE = 20f;
    private static final float MAX_RANGE_TILES = 40f;

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

    // ---------------------------------------------------------------- weapon details (no drawing, tested)

    /** One line of the details card: label, value, a 0..1 bar (negative = none) or else an optional muted note in its place. */
    record DetailRow(String label, String value, String note, float bar) { }

    /**
     * The four facts of the weapon in hand. Firearms read their FireDefinition and the slot's rounds;
     * the knife takes its numbers from the player (melee damage, blade reach, swing length).
     */
    static List<DetailRow> details(WeaponComponent slot, float meleeDamage, float reachTiles, float swingSeconds) {
        List<DetailRow> rows = new ArrayList<>(4);
        Weapon weapon = slot.equipped;
        if (weapon.isFirearm()) {
            FireDefinition fire = weapon.getFire();
            rows.add(new DetailRow("Damage", String.valueOf(fire.damage), null, fire.damage / MAX_DAMAGE));
            rows.add(new DetailRow("Range", tiles(fire.rangeTiles), null, fire.rangeTiles / MAX_RANGE_TILES));
            rows.add(new DetailRow("Mag / Res", ammoText(slot), capacityText(slot, fire), -1f));
            rows.add(new DetailRow("Reload", String.format(Locale.ROOT, "%.1f s", fire.reloadSeconds), null, -1f));
        } else {
            rows.add(new DetailRow("Damage", String.format(Locale.ROOT, "%.0f", meleeDamage), null, meleeDamage / MAX_DAMAGE));
            rows.add(new DetailRow("Reach", tiles(reachTiles), null, reachTiles / MAX_RANGE_TILES));
            rows.add(new DetailRow("Swing", String.format(Locale.ROOT, "%.2f s", swingSeconds), null, -1f));
        }
        return rows;
    }

    /** "8 / 36": rounds in the magazine / reserve rounds, the same text as the HUD's (HudRenderer.appendAmmo). */
    static String ammoText(WeaponComponent slot) {
        StringBuilder out = new StringBuilder();
        HudRenderer.appendAmmo(out, slot);
        return out.toString();
    }

    /** "max 12 / 72": what the magazine and the reserve can hold. */
    static String capacityText(WeaponComponent slot, FireDefinition fire) {
        return "max " + fire.magazine + " / " + slot.maxReserve;
    }

    /** Under the name: how the weapon is used, and RELOADING / NO AMMO when that applies. */
    static String kind(WeaponComponent slot) {
        Weapon weapon = slot.equipped;
        if (!weapon.isFirearm()) return "Melee, one hand - breaks cracked walls";
        String kind = (weapon.getFire().isAutomatic() ? "Full-auto" : "Semi-auto") + ", two hands";
        if (slot.reloadTime > 0f) return kind + " - RELOADING";
        if (slot.ammo == 0 && slot.reserve == 0) return kind + " - NO AMMO";
        return kind;
    }

    // ---------------------------------------------------------------- drawing

    /** World units per tile of the current level (set each render). */
    private float tileSize = 16f;

    public void render(Batch batch, World world) {
        Entity player = world.getPlayer();
        tileSize = world.getLevel().getGrid().getTileSize();
        float width = Math.min(PANEL_WIDTH, camera.viewportWidth - 2 * PAD);
        float height = Math.min(PANEL_HEIGHT, camera.viewportHeight - 2 * PAD);
        float x0 = (camera.viewportWidth - width) / 2f;
        float y0 = (camera.viewportHeight - height) / 2f;
        float top = y0 + height - PAD;
        float rightX = x0 + PAD + PLAYER_COLUMN;
        float rightWidth = width - PLAYER_COLUMN - 2 * PAD;

        ArsenalComponent arsenal = player != null ? player.getArsenal() : null;
        int slots = arsenal != null ? arsenal.slots.size : 0;
        float listTop = top - LINE * 1.5f;
        float detailTop = listTop - slots * ROW_HEIGHT - Math.max(0, slots - 1) * ROW_GAP - DETAIL_GAP;
        float detailBottom = y0 + PAD;
        WeaponComponent inHand = slots > 0 ? arsenal.slots.get(arsenal.current) : null;
        List<DetailRow> rows = inHand != null && inHand.equipped != null
            ? details(inHand, meleeDamage(player), reachUnits(inHand) / tileSize, swingSeconds(player)) : List.of();
        float rowsTop = detailTop - LINE * 3.2f;
        float playerX = x0 + PAD;
        float playerWidth = PLAYER_COLUMN - PAD;
        List<PlayerGroup> groups = player != null
            ? playerGroups(player, world.getLevel().getPhysics(), tileSize, world.getRespawnPoint()) : List.of();

        // Backgrounds, rows, bars (shapes), translucent over the paused game
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SHADE);
        shapes.rect(0, 0, camera.viewportWidth, camera.viewportHeight);
        shapes.setColor(PANEL);
        shapes.rect(x0, y0, width, height);
        if (player != null) drawPlayerCards(player, groups, playerX, playerWidth, listTop);
        for (int i = 0; i < slots; i++) {
            float rowTop = listTop - i * (ROW_HEIGHT + ROW_GAP);
            shapes.setColor(i == arsenal.current ? CARD_SELECTED : CARD);
            shapes.rect(rightX, rowTop - ROW_HEIGHT, rightWidth, ROW_HEIGHT);
        }
        if (inHand != null) {
            shapes.setColor(CARD);
            shapes.rect(rightX, detailBottom, rightWidth, detailTop - detailBottom);
            float textX = rightX + DETAIL_ICON_BOX + PAD;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).bar() >= 0f) bar(textX + BAR_X, rowsTop - i * DETAIL_LINE, rows.get(i).bar());
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(x0, y0, width, height);
        if (inHand != null) {
            float rowTop = listTop - arsenal.current * (ROW_HEIGHT + ROW_GAP);
            shapes.rect(rightX, rowTop - ROW_HEIGHT, rightWidth, ROW_HEIGHT);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Text and weapon sprites
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(TITLE);
        font.draw(batch, "PLAYER", x0 + PAD, top);
        font.draw(batch, "WEAPONS", rightX, top);
        font.setColor(MUTED);
        font.draw(batch, "1 / 2 / 3 select   I close", rightX, top, rightWidth, Align.right, false);
        if (player != null) drawPlayerText(batch, player, groups, playerX, playerWidth, listTop);
        for (int i = 0; i < slots; i++) {
            drawWeaponRow(batch, arsenal.slots.get(i), i, i == arsenal.current, rightX, listTop - i * (ROW_HEIGHT + ROW_GAP), rightWidth);
        }
        if (inHand != null && inHand.equipped != null) drawDetails(batch, inHand, rows, rightX, detailTop, detailBottom, rowsTop);
        batch.end();
    }

    // ---------------------------------------------------------------- player

    /** One labelled value of the player column. */
    record PlayerLine(String label, String value) { }

    /** A titled card of the player column. */
    record PlayerGroup(String title, List<PlayerLine> lines) { }

    /**
     * The player's facts below the health card, in three cards: movement (in tiles), body and combat,
     * location. Missing components leave their lines out.
     */
    static List<PlayerGroup> playerGroups(Entity player, PhysicsSettings physics, float tile, Vector2 respawn) {
        List<PlayerGroup> groups = new ArrayList<>(3);
        MovementComponent movement = player.getMovement();
        if (movement != null) {
            float airTime = 2f * movement.jumpSpeed / physics.gravity;
            float jumpHeight = movement.jumpSpeed * movement.jumpSpeed / (2f * physics.gravity);
            groups.add(new PlayerGroup("MOVEMENT", List.of(
                new PlayerLine("Walk / climb", String.format(Locale.ROOT, "%.1f / %.1f tiles/s", movement.moveSpeed / tile, movement.climbSpeed / tile)),
                new PlayerLine("Jump", String.format(Locale.ROOT, "%.1f high, %.1f far", jumpHeight / tile, movement.moveSpeed * airTime / tile)),
                new PlayerLine("Air time", String.format(Locale.ROOT, "%.2f s", airTime)))));
        }
        List<PlayerLine> body = new ArrayList<>(4);
        ColliderComponent collider = player.getCollider();
        if (collider != null) {
            String box = String.format(Locale.ROOT, "%.0f x %.0f u", collider.width, collider.height);
            if (collider.crouchHeight > 0f) box += String.format(Locale.ROOT, ", crouch %.0f", collider.crouchHeight);
            body.add(new PlayerLine("Hit box", box));
        }
        HealthComponent health = player.getHealth();
        if (health != null) body.add(new PlayerLine("Invulnerable", String.format(Locale.ROOT, "%.1f s after a hit", health.invulnerability)));
        body.add(new PlayerLine("Enemy touch", "-" + Enemy.CONTACT_DAMAGE + " HP"));
        AttackComponent attack = player.getAttack();
        if (attack != null) body.add(new PlayerLine("Melee", attack.damage + " dmg per swing"));
        groups.add(new PlayerGroup("BODY & COMBAT", body));

        List<PlayerLine> location = new ArrayList<>(3);
        Vector2 feet = player.getTransform().position;
        location.add(new PlayerLine("Tile", (int) (feet.x / tile) + ", " + (int) (feet.y / tile)));
        location.add(new PlayerLine("Respawn", (int) (respawn.x / tile) + ", " + (int) (respawn.y / tile)));
        AnimationComponent animation = player.getAnimation();
        if (animation != null) {
            location.add(new PlayerLine("Stance", AnimationSet.DEFAULT.equals(animation.getStance())
                ? "default" : animation.getStance().toLowerCase(Locale.ROOT)));
        }
        groups.add(new PlayerGroup("LOCATION", location));
        return groups;
    }

    /** Height of a group card: padding, its title, its lines (LibGDX text hangs down from its y). */
    static float groupHeight(PlayerGroup group) {
        return 2f * CARD_PAD + GROUP_TITLE + Math.max(0, group.lines().size() - 1) * LINE + TEXT_HEIGHT;
    }

    /** Total height of the player cards, so the column can be checked against the panel. */
    static float playerColumnHeight(boolean hasHealth, List<PlayerGroup> groups) {
        float height = hasHealth ? HEALTH_CARD + GROUP_GAP : 0f;
        for (PlayerGroup group : groups) height += groupHeight(group) + GROUP_GAP;
        return height - GROUP_GAP;
    }

    /** Room the player cards have under the panel title: the panel minus its padding and the title row. */
    static float playerColumnRoom() {
        return PANEL_HEIGHT - 2f * PAD - LINE * 1.5f;
    }

    /** Space between the bottom of the health text and the top of the health bar (must stay positive). */
    static float healthTextToBarGap() {
        return (HEALTH_CARD - CARD_PAD - HP_BAR_HEIGHT) - (CARD_PAD + TEXT_HEIGHT);
    }

    /** Health card and group cards (shapes pass): backgrounds and the health bar, from cardsTop down. */
    private void drawPlayerCards(Entity player, List<PlayerGroup> groups, float x, float width, float cardsTop) {
        float y = cardsTop;
        HealthComponent health = player.getHealth();
        if (health != null) {
            shapes.setColor(CARD);
            shapes.rect(x, y - HEALTH_CARD, width, HEALTH_CARD);
            float barX = x + CARD_PAD;
            float barWidth = width - 2f * CARD_PAD;
            float barY = y - HEALTH_CARD + CARD_PAD;
            shapes.setColor(BAR_BACK);
            shapes.rect(barX, barY, barWidth, HP_BAR_HEIGHT);
            shapes.setColor(HP_FILL);
            shapes.rect(barX, barY, barWidth * health.getHealth() / Math.max(1f, health.getMaxHealth()), HP_BAR_HEIGHT);
            y -= HEALTH_CARD + GROUP_GAP;
        }
        for (PlayerGroup group : groups) {
            float h = groupHeight(group);
            shapes.setColor(CARD);
            shapes.rect(x, y - h, width, h);
            y -= h + GROUP_GAP;
        }
    }

    /** Health card and group cards (text pass): titles in olive, labels muted on the left, values on the right. */
    private void drawPlayerText(Batch batch, Entity player, List<PlayerGroup> groups, float x, float width, float cardsTop) {
        float y = cardsTop;
        float inner = width - 2f * CARD_PAD;
        HealthComponent health = player.getHealth();
        if (health != null) {
            font.setColor(TITLE);
            font.draw(batch, "HEALTH", x + CARD_PAD, y - CARD_PAD);
            font.setColor(TEXT);
            font.draw(batch, health.getHealth() + " / " + health.getMaxHealth(), x + CARD_PAD, y - CARD_PAD, inner, Align.right, false);
            y -= HEALTH_CARD + GROUP_GAP;
        }
        for (PlayerGroup group : groups) {
            float lineY = y - CARD_PAD;
            font.setColor(TITLE);
            font.draw(batch, group.title(), x + CARD_PAD, lineY);
            lineY -= GROUP_TITLE;
            for (PlayerLine line : group.lines()) {
                font.setColor(MUTED);
                font.draw(batch, line.label(), x + CARD_PAD, lineY);
                font.setColor(TEXT);
                font.draw(batch, line.value(), x + CARD_PAD, lineY, inner, Align.right, false);
                lineY -= LINE;
            }
            y -= groupHeight(group) + GROUP_GAP;
        }
    }

    // ---------------------------------------------------------------- weapons

    /** A list row: sprite, key and name; nothing else. */
    private void drawWeaponRow(Batch batch, WeaponComponent slot, int index, boolean isInHand, float x, float rowTop, float width) {
        Weapon weapon = slot.equipped;
        if (weapon == null) return;
        float middle = rowTop - ROW_HEIGHT / 2f;
        drawSprite(batch, weapon.getRegion(), x + PAD / 2f, middle, ROW_ICON_WIDTH, ROW_ICON_HEIGHT, DETAIL_ICON_SCALE);
        float textY = middle + font.getCapHeight() / 2f;
        font.setColor(isInHand ? TITLE : MUTED);
        font.draw(batch, "[" + (index + 1) + "]", x + PAD + ROW_ICON_WIDTH, textY);
        font.setColor(isInHand ? TITLE : TEXT);
        font.draw(batch, weapon.getName(), x + PAD + ROW_ICON_WIDTH + 34f, textY);
        if (isInHand) {
            font.setColor(TITLE);
            font.draw(batch, "IN HAND", x, textY, width - PAD, Align.right, false);
        }
    }

    /** The weapon in hand: big sprite on the left, name, how it is used, then its rows with bars. */
    private void drawDetails(Batch batch, WeaponComponent slot, List<DetailRow> rows, float x, float detailTop,
                             float detailBottom, float rowsTop) {
        Weapon weapon = slot.equipped;
        float boxHeight = detailTop - detailBottom;
        drawSprite(batch, weapon.getRegion(), x + PAD / 2f, detailBottom + boxHeight / 2f,
            DETAIL_ICON_BOX - PAD, boxHeight - 2 * PAD, DETAIL_ICON_SCALE);

        float textX = x + DETAIL_ICON_BOX + PAD;
        float y = detailTop - LINE * 0.8f;
        font.setColor(TITLE);
        font.draw(batch, weapon.getName(), textX, y);
        font.setColor(MUTED);
        font.draw(batch, kind(slot), textX, y - LINE);
        for (int i = 0; i < rows.size(); i++) {
            DetailRow row = rows.get(i);
            float rowY = rowsTop - i * DETAIL_LINE;
            font.setColor(MUTED);
            font.draw(batch, row.label(), textX, rowY);
            font.setColor(TEXT);
            font.draw(batch, row.value(), textX + VALUE_X, rowY);
            if (row.note() != null) {
                font.setColor(MUTED);
                font.draw(batch, row.note(), textX + BAR_X, rowY);
            }
        }
    }

    /** Draws a weapon sprite at a whole-number scale (crisp pixels), centred in a box at (x, middle). */
    private static void drawSprite(Batch batch, TextureRegion region, float x, float middle, float boxWidth, float boxHeight, float maxScale) {
        float fit = Math.min(boxWidth / region.getRegionWidth(), boxHeight / region.getRegionHeight());
        float scale = Math.max(1f, (float) Math.floor(Math.min(maxScale, fit)));
        float w = region.getRegionWidth() * scale;
        float h = region.getRegionHeight() * scale;
        batch.setColor(Color.WHITE);
        batch.draw(region, x + (boxWidth - w) / 2f, middle - h / 2f, w, h);
    }

    /** A bar beside a detail row whose text baseline is at y. */
    private void bar(float x, float y, float fraction) {
        shapes.setColor(BAR_BACK);
        shapes.rect(x, y - BAR_HEIGHT - 4f, BAR_WIDTH, BAR_HEIGHT);
        shapes.setColor(BAR_FILL);
        shapes.rect(x, y - BAR_HEIGHT - 4f, BAR_WIDTH * Math.max(0f, Math.min(1f, fraction)), BAR_HEIGHT);
    }

    // ---------------------------------------------------------------- helpers

    private static String tiles(float value) {
        return String.format(Locale.ROOT, "%.1f tiles", value);
    }

    private static float meleeDamage(Entity player) {
        AttackComponent attack = player.getAttack();
        return attack != null ? attack.damage : 0f;
    }

    /** Length of the knife swing (ATTACK of the default set), seconds. */
    private static float swingSeconds(Entity player) {
        AnimationComponent animation = player.getAnimation();
        if (animation == null) return 0f;
        AnimationSet base = animation.stances.get(AnimationSet.DEFAULT);
        return base.animations.containsKey(AnimationType.ATTACK) ? base.animations.get(AnimationType.ATTACK).getAnimationDuration() : 0f;
    }

    /** How far the blade tip is from the hand, world units (0 for a firearm). */
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
