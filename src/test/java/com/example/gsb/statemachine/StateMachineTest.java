package com.example.gsb.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StateMachineTest {

    enum OrderState { CREATED, PAID, SHIPPED, CANCELLED }

    enum OrderEvent { PAY, SHIP, CANCEL }

    record OrderContext(boolean paymentApproved) {
    }

    private StateMachineDefinition<OrderState, OrderEvent, OrderContext> orderDefinition() {
        return StateMachineBuilder.<OrderState, OrderEvent, OrderContext>named("order")
                .initialState(OrderState.CREATED)
                .transition().from(OrderState.CREATED).on(OrderEvent.PAY).to(OrderState.PAID)
                    .guard(OrderContext::paymentApproved).add()
                .transition().from(OrderState.PAID).on(OrderEvent.SHIP).to(OrderState.SHIPPED).add()
                .transition().from(OrderState.CREATED).on(OrderEvent.CANCEL).to(OrderState.CANCELLED).add()
                .timeout(OrderState.PAID, Duration.ofMinutes(30), OrderState.CANCELLED)
                .build();
    }

    @Nested
    @DisplayName("合法迁移")
    class LegalTransition {

        @Test
        void movesToTargetStateAndRecordsHistory() {
            StateMachine<OrderState, OrderEvent, OrderContext> sm =
                    StateMachine.inMemory(orderDefinition());

            TransitionRecord<OrderState, OrderEvent> record =
                    sm.fire(OrderEvent.PAY, new OrderContext(true));

            assertThat(sm.currentState()).isEqualTo(OrderState.PAID);
            assertThat(record.accepted()).isTrue();
            assertThat(record.from()).isEqualTo(OrderState.CREATED);
            assertThat(record.event()).isEqualTo(OrderEvent.PAY);
            assertThat(record.to()).isEqualTo(OrderState.PAID);
            assertThat(record.elapsed().toNanos()).isGreaterThanOrEqualTo(0);

            sm.fire(OrderEvent.SHIP, new OrderContext(true));
            assertThat(sm.currentState()).isEqualTo(OrderState.SHIPPED);
            assertThat(sm.history()).hasSize(2);
        }

        @Test
        void runsActionOnTransition() {
            AtomicInteger actionCalls = new AtomicInteger();
            StateMachineDefinition<OrderState, OrderEvent, OrderContext> def =
                    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>named("order")
                            .initialState(OrderState.CREATED)
                            .transition().from(OrderState.CREATED).on(OrderEvent.PAY).to(OrderState.PAID)
                                .action(ctx -> actionCalls.incrementAndGet()).add()
                            .build();
            StateMachine<OrderState, OrderEvent, OrderContext> sm = StateMachine.inMemory(def);

            sm.fire(OrderEvent.PAY, new OrderContext(true));

            assertThat(actionCalls).hasValue(1);
            assertThat(sm.currentState()).isEqualTo(OrderState.PAID);
        }
    }

    @Nested
    @DisplayName("非法迁移")
    class IllegalTransition {

        @Test
        void rejectsUndefinedEventWithHelpfulMessage() {
            StateMachine<OrderState, OrderEvent, OrderContext> sm =
                    StateMachine.inMemory(orderDefinition());

            assertThatThrownBy(() -> sm.fire(OrderEvent.SHIP, new OrderContext(true)))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("CREATED")
                    .hasMessageContaining("SHIP")
                    .hasMessageContaining("PAY")
                    .hasMessageContaining("CANCEL");

            assertThat(sm.currentState()).isEqualTo(OrderState.CREATED);
            assertThat(sm.history()).singleElement().satisfies(r -> {
                assertThat(r.rejected()).isTrue();
                assertThat(r.event()).isEqualTo(OrderEvent.SHIP);
            });
        }

        @Test
        void allowedEventsListIsEmptyForDeadEndState() {
            StateMachineDefinition<OrderState, OrderEvent, OrderContext> def =
                    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>named("order")
                            .initialState(OrderState.CREATED)
                            .transition().from(OrderState.CREATED).on(OrderEvent.PAY).to(OrderState.PAID).add()
                            .build();
            StateMachine<OrderState, OrderEvent, OrderContext> sm = StateMachine.inMemory(def);
            sm.fire(OrderEvent.PAY, new OrderContext(true));

            assertThatThrownBy(() -> sm.fire(OrderEvent.PAY, new OrderContext(true)))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("allowedEvents=[]");
        }
    }

    @Nested
    @DisplayName("守卫")
    class GuardSemantics {

        @Test
        void guardReturningFalseRejectsTransition() {
            StateMachine<OrderState, OrderEvent, OrderContext> sm =
                    StateMachine.inMemory(orderDefinition());

            TransitionRecord<OrderState, OrderEvent> record =
                    sm.fire(OrderEvent.PAY, new OrderContext(false));

            assertThat(record.rejected()).isTrue();
            assertThat(record.rejection()).contains("guard");
            assertThat(sm.currentState()).isEqualTo(OrderState.CREATED);
        }

        @Test
        void guardThrowingIsTreatedAsNotAllowed() {
            StateMachineDefinition<OrderState, OrderEvent, OrderContext> def =
                    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>named("order")
                            .initialState(OrderState.CREATED)
                            .transition().from(OrderState.CREATED).on(OrderEvent.PAY).to(OrderState.PAID)
                                .guard(ctx -> { throw new IllegalStateException("boom"); }).add()
                            .build();
            StateMachine<OrderState, OrderEvent, OrderContext> sm = StateMachine.inMemory(def);

            TransitionRecord<OrderState, OrderEvent> record =
                    sm.fire(OrderEvent.PAY, new OrderContext(true));

            assertThat(record.rejected()).isTrue();
            assertThat(sm.currentState()).isEqualTo(OrderState.CREATED);
        }
    }

    @Nested
    @DisplayName("动作异常")
    class ActionFailure {

        @Test
        void actionExceptionLeavesStateUnchanged() {
            AtomicBoolean actionRan = new AtomicBoolean();
            StateMachineDefinition<OrderState, OrderEvent, OrderContext> def =
                    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>named("order")
                            .initialState(OrderState.CREATED)
                            .transition().from(OrderState.CREATED).on(OrderEvent.PAY).to(OrderState.PAID)
                                .action(ctx -> {
                                    actionRan.set(true);
                                    throw new RuntimeException("payment gateway down");
                                }).add()
                            .build();
            StateMachine<OrderState, OrderEvent, OrderContext> sm = StateMachine.inMemory(def);

            assertThatThrownBy(() -> sm.fire(OrderEvent.PAY, new OrderContext(true)))
                    .isInstanceOf(TransitionFailedException.class)
                    .hasMessageContaining("state unchanged")
                    .rootCause().hasMessageContaining("payment gateway down");

            assertThat(actionRan).isTrue();
            assertThat(sm.currentState()).isEqualTo(OrderState.CREATED);
            assertThat(sm.history()).singleElement().satisfies(r -> {
                assertThat(r.rejected()).isTrue();
                assertThat(r.rejection()).contains("action failed");
            });
        }
    }

    @Nested
    @DisplayName("超时迁移")
    class TimeoutTransitions {

        @Test
        void movesToTimeoutTargetAfterDeadline() {
            MutableTimeSource clock = new MutableTimeSource(Instant.parse("2026-09-24T00:00:00Z"));
            StateMachine<OrderState, OrderEvent, OrderContext> sm =
                    new StateMachine<>(orderDefinition(), new InMemoryStateStore<>(), clock);

            sm.fire(OrderEvent.PAY, new OrderContext(true));
            assertThat(sm.checkTimeout()).isNull();

            clock.advance(Duration.ofMinutes(31));
            TransitionRecord<OrderState, OrderEvent> record = sm.checkTimeout();

            assertThat(record).isNotNull();
            assertThat(record.accepted()).isTrue();
            assertThat(record.from()).isEqualTo(OrderState.PAID);
            assertThat(record.to()).isEqualTo(OrderState.CANCELLED);
            assertThat(record.event()).isNull();
            assertThat(sm.currentState()).isEqualTo(OrderState.CANCELLED);
        }

        @Test
        void noTimeoutWhenStateHasNoTimeoutConfigured() {
            MutableTimeSource clock = new MutableTimeSource(Instant.parse("2026-09-24T00:00:00Z"));
            StateMachine<OrderState, OrderEvent, OrderContext> sm =
                    new StateMachine<>(orderDefinition(), new InMemoryStateStore<>(), clock);

            clock.advance(Duration.ofHours(1));
            assertThat(sm.checkTimeout()).isNull();
            assertThat(sm.currentState()).isEqualTo(OrderState.CREATED);
        }
    }

    @Nested
    @DisplayName("持久化 SPI")
    class Persistence {

        @Test
        void stateSurvivesAcrossMachineInstancesSharingStore() {
            InMemoryStateStore<OrderState> store = new InMemoryStateStore<>();
            StateMachineDefinition<OrderState, OrderEvent, OrderContext> def = orderDefinition();

            StateMachine<OrderState, OrderEvent, OrderContext> first =
                    new StateMachine<>(def, store, TimeSource.system());
            first.fire(OrderEvent.PAY, new OrderContext(true));

            StateMachine<OrderState, OrderEvent, OrderContext> second =
                    new StateMachine<>(def, store, TimeSource.system());
            assertThat(second.currentState()).isEqualTo(OrderState.PAID);
        }
    }

    private static final class MutableTimeSource implements TimeSource {
        private Instant now;

        MutableTimeSource(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant now() {
            return now;
        }
    }
}
