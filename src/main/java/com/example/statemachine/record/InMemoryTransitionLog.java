package com.example.statemachine.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe in-memory {@link TransitionLog}. */
public final class InMemoryTransitionLog<S, E> implements TransitionLog<S, E> {

    private final CopyOnWriteArrayList<TransitionRecord<S, E>> records = new CopyOnWriteArrayList<>();

    @Override
    public void append(TransitionRecord<S, E> record) {
        records.add(Objects.requireNonNull(record, "record"));
    }

    @Override
    public List<TransitionRecord<S, E>> findAll() {
        return List.copyOf(records);
    }

    @Override
    public List<TransitionRecord<S, E>> findBySource(S source) {
        return filter(record -> Objects.equals(record.source(), source));
    }

    @Override
    public List<TransitionRecord<S, E>> findByEvent(E event) {
        return filter(record -> Objects.equals(record.event(), event));
    }

    @Override
    public List<TransitionRecord<S, E>> findRejected() {
        return filter(TransitionRecord::rejected);
    }

    @Override
    public Optional<TransitionRecord<S, E>> findLast() {
        return records.isEmpty()
                ? Optional.empty()
                : Optional.of(records.get(records.size() - 1));
    }

    private List<TransitionRecord<S, E>> filter(java.util.function.Predicate<TransitionRecord<S, E>> p) {
        List<TransitionRecord<S, E>> result = new ArrayList<>();
        for (TransitionRecord<S, E> record : records) {
            if (p.test(record)) {
                result.add(record);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
