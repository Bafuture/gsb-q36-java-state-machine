package com.example.statemachine.core;

/**
 * Abstraction over "current time" so that timeout behaviour can be tested
 * deterministically without sleeping.
 */
@FunctionalInterface
public interface Clock {

    /** Returns the current time in milliseconds since the epoch. */
    long nowMillis();
}
