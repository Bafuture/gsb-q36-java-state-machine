package com.example.gsb.statemachine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 状态机定义 DSL。示例：
 *
 * <pre>{@code
 * StateMachineDefinition<OrderState, OrderEvent, OrderContext> def =
 *     StateMachineBuilder.<OrderState, OrderEvent, OrderContext>named("order")
 *         .initialState(OrderState.CREATED)
 *         .transition().from(CREATED).on(PAY).to(PAID)
 *             .guard(ctx -> ctx.paid()).action(ctx -> ctx.notify()).add()
 *         .timeout(PAID, Duration.ofMinutes(30), CANCELLED)
 *         .build();
 * }</pre>
 */
public final class StateMachineBuilder<S, E, C> {

    private final String name;
    private S initialState;
    private final List<Transition<S, E, C>> transitions = new ArrayList<>();
    private final List<TimeoutTransition<S>> timeouts = new ArrayList<>();

    private StateMachineBuilder(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public static <S, E, C> StateMachineBuilder<S, E, C> named(String name) {
        return new StateMachineBuilder<>(name);
    }

    public StateMachineBuilder<S, E, C> initialState(S state) {
        this.initialState = Objects.requireNonNull(state, "initialState");
        return this;
    }

    /** 开始定义一条迁移，链式调用 {@code from/on/to} 后以 {@link TransitionBuilder#add()} 收尾。 */
    public TransitionBuilder transition() {
        return new TransitionBuilder();
    }

    /** 为某状态配置超时时间与超时后的目标状态。 */
    public StateMachineBuilder<S, E, C> timeout(S state, Duration timeout, S target) {
        timeouts.add(new TimeoutTransition<>(state, timeout, target));
        return this;
    }

    public StateMachineDefinition<S, E, C> build() {
        if (initialState == null) {
            throw new IllegalStateException("initialState is required");
        }
        if (transitions.isEmpty() && timeouts.isEmpty()) {
            throw new IllegalStateException("at least one transition or timeout is required");
        }
        return new StateMachineDefinition<>(name, initialState, transitions, timeouts);
    }

    public final class TransitionBuilder {

        private S from;
        private E event;
        private S to;
        private Guard<C> guard;
        private Action<C> action;

        private TransitionBuilder() {
        }

        public TransitionBuilder from(S state) {
            this.from = Objects.requireNonNull(state, "from");
            return this;
        }

        public TransitionBuilder on(E evt) {
            this.event = Objects.requireNonNull(evt, "event");
            return this;
        }

        public TransitionBuilder to(S state) {
            this.to = Objects.requireNonNull(state, "to");
            return this;
        }

        public TransitionBuilder guard(Guard<C> g) {
            this.guard = Objects.requireNonNull(g, "guard");
            return this;
        }

        public TransitionBuilder action(Action<C> a) {
            this.action = Objects.requireNonNull(a, "action");
            return this;
        }

        public StateMachineBuilder<S, E, C> add() {
            if (from == null || event == null || to == null) {
                throw new IllegalStateException("transition requires from + on(event) + to");
            }
            transitions.add(new Transition<>(from, event, to, guard, action));
            return StateMachineBuilder.this;
        }
    }
}
