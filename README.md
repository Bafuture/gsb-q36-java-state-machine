# 通用状态机引擎

把散落在各处的 if-else 状态流转规则收敛为一份集中声明的状态机定义：
状态、事件、迁移、守卫、动作都在 DSL 里定义，运行时统一做校验、执行、记录与导出。

## 快速开始

```java
enum OrderState { CREATED, PAID, SHIPPED, CANCELLED }
enum OrderEvent { PAY, SHIP, CANCEL }
record OrderContext(boolean paymentApproved) {}

StateMachineDefinition<OrderState, OrderEvent, OrderContext> def =
    StateMachineBuilder.<OrderState, OrderEvent, OrderContext>named("order")
        .initialState(OrderState.CREATED)
        .transition().from(CREATED).on(PAY).to(PAID)
            .guard(OrderContext::paymentApproved)          // 守卫：false 或抛异常都视为不允许
            .action(ctx -> sendPaidNotification())         // 动作：抛异常则状态不变
            .add()
        .transition().from(PAID).on(SHIP).to(SHIPPED).add()
        .transition().from(CREATED).on(CANCEL).to(CANCELLED).add()
        .timeout(PAID, Duration.ofMinutes(30), CANCELLED)  // 超时迁移
        .build();

StateMachine<OrderState, OrderEvent, OrderContext> sm = StateMachine.inMemory(def);
sm.fire(OrderEvent.PAY, new OrderContext(true));           // 合法迁移
sm.checkTimeout();                                          // 由调度器定期调用
List<TransitionRecord<OrderState, OrderEvent>> log = sm.history();
String diagram = MermaidExporter.export(def);               // 或 GraphvizExporter.export(def)
```

## 核心语义

| 场景 | 行为 |
|------|------|
| 未定义的「状态 + 事件」 | 记录一条被拒绝的迁移，抛 `IllegalTransitionException`，消息含当前状态、事件、允许的事件列表 |
| 守卫返回 `false` 或抛异常 | 视为不允许迁移，返回被拒绝的 `TransitionRecord`，状态不变 |
| 动作抛异常 | 状态保证不变（动作在落库前执行），记录后抛 `TransitionFailedException` |
| 超时 | `checkTimeout()` 发现当前状态停留超过配置时长即迁往目标状态，记录中 `event == null` |

## 状态持久化 SPI

`StateStore<S>` 只有两个方法：`load()` 返回 `Optional<StateSnapshot<S>>`，`save()` 覆盖式写入。
`StateSnapshot` 包含当前状态与进入时间（超时判定依赖它）。仓库自带 `InMemoryStateStore`。

接入数据库（以关系型库为例）：

```sql
CREATE TABLE machine_state (
  machine_id  VARCHAR(64) PRIMARY KEY,
  state       VARCHAR(64) NOT NULL,
  entered_at  TIMESTAMP   NOT NULL,
  version     BIGINT      NOT NULL DEFAULT 0   -- 乐观锁，跨进程并发用
);
```

- `load()`：`SELECT state, entered_at FROM machine_state WHERE machine_id = ?`，反序列化为 `StateSnapshot`。
- `save()`：`UPDATE ... WHERE machine_id = ? AND version = ?`，影响行数为 0 时说明并发冲突，重试或报错。
- 多实例部署时，建议把「load → 校验 → save」包在同一事务里，或用消息队列按 machine_id 串行化事件。

## 时间来源

`TimeSource` 是函数式接口（`Instant now()`），生产用 `TimeSource.system()`，
测试注入假时钟（见 `StateMachineTest` 的 `MutableTimeSource`），无需 sleep 即可验证超时迁移。

## 导出

`MermaidExporter` 输出 `stateDiagram-v2`，`GraphvizExporter` 输出 DOT；
超时边以 `timeout(PT30M)` 标注（Graphviz 中为虚线），带守卫的迁移追加 `[guard]` 标记。

## 设计取舍

- **定义与运行时分离**：`StateMachineDefinition` 不可变、可共享、可导出；`StateMachine` 持有 store/clock/history，代表一个业务对象实例。改规则只动定义，不动流转逻辑。
- **拒绝即记录**：非法迁移、守卫拒绝、动作失败都会进 `history()`，便于审计与排障；非法迁移额外抛异常，杜绝静默忽略。
- **动作先于落库**：保证「动作失败 ⇒ 状态不变」这一事务性语义，代价是动作需自行保证幂等（重试时可能再次执行）。
- **超时靠轮询而非定时器**：`checkTimeout()` 由调用方（调度器/定时任务）触发，引擎本身不起线程，嵌入式使用零负担。

## 已知限制

- 迁移记录保存在内存 `CopyOnWriteArrayList` 中，重启即丢失；如需持久审计，请包装 `StateMachine` 或扩展 `StateStore`。
- 单实例并发安全（`fire`/`checkTimeout` 串行化）；跨进程并发需 `StateStore` 实现层解决（如乐观锁）。
- 超时迁移不执行 action，也没有独立的超时事件类型（记录中 `event` 为 `null`）。
- 不支持层级状态、并行状态、进入/退出回调（entry/exit）；迁移表为扁平结构。
- 守卫只能抛 unchecked 异常（接口未声明 checked exception）。

## 构建与测试

```bash
./mvnw -q verify
```

测试覆盖：合法迁移、非法迁移（错误信息含状态/事件/允许列表）、守卫失败与守卫抛异常、
动作异常不改状态、超时迁移（假时钟）、持久化 SPI 跨实例恢复、Mermaid/Graphviz 导出。
