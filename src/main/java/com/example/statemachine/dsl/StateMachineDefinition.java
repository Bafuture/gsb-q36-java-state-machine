package com.example.statemachine.dsl;

import com.example.statemachine.core.Action;
import com.example.statemachine.core.Guard;
import com.example.statemachine.core.TimeoutConfig;
import com.example.statemachine.core.Transition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable definition of a state machine: states, events, transitions and
 * optional per-state timeouts. Build one with {@link #builder(Object)}.
 *
 * <pre>{@code
 * StateMachineDefinition<OrderState, OrderEvent> def =
 *     StateMachineDefinition.builder(OrderState.CREATED)
 *         .states(OrderState.class)
 *         .events(OrderEvent.class)
 *         .transition(CREATED).on(SUBMIT).to(PAID)
 *             .guard(ctx -> order.hasPayment())
 *             .action(ctx -> notifyService())
 *         .transition(PAID).on(SHIP).to(SHIPPED)
 *         .timeout(CREATED).after(Duration.ofMinutes(30)).event(TIMEOUT).to(CANCELLED)
 *         .build();
 * }</pre>
 */
public final class StateMachineDefinition<S, E> {

    private final S initialState;
    private final Set<S> states;
    private final Set<E> events;
    private final List<Transition<S, E>> transitions;
    private final Map<S, TimeoutConfig<S, E>> timeouts;

    private StateMachineDefinition(Builder<S, E> builder) {
        this.initialState = Objects.requireNonNull(builder.initialState, "initialState");
        this.states = Collections.unmodifiableSet(new LinkedHashSet<>(builder.states));
        this.events = Collections.unmodifiableSet(new LinkedHashSet<>(builder.events));
        this.transitions = List.copyOf(builder.transitions);

        Map<S, TimeoutConfig<S, E>> t = new LinkedHashMap<>();
        for (TimeoutConfig<S, E> timeout : builder.timeouts) {
            if (t.put(timeout.state(), timeout) != null) {
                throw new IllegalStateException("Duplicate timeout configuration for state: " + timeout.state());
            }
        }
        this.timeouts = Collections.unmodifiableMap(t);
    }

    public static <S, E> Builder<S, E> builder(S initialState) {
        return new Builder<>(initialState);
    }

    public S initialState() {
        return initialState;
    }

    public Set<S> states() {
        return states;
    }

    public Set<E> events() {
        return events;
    }

    public List<Transition<S, E>> transitions() {
        return transitions;
    }

    public Map<S, TimeoutConfig<S, E>> timeouts() {
        return timeouts;
    }

    /** Transitions declared from the given state, in declaration order. */
    public List<Transition<S, E>> transitionsFrom(S state) {
        List<Transition<S, E>> result = new ArrayList<>();
        for (Transition<S, E> transition : transitions) {
            if (transition.source().equals(state)) {
                result.add(transition);
            }
        }
        return result;
    }

    /** Events that have at least one declared transition from the state. */
    public Set<E> allowedEvents(S state) {
        Set<E> result = new LinkedHashSet<>();
        for (Transition<S, E> transition : transitions) {
            if (transition.source().equals(state)) {
                result.add(transition.event());
            }
        }
        return result;
    }

    /** Fluent builder. */
    public static final class Builder<S, E> {

        private final S initialState;
        private final Set<S> states = new LinkedHashSet<>();
        private final Set<E> events = new LinkedHashSet<>();
        private final List<Transition<S, E>> transitions = new ArrayList<>();
        private final List<TimeoutConfig<S, E>> timeouts = new ArrayList<>();

        private Builder(S initialState) {
            this.initialState = Objects.requireNonNull(initialState, "initialState");
            this.states.add(initialState);
        }

        /** Registers one or more known states (transitions auto-register theirs too). */
        @SafeVarargs
        public final Builder<S, E> states(S... states) {
            Collections.addAll(this.states, states);
            return this;
        }

        /** Registers every constant of an enum as a known state. */
        public Builder<S, E> states(Class<? extends S> stateEnum) {
            requireEnum(stateEnum, "states");
            Collections.addAll(this.states, stateEnum.getEnumConstants());
            return this;
        }

        /** Registers one or more known events. */
        @SafeVarargs
        public final Builder<S, E> events(E... events) {
            Collections.addAll(this.events, events);
            return this;
        }

        /** Registers every constant of an enum as a known event. */
        public Builder<S, E> events(Class<? extends E> eventEnum) {
            requireEnum(eventEnum, "events");
            Collections.addAll(this.events, eventEnum.getEnumConstants());
            return this;
        }

        public TransitionBuilder<S, E> transition(S source) {
            Objects.requireNonNull(source, "source");
            this.states.add(source);
            return new TransitionBuilder<>(this, source);
        }

        public TimeoutBuilder<S, E> timeout(S state) {
            Objects.requireNonNull(state, "state");
            this.states.add(state);
            return new TimeoutBuilder<>(this, state);
        }

        public StateMachineDefinition<S, E> build() {
            Set<S> referenced = new HashSet<>();
            for (Transition<S, E> transition : transitions) {
                referenced.add(transition.source());
                referenced.add(transition.target());
                events.add(transition.event());
            }
            for (TimeoutConfig<S, E> timeout : timeouts) {
                referenced.add(timeout.state());
                referenced.add(timeout.targetState());
                events.add(timeout.timeoutEvent());
            }
            states.addAll(referenced);

            if (!states.contains(initialState)) {
                throw new IllegalStateException("Initial state not among declared states: " + initialState);
            }
            for (Transition<S, E> transition : transitions) {
                if (!states.contains(transition.source()) || !states.contains(transition.target())) {
                    throw new IllegalStateException("Transition references undeclared state: " + transition);
                }
            }
            Map<Key<S, E>, S> unguarded = new LinkedHashMap<>();
            for (Transition<S, E> transition : transitions) {
                if (transition.guard() == null) {
                    Key<S, E> key = new Key<>(transition.source(), transition.event());
                    S existing = unguarded.put(key, transition.target());
                    if (existing != null) {
                        throw new IllegalStateException(String.format(
                                "Ambiguous definition: multiple unguarded transitions from '%s' on event '%s' "
                                        + "(targets '%s' and '%s'). Add guards to disambiguate.",
                                transition.source(), transition.event(), existing, transition.target()));
                    }
                }
            }
            return new StateMachineDefinition<>(this);
        }

        private static void requireEnum(Class<?> type, String method) {
            if (!type.isEnum()) {
                throw new IllegalArgumentException(method + "(Class) requires an enum type, got: " + type);
            }
        }
    }

    /** {@code transition(source).on(event).to(target)[.guard(..)][.action(..)]}. */
    public static final class TransitionBuilder<S, E> {

        private final Builder<S, E> owner;
        private final S source;
        private E event;

        private TransitionBuilder(Builder<S, E> owner, S source) {
            this.owner = owner;
            this.source = source;
        }

        public TransitionBuilder<S, E> on(E event) {
            this.event = Objects.requireNonNull(event, "event");
            owner.events.add(event);
            return this;
        }

        public GuardStep<S, E> to(S target) {
            Objects.requireNonNull(target, "target");
            if (event == null) {
                throw new IllegalStateException("call on(event) before to(target)");
            }
            owner.states.add(target);
            return new GuardStep<>(owner, source, event, target);
        }
    }

    /** Optional guard/action step; any method returns the owner {@link Builder}. */
    public static final class GuardStep<S, E> {

        private final Builder<S, E> owner;
        private final S source;
        private final E event;
        private final S target;
        private Guard<S, E> guard;
        private Action<S, E> action;

        private GuardStep(Builder<S, E> owner, S source, E event, S target) {
            this.owner = owner;
            this.source = source;
            this.event = event;
            this.target = target;
        }

        public GuardStep<S, E> guard(Guard<S, E> guard) {
            this.guard = Objects.requireNonNull(guard, "guard");
            return this;
        }

        public GuardStep<S, E> action(Action<S, E> action) {
            this.action = Objects.requireNonNull(action, "action");
            return this;
        }

        public Builder<S, E> and() {
            commit();
            return owner;
        }

        public TransitionBuilder<S, E> transition(S nextSource) {
            commit();
            return owner.transition(nextSource);
        }

        public TimeoutBuilder<S, E> timeout(S state) {
            commit();
            return owner.timeout(state);
        }

        public StateMachineDefinition<S, E> build() {
            commit();
            return owner.build();
        }

        private void commit() {
            owner.transitions.add(new Transition<>(source, event, target, guard, action));
        }
    }

    /** {@code timeout(state).after(ms).event(e).to(target)}. */
    public static final class TimeoutBuilder<S, E> {

        private final Builder<S, E> owner;
        private final S state;
        private long timeoutMillis;

        private TimeoutBuilder(Builder<S, E> owner, S state) {
            this.owner = owner;
            this.state = state;
        }

        public TimeoutBuilder<S, E> after(long timeoutMillis) {
            if (timeoutMillis <= 0) {
                throw new IllegalArgumentException("timeout must be > 0 ms, got " + timeoutMillis);
            }
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public TimeoutBuilder<S, E> after(java.time.Duration timeout) {
            return after(Objects.requireNonNull(timeout, "timeout").toMillis());
        }

        public TimeoutTargetBuilder<S, E> event(E timeoutEvent) {
            Objects.requireNonNull(timeoutEvent, "timeoutEvent");
            owner.events.add(timeoutEvent);
            return new TimeoutTargetBuilder<>(owner, state, timeoutMillis, timeoutEvent);
        }
    }

    /** Final step of the timeout DSL. */
    public static final class TimeoutTargetBuilder<S, E> {

        private final Builder<S, E> owner;
        private final S state;
        private final long timeoutMillis;
        private final E timeoutEvent;

        private TimeoutTargetBuilder(Builder<S, E> owner, S state, long timeoutMillis, E timeoutEvent) {
            this.owner = owner;
            this.state = state;
            this.timeoutMillis = timeoutMillis;
            this.timeoutEvent = timeoutEvent;
        }

        public Builder<S, E> to(S targetState) {
            Objects.requireNonNull(targetState, "targetState");
            owner.states.add(targetState);
            owner.timeouts.add(new TimeoutConfig<>(state, timeoutMillis, timeoutEvent, targetState));
            return owner;
        }
    }

    private record Key<S, E>(S source, E event) {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key<?, ?> key)) {
                return false;
            }
            return Objects.equals(source, key.source) && Objects.equals(event, key.event);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, event);
        }
    }
}

