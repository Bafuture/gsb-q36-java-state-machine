package com.example.gsb.statemachine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 状态机的不可变定义：初始状态、全部迁移与超时配置。
 * 通过 {@link StateMachineBuilder} 构建。
 */
public final class StateMachineDefinition<S, E, C> {

    private final String name;
    private final S initialState;
    private final List<Transition<S, E, C>> transitions;
    private final Map<S, TimeoutTransition<S>> timeouts;
    private final Map<S, Map<E, Transition<S, E, C>>> index;

    StateMachineDefinition(String name,
                           S initialState,
                           List<Transition<S, E, C>> transitions,
                           List<TimeoutTransition<S>> timeouts) {
        this.name = name;
        this.initialState = initialState;
        this.transitions = List.copyOf(transitions);
        Map<S, TimeoutTransition<S>> timeoutMap = new LinkedHashMap<>();
        for (TimeoutTransition<S> t : timeouts) {
            timeoutMap.put(t.state(), t);
        }
        this.timeouts = Collections.unmodifiableMap(timeoutMap);
        Map<S, Map<E, Transition<S, E, C>>> idx = new LinkedHashMap<>();
        for (Transition<S, E, C> t : this.transitions) {
            idx.computeIfAbsent(t.from(), k -> new LinkedHashMap<>()).put(t.event(), t);
        }
        Map<S, Map<E, Transition<S, E, C>>> frozen = new LinkedHashMap<>();
        idx.forEach((s, m) -> frozen.put(s, Collections.unmodifiableMap(m)));
        this.index = Collections.unmodifiableMap(frozen);
    }

    public String name() {
        return name;
    }

    public S initialState() {
        return initialState;
    }

    Optional<Transition<S, E, C>> findTransition(S from, E event) {
        Map<E, Transition<S, E, C>> byEvent = index.get(from);
        return byEvent == null ? Optional.empty() : Optional.ofNullable(byEvent.get(event));
    }

    /** 某状态当前允许的事件列表（按定义顺序）。 */
    public List<E> allowedEvents(S state) {
        Map<E, Transition<S, E, C>> byEvent = index.get(state);
        return byEvent == null ? List.of() : List.copyOf(byEvent.keySet());
    }

    Optional<TimeoutTransition<S>> timeoutOf(S state) {
        return Optional.ofNullable(timeouts.get(state));
    }

    /** 导出用：全部迁移定义（只读视图）。 */
    public List<TransitionView<S, E>> transitionViews() {
        List<TransitionView<S, E>> views = new ArrayList<>();
        for (Transition<S, E, C> t : transitions) {
            views.add(new TransitionView<>(t.from(), t.event(), t.to(), t.guard() != null, t.action() != null));
        }
        return List.copyOf(views);
    }

    /** 导出用：全部超时配置（只读视图）。 */
    public List<TimeoutView<S>> timeoutViews() {
        return timeouts.values().stream()
                .map(t -> new TimeoutView<>(t.state(), t.timeout(), t.target()))
                .toList();
    }

    /** 迁移的只读视图，供导出器等使用。 */
    public record TransitionView<S, E>(S from, E event, S to, boolean hasGuard, boolean hasAction) {
    }

    /** 超时配置的只读视图，供导出器等使用。 */
    public record TimeoutView<S>(S state, Duration timeout, S target) {
    }
}
