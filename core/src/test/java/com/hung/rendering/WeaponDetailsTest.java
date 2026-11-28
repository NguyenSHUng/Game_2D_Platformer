package com.hung.rendering;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.hung.combat.Weapon;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.WeaponComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The character sheet's details card shows only the essentials of the weapon in hand: damage,
 * range / reach, magazine / reserve for firearms, reload / swing time. No DPS, bullet speed or spread.
 */
class WeaponDetailsTest {

    private static WeaponComponent firearm(String mode, int damage, float range, int magazine, float reload,
                                           int ammo, int reserve, int maxReserve) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.name = "Gun";
        definition.pivot = new Vector2();
        definition.fire = new FireDefinition();
        definition.fire.mode = mode;
        definition.fire.damage = damage;
        definition.fire.rangeTiles = range;
        definition.fire.magazine = magazine;
        definition.fire.reloadSeconds = reload;
        WeaponComponent slot = new WeaponComponent();
        slot.equipped = new Weapon(definition, new TextureRegion());
        slot.ammo = ammo;
        slot.reserve = reserve;
        slot.maxReserve = maxReserve;
        return slot;
    }

    private static WeaponComponent knife() {
        WeaponDefinition definition = new WeaponDefinition();
        definition.name = "Combat Knife";
        definition.pivot = new Vector2();
        WeaponComponent slot = new WeaponComponent();
        slot.equipped = new Weapon(definition, new TextureRegion());
        return slot;
    }

    private static List<String> labels(List<StatsPanel.DetailRow> rows) {
        return rows.stream().map(StatsPanel.DetailRow::label).toList();
    }

    @Test
    void aFirearmShowsDamageRangeAmmoAndReload() {
        List<StatsPanel.DetailRow> rows = StatsPanel.details(firearm("SEMI", 8, 20f, 12, 1.2f, 7, 29, 72), 5f, 0f, 0.4f);
        assertEquals(List.of("Damage", "Range", "Mag / Res", "Reload"), labels(rows));
        assertEquals("8", rows.get(0).value());
        assertEquals("20.0 tiles", rows.get(1).value());
        assertEquals("7 / 29", rows.get(2).value());
        assertEquals("max 12 / 72", rows.get(2).note());
        assertEquals("1.2 s", rows.get(3).value());
    }

    @Test
    void theKnifeShowsDamageReachAndSwing() {
        List<StatsPanel.DetailRow> rows = StatsPanel.details(knife(), 5f, 1.25f, 0.4f);
        assertEquals(List.of("Damage", "Reach", "Swing"), labels(rows));
        assertEquals("5", rows.get(0).value());
        assertEquals("1.3 tiles", rows.get(1).value());
        assertEquals("0.40 s", rows.get(2).value());
        for (StatsPanel.DetailRow row : rows) assertNull(row.note());
    }

    @Test
    void theAmmoRowIsTheHudsNumbers() {
        WeaponComponent rifle = firearm("AUTO", 6, 26f, 30, 1.8f, 30, 60, 150);
        StringBuilder hud = new StringBuilder();
        HudRenderer.appendWeapon(hud, rifle);
        String ammo = StatsPanel.details(rifle, 0f, 0f, 0f).get(2).value();
        assertTrue(hud.toString().endsWith("  " + ammo), hud + " vs " + ammo);
    }

    @Test
    void comparisonBarsStayWithinTheirTrack() {
        for (StatsPanel.DetailRow row : StatsPanel.details(firearm("SEMI", 8, 20f, 12, 1f, 12, 36, 72), 0f, 0f, 0f)) {
            assertTrue(row.bar() <= 1f, row.label());
            if (row.bar() < 0f) assertTrue(row.label().equals("Mag / Res") || row.label().equals("Reload"), row.label());
        }
    }

    @Test
    void theKindLineSaysHowItIsUsedAndWhenItIsReloadingOrEmpty() {
        assertEquals("Semi-auto, two hands", StatsPanel.kind(firearm("SEMI", 8, 20f, 12, 1f, 5, 30, 72)));
        WeaponComponent rifle = firearm("AUTO", 6, 26f, 30, 1.8f, 0, 60, 150);
        rifle.reloadTime = 0.5f;
        assertEquals("Full-auto, two hands - RELOADING", StatsPanel.kind(rifle));
        assertEquals("Semi-auto, two hands - NO AMMO", StatsPanel.kind(firearm("SEMI", 8, 20f, 12, 1f, 0, 0, 72)));
        assertEquals("Melee, one hand - breaks cracked walls", StatsPanel.kind(knife()));
    }
}
