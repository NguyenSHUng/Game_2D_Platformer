package com.hung.entity.enemy;

import com.badlogic.gdx.utils.Json;
import com.hung.component.AttackComponent;
import com.hung.component.HealthComponent;
import com.hung.core.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Golden values of the stats an enemy gets when it is created: every mode's EnemySkill, health and
 * attack damage per gameplay class (EnemyFactory), contact damage and knockback resistance.
 * A changed number here means the difficulty of a mode changed.
 */
class EnemyStatScalingTest {

    private static final String ENEMIES = "../assets/sprites/enemies/";

    private static EnemySkill skill(String mode) {
        return GameMode.valueOf(mode).enemySkill;
    }

    private static EnemyDefinition definition(String durabilityClass, String damageClass) {
        EnemyDefinition definition = new EnemyDefinition();
        definition.gameplay = new EnemyDefinition.Gameplay();
        definition.gameplay.durabilityClass = durabilityClass;
        definition.gameplay.damageClass = damageClass;
        return definition;
    }

    private static Enemy enemyWith(EnemySkill skill) {
        Enemy enemy = new MeleeEnemy(20f);
        enemy.setSkill(skill);
        return enemy;
    }

    /** The same parser settings as ResourceLoader.loadExternalDefinition, without Gdx.files. */
    private static EnemyDefinition load(String id) throws IOException {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        String text = Files.readString(Path.of(ENEMIES + id + "/character.json"), StandardCharsets.UTF_8);
        return json.fromJson(EnemyDefinition.class, text);
    }

    @Test
    void everyModeKeepsItsSkillValues() {
        assertEquals(new EnemySkill(1f, 1f, 1.15f, 1.1f, 0.95f, 0f, 1.5f, 0f, 0f, false), EnemySkill.EASY);
        assertEquals(new EnemySkill(1.25f, 1.25f, 1.3f, 1.3f, 0.8f, 0.2f, 3f, 96f, 0.12f, true), EnemySkill.MEDIUM);
        assertEquals(new EnemySkill(1.6f, 1.5f, 1.5f, 1.5f, 0.65f, 0.4f, 5f, 160f, 0.2f, true), EnemySkill.HARD);
    }

    @Test
    void everyModeUsesItsOwnSkill() {
        assertSame(EnemySkill.EASY, GameMode.EASY.enemySkill);
        assertSame(EnemySkill.MEDIUM, GameMode.MEDIUM.enemySkill);
        assertSame(EnemySkill.HARD, GameMode.HARD.enemySkill);
    }

    /** Health = round(base × healthScale); base LOW 20, MEDIUM 40, anything else 30. */
    @ParameterizedTest(name = "{0} durability {1} -> {2} hp")
    @CsvSource(nullValues = "NONE", value = {
        "EASY,   LOW,    20", "MEDIUM, LOW,    25", "HARD, LOW,    32",
        "EASY,   MEDIUM, 40", "MEDIUM, MEDIUM, 50", "HARD, MEDIUM, 64",
        "EASY,   NONE,   30", "MEDIUM, NONE,   38", "HARD, NONE,   48",
        "EASY,   HIGH,   30", "MEDIUM, HIGH,   38", "HARD, HIGH,   48",
    })
    void healthPerDurabilityClass(String mode, String durability, int expected) {
        HealthComponent health = EnemyFactory.createHealth(definition(durability, null), enemyWith(skill(mode)));
        assertEquals(expected, health.getMaxHealth());
        assertEquals(expected, health.getHealth());
    }

    /** Attack damage = round(base × damageScale); base MEDIUM 12, anything else 8. */
    @ParameterizedTest(name = "{0} damage {1} -> {2}")
    @CsvSource(nullValues = "NONE", value = {
        "EASY,   LOW,    8",  "MEDIUM, LOW,    10", "HARD, LOW,    12",
        "EASY,   MEDIUM, 12", "MEDIUM, MEDIUM, 15", "HARD, MEDIUM, 18",
        "EASY,   NONE,   8",  "MEDIUM, NONE,   10", "HARD, NONE,   12",
        "EASY,   HIGH,   8",  "MEDIUM, HIGH,   10", "HARD, HIGH,   12",
    })
    void attackDamagePerDamageClass(String mode, String damage, int expected) {
        AttackComponent attack = EnemyFactory.createAttack(definition(null, damage), skill(mode));
        assertEquals(expected, attack.damage);
    }

    @Test
    void noGameplayBlockUsesTheDefaults() {
        EnemyDefinition bare = new EnemyDefinition();
        assertEquals(48, EnemyFactory.createHealth(bare, enemyWith(EnemySkill.HARD)).getMaxHealth());
        assertEquals(12, EnemyFactory.createAttack(bare, EnemySkill.HARD).damage);
    }

    /** Contact damage = round(10 × damageScale); knockback resistance is the skill's, also copied into the health. */
    @ParameterizedTest(name = "{0}: contact {1}, knockback resistance {2}")
    @CsvSource({"EASY, 10, 0", "MEDIUM, 13, 0.2", "HARD, 15, 0.4"})
    void contactDamageAndKnockbackResistance(String mode, int contact, float resistance) {
        Enemy enemy = enemyWith(skill(mode));
        assertEquals(contact, enemy.getContactDamage());
        assertEquals(resistance, enemy.getKnockbackResistance(), 1e-6);
        assertEquals(resistance, EnemyFactory.createHealth(definition("LOW", null), enemy).knockbackResistance, 1e-6);
    }

    /** createHealth reads the enemy's skill, so EnemyFactory must call setSkill before it. */
    @Test
    void healthUsesTheSkillAlreadySetOnTheEnemy() {
        Enemy unset = new MeleeEnemy(20f);
        assertEquals(20, EnemyFactory.createHealth(definition("LOW", null), unset).getMaxHealth());
        assertEquals(0f, EnemyFactory.createHealth(definition("LOW", null), unset).knockbackResistance, 1e-6);
    }

    /** Walk / patrol speed = max(groundSpeed, airSpeed) × drawScale × 1.2, the same in every mode. */
    @ParameterizedTest(name = "{0} walks at {1}")
    @CsvSource({
        "small_melee_ghoul, 18", "small_melee_prison, 19.776", "small_melee_toxic, 19.776",
        "crawler_01, 41.268", "flyer_wasp_01, 31.2",
    })
    void moveSpeedOfRealEnemies(String id, float expected) throws IOException {
        assertEquals(expected, EnemyFactory.moveSpeed(load(id)), 1e-4);
    }

    /** The enemies the three maps place, with the classes their character.json files give them. */
    @ParameterizedTest(name = "{0} on {1}: {2} hp, {3} damage")
    @CsvSource({
        "small_melee_ghoul,  EASY, 30, 8",  "small_melee_ghoul,  MEDIUM, 38, 10", "small_melee_ghoul,  HARD, 48, 12",
        "small_melee_prison, EASY, 30, 8",  "small_melee_prison, MEDIUM, 38, 10", "small_melee_prison, HARD, 48, 12",
        "small_melee_toxic,  EASY, 30, 8",  "small_melee_toxic,  MEDIUM, 38, 10", "small_melee_toxic,  HARD, 48, 12",
        "crawler_01,         EASY, 20, 8",  "crawler_01,         MEDIUM, 25, 10", "crawler_01,         HARD, 32, 12",
        "flyer_wasp_01,      EASY, 20, 12", "flyer_wasp_01,      MEDIUM, 25, 15", "flyer_wasp_01,      HARD, 32, 18",
    })
    void realEnemiesPerMode(String id, String mode, int health, int damage) throws IOException {
        EnemyDefinition definition = load(id);
        assertEquals(health, EnemyFactory.createHealth(definition, enemyWith(skill(mode))).getMaxHealth());
        assertEquals(damage, EnemyFactory.createAttack(definition, skill(mode)).damage);
    }
}
