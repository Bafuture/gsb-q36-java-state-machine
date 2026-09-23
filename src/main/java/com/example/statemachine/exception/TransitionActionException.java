package com.example.statemachine.exception;

/**
 * Thrown when a transition action throws. The state is guaranteed to be
 * unchanged when this exception propagates: the action is executed before the
 * new state is persisted.
 */
public class TransitionActionException extends StateMachineException {

    private final transient Object sourceState;
    private final transient Object event;
    private final transient Object intendedTargetState;

    public TransitionActionException(Object sourceState,
                                     Object event,
                                     Object intendedTargetState,
                                     Throwable cause) {
        super(String.format(
                "Action of transition '%s' --%s--> '%s' threw '%s: %s'; state remains '%s'",
                sourceState, event, intendedTargetState,
                cause.getClass().getSimpleName(), cause.getMessage(), sourceState), cause);
        this.sourceState = sourceState;
        this.event = event;
        this.intendedTargetState = intendedTargetState;
    }

    public Object sourceState() {
        return sourceState;
    }

    public Object event() {
        return event;
    }

    public Object intendedTargetState() {
        return intendedTargetState;
    }
}
