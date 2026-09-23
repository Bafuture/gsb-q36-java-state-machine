package com.example.statemachine.support;

import com.example.statemachine.core.Clock;

/** Programmatically controllable clock for deterministic timeout tests. */
public final class ManualClock implements Clock {

    private long now;

    public ManualClock() {
        this(0L);
    }

    public ManualClock(long startMillis) {
        this.now = startMillis;
    }

    @Override
    public long nowMillis() {
        return now;
    }

    public void advanceMillis(long delta) {
        this.now += delta;
    }
}
