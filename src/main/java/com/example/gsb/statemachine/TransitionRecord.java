package com.example.gsb.statemachine;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次迁移尝试的记录。
 *
 * @param from       源状态
 * @param event      触发事件；超时自动迁移时为 {@code null}
 * @param to         目标状态；被拒绝时为 {@code null}
 * @param elapsed    本次迁移尝试的耗时
 * @param accepted   是否真正发生了迁移
 * @param rejection  拒绝原因；成功时为 {@code null}
 */
public record TransitionRecord<S, E>(
        S from,
        E event,
        S to,
        Duration elapsed,
        boolean accepted,
        String rejection) {

    public TransitionRecord {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(elapsed, "elapsed");
        if (accepted) {
            Objects.requireNonNull(to, "to must be present when accepted");
        }
    }

    public boolean rejected() {
        return !accepted;
    }
}
