package com.hung.core;

/**
 * Receives one type of event from the {@link EventBus}.
 *
 * @param <T> the event class it listens to
 */
@FunctionalInterface
public interface EventListener<T> {

    /** Called during {@link EventBus#publish}; copy what you keep, the event object is reused. */
    void onEvent(T event);
}
