package com.example.gsb.statemachine;

/**
 * 迁移动作（action）执行失败时抛出。此时状态保证未发生变更。
 */
public class TransitionFailedException extends RuntimeException {

    public TransitionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
