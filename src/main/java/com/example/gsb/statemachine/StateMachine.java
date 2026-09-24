package com.example.gsb.statemachine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 状态机运行时。语义：
 *
 * <ul>
 *   <li>未定义的「状态 + 事件」组合：记录一条被拒绝的迁移，并抛出
 *       {@link IllegalTransitionException}（含当前状态、事件、允许的事件列表）。</li>
 *   <li>守卫返回 {@code false} 或抛异常：视为不允许迁移，记录后返回被拒绝的
 *       {@link TransitionRecord}，状态不变。</li>
 *   <li>动作抛异常：状态保证不变，记录后抛出 {@link TransitionFailedException}。</li>
 *   <li>{@link #checkTimeout()}：当前状态配置了超时且已超时，则自动迁往目标状态。</li>
 * </ul>
 *
 * <p>对单台实例的并发调用是安全的（fire/checkTimeout 串行化）；
 * 跨进程并发需要在 {@link StateStore} 实现层解决（如数据库乐观锁）。
 */
public final class StateMachine<S, E, C> {

    private final StateMachineDefinition<S, E, C> definition;
    private final StateStore<S> store;
    private final TimeSource timeSource;
    private final List<TransitionRecord<S, E>> history = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    public StateMachine(StateMachineDefinition<S, E, C> definition,
                        StateStore<S> store,
                        TimeSource timeSource) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.store = Objects.requireNonNull(store, "store");
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
    }

    /** 便捷构造：内存存储 + 系统时钟。 */
    public static <S, E, C> StateMachine<S, E, C> inMemory(StateMachineDefinition<S, E, C> definition) {
        return new StateMachine<>(definition, new InMemoryStateStore<>(), TimeSource.system());
    }

    /** 当前状态；首次访问时落库初始状态。 */
    public S currentState() {
        return snapshot().state();
    }

    /** 无上下文触发事件。 */
    public TransitionRecord<S, E> fire(E event) {
        return fire(event, null);
    }

    /**
     * 触发事件。返回迁移记录；非法迁移抛 {@link IllegalTransitionException}，
     * 动作失败抛 {@link TransitionFailedException}（状态不变）。
     */
    public TransitionRecord<S, E> fire(E event, C context) {
        Objects.requireNonNull(event, "event");
        synchronized (lock) {
            long startNanos = System.nanoTime();
            StateSnapshot<S> snapshot = snapshot();
            S from = snapshot.state();

            Transition<S, E, C> transition = definition.findTransition(from, event)
                    .orElse(null);
            if (transition == null) {
                TransitionRecord<S, E> record = rejected(from, event, startNanos,
                        "no transition defined for event " + event + " in state " + from);
                history.add(record);
                throw new IllegalTransitionException(from, event, definition.allowedEvents(from));
            }

            Guard<C> guard = transition.guard();
            if (guard != null) {
                boolean allowed;
                try {
                    allowed = guard.test(context);
                } catch (RuntimeException e) {
                    allowed = false;
                }
                if (!allowed) {
                    TransitionRecord<S, E> record = rejected(from, event, startNanos,
                            "guard rejected transition to " + transition.to());
                    history.add(record);
                    return record;
                }
            }

            Action<C> action = transition.action();
            if (action != null) {
                try {
                    action.execute(context);
                } catch (Exception e) {
                    TransitionRecord<S, E> record = rejected(from, event, startNanos,
                            "action failed: " + e.getMessage());
                    history.add(record);
                    throw new TransitionFailedException(
                            "Action failed for transition " + from + " --" + event + "--> "
                                    + transition.to() + "; state unchanged", e);
                }
            }

            store.save(new StateSnapshot<>(transition.to(), timeSource.now()));
            TransitionRecord<S, E> record = new TransitionRecord<>(
                    from, event, transition.to(), elapsed(startNanos), true, null);
            history.add(record);
            return record;
        }
    }

    /**
     * 检查当前状态是否已超时；超时则迁往配置的目标状态。
     *
     * @return 发生了超时迁移时返回对应记录，否则返回 {@code null}
     */
    public TransitionRecord<S, E> checkTimeout() {
        synchronized (lock) {
            long startNanos = System.nanoTime();
            StateSnapshot<S> snapshot = snapshot();
            var timeout = definition.timeoutOf(snapshot.state()).orElse(null);
            if (timeout == null) {
                return null;
            }
            Instant now = timeSource.now();
            if (Duration.between(snapshot.enteredAt(), now).compareTo(timeout.timeout()) < 0) {
                return null;
            }
            store.save(new StateSnapshot<>(timeout.target(), now));
            TransitionRecord<S, E> record = new TransitionRecord<>(
                    snapshot.state(), null, timeout.target(), elapsed(startNanos), true, null);
            history.add(record);
            return record;
        }
    }

    /** 全部迁移记录（含被拒绝的），按发生顺序。 */
    public List<TransitionRecord<S, E>> history() {
        return List.copyOf(history);
    }

    public StateMachineDefinition<S, E, C> definition() {
        return definition;
    }

    private StateSnapshot<S> snapshot() {
        return store.load().orElseGet(() -> {
            StateSnapshot<S> initial = new StateSnapshot<>(definition.initialState(), timeSource.now());
            store.save(initial);
            return initial;
        });
    }

    private TransitionRecord<S, E> rejected(S from, E event, long startNanos, String reason) {
        return new TransitionRecord<>(from, event, null, elapsed(startNanos), false, reason);
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
