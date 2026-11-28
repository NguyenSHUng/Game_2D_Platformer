package com.hung.rendering;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.hung.combat.Weapon;
import com.hung.combat.definition.FireDefinition;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.WeaponComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The HUD's weapon line: magazine and reserve, reloading, out of ammo. */
class HudWeaponTextTest {

    private static WeaponComponent weapon(String name, boolean isFirearm, int ammo, int reserve) {
        WeaponDefinition definition = new WeaponDefinition();
        definition.name = name;
        definition.pivot = new Vector2();
        if (isFirearm) definition.fire = new FireDefinition();
        WeaponComponent component = new WeaponComponent();
        component.equipped = new Weapon(definition, new TextureRegion());
        component.ammo = ammo;
        component.reserve = reserve;
        return component;
    }

    private static String text(WeaponComponent weapon) {
        StringBuilder out = new StringBuilder();
        HudRenderer.appendWeapon(out, weapon);
        return out.toString();
    }

    @Test
    void firearmsShowMagazineAndReserve() {
        assertEquals("Service Pistol  12 / 36", text(weapon("Service Pistol", true, 12, 36)));
    }

    @Test
    void reloadingIsShown() {
        WeaponComponent rifle = weapon("Assault Rifle", true, 0, 60);
        rifle.reloadTime = 1f;
        assertEquals("Assault Rifle  0 / 60  RELOADING", text(rifle));
    }

    @Test
    void anEmptyMagazineAndReserveShowNoAmmo() {
        assertEquals("Assault Rifle  0 / 0  NO AMMO", text(weapon("Assault Rifle", true, 0, 0)));
        assertEquals("Assault Rifle  3 / 0", text(weapon("Assault Rifle", true, 3, 0)));
    }

    @Test
    void theKnifeShowsOnlyItsName() {
        assertEquals("Combat Knife", text(weapon("Combat Knife", false, 0, 0)));
    }
}
