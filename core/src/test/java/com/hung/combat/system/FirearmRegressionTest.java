package com.hung.combat.system;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.hung.combat.Weapon;
import com.hung.combat.WeaponFactory;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.AnimationComponent;
import com.hung.component.AttackComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.WeaponComponent;
import com.hung.entity.Entity;
import com.hung.entity.player.Player;
import com.hung.world.Level;
import com.hung.world.TileGrid;
import com.hung.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How the player's real weapons (knife, service pistol, assault rifle) fire, reload and switch
 * today: magazine sizes, fire rate, reload times, when a reload starts, what blocks shooting, and
 * that every gun keeps its own rounds across switches. Weapons are built by WeaponFactory, as in the game.
 */
class FirearmRegressionTest {

    private static final String WEAPONS = "../assets/sprites/weapons/";
    private static final int KNIFE = 0;
    private static final int PISTOL = 1;
    private static final int RIFLE = 2;

    private World world;
    private Player player;
    private ShootingSystem shooting;
    private WeaponSwitchSystem switching;

    private static WeaponDefinition definition(String id) throws IOException {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        return json.fromJson(WeaponDefinition.class,
            Files.readString(Path.of(WEAPONS + id + "/" + id + ".json"), StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() throws IOException {
        world = new World(new Level("test", null, new TileGrid(40, 12, 16f), null, new Vector2(), null, new Array<Rectangle>()));
        player = new Player();
        player.getTransform().position.set(100f, 32f);
        player.setAnimation(new AnimationComponent());
        player.setHealth(new HealthComponent(100));
        player.setMovement(new MovementComponent());
        player.setAttack(new AttackComponent());
        Array<WeaponComponent> slots = new Array<>();
        for (String id : new String[] {"combat_knife", "service_pistol", "assault_rifle"}) {
            Weapon weapon = new Weapon(definition(id), new TextureRegion());
            slots.add(WeaponFactory.createWeaponSlot(player, weapon, null));
        }
        WeaponFactory.equipArsenal(player, slots);
        world.setPlayer(player);
        shooting = new ShootingSystem(world);
        switching = new WeaponSwitchSystem(world);
    }

    private WeaponComponent slot(int index) {
        return player.getArsenal().slots.get(index);
    }

    private void hold(int index) {
        player.getArsenal().requestedSlot = index;
        switching.update();
        assertSame(slot(index), player.getWeapon());
    }

    private void pullTrigger(float delta) {
        player.getWeapon().isFireRequested = true;
        shooting.update(delta);
    }

    private int bullets() {
        int count = 0;
        for (Entity entity : world.getEntities()) if (entity.getProjectile() != null) count++;
        return count;
    }

    @Test
    void theRealGunsKeepTheirNumbers() {
        FireDefinition pistol = slot(PISTOL).equipped.getFire();
        FireDefinition rifle = slot(RIFLE).equipped.getFire();
        assertEquals(12, pistol.magazine);
        assertEquals(1.1f, pistol.reloadSeconds, 1e-6);
        assertEquals(5f, pistol.roundsPerSecond, 1e-6);
        assertEquals(30, rifle.magazine);
        assertEquals(1.8f, rifle.reloadSeconds, 1e-6);
        assertEquals(9f, rifle.roundsPerSecond, 1e-6);
        assertTrue(rifle.isAutomatic());
        assertNull(slot(KNIFE).equipped.getFire());
    }

    @Test
    void firearmsStartWithAFullMagazineTheKnifeWithNone() {
        assertEquals(0, slot(KNIFE).ammo);
        assertEquals(12, slot(PISTOL).ammo);
        assertEquals(30, slot(RIFLE).ammo);
    }

    @Test
    void theKnifeNeverShoots() {
        hold(KNIFE);
        pullTrigger(0.016f);
        assertEquals(0, bullets());
        assertEquals(0, slot(KNIFE).ammo);
    }

    @Test
    void aShotUsesOneRoundAndWaitsForTheFireRate() {
        hold(PISTOL);
        pullTrigger(0.016f);
        assertEquals(11, slot(PISTOL).ammo);
        assertEquals(1, bullets());
        assertEquals(1f / 5f, slot(PISTOL).cooldown, 1e-6);

        pullTrigger(0.1f);
        assertEquals(11, slot(PISTOL).ammo);
        pullTrigger(0.11f);
        assertEquals(10, slot(PISTOL).ammo);
    }

    @Test
    void theLastRoundStartsAReloadThatRefillsTheMagazine() {
        hold(PISTOL);
        slot(PISTOL).ammo = 1;
        pullTrigger(0.016f);
        assertEquals(0, slot(PISTOL).ammo);
        assertEquals(1.1f, slot(PISTOL).reloadTime, 1e-6);

        shooting.update(1.0f);
        assertEquals(0, slot(PISTOL).ammo);
        shooting.update(0.11f);
        assertEquals(12, slot(PISTOL).ammo);
        assertEquals(0f, slot(PISTOL).reloadTime, 1e-6);
    }

    @Test
    void theTriggerOnAnEmptyMagazineReloadsInsteadOfShooting() {
        hold(RIFLE);
        slot(RIFLE).ammo = 0;
        pullTrigger(0.016f);
        assertEquals(0, bullets());
        assertEquals(1.8f, slot(RIFLE).reloadTime, 1e-6);
    }

    @Test
    void reloadKeyWorksOnlyOnANotFullMagazineAndBlocksShooting() {
        hold(RIFLE);
        slot(RIFLE).isReloadRequested = true;
        shooting.update(0.016f);
        assertEquals(0f, slot(RIFLE).reloadTime, 1e-6);

        slot(RIFLE).ammo = 5;
        slot(RIFLE).isReloadRequested = true;
        shooting.update(0.016f);
        assertEquals(1.8f, slot(RIFLE).reloadTime, 1e-6);
        pullTrigger(0.5f);
        assertEquals(5, slot(RIFLE).ammo);
        assertEquals(0, bullets());
        shooting.update(1.4f);
        assertEquals(30, slot(RIFLE).ammo);
    }

    @Test
    void noShootingOrReloadingWhileHurtDeadOrClimbing() {
        hold(PISTOL);
        slot(PISTOL).ammo = 5;
        player.getHealth().hurtTime = 0.3f;
        pullTrigger(0.016f);
        slot(PISTOL).isReloadRequested = true;
        shooting.update(0.016f);
        assertEquals(5, slot(PISTOL).ammo);
        assertEquals(0f, slot(PISTOL).reloadTime, 1e-6);

        player.getHealth().hurtTime = 0f;
        player.getMovement().isClimbing = true;
        pullTrigger(0.016f);
        assertEquals(5, slot(PISTOL).ammo);

        player.getMovement().isClimbing = false;
        player.getHealth().applyDamage(100);
        pullTrigger(0.016f);
        assertEquals(5, slot(PISTOL).ammo);
        assertEquals(0, bullets());
    }

    @Test
    void switchingCancelsTheReloadAndEveryGunKeepsItsRounds() {
        hold(PISTOL);
        slot(PISTOL).ammo = 5;
        slot(PISTOL).isReloadRequested = true;
        shooting.update(0.016f);
        assertTrue(slot(PISTOL).reloadTime > 0f);

        hold(RIFLE);
        assertEquals(0f, slot(PISTOL).reloadTime, 1e-6);
        assertEquals(5, slot(PISTOL).ammo);
        assertEquals(30, slot(RIFLE).ammo);
        pullTrigger(0.016f);
        assertEquals(29, slot(RIFLE).ammo);

        hold(PISTOL);
        assertEquals(5, slot(PISTOL).ammo);
        hold(RIFLE);
        assertEquals(29, slot(RIFLE).ammo);
    }

    @Test
    void noSwitchingDuringASwingOrAHit() {
        hold(PISTOL);
        player.getAttack().isAttacking = true;
        player.getArsenal().requestedSlot = RIFLE;
        switching.update();
        assertSame(slot(PISTOL), player.getWeapon());

        player.getAttack().isAttacking = false;
        player.getHealth().hurtTime = 0.3f;
        player.getArsenal().requestedSlot = RIFLE;
        switching.update();
        assertSame(slot(PISTOL), player.getWeapon());
    }
}
