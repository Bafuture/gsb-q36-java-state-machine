package com.example.statemachine.record;

import java.util.Objects;

/**
 * Immutable audit entry for one transition attempt.
 *
 * <p>{@code target} is {@code null} for rejected attempts (no defined
 * transition, guard failed, guard/action threw). {@code durationNanos} measures
 * the whole attempt, including guard/action execution.
 */
public final class TransitionRecord<S, E> {

    private final long sequence;
    private final long timestampMillis;
    private final S source;
    private final E event;
    private final S target;
    private final boolean rejected;
    private final boolean timeout;
    private final long durationNanos;
    private final String failureReason;

    public TransitionRecord(long sequence,
                            long timestampMillis,
                            S source,
                            E event,
                            S target,
                            boolean rejected,
                            boolean timeout,
                            long durationNanos,
                            String failureReason) {
        this.sequence = sequence;
        this.timestampMillis = timestampMillis;
        this.source = Objects.requireNonNull(source, "source");
        this.event = Objects.requireNonNull(event, "event");
        this.target = target;
        this.rejected = rejected;
        this.timeout = timeout;
        this.durationNanos = durationNanos;
        this.failureReason = failureReason;
    }

    public long sequence() {
        return sequence;
    }

    public long timestampMillis() {
        return timestampMillis;
    }

    public S source() {
        return source;
    }

    public E event() {
        return event;
    }

    /** @return target state, or {@code null} when the attempt was rejected. */
    public S target() {
        return target;
    }

    public boolean rejected() {
        return rejected;
    }

    public boolean timeout() {
        return timeout;
    }

    public long durationNanos() {
        return durationNanos;
    }

    public String failureReason() {
        return failureReason;
    }

    @Override
    public String toString() {
        return "#" + sequence + " " + source + " --" + event + "--> "
                + (target != null ? target : "<rejected>")
                + (timeout ? " [timeout]" : "")
                + " " + durationNanos + "ns";
    }
}
