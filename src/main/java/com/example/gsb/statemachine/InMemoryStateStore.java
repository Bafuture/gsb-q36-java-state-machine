package com.example.gsb.statemachine;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于内存的 {@link StateStore} 实现，适用于单测与原型验证。
 */
public final class InMemoryStateStore<S> implements StateStore<S> {

    private final AtomicReference<StateSnapshot<S>> holder = new AtomicReference<>();

    @Override
    public Optional<StateSnapshot<S>> load() {
        return Optional.ofNullable(holder.get());
    }

    @Override
    public void save(StateSnapshot<S> snapshot) {
        holder.set(snapshot);
    }
}
