package com.hung.entity.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.hung.ai.PatrolSystem;
import com.hung.ai.tactic.HitAndRunTactic;
import com.hung.ai.tactic.TacticPolicy;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.component.PatrolComponent;
import com.hung.core.GameMode;
import com.hung.entity.Entity;
import com.hung.entity.player.Player;
import com.hung.physics.CollisionSystem;
import com.hung.physics.PhysicsSystem;
import com.hung.world.Level;
import com.hung.world.PhysicsSettings;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import com.hung.world.World;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scripted playtest of the MEDIUM map with its real terrain, rooms and enemies: in every room
 * with enemies, a player stands near them, then sways back and forth, while the real patrol, hunt
 * and physics run. Checked: no hunter leaves its room, stands on a hazard or falls out of the map.
 * The numbers of the run (strikes, body contact) are written to build/playtest/medium.txt and
 * compared with the same map played with direct chase only.
 */
class MediumMapPlaytestTest {

    private static final Path MAP = Path.of("..", "assets", GameMode.MEDIUM.mapPath);
    private static final Path REPORT = Path.of("build", "playtest", "medium.txt");
    private static final float STEP = 1f / 60f;
    private static final float SECONDS_PER_ROOM = 10f;
    private static final float SWING_SECONDS = 0.35f;
    /** Same constants as EnemyFactory. */
    private static final float SIZE_SCALE = 1.2f;
    private static final int COLLIDER_INSET = 4;
    private static final float PLAYER_WIDTH = 7f;
    private static final float PLAYER_HEIGHT = 23f;
    /** Rough reach of the player's knife beyond the bodies touching, world units (an estimate for the report). */
    private static final float KNIFE_REACH = 14f;

    private record Spawn(String name, String definitionPath, String roomId, float x, float y,
                         float patrolMinX, float patrolMaxX, boolean isFacingRight) {
    }

    private static final class Stats {
        int enemies;
        int hitAndRun;
        int strikes;
        int contactFrames;
        int exposedFrames;
        int frames;
        int lateStrikes;
        int lateContactFrames;
        int lateExposedFrames;
        final List<String> leftRoom = new ArrayList<>();
        final List<String> onHazard = new ArrayList<>();
        final List<String> fellOut = new ArrayList<>();
    }

    @Test
    void mediumMapHuntersStaySafeAndInTheirRooms() throws Exception {
        Stats tactics = play(GameMode.MEDIUM.tacticPolicy);
        Stats direct = play(TacticPolicy.DIRECT_ONLY);
        writeReport(tactics, direct);

        assertEquals(49, tactics.enemies);
        assertEquals(30, tactics.hitAndRun);
        for (Stats stats : new Stats[] {tactics, direct}) {
            assertTrue(stats.leftRoom.isEmpty(), "left their room: " + stats.leftRoom);
            assertTrue(stats.onHazard.isEmpty(), "stood on a hazard: " + stats.onHazard);
            assertTrue(stats.fellOut.isEmpty(), "fell out of the map: " + stats.fellOut);
        }
        assertTrue(tactics.strikes > 0 && direct.strikes > 0);
    }

    // ------------------------------------------------------------------ one run of the whole map

    private Stats play(TacticPolicy policy) throws Exception {
        Document map = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(MAP.toFile());
        Element root = map.getDocumentElement();
        int width = Integer.parseInt(root.getAttribute("width"));
        int height = Integer.parseInt(root.getAttribute("height"));
        float tile = Float.parseFloat(root.getAttribute("tilewidth"));
        float mapHeight = height * tile;
        TileGrid grid = readTerrain(map, width, height, tile);
        Map<String, Room> rooms = readRooms(map, mapHeight);
        List<Spawn> spawns = readSpawns(map, mapHeight);
        Map<String, EnemyDefinition> definitions = new HashMap<>();

        Stats stats = new Stats();
        Map<String, List<Spawn>> byRoom = new HashMap<>();
        for (Spawn spawn : spawns) byRoom.computeIfAbsent(spawn.roomId(), id -> new ArrayList<>()).add(spawn);
        for (Map.Entry<String, List<Spawn>> entry : byRoom.entrySet()) {
            Level level = new Level("medium", null, grid, new PhysicsSettings(tile, 6f, 3.5f, 6f), new Vector2(), null,
                new Array<Rectangle>());
            for (Room room : rooms.values()) level.addRoom(room);
            World world = new World(level);
            Room room = rooms.get(entry.getKey());
            List<HunterEnemy> hunters = new ArrayList<>();
            for (Spawn spawn : entry.getValue()) {
                EnemyDefinition definition = definitions.computeIfAbsent(spawn.definitionPath(), MediumMapPlaytestTest::load);
                HunterEnemy hunter = create(definition, spawn);
                hunter.setHomeRoom(room);
                hunter.setTactic(policy.create(room.regionId));
                if (hunter.tactic() instanceof HitAndRunTactic) stats.hitAndRun++;
                world.addEntity(hunter);
                hunters.add(hunter);
            }
            stats.enemies += hunters.size();
            playRoom(world, room, hunters, stats, regionNumber(room.regionId) >= 3);
        }
        return stats;
    }

    /** The player near the first hunter, on its floor: stands for a while, then sways ±40 units. */
    private void playRoom(World world, Room room, List<HunterEnemy> hunters, Stats stats, boolean isLate) {
        TileGrid grid = world.getLevel().getGrid();
        HunterEnemy first = hunters.get(0);
        float floorY = first instanceof FlyingEnemy ? first.getTransform().position.y - FlyingEnemy.CRUISE_HEIGHT
            : first.getTransform().position.y;
        float baseX = standingSpot(grid, room, first.getTransform().position.x, floorY);
        Player player = new Player();
        ColliderComponent body = new ColliderComponent();
        body.width = PLAYER_WIDTH;
        body.height = PLAYER_HEIGHT;
        player.setCollider(body);
        player.getTransform().position.set(baseX, floorY);

        PatrolSystem patrol = new PatrolSystem(world);
        PhysicsSystem physics = new PhysicsSystem(world, new CollisionSystem(world));
        Map<HunterEnemy, Float> swings = new HashMap<>();
        float time = 0f;
        for (int frame = 0; frame < SECONDS_PER_ROOM / STEP; frame++, time += STEP) {
            float sway = time < SECONDS_PER_ROOM / 2f ? 0f : 40f * (float) Math.sin(time * 1.5f);
            float x = baseX + sway;
            if (onFloor(grid, x, floorY) && room.bounds.contains(x, floorY + 1f)) player.getTransform().position.x = x;

            patrol.update();
            for (HunterEnemy hunter : hunters) hunter.updateBehaviour(STEP, world, player);
            physics.update(STEP);
            for (HunterEnemy hunter : hunters) {
                AttackComponent attack = hunter.getAttack();
                if (attack.isRequested) {
                    attack.isRequested = false;
                    attack.isAttacking = true;
                    swings.put(hunter, SWING_SECONDS);
                    stats.strikes++;
                    if (isLate) stats.lateStrikes++;
                }
                if (attack.isAttacking) {
                    float left = swings.get(hunter) - STEP;
                    swings.put(hunter, left);
                    if (left <= 0f) attack.isAttacking = false;
                }
                check(hunter, player, room, grid, stats, isLate);
            }
            stats.frames++;
        }
    }

    private static void check(HunterEnemy hunter, Entity player, Room room, TileGrid grid, Stats stats, boolean isLate) {
        Vector2 at = hunter.getTransform().position;
        String name = room.id + "/" + hunter.getClass().getSimpleName() + "@" + (int) at.x;
        float half = hunter.getCollider().width / 2f;
        boolean isInside = at.x - half >= room.bounds.x - 1f && at.x + half <= room.bounds.x + room.bounds.width + 1f;
        if (!isInside && !stats.leftRoom.contains(name)) stats.leftRoom.add(name);
        if (at.y < 0f && !stats.fellOut.contains(name)) stats.fellOut.add(name);
        if (!(hunter instanceof FlyingEnemy) && grid.get(grid.toTile(at.x), grid.toTile(at.y + 1f)).isHazard()
            && !stats.onHazard.contains(name)) stats.onHazard.add(name);
        Vector2 p = player.getTransform().position;
        boolean isTouching = Math.abs(p.x - at.x) < half + PLAYER_WIDTH / 2f
            && p.y < at.y + hunter.getCollider().height && at.y < p.y + PLAYER_HEIGHT;
        boolean isLevel = p.y < at.y + hunter.getCollider().height && at.y < p.y + PLAYER_HEIGHT;
        boolean isExposed = isLevel && Math.abs(p.x - at.x) < half + PLAYER_WIDTH / 2f + KNIFE_REACH;
        if (isTouching) stats.contactFrames++;
        if (isExposed) stats.exposedFrames++;
        if (isLate && isTouching) stats.lateContactFrames++;
        if (isLate && isExposed) stats.lateExposedFrames++;
    }

    private static int regionNumber(String regionId) {
        return regionId != null && regionId.startsWith("region_") ? Integer.parseInt(regionId.substring(7)) : 0;
    }

    /** 70 units to the side of the hunter where there is floor, inside the room; else the hunter's x. */
    private static float standingSpot(TileGrid grid, Room room, float x, float floorY) {
        for (float offset : new float[] {70f, -70f, 50f, -50f, 30f, -30f}) {
            float spot = x + offset;
            if (onFloor(grid, spot, floorY) && room.bounds.contains(spot, floorY + 1f)) return spot;
        }
        return x;
    }

    private static boolean onFloor(TileGrid grid, float x, float y) {
        return grid.isSupport(grid.toTile(x), grid.toTile(y) - 1) && !grid.get(grid.toTile(x), grid.toTile(y + 1f)).isSolid();
    }

    // ------------------------------------------------------------------ enemies as EnemyFactory builds them

    private static HunterEnemy create(EnemyDefinition definition, Spawn spawn) {
        float scale = EnemyFactory.drawScale(definition);
        float attackRange = attackRange(definition);
        HunterEnemy hunter = switch (definition.archetype) {
            case SMALL_MELEE -> new MeleeEnemy(attackRange);
            case CRAWLER -> new CrawlerEnemy(attackRange);
            case FLYING -> new FlyingEnemy(attackRange, spawn.y());
        };
        hunter.setSkill(GameMode.MEDIUM.enemySkill);
        hunter.getTransform().position.set(spawn.x(), spawn.y());
        hunter.getTransform().isFacingRight = spawn.isFacingRight();
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = EnemyFactory.moveSpeed(definition);
        movement.isFlying = hunter instanceof FlyingEnemy;
        hunter.setMovement(movement);
        EnemyDefinition.Bounds bounds = definition.animations.get("idle").frames.first().bounds;
        ColliderComponent collider = new ColliderComponent();
        collider.width = Math.max(4f, (bounds.width - COLLIDER_INSET) * scale);
        collider.height = Math.max(4f, bounds.height * scale);
        hunter.setCollider(collider);
        PatrolComponent patrol = new PatrolComponent();
        patrol.minX = spawn.patrolMinX() + collider.width / 2f;
        patrol.maxX = spawn.patrolMaxX() - collider.width / 2f;
        patrol.direction = spawn.isFacingRight() ? 1 : -1;
        hunter.setPatrol(patrol);
        hunter.setHealth(EnemyFactory.createHealth(definition, hunter));
        hunter.setAttack(EnemyFactory.createAttack(definition, GameMode.MEDIUM.enemySkill));
        return hunter;
    }

    /** As EnemyFactory.attackRange: 80 % of the shortest measured reach plus half the player's width. */
    private static float attackRange(EnemyDefinition definition) {
        float shortest = Float.MAX_VALUE;
        if (definition.gameplay != null && definition.gameplay.attacks != null) {
            for (EnemyDefinition.AttackInfo info : definition.gameplay.attacks.values()) {
                if (info.reach != null) shortest = Math.min(shortest, info.reach.world);
            }
        }
        return shortest < Float.MAX_VALUE ? shortest * SIZE_SCALE * 0.8f + 4f : 16f;
    }

    private static EnemyDefinition load(String path) {
        try {
            Json json = new Json();
            json.setIgnoreUnknownFields(true);
            return json.fromJson(EnemyDefinition.class, Files.readString(Path.of("..", "assets", path), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(path, e);
        }
    }

    // ------------------------------------------------------------------ the map file (as WorldLoader / RoomLoader read it)

    private static TileGrid readTerrain(Document map, int width, int height, float tile) throws Exception {
        Map<Integer, TileType> types = new HashMap<>();
        NodeList tilesets = map.getElementsByTagName("tileset");
        for (int t = 0; t < tilesets.getLength(); t++) {
            Element tileset = (Element) tilesets.item(t);
            int first = Integer.parseInt(tileset.getAttribute("firstgid"));
            NodeList tiles = tileset.getElementsByTagName("tile");
            for (int i = 0; i < tiles.getLength(); i++) {
                Element element = (Element) tiles.item(i);
                String type = property(element, "type");
                if (type != null) types.put(first + Integer.parseInt(element.getAttribute("id")), TileType.fromName(type));
            }
        }
        NodeList layers = map.getElementsByTagName("layer");
        for (int l = 0; l < layers.getLength(); l++) {
            Element layer = (Element) layers.item(l);
            if (!Level.TERRAIN_LAYER.equals(layer.getAttribute("name"))) continue;
            byte[] raw = inflate(Base64.getDecoder().decode(layer.getElementsByTagName("data").item(0).getTextContent().trim()));
            TileGrid grid = new TileGrid(width, height, tile);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int index = (row * width + column) * 4;
                    int gid = (raw[index] & 0xff) | (raw[index + 1] & 0xff) << 8 | (raw[index + 2] & 0xff) << 16 | (raw[index + 3] & 0xff) << 24;
                    TileType type = types.get(gid & 0x1FFFFFFF);
                    if (type != null) grid.set(column, height - 1 - row, type);
                }
            }
            return grid;
        }
        throw new IllegalStateException("no terrain layer");
    }

    private static byte[] inflate(byte[] data) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) out.write(buffer, 0, inflater.inflate(buffer));
        inflater.end();
        return out.toByteArray();
    }

    private static Map<String, Room> readRooms(Document map, float mapHeight) {
        Map<String, Room> rooms = new HashMap<>();
        for (Element object : objects(map, "rooms")) {
            float x = Float.parseFloat(object.getAttribute("x"));
            float y = Float.parseFloat(object.getAttribute("y"));
            float w = Float.parseFloat(object.getAttribute("width"));
            float h = Float.parseFloat(object.getAttribute("height"));
            String name = object.getAttribute("name");
            rooms.put(name, new Room(name, rooms.size(), property(object, "kind"), property(object, "region"), true,
                new Rectangle(x, mapHeight - y - h, w, h)));
        }
        return rooms;
    }

    private static List<Spawn> readSpawns(Document map, float mapHeight) {
        List<Spawn> spawns = new ArrayList<>();
        for (Element object : objects(map, "enemies")) {
            float x = Float.parseFloat(object.getAttribute("x"));
            float y = Float.parseFloat(object.getAttribute("y"));
            float w = Float.parseFloat(object.getAttribute("width"));
            float h = Float.parseFloat(object.getAttribute("height"));
            spawns.add(new Spawn(object.getAttribute("name"), property(object, "definition"), property(object, "room"),
                x + w / 2f, mapHeight - y - h, Float.parseFloat(property(object, "patrolMinX")),
                Float.parseFloat(property(object, "patrolMaxX")), !"LEFT".equals(property(object, "facing"))));
        }
        return spawns;
    }

    private static List<Element> objects(Document map, String layerName) {
        List<Element> list = new ArrayList<>();
        NodeList groups = map.getElementsByTagName("objectgroup");
        for (int g = 0; g < groups.getLength(); g++) {
            Element group = (Element) groups.item(g);
            if (!layerName.equals(group.getAttribute("name"))) continue;
            NodeList objects = group.getElementsByTagName("object");
            for (int i = 0; i < objects.getLength(); i++) list.add((Element) objects.item(i));
        }
        return list;
    }

    private static String property(Element object, String name) {
        NodeList properties = object.getElementsByTagName("property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            if (name.equals(property.getAttribute("name"))) return property.getAttribute("value");
        }
        return null;
    }

    private static void writeReport(Stats tactics, Stats direct) throws Exception {
        Files.createDirectories(REPORT.getParent());
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
            "MEDIUM map, %d enemies (%d hit-and-run), %.0f s per room; exposed = in the player's knife reach (estimate)%n",
            tactics.enemies, tactics.hitAndRun, SECONDS_PER_ROOM));
        String row = "%-26s %8s %8s %8s %9s %9s %8s%n";
        out.append(String.format(Locale.ROOT, row, "", "strikes", "contact", "exposed", "leftRoom", "onHazard", "fellOut"));
        for (Object[] line : new Object[][] {
            {"whole map, MEDIUM policy", tactics.strikes, tactics.contactFrames, tactics.exposedFrames, tactics.leftRoom.size(), tactics.onHazard.size(), tactics.fellOut.size()},
            {"whole map, direct only", direct.strikes, direct.contactFrames, direct.exposedFrames, direct.leftRoom.size(), direct.onHazard.size(), direct.fellOut.size()},
            {"regions 3-4, MEDIUM policy", tactics.lateStrikes, tactics.lateContactFrames, tactics.lateExposedFrames, "", "", ""},
            {"regions 3-4, direct only", direct.lateStrikes, direct.lateContactFrames, direct.lateExposedFrames, "", "", ""},
        }) {
            Object[] cells = new String[line.length];
            for (int i = 0; i < line.length; i++) cells[i] = String.valueOf(line[i]);
            out.append(String.format(Locale.ROOT, row, cells));
        }
        Files.writeString(REPORT, out.toString(), StandardCharsets.UTF_8);
    }
}
