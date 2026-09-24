package com.example.gsb.statemachine;

import java.util.List;

/**
 * 未定义的「状态 + 事件」组合被拒绝时抛出。
 * 错误信息包含当前状态、事件与当前允许的事件列表。
 */
public class IllegalTransitionException extends RuntimeException {

    private final Object currentState;
    private final Object event;
    private final List<?> allowedEvents;

    public IllegalTransitionException(Object currentState, Object event, List<?> allowedEvents) {
        super("Illegal transition: state=" + currentState
                + ", event=" + event
                + ", allowedEvents=" + allowedEvents);
        this.currentState = currentState;
        this.event = event;
        this.allowedEvents = List.copyOf(allowedEvents);
    }

    public Object currentState() {
        return currentState;
    }

    public Object event() {
        return event;
    }

    public List<?> allowedEvents() {
        return allowedEvents;
    }
}
