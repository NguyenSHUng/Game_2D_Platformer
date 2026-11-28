package com.hung.combat;

import com.hung.component.HealthComponent;
import com.hung.entity.Entity;
import com.hung.entity.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The health pack rule on the player's 100 hit points. */
class PickupsTest {

    private static Player playerWith(int health) {
        Player player = new Player();
        HealthComponent component = new HealthComponent(100);
        component.setHealthClamped(health);
        player.setHealth(component);
        return player;
    }

    @Test
    void aHealthPackRestores25() {
        Player player = playerWith(50);
        assertEquals(25, Pickups.applyHealthPack(player));
        assertEquals(75, player.getHealth().getHealth());
    }

    @Test
    void nearlyFullTakesOnlyWhatIsMissing() {
        Player player = playerWith(90);
        assertEquals(10, Pickups.applyHealthPack(player));
        assertEquals(100, player.getHealth().getHealth());
    }

    @Test
    void fullOrDeadPlayersLeaveItOnTheGround() {
        assertEquals(0, Pickups.applyHealthPack(playerWith(100)));
        Player dead = playerWith(0);
        assertEquals(0, Pickups.applyHealthPack(dead));
        assertEquals(0, dead.getHealth().getHealth());
    }

    @Test
    void anEntityWithoutHealthTakesNothing() {
        assertEquals(0, Pickups.applyHealthPack(new Entity()));
    }

    @Test
    void aQuarterOfThePlayersHealth() {
        assertEquals(25, Pickups.HEALTH_PACK_HEAL);
    }
}
