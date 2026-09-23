package com.example.statemachine.persistence;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple thread-safe {@link StateStore} backed by an {@link AtomicReference}.
 * Intended for tests and single-process demos; production code should provide
 * a database-backed implementation of the SPI.
 */
public final class InMemoryStateStore<S> implements StateStore<S> {

    private final AtomicReference<S> state = new AtomicReference<>();

    public InMemoryStateStore() {
    }

    public InMemoryStateStore(S initialState) {
        state.set(initialState);
    }

    @Override
    public Optional<S> load() {
        return Optional.ofNullable(state.get());
    }

    @Override
    public void save(S currentState, S newState) {
        if (newState == null) {
            throw new IllegalArgumentException("newState must not be null");
        }
        state.set(newState);
    }
}
