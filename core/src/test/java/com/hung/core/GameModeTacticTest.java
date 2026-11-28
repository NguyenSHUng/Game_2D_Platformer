package com.hung.core;

import com.hung.ai.tactic.HitAndRunTactic;
import com.hung.ai.tactic.TacticPolicy;
import com.hung.entity.enemy.EnemySkill;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Each mode's tactics on its real map: every enemy's room has a region, EASY and HARD hunters all
 * chase directly, MEDIUM's hit and run in regions 3–4. The skill (numbers) stays the mode's own.
 */
class GameModeTacticTest {

    @Test
    void modesKeepTheirSkillAndGetTheirPolicy() {
        assertSame(EnemySkill.EASY, GameMode.EASY.enemySkill);
        assertSame(EnemySkill.MEDIUM, GameMode.MEDIUM.enemySkill);
        assertSame(EnemySkill.HARD, GameMode.HARD.enemySkill);
        assertEquals(TacticPolicy.DIRECT_ONLY, GameMode.EASY.tacticPolicy);
        assertEquals(new TacticPolicy(3), GameMode.MEDIUM.tacticPolicy);
        assertEquals(TacticPolicy.DIRECT_ONLY, GameMode.HARD.tacticPolicy);
    }

    @Test
    void hitAndRunHuntersPerMap() throws Exception {
        assertEquals(0, hitAndRunCount(GameMode.EASY));
        assertEquals(12 + 18, hitAndRunCount(GameMode.MEDIUM));
        assertEquals(0, hitAndRunCount(GameMode.HARD));
    }

    /** Enemies in the mode's map whose room's region makes the policy pick HitAndRun (as WorldLoader does). */
    private static int hitAndRunCount(GameMode mode) throws Exception {
        Document map = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(Path.of("..", "assets", mode.mapPath).toFile());
        Map<String, String> regionOfRoom = new HashMap<>();
        int count = 0;
        NodeList groups = map.getElementsByTagName("objectgroup");
        for (int g = 0; g < groups.getLength(); g++) {
            Element group = (Element) groups.item(g);
            if (!"rooms".equals(group.getAttribute("name"))) continue;
            NodeList rooms = group.getElementsByTagName("object");
            for (int r = 0; r < rooms.getLength(); r++) {
                Element room = (Element) rooms.item(r);
                regionOfRoom.put(room.getAttribute("name"), property(room, "region"));
            }
        }
        for (int g = 0; g < groups.getLength(); g++) {
            Element group = (Element) groups.item(g);
            if (!"enemies".equals(group.getAttribute("name"))) continue;
            NodeList enemies = group.getElementsByTagName("object");
            for (int e = 0; e < enemies.getLength(); e++) {
                Element enemy = (Element) enemies.item(e);
                String region = regionOfRoom.get(property(enemy, "room"));
                assertNotNull(region, mode + ": " + enemy.getAttribute("name") + " has no region");
                if (mode.tacticPolicy.create(region) instanceof HitAndRunTactic) count++;
            }
        }
        return count;
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
