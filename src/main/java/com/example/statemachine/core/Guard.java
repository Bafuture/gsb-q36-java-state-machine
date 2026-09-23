package com.example.statemachine.core;

/**
 * Guard predicate evaluated before a transition is taken.
 *
 * <p>Returning {@code false} rejects the transition. Throwing any exception is
 * also treated as "transition not allowed" (and is wrapped / rethrown by the
 * engine as a {@code GuardRejectedException}).
 */
@FunctionalInterface
public interface Guard<S, E> {

    boolean evaluate(TransitionContext<S, E> context) throws Exception;
}
