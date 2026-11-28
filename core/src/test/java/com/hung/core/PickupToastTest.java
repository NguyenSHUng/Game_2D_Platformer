package com.hung.core;

import com.hung.combat.PickupType;
import com.hung.world.event.PickupCollectedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The toast shown for a picked item says what it actually gave. */
class PickupToastTest {

    @Test
    void healthAndAmmoMessages() {
        PickupCollectedEvent event = new PickupCollectedEvent();
        assertEquals("HP", GameScreen.pickupTag(event.set(PickupType.HEALTH_PACK, 10)));
        assertEquals("+10 HP", GameScreen.pickupText(event));
        assertEquals("AMMO", GameScreen.pickupTag(event.set(PickupType.PISTOL_AMMO, 24)));
        assertEquals("+24 pistol rounds", GameScreen.pickupText(event));
        assertEquals("AMMO", GameScreen.pickupTag(event.set(PickupType.RIFLE_AMMO, 12)));
        assertEquals("+12 rifle rounds", GameScreen.pickupText(event));
    }
}
