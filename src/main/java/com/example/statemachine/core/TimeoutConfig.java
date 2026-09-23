package com.example.statemachine.core;

import java.util.Objects;

/**
 * Timeout configuration for one state: if the machine stays in {@code state}
 * for at least {@code timeoutMillis} milliseconds, it may automatically move
 * to {@code targetState} when the timeout event is processed.
 */
public final class TimeoutConfig<S, E> {

    private final S state;
    private final long timeoutMillis;
    private final E timeoutEvent;
    private final S targetState;

    public TimeoutConfig(S state, long timeoutMillis, E timeoutEvent, S targetState) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be > 0, got " + timeoutMillis);
        }
        this.state = Objects.requireNonNull(state, "state");
        this.timeoutMillis = timeoutMillis;
        this.timeoutEvent = Objects.requireNonNull(timeoutEvent, "timeoutEvent");
        this.targetState = Objects.requireNonNull(targetState, "targetState");
    }

    public S state() {
        return state;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public E timeoutEvent() {
        return timeoutEvent;
    }

    public S targetState() {
        return targetState;
    }
}
