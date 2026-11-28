package com.hung.rendering;

import com.badlogic.gdx.math.Vector2;
import com.hung.component.AttackComponent;
import com.hung.component.ColliderComponent;
import com.hung.component.HealthComponent;
import com.hung.component.MovementComponent;
import com.hung.entity.player.Player;
import com.hung.world.PhysicsSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The character sheet's player column: health card, then movement / body and combat / location cards that fit the panel. */
class PlayerColumnTest {

    private static final float TILE = 16f;

    private static Player player() {
        PhysicsSettings physics = physics();
        Player player = new Player();
        player.getTransform().position.set(149 * TILE + 3f, 50 * TILE);
        MovementComponent movement = new MovementComponent();
        movement.moveSpeed = physics.playerMoveSpeed;
        movement.jumpSpeed = physics.playerJumpSpeed;
        movement.climbSpeed = physics.playerClimbSpeed;
        player.setMovement(movement);
        ColliderComponent body = new ColliderComponent();
        body.width = 7f;
        body.height = 27f;
        body.crouchHeight = 22f;
        player.setCollider(body);
        player.setHealth(new HealthComponent(100));
        AttackComponent attack = new AttackComponent();
        attack.damage = 10;
        player.setAttack(attack);
        return player;
    }

    private static PhysicsSettings physics() {
        return new PhysicsSettings(TILE, PhysicsSettings.DEFAULT_MOVE_SPEED_TILES,
            PhysicsSettings.DEFAULT_JUMP_HEIGHT_TILES, PhysicsSettings.DEFAULT_JUMP_DISTANCE_TILES);
    }

    private static List<StatsPanel.PlayerGroup> groups() {
        return StatsPanel.playerGroups(player(), physics(), TILE, new Vector2(141 * TILE, 50 * TILE));
    }

    @Test
    void threeCardsWithTheFactsTheSheetAlwaysHad() {
        List<StatsPanel.PlayerGroup> groups = groups();
        assertEquals(List.of("MOVEMENT", "BODY & COMBAT", "LOCATION"), groups.stream().map(StatsPanel.PlayerGroup::title).toList());
        assertEquals(List.of("Walk / climb", "Jump", "Air time"), labels(groups.get(0)));
        assertEquals("3.5 high, 6.0 far", groups.get(0).lines().get(1).value());
        assertEquals(List.of("Hit box", "Invulnerable", "Enemy touch", "Melee"), labels(groups.get(1)));
        assertEquals("7 x 27 u, crouch 22", groups.get(1).lines().get(0).value());
        assertEquals("10 dmg per swing", groups.get(1).lines().get(3).value());
        assertEquals(List.of("Tile", "Respawn"), labels(groups.get(2)));
        assertEquals("149, 50", groups.get(2).lines().get(0).value());
        assertEquals("141, 50", groups.get(2).lines().get(1).value());
    }

    @Test
    void theCardsFitThePanel() {
        List<StatsPanel.PlayerGroup> groups = groups();
        // In game LOCATION also has its Stance line: one line taller than here
        StatsPanel.PlayerLine any = new StatsPanel.PlayerLine("Stance", "pistol");
        float oneLine = StatsPanel.groupHeight(new StatsPanel.PlayerGroup("", List.of(any, any)))
            - StatsPanel.groupHeight(new StatsPanel.PlayerGroup("", List.of(any)));
        float full = StatsPanel.playerColumnHeight(true, groups) + oneLine;
        assertTrue(full <= StatsPanel.playerColumnRoom(), full + " > " + StatsPanel.playerColumnRoom());
    }

    @Test
    void theHealthTextNoLongerSitsOnItsBar() {
        assertTrue(StatsPanel.healthTextToBarGap() > 0f);
    }

    private static List<String> labels(StatsPanel.PlayerGroup group) {
        return group.lines().stream().map(StatsPanel.PlayerLine::label).toList();
    }
}
