package com.hung.rendering;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.hung.combat.Weapon;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.WeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The character sheet shows magazine / reserve from the WeaponComponent, the very numbers the HUD shows. */
class AmmoDisplayTest {

    private static WeaponComponent slot(String name, int magazine, int ammo, int reserve, int maxReserve) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.name = name;
        definition.pivot = new Vector2();
        definition.fire = new FireDefinition();
        definition.fire.magazine = magazine;
        WeaponComponent component = new WeaponComponent();
        component.equipped = new Weapon(definition, new TextureRegion());
        component.ammo = ammo;
        component.reserve = reserve;
        component.maxReserve = maxReserve;
        return component;
    }

    private static String hud(WeaponComponent slot) {
        StringBuilder out = new StringBuilder();
        HudRenderer.appendWeapon(out, slot);
        return out.toString();
    }

    @Test
    void pistolAndRifleShowMagazineAndReserve() {
        WeaponComponent pistol = slot("Service Pistol", 12, 8, 36, 72);
        WeaponComponent rifle = slot("Assault Rifle", 30, 30, 60, 150);
        assertEquals("8 / 36", StatsPanel.ammoText(pistol));
        assertEquals("max 12 / 72", StatsPanel.capacityText(pistol, pistol.equipped.getFire()));
        assertEquals("30 / 60", StatsPanel.ammoText(rifle));
        assertEquals("max 30 / 150", StatsPanel.capacityText(rifle, rifle.equipped.getFire()));
    }

    @Test
    void theSheetAndTheHudNeverDisagree() {
        WeaponComponent pistol = slot("Service Pistol", 12, 12, 36, 72);
        for (int shot = 0; shot < 5; shot++) pistol.ammo--;
        pistol.reserve -= 7;
        assertTrue(hud(pistol).endsWith("  " + StatsPanel.ammoText(pistol)), hud(pistol));
        assertEquals("7 / 29", StatsPanel.ammoText(pistol));

        pistol.reloadTime = 1f;
        assertTrue(hud(pistol).contains("  " + StatsPanel.ammoText(pistol) + "  RELOADING"), hud(pistol));
    }
}
