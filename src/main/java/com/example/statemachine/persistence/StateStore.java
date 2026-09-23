package com.example.statemachine.persistence;

import java.util.Optional;

/**
 * SPI for loading and persisting the current state of a state-machine
 * instance.
 *
 * <p>Implementations are responsible for their own concurrency and
 * transactional guarantees. A database implementation typically stores the
 * state together with the id of the business object (one row per instance),
 * and {@link #save(Object, Object)} is executed inside the same transaction as
 * the surrounding business write; the state change of an action-based
 * transition is only persisted <em>after</em> the action succeeds, so no
 * compensating update is needed when the action fails.
 *
 * @param <S> state type
 */
public interface StateStore<S> {

    /**
     * @return the persisted current state, or empty if no state has been
     *         stored yet for this instance.
     */
    Optional<S> load();

    /**
     * Persists the new current state.
     *
     * @param currentState the state that is being left (may be {@code null}
     *                     for the very first initialization)
     * @param newState     the new current state (never {@code null})
     */
    void save(S currentState, S newState);
}
