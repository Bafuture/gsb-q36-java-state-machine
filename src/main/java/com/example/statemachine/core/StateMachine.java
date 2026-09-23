package com.example.statemachine.core;

import com.example.statemachine.dsl.StateMachineDefinition;
import com.example.statemachine.exception.GuardRejectedException;
import com.example.statemachine.exception.InvalidTransitionException;
import com.example.statemachine.exception.TransitionActionException;
import com.example.statemachine.persistence.InMemoryStateStore;
import com.example.statemachine.persistence.StateStore;
import com.example.statemachine.record.InMemoryTransitionLog;
import com.example.statemachine.record.TransitionLog;
import com.example.statemachine.record.TransitionRecord;
import com.example.statemachine.time.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A running state-machine instance bound to one definition, one
 * {@link StateStore}, one {@link Clock} and one {@link TransitionLog}.
 *
 * <p>Semantics:
 * <ul>
 *   <li>events with no declared transition from the current state throw
 *       {@link InvalidTransitionException} (never silently ignored);</li>
 *   <li>a guard returning {@code false} or throwing rejects the transition
 *       ({@link GuardRejectedException});</li>
 *   <li>a throwing action rejects the transition and the state is guaranteed
 *       unchanged ({@link TransitionActionException});</li>
 *   <li>every attempt, rejected or not, is appended to the {@link TransitionLog}.</li>
 * </ul>
 *
 * Instances are synchronized and safe for use by one thread at a time.
 */
public final class StateMachine<S, E> {

    private final StateMachineDefinition<S, E> definition;
    private final StateStore<S> store;
    private final Clock clock;
    private final TransitionLog<S, E> log;
    private final AtomicLong sequence = new AtomicLong();

    /** Wall-clock time (per {@link Clock}) at which the current state was entered. */
    private long enteredAtMillis;

    public StateMachine(StateMachineDefinition<S, E> definition,
                        StateStore<S> store,
                        Clock clock,
                        TransitionLog<S, E> log) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");

        if (store.load().isEmpty()) {
            store.save(null, definition.initialState());
        }
        this.enteredAtMillis = clock.nowMillis();
    }

    /** Convenience factory with in-memory persistence/audit log and wall clock. */
    public static <S, E> StateMachine<S, E> create(StateMachineDefinition<S, E> definition) {
        return new StateMachine<>(definition, new InMemoryStateStore<>(),
                SystemClock.INSTANCE, new InMemoryTransitionLog<>());
    }

    public S currentState() {
        return store.load().orElseThrow();
    }

    public TransitionLog<S, E> log() {
        return log;
    }

    public StateMachineDefinition<S, E> definition() {
        return definition;
    }

    /**
     * Fires an event.
     *
     * @return the audit record of the accepted transition
     * @throws InvalidTransitionException if no transition is declared for
     *         current state + event
     * @throws GuardRejectedException if all candidate transitions are guarded
     *         and every guard returned false or threw
     * @throws TransitionActionException if the selected transition's action threw
     */
    public synchronized TransitionRecord<S, E> fire(E event) {
        return doFire(event, false);
    }

    /**
     * Evaluates the timeout configured for the current state (if any).
     *
     * @return the record of the automatic timeout transition, or empty if no
     *         timeout is configured or it has not elapsed yet
     */
    public synchronized Optional<TransitionRecord<S, E>> checkTimeout() {
        S current = currentState();
        TimeoutConfig<S, E> timeout = definition.timeouts().get(current);
        if (timeout == null) {
            return Optional.empty();
        }
        long elapsed = clock.nowMillis() - enteredAtMillis;
        if (elapsed < timeout.timeoutMillis()) {
            return Optional.empty();
        }
        return Optional.of(applyTimeout(timeout));
    }

    private TransitionRecord<S, E> doFire(E event, boolean isTimeout) {
        Objects.requireNonNull(event, "event");
        S current = currentState();
        long startNanos = System.nanoTime();

        try {
            List<Transition<S, E>> candidates = candidates(current, event);
            if (candidates.isEmpty()) {
                throw new InvalidTransitionException(current, event, new ArrayList<>(definition.allowedEvents(current)));
            }

            Transition<S, E> selected = null;
            Throwable guardFailure = null;
            for (Transition<S, E> candidate : candidates) {
                Guard<S, E> guard = candidate.guard();
                if (guard == null) {
                    selected = candidate;
                    break;
                }
                try {
                    if (guard.evaluate(new TransitionContext<>(current, event, candidate.target()))) {
                        selected = candidate;
                        break;
                    }
                } catch (Exception e) {
                    guardFailure = e;
                }
            }

            if (selected == null) {
                String reason = guardFailure != null
                        ? "guard threw " + guardFailure.getClass().getSimpleName()
                        : "guard returned false";
                throw new GuardRejectedException(current, event,
                        new ArrayList<>(definition.allowedEvents(current)), reason, guardFailure);
            }

            Action<S, E> action = selected.action();
            if (action != null) {
                try {
                    action.execute(new TransitionContext<>(current, event, selected.target()));
                } catch (Exception e) {
                    throw new TransitionActionException(current, event, selected.target(), e);
                }
            }

            return commit(current, event, selected.target(), false, startNanos, null);
        } catch (RuntimeException failure) {
            appendRejected(current, event, isTimeout, startNanos, failure);
            throw failure;
        }
    }

    private TransitionRecord<S, E> applyTimeout(TimeoutConfig<S, E> timeout) {
        S current = currentState();
        E event = timeout.timeoutEvent();
        long startNanos = System.nanoTime();
        try {
            return commit(current, event, timeout.targetState(), true, startNanos, null);
        } catch (RuntimeException failure) {
            appendRejected(current, event, true, startNanos, failure);
            throw failure;
        }
    }

    private TransitionRecord<S, E> commit(S current,
                                          E event,
                                          S target,
                                          boolean timeout,
                                          long startNanos,
                                          String reason) {
        store.save(current, target);
        enteredAtMillis = clock.nowMillis();
        TransitionRecord<S, E> record = new TransitionRecord<>(
                sequence.incrementAndGet(), clock.nowMillis(),
                current, event, target, false, timeout,
                System.nanoTime() - startNanos, reason);
        log.append(record);
        return record;
    }

    private void appendRejected(S current,
                                E event,
                                boolean timeout,
                                long startNanos,
                                RuntimeException failure) {
        TransitionRecord<S, E> record = new TransitionRecord<>(
                sequence.incrementAndGet(), clock.nowMillis(),
                current, event, null, true, timeout,
                System.nanoTime() - startNanos,
                failure.getClass().getSimpleName() + ": " + failure.getMessage());
        log.append(record);
    }

    private List<Transition<S, E>> candidates(S current, E event) {
        List<Transition<S, E>> result = new ArrayList<>();
        for (Transition<S, E> transition : definition.transitionsFrom(current)) {
            if (transition.event().equals(event)) {
                result.add(transition);
            }
        }
        return result;
    }

    /** Events allowed from the current state (declarative; ignores guards). */
    public Set<E> allowedEvents() {
        return definition.allowedEvents(currentState());
    }
}
