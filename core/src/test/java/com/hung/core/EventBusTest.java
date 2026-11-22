package com.hung.core;

import com.badlogic.gdx.utils.Array;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventBusTest {

    private static class Ping {
    }

    private static class Pong {
    }

    @Test
    void deliversToListenersOfTheExactClassInSubscriptionOrder() {
        EventBus bus = new EventBus();
        Array<String> calls = new Array<>();
        bus.subscribe(Ping.class, e -> calls.add("a"));
        bus.subscribe(Ping.class, e -> calls.add("b"));
        bus.subscribe(Pong.class, e -> calls.add("pong"));
        bus.publish(new Ping());
        assertEquals("[a, b]", calls.toString());
    }

    @Test
    void ignoresADuplicateSubscription() {
        EventBus bus = new EventBus();
        int[] count = {0};
        EventListener<Ping> listener = e -> count[0]++;
        bus.subscribe(Ping.class, listener);
        bus.subscribe(Ping.class, listener);
        bus.publish(new Ping());
        assertEquals(1, count[0]);
    }

    @Test
    void unsubscribingDuringPublishIsSafeAndTakesEffectNextTime() {
        EventBus bus = new EventBus();
        Array<String> calls = new Array<>();
        EventListener<Ping> second = e -> calls.add("second");
        bus.subscribe(Ping.class, e -> {
            calls.add("first");
            bus.unsubscribe(Ping.class, second);
        });
        bus.subscribe(Ping.class, second);
        bus.publish(new Ping());
        bus.publish(new Ping());
        assertEquals("[first, second, first]", calls.toString());
    }
}
