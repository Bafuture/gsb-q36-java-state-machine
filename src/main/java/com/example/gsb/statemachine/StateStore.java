package com.example.gsb.statemachine;

import java.util.Optional;

/**
 * 状态持久化 SPI。实现方负责把状态快照存到任意介质（内存、数据库、Redis……）。
 *
 * <p>数据库接入示例：用一张表保存 {@code (machine_id, state, entered_at)}，
 * {@link #load()} 执行 SELECT，{@link #save(StateSnapshot)} 执行 UPSERT 即可。
 * 详见 README「状态持久化 SPI」一节。
 */
public interface StateStore<S> {

    /** 读取当前快照；从未保存过时返回 {@link Optional#empty()}。 */
    Optional<StateSnapshot<S>> load();

    /** 覆盖式保存当前快照。 */
    void save(StateSnapshot<S> snapshot);
}
