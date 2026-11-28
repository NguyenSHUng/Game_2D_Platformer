package com.hung.combat.definition;

/**
 * The loot art manifest (assets/sprites/items/items.json): one strip per entry, in the weapons'
 * effect format. Chest strips start with the map's closed chest tile.
 */
public class ItemArtDefinition {
    public EffectDefinition chestWoodOpening;
    public EffectDefinition chestGoldOpening;
    public EffectDefinition healthPack;
    public EffectDefinition pistolAmmo;
    public EffectDefinition rifleAmmo;
    /** Dust where a thrown item lands. */
    public EffectDefinition lootLand;
    /** Flash where an item is picked up. */
    public EffectDefinition pickup;
}
