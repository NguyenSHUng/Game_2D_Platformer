package com.hung.component;

import com.hung.combat.WeaponEffect;
import com.hung.entity.Entity;

/** A bullet in flight. ProjectileSystem moves it, hits tiles and bodies, and removes it. */
public class ProjectileComponent {

    public int damage;

    /** Who fired it: never hits its owner or the owner's side. */
    public Entity owner;

    /** World units it may still travel. */
    public float rangeLeft;

    /** Impact effect to spawn where it stops (nullable). */
    public WeaponEffect impact;
}
