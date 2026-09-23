package com.example.statemachine.time;

import com.example.statemachine.core.Clock;

/** Clock backed by the wall clock of the running JVM. */
public final class SystemClock implements Clock {

    public static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
    }

    @Override
    public long nowMillis() {
        return System.currentTimeMillis();
    }
}
