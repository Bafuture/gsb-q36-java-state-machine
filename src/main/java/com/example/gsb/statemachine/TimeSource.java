package com.example.gsb.statemachine;

import java.time.Instant;

/**
 * 时间来源抽象。生产环境使用 {@link #system()}，测试可注入假时钟。
 */
@FunctionalInterface
public interface TimeSource {

    Instant now();

    static TimeSource system() {
        return Instant::now;
    }
}
