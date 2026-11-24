package com.hung.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameModeTest {

    @Test
    void everyModeHasItsOwnMapWithoutABoss() throws IOException {
        assertNotEquals(GameMode.EASY.mapPath, GameMode.MEDIUM.mapPath);
        assertNotEquals(GameMode.MEDIUM.mapPath, GameMode.HARD.mapPath);
        for (GameMode mode : GameMode.values()) {
            String map = Files.readString(Path.of("..", "assets", mode.mapPath), StandardCharsets.UTF_8);
            assertTrue(map.contains("type=\"ENEMY\""), mode.name());
            assertFalse(map.contains("mini_boss"), mode.name());
        }
    }

    @Test
    void enemiesGetStrongerFromModeToMode() {
        for (int i = 1; i < GameMode.values().length; i++) {
            var weaker = GameMode.values()[i - 1].enemySkill;
            var stronger = GameMode.values()[i].enemySkill;
            assertTrue(stronger.healthScale() > weaker.healthScale());
            assertTrue(stronger.damageScale() > weaker.damageScale());
            assertTrue(stronger.aggroScale() > weaker.aggroScale());
            assertTrue(stronger.chaseScale() > weaker.chaseScale());
            assertTrue(stronger.cooldownScale() < weaker.cooldownScale());
            assertTrue(stronger.memorySeconds() > weaker.memorySeconds());
        }
    }
}
