package com.hung.world.loader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.hung.combat.Weapon;
import com.hung.combat.WeaponFactory;
import com.hung.combat.definition.AttachmentProfile;
import com.hung.combat.definition.WeaponDefinition;
import com.hung.component.WeaponComponent;
import com.hung.entity.enemy.EnemyDefinition;
import com.hung.entity.enemy.EnemyFactory;
import com.hung.entity.enemy.EnemySkill;
import com.hung.entity.player.CharacterDefinition;
import com.hung.entity.player.PlayerFactory;
import com.hung.entity.Entity;
import com.hung.input.KeyboardController;
import com.hung.util.Constants;
import com.hung.util.ResourceLoader;
import com.hung.world.EnemySpawn;
import com.hung.world.Level;
import com.hung.world.LightSource;
import com.hung.world.PhysicsSettings;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;

/**
 * Builds a ready-to-play World from a Tiled map exported by the map generator
 * (build_asset/build_map, tools/export-game.mjs):
 *   - "terrain" tile layer → collision grid (each tile's "type" property)
 *   - "spawns" objects     → player spawn and exit
 *   - "enemies" objects    → enemies from the enemy generator's character.json
 *   - "pois" objects       → checkpoints (type CHECKPOINT)
 *   - "rooms" / "doors"    → rooms with camera bounds and door entities (RoomLoader)
 *   - map properties       → movement physics ("physics.*", in tiles) and, optionally,
 *                            "playerCharacter" / "playerWeapon" JSON paths (a player whose strips leave
 *                            the weapon out holds Constants.DEFAULT_PLAYER_WEAPON when none is named)
 * The player is the map's playerCharacter, else Constants.DEFAULT_PLAYER_CHARACTER; when
 * that file doesn't exist the world has no player (free camera).
 */
public final class WorldLoader {

    private static final String TAG = "WorldLoader";
    private static final String SPAWNS_LAYER = "spawns";
    private static final String ENEMIES_LAYER = "enemies";
    private static final String POIS_LAYER = "pois";
    private static final String LIGHTS_LAYER = "lights";
    /** Light radius when a light object has none, world units. */
    private static final float DEFAULT_LIGHT_RADIUS = 64f;
    private static final String CHECKPOINT_TYPE = "CHECKPOINT";
    private static final String TILE_TYPE_PROPERTY = "type";

    private WorldLoader() {
    }

    public static World load(String mapPath, EnemySkill enemySkill, ResourceLoader resources) {
        resources.queueMap(mapPath);
        resources.finishLoading();
        TiledMap map = resources.getMap(mapPath);
        MapProperties properties = map.getProperties();

        TiledMapTileLayer terrain = (TiledMapTileLayer) map.getLayers().get(Level.TERRAIN_LAYER);
        if (terrain == null) {
            throw new IllegalArgumentException(mapPath + " has no '" + Level.TERRAIN_LAYER + "' tile layer");
        }
        TileGrid grid = buildGrid(terrain);
        PhysicsSettings physics = new PhysicsSettings(grid.getTileSize(),
            number(properties, "physics.moveSpeed", PhysicsSettings.DEFAULT_MOVE_SPEED_TILES),
            number(properties, "physics.jumpHeight", PhysicsSettings.DEFAULT_JUMP_HEIGHT_TILES),
            number(properties, "physics.jumpDistance", PhysicsSettings.DEFAULT_JUMP_DISTANCE_TILES));

        Vector2 spawn = findPoint(map, "PLAYER_SPAWN", new Vector2(grid.getTileSize(), grid.getTileSize()));
        Vector2 exit = findPoint(map, "EXIT", null);
        Array<EnemySpawn> enemySpawns = readEnemySpawns(map);

        // Queue every texture before creating entities
        ObjectMap<String, EnemyDefinition> enemyDefinitions = new ObjectMap<>();
        for (EnemySpawn enemySpawn : enemySpawns) {
            if (enemyDefinitions.containsKey(enemySpawn.definitionPath)) continue;
            EnemyDefinition definition = resources.loadExternalDefinition(EnemyDefinition.class, enemySpawn.definitionPath);
            enemyDefinitions.put(enemySpawn.definitionPath, definition);
            EnemyFactory.queueTextures(definition, enemySpawn.definitionPath, resources);
        }
        PlayerAssets player = queuePlayer(properties, resources);
        resources.finishLoading();

        Array<Rectangle> checkpoints = readRectangles(map, POIS_LAYER, CHECKPOINT_TYPE);
        Level level = new Level(mapPath, map, grid, physics, spawn, exit, checkpoints);
        readLights(map, level.getLights());
        World world = new World(level);
        RoomLoader.load(map, world);
        for (EnemySpawn enemySpawn : enemySpawns) {
            world.addEntity(EnemyFactory.createEnemy(enemyDefinitions.get(enemySpawn.definitionPath),
                enemySpawn.definitionPath, resources, enemySpawn, enemySkill));
        }
        if (player != null) {
            world.setPlayer(createPlayer(player, resources, spawn, physics));
        }
        Gdx.app.log(TAG, mapPath + ": " + grid.getWidth() + "x" + grid.getHeight() + " tiles, "
            + enemySpawns.size + " enemies, " + checkpoints.size + " checkpoints, " + level.getLights().size + " lights, "
            + (player != null ? "player " + player.character.id + (player.weapons.size > 0 ? " carrying " + weaponIds(player) : "")
                : "no player (free camera)"));
        return world;
    }

    private static TileGrid buildGrid(TiledMapTileLayer layer) {
        TileGrid grid = new TileGrid(layer.getWidth(), layer.getHeight(), layer.getTileWidth());
        for (int y = 0; y < layer.getHeight(); y++) {
            for (int x = 0; x < layer.getWidth(); x++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                if (cell == null || cell.getTile() == null) continue;
                grid.set(x, y, TileType.fromName(cell.getTile().getProperties().get(TILE_TYPE_PROPERTY, String.class)));
            }
        }
        return grid;
    }

    /** Feet point (bottom-center) of the first rectangle object of the given type in the "spawns" layer. */
    private static Vector2 findPoint(TiledMap map, String type, Vector2 fallback) {
        MapLayer layer = map.getLayers().get(SPAWNS_LAYER);
        if (layer == null) return fallback;
        for (MapObject object : layer.getObjects()) {
            if (object instanceof RectangleMapObject && type.equals(object.getProperties().get("type", String.class))) {
                Rectangle rect = ((RectangleMapObject) object).getRectangle();
                return new Vector2(rect.x + rect.width / 2f, rect.y);
            }
        }
        return fallback;
    }

    /**
     * The map's light objects (points with kind, radius px, color, intensity, flicker, phase).
     * Position from the object's x / y properties, which the TMX loader already flips to y up.
     */
    private static void readLights(TiledMap map, Array<LightSource> out) {
        MapLayer layer = map.getLayers().get(LIGHTS_LAYER);
        if (layer == null) return;
        for (MapObject object : layer.getObjects()) {
            MapProperties properties = object.getProperties();
            LightSource light = new LightSource();
            light.x = number(properties, "x", 0f);
            light.y = number(properties, "y", 0f);
            light.radius = number(properties, "radius", DEFAULT_LIGHT_RADIUS);
            light.intensity = number(properties, "intensity", 1f);
            light.phase = number(properties, "phase", 0f);
            light.flicker = LightSource.parseFlicker(properties.get("flicker", String.class));
            light.kind = properties.get("kind", String.class);
            String color = properties.get("color", String.class);
            if (color != null && color.startsWith("#") && color.length() == 7) {
                light.color.set(Integer.parseInt(color.substring(1), 16) << 8 | 0xff);
            }
            out.add(light);
        }
    }

    /** Every rectangle object of the given type in a layer (world units, y up). */
    private static Array<Rectangle> readRectangles(TiledMap map, String layerName, String type) {
        Array<Rectangle> rectangles = new Array<>();
        MapLayer layer = map.getLayers().get(layerName);
        if (layer == null) return rectangles;
        for (MapObject object : layer.getObjects()) {
            if (object instanceof RectangleMapObject && type.equals(object.getProperties().get("type", String.class))) {
                rectangles.add(new Rectangle(((RectangleMapObject) object).getRectangle()));
            }
        }
        return rectangles;
    }

    private static Array<EnemySpawn> readEnemySpawns(TiledMap map) {
        Array<EnemySpawn> spawns = new Array<>();
        MapLayer layer = map.getLayers().get(ENEMIES_LAYER);
        if (layer == null) return spawns;
        for (MapObject object : layer.getObjects()) {
            if (!(object instanceof RectangleMapObject)) continue;
            MapProperties properties = object.getProperties();
            String definition = properties.get("definition", String.class);
            if (definition == null || definition.isEmpty()) {
                Gdx.app.log(TAG, "Enemy '" + object.getName() + "' has no definition; skipped");
                continue;
            }
            Rectangle rect = ((RectangleMapObject) object).getRectangle();
            EnemySpawn spawn = new EnemySpawn();
            spawn.name = object.getName();
            spawn.definitionPath = definition;
            spawn.x = rect.x + rect.width / 2f;
            spawn.y = rect.y;
            spawn.patrolMinX = number(properties, "patrolMinX", rect.x);
            spawn.patrolMaxX = number(properties, "patrolMaxX", rect.x + rect.width);
            spawn.isFacingRight = !"LEFT".equals(properties.get("facing", String.class));
            spawns.add(spawn);
        }
        return spawns;
    }

    /** Tiled stores ints and floats as different types; read either as float. */
    private static float number(MapProperties properties, String key, float fallback) {
        Object value = properties.get(key);
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    // ------------------------------------------------------------------ player (optional)

    private static final class PlayerAssets {
        String characterPath;
        CharacterDefinition character;
        /** Carried weapons, first in hand (knife, then the sidearm). */
        final Array<WeaponDefinition> weapons = new Array<>();
    }

    private static PlayerAssets queuePlayer(MapProperties properties, ResourceLoader resources) {
        String characterPath = properties.get("playerCharacter", String.class);
        if (characterPath == null || characterPath.isEmpty()) {
            characterPath = Constants.DEFAULT_PLAYER_CHARACTER;
            if (!Gdx.files.internal(characterPath).exists()) {
                Gdx.app.log(TAG, "No player character at " + characterPath);
                return null;
            }
        }
        PlayerAssets assets = new PlayerAssets();
        assets.characterPath = characterPath;
        assets.character = resources.loadExternalDefinition(CharacterDefinition.class, characterPath);
        PlayerFactory.queueTextures(assets.character, characterPath, resources);
        String weaponPath = properties.get("playerWeapon", String.class);
        if ((weaponPath == null || weaponPath.isEmpty()) && "SEPARATE".equals(assets.character.weaponLayer)) {
            // The strips leave the weapon out: hold the weapon generator's default one
            weaponPath = Constants.DEFAULT_PLAYER_WEAPON;
            if (!Gdx.files.internal(weaponPath).exists()) {
                Gdx.app.log(TAG, "No weapon at " + weaponPath + "; the player is drawn unarmed");
                weaponPath = null;
            }
        }
        queueWeapon(assets, weaponPath, resources);
        // A player without baked weapons also carries the weapon generator's firearms (keys 2, 3 / Q)
        if ("SEPARATE".equals(assets.character.weaponLayer)) {
            for (String path : Constants.DEFAULT_PLAYER_ARSENAL) {
                if (Gdx.files.internal(path).exists()) queueWeapon(assets, path, resources);
            }
        }
        return assets;
    }

    private static void queueWeapon(PlayerAssets assets, String path, ResourceLoader resources) {
        if (path == null || path.isEmpty()) return;
        WeaponDefinition weapon = resources.loadExternalDefinition(WeaponDefinition.class, path);
        WeaponFactory.queueWeaponAssets(weapon, resources);
        assets.weapons.add(weapon);
    }

    private static Entity createPlayer(PlayerAssets assets, ResourceLoader resources, Vector2 spawn, PhysicsSettings physics) {
        Entity player = PlayerFactory.createPlayer(assets.character, assets.characterPath, resources,
            spawn.x, spawn.y, new KeyboardController(), physics);
        Array<WeaponComponent> slots = new Array<>();
        for (WeaponDefinition definition : assets.weapons) {
            Weapon weapon = WeaponFactory.createWeapon(definition, resources);
            slots.add(WeaponFactory.createWeaponSlot(player, weapon, loadAttachmentProfile(resources, assets.character, definition)));
        }
        WeaponFactory.equipArsenal(player, slots);
        return player;
    }

    private static String weaponIds(PlayerAssets assets) {
        StringBuilder ids = new StringBuilder();
        for (WeaponDefinition weapon : assets.weapons) ids.append(ids.length() > 0 ? ", " : "").append(weapon.id);
        return ids.toString();
    }

    /** Reads attachments/&lt;characterId&gt;/&lt;weaponId&gt;.json; null when the pair has no profile. */
    private static AttachmentProfile loadAttachmentProfile(ResourceLoader resources,
                                                           CharacterDefinition character, WeaponDefinition weapon) {
        requireId(character.id, "Character '" + character.name + "'");
        requireId(weapon.id, "Weapon '" + weapon.name + "'");

        String path = String.format(Constants.ATTACHMENT_PROFILE_PATH, character.id, weapon.id);
        AttachmentProfile profile = resources.loadOptionalDefinition(AttachmentProfile.class, path);
        if (profile == null) {
            Gdx.app.log(TAG, "No attachment profile at " + path + "; using default hand anchors");
            return null;
        }
        if (!character.id.equals(profile.character) || !weapon.id.equals(profile.weapon)) {
            throw new IllegalArgumentException(path + " is for '" + profile.character + "' / '" + profile.weapon
                + "', expected '" + character.id + "' / '" + weapon.id + "'");
        }
        Gdx.app.log(TAG, "Attachment profile: " + path);
        return profile;
    }

    private static void requireId(String id, String owner) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException(owner + " has no \"id\"; it is needed to find attachment profiles");
        }
    }
}
