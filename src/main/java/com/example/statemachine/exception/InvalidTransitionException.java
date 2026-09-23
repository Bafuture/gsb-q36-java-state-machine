package com.example.statemachine.exception;

import java.util.List;

/**
 * Thrown when an event is fired in a state for which no transition (or no
 * transition whose guard passes) is defined. The message always carries the
 * current state, the rejected event and the list of currently allowed events.
 */
public class InvalidTransitionException extends StateMachineException {

    private final transient Object currentState;
    private final transient Object event;
    private final transient List<Object> allowedEvents;

    public InvalidTransitionException(Object currentState, Object event, List<Object> allowedEvents) {
        super(String.format(
                "Illegal transition: no allowed transition from state '%s' on event '%s'. "
                        + "Currently allowed events: %s",
                currentState, event, allowedEvents));
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
