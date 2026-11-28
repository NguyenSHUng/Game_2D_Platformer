package com.hung.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.ai.PatrolSystem;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.entity.player.Player;
import com.hung.physics.CollisionSystem;
import com.hung.physics.PhysicsSystem;
import com.hung.world.Level;
import com.hung.world.PhysicsSettings;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The hunt frame by frame — position, velocity, facing, attack request, cooldown and engagement —
 * compared with a baseline recorded from the hunt before it moved into DirectChaseTactic.
 * Scenarios: noticing, closing in, attacking, cooldown, holding, a player running through, a hit,
 * a player out of range, a gap to jump and a room edge; every enemy class on every mode.
 * Without the baseline file the test records it and fails once, so a baseline is never made silently.
 */
class DirectChaseParityTest {

    private static final Path BASELINE = Path.of("src/test/resources/parity/direct_chase_baseline.txt");
    private static final float TILE = 16f;
    private static final float STEP = 1f / 60f;
    private static final float FLOOR_Y = 2 * TILE;
    /** How long the emulated ATTACK animation keeps isAttacking on, seconds. */
    private static final float SWING_SECONDS = 0.35f;
    private static final String[] MODES = {"EASY", "MEDIUM", "HARD"};
    private static final String[] TYPES = {"MELEE", "CRAWLER", "FLYING"};

    private record Scenario(String type, String mode, float playerDx, float playerVx, int frames,
                            boolean hasGap, boolean hasRoom, int hitFrame) {
        String name() {
            return type + "/" + mode + "/dx=" + playerDx + "/vx=" + playerVx + (hasGap ? "/gap" : "")
                + (hasRoom ? "/room" : "") + (hitFrame >= 0 ? "/hit@" + hitFrame : "");
        }
    }

    private static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<>();
        for (String type : TYPES) {
            for (String mode : MODES) {
                list.add(new Scenario(type, mode, 100f, 0f, 180, false, false, -1));   // notice, close in, attack, cooldown, hold
                list.add(new Scenario(type, mode, 150f, -96f, 150, false, false, -1)); // player runs in and past
                list.add(new Scenario(type, mode, 60f, 0f, 120, false, false, 20));    // hit mid-hunt
                list.add(new Scenario(type, mode, 400f, 0f, 60, false, false, -1));    // out of range: patrol only
                if (!type.equals("FLYING")) list.add(new Scenario(type, mode, 140f, 0f, 240, true, false, -1));
            }
            list.add(new Scenario(type, "MEDIUM", 96f, 0f, 120, false, true, -1));    // player in the doorway
        }
        return list;
    }

    @Test
    void huntMatchesTheRecordedBaseline() throws IOException {
        Map<String, List<String>> actual = new LinkedHashMap<>();
        for (Scenario scenario : scenarios()) actual.put(scenario.name(), run(scenario));

        if (!Files.exists(BASELINE)) {
            Files.createDirectories(BASELINE.getParent());
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : actual.entrySet()) {
                out.append("# ").append(entry.getKey()).append('\n');
                for (String line : entry.getValue()) out.append(line).append('\n');
            }
            Files.writeString(BASELINE, out.toString(), StandardCharsets.UTF_8);
            fail("Baseline recorded at " + BASELINE.toAbsolutePath() + "; run again to compare");
        }

        Map<String, List<String>> expected = read();
        assertEquals(expected.keySet(), actual.keySet(), "scenarios");
        for (String name : expected.keySet()) {
            List<String> want = expected.get(name);
            List<String> got = actual.get(name);
            assertEquals(want.size(), got.size(), name + ": frames");
            for (int i = 0; i < want.size(); i++) {
                if (!want.get(i).equals(got.get(i))) {
                    fail(name + " differs at frame " + i + "\n  baseline: " + want.get(i) + "\n  now:      " + got.get(i));
                }
            }
        }
    }

    /** Every scenario exercises what it is meant to: at least one attack, and engagement where the player is near. */
    @Test
    void scenariosCoverAttacksAndEngagement() {
        for (Scenario scenario : scenarios()) {
            if (scenario.playerDx() >= 400f) continue;
            List<String> lines = run(scenario);
            assertTrue(lines.stream().anyMatch(line -> line.contains(" engaged")), scenario.name());
            if (!scenario.hasGap() && !scenario.hasRoom()) {
                assertTrue(lines.stream().anyMatch(line -> line.contains(" REQ")), scenario.name());
            }
        }
    }

    @Test
    void everyHunterHasItsOwnDirectChaseTactic() {
        HunterEnemy first = new MeleeEnemy(20f);
        HunterEnemy second = new MeleeEnemy(20f);
        assertTrue(first.tactic() instanceof com.hung.ai.tactic.DirectChaseTactic);
        assertTrue(new FlyingEnemy(12f, 72f).tactic() instanceof com.hung.ai.tactic.DirectChaseTactic);
        assertTrue(new CrawlerEnemy(22f).tactic() instanceof com.hung.ai.tactic.DirectChaseTactic);
        assertNotSame(first.tactic(), second.tactic());
    }

    private static Map<String, List<String>> read() throws IOException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> current = null;
        for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            if (line.startsWith("# ")) {
                current = new ArrayList<>();
                map.put(line.substring(2), current);
            } else if (current != null && !line.isEmpty()) {
                current.add(line);
            }
        }
        return map;
    }

    // ------------------------------------------------------------------ one scenario, in the game's system order

    private static List<String> run(Scenario scenario) {
        TileGrid grid = new TileGrid(80, 14, TILE);
        for (int x = 0; x < 80; x++) grid.set(x, 1, TileType.SOLID);
        float startX = 200f;
        if (scenario.hasGap()) {
            for (int x = 15; x < 17; x++) grid.set(x, 1, TileType.EMPTY);
        }
        Level level = new Level("parity", null, grid, new PhysicsSettings(TILE, 6f, 3.5f, 6f), new Vector2(), null, new Array<Rectangle>());
        World world = new World(level);
        HunterEnemy enemy = enemy(scenario, startX);
        if (scenario.hasRoom()) {
            Room room = new Room("room_p", 0, "STANDARD", "region_1", true, new Rectangle(100f, 0f, startX + scenario.playerDx() - 100f + 2f, 224f));
            level.addRoom(room);
            enemy.setHomeRoom(room);
        }
        world.addEntity(enemy);

        Player player = new Player();
        player.getTransform().position.set(startX + scenario.playerDx(), FLOOR_Y);
        player.getTransform().velocity.x = scenario.playerVx();
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 23f;
        player.setCollider(body);

        PatrolSystem patrol = new PatrolSystem(world);
        PhysicsSystem physics = new PhysicsSystem(world, new CollisionSystem(world));
        AttackComponent attack = enemy.getAttack();
        HealthComponent health = enemy.getHealth();
        float swing = 0f;
        List<String> lines = new ArrayList<>();
        for (int frame = 0; frame < scenario.frames(); frame++) {
            if (frame == scenario.hitFrame()) {
                health.hurtTime = 0.3f;
                enemy.getTransform().velocity.x = -60f;
            }
            patrol.update();
            enemy.updateBehaviour(STEP, world, player);
            lines.add(line(frame, enemy));
            physics.update(STEP);
            // What AttackSystem and HealthSystem do with the request and the timers
            if (attack.isRequested) {
                attack.isRequested = false;
                if (!attack.isAttacking && health.hurtTime <= 0f) {
                    attack.isAttacking = true;
                    swing = SWING_SECONDS;
                }
            }
            if (attack.isAttacking) {
                swing -= STEP;
                if (swing <= 0f) attack.isAttacking = false;
            }
            health.hurtTime = Math.max(0f, health.hurtTime - STEP);
            player.getTransform().position.x += player.getTransform().velocity.x * STEP;
        }
        return lines;
    }

    /** Sizes and reaches EnemyFactory gives the real ghoul, crawler and wasp. */
    private static HunterEnemy enemy(Scenario scenario, float x) {
        HunterEnemy enemy;
        float width;
        float height;
        float walk;
        switch (scenario.type()) {
            case "MELEE" -> { enemy = new MeleeEnemy(19.36f); width = 16.8f; height = 23.4f; walk = 18f; }
            case "CRAWLER" -> { enemy = new CrawlerEnemy(22.72f); width = 34.2f; height = 13.2f; walk = 41.268f; }
            default -> { enemy = new FlyingEnemy(12.64f, FLOOR_Y + FlyingEnemy.CRUISE_HEIGHT); width = 19.8f; height = 15f; walk = 31.2f; }
        }
        boolean isFlying = enemy instanceof FlyingEnemy;
        enemy.setSkill(com.hung.core.GameMode.valueOf(scenario.mode()).enemySkill);
        enemy.getTransform().position.set(x, isFlying ? FLOOR_Y + FlyingEnemy.CRUISE_HEIGHT : FLOOR_Y);
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = walk;
        movement.isFlying = isFlying;
        enemy.setMovement(movement);
        ColliderComponent collider = new ColliderComponent();
        collider.width = width;
        collider.height = height;
        enemy.setCollider(collider);
        enemy.setAttack(new AttackComponent());
        enemy.setHealth(new HealthComponent(30));
        PatrolComponent patrol = new PatrolComponent();
        patrol.minX = x - 40f;
        patrol.maxX = x + 40f;
        enemy.setPatrol(patrol);
        return enemy;
    }

    private static String line(int frame, HunterEnemy enemy) {
        Vector2 position = enemy.getTransform().position;
        Vector2 velocity = enemy.getTransform().velocity;
        return String.format(Locale.ROOT, "%d x=%.4f y=%.4f vx=%.4f vy=%.4f %s%s cd=%.4f%s", frame,
            position.x, position.y, velocity.x, velocity.y, enemy.getTransform().isFacingRight ? "R" : "L",
            enemy.getAttack().isRequested ? " REQ" : "", enemy.cooldownLeft(), enemy.isEngaged() ? " engaged" : "");
    }
}
