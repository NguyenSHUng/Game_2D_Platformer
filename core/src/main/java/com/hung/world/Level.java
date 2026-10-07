package com.hung.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * A loaded level: the Tiled map to draw, its collision grid, movement settings
 * and where the player starts. World size is the map size in pixels.
 */
public class Level {

    /** Tile layer holding the collision tiles (each tile's "type" property). */
    public static final String TERRAIN_LAYER = "terrain";

    private final String name;
    private final TiledMap map;
    private final TileGrid grid;
    private final PhysicsSettings physics;
    private final Vector2 playerSpawn;
    private final Vector2 exit;
    private final Array<Rectangle> checkpoints;
    /** Point lights from the map's "lights" layer (render-only), filled by WorldLoader. */
    private final Array<LightSource> lights = new Array<>();
    /** Rooms from the map's "rooms" layer (empty on maps without rooms), filled by RoomLoader. */
    private final Array<Room> rooms = new Array<>();
    private final ObjectMap<String, Room> roomsById = new ObjectMap<>();

    public Level(String name, TiledMap map, TileGrid grid, PhysicsSettings physics, Vector2 playerSpawn, Vector2 exit,
                 Array<Rectangle> checkpoints) {
        this.name = name;
        this.map = map;
        this.grid = grid;
        this.physics = physics;
        this.playerSpawn = playerSpawn;
        this.exit = exit;
        this.checkpoints = checkpoints;
    }

    public String getName() { return name; }
    public TiledMap getMap() { return map; }
    public TileGrid getGrid() { return grid; }
    public PhysicsSettings getPhysics() { return physics; }
    public Vector2 getPlayerSpawn() { return playerSpawn; }
    public Vector2 getExit() { return exit; }
    /** Checkpoint areas (world units) from the map's pois layer. */
    public Array<Rectangle> getCheckpoints() { return checkpoints; }
    public Array<LightSource> getLights() { return lights; }
    public Array<Room> getRooms() { return rooms; }
    public Room getRoom(String id) { return id != null ? roomsById.get(id) : null; }

    public void addRoom(Room room) {
        rooms.add(room);
        roomsById.put(room.id, room);
    }

    /** The room whose camera bounds hold the point; null outside every room. */
    public Room findRoomAt(float x, float y) {
        for (int i = 0; i < rooms.size; i++) {
            if (rooms.get(i).bounds.contains(x, y)) return rooms.get(i);
        }
        return null;
    }

    public float getWidth() { return grid.getWidth() * grid.getTileSize(); }
    public float getHeight() { return grid.getHeight() * grid.getTileSize(); }
}
