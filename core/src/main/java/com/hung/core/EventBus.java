package com.hung.core;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Observer hub that lets systems talk without knowing each other: a publisher (e.g. DoorSystem)
 * fills an event object and publishes it; every listener subscribed to that event class
 * (camera, minimap, …) is called in subscription order. Listeners never see the publisher.
 *
 * Publishers reuse their event objects (no allocation per publish), so listeners must copy
 * whatever they want to keep. Listener lists are copy-on-write: subscribing or unsubscribing
 * (rare) builds a new list, so doing it from inside a listener is safe and publish never allocates.
 */
public class EventBus {

    private final ObjectMap<Class<?>, Array<EventListener<?>>> listeners = new ObjectMap<>();

    public <T> void subscribe(Class<T> type, EventListener<? super T> listener) {
        Array<EventListener<?>> current = listeners.get(type);
        if (current != null && current.contains(listener, true)) return;
        Array<EventListener<?>> next = current == null ? new Array<>() : new Array<>(current);
        next.add(listener);
        listeners.put(type, next);
    }

    public <T> void unsubscribe(Class<T> type, EventListener<? super T> listener) {
        Array<EventListener<?>> current = listeners.get(type);
        if (current == null || !current.contains(listener, true)) return;
        Array<EventListener<?>> next = new Array<>(current);
        next.removeValue(listener, true);
        listeners.put(type, next);
    }

    /** Calls every listener of the event's exact class. */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        Array<EventListener<?>> list = listeners.get(event.getClass());
        if (list == null) return;
        for (int i = 0; i < list.size; i++) {
            ((EventListener<T>) list.get(i)).onEvent(event);
        }
    }
}
