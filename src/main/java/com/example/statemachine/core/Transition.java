package com.example.statemachine.core;

import java.util.Objects;

/**
 * A single declared transition: {@code source --event--> target}, optionally
 * guarded and/or carrying an action.
 */
public final class Transition<S, E> {

    private final S source;
    private final E event;
    private final S target;
    private final Guard<S, E> guard;
    private final Action<S, E> action;

    public Transition(S source,
                      E event,
                      S target,
                      Guard<S, E> guard,
                      Action<S, E> action) {
        this.source = Objects.requireNonNull(source, "source");
        this.event = Objects.requireNonNull(event, "event");
        this.target = Objects.requireNonNull(target, "target");
        this.guard = guard;
        this.action = action;
    }

    public S source() {
        return source;
    }

    public E event() {
        return event;
    }

    public S target() {
        return target;
    }

    public Guard<S, E> guard() {
        return guard;
    }

    public Action<S, E> action() {
        return action;
    }

    public boolean isGuarded() {
        return guard != null;
    }

    @Override
    public String toString() {
        return source + " --" + event + "--> " + target
                + (guard != null ? " [guarded]" : "")
                + (action != null ? " [action]" : "");
    }
}
