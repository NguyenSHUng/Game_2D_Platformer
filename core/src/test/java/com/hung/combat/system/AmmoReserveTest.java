package com.hung.combat.system;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.hung.combat.AmmoType;
import com.hung.combat.Pickups;
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

/**
 * Reserve ammunition: each firearm carries rounds outside its magazine, a reload moves them in
 * (same reload time as before), an empty reserve means no reload, and pickups fill the reserve up to its max.
 */
class AmmoReserveTest {

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

    private static Player playerWith(String... weaponIds) throws IOException {
        Player player = new Player();
        player.getTransform().position.set(100f, 32f);
        player.setAnimation(new AnimationComponent());
        player.setHealth(new HealthComponent(100));
        player.setMovement(new MovementComponent());
        player.setAttack(new AttackComponent());
        Array<WeaponComponent> slots = new Array<>();
        for (String id : weaponIds) slots.add(WeaponFactory.createWeaponSlot(player, new Weapon(definition(id), new TextureRegion()), null));
        WeaponFactory.equipArsenal(player, slots);
        return player;
    }

    @BeforeEach
    void setUp() throws IOException {
        world = new World(new Level("test", null, new TileGrid(40, 12, 16f), null, new Vector2(), null, new Array<Rectangle>()));
        player = playerWith("combat_knife", "service_pistol", "assault_rifle");
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

    private void pressReload() {
        player.getWeapon().isReloadRequested = true;
        shooting.update(0.016f);
    }

    private int bullets() {
        int count = 0;
        for (Entity entity : world.getEntities()) if (entity.getProjectile() != null) count++;
        return count;
    }

    // ------------------------------------------------------------------ starting numbers

    @Test
    void firearmsStartWithAFullMagazineAndTheirReserve() {
        assertEquals(12, slot(PISTOL).ammo);
        assertEquals(36, slot(PISTOL).reserve);
        assertEquals(72, slot(PISTOL).maxReserve);
        assertEquals(30, slot(RIFLE).ammo);
        assertEquals(60, slot(RIFLE).reserve);
        assertEquals(150, slot(RIFLE).maxReserve);
    }

    @Test
    void theKnifeCarriesNoAmmunition() {
        assertEquals(0, slot(KNIFE).ammo);
        assertEquals(0, slot(KNIFE).reserve);
        assertEquals(0, slot(KNIFE).maxReserve);
        assertNull(AmmoType.forStance(slot(KNIFE).equipped.getStance()));
    }

    @Test
    void ammoTypesFollowTheWeaponsStance() {
        assertSame(AmmoType.PISTOL, AmmoType.forStance(slot(PISTOL).equipped.getStance()));
        assertSame(AmmoType.RIFLE, AmmoType.forStance(slot(RIFLE).equipped.getStance()));
        assertEquals(24, AmmoType.PISTOL.pickupAmount);
        assertEquals(30, AmmoType.RIFLE.pickupAmount);
    }

    @Test
    void aFirearmWithAnUnknownStanceGetsThreeMagazinesUpToSix() {
        WeaponDefinition definition = new WeaponDefinition();
        definition.id = "test_smg";
        definition.pivot = new Vector2();
        definition.stance = "SMG";
        definition.fire = new FireDefinition();
        definition.fire.magazine = 20;
        WeaponComponent slot = WeaponFactory.createWeaponSlot(player, new Weapon(definition, new TextureRegion()), null);
        assertEquals(20, slot.ammo);
        assertEquals(60, slot.reserve);
        assertEquals(120, slot.maxReserve);
    }

    // ------------------------------------------------------------------ reloading from the reserve

    @Test
    void aReloadMovesRoundsFromTheReserveInTheSameTime() {
        hold(PISTOL);
        slot(PISTOL).ammo = 5;
        pressReload();
        assertEquals(1.1f, slot(PISTOL).reloadTime, 1e-6);
        shooting.update(1.0f);
        assertEquals(5, slot(PISTOL).ammo);
        assertEquals(36, slot(PISTOL).reserve);
        shooting.update(0.11f);
        assertEquals(12, slot(PISTOL).ammo);
        assertEquals(29, slot(PISTOL).reserve);
    }

    @Test
    void theRifleReloadTakesItsOwnTime() {
        hold(RIFLE);
        slot(RIFLE).ammo = 0;
        pullTrigger(0.016f);
        assertEquals(1.8f, slot(RIFLE).reloadTime, 1e-6);
        shooting.update(1.8f);
        assertEquals(30, slot(RIFLE).ammo);
        assertEquals(30, slot(RIFLE).reserve);
    }

    @Test
    void aReloadTakesOnlyWhatTheReserveHolds() {
        hold(PISTOL);
        slot(PISTOL).ammo = 0;
        slot(PISTOL).reserve = 5;
        pullTrigger(0.016f);
        shooting.update(1.2f);
        assertEquals(5, slot(PISTOL).ammo);
        assertEquals(0, slot(PISTOL).reserve);
    }

    @Test
    void theMagazineNeverHoldsMoreThanItsSize() {
        hold(RIFLE);
        slot(RIFLE).ammo = 29;
        slot(RIFLE).reserve = 150;
        pressReload();
        shooting.update(2f);
        assertEquals(30, slot(RIFLE).ammo);
        assertEquals(149, slot(RIFLE).reserve);
    }

    @Test
    void anEmptyReserveMeansNoReload() {
        hold(PISTOL);
        slot(PISTOL).ammo = 2;
        slot(PISTOL).reserve = 0;
        pressReload();
        assertEquals(0f, slot(PISTOL).reloadTime, 1e-6);

        pullTrigger(0.016f);
        pullTrigger(0.25f);
        assertEquals(0, slot(PISTOL).ammo);
        assertEquals(0f, slot(PISTOL).reloadTime, 1e-6);
        assertEquals(2, bullets());

        pullTrigger(0.25f);
        assertEquals(2, bullets());
        assertEquals(0f, slot(PISTOL).reloadTime, 1e-6);
    }

    @Test
    void aFullMagazineDoesNotReloadEvenWithAReserve() {
        hold(RIFLE);
        pressReload();
        assertEquals(0f, slot(RIFLE).reloadTime, 1e-6);
        assertEquals(60, slot(RIFLE).reserve);
    }

    @Test
    void switchingKeepsEveryGunsReserveAndACancelledReloadMovesNothing() {
        hold(PISTOL);
        slot(PISTOL).ammo = 3;
        pressReload();
        hold(RIFLE);
        assertEquals(3, slot(PISTOL).ammo);
        assertEquals(36, slot(PISTOL).reserve);
        assertEquals(60, slot(RIFLE).reserve);
        shooting.update(2f);
        hold(PISTOL);
        assertEquals(3, slot(PISTOL).ammo);
        assertEquals(36, slot(PISTOL).reserve);
    }

    // ------------------------------------------------------------------ ammo pickups

    @Test
    void anAmmoPickupFillsTheMatchingReserveUpToItsMax() {
        assertEquals(24, Pickups.applyAmmo(player, AmmoType.PISTOL));
        assertEquals(60, slot(PISTOL).reserve);
        assertEquals(12, Pickups.applyAmmo(player, AmmoType.PISTOL));
        assertEquals(72, slot(PISTOL).reserve);
        assertEquals(0, Pickups.applyAmmo(player, AmmoType.PISTOL));
        assertEquals(72, slot(PISTOL).reserve);

        assertEquals(30, Pickups.applyAmmo(player, AmmoType.RIFLE));
        assertEquals(90, slot(RIFLE).reserve);
        assertEquals(12, slot(PISTOL).ammo);
        assertEquals(30, slot(RIFLE).ammo);
    }

    @Test
    void anAmmoPickupLeavesTheMagazineAndTheOtherGunAlone() {
        slot(RIFLE).ammo = 4;
        Pickups.applyAmmo(player, AmmoType.PISTOL);
        assertEquals(4, slot(RIFLE).ammo);
        assertEquals(60, slot(RIFLE).reserve);
        assertEquals(12, slot(PISTOL).ammo);
    }

    @Test
    void theDeadOrUnarmedTakeNoAmmo() throws IOException {
        player.getHealth().applyDamage(100);
        assertEquals(0, Pickups.applyAmmo(player, AmmoType.RIFLE));
        assertEquals(60, slot(RIFLE).reserve);

        Player knifeOnly = playerWith("combat_knife");
        assertEquals(0, Pickups.applyAmmo(knifeOnly, AmmoType.PISTOL));
        assertEquals(0, Pickups.applyAmmo(new Entity(), AmmoType.PISTOL));
    }
}
