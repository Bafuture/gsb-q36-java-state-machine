package com.example.statemachine;

import com.example.statemachine.core.StateMachine;
import com.example.statemachine.dsl.StateMachineDefinition;
import com.example.statemachine.exception.GuardRejectedException;
import com.example.statemachine.exception.InvalidTransitionException;
import com.example.statemachine.exception.TransitionActionException;
import com.example.statemachine.export.MermaidExporter;
import com.example.statemachine.persistence.InMemoryStateStore;
import com.example.statemachine.record.InMemoryTransitionLog;
import com.example.statemachine.record.TransitionRecord;
import com.example.statemachine.support.ManualClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateMachineTest {

    enum State { CREATED, PAID, SHIPPED, CANCELLED }

    enum Event { SUBMIT, SHIP, CANCEL, TIMEOUT, PAY_RETRY }

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private static StateMachineDefinition.Builder<State, Event> baseBuilder() {
        return StateMachineDefinition.<State, Event>builder(State.CREATED)
                .states(State.class)
                .events(Event.class);
    }

    @Nested
    class LegalTransition {

        @Test
        void followsDeclaredTransitionAndRunsGuardThenAction() {
            AtomicBoolean actionRan = new AtomicBoolean(false);
            AtomicBoolean guardSawContext = new AtomicBoolean(false);

            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                        .guard(ctx -> {
                            guardSawContext.set(ctx.source() == State.CREATED
                                    && ctx.event() == Event.SUBMIT
                                    && ctx.target() == State.PAID);
                            return true;
                        })
                        .action(ctx -> actionRan.set(true))
                    .and()
                    .transition(State.PAID).on(Event.SHIP).to(State.SHIPPED)
                    .build();

            StateMachine<State, Event> machine = StateMachine.create(definition);

            TransitionRecord<State, Event> record = machine.fire(Event.SUBMIT);

            assertThat(machine.currentState()).isEqualTo(State.PAID);
            assertThat(actionRan).isTrue();
            assertThat(guardSawContext).isTrue();
            assertThat(record.rejected()).isFalse();
            assertThat(record.source()).isEqualTo(State.CREATED);
            assertThat(record.event()).isEqualTo(Event.SUBMIT);
            assertThat(record.target()).isEqualTo(State.PAID);
            assertThat(record.durationNanos()).isGreaterThanOrEqualTo(0);
            assertThat(machine.log().findAll()).hasSize(1);

            machine.fire(Event.SHIP);
            assertThat(machine.currentState()).isEqualTo(State.SHIPPED);
            assertThat(machine.log().findAll()).hasSize(2);
        }

        @Test
        void picksFirstTransitionWhoseGuardPasses() {
            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.CANCELLED)
                        .guard(ctx -> false)
                    .and()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                        .guard(ctx -> true)
                    .build();

            StateMachine<State, Event> machine = StateMachine.create(definition);

            machine.fire(Event.SUBMIT);

            assertThat(machine.currentState()).isEqualTo(State.PAID);
        }
    }

    @Nested
    class IllegalTransition {

        @Test
        void rejectsUndeclaredStateEventCombinationWithContextMessage() {
            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                    .and()
                    .transition(State.CREATED).on(Event.CANCEL).to(State.CANCELLED)
                    .and()
                    .transition(State.PAID).on(Event.SHIP).to(State.SHIPPED)
                    .build();

            StateMachine<State, Event> machine = StateMachine.create(definition);

            assertThatThrownBy(() -> machine.fire(Event.SHIP))
                    .isInstanceOf(InvalidTransitionException.class)
                    .hasMessageContaining(State.CREATED.name())
                    .hasMessageContaining(Event.SHIP.name())
                    .hasMessageContaining(Event.SUBMIT.name())
                    .hasMessageContaining(Event.CANCEL.name());

            assertThat(machine.currentState()).isEqualTo(State.CREATED);
            TransitionRecord<State, Event> record = machine.log().findLast().orElseThrow();
            assertThat(record.rejected()).isTrue();
            assertThat(record.target()).isNull();
        }
    }

    @Nested
    class GuardRejection {

        @Test
        void guardReturningFalseRejectsTransitionAndKeepsState() {
            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                        .guard(ctx -> false)
                    .build();

            StateMachine<State, Event> machine = StateMachine.create(definition);

            assertThatThrownBy(() -> machine.fire(Event.SUBMIT))
                    .isInstanceOf(GuardRejectedException.class)
                    .hasMessageContaining(State.CREATED.name())
                    .hasMessageContaining(Event.SUBMIT.name())
                    .hasMessageContaining(Event.SUBMIT.name());

            assertThat(machine.currentState()).isEqualTo(State.CREATED);
            assertThat(machine.log().findLast().orElseThrow().rejected()).isTrue();
        }

        @Test
        void guardThrowingIsTreatedAsRejection() {
            IllegalStateException boom = new IllegalStateException("guard boom");
            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                        .guard(ctx -> {
                            throw boom;
                        })
                    .build();

            StateMachine<State, Event> machine = StateMachine.create(definition);

            assertThatThrownBy(() -> machine.fire(Event.SUBMIT))
                    .isInstanceOf(GuardRejectedException.class)
                    .hasCause(boom);

            assertThat(machine.currentState()).isEqualTo(State.CREATED);
            assertThat(machine.log().findLast().orElseThrow().rejected()).isTrue();
        }
    }

    @Nested
    class ActionException {

        @Test
        void actionThrowingLeavesStateUnchanged() {
            InMemoryStateStore<State> store = new InMemoryStateStore<>();
            InMemoryTransitionLog<State, Event> log = new InMemoryTransitionLog<>();
            RuntimeException boom = new IllegalStateException("action boom");

            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                        .guard(ctx -> true)
                        .action(ctx -> {
                            throw boom;
                        })
                    .and()
                    .transition(State.CREATED).on(Event.CANCEL).to(State.CANCELLED)
                    .build();

            StateMachine<State, Event> machine =
                    new StateMachine<>(definition, store, new ManualClock(1_000), log);

            assertThatThrownBy(() -> machine.fire(Event.SUBMIT))
                    .isInstanceOf(TransitionActionException.class)
                    .hasCause(boom)
                    .hasMessageContaining(State.CREATED.name())
                    .hasMessageContaining(State.PAID.name());

            assertThat(machine.currentState())
                    .as("state must not change when the action throws")
                    .isEqualTo(State.CREATED);
            assertThat(store.load()).contains(State.CREATED);

            TransitionRecord<State, Event> record = log.findLast().orElseThrow();
            assertThat(record.rejected()).isTrue();
            assertThat(record.target()).isNull();
            assertThat(record.source()).isEqualTo(State.CREATED);

            machine.fire(Event.CANCEL);
            assertThat(machine.currentState())
                    .as("machine keeps working after a failed action")
                    .isEqualTo(State.CANCELLED);
            assertThat(store.load()).contains(State.CANCELLED);
        }
    }

    @Nested
    class TimeoutTransition {

        @Test
        void movesToTargetOnlyAfterConfiguredTimeout() {
            ManualClock clock = new ManualClock(10_000);
            InMemoryTransitionLog<State, Event> log = new InMemoryTransitionLog<>();

            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                    .and()
                    .timeout(State.CREATED).after(TIMEOUT_MS).event(Event.TIMEOUT).to(State.CANCELLED)
                    .build();

            StateMachine<State, Event> machine =
                    new StateMachine<>(definition, new InMemoryStateStore<>(), clock, log);

            assertThat(machine.checkTimeout()).isEmpty();
            assertThat(machine.currentState()).isEqualTo(State.CREATED);

            clock.advanceMillis(TIMEOUT_MS - 1);
            assertThat(machine.checkTimeout()).isEmpty();

            clock.advanceMillis(1);
            TransitionRecord<State, Event> record = machine.checkTimeout().orElseThrow();

            assertThat(machine.currentState()).isEqualTo(State.CANCELLED);
            assertThat(record.timeout()).isTrue();
            assertThat(record.rejected()).isFalse();
            assertThat(record.event()).isEqualTo(Event.TIMEOUT);
            assertThat(record.target()).isEqualTo(State.CANCELLED);

            assertThat(machine.checkTimeout())
                    .as("no timeout configured on CANCELLED; entry timer was reset")
                    .isEmpty();
            assertThat(log.findAll()).hasSize(1);
        }

        @Test
        void normalTransitionResetsTheTimeoutTimer() {
            ManualClock clock = new ManualClock(0);
            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                    .and()
                    .timeout(State.CREATED).after(TIMEOUT_MS).event(Event.TIMEOUT).to(State.CANCELLED)
                    .build();

            StateMachine<State, Event> machine =
                    new StateMachine<>(definition, new InMemoryStateStore<>(), clock,
                            new InMemoryTransitionLog<>());

            clock.advanceMillis(TIMEOUT_MS - 1);
            machine.fire(Event.SUBMIT);
            assertThat(machine.currentState()).isEqualTo(State.PAID);

            clock.advanceMillis(TIMEOUT_MS - 1);
            assertThat(machine.checkTimeout()).isEmpty();
        }
    }

    @Nested
    class PersistenceLogAndExport {

        @Test
        void resumesFromPreloadedStoreAndSupportsLogQueries() {
            ManualClock clock = new ManualClock(0);
            InMemoryStateStore<State> store = new InMemoryStateStore<>(State.PAID);
            InMemoryTransitionLog<State, Event> log = new InMemoryTransitionLog<>();

            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                    .and()
                    .transition(State.PAID).on(Event.SHIP).to(State.SHIPPED)
                    .and()
                    .transition(State.PAID).on(Event.CANCEL).to(State.CANCELLED)
                    .build();

            StateMachine<State, Event> machine = new StateMachine<>(definition, store, clock, log);

            assertThat(machine.currentState()).isEqualTo(State.PAID);
            machine.fire(Event.SHIP);

            assertThat(log.findBySource(State.PAID)).hasSize(1);
            assertThat(log.findByEvent(Event.SHIP)).hasSize(1);
            assertThat(log.findRejected()).isEmpty();
            assertThat(log.findLast().orElseThrow().target()).isEqualTo(State.SHIPPED);
        }

        @Test
        void exportsMermaidStateDiagram() {
            StateMachineDefinition<State, Event> definition = baseBuilder()
                    .transition(State.CREATED).on(Event.SUBMIT).to(State.PAID)
                        .guard(ctx -> true)
                    .and()
                    .transition(State.PAID).on(Event.SHIP).to(State.SHIPPED)
                        .action(ctx -> { })
                    .and()
                    .timeout(State.CREATED).after(TIMEOUT_MS).event(Event.TIMEOUT).to(State.CANCELLED)
                    .build();

            String mermaid = MermaidExporter.export(definition);

            assertThat(mermaid).startsWith("stateDiagram-v2");
            assertThat(mermaid).contains("[*] --> CREATED");
            assertThat(mermaid).contains("CREATED --> PAID : SUBMIT [guard]");
            assertThat(mermaid).contains("PAID --> SHIPPED : SHIP / action");
            assertThat(mermaid).contains("timeout after " + TIMEOUT_MS + "ms");
            assertThat(mermaid).contains("CREATED --> CANCELLED : TIMEOUT");
        }
    }
}
