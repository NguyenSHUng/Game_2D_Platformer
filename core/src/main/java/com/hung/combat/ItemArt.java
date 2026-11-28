package com.hung.combat;

import com.hung.combat.definition.EffectDefinition;
import com.hung.combat.definition.ItemArtDefinition;
import com.hung.util.Constants;
import com.hung.util.ResourceLoader;

import java.util.EnumMap;
import java.util.Map;

/**
 * The loot's pictures: each item lying in the world, each chest tier's opening, the dust of a landing
 * item and the flash of a picked one. Anything missing (no manifest, a test) is null and the game
 * falls back to its placeholders: coloured boxes for items, a darkened tile for opened chests.
 */
public final class ItemArt {

    /** No art at all. */
    public static final ItemArt NONE = new ItemArt(new EnumMap<>(PickupType.class), new EnumMap<>(ChestTier.class), null, null);

    private final Map<PickupType, WeaponEffect> items;
    private final Map<ChestTier, WeaponEffect> chestOpenings;
    private final WeaponEffect landing;
    private final WeaponEffect pickup;

    public ItemArt(Map<PickupType, WeaponEffect> items, Map<ChestTier, WeaponEffect> chestOpenings,
                   WeaponEffect landing, WeaponEffect pickup) {
        this.items = items;
        this.chestOpenings = chestOpenings;
        this.landing = landing;
        this.pickup = pickup;
    }

    public WeaponEffect item(PickupType type) { return items.get(type); }
    public WeaponEffect chestOpening(ChestTier tier) { return chestOpenings.get(tier); }
    public WeaponEffect landing() { return landing; }
    public WeaponEffect pickup() { return pickup; }

    /** Reads the manifest (Constants.ITEM_ART_PATH) and loads its strips; NONE when there is no manifest. */
    public static ItemArt load(ResourceLoader resources) {
        ItemArtDefinition definition = resources.loadOptionalDefinition(ItemArtDefinition.class, Constants.ITEM_ART_PATH);
        if (definition == null) return NONE;
        EffectDefinition[] strips = {definition.chestWoodOpening, definition.chestGoldOpening, definition.healthPack,
            definition.pistolAmmo, definition.rifleAmmo, definition.lootLand, definition.pickup};
        for (EffectDefinition strip : strips) if (strip != null) resources.queueTexture(strip.texture);
        resources.finishLoading();
        Map<PickupType, WeaponEffect> items = new EnumMap<>(PickupType.class);
        put(items, PickupType.HEALTH_PACK, definition.healthPack, resources);
        put(items, PickupType.PISTOL_AMMO, definition.pistolAmmo, resources);
        put(items, PickupType.RIFLE_AMMO, definition.rifleAmmo, resources);
        Map<ChestTier, WeaponEffect> chests = new EnumMap<>(ChestTier.class);
        put(chests, ChestTier.WOOD, definition.chestWoodOpening, resources);
        put(chests, ChestTier.GOLD, definition.chestGoldOpening, resources);
        return new ItemArt(items, chests, effect(definition.lootLand, resources), effect(definition.pickup, resources));
    }

    private static <K> void put(Map<K, WeaponEffect> map, K key, EffectDefinition strip, ResourceLoader resources) {
        WeaponEffect effect = effect(strip, resources);
        if (effect != null) map.put(key, effect);
    }

    private static WeaponEffect effect(EffectDefinition strip, ResourceLoader resources) {
        return strip != null ? WeaponFactory.createEffect(strip, resources.getTexture(strip.texture)) : null;
    }
}
