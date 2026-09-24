package com.example.gsb.statemachine;

import java.time.Instant;
import java.util.Objects;

/**
 * 被持久化的状态快照：当前状态 + 进入该状态的时间（用于超时判定）。
 */
public record StateSnapshot<S>(S state, Instant enteredAt) {

    public StateSnapshot {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(enteredAt, "enteredAt");
    }
}
