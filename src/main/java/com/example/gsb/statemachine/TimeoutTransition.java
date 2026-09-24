package com.example.gsb.statemachine;

import java.time.Duration;
import java.util.Objects;

/**
 * 超时迁移配置：停留在某状态超过 timeout 后，自动迁往 target。
 */
record TimeoutTransition<S>(S state, Duration timeout, S target) {

    TimeoutTransition {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(target, "target");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }
}
