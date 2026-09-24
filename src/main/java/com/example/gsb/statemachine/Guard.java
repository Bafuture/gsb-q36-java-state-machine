package com.example.gsb.statemachine;

/**
 * 守卫条件：迁移发生前进行校验。返回 {@code false} 或抛出异常都视为「不允许迁移」。
 *
 * @param <C> 业务上下文类型，无上下文时可使用 {@link Void}
 */
@FunctionalInterface
public interface Guard<C> {

    boolean test(C context);
}
