package com.example.gsb.statemachine;

/**
 * 迁移动作：在守卫通过后、状态落库前执行。
 * 若动作抛出异常，状态保证不发生变更。
 *
 * @param <C> 业务上下文类型，无上下文时可使用 {@link Void}
 */
@FunctionalInterface
public interface Action<C> {

    void execute(C context) throws Exception;
}
