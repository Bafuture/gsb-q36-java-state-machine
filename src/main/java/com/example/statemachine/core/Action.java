package com.example.statemachine.core;

/**
 * Side effect executed while a transition is being applied.
 *
 * <p>If an action throws, the state is <strong>not</strong> changed: the action
 * runs before the new state is persisted.
 */
@FunctionalInterface
public interface Action<S, E> {

    void execute(TransitionContext<S, E> context) throws Exception;
}
