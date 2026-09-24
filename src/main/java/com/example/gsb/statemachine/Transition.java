package com.example.gsb.statemachine;

import java.util.Objects;

/**
 * 一条迁移定义：from + event -> to，可携带守卫与动作。
 */
final class Transition<S, E, C> {

    private final S from;
    private final E event;
    private final S to;
    private final Guard<C> guard;
    private final Action<C> action;

    Transition(S from, E event, S to, Guard<C> guard, Action<C> action) {
        this.from = Objects.requireNonNull(from, "from");
        this.event = Objects.requireNonNull(event, "event");
        this.to = Objects.requireNonNull(to, "to");
        this.guard = guard;
        this.action = action;
    }

    S from() {
        return from;
    }

    E event() {
        return event;
    }

    S to() {
        return to;
    }

    Guard<C> guard() {
        return guard;
    }

    Action<C> action() {
        return action;
    }
}
