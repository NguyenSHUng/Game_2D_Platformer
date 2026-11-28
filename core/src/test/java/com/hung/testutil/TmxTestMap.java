package com.hung.testutil;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.hung.world.Level;
import com.hung.world.Room;
import com.hung.world.TileGrid;
import com.hung.world.TileType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;

/**
 * A real map file read without LibGDX, the way WorldLoader / RoomLoader see it: the collision grid,
 * the rooms (y up), the objects of a type and the tile layers' cells.
 */
public final class TmxTestMap {

    /** One object of an object layer, rectangle in world units with y up. */
    public record MapObject(int id, String name, String type, Rectangle rect, Map<String, String> properties) {
    }

    public final String mapPath;
    public final TileGrid grid;
    public final Level level;
    public final List<MapObject> objects = new ArrayList<>();
    private final Document document;
    private final int width;
    private final int height;
    private final float tile;

    public TmxTestMap(String mapPath) throws Exception {
        this.mapPath = mapPath;
        document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("..", "assets", mapPath).toFile());
        Element root = document.getDocumentElement();
        width = Integer.parseInt(root.getAttribute("width"));
        height = Integer.parseInt(root.getAttribute("height"));
        tile = Float.parseFloat(root.getAttribute("tilewidth"));
        grid = readTerrain();
        level = new Level(mapPath, null, grid, null, new Vector2(), null, new Array<Rectangle>());
        readObjects();
        int index = 0;
        for (MapObject object : objectsOf("ROOM")) {
            level.addRoom(new Room(object.name(), index++, object.properties().get("kind"), object.properties().get("region"),
                true, object.rect()));
        }
    }

    public List<MapObject> objectsOf(String type) {
        List<MapObject> list = new ArrayList<>();
        for (MapObject object : objects) if (type.equals(object.type())) list.add(object);
        return list;
    }

    /** Global tile id (flip bits removed) of a tile layer's cell, column / row with row 0 at the bottom; 0 = empty. */
    public int cell(String layerName, int column, int row) throws Exception {
        NodeList layers = document.getElementsByTagName("layer");
        for (int l = 0; l < layers.getLength(); l++) {
            Element layer = (Element) layers.item(l);
            if (!layerName.equals(layer.getAttribute("name"))) continue;
            byte[] raw = data(layer);
            int index = ((height - 1 - row) * width + column) * 4;
            return gid(raw, index);
        }
        return 0;
    }

    private TileGrid readTerrain() throws Exception {
        Map<Integer, TileType> types = new HashMap<>();
        NodeList tilesets = document.getElementsByTagName("tileset");
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
        NodeList layers = document.getElementsByTagName("layer");
        for (int l = 0; l < layers.getLength(); l++) {
            Element layer = (Element) layers.item(l);
            if (!Level.TERRAIN_LAYER.equals(layer.getAttribute("name"))) continue;
            byte[] raw = data(layer);
            TileGrid terrain = new TileGrid(width, height, tile);
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    TileType type = types.get(gid(raw, (row * width + column) * 4));
                    if (type != null) terrain.set(column, height - 1 - row, type);
                }
            }
            return terrain;
        }
        throw new IllegalStateException("no terrain layer");
    }

    private void readObjects() {
        float mapHeight = height * tile;
        NodeList elements = document.getElementsByTagName("object");
        for (int i = 0; i < elements.getLength(); i++) {
            Element object = (Element) elements.item(i);
            float w = object.hasAttribute("width") ? Float.parseFloat(object.getAttribute("width")) : 0f;
            float h = object.hasAttribute("height") ? Float.parseFloat(object.getAttribute("height")) : 0f;
            float x = Float.parseFloat(object.getAttribute("x"));
            float y = Float.parseFloat(object.getAttribute("y"));
            Map<String, String> properties = new HashMap<>();
            NodeList list = object.getElementsByTagName("property");
            for (int p = 0; p < list.getLength(); p++) {
                Element property = (Element) list.item(p);
                properties.put(property.getAttribute("name"), property.getAttribute("value"));
            }
            objects.add(new MapObject(Integer.parseInt(object.getAttribute("id")), object.getAttribute("name"),
                object.getAttribute("type"), new Rectangle(x, mapHeight - y - h, w, h), properties));
        }
    }

    private static int gid(byte[] raw, int index) {
        int gid = (raw[index] & 0xff) | (raw[index + 1] & 0xff) << 8 | (raw[index + 2] & 0xff) << 16 | (raw[index + 3] & 0xff) << 24;
        return gid & 0x1FFFFFFF;
    }

    private static byte[] data(Element layer) throws Exception {
        byte[] compressed = Base64.getDecoder().decode(layer.getElementsByTagName("data").item(0).getTextContent().trim());
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) out.write(buffer, 0, inflater.inflate(buffer));
        inflater.end();
        return out.toByteArray();
    }

    private static String property(Element object, String name) {
        NodeList properties = object.getElementsByTagName("property");
        for (int i = 0; i < properties.getLength(); i++) {
            Element property = (Element) properties.item(i);
            if (name.equals(property.getAttribute("name"))) return property.getAttribute("value");
        }
        return null;
    }
}
