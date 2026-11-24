package com.hung.util;

/**
 * Global game constants shared by several systems.
 * Per-map and per-character values live in map/JSON files, not here.
 */
public final class Constants {

    /**
     * One Tiled map per game mode (core/GameMode): 13, 25 and 42 rooms, each exported by the map
     * generator (build_asset/build_map/platformer-map-generator, tools/export-game.mjs --mode <mode>).
     */
    public static final String EASY_MAP_PATH = "maps/bunker_easy/map_easy.tmx";
    public static final String MEDIUM_MAP_PATH = "maps/bunker_medium/map_medium.tmx";
    public static final String HARD_MAP_PATH = "maps/bunker_hard/map_hard.tmx";

    /**
     * Player used when the map has no "playerCharacter" property (written by
     * build_asset/build_character/player-character-generator, tools/export-game.mjs).
     * Missing file = no player, free camera.
     */
    public static final String DEFAULT_PLAYER_CHARACTER = "sprites/players/player_soldier/player_soldier.json";

    /**
     * Weapon equipped on a player whose strips leave the weapon out (weaponLayer SEPARATE) when the
     * map has no "playerWeapon" property (written by build_asset/build_weapon/pixel-weapon-generator,
     * tools/export-game.mjs). Missing file = unarmed look.
     */
    public static final String DEFAULT_PLAYER_WEAPON = "sprites/weapons/combat_knife/combat_knife.json";

    /**
     * Other weapons of that player, in slot order after the knife (keys 2, 3 / Q), each when its file
     * exists: the weapon generator's pistol and assault rifle.
     */
    public static final String[] DEFAULT_PLAYER_ARSENAL = {
        "sprites/weapons/service_pistol/service_pistol.json",
        "sprites/weapons/assault_rifle/assault_rifle.json",
    };

    /** Attachment profile for a character/weapon pair: format with (characterId, weaponId). */
    public static final String ATTACHMENT_PROFILE_PATH = "attachments/%s/%s.json";

    /**
     * Minimum visible world area (1 world unit = 1 map pixel; tiles are 16 px), so about
     * 40 × 22 tiles; the viewport extends one axis to fit the window aspect ratio.
     */
    public static final float VIEW_MIN_WIDTH = 640f;
    public static final float VIEW_MIN_HEIGHT = 360f;

    /** Upper bound for one frame's delta so a stall (window drag, breakpoint) can't teleport entities. */
    public static final float MAX_FRAME_TIME = 0.1f;

    private Constants() {
    }
}
