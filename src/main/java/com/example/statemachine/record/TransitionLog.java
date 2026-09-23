package com.example.statemachine.record;

import java.util.List;
import java.util.Optional;

/**
 * Read/write audit log of transition attempts.
 */
public interface TransitionLog<S, E> {

    void append(TransitionRecord<S, E> record);

    /** @return all records in append order (snapshot). */
    List<TransitionRecord<S, E>> findAll();

    /** @return records whose source state equals the given state. */
    List<TransitionRecord<S, E>> findBySource(S source);

    /** @return records triggered by the given event. */
    List<TransitionRecord<S, E>> findByEvent(E event);

    /** @return rejected records only. */
    List<TransitionRecord<S, E>> findRejected();

    /** @return the most recently appended record, if any. */
    Optional<TransitionRecord<S, E>> findLast();
}
