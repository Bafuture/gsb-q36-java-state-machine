# 通用状态机引擎（Java 17 + 零运行时依赖）

一个从零实现的轻量状态机引擎，用于把散落在各业务类里的状态流转 `if-else`
集中成一份**可声明、可校验、可导出、可审计**的定义。运行时只依赖 JDK；
测试使用 JUnit 5 与 AssertJ。

## 快速开始

```bash
./mvnw -q verify
```

## 一分钟示例

```java
enum OrderState { CREATED, PAID, SHIPPED, CANCELLED }
enum OrderEvent { SUBMIT, SHIP, CANCEL, TIMEOUT }

StateMachineDefinition<OrderState, OrderEvent> definition =
    StateMachineDefinition.<OrderState, OrderEvent>builder(OrderState.CREATED)
        .states(OrderState.class)                 // 可选：提前登记状态
        .events(OrderEvent.class)                 // 可选：提前登记事件
        .transition(CREATED).on(SUBMIT).to(PAID)
            .guard(ctx -> paymentService.isPaid(orderId))
            .action(ctx -> notifyPaid(orderId))
        .and()
        .transition(PAID).on(SHIP).to(SHIPPED)
        .and()
        .transition(CREATED).on(CANCEL).to(CANCELLED)
        .and()
        .timeout(CREATED).after(Duration.ofMinutes(30))
            .event(TIMEOUT).to(CANCELLED)
        .build();

StateMachine<OrderState, OrderEvent> machine = StateMachine.create(definition);
machine.fire(SUBMIT);                             // 守卫通过 -> PAID，并执行 action
String mermaid = MermaidExporter.export(definition);
machine.checkTimeout();                           // 由调度器/请求入口周期性调用
```

## 模块结构

| 包 | 职责 |
|----|------|
| `core` | `StateMachine` 引擎、`Transition`、`TimeoutConfig`、`Guard`、`Action`、`Clock` |
| `dsl` | `StateMachineDefinition` 流式 Builder（定义状态/事件/迁移/超时） |
| `exception` | `InvalidTransitionException`、`GuardRejectedException`、`TransitionActionException` |
| `persistence` | 状态持久化 SPI `StateStore` 与内存实现 `InMemoryStateStore` |
| `record` | 迁移审计 `TransitionRecord`、`TransitionLog` 及内存实现 |
| `export` | `MermaidExporter`：导出 Mermaid `stateDiagram-v2` 文本 |
| `time` | `SystemClock`（生产用墙钟） |

## 核心语义

1. **合法迁移**：按 `from + event + to` 定义；迁移可带 `guard` 与 `action`。
   同一 `(from, event)` 允许声明多个带守卫的候选，按声明顺序选择**第一个守卫通过**
   的迁移；无守卫迁移在同一 key 下只允许一个（构建期检测歧义并直接报错）。
2. **非法迁移**：当前状态 + 事件没有任何候选迁移时抛
   `InvalidTransitionException`，消息固定包含「当前状态 + 事件 + 当前允许的事件
   列表」，绝不静默忽略。
3. **守卫语义**：
   - 守卫返回 `false` → 拒绝（`GuardRejectedException`）；
   - 守卫抛异常 → 同样视为不允许迁移，原始异常作为 cause 保留。
4. **动作语义**：动作在**新状态落库之前**执行。动作抛异常时包装为
   `TransitionActionException`，`StateStore.save` 不会被调用，状态保持不变
   （测试 `ActionException.actionThrowingLeavesStateUnchanged` 显式验证）。
5. **超时迁移**：每个状态至多配置一条超时（停留时长 + 超时事件 + 目标状态）。
   引擎记录每次进入状态的时刻，`checkTimeout()` 由外部调度（定时器、消息延迟队列、
   请求入口等）驱动；未超时返回 `Optional.empty()`，超时则完成迁移并打审计。
   时间来自可注入的 `Clock`，测试用 `ManualClock` 推进时间，不依赖真实 sleep。
6. **迁移记录**：每次尝试（无论成功/拒绝/超时）都写一条 `TransitionRecord`：
   序号、时间戳、源状态、事件、目标状态（被拒绝时为 `null`）、是否被拒绝、是否为
   超时迁移、纳秒耗时、失败原因。`TransitionLog` 提供 `findAll / findBySource /
   findByEvent / findRejected / findLast` 查询。

## 状态持久化 SPI 与数据库接入

```java
public interface StateStore<S> {
    Optional<S> load();
    void save(S currentState, S newState);
}
```

接入数据库时，通常每个业务对象一行（如 `order(id, state, updated_at)`），实现要点：

- `load()` 按业务主键查出当前状态；
- `save(old, new)` 用乐观条件更新保证并发安全，例如
  `UPDATE ... SET state = :new WHERE id = :id AND state = :old`，更新行数为 0 时
  说明并发竞争，抛出异常即可（该次尝试同样会被审计记录）；
- 与业务写操作放在**同一事务**里；动作异常时事务回滚或 `save` 根本未执行，
  状态天然不变；
- 若需要跨 JVM 的严格超时语义，额外持久化 `entered_at`（可用 `save` 的时间戳列），
  由数据库/调度器扫描到期对象并投递超时事件。

## 导出

`MermaidExporter.export(definition)` 输出：

```text
stateDiagram-v2
    [*] --> CREATED
    CREATED --> PAID : SUBMIT [guard] / action
    PAID --> SHIPPED : SHIP
    CREATED --> CANCELLED : TIMEOUT (timeout after 1800000ms)
    note right of CREATED : initial state
```

非标识符形态的状态名会被稳定地映射为 `S_<hash>` 节点 id。

## 设计取舍

- **定义期不可变 + 构建期校验**：`StateMachineDefinition` 构建后不可变；重复的无守卫
  迁移、引用未登记状态、非法超时时间在 `build()` 阶段直接失败，而不是等到运行时。
- **失败用异常而非布尔返回值**：非法迁移是调用方必须感知的编程/流程错误，返回
  `false` 容易被忽略；同时无论哪种失败都会写审计日志，保证「不得静默忽略」。
- **动作先于落库**：换取了「动作失败 ⇒ 状态一定不变」的强保证；代价是动作自身的
  外部副作用无法回滚（见已知限制）。
- **超时采用轮询驱动而非内置线程**：引擎不创建后台线程、不绑定运行框架，方便在
  Web/批处理/测试中使用；配合延迟消息或定时任务即可获得生产级语义。
- **时间与耗时分离**：超时判定走可注入的 `Clock`（毫秒），单次尝试耗时走
  `System.nanoTime()`（单调、纳秒），避免墙钟回拨污染耗时统计。
- **状态/事件用泛型而非必须继承接口**：枚举、字符串、值对象都可以直接作为状态，
  DSL 上手成本最低。
- **只提供 Mermaid 导出**：Graphviz 文本在信息上是同构的，后续加一个 exporter 即可，
  避免过早抽象；导出器是无状态静态工具，可平行扩展。

## 已知限制

- 动作的外部副作用（发 MQ、调 HTTP、写其他表）不会随动作失败自动回滚/补偿，需要
  调用方保证幂等或自行做事务外发模式（如 outbox）。
- 超时是**惰性求值**：没有后台线程自动触发，必须由外部调用 `checkTimeout()`；对象
  停在某状态时不会产生记录，只有检测/触发时才记录。
- 内存实现的 `enteredAt` 不持久化，JVM 重启后超时计时从构造时刻重新开始；严格语义
  请把进入时间落库并在构造时传入（或扩展 SPI）。
- 守卫不接收业务参数包，只能闭包捕获；如需每事件携带 payload，可在 `fire` 外层
  用对象捕获（目前引擎不感知 payload）。
- 不支持嵌套状态、并行（正交）状态、子状态机与内部迁移；这是平铺式 FSM。
- `StateMachine` 单实例通过 `synchronized` 保证串行；跨多实例并发需由数据库
  `StateStore` 的乐观锁兜底。
- 迁移记录的内存实现只进不出，生产环境请实现 `TransitionLog` 落库（与业务库同事务或
  异步入库）。

## 测试覆盖

`./mvnw -q verify` 共 10 个用例，覆盖要求的五类场景：

- 合法迁移（守卫/动作上下文、多守卫候选选择）
- 非法迁移（异常消息三要素、状态不变、审计被拒绝）
- 守卫失败（返回 `false` 与抛异常两种）
- 动作异常不改状态（store 与机器状态双重断言，之后机器仍可正常迁移）
- 超时迁移（未到期/恰好到期/迁移后计时器重置/普通迁移重置计时器）

另含持久化恢复、审计查询与 Mermaid 导出的测试。
