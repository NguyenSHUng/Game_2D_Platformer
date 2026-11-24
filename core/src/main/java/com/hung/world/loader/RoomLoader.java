package com.hung.world.loader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.hung.component.DoorComponent;
import com.hung.entity.EntityFactory;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.World;

/**
 * Builds the level's rooms and doors from the map generator's object layers
 * (build_asset/build_map, TmxExporter):
 *   "rooms" — one rectangle per room = its camera bounds (kind, region, onPath)
 *   "doors" — one rectangle per door = its trigger; properties room, targetRoom, targetDoor,
 *             kind, direction and the arrival feet point spawnX / spawnY (Tiled pixels, y down)
 * Rectangles come out of the TMX loader already flipped to y up; spawnY is flipped here.
 * Each door becomes an entity with a DoorComponent holding the target room's camera bounds.
 * Maps without these layers (outdoor maps, older exports) simply have no rooms.
 */
public final class RoomLoader {

    private static final String TAG = "RoomLoader";
    private static final String ROOMS_LAYER = "rooms";
    private static final String DOORS_LAYER = "doors";

    private RoomLoader() {
    }

    public static void load(TiledMap map, World world) {
        Level level = world.getLevel();
        readRooms(map, level);
        if (level.getRooms().size == 0) return;
        int doors = readDoors(map, world, level.getHeight());
        Gdx.app.log(TAG, level.getRooms().size + " rooms, " + doors + " doors");
    }

    private static void readRooms(TiledMap map, Level level) {
        MapLayer layer = map.getLayers().get(ROOMS_LAYER);
        if (layer == null) return;
        for (MapObject object : layer.getObjects()) {
            if (!(object instanceof RectangleMapObject)) continue;
            MapProperties properties = object.getProperties();
            level.addRoom(new Room(object.getName(), level.getRooms().size,
                properties.get("kind", "STANDARD", String.class),
                properties.get("region", String.class),
                properties.get("onPath", Boolean.TRUE, Boolean.class),
                ((RectangleMapObject) object).getRectangle()));
        }
    }

    private static int readDoors(TiledMap map, World world, float mapHeight) {
        MapLayer layer = map.getLayers().get(DOORS_LAYER);
        if (layer == null) return 0;
        Level level = world.getLevel();
        int count = 0;
        for (MapObject object : layer.getObjects()) {
            if (!(object instanceof RectangleMapObject)) continue;
            MapProperties properties = object.getProperties();
            Room room = level.getRoom(properties.get("room", String.class));
            Room target = level.getRoom(properties.get("targetRoom", String.class));
            if (room == null || target == null) {
                Gdx.app.log(TAG, "Door '" + object.getName() + "' links unknown rooms; skipped");
                continue;
            }
            DoorComponent door = new DoorComponent();
            door.id = object.getName();
            door.roomId = room.id;
            door.targetRoomId = target.id;
            door.targetDoorId = properties.get("targetDoor", String.class);
            door.kind = properties.get("kind", String.class);
            door.direction = DoorComponent.Direction.valueOf(properties.get("direction", "RIGHT", String.class));
            door.trigger.set(((RectangleMapObject) object).getRectangle());
            door.spawn.set(number(properties, "spawnX"), mapHeight - number(properties, "spawnY"));
            door.cameraBounds.set(target.bounds);
            world.addEntity(EntityFactory.createDoor(door));

            if (!room.neighbors.contains(target, true)) room.neighbors.add(target);
            room.doorways.add(door.trigger.getCenter(new Vector2()));
            count++;
        }
        return count;
    }

    /** Tiled stores ints and floats as different types; read either as float. */
    private static float number(MapProperties properties, String key) {
        Object value = properties.get(key);
        if (!(value instanceof Number)) throw new IllegalArgumentException("Door property '" + key + "' missing");
        return ((Number) value).floatValue();
    }

    /** The room holding a point, else the closest one (a spawn exactly on a wall line). */
    public static Room roomAtOrNearest(Level level, Vector2 point) {
        Room room = level.findRoomAt(point.x, point.y);
        if (room != null) return room;
        float best = Float.MAX_VALUE;
        Rectangle bounds;
        for (Room candidate : level.getRooms()) {
            bounds = candidate.bounds;
            float dx = Math.max(Math.max(bounds.x - point.x, 0f), point.x - (bounds.x + bounds.width));
            float dy = Math.max(Math.max(bounds.y - point.y, 0f), point.y - (bounds.y + bounds.height));
            float distance = dx * dx + dy * dy;
            if (distance < best) {
                best = distance;
                room = candidate;
            }
        }
        return room;
    }
}
