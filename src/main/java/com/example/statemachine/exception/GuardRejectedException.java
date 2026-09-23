package com.example.statemachine.exception;

import java.util.List;

/**
 * Thrown when a transition cannot be taken because its guard returned
 * {@code false} (or threw). A guard exception is attached as {@link #getCause()}
 * and, per the required semantics, also means the transition is not allowed.
 */
public class GuardRejectedException extends StateMachineException {

    private final transient Object currentState;
    private final transient Object event;
    private final transient List<Object> allowedEvents;

    public GuardRejectedException(Object currentState,
                                  Object event,
                                  List<Object> allowedEvents,
                                  String reason,
                                  Throwable cause) {
        super(String.format(
                "Transition from state '%s' on event '%s' rejected by guard: %s. "
                        + "Currently allowed events: %s",
                currentState, event, reason, allowedEvents), cause);
        this.currentState = currentState;
        this.event = event;
        this.allowedEvents = List.copyOf(allowedEvents);
    }

    public Object currentState() {
        return currentState;
    }

    public Object event() {
        return event;
    }

    public List<Object> allowedEvents() {
        return allowedEvents;
    }
}
