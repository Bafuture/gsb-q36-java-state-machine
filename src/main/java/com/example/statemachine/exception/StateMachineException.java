package com.example.statemachine.exception;

/** Base type for all state-machine-engine errors. */
public class StateMachineException extends RuntimeException {

    public StateMachineException(String message) {
        super(message);
    }

    public StateMachineException(String message, Throwable cause) {
        super(message, cause);
    }
}
