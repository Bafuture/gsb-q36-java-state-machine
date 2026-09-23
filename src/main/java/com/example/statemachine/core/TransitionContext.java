package com.example.statemachine.core;

import java.util.Objects;

/**
 * Immutable information handed to a {@link Guard} or {@link Action} during a
 * transition attempt.
 *
 * @param <S> state type
 * @param <E> event type
 */
public final class TransitionContext<S, E> {

    private final S source;
    private final E event;
    private final S target;

    public TransitionContext(S source, E event, S target) {
        this.source = Objects.requireNonNull(source, "source");
        this.event = Objects.requireNonNull(event, "event");
        this.target = Objects.requireNonNull(target, "target");
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

    @Override
    public String toString() {
        return source + " --" + event + "--> " + target;
    }
}
