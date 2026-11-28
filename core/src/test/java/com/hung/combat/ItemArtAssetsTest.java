package com.hung.combat;

import com.badlogic.gdx.utils.Json;
import com.hung.combat.definition.EffectDefinition;
import com.hung.combat.definition.ItemArtDefinition;
import com.hung.core.GameMode;
import com.hung.util.Constants;
import com.hung.world.system.ChestSystem;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loot art on disk matches its manifest and the game: strip sizes, the chest opening's length,
 * items standing on their bottom-centre pixel, and every chest strip starting with the very tile
 * the maps draw for that chest.
 */
class ItemArtAssetsTest {

    private static final String ASSETS = "../assets/";

    private static ItemArtDefinition manifest() throws Exception {
        return new Json().fromJson(ItemArtDefinition.class, Files.readString(Path.of(ASSETS + Constants.ITEM_ART_PATH), StandardCharsets.UTF_8));
    }

    private static BufferedImage image(String path) throws Exception {
        return ImageIO.read(new File(ASSETS + path));
    }

    @Test
    void everyStripExistsWithTheSizeItDeclares() throws Exception {
        ItemArtDefinition art = manifest();
        EffectDefinition[] strips = {art.chestWoodOpening, art.chestGoldOpening, art.healthPack, art.pistolAmmo,
            art.rifleAmmo, art.lootLand, art.pickup};
        for (EffectDefinition strip : strips) {
            assertNotNull(strip);
            BufferedImage image = image(strip.texture);
            assertEquals(strip.frameWidth * strip.frameCount, image.getWidth(), strip.texture);
            assertEquals(strip.frameHeight, image.getHeight(), strip.texture);
            assertEquals(strip.frameCount, strip.durations.length, strip.texture);
        }
    }

    @Test
    void chestsAreTileSizedAndOpenInOpenSeconds() throws Exception {
        for (EffectDefinition chest : new EffectDefinition[] {manifest().chestWoodOpening, manifest().chestGoldOpening}) {
            assertEquals(16, chest.frameWidth);
            assertEquals(16, chest.frameHeight);
            int millis = 0;
            for (int duration : chest.durations) millis += duration;
            assertEquals(Math.round(ChestSystem.OPEN_SECONDS * 1000f), millis);
            assertTrue(!chest.loop, "the opening holds its last frame");
        }
    }

    @Test
    void itemsStandOnTheirBottomCentrePixel() throws Exception {
        for (EffectDefinition item : new EffectDefinition[] {manifest().healthPack, manifest().pistolAmmo, manifest().rifleAmmo}) {
            assertEquals(item.frameWidth / 2f - 0.5f, item.origin.x, 1e-6);
            assertEquals(item.frameHeight - 0.5f, item.origin.y, 1e-6);
            BufferedImage image = image(item.texture);
            boolean isBottomUsed = false;
            for (int x = 0; x < item.frameWidth; x++) isBottomUsed |= (image.getRGB(x, item.frameHeight - 1) >>> 24) != 0;
            assertTrue(isBottomUsed, item.texture + " floats above the floor");
        }
    }

    /** For each map: the tile under a wood and a gold chest equals frame 0 of that tier's strip. */
    @Test
    void chestStripsStartWithTheMapsChestTiles() throws Exception {
        BufferedImage wood = image(manifest().chestWoodOpening.texture);
        BufferedImage gold = image(manifest().chestGoldOpening.texture);
        for (GameMode mode : GameMode.values()) {
            Path mapFile = Path.of(ASSETS + mode.mapPath);
            Document map = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(mapFile.toFile());
            com.hung.testutil.TmxTestMap tmx = new com.hung.testutil.TmxTestMap(mode.mapPath);
            Element props = tileset(map, "props");
            int first = Integer.parseInt(props.getAttribute("firstgid"));
            int columns = Integer.parseInt(props.getAttribute("columns"));
            int margin = Integer.parseInt(props.getAttribute("margin"));
            int spacing = Integer.parseInt(props.getAttribute("spacing"));
            BufferedImage sheet = ImageIO.read(mapFile.getParent().resolve(
                ((Element) props.getElementsByTagName("image").item(0)).getAttribute("source")).toFile());
            int checked = 0;
            for (com.hung.testutil.TmxTestMap.MapObject chest : tmx.objectsOf("CHEST")) {
                int column = (int) ((chest.rect().x + chest.rect().width / 2f) / 16f);
                int row = (int) ((chest.rect().y + 1f) / 16f);
                int index = tmx.cell("props", column, row) - first;
                int tileX = margin + (index % columns) * (16 + spacing);
                int tileY = margin + (index / columns) * (16 + spacing);
                BufferedImage strip = ChestTier.fromName(chest.properties().get("tier")) == ChestTier.GOLD ? gold : wood;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        assertEquals(sheet.getRGB(tileX + x, tileY + y), strip.getRGB(x, y),
                            mode + " chest " + chest.id() + " pixel " + x + "," + y);
                    }
                }
                checked++;
            }
            assertTrue(checked > 0);
        }
    }

    private static Element tileset(Document map, String name) {
        NodeList tilesets = map.getElementsByTagName("tileset");
        for (int i = 0; i < tilesets.getLength(); i++) {
            Element tileset = (Element) tilesets.item(i);
            if (name.equals(tileset.getAttribute("name"))) return tileset;
        }
        throw new IllegalStateException("no " + name + " tileset");
    }
}
