# atlas-richie-component-concurrency

> Richie 平台并发编程工具组件，聚焦 **JDK 25 结构化并发**与**虚拟线程**的高频模式封装。

---

## 目录

- [概述](#概述)
- [核心特性](#核心特性)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [核心概念速览](#核心概念速览)
- [三大板块详解](#三大板块详解)
  - [板块一：结构化并发与虚拟线程（`virtual/`）](#板块一结构化并发与虚拟线程virtual)
    - [1.1 StructuredConcurrency 结构化并发](#11-structuredconcurrency-结构化并发)
    - [1.2 VirtualThreadFactory 虚拟线程工厂](#12-virtualthreadfactory-虚拟线程工厂)
    - [1.3 BatchProcessor 批量处理器](#13-batchprocessor-批量处理器)
  - [板块二：限流与容错算法（`algorithm/`）](#板块二限流与容错算法algorithm)
    - [2.1 Retryer 重试器](#21-retryer-重试器)
    - [2.2 RateLimiter 令牌桶限流器](#22-ratelimiter-令牌桶限流器)
    - [2.3 CircuitBreaker 熔断器](#23-circuitbreaker-熔断器)
    - [2.4 Debouncer 防抖器](#24-debouncer-防抖器)
  - [板块三：动态线程池（`threadpool/`）](#板块三动态线程池threadpool)
    - [3.0 实现原理](#30-实现原理)
      - [3.0.1 整体架构](#301-整体架构)
      - [3.0.2 调参流程](#302-调参流程)
      - [3.0.3 拒绝计数原理](#303-拒绝计数原理)
      - [3.0.4 与 dynamic-tp 的定位差异](#304-与-dynamic-tp-的定位差异)
    - [3.1 DynamicExecutor 动态线程池](#31-dynamicexecutor-动态线程池)
- [配置说明](#配置说明)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)
- [相关文档](#相关文档)

---

## 概述

`atlas-richie-component-concurrency` 是 Richie 平台的并发编程工具组件。它不封装第三方线程池管理库，而是直接基于 JDK 25 的标准并发原语（`StructuredTaskScope`、虚拟线程、`ScopedValue`），把分布式与高并发场景里最常见的 8 类模式封装成语义清晰的工具。

设计哲学：

- **用语义命名替代样板代码**：`gatherAll`、`race`、`withDeadline`、`gatherBatched`、`gatherAllBestEffort` 这些方法名本身就是设计意图的描述，调用者看一眼就知道选择了哪种并发模式。
- **虚拟线程优先**：所有底层实现都基于虚拟线程，让 IO 密集型场景享受百万级并发。
- **零第三方并发依赖**：不绑定 Resilience4j、Guava RateLimiter、Hystrix 等。令牌桶、熔断、限流、防抖全部由本组件内置实现，方便业务侧统一演进与排障。
- **声明式与编程式 API 并存**：核心组件（`Retryer`、`RateLimiter`、`CircuitBreaker`、`Debouncer`、`BatchProcessor`）提供编程式 Builder，同时通过 `ConcurrencyProperties` 暴露 `@ConfigurationProperties` 配置入口，开箱即用。

> 适用版本：JDK 25、Spring Boot 4.0.x、Spring Framework 7.x。

---

## 核心特性

本组件按场景职责划分为三大模块，每个模块对应一个源码子包。模块分组如下：

| 模块 | 组件 | 解决什么问题 | 一句话价值 |
|------|------|--------------|-----------|
| **结构化并发与虚拟线程**（`virtual/`） | `StructuredConcurrency` | JDK `StructuredTaskScope` API 冗长 | 一行调用覆盖汇聚、竞速、超时、分批、尽力汇聚 5 种模式 |
| **结构化并发与虚拟线程**（`virtual/`） | `VirtualThreadFactory` | 虚拟线程难以观测、上下文传播难 | 带命名前缀 + ScopedValue 绑定的虚拟线程工厂 |
| **结构化并发与虚拟线程**（`virtual/`） | `BatchProcessor` | 批量并发执行需手动管理信号量、线程数、超时 | 流式 API + 错误隔离 + 按输入顺序返回结果 |
| **限流与容错算法**（`algorithm/`） | `Retryer` | 临时性故障需要手动指数退避 | 指数退避 + 全量抖动 + 异常过滤 + Fallback 一体化 |
| **限流与容错算法**（`algorithm/`） | `RateLimiter` | `Semaphore` 用法复杂，且需手写定时器 | 令牌桶 + 三档等待语义（非阻塞 / 限时阻塞 / 无限阻塞）|
| **限流与容错算法**（`algorithm/`） | `CircuitBreaker` | 下游故障需要手动熔断防雪崩 | 三态状态机 + 失败率/次数/时间窗 + 单次探测 |
| **限流与容错算法**（`algorithm/`） | `Debouncer` | `ScheduledExecutorService` 防抖样板代码 | `trigger()` 一个方法替代 `cancel + schedule` 套路 |
| **动态线程池**（`threadpool/`） | `DynamicExecutor` | 标准 `ThreadPoolExecutor` 缺少运行时调整能力 | 事件驱动 resize + 拒绝计数 + 状态快照 + `@Qualifier` 多池注入 |
| **动态线程池**（`threadpool/`） | `PoolResizeEvent` | 调参事件散落在配置监听器，缺乏统一抽象 | 不可变事件对象，只更新非 null 字段，配置中心变更一行对接 |
| **动态线程池**（`threadpool/`） | `PoolStatus` | `ThreadPoolExecutor` 需多次调用 getter 才能拼出运行态快照 | 9 个核心指标一次返回的不可变快照，与拒绝计数天然同步 |

附加能力：

- 限流、熔断、动态线程池三大子系统的 Spring Boot 自动装配。
- 完整的配置属性绑定（`platform.concurrency.*`）。
- 全部源码带 Javadoc，组件内部以 `sealed interface` / `record` 表达不可变结果。
- 无运行时反射，所有 API 在编译期静态校验。

---

## 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 25+ | 使用 `StructuredTaskScope`、`ScopedValue`、`Thread.ofVirtual()` |
| Spring Boot | 4.0.x | 自动装配 |
| Maven | 3.9.0+ | 构建工具 |

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-concurrency</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

无需再单独引入 Resilience4j、Guava 等并发库。本组件只依赖 Spring Boot（`provided` 作用域）。

### 2. 最小示例

```java
import com.richie.component.concurrency.virtual.StructuredConcurrency;
import java.time.Duration;
import java.util.List;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        // 并行查询用户与订单
        var results = StructuredConcurrency.gatherAll(List.of(
            () -> userService.findById(1L),
            () -> orderService.findByUser(1L)
        ));
        User user = results.get(0);
        List<Order> orders = results.get(1);

        // 500ms 内必须返回，超时自动取消
        Data data = StructuredConcurrency.withDeadline(
            () -> externalService.query(),
            Duration.ofMillis(500)
        );
    }
}
```

---

## 核心概念速览

### 什么是结构化并发

JDK 25 引入的 `java.util.concurrent.StructuredTaskScope` 把一组并发任务当作"一个工作单元"来管理。父任务通过 `fork()` 派生子任务，通过 `join()` 等待所有子任务完成。当父任务结束时，无论子任务是否完成，scope 会自动确保它们被取消或 join。这种"父子绑定 + 生命周期一致"的语义，比 `CompletableFuture` 更适合表达"一组相互独立的并行任务"。

本组件的 `StructuredConcurrency` 工具类把这套语义封装成 5 个方法：

| 方法 | 模式 | 何时使用 |
|------|------|----------|
| `gatherAll` | 全部成功才返回 | 多个独立查询需要"全或无" |
| `race` | 首个成功即返回 | 多路容灾、缓存穿透 |
| `withDeadline` | 限时执行 | 任何对外调用都该有超时 |
| `gatherBatched` | 分批并发 | 任务量极大、需要控制并发度 |
| `gatherAllBestEffort` | 单项失败不影响整体 | 批量聚合，少数失败可接受 |

### 什么是虚拟线程

JDK 21+ 引入的轻量级线程，由 JVM 把阻塞操作卸载到操作系统线程池上。对 IO 密集型任务，可以同时存在百万级虚拟线程而不会耗尽内存。本组件的所有 `forEach`、`mapParallel`、`fork` 操作都跑在虚拟线程上，业务代码无需关心线程池大小。

### 什么是 ScopedValue

JDK 21+ 引入的不可变线程局部变量，比 `ThreadLocal` 性能更好且不会被继承链污染。`VirtualThreadFactory.Builder.scopedValue()` 可以在虚拟线程创建时绑定上下文，避免每次手动 `where().run()`。

---

## 三大板块详解

本组件按"场景职责"划分为三大板块，每个板块对应一个源码子包。读者可按需跳读：写并发编排看板块一、写调用保护看板块二、调线程池参数看板块三。

### 板块一：结构化并发与虚拟线程（`virtual/`）

聚焦 JDK 25 `StructuredTaskScope` 与 `Thread.ofVirtual()` 的高频模式封装，把"并发编排模式 + 虚拟线程上下文传播"封装成语义清晰的静态工具与工厂类。`virtual/` 子包包含三大组件：`StructuredConcurrency`（汇聚、竞速、超时、分批、尽力汇聚 5 种结构化并发模式）、`VirtualThreadFactory`（带命名前缀与 `ScopedValue` 绑定的虚拟线程工厂）、`BatchProcessor`（并发限流 + 错误隔离 + 按输入顺序收集结果的批量处理）。本板块的所有 API 都跑在虚拟线程之上，业务代码无需关心线程池大小与生命周期。

#### 1.1 StructuredConcurrency 结构化并发

##### 1.1.1 它是什么

`StructuredConcurrency` 是对 JDK 25 `StructuredTaskScope` 的高频场景封装。设计目标是把"并发编排模式"作为 API 暴露给调用方：调用方只需要选择 `gatherAll` / `race` / `withDeadline` / `gatherBatched` / `gatherAllBestEffort` 之一，不需要关心 `Joiner` 选型、scope 生命周期、`Configuration.withTimeout` 调用顺序这些底层细节。

底层所有方法共享一个静态 `VirtualThreadFactory`（线程名前缀 `ar-concurrency-`），保证线程名计数器在多次 `open` 调用间单调递增。

##### 1.1.2 接口设计语义

| 方法名 | 调用方的承诺 | 被调用方（方法本身）的保证 |
|--------|--------------|----------------------------|
| `gatherAll(tasks)` | "我提供了一组任务，全部成功才有用；任一失败我可以接受整体失败。" | 并行执行所有任务，全部成功则按输入顺序返回结果列表；任一任务失败则取消其余并透传原异常。 |
| `gatherAllSuppliers(tasks)` | 同上，但任务用 `Supplier` 表达，无需处理受检异常。 | 内部把 `Supplier` 适配成 `Callable`，语义与 `gatherAll` 完全一致。 |
| `race(tasks)` | "同一目标的多种实现，任一成功即可，其余的结果丢弃。" | 并行执行所有任务，返回首个成功的结果；首个成功后其余任务被自动取消。全部失败时抛最后一个异常。 |
| `raceSuppliers(tasks)` | 同上，但用 `Supplier` 表达。 | 同上，适配成 `Callable`。 |
| `withDeadline(task, timeout)` | "我对单个外部调用有 SLA，超时即放弃。" | 在 `timeout` 内执行任务；超时则由 JDK scope 自动取消任务并抛出 `TimeoutException`。 |
| `gatherBatched(tasks, batchSize)` | "任务量极大，不能一次性 fork 全部任务，否则会创建海量虚拟线程。" | 每批 `batchSize` 个任务并发执行，批间串行；最终把全部批的结果扁平合并返回。 |
| `gatherAllBestEffort(tasks)` | "我需要尽可能多的结果，单个失败不应影响其他。" | 并行执行所有任务，捕获每个任务的 `Throwable`，最终同时返回成功结果与失败明细。**不会**因单任务失败而取消其他任务。 |
| `gatherAllBestEffortSuppliers(tasks)` | 同上，但用 `Supplier` 表达。 | 同上，适配成 `Callable`。 |

##### 1.1.3 使用场景

1. **订单详情页聚合查询**：并行查询用户信息、订单主体、收货地址、优惠券状态，任一失败即整体失败。
2. **多级缓存穿透**：并行查 Redis 与 Caffeine，任一命中即返回。
3. **外部 API 超时控制**：所有外部调用必须 500ms 内返回，否则走兜底逻辑。
4. **报表数据批量拉取**：从 10 个数据源并行拉数据，允许 2 个失败但仍能展示部分数据。
5. **百万级 ID 校验**：把 100 万个 ID 分成 100 批每批 1 万，并发校验，每批完成后再启动下一批。

##### 1.1.4 没有这个组件时的样子

5 个并行查询加超时，写起来是这样的：

```java
import java.util.concurrent.*;

public List<Object> gatherAllManual(List<Callable<Object>> tasks, Duration timeout) throws Exception {
    List<Future<Object>> futures = new ArrayList<>();
    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    try {
        for (Callable<Object> t : tasks) {
            futures.add(pool.submit(t));
        }
        List<Object> results = new ArrayList<>(tasks.size());
        for (Future<Object> f : futures) {
            try {
                results.add(f.get(timeout.toMillis(), TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                f.cancel(true);
                throw e;
            }
        }
        return results;
    } finally {
        pool.shutdownNow();  // 容易漏掉，且无法保证子任务真正结束
    }
}
```

痛点：

- 需要手动管理线程池与生命周期。
- 任一超时只 cancel 了一个 Future，其他 Future 仍可能阻塞在 `f.get()`。
- `shutdownNow` 不保证子任务真正结束。
- `Future.get(timeout)` 的 timeout 是"等到这里为止"，不是"全局 deadline"，语义不直观。

##### 1.1.5 使用这个组件

```java
List<Object> results = StructuredConcurrency.gatherAll(tasks);
```

一行解决。

##### 1.1.6 完整代码示例

```java
import com.richie.component.concurrency.virtual.StructuredConcurrency;
import java.time.Duration;
import java.util.List;

public class StructuredConcurrencyExamples {

    // ========== 模式一：汇聚 gatherAll ==========
    public UserDashboard loadDashboard(Long userId) throws Exception {
        var results = StructuredConcurrency.gatherAll(List.of(
            () -> userService.findById(userId),
            () -> orderService.findByUser(userId),
            () -> couponService.findByUser(userId)
        ));
        return new UserDashboard(
            (User) results.get(0),
            (List<Order>) results.get(1),
            (List<Coupon>) results.get(2)
        );
    }

    // ========== 模式二：竞速 race（多级缓存穿透） ==========
    public String getCacheValue(String key) throws Exception {
        return StructuredConcurrency.race(List.of(
            () -> l1Cache.get(key),
            () -> l2Cache.get(key),
            () -> remoteCache.get(key)
        ));
    }

    // ========== 模式三：超时 withDeadline ==========
    public String callExternal(Duration timeout) throws Exception {
        return StructuredConcurrency.withDeadline(
            () -> externalApi.query(timeout.dividedBy(2)),
            timeout
        );
    }

    // ========== 模式四：分批 gatherBatched ==========
    public List<Boolean> validateIds(List<Long> ids, int batchSize) throws Exception {
        var tasks = ids.stream()
            .<Callable<Boolean>>map(id -> () -> validator.check(id))
            .toList();
        return StructuredConcurrency.gatherBatched(tasks, batchSize);
    }

    // ========== 模式五：尽力汇聚 gatherAllBestEffort ==========
    public Dashboard loadDashboardBestEffort(Long userId) {
        var outcome = StructuredConcurrency.gatherAllBestEffort(List.of(
            () -> userService.findById(userId),
            () -> orderService.findByUser(userId),
            () -> couponService.findByUser(userId)
        ));

        if (!outcome.hasAnySuccess()) {
            throw new ServiceUnavailableException("all data sources failed");
        }

        return new Dashboard(
            outcome.successCount() >= 1 ? (User) outcome.successes().get(0) : null,
            outcome.successCount() >= 2 ? (List<Order>) outcome.successes().get(1) : null,
            outcome.successCount() >= 3 ? (List<Coupon>) outcome.successes().get(2) : null,
            outcome.failures()
        );
    }

    // ========== Supplier 版本 ==========
    public List<String> fetchBySuppliers() throws Exception {
        return StructuredConcurrency.gatherAllSuppliers(List.of(
            () -> userService.findName(1L),
            () -> orderService.findCode(1L)
        ));
    }
}
```

##### 1.1.7 API 速查

| 方法 | 异常传播 | 返回值 |
|------|----------|--------|
| `gatherAll(Collection<Callable>)` | 任一任务抛异常即透传并取消其他 | `List<T>` 按输入顺序 |
| `gatherAllSuppliers(Collection<Supplier>)` | 同上 | `List<T>` 按输入顺序 |
| `race(Collection<Callable>)` | 全部失败时抛最后异常 | `T` 首个成功的结果 |
| `raceSuppliers(Collection<Supplier>)` | 同上 | `T` |
| `withDeadline(Callable, Duration)` | 超时抛 `TimeoutException` 或任务原异常 | `T` |
| `gatherBatched(Collection<Callable>, int)` | 任一任务异常 | `List<T>` 扁平合并 |
| `gatherAllBestEffort(Collection<Callable>)` | 不抛异常（除非被中断） | `BestEffortResult<T>` |
| `gatherAllBestEffortSuppliers(Collection<Supplier>)` | 同上 | `BestEffortResult<T>` |

`BestEffortResult` 字段：`successes()` 成功结果列表、`failures()` 异常列表、`failedIndices()` 失败下标列表、`hasAnySuccess()` / `successCount()` / `failureCount()` 辅助方法。

---

#### 1.2 VirtualThreadFactory 虚拟线程工厂

##### 1.2.1 它是什么

`VirtualThreadFactory` 实现标准 `ThreadFactory`，但额外提供两件事：

1. **命名规则**：每个虚拟线程都有可读前缀（如 `async-job-1`），配合 JDK 25 的 `jcmd <pid> Thread.print` 或 JFR 能精准定位线程。
2. **ScopedValue 绑定**：通过 Builder 把 `ScopedValue` 键值对"焊"到工厂上，从此该工厂创建的所有虚拟线程都自动携带这些上下文，无需调用方在每个 `where().run()` 处手动包装。

底层通过 `Thread.ofVirtual().name(name).unstarted(...)` 创建线程，零反射。

##### 1.2.2 接口设计语义

| 方法名 | 调用方的承诺 | 被调用方的保证 |
|--------|--------------|----------------|
| `builder()` | "我要定制一个工厂。" | 返回一个空白 Builder，所有字段都有默认值（前缀 `vt-`、无 ScopedValue 绑定）。 |
| `of(namePrefix)` | "我只要命名前缀，其他用默认。" | 创建一个简单工厂，`newThread(runnable)` 返回的虚拟线程名格式为 `<prefix><自增序号>`。 |
| `Builder.namePrefix(prefix)` | "我的线程叫这个前缀。" | 设置前缀；前缀为 null 抛 `NullPointerException`。 |
| `Builder.scopedValue(key, value)` | "每个虚拟线程都要携带这个上下文。" | 把键值对追加进绑定列表。 |
| `Builder.scopedValues(bindings...)` | "我有多个上下文要绑定。" | 一次性替换绑定数组（不是追加）。 |
| `Builder.build()` | "配置完了，给我工厂。" | 返回不可变工厂实例，线程安全。 |

##### 1.2.3 使用场景

1. **统一线程命名规范**：业务侧多个异步任务用不同前缀（`order-async-`、`report-async-`），日志排查一目了然。
2. **租户上下文自动传递**：把 `ScopedValue.newInstance()` 的租户 ID 绑定到工厂，所有虚拟线程无需 `ScopedValue.where(...)` 包装。
3. **Spring `@Async` 自定义执行器**：把工厂注入到 `ThreadPoolTaskExecutor.setThreadFactory(...)`，所有 `@Async` 任务都跑在带前缀的虚拟线程上。
4. **日志 traceId 传递**：把 traceId 的 `ScopedValue` 绑定到工厂，避免 `MDC.getCopyOfContextMap` 拷贝。

##### 1.2.4 没有这个组件时的样子

```java
// 想要命名 + ScopedValue 传递
ThreadFactory factory = r -> {
    var carrier = ScopedValue.where(TRACE_ID, "abc123");
    return Thread.ofVirtual()
        .name("order-async-" + counter.incrementAndGet())
        .unstarted(() -> carrier.run(r));
};
```

每次写都得到处复制这段样板代码，且 ScopedValue 容易写错（漏掉 `.run()` 调用）。

##### 1.2.5 使用这个组件

```java
ThreadFactory factory = VirtualThreadFactory.builder()
    .namePrefix("order-async-")
    .scopedValue(TRACE_ID, "abc123")
    .build();
```

##### 1.2.6 完整代码示例

```java
import com.richie.component.concurrency.virtual.VirtualThreadFactory;
import java.lang.ScopedValue;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadFactoryExamples {

    // 1) 最简用法
    ThreadFactory simple = VirtualThreadFactory.of("job-");
    Thread vt = simple.newThread(() -> System.out.println(Thread.currentThread().getName()));
    vt.start();  // 输出 job-1

    // 2) 自定义前缀 + 批量绑定
    static final ScopedValue<String> TENANT = ScopedValue.newInstance();
    static final ScopedValue<String> TRACE = ScopedValue.newInstance();

    ThreadFactory factory = VirtualThreadFactory.builder()
        .namePrefix("order-async-")
        .scopedValue(TENANT, "tenant-42")
        .scopedValue(TRACE, "trace-xyz")
        .build();

    Thread t1 = factory.newThread(() -> {
        // 在线程内可以直接访问绑定值，无需 where().run() 包装
        System.out.println(ScopedValue.get(TENANT));  // 输出 tenant-42
        System.out.println(ScopedValue.get(TRACE));    // 输出 trace-xyz
    });
    t1.start();

    // 3) ScopedValueBinding 数组方式
    var bindings = new VirtualThreadFactory.ScopedValueBinding[] {
        new VirtualThreadFactory.ScopedValueBinding<>(TENANT, "tenant-99"),
        new VirtualThreadFactory.ScopedValueBinding<>(TRACE, "trace-zzz")
    };
    ThreadFactory factory2 = VirtualThreadFactory.builder()
        .namePrefix("report-async-")
        .scopedValues(bindings)
        .build();

    // 4) 与 Spring ThreadPoolTaskExecutor 结合
    // ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // executor.setThreadFactory(VirtualThreadFactory.of("async-"));
    // executor.initialize();
}
```

##### 1.2.7 API 速查

| 方法 | 说明 |
|------|------|
| `builder()` | 返回 Builder（默认前缀 `vt-`） |
| `of(namePrefix)` | 快速工厂，仅指定前缀 |
| `Builder.namePrefix(prefix)` | 设置前缀 |
| `Builder.scopedValue(key, value)` | 绑定单个 ScopedValue |
| `Builder.scopedValues(bindings...)` | 批量替换 ScopedValue 绑定 |
| `Builder.build()` | 构建不可变工厂 |
| `newThread(runnable)` | 创建虚拟线程（线程名格式 `<prefix><序号>`） |

---

#### 1.3 BatchProcessor 批量处理器

##### 1.3.1 它是什么

`BatchProcessor` 是基于 JDK 25 `StructuredTaskScope` 的并发批量处理工具。它把"并发限流 + 单项错误隔离 + 整体超时 + 按输入顺序收集结果"封装成流式 API：

- **并发限流**：`Semaphore` 控制同时执行的虚拟线程数。
- **错误隔离**：单项失败不影响其他项继续执行，失败明细汇总到结果对象。
- **整体超时**：`StructuredTaskScope.withTimeout` 控制总耗时，超时后返回已完成的部分结果。
- **顺序保留**：`mapParallel` 模式下，结果列表与输入集合严格按索引对应（失败项对应 `null`）。

##### 1.3.2 接口设计语义

| 方法名 | 调用方的承诺 | 被调用方的保证 |
|--------|--------------|----------------|
| `of(items)` | "我有 N 条数据要处理。" | 创建 Builder，并对输入做不可变拷贝。 |
| `parallelism(n)` | "同时最多 n 个任务在跑。" | `Semaphore(n)` 限流；n 必须 ≥ 1，默认 `max(2, CPU*2)`。 |
| `timeout(d)` | "整个批量处理最多 d 时长。" | `withTimeout(d)` 控制，超时返回部分结果。 |
| `forEach(consumer)` | "我只关心副作用（写库、推送），不关心返回值。" | 并发执行 `consumer.accept(item)`，返回 `BatchResult`（含成功/失败计数与异常明细）。 |
| `mapParallel(mapper)` | "我需要每条数据的处理结果，按输入顺序收集。" | 并发执行 `mapper.apply(item)`，返回 `BatchMappingResult`（结果列表按下标对齐，失败项为 `null`）。 |

##### 1.3.3 使用场景

1. **订单批量处理**：1000 个订单并发调用下游发货接口，限制并发 20，整体超时 5 分钟。
2. **批量 ID 查询**：10000 个用户 ID 并发查数据库，结果按输入顺序组装。
3. **批量文件上传**：500 个文件并发上传，失败的文件单独重试。
4. **数据迁移**：百万条记录并发迁移，允许部分失败但要拿到全部错误明细。
5. **批量消息推送**：1000 个用户并发推送，单条失败不影响其他用户。

##### 1.3.4 没有这个组件时的样子

```java
public void processOrders(List<Order> orders) {
    Semaphore semaphore = new Semaphore(20);
    ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
    List<Future<?>> futures = new ArrayList<>();
    ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
    AtomicInteger success = new AtomicInteger();

    long deadline = System.currentTimeMillis() + Duration.ofMinutes(5).toMillis();

    for (Order o : orders) {
        futures.add(pool.submit(() -> {
            try {
                if (!semaphore.tryAcquire(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS)) {
                    errors.add(new TimeoutException("semaphore timeout"));
                    return;
                }
                try {
                    processOrder(o);
                    success.incrementAndGet();
                } finally {
                    semaphore.release();
                }
            } catch (Throwable t) {
                errors.add(t);
            }
        }));
    }

    for (Future<?> f : futures) {
        try { f.get(); } catch (Exception ignored) {}
    }
    pool.shutdown();
}
```

痛点：

- 整体超时和信号量超时是两个不同维度，容易搞混。
- 结果顺序要自己用 `AtomicReferenceArray` 维护。
- 线程池关闭与超时取消逻辑冗长。
- 失败项与未完成项难区分。

##### 1.3.5 使用这个组件

```java
BatchResult result = BatchProcessor.of(orders)
    .parallelism(20)
    .timeout(Duration.ofMinutes(5))
    .forEach(this::processOrder);

if (result.hasError()) {
    log.warn("失败 {} / 成功 {}", result.failureCount(), result.successCount());
}
```

##### 1.3.6 完整代码示例

```java
import com.richie.component.concurrency.virtual.BatchProcessor;
import com.richie.component.concurrency.virtual.BatchResult;
import com.richie.component.concurrency.virtual.BatchMappingResult;
import java.time.Duration;
import java.util.List;

public class BatchProcessorExamples {

    // 1) forEach：无返回值，按输入顺序不需要关心结果
    public BatchResult batchProcessOrders(List<Order> orders) {
        return BatchProcessor.of(orders)
            .parallelism(20)
            .timeout(Duration.ofMinutes(5))
            .forEach(this::processOrder);
    }

    // 2) mapParallel：按输入顺序收集结果（失败项对应 null）
    public BatchMappingResult<Long, String> batchFormatOrders(List<Long> orderIds) {
        return BatchProcessor.of(orderIds)
            .parallelism(20)
            .timeout(Duration.ofMinutes(5))
            .mapParallel(orderService::formatOrder);
    }

    // 3) 检查并处理失败项
    public void processWithFailureHandling(List<Long> userIds) {
        BatchMappingResult<Long, UserDTO> result = BatchProcessor.of(userIds)
            .parallelism(50)
            .timeout(Duration.ofSeconds(30))
            .mapParallel(userService::findById);

        List<UserDTO> users = result.results();  // 失败项为 null
        for (int i = 0; i < users.size(); i++) {
            UserDTO u = users.get(i);
            if (u == null) {
                Long failedId = userIds.get(i);
                Throwable error = result.errors().get(/* 通过 failedId 索引 */ 0);
                log.warn("用户 {} 拉取失败: {}", failedId, error.getMessage());
            }
        }

        if (result.hasError()) {
            log.warn("批量完成: success={}, failure={}",
                result.successCount(), result.failureCount());
        }
    }

    // 4) 超时场景：返回已完成的部分结果
    public void processWithTimeout(List<Order> orders) {
        BatchMappingResult<Order, Receipt> result = BatchProcessor.of(orders)
            .parallelism(10)
            .timeout(Duration.ofSeconds(10))
            .mapParallel(orderService::ship);

        // 即使超时，result.results() 仍包含已完成的部分
        long completed = result.results().stream().filter(Objects::nonNull).count();
        log.info("已完成 {} / {} 个，超时未完成 {} 个",
            completed, orders.size(), result.failureCount());
    }
}
```

##### 1.3.7 API 速查

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `of(items)` | 必填 | 创建 Builder，输入会被不可变拷贝 |
| `parallelism(n)` | `max(2, CPU*2)` | 最大并发数，n ≥ 1 |
| `timeout(d)` | 30 分钟 | 整体超时，d 必须为正 |
| `forEach(consumer)` | - | 处理每项，返回 `BatchResult` |
| `mapParallel(mapper)` | - | 映射每项，返回 `BatchMappingResult` |

`BatchResult` 字段：`successCount()`、`failureCount()`、`errors()`、`hasError()`、`empty()`。

`BatchMappingResult` 额外字段：`results()` 按输入顺序的结果列表（失败项为 `null`）、`resultAt(index)` 按下标取值（越界抛 `IndexOutOfBoundsException`）。

---

### 板块二：限流与容错算法（`algorithm/`）

把分布式与高并发场景里最常见的"对外部调用做保护"的算法封装成无状态工具与构建器，调用方通过 Builder 模式按需组装。`algorithm/` 子包包含四大组件：`Retryer`（指数退避 + 全量抖动 + 异常过滤 + Fallback 的一体重试器）、`RateLimiter`（三档等待语义的令牌桶）、`CircuitBreaker`（三态状态机的熔断保护）、`Debouncer`（一个 `trigger()` 完成所有防抖调度）。这些组件都不绑定任何第三方库，零运行时反射，业务侧可自由演进或替换实现。

#### 2.1 Retryer 重试器

##### 2.1.1 它是什么

`Retryer` 是分布式调用场景下的通用重试工具。它把"指数退避 + 全量抖动 + 异常过滤 + Fallback 降级"四件套封装成一个 Builder：

- **指数退避**：第 n 次失败后等待 `min(initialBackoff × 2^(n-1), maxBackoff)`。
- **全量抖动**：实际退避在 `[backoff/2, backoff]` 区间随机选取（AWS 架构师 Marc Brooker 提出的算法），避免多客户端同时重试造成的惊群效应。
- **精准异常匹配**：只对 `retryOn(...)` 指定的异常类型重试，业务异常（如 4xx）会立即透传。
- **中断感知**：线程被中断时立即抛 `RetryExhaustedException` 并恢复中断标志，不浪费等待时间。
- **Fallback 降级**：可选 `execute(task, fallback)` 重试耗尽后返回兜底值。

底层用 `Thread.sleep` + `ThreadLocalRandom` 实现，不依赖 `ScheduledExecutorService`。

##### 2.1.2 接口设计语义

| 方法名 | 调用方的承诺 | 被调用方的保证 |
|--------|--------------|----------------|
| `of(initialBackoff)` | "我的首次退避时间是 X。" | 返回 Builder，首次退避为 X；X 必须非负。 |
| `maxAttempts(n)` | "我最多能容忍 n 次尝试（含首次）。" | n ≥ 1；n=1 表示不重试。 |
| `maxBackoff(d)` | "退避不要超过这个上限。" | 退避公式中加 min(..., d) 限制；d 必须非负。 |
| `jitter(true)` | "我是多客户端场景，启用抖动。" | 实际退避在 `[backoff/2, backoff]` 随机。 |
| `retryOn(types...)` | "我只对这些异常重试。" | 只对指定类型（含子类）触发重试；其他异常立即 sneaky throw。 |
| `execute(task)` | "我要拿到结果，重试耗尽就抛。" | 重试耗尽抛 `RetryExhaustedException`，cause 是最后一次异常。 |
| `execute(task, fallback)` | "重试耗尽也要返回值，不能让上游崩。" | 重试耗尽、被中断、非重试异常都返回 fallback。**只 catch `Exception`，不吞 `Error`**。 |
| `execute(runnable)` | "我没返回值。" | 内部用 `Executors.callable(runnable, null)` 包装后复用逻辑。 |

##### 2.1.3 使用场景

1. **HTTP 调用瞬态故障重试**：网络抖动、503 错误，重试 3 次（间隔 100ms / 200ms / 400ms）。
2. **数据库连接获取重试**：连接池瞬时打满，等待后重试。
3. **消息队列发送重试**：发送失败后重试，避免业务数据丢失。
4. **缓存预热失败降级**：重试 3 次后仍失败返回 `null`，不影响主流程。
5. **健康检查调用**：定时 ping 下游服务，失败返回 false 而非抛异常。

##### 2.1.4 没有这个组件时的样子

```java
public String callRemoteWithRetry(String url) {
    int maxAttempts = 3;
    long backoff = 100;
    for (int i = 1; i <= maxAttempts; i++) {
        try {
            return httpClient.get(url);
        } catch (IOException e) {
            if (i == maxAttempts) throw new RuntimeException(e);
            try {
                Thread.sleep(backoff * (1L << (i - 1)));  // 没抖动
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
    }
    throw new IllegalStateException();
}
```

痛点：

- 没有抖动，1000 个客户端同时重试时仍然可能撞车。
- 退避时间没有上限，长时间重试可能等几小时。
- 重试异常类型无法配置，404 也会被重试。
- 每次业务代码都要复制这段样板。

##### 2.1.5 使用这个组件

```java
String result = Retryer.of(Duration.ofMillis(100))
    .maxAttempts(3)
    .jitter(true)
    .retryOn(IOException.class, TimeoutException.class)
    .execute(() -> httpClient.get(url));
```

##### 2.1.6 完整代码示例

```java
import com.richie.component.concurrency.algorithm.Retryer;
import com.richie.component.concurrency.algorithm.RetryExhaustedException;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RetryerExamples {

    // 1) 基本用法：指数退避 + 全量抖动 + 异常过滤
    public String callExternalApi() {
        return Retryer.of(Duration.ofMillis(100))
            .maxAttempts(3)
            .maxBackoff(Duration.ofSeconds(2))
            .jitter(true)
            .retryOn(IOException.class, TimeoutException.class)
            .execute(() -> httpClient.get("/api/data"));
    }

    // 2) 带 Fallback，重试耗尽返回兜底值
    public String callWithFallback() {
        return Retryer.of(Duration.ofMillis(200))
            .maxAttempts(3)
            .execute(() -> httpClient.get("/api/optional"), "default-value");
    }

    // 3) 无返回值版本
    public void publishEvent() {
        Retryer.of(Duration.ofMillis(500))
            .maxAttempts(5)
            .execute(() -> messageQueue.send(event));
    }

    // 4) 显式处理重试耗尽
    public String callWithExplicitHandling() {
        try {
            return Retryer.of(Duration.ofMillis(50))
                .maxAttempts(3)
                .execute(() -> remoteCall());
        } catch (RetryExhaustedException e) {
            Throwable cause = e.getCause();
            log.warn("重试耗尽，最后异常: {}", cause.getMessage(), e);
            throw new BusinessException("远程调用失败", e);
        }
    }

    // 5) 业务异常立即透传（不被重试）
    public String callWithBusinessExceptionFilter() {
        return Retryer.of(Duration.ofMillis(100))
            .maxAttempts(5)
            .retryOn(IOException.class)  // 只对 IO 异常重试
            .execute(() -> httpClient.get("/api/data"));
        // 如果抛 IllegalArgumentException（业务异常），会立即透传，不重试
    }
}
```

##### 2.1.7 API 速查

| 方法 | 默认值 | 说明 |
|------|--------|------|
| `of(initialBackoff)` | 必填 | 创建 Builder，初始退避必须非负 |
| `maxAttempts(n)` | 3 | 最大尝试次数（含首次），n ≥ 1 |
| `maxBackoff(d)` | 30s | 退避上限 |
| `jitter(bool)` | false | 全量抖动 |
| `retryOn(types...)` | `{Exception.class}` | 触发重试的异常类型，至少 1 个 |
| `execute(Callable)` | - | 执行任务，重试耗尽抛 `RetryExhaustedException` |
| `execute(Callable, fallback)` | - | 执行任务，失败返回 fallback（不吞 `Error`） |
| `execute(Runnable)` | - | 无返回值版本 |

`RetryExhaustedException` 是 `RuntimeException` 子类，`getCause()` 返回最后一次原始异常。

---

#### 2.2 RateLimiter 令牌桶限流器

##### 2.2.1 它是什么

`RateLimiter` 基于经典令牌桶算法实现对外部调用的限流。桶以固定速率补充令牌，每次调用消耗 1 个（或 N 个）令牌；桶空时根据方法语义返回 `false` 或阻塞等待。

核心设计：

- **三档等待语义**：`tryAcquire()` 非阻塞、`tryAcquire(Duration)` 限时阻塞、`acquire()` 无限阻塞，调用方按需选择。
- **虚拟线程调度器**：内部用 `Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())` 定期补充令牌，资源占用极低。
- **多令牌粒度**：`tryAcquire(n)` / `acquire(n)` 支持一次消费 N 个令牌，适合批量导入场景。
- **生命周期管理**：`close()` 幂等地关闭调度器，关闭后 `tryAcquire` 返回 false，`acquire` 抛 `IllegalStateException`。

##### 2.2.2 接口设计语义

| 方法名 | 调用方的承诺 | 被调用方的保证 |
|--------|--------------|----------------|
| `ofTokensPerSecond(n)` | "我每秒最多允许 n 次调用。" | 桶容量 = n，每 1 秒补充 n 个令牌。 |
| `ofTokensPerDuration(n, window)` | "我在 window 时长内最多允许 n 次调用。" | 桶容量 = n，每 window 补充一次。 |
| `ofTryAcquireTimeout(n, timeout)` | （已弃用） | 自 2.2.0 起 `try*` 前缀约定为严格非阻塞，本方法保留仅为兼容。请改用 `ofTokensPerDuration(n, window)` + `tryAcquire(Duration)`。 |
| `builder()` | "我要细粒度配置。" | 返回 Builder。 |
| `tryAcquire()` | "立刻给我令牌，没有就跳过。" | 桶空立即返回 false，**不阻塞**。 |
| `tryAcquire(n)` | 同上，但一次拿 n 个。 | n ≥ 1；桶内不足 n 个时立即返回 false。 |
| `tryAcquire(Duration)` | "我可以等，但不能超过 timeout。" | 在 timeout 内阻塞等待 1 个令牌；超时返回 false；中断时恢复中断标志并返回 false。 |
| `tryAcquire(n, Duration)` | 同上，但一次拿 n 个。 | 同上。 |
| `acquire()` | "我一定要拿到令牌，挂起多久都接受。" | 阻塞至拿到 1 个令牌；中断抛 `InterruptedException`。 |
| `acquire(n)` | 同上，但一次拿 n 个。 | 阻塞至拿到 n 个令牌。 |
| `acquireUninterruptibly(n)` | "我不响应中断。" | 不抛 `InterruptedException`，但保留中断标志。 |
| `availablePermits()` | "我看一下桶里还有几个令牌。" | 返回瞬时快照（**近似值**，不保证原子一致）。 |
| `close()` | "我用完了。" | 关闭调度器线程；幂等。 |

##### 2.2.3 使用场景

1. **外部 API 全局限流**：调用第三方支付接口，限制每秒 100 次。
2. **数据库写入限流**：批量写入时限制每秒 500 次，避免数据库压力过大。
3. **定时任务限流**：每分钟最多触发 10 次，超过则丢弃。
4. **SLA 严苛场景的限时等待**：关键路径上 500ms 拿不到令牌就走兜底逻辑。
5. **突发流量保护**：60 秒内允许 1000 次调用，应对短时突发。

##### 2.2.4 没有这个组件时的样子

```java
public class ManualRateLimiter {
    private final Semaphore semaphore = new Semaphore(100);
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    public ManualRateLimiter() {
        scheduler.scheduleAtFixedRate(() -> {
            int release = 100 - semaphore.availablePermits();
            if (release > 0) semaphore.release(release);
        }, 1, 1, TimeUnit.SECONDS);
    }

    public boolean tryAcquire() { return semaphore.tryAcquire(); }
    public void acquire() throws InterruptedException { semaphore.acquire(); }
    public void close() { scheduler.shutdown(); }
}
```

痛点：

- 桶容量和补充速率是硬编码。
- 没有多令牌粒度（要拿 5 个令牌得循环 5 次）。
- 没有限时阻塞变体。
- 线程工厂没法换虚拟线程。
- 关闭逻辑容易漏。

##### 2.2.5 使用这个组件

```java
RateLimiter limiter = RateLimiter.ofTokensPerSecond(100);
if (limiter.tryAcquire()) {
    callExternalService();
}
```

##### 2.2.6 完整代码示例

```java
import com.richie.component.concurrency.algorithm.RateLimiter;
import java.time.Duration;

public class RateLimiterExamples {

    // 1) 非阻塞：每秒 100 次
    public void callExternalNonBlocking(RateLimiter limiter) {
        if (limiter.tryAcquire()) {
            externalService.call();
        } else {
            log.warn("限流中，跳过本次调用");
        }
    }

    // 2) 限时等待：500ms 内拿不到就放弃
    public boolean callExternalWithTimeout(RateLimiter limiter) {
        if (limiter.tryAcquire(Duration.ofMillis(500))) {
            externalService.call();
            return true;
        } else {
            return false;  // SLA 超时
        }
    }

    // 3) 无限阻塞：必须等
    public void callExternalBlocking(RateLimiter limiter) throws InterruptedException {
        limiter.acquire();
        externalService.call();
    }

    // 4) 一次拿多个令牌：批量调用
    public void callBatch(RateLimiter limiter, List<Request> batch) throws InterruptedException {
        limiter.acquire(batch.size());  // 一次拿 batch.size() 个令牌
        externalService.batchCall(batch);
    }

    // 5) 自定义周期：60 秒内最多 1000 次
    public RateLimiter createQuotaLimiter() {
        return RateLimiter.ofTokensPerDuration(1000, Duration.ofMinutes(1));
    }

    // 6) Builder 模式自定义
    public RateLimiter createCustomLimiter() {
        return RateLimiter.builder()
            .permits(200)
            .period(Duration.ofSeconds(1))
            .build();
    }

    // 7) 资源管理：在 Spring Bean 关闭时释放
    // @PreDestroy
    public void shutdown(RateLimiter limiter) {
        limiter.close();  // 关闭虚拟线程调度器
    }

    // 8) 监控：获取当前可用令牌数
    public int monitor(RateLimiter limiter) {
        return limiter.availablePermits();  // 近似值，仅用于监控
    }
}
```

##### 2.2.7 API 速查

| 方法 | 说明 |
|------|------|
| `ofTokensPerSecond(n)` | 每秒补充 n 个令牌（n ≥ 1） |
| `ofTokensPerDuration(n, window)` | 每 window 补充 n 个令牌 |
| `ofTryAcquireTimeout(n, timeout)` | 已弃用，请改用 `ofTokensPerDuration` + `tryAcquire(Duration)` |
| `builder()` | 自定义构建器 |
| `tryAcquire()` | 非阻塞拿 1 个令牌 |
| `tryAcquire(n)` | 非阻塞拿 n 个令牌 |
| `tryAcquire(Duration)` | 限时阻塞拿 1 个令牌 |
| `tryAcquire(n, Duration)` | 限时阻塞拿 n 个令牌 |
| `acquire()` | 无限阻塞拿 1 个令牌 |
| `acquire(n)` | 无限阻塞拿 n 个令牌 |
| `acquireUninterruptibly(n)` | 不可中断阻塞 |
| `availablePermits()` | 当前可用令牌数（近似） |
| `close()` | 关闭限流器（幂等） |
| `Builder.period(d)` | 自定义周期（d 必须正） |
| `Builder.tryAcquireTimeoutEnabled(bool)` | 已弃用，不再影响 tryAcquire 行为 |

---

#### 2.3 CircuitBreaker 熔断器

##### 2.3.1 它是什么

`CircuitBreaker` 是借鉴 Hystrix / Resilience4j 设计的三态状态机，用于保护下游服务在持续故障期间不被压垮。

三种状态：

- **CLOSED（闭合）**：正常状态，请求通过；同时累计成功/失败统计。
- **OPEN（断开）**：故障状态，请求立即被拒绝（抛 `CircuitBreakerOpenException`），不再调用下游。
- **HALF_OPEN（半开）**：探测状态，等待 `openDuration` 后进入；放行首个试探调用，成功则回到 CLOSED，失败则再次 OPEN。

三种触发模式：

- **失败率模式（默认）**：监控最近 N 次调用，失败率超过阈值时熔断。要求窗口内至少累积 10 次调用才判定，避免冷启动误熔断。
- **绝对次数模式（`ofCount`）**：窗口内累计 N 次失败即熔断，无需计算失败率。
- **时间窗口模式（`ofRate`）**：最近 windowDuration 内的失败率触发熔断（`TIME_BASED` 滑动窗口）。

HALF_OPEN 状态采用 **单次探测** 语义：通过 `AtomicBoolean halfOpenGate` 的 `compareAndSet` 保证同一时刻只有一个试探调用放行，其他并发线程按 OPEN 拒绝。

##### 2.3.2 接口设计语义

| 方法名 | 调用方的承诺 | 被调用方的保证 |
|--------|--------------|----------------|
| `ofDefaults()` | "我用默认参数就行。" | 失败率 50% / 窗口 100 / OPEN 持续 10s。 |
| `of(failurePercent, openDuration)` | "我用失败率模式，100 次窗口。" | 同上，参数化失败率和 OPEN 持续时间。 |
| `ofCount(failureCount, openDuration)` | "我按绝对次数熔断。" | N 次失败即熔断，无最小样本要求。 |
| `ofRate(failurePercent, window, openDuration)` | "我用时间窗口模式。" | 时间窗口内的失败率。 |
| `builder()` | "我要细粒度配置。" | 返回 Builder。 |
| `execute(task)` | "执行任务，熔断时我要知道。" | OPEN 状态抛 `CircuitBreakerOpenException`；任务异常也透传。 |
| `execute(task, fallback)` | "任何失败都不能让上游崩。" | 熔断和异常都返回 fallback，不抛异常。 |
| `executeOrThrow(task)` | "我要明确语义：我会抛异常。" | 同 `execute(task)`，但方法名强化"会抛"语义。 |
| `state()` | "我需要监控熔断器状态。" | 返回当前 `CLOSED` / `OPEN` / `HALF_OPEN`。 |
| `reset()` | "强制把熔断器恢复到 CLOSED。" | 清空统计，幂等。 |
| `forceOpen()` | "我要测试 OPEN 状态。" | 强制设为 OPEN，不影响 `openDuration` 计时。 |

##### 2.3.3 使用场景

1. **外部 API 调用保护**：支付接口、短信接口失败率超过 50% 时熔断，避免拖垮整个系统。
2. **下游服务降级**：用户服务故障时熔断，返回缓存中的兜底数据。
3. **数据库故障保护**：主库失败率上升时熔断，应用层切到只读库。
4. **新服务上线冷启动**：用绝对次数模式（如 5 次失败即熔断）避免冷启动被误判。
5. **多租户隔离**：每个租户一个熔断器实例，单租户故障不影响其他租户。

##### 2.3.4 没有这个组件时的样子

```java
public class ManualCircuitBreaker {
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicLong openedAt = new AtomicLong();
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    public <T> T execute(Callable<T> task) throws Exception {
        if (state.get() == State.OPEN) {
            long elapsed = System.nanoTime() - openedAt.get();
            if (elapsed > Duration.ofSeconds(10).toNanos()) {
                synchronized (this) {
                    if (state.get() == State.OPEN) state.set(State.HALF_OPEN);
                }
            } else {
                throw new RuntimeException("Circuit open");
            }
        }
        if (state.get() == State.HALF_OPEN && !halfOpenGate.compareAndSet(false, true)) {
            throw new RuntimeException("Circuit half-open, probe in progress");
        }
        try {
            T result = task.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }
    // ... 还要实现 onSuccess / onFailure / 状态转移 / 单次探测门控等
}
```

痛点：

- 单次探测门控的 `compareAndSet` 容易被遗忘。
- HALF_OPEN 状态转移的双重检查锁容易写错。
- 失败率统计要维护滑动窗口。
- 冷启动保护（最小样本数）容易被遗漏。

##### 2.3.5 使用这个组件

```java
CircuitBreaker breaker = CircuitBreaker.ofDefaults();
String result = breaker.execute(() -> callRemoteService(), "default");
```

##### 2.3.6 完整代码示例

```java
import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.CircuitBreakerOpenException;
import java.time.Duration;

public class CircuitBreakerExamples {

    // 1) 默认配置 + 带 Fallback
    public String callWithDefault(String url) {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        return breaker.execute(() -> httpClient.get(url), "fallback");
    }

    // 2) 自定义失败率 + 窗口
    public CircuitBreaker createAggressiveBreaker() {
        return CircuitBreaker.builder()
            .failurePercent(60)
            .windowSize(200)
            .openDuration(Duration.ofSeconds(30))
            .build();
    }

    // 3) 绝对次数模式（适合冷启动场景）
    public CircuitBreaker createCountBasedBreaker() {
        return CircuitBreaker.ofCount(5, Duration.ofSeconds(15));
    }

    // 4) 时间窗口模式
    public CircuitBreaker createTimeWindowBreaker() {
        return CircuitBreaker.ofRate(50,
            Duration.ofSeconds(10),
            Duration.ofSeconds(5));
    }

    // 5) 显式处理熔断异常
    public String callWithExplicitHandling(String url) {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        try {
            return breaker.executeOrThrow(() -> httpClient.get(url));
        } catch (CircuitBreakerOpenException e) {
            log.warn("熔断开启，跳过远程调用");
            return getCachedValue(url);
        } catch (Exception e) {
            log.error("远程调用失败", e);
            throw new BusinessException("调用失败", e);
        }
    }

    // 6) 监控熔断器状态
    public void monitorState(CircuitBreaker breaker) {
        CircuitBreaker.State state = breaker.state();
        if (state == CircuitBreaker.State.OPEN) {
            metrics.gauge("circuit_breaker.state", 1);  // 暴露给 Prometheus
        } else if (state == CircuitBreaker.State.HALF_OPEN) {
            metrics.gauge("circuit_breaker.state", 0.5);
        } else {
            metrics.gauge("circuit_breaker.state", 0);
        }
    }

    // 7) 测试场景：强制设为 OPEN
    public void simulateOpen(CircuitBreaker breaker) {
        breaker.forceOpen();
        // 测试兜底逻辑
        String result = breaker.execute(() -> callRemote(), "fallback");
        assert "fallback".equals(result);
        breaker.reset();  // 恢复 CLOSED
    }
}
```

##### 2.3.7 API 速查

| 方法 | 说明 |
|------|------|
| `ofDefaults()` | 默认配置（50% / 100 窗口 / 10s） |
| `of(failurePercent, openDuration)` | 失败率模式，100 次窗口 |
| `ofCount(failureCount, openDuration)` | 绝对次数模式 |
| `ofRate(failurePercent, window, openDuration)` | 时间窗口模式 |
| `builder()` | 细粒度 Builder |
| `execute(task)` | 执行任务，OPEN 抛 `CircuitBreakerOpenException` |
| `execute(task, fallback)` | 执行任务，失败返回 fallback |
| `executeOrThrow(task)` | 同 `execute`，但方法名强调"会抛" |
| `state()` | 当前状态（`CLOSED` / `OPEN` / `HALF_OPEN`） |
| `reset()` | 强制重置 CLOSED |
| `forceOpen()` | 强制设为 OPEN（测试用） |
| `Builder.failurePercent(p)` | 失败率阈值（1-100） |
| `Builder.failureCount(n)` | 绝对失败次数（n ≥ 1） |
| `Builder.windowSize(n)` | 计数窗口大小（n ≥ 10，默认 100） |
| `Builder.windowDuration(d)` | 时间窗口长度（仅 `TIME_BASED`） |
| `Builder.openDuration(d)` | OPEN 持续时间 |
| `Builder.slidingWindowType(type)` | 滑动窗口类型（`COUNT_BASED` / `TIME_BASED`） |

`CircuitBreaker.State` 枚举：`CLOSED` / `OPEN` / `HALF_OPEN`。

---

#### 2.4 Debouncer 防抖器

##### 2.4.1 它是什么

`Debouncer` 把"重复触发即重置计时器"的防抖模式封装成一个 `trigger()` 方法。底层用 `ScheduledExecutorService`（虚拟线程工厂），每次 `trigger()` 取消旧计时器并启动新计时器；如果 `delay` 时间内没有新的 `trigger()`，则执行挂起的操作。

设计意图：调用方不需要维护 `ScheduledFuture` 引用，不需要 try/catch `InterruptedException`，不需要记住 cancel + schedule 的两步骤套路。`trigger()` 一个调用点完成所有逻辑。

##### 2.4.2 接口设计语义

| 方法名 | 调用方的承诺 | 被调用方的保证 |
|--------|--------------|----------------|
| `of(delay, action)` | "我有一个延迟 d 的防抖动作。" | 创建防抖器；delay 必须为正；调度器使用虚拟线程。 |
| `trigger()` | "我又触发了，重置倒计时。" | 取消当前挂起的任务，创建新任务；多次 trigger 在 delay 内会一直重置；防抖器关闭后无效果。 |
| `flush()` | "立即执行挂起的操作，别再等了。" | 取消计时器并立即执行 `action.run()`；无挂起时无效果；吞掉 action 的异常。 |
| `cancel()` | "取消挂起的操作，但保留防抖器。" | 取消计时器；不影响后续 `trigger()` 调用。 |
| `isPending()` | "我看看有没有挂起的操作。" | 有挂起且未完成返回 true；无或已关闭返回 false。 |
| `close()` | "我用完了，释放调度器。" | 关闭调度器线程；幂等。 |

##### 2.4.3 使用场景

1. **搜索框输入防抖**：用户连续输入时不去打后端，停止输入 300ms 后才触发搜索。
2. **表单自动保存**：用户编辑表单时频繁触发"保存"，停止编辑 1 秒后真正保存。
3. **窗口 resize 事件**：浏览器 resize 事件高频触发，做防抖后只在 resize 停止后才执行重新布局。
4. **按钮防重复提交**：用户连续点击按钮，只在最后一次点击 500ms 后才真正提交。
5. **编辑器内容同步**：编辑器内容变更时频繁触发同步，防抖 2 秒后同步到服务端。

##### 2.4.4 没有这个组件时的样子

```java
public class ManualDebouncer {
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> future;

    public void trigger(Runnable action, Duration delay) {
        synchronized (this) {
            if (future != null) {
                future.cancel(false);
            }
            future = scheduler.schedule(action, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
```

痛点：

- 每次创建都需要写 `cancel + schedule` 模板。
- 调度器关闭与 cancel 逻辑容易漏。
- 多线程并发 trigger 需要加锁。
- `flush()`（立即执行）要单独实现。

##### 2.4.5 使用这个组件

```java
Debouncer debouncer = Debouncer.of(Duration.ofMillis(300), this::save);
onChange(() -> debouncer.trigger());
debouncer.close();
```

##### 2.4.6 完整代码示例

```java
import com.richie.component.concurrency.algorithm.Debouncer;
import java.time.Duration;

public class DebouncerExamples {

    // 1) 搜索框输入防抖
    private final Debouncer searchDebouncer = Debouncer.of(
        Duration.ofMillis(300),
        () -> {
            String keyword = searchBox.getValue();
            searchService.search(keyword);
        }
    );

    public void onUserInput(String input) {
        searchBox.setValue(input);
        searchDebouncer.trigger();  // 用户每输入一个字符都触发，停止 300ms 后才搜索
    }

    // 2) 表单自动保存
    private final Debouncer saveDebouncer = Debouncer.of(
        Duration.ofSeconds(1),
        () -> formService.save(currentForm)
    );

    public void onFormChange() {
        saveDebouncer.trigger();  // 每次编辑都重置，停止编辑 1s 后才保存
    }

    // 3) 立即执行挂起的操作（flush）
    public void onSubmitClick() {
        saveDebouncer.flush();  // 用户点击提交时立即保存，不等 1s 计时
    }

    // 4) 取消挂起的操作（cancel）
    public void onResetClick() {
        saveDebouncer.cancel();  // 用户点击重置时取消保存
    }

    // 5) 查询状态
    public void onSaveButtonHover() {
        if (saveDebouncer.isPending()) {
            statusBar.show("正在等待保存...");
        }
    }

    // 6) 资源管理
    // @PreDestroy
    public void cleanup() {
        searchDebouncer.close();
        saveDebouncer.close();
    }
}
```

##### 2.4.7 API 速查

| 方法 | 说明 |
|------|------|
| `of(delay, action)` | 创建防抖器（delay 必须为正） |
| `trigger()` | 触发防抖（重复触发重置计时） |
| `flush()` | 立即执行挂起的操作 |
| `cancel()` | 取消挂起的操作（不影响后续 trigger） |
| `isPending()` | 是否有待执行的操作 |
| `close()` | 关闭防抖器（幂等） |

---

### 板块三：动态线程池（`threadpool/`）

在标准 `ThreadPoolExecutor` 之上扩展"事件驱动 resize + 拒绝计数 + 一站式运行态快照"，让线程池参数可以在不重启应用的前提下运行时调整。`threadpool/` 子包包含三大组件：`DynamicExecutor`（继承自 `ThreadPoolExecutor` 的可调整线程池）、`PoolResizeEvent`（热更新事件，只更新非 null 字段）、`PoolStatus`（9 个核心指标的不可变运行态快照）。多池场景通过 `platform.concurrency.thread-pools` Map 配置驱动，`@Resource(name = "<poolName>")` 按名注入，无需在业务侧手写 `@Bean`。

### 3.0 实现原理

本小节从源码层面拆解动态线程池的运行机制，便于读者判断扩展边界与改造空间。3.1 节是面向调用的 API 描述，本节是面向实现的设计描述。

#### 3.0.1 整体架构

整套机制分为三层：核心层负责热更新能力本身；自动装配层负责把多个池子挂到 Spring 容器；配置中心联动层负责监听外部配置变更并触发核心层。三层职责严格分离，核心层完全不依赖 Spring 容器，单测可以脱离 Spring 启动。

```
┌──────────────────────────────────────────────────────────────────┐
│  第一层：核心（threadpool/）                                       │
│                                                                  │
│   DynamicExecutor                                                │
│     ├─ extends ThreadPoolExecutor                                │
│     ├─ onResize(PoolResizeEvent)   事件驱动 resize               │
│     └─ snapshot() → PoolStatus     一站式运行态快照               │
│                                                                  │
│   PoolResizeEvent  (record, 不可变事件对象)                        │
│     └─ corePoolSize / maximumPoolSize /                          │
│        keepAliveTime / rejectedHandler   全部可缺省              │
│                                                                  │
│   PoolStatus  (record, 不可变快照)                                 │
│     └─ poolSize / activeCount / queueSize / ... / rejectedCount  │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │  resize
┌──────────────────────────────────────────────────────────────────┐
│  第二层：自动装配（config/）                                       │
│                                                                  │
│   AlgorithmAutoConfiguration                                     │
│     ├─ 绑定 ConcurrencyProperties.thread-pools                   │
│     ├─ 遍历 Map<String, PoolProperties> 注册多个 Bean            │
│     └─ Bean 名 = Map key，支持 @Qualifier / @Resource / Map 注入 │
└──────────────────────────────────────────────────────────────────┘
                              ▲
┌──────────────────────────────────────────────────────────────────┐
│  第三层：配置中心联动（config/ThreadPoolConfigRefresher.java）     │
│                                                                  │
│   @ConditionalOnClass(name = "org.springframework.cloud.context" │
│                        .refresh.event.EnvironmentChangeEvent)    │
│                                                                  │
│   ThreadPoolConfigRefresher                                      │
│     ├─ @EventListener(EnvironmentChangeEvent.class)              │
│     ├─ 过滤 keys 前缀 platform.concurrency.thread-pools.*        │
│     ├─ Binder 重新绑定最新 PoolProperties Map                    │
│     └─ 对每个变更池调用 DynamicExecutor.onResize(...)            │
└──────────────────────────────────────────────────────────────────┘
```

各层关键点：

- **核心层零 Spring 依赖**：`DynamicExecutor` 仅继承 `java.util.concurrent.ThreadPoolExecutor`，可在任何 Java 进程内使用。
- **装配层只做"配置 → Bean"的转换**：`AlgorithmAutoConfiguration` 启动时遍历 `platform.concurrency.thread-pools` Map，把每个 key 注册成一个 `DynamicExecutor` 单例 Bean，Bean 名 = key，方便 `@Qualifier` 引用。
- **联动层按需激活**：只有当 Spring Cloud 的 `EnvironmentChangeEvent` 类在 classpath 上时才生效，缺这个依赖项目里不会出现这个 Bean。对 Nacos/Apollo/Config/ZK/Consul 等任何能触发该事件的配置中心都通用。

#### 3.0.2 调参流程

以 Nacos 推送为例，完整链路如下：

```
Nacos 控制台修改配置
        │
        ▼
Nacos Client 长轮询发现变更
        │
        ▼
Spring Cloud 刷新 Environment
        │
        ▼
发布 EnvironmentChangeEvent
        │
        ▼
ThreadPoolConfigRefresher.onEnvironmentChange()
        │
        ▼
过滤 keys: platform.concurrency.thread-pools.* 命中
        │
        ▼
Binder 重新绑定 ConcurrencyProperties.thread-pools Map
        │
        ▼
与本地旧 PoolProperties diff
        │
        ▼
仅对变化池构造 PoolResizeEvent（非 null 字段填充，null 表示不调整）
        │
        ▼
DynamicExecutor.onResize(event)
        │
        ▼
内部按字段调用 setCorePoolSize / setMaximumPoolSize /
        setKeepAliveTime / setRejectedExecutionHandler
```

`ThreadPoolConfigRefresher` 关键源码结构（位于 `com.richie.component.concurrency.config.ThreadPoolConfigRefresher`）：

```java
import com.richie.component.concurrency.config.ConcurrencyProperties;
import com.richie.component.concurrency.config.PoolProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import com.richie.component.concurrency.threadpool.PoolResizeEvent;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class ThreadPoolConfigRefresher {

    private static final String POOL_PREFIX = "platform.concurrency.thread-pools.";

    private final Map<String, DynamicExecutor> executors;
    private final Environment environment;
    private final Map<String, PoolProperties> latestSnapshot = new HashMap<>();

    public ThreadPoolConfigRefresher(
            Map<String, DynamicExecutor> executors,
            Environment environment) {
        this.executors = executors;
        this.environment = environment;
    }

    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        Set<String> changedKeys = event.getKeys();
        boolean poolChanged = changedKeys.stream()
                .anyMatch(k -> k.startsWith(POOL_PREFIX));
        if (!poolChanged) {
            return;
        }

        ConcurrencyProperties props = Binder.get(environment)
                .bind("platform.concurrency", ConcurrencyProperties.class)
                .orElse(null);
        if (props == null || props.getThreadPools() == null) {
            return;
        }

        props.getThreadPools().forEach((poolName, newPool) -> {
            DynamicExecutor executor = executors.get(poolName);
            if (executor == null) {
                return;
            }
            PoolProperties oldPool = latestSnapshot.get(poolName);
            if (oldPool != null && oldPool.equals(newPool)) {
                return;
            }
            applyResize(executor, newPool);
            latestSnapshot.put(poolName, copyOf(newPool));
        });
    }

    private void applyResize(DynamicExecutor executor, PoolProperties p) {
        PoolResizeEvent.Builder builder = PoolResizeEvent.builder();
        boolean changed = false;

        if (p.getCorePoolSize() != null) {
            builder.corePoolSize(p.getCorePoolSize());
            changed = true;
        }
        if (p.getMaximumPoolSize() != null) {
            builder.maximumPoolSize(p.getMaximumPoolSize());
            changed = true;
        }
        if (p.getKeepAliveTime() != null) {
            builder.keepAliveTime(p.getKeepAliveTime());
            changed = true;
        }
        if (p.getRejectedHandler() != null) {
            builder.rejectedHandler(toHandler(p.getRejectedHandler()));
            changed = true;
        }
        if (changed) {
            executor.onResize(builder.build());
        }
    }

    private RejectedExecutionHandler toHandler(String name) {
        // AbortPolicy / CallerRunsPolicy / DiscardPolicy / DiscardOldestPolicy
        return switch (name.toLowerCase()) {
            case "callerrunspolicy" -> new ThreadPoolExecutor.CallerRunsPolicy();
            case "discardpolicy" -> new ThreadPoolExecutor.DiscardPolicy();
            case "discardoldestpolicy" -> new ThreadPoolExecutor.DiscardOldestPolicy();
            default -> new ThreadPoolExecutor.AbortPolicy();
        };
    }
}
```

**关键设计取舍**：

- **diff 在装配层做**：`ThreadPoolConfigRefresher` 缓存最近一次 `PoolProperties` 快照，仅在字段真正变化时才下发 `onResize`，避免无谓触发。
- **null 字段保持原值**：`PoolProperties` 的字段都是包装类型（`Integer` / `Duration`），配置里不写就代表"不动"，不会强制覆盖成默认值。
- **`queueCapacity` 与 `threadNamePrefix` 不可动态变更**：
  - **`queueCapacity`** 调整需要新建 `LinkedBlockingQueue` 并把旧队列里的任务"搬家"，JDK `ThreadPoolExecutor` 没有公开 API 支持运行时换队列。本组件选择不在 `onResize` 里支持，启动时通过 `platform.concurrency.thread-pools.<poolName>.queue-capacity` 配置；如确需调整，请重启。
  - **`threadNamePrefix`** 只影响"此后新创建的 Worker"，存量线程名无法回改。如果调参后立即观察线程名不一致是预期行为。
  - 上述两项如果出现在配置变更中，本组件会在日志里写一条 WARN 后静默忽略，不抛异常中断整批刷新。

#### 3.0.3 拒绝计数原理

`ThreadPoolExecutor.rejectedExecution(Runnable, ThreadPoolExecutor)` 是包内可见方法，子类无法通过覆写来统计拒绝数。`DynamicExecutor` 用了"装饰器 + 委托"模式解决：

```java
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

public class DynamicExecutor extends ThreadPoolExecutor {

    private final CountingHandler countingHandler;

    public DynamicExecutor(int core, int max, long keepAlive, TimeUnit unit,
                           java.util.concurrent.BlockingQueue<Runnable> queue,
                           RejectedExecutionHandler userHandler) {
        super(core, max, keepAlive, unit, queue, userHandler);
        // super 已把 userHandler 存进父类字段；
        // 我们再把它"抢"出来包一层
        this.countingHandler = new CountingHandler(userHandler);
        // 重新设给父类，替换原 handler
        super.setRejectedExecutionHandler(countingHandler);
    }

    @Override
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        // 业务代码调 getRejectedExecutionHandler() 拿到的还是 userHandler
        return countingHandler.getDelegate();
    }

    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        // onResize 切策略时走这里，原子替换委托对象
        countingHandler.setDelegate(handler);
    }

    public long getRejectionCount() {
        return countingHandler.getCount();
    }

    public void resetRejectionCount() {
        countingHandler.reset();
    }

    private static final class CountingHandler implements RejectedExecutionHandler {
        private static final AtomicLong COUNTER = new AtomicLong();
        private volatile RejectedExecutionHandler delegate;

        CountingHandler(RejectedExecutionHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            COUNTER.incrementAndGet();
            delegate.rejectedExecution(r, e);
        }

        long getCount() { return COUNTER.get(); }
        void reset()    { COUNTER.set(0); }
        RejectedExecutionHandler getDelegate() { return delegate; }
        void setDelegate(RejectedExecutionHandler h) { this.delegate = h; }
    }
}
```

关键点：

- **TPE 内部 `execute()` 实际调用的是 `CountingHandler.rejectedExecution`**：先 `incrementAndGet()` 再委托给用户 handler，计数 + 用户原行为都保留。
- **`getRejectedExecutionHandler()` 覆写后返回用户原始 handler**：监控代码 / 第三方工具 / 业务侧调试时拿到的还是当初 `new` 出来的那个对象，对包装层无感知。
- **`onResize` 切拒绝策略走 `setRejectedExecutionHandler`**：覆写后的 setter 不直接改父类字段，而是 `countingHandler.setDelegate(newHandler)` 原子替换委托对象，TPE 内部的 handler 引用与新委托保持一致。

> 注：上面 `CountingHandler` 是简化示例，用于说明原理；实际实现中计数器是 `DynamicExecutor` 的实例字段而非静态变量，每个池独立计数。

#### 3.0.4 与 dynamic-tp 的定位差异

一句话总结：**本组件是"刚好够用"的 JDK 25 + Spring Boot 原生实现；dynamic-tp 是"功能完备"的开源平台方案**。

两者的完整差异对比、决策树与仓库地址见 [常见问题 Q1](#q1为什么不继续使用-dynamic-tp)。下面是定位差异的速查版：

| 维度 | 本组件（`DynamicExecutor`） | dynamic-tp |
|------|---------------------------|------------|
| 设计目标 | 轻量替代，覆盖 80% 平台线程池场景 | 平台级线程池管理框架，覆盖全部高级能力 |
| 依赖体积 | 0 额外依赖（仅随本组件引入） | 需引入 `dynamic-tp-spring-boot-starter` 及其传递依赖 |
| 自动装配 | 零配置（引入本组件即可） | 需要引入 starter 并配置 `dynamic-tp` 命名空间 |
| 核心能力 | resize + 拒绝计数 + 快照 | resize + 拒绝计数 + 快照 + 告警 + Web 后台 + 任务包装 + 持久化 |
| 适配 Spring Cloud | 通过 `EnvironmentChangeEvent` 间接适配所有配置中心 | 内置 Nacos/Apollo/Etcd 等专用适配器 |
| 学习曲线 | 阅读本 README 即可上手 | 需要熟悉其配置模型与扩展点 |
| 适用项目 | 简单调参 + 基础监控够用的中小项目 | 需要告警 / Web 后台 / 任务包装 / 多环境管理的中大型项目 |

**何时选谁**：

1. **选本组件**：多池 + 调参 + 拒绝计数 + 基础快照 + 已用 Spring Cloud 配置中心联动（无需写额外监听代码）。
2. **选本组件**：不希望为线程池管理再引入 starter 与一整套传递依赖。
3. **选本组件**：线程池数量可控（一般 < 10 个），人工调参 + 监控告警够用。
4. **选 dynamic-tp**：需要钉钉/企微/飞书/邮件等多渠道告警。
5. **选 dynamic-tp**：需要可视化 Web 后台管理线程池参数与运行时指标。
6. **选 dynamic-tp**：需要 MDC / TransmittableThreadLocal 等任务包装能力，或需要线程池数据持久化。
7. **两者共存**：核心池用本组件（轻量、零依赖），需要特殊处理的池单独接 dynamic-tp（按池粒度引入）。

dynamic-tp 仓库地址：<https://github.com/dromara/dynamic-tp>，详细能力矩阵与使用文档见该仓库 README。

#### 3.1 DynamicExecutor 动态线程池

##### 3.1.1 它是什么

`DynamicExecutor` 继承自标准 `ThreadPoolExecutor`，对外暴露两组"标准 TPE 没有的能力"：

1. **事件驱动 resize**：`onResize(PoolResizeEvent)` 可以在不重启应用的前提下，运行时调整 `corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler`。事件源可以是 Nacos、Etcd、Admin API、定时器或 JMX，本组件不绑定任何具体事件源。
2. **拒绝计数 + 状态快照**：构造时自动把用户传入的 `RejectedExecutionHandler` 包装为内部 `CountingHandler`，对 `getRejectedExecutionHandler()` 调用方完全透明。`snapshot()` 一次返回 9 个监控指标的不可变快照。

底层 TPE 行为与 JDK `ThreadPoolExecutor` 完全一致（`execute` / `submit` / `shutdown` / `shutdownNow` / `awaitTermination` 等接口都不变），可作为 TPE 的直接替换。

##### 3.1.2 接口设计语义

| 方法 | 调用方的承诺 | 被调用方的保证 |
|------|--------------|----------------|
| `DynamicExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue)` | "我要创建可调整的线程池。" | 构造参数与标准 TPE 一致；handler 默认 `AbortPolicy`，被自动包装为 `CountingHandler` 以支持拒绝计数。 |
| `DynamicExecutor(..., threadFactory, handler)` | "我要自定义线程工厂与拒绝策略。" | 完整参数构造；handler 同样被 `CountingHandler` 透明包装，`getRejectedExecutionHandler()` 返回用户传入的原始 handler。 |
| `onResize(PoolResizeEvent)` | "我要热更新线程池参数。" | 只更新事件中非 null 的字段（`corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler`），null 字段表示"不调整"。事件为 null 时忽略并打 WARN。 |
| `snapshot()` | "我要拍快照监控。" | 返回不可变 `PoolStatus`（含 `poolSize` / `activeCount` / `queueSize` / `queueRemainingCapacity` / `completedTaskCount` / `totalTaskCount` / `rejectedCount` 等 9 个字段）。 |
| `getRejectionCount()` | "我要拿到拒绝总数。" | 返回自线程池创建以来累计被拒绝的任务数（长整型，跨重启需要持久化自行处理）。 |
| `resetRejectionCount()` | "我要重置拒绝计数。" | 把内部计数器清零（与 `snapshot()` 的 `rejectedCount` 同步刷新）。 |
| `PoolResizeEvent.builder()` | "我要构造热更新事件。" | 所有字段可缺省（null 表示不调整）；拒绝策略可选；不包含 `queueCapacity`（见下方设计决策）。 |
| `PoolStatus.builder()` | "我要手工构造一个状态快照。" | 9 个字段独立赋值，便于测试或对接第三方监控。 |

##### 3.1.3 使用场景

1. **配置中心驱动调参**：Nacos/Etcd 配置变更监听器收到参数变更后，构造 `PoolResizeEvent` 调 `onResize`，做到"不停机调参"。
2. **多租户 / 多业务线隔离**：每种业务场景一个 `DynamicExecutor` 实例（订单处理、通知推送、报表导出各自独立池），通过 `@Qualifier` 按名注入。
3. **拒绝计数与告警**：在 `snapshot().getRejectedCount()` 持续增长时触发告警；与 `queueSize` / `activeCount` 一起暴露给 Prometheus。
4. **突发流量保护**：上游 API 故障导致任务积压时，运行时把 `rejectedHandler` 从 `AbortPolicy` 切到 `CallerRunsPolicy`，利用调用方线程消化部分压力。
5. **生产环境故障注入测试**：用 `forceResize` 直接调大 / 调小线程池参数，验证下游服务在各种并发级别下的表现。

##### 3.1.4 没有这个组件时的样子

调整 `ThreadPoolExecutor` 参数需要拿到原始引用，且无法在不重启应用的前提下批量调参：

```java
// 1) 修改后必须重启
@Bean
public ThreadPoolExecutor orderExecutor() {
    return new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000));
}

// 2) 想"动态调"需要自己造轮子：监听配置中心 + 反序列化 + 调用 setter
@NacosConfigListener(dataId = "order-executor.yml")
public void onConfigChange(String newConfig) {
    PoolConfig cfg = parse(newConfig);
    orderExecutor.setCorePoolSize(cfg.getCore());
    orderExecutor.setMaximumPoolSize(cfg.getMax());
    // ...还要解决 setRejectedExecutionHandler 没法做计数的问题
    // ...还要写快照序列化逻辑
}
```

痛点：

- 调整逻辑散落在配置监听器里，没有统一的事件 API。
- `setRejectedExecutionHandler` 调用后用户 handler 被覆盖，监控/告警无法统计"被拒绝了几次"。
- `ThreadPoolExecutor` 没有一站式快照，需要分别调用 `getPoolSize` / `getActiveCount` / `getQueue().size()` 等，线程之间无一致性保证。
- 多池配置要逐个写 `@Bean`，无法用 `Map<String, PoolProperties>` 配置驱动。

##### 3.1.5 使用这个组件

```java
DynamicExecutor orderExecutor = new DynamicExecutor(
    4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000)
);

// 配置中心变更后调一行即可
orderExecutor.onResize(PoolResizeEvent.builder()
    .corePoolSize(16)
    .maximumPoolSize(32)
    .build());

// 监控直接拿快照
PoolStatus status = orderExecutor.snapshot();
```

##### 3.1.6 完整代码示例

```java
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import com.richie.component.concurrency.threadpool.DynamicExecutorService;
import com.richie.component.concurrency.threadpool.PoolResizeEvent;
import com.richie.component.concurrency.threadpool.PoolStatus;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DynamicExecutorExamples {

  // ========== 1) 基本创建和使用 ==========
  private final DynamicExecutor orderExecutor = new DynamicExecutor(
          4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000));

  public void submitOrderTask(Runnable task) {
    orderExecutor.execute(task);
  }

  // ========== 2) 运行时 onResize 热更新 ==========
  public void onNacosConfigChange(int newCore, int newMax, Duration newKeepAlive) {
    // 只传需要变更的字段，其他字段保持原值
    orderExecutor.onResize(PoolResizeEvent.builder()
            .corePoolSize(newCore)
            .maximumPoolSize(newMax)
            .keepAliveTime(newKeepAlive)
            .build());
  }

  // ========== 3) PoolStatus 快照监控 ==========
  @Scheduled(fixedRate = 5000)
  public void reportStatus() {
    PoolStatus status = orderExecutor.snapshot();
    metrics.gauge("threadpool.pool_size", status.getPoolSize());
    metrics.gauge("threadpool.active_count", status.getActiveCount());
    metrics.gauge("threadpool.queue_size", status.getQueueSize());
    metrics.gauge("threadpool.queue_remaining", status.getQueueRemainingCapacity());
    metrics.gauge("threadpool.rejected_count", status.getRejectedCount());
    metrics.gauge("threadpool.completed_count", status.getCompletedTaskCount());
  }

  // ========== 4) CountingHandler 拒绝计数 ==========
  public void onRejectionThresholdExceeded() {
    if (orderExecutor.snapshot().getRejectedCount() > 100) {
      // 拒绝数超过阈值，临时把策略改为 CallerRunsPolicy
      orderExecutor.onResize(PoolResizeEvent.builder()
              .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
              .build());
    }
  }

  public long getRejectionCount() {
    return orderExecutor.getRejectionCount();  // 累计值
  }

  public void resetDailyRejectionCount() {
    orderExecutor.resetRejectionCount();  // 重置
  }

  // ========== 5) Spring 多池注入 ==========

  // 方式一：按名称获取指定线程池（@Resource + name）
  // 对应配置: platform.concurrency.thread-pools.order-executor.*
  @jakarta.annotation.Resource(name = "order-executor")
  private DynamicExecutor orderExecutorByName;

  // 方式二：@Qualifier + @Autowired
  @Qualifier("notification-executor")
  @Autowired
  private DynamicExecutor notificationExecutor;

  // 方式三：批量注入所有线程池
  @Autowired
  private Map<String, DynamicExecutor> allExecutors;

  public void executeByPoolName(String poolName, Runnable task) {
    DynamicExecutor pool = allExecutors.get(poolName);
    if (pool != null) {
      pool.execute(task);
    } else {
      throw new IllegalArgumentException("Unknown pool: " + poolName);
    }
  }

  // ========== 6) 优雅关闭 ==========
  @PreDestroy
  public void shutdown() throws InterruptedException {
    orderExecutor.shutdown();
    if (!orderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      orderExecutor.shutdownNow();
    }
  }
}
```

##### 3.1.7 设计决策

- **拒绝策略作为运行时事件参数**：拒绝策略本质上是一个"运行时策略选择"，项目上线初期配置不合理时，无需改代码重新发版即可通过 `onResize` 热更新。
- **不包含 `queueCapacity`**：队列容量热更新无法解决"生产者-消费者速率不匹配"问题，反而会在实例宕机时积压更多未消费任务，扩大故障爆炸半径。`queueCapacity` 只能在启动时通过 `platform.concurrency.thread-pools.*.queue-capacity` 配置。
- **CountingHandler 透明计数**：TPE 的 `rejectedExecution` 不是可覆写的 protected 方法，无法通过子类覆写来计数。`DynamicExecutor` 构造时把用户 handler 包装为内部 `CountingHandler`（`AtomicLong` 计数器），再传给 `super(...)`。`getRejectedExecutionHandler()` 经过覆写后返回用户原始 handler，监控代码不会感知包装层的存在。
- **`onResize` 只更新非 null 字段**：调用方可以只关心"我想改的那一项"，避免"调一个参数要带 4 个其他参数"的样板。事件本身不可变，便于跨线程传递。
- **构造函数签名与标准 TPE 一致**：4 / 5 / 6 / 7 参构造分别覆盖常见使用场景，IDE 提示和文档继承自 `ThreadPoolExecutor`，无学习成本。

##### 3.1.8 API 速查

| 方法 | 说明 |
|------|------|
| `DynamicExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue)` | 4 参构造，默认线程工厂 + `AbortPolicy` |
| `DynamicExecutor(..., workQueue, threadFactory)` | 5 参构造，自定义线程工厂 + `AbortPolicy` |
| `DynamicExecutor(..., workQueue, handler)` | 6 参构造，默认线程工厂 + 自定义 handler |
| `DynamicExecutor(..., workQueue, threadFactory, handler)` | 完整 7 参构造 |
| `onResize(event)` | 运行时调整（只更新非 null 字段） |
| `snapshot()` | 一站式运行态快照（`PoolStatus`，含 9 个字段） |
| `getRejectionCount()` | 累计拒绝数 |
| `resetRejectionCount()` | 重置拒绝计数 |
| `PoolResizeEvent.builder()` | 创建热更新事件 |
| `PoolResizeEvent.Builder.corePoolSize(Integer)` | 设置核心线程数（null = 不调整） |
| `PoolResizeEvent.Builder.maximumPoolSize(Integer)` | 设置最大线程数（null = 不调整） |
| `PoolResizeEvent.Builder.keepAliveTime(Duration)` | 设置空闲存活时间（null = 不调整） |
| `PoolResizeEvent.Builder.rejectedHandler(handler)` | 设置拒绝策略（null = 不调整） |
| `PoolStatus.getPoolSize()` | 当前工作线程数（含空闲） |
| `PoolStatus.getActiveCount()` | 当前活跃线程数 |
| `PoolStatus.getQueueSize()` | 队列已用容量 |
| `PoolStatus.getQueueRemainingCapacity()` | 队列剩余容量 |
| `PoolStatus.getCompletedTaskCount()` | 已完成任务数 |
| `PoolStatus.getTotalTaskCount()` | 已提交任务总数 |
| `PoolStatus.getRejectedCount()` | 累计拒绝数 |
| `PoolStatus.getCorePoolSize()` | 当前核心线程数 |
| `PoolStatus.getMaximumPoolSize()` | 当前最大线程数 |

##### 3.1.9 PoolResizeEvent 热更新事件

`PoolResizeEvent` 是 `DynamicExecutor.onResize` 接收的不可变事件对象。它把"我想改的线程池参数"打包成一个值对象，让配置中心监听器只需要关心"变化的部分"，不需要回传完整的当前配置。事件共 4 个字段：`corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler`，全部允许为 `null`（表示"不调整该项"）。

通过 `PoolResizeEvent.builder()` 构建，所有字段独立可缺省；事件本身不可变，可跨线程安全传递。`onResize` 收到事件后只更新非 null 字段，保持其余参数不变。`queueCapacity` 不在事件字段中——队列容量热更新无法解决"生产者-消费者速率不匹配"问题，反而会在实例宕机时积压更多未消费任务，扩大故障爆炸半径。`queueCapacity` 只能在启动时通过 `platform.concurrency.thread-pools.<poolName>.queue-capacity` 配置（详见 [配置说明](#配置说明)）。`PoolResizeEvent` 与 Nacos/Etcd 等配置中心的对接示例见 [常见问题 Q17](#q17如何与配置中心nacosetcd集成实现动态调参)。

```java
PoolResizeEvent event = PoolResizeEvent.builder()
    .corePoolSize(16)
    .maximumPoolSize(32)
    .keepAliveTime(Duration.ofMinutes(2))
    .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
    .build();
orderExecutor.onResize(event);
```

##### 3.1.10 PoolStatus 运行态快照

`PoolStatus` 是 `DynamicExecutor.snapshot()` 返回的不可变运行态快照。它把 9 个核心监控指标（`poolSize` / `activeCount` / `queueSize` / `queueRemainingCapacity` / `completedTaskCount` / `totalTaskCount` / `rejectedCount` / `corePoolSize` / `maximumPoolSize`）一次性聚合成值对象，避免业务代码分别调用 `ThreadPoolExecutor` 多个 getter 时遇到的线程间一致性缺失问题。`PoolStatus.builder()` 提供 9 个字段独立赋值入口，便于单元测试中 mock 任意状态，或对接第三方监控系统做序列化。

所有字段都是不可变的 `int` / `long`，可安全跨线程传递而无需同步。`PoolStatus.getRejectedCount()` 与 `DynamicExecutor.getRejectionCount()` 共享同一个内部计数器，调用 `resetRejectionCount()` 后下一次 `snapshot()` 的 `rejectedCount` 会同步归零。典型用法见 [板块三 3.1.6](#3316-完整代码示例) 的 `reportStatus()` 示例。

```java
PoolStatus status = orderExecutor.snapshot();
metrics.gauge("threadpool.pool_size", status.getPoolSize());
metrics.gauge("threadpool.rejected_count", status.getRejectedCount());
```

---

## 配置说明

本节按"三大板块"对应子系统展开配置：`rate-limiter` 板块二的 `RateLimiter`（[板块二 2.2](#22-ratelimiter-令牌桶限流器)）、`circuit-breaker` 板块二的 `CircuitBreaker`（[板块二 2.3](#23-circuitbreaker-熔断器)）、`thread-pools` 板块三的 `DynamicExecutor`（[板块三 3.1](#31-dynamicexecutor-动态线程池)）。其余板块的组件（`StructuredConcurrency` / `VirtualThreadFactory` / `BatchProcessor` / `Retryer` / `Debouncer`）无运行时配置入口，仅以编程式 Builder 提供。

### 1. 统一配置入口

所有配置通过 `platform.concurrency.*` 统一前缀挂载，由 `ConcurrencyProperties` 绑定：

```yaml
platform:
  concurrency:
    rate-limiter:    # 令牌桶限流器（板块二 2.2）
      ...
    circuit-breaker: # 熔断器（板块二 2.3）
      ...
    thread-pools:    # 动态线程池（板块三 3.1，多池，key = 池名 = Bean 名）
      <poolName>:
        ...
```

### 2. 完整 YAML 示例

```yaml
spring:
  threads:
    virtual:
      enabled: true   # 决定使用虚拟线程还是平台线程池

platform:
  concurrency:
    # ========== 令牌桶限流器（板块二 2.2） ==========
    rate-limiter:
      # 是否注册 RateLimiter Bean 到 Spring 容器（默认：false）
      # 启用后容器关闭时自动调用 close() 释放调度器线程
      enabled: true

      # 每秒补充令牌数（默认：100），同时也是令牌桶容量
      permits-per-second: 200

    # ========== 熔断器（板块二 2.3） ==========
    circuit-breaker:
      # 是否注册 CircuitBreaker Bean 到 Spring 容器（默认：false）
      enabled: true

      # 失败率阈值（0.0 ~ 1.0，默认：0.5）
      # 内部会乘以 100 转为整数百分比
      failure-rate-threshold: 0.6

      # 滑动窗口大小（默认：10，最小 10）
      # 窗口越大失败率统计越平滑，但对突发故障的反应越迟钝
      sliding-window-size: 100

      # OPEN 状态持续时间（默认：30s）
      # 超时后进入 HALF_OPEN 试探状态
      wait-duration: 15s

      # 半开探测阶段需要的连续成功次数（默认：3）
      # 当前版本底层熔断器采用单次探测即切换状态，该字段预留扩展
      half-open-max-successes: 3

    # ========== 动态线程池（板块三 3.1，多池，key = 池名 = Bean 名） ==========
    # 有配置就创建对应 DynamicExecutor Bean，不配置就没有
    # 业务方可通过 @Resource(name = "<poolName>") 或 @Qualifier("<poolName>") 注入
    thread-pools:
      order-executor:
        # 核心线程数（默认：4）
        core-pool-size: 8
        # 最大线程数（默认：8）
        maximum-pool-size: 16
        # 空闲线程存活时间（默认：60s）
        keep-alive-time: 30s
        # 工作队列容量，基于此值创建 LinkedBlockingQueue（默认：2000）
        queue-capacity: 500
        # 线程名前缀；为空时使用 "<poolName>-" 作为前缀
        thread-name-prefix: order-worker-
        # 拒绝策略（大小写不敏感，默认：AbortPolicy）
        # 可选：AbortPolicy / CallerRunsPolicy / DiscardPolicy / DiscardOldestPolicy
        rejected-handler: CallerRunsPolicy

      notification-executor:
        core-pool-size: 2
        maximum-pool-size: 4
        keep-alive-time: 60s
        queue-capacity: 200
        rejected-handler: AbortPolicy
```

### 3. 字段说明

#### 3.1 令牌桶限流器（`platform.concurrency.rate-limiter.*`，对应板块二 2.2）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否注册 `RateLimiter` Bean 到 Spring 容器 |
| `permits-per-second` | int | `100` | 每秒补充令牌数（同时也是桶容量） |

#### 3.2 熔断器（`platform.concurrency.circuit-breaker.*`，对应板块二 2.3）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否注册 `CircuitBreaker` Bean 到 Spring 容器 |
| `failure-rate-threshold` | double | `0.5` | 失败率阈值（0.0-1.0） |
| `sliding-window-size` | int | `10` | 滑动窗口大小（最小 10） |
| `wait-duration` | Duration | `30s` | OPEN 状态持续时间 |
| `half-open-max-successes` | int | `3` | 半开探测成功次数（预留扩展，当前不参与计算） |

#### 3.3 动态线程池（`platform.concurrency.thread-pools.*`，对应板块三 3.1）

多池配置，每个 key 即为一个命名线程池，同时作为对应 Spring Bean 的名称。业务方通过 `@Resource(name = "<poolName>")` / `@Qualifier("<poolName>")` 注入，或通过 `Map<String, DynamicExecutor>` 批量注入。

每个池（`PoolProperties`）的可配置项：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `{poolName}.core-pool-size` | int | `4` | 核心线程数（即使没有任务也会保留）|
| `{poolName}.maximum-pool-size` | int | `8` | 最大线程数（任务队列满时创建新线程直到此上限）|
| `{poolName}.keep-alive-time` | Duration | `60s` | 超过核心线程数的线程空闲此时间后被回收 |
| `{poolName}.queue-capacity` | int | `2000` | 工作队列容量，基于此值创建 `LinkedBlockingQueue` |
| `{poolName}.thread-name-prefix` | String | `""`（=`<poolName>-`）| 线程名前缀 |
| `{poolName}.rejected-handler` | String | `AbortPolicy` | 拒绝策略，大小写不敏感，可选 `AbortPolicy` / `CallerRunsPolicy` / `DiscardPolicy` / `DiscardOldestPolicy` |

> 容器关闭时自动调用每个已注册 `DynamicExecutor` 的 `shutdown()`，并最多等待 5 秒；若未终止则降级为 `shutdownNow()`。

### 4. 关键设计决策

- **`failure-rate-threshold` 暴露为 `double`**：配置可读性比 `0.5` 比 `50` 更直观；内部构建 `CircuitBreaker.Builder` 时乘以 100 转为整数百分比。
- **`permits-per-second` 是唯一可调的 RateLimiter 配置**：默认工厂是 `RateLimiter.ofTokensPerSecond`，适合"开箱即用"场景；复杂场景（自定义周期、容量）建议业务侧自己 `@Primary` Bean 覆盖。
- **`half-open-max-successes` 预留字段**：当前底层采用"单次探测成功即关闭"语义，配置项保留是为了未来扩展时无需破坏配置契约。
- **`thread-pools` 是 `Map<String, PoolProperties>`**：key 是池名（同时也是 Bean 名），有配置就注册对应 `DynamicExecutor` Bean，不配置就没有。运行时调参不通过此配置项，而是通过 `DynamicExecutor.onResize(PoolResizeEvent)` API（详见 [板块三 3.1.9](#319-poolresizeevent-热更新事件) 与 [板块三 3.1](#31-dynamicexecutor-动态线程池)）。

---

## 最佳实践

### 通用原则

1. **优先用语义命名方法**：`gatherAll`、`race`、`withDeadline` 一眼表达意图，比 `CompletableFuture.allOf().get()` 更易维护。
2. **虚拟线程优先**：JDK 25 项目默认启用 `spring.threads.virtual.enabled=true`，让 IO 密集型任务享受百万级并发。
3. **零依赖哲学**：本组件不绑定第三方并发库；如确实需要 Resilience4j、Hystrix 等，请在业务模块自行引入。
4. **配置驱动**：能用配置解决的不要写代码。`platform.concurrency.*` 配置项覆盖 90% 场景。

### 板块一：结构化并发与虚拟线程

#### 结构化并发（对应 1.1 StructuredConcurrency）

1. **优先 `gatherAll` 替代手动 `CompletableFuture`**：错误传播更准确、取消语义更清晰。
2. **竞速模式仅用于容灾**：对同一目标的多种实现，任意一路成功即返回（多级缓存、多区域容灾）。
3. **超时模式保护外部调用**：所有外部 API 调用必须带超时（推荐 200-500ms）。
4. **大任务分批执行**：超过 100 个并发任务时，用 `gatherBatched` 控制并发度，避免 fork 过多虚拟线程。
5. **尽力汇聚用于容错聚合**：当目标是"尽可能拿到一些结果"而非"全部成功"时，用 `gatherAllBestEffort` 而非 `gatherAll`。

#### 虚拟线程（对应 1.2 VirtualThreadFactory）

1. **始终设置线程名前缀**：便于问题排查（JFR、Micrometer）。
2. **ScopedValue 替代 ThreadLocal**：虚拟线程下 ThreadLocal 有性能问题，优先使用 `ScopedValue`。
3. **不要池化虚拟线程**：虚拟线程轻量级（创建成本约 1KB），每次创建新线程比池化管理更简单。
4. **避免在虚拟线程内做 CPU 密集型计算**：虚拟线程适合 IO 阻塞场景，CPU 密集型仍应用平台线程池。

#### 批量处理器（对应 1.3 BatchProcessor）

1. **合理设置并行度**：IO 密集型用 50-200；CPU 密集型用 `CPU 数 ± 2`。
2. **设置适当超时**：避免单个慢任务阻塞整体，超时后部分结果仍可使用。
3. **始终检查 `result.hasError()`**：对失败项做补偿处理。
4. **虚拟线程下无需池化**：`BatchProcessor` 利用虚拟线程轻量级特性，不需要额外线程池。

### 板块二：限流与容错算法

#### 重试器（对应 2.1 Retryer）

1. **始终设置合理退避**：`initialBackoff` 从 50-200ms 起步，避免短时间密集重试击垮下游。
2. **多客户端场景务必启用抖动**：`jitter(true)` 避免服务端同时收到大量重试请求。
3. **精准限定重试异常**：`retryOn(IOException.class, TimeoutException.class)` 避免对业务异常（如 4xx）重试。
4. **关键业务用 Fallback**：`execute(task, fallback)` 在重试耗尽后返回兜底值，避免上游崩溃。
5. **不要重试 `Error`**：`Retryer` 只 catch `Exception`，`OutOfMemoryError` 等会被透传。

#### 限流器（对应 2.2 RateLimiter）

1. **根据下游 SLA 选择限流粒度**：外部 API 用 `ofTokensPerSecond`；批量任务用 `ofTokensPerDuration`。
2. **按场景选 `tryAcquire` 变体**：
   - 业务关键路径 → `tryAcquire()`（严格非阻塞）
   - SLA 严苛 → `tryAcquire(Duration)`（限时阻塞）
   - 后台任务 → `acquire()`（无限阻塞）
3. **多令牌粒度控制突发**：批量导入等场景用 `tryAcquire(n)` 一次消费多个令牌。
4. **避免弃用 API**：不要使用 `ofTryAcquireTimeout` 或 `Builder.tryAcquireTimeoutEnabled`，改用 `ofTokensPerDuration` + `tryAcquire(Duration)`。
5. **资源管理**：在 `@PreDestroy` 钩子中调用 `close()`，释放调度器线程。

#### 熔断器（对应 2.3 CircuitBreaker）

1. **粒度按服务划分**：每个下游服务一个熔断器实例，避免一个故障污染所有调用。
2. **失败率 vs 失败次数**：高 QPS 用失败率模式；低 QPS 用绝对次数模式（避免冷启动误判）。
3. **OPEN 持续时间 > 下游平均恢复时间**：通常 5-30 秒；过短会反复抖动，过长会拉长恢复延迟。
4. **Fallback 兜底不可省**：始终用 `execute(task, fallback)` 而非 `executeOrThrow`，避免熔断导致接口 500。
5. **不要在事务代码中熔断**：熔断快速失败会绕过事务回滚路径，对外服务熔断更合适。
6. **监控 `state()`**：把 `CircuitBreaker.State` 暴露到 Prometheus，便于实时观察熔断状态。

#### 防抖器（对应 2.4 Debouncer）

1. **延迟时间按场景调整**：搜索框 300ms；表单保存 1s；按钮防重复 500ms。
2. **在 Spring Bean 销毁时调用 `close()`**：释放虚拟线程调度器。
3. **`flush()` 用于用户主动操作**：用户点击"立即保存"按钮时调用 `flush()`，绕过等待时间。
4. **`cancel()` 用于撤销**：用户点击"取消编辑"时调用 `cancel()`，取消挂起的操作。
5. **不要在防抖操作里做重活**：防抖的初衷是延迟执行，建议防抖动作本身仍走异步。

### 板块三：动态线程池

#### 动态线程池（对应 3.1 DynamicExecutor）

1. **每个业务场景一个独立线程池**：订单处理、通知推送、报表导出等不同负载特征使用独立池，避免一个慢调用拖累全部任务。
2. **`onResize` 与配置中心对接**：在 Nacos/Etcd 配置监听器中构造 `PoolResizeEvent` 并调用 `onResize`，实现"不停机调参"。`PoolResizeEvent.builder()` 支持仅传递需要变更的字段。
3. **监控 `PoolStatus.rejectedCount` 趋势**：拒绝计数持续上升说明线程池或队列过小，应触发调大线程数或切换到 `CallerRunsPolicy`。
4. **不要频繁 `onResize`**：过度调整会导致 `ThreadPoolExecutor` 内部反复重建 Worker，建议每次调整间隔 > 1 分钟。
5. **保留默认队列容量避免生产消费失衡**：高频抖动队列大小会加大系统抖动；队列容量通过 `platform.concurrency.thread-pools.<poolName>.queue-capacity` 启动时配置，运行期不参与 `onResize`。

### 错误处理哲学

| 组件 | 推荐错误处理方式 |
|------|------------------|
| `StructuredConcurrency.gatherAll` | 让异常透传，由上层做统一错误处理 |
| `StructuredConcurrency.gatherAllBestEffort` | 检查 `outcome.failures()`，对失败任务做补偿 |
| `Retryer.execute(task)` | `catch (RetryExhaustedException)` 透传到上层或包装为业务异常 |
| `Retryer.execute(task, fallback)` | 直接用 fallback，不需要 catch |
| `BatchProcessor` | 检查 `result.hasError()`，失败项单独重试或补偿 |
| `RateLimiter` | 非阻塞场景检查返回值；阻塞场景由 `InterruptedException` 决定 |
| `CircuitBreaker` | 关键业务用 `execute(task, fallback)`；监控场景用 `executeOrThrow` |
| `Debouncer` | 内部 `action` 异常会被吞掉（防抖器设计如此），关键操作建议自带 try/catch |

### 测试建议

| 组件 | 测试建议 |
|------|----------|
| `StructuredConcurrency` | 用 `Mockito` mock 各任务；构造混合成功/失败场景验证顺序保证 |
| `VirtualThreadFactory` | 验证线程名前缀；用 `ScopedValue.get(key)` 验证上下文传递 |
| `Retryer` | 用 `AtomicInteger` 计数器控制失败次数；验证退避时间是否符合预期 |
| `BatchProcessor` | 验证 `successCount` / `failureCount`；验证 `results()` 与输入顺序一致 |
| `RateLimiter` | 高频调用后断言 `availablePermits()` 接近 0；验证 `close()` 后行为 |
| `CircuitBreaker` | 用 `Builder.build(LongSupplier)` 注入假时钟；构造失败率超阈值的场景 |
| `Debouncer` | 用 `CountDownLatch` 等待 action 执行；验证 `trigger()` 重置计时 |

### 监控与可观测性

1. **虚拟线程监控**：JDK 25 的 JFR 原生支持虚拟线程事件（`jdk.VirtualThreadStart`、`jdk.VirtualThreadPinned`），可以用 `jfr` 命令或 JMC 实时观察。
2. **Micrometer 指标**：建议暴露以下指标：
   - `circuit_breaker.state`：当前状态（0=CLOSED / 0.5=HALF_OPEN / 1=OPEN）
   - `rate_limiter.available_permits`：当前可用令牌数
   - `batch_processor.success_count` / `failure_count`：批量处理成功/失败数
3. **熔断状态告警**：当 `CircuitBreaker.State` 变为 OPEN 时发告警通知；持续 OPEN 时间过长时升级为严重告警。
4. **批量处理失败明细**：定期扫描 `BatchResult.errors()`，失败率超过阈值时触发告警。

---

## 常见问题

### Q1：为什么不继续使用 Dynamic-TP？

本组件的 `DynamicExecutor` 是**轻量级动态线程池实现**，目标是用 0 额外依赖覆盖 80% 平台线程池场景（多池管理、运行时调参、拒绝计数、运行态快照）。它不追求功能完备，而是把"最常用、缺它就难受"的能力做对、做透。dynamic-tp（[dromara/dynamic-tp](https://github.com/dromara/dynamic-tp)）则是一套功能完备的平台级线程池管理框架。两者的能力边界如下。

#### 特性对比

| 特性 | 本组件（`DynamicExecutor`） | dynamic-tp |
|------|---------------------------|------------|
| 配置中心对接（Nacos/Apollo/Config/ZK/Consul） | 通过 Spring Cloud `EnvironmentChangeEvent` 间接支持，覆盖所有能触发该事件的配置中心 | 内置 Nacos / Apollo / Etcd / ZK 等专用适配器 |
| 运行时调参（core/max/keepAlive/handler） | 支持（`onResize` 事件驱动，详见 [板块三 3.0.2](#302-调参流程)） | 支持（管理后台或配置中心推送） |
| 拒绝计数 | 支持（`CountingHandler` 透明包装，详见 [板块三 3.0.3](#303-拒绝计数原理)） | 支持 |
| 运行态快照 | 支持（`PoolStatus` 9 个指标一站式返回） | 支持 |
| 告警通知（钉钉/企微/飞书/邮件） | **不支持**（需业务侧自行接 Prometheus / 监控告警） | 内置多种告警渠道 |
| Web 管理后台 | **不支持** | 内置可视化界面 |
| 任务包装（MDC / Ttl / Transmittable） | **不支持** | 内置 `MdcRunnable` / `TtlRunnable` / `TransmittableThreadLocal` 包装 |
| 线程池数据持久化 | **不支持** | 内置基于数据库 / Redis 的持久化 |
| 依赖体积 | 0 额外依赖（随本组件引入即可） | 需引入 `dynamic-tp-spring-boot-starter` 及传递依赖 |
| 自动装配 | 零配置（引入依赖后按 `platform.concurrency.thread-pools.*` 写配置即可） | 需引入 starter 并配置 `dynamic-tp` 命名空间 |

#### 决策指南

- **只想要"多池 + 调参 + 基本监控"**：用本组件，零额外依赖，[板块三 3.1](#31-dynamicexecutor-动态线程池) 的 API 一行就能把线程池跑起来，调参靠 `onResize` + 配置中心联动。
- **需要告警 / Web 后台 / 任务包装 / 数据持久化**：引入 [dynamic-tp](https://github.com/dromara/dynamic-tp)，它已经把这些能力做成开箱即用的功能。
- **已经在用 Spring Cloud 配置中心**：本组件可直接复用 `EnvironmentChangeEvent`，无需写监听代码（详见 [板块三 3.0.2](#302-调参流程)），这种情况下本组件的接入成本更低。
- **不希望再为线程池管理引入 starter 及其传递依赖**：用本组件，本组件仅依赖 Spring Boot（`provided` 作用域），不会让项目变胖。
- **两者不冲突**：核心池用本组件，需要特殊处理（如要 MDC 透传、要落库告警）的池单独接 dynamic-tp，按池粒度混合使用没问题。

#### 总结

> **本组件 = 轻量 + 够用**；dynamic-tp = **功能完备 + 完备意味着更重的依赖**。先用本组件跑起来，遇到真需求再上 dynamic-tp，是大多数项目的合理演进路径。

如需使用 dynamic-tp，请在业务模块自行引入依赖，本组件不强制依赖，也不互斥。

### Q2：StructuredConcurrency 和 TenantStructuredTaskScope 有什么区别？

- `StructuredConcurrency`：通用并发语义封装（汇聚、竞速、超时、分批、尽力汇聚），不绑定任何特定上下文。
- `TenantStructuredTaskScope`：基于 `ScopedValue` 的租户上下文感知并发工具，提供 `awaitAll` / `anySuccessful` 等工厂方法。

两者是互补关系，不是替代关系。可以在 `TenantStructuredTaskScope` 内调用 `StructuredConcurrency` 的静态方法。

### Q3：如何监控虚拟线程？

1. JDK 25 JFR：使用 `jcmd <pid> JFR.start name=vthreads` 启动 JFR 录制，事件 `jdk.VirtualThreadStart` / `jdk.VirtualThreadPinned` 可观测虚拟线程。
2. Micrometer：使用 `jvm.threads.*` 指标，包含平台线程和虚拟线程统计。
3. JMX：`ThreadMXBean.getThreadInfo(long[])` 支持获取虚拟线程信息。

### Q4：RateLimiter 与 CircuitBreaker 应该先调用哪个？

通常先限流后熔断：

- **限流**控制并发请求速率（保护自身线程不被吃满）。
- **熔断**保护下游服务（在持续故障时快速失败）。

典型链路：`RateLimiter.tryAcquire() → CircuitBreaker.execute(task, fallback) → 业务逻辑`。

### Q5：熔断器触发后什么时候会自动恢复？

OPEN 状态持续 `openDuration`（默认 10 秒）后自动进入 HALF_OPEN，下一次调用被作为"试探"：

- 试探成功 → 回到 CLOSED（恢复正常）
- 试探失败 → 再次进入 OPEN 并重新计时

### Q6：Debouncer 和 RateLimiter 有什么区别？

- `Debouncer`：延迟执行动作，多次触发会重置计时器；用于"停止操作 X 秒后执行 Y"。
- `RateLimiter`：控制单位时间内的最大调用次数；用于"每秒最多调用 N 次"。

两者解决不同问题，不应混用。

### Q7：什么时候该用 Retryer，什么时候不该用？

**该用**：

- 临时性网络故障（503、超时）。
- 数据库连接获取失败。
- 分布式锁瞬时竞争。

**不该用**：

- 业务异常（如 4xx）：用 `retryOn` 过滤掉。
- 事务性操作：重试可能导致重复执行（如支付）。
- 长时间运行的任务：重试成本过高。

### Q8：BatchProcessor 的 mapParallel 和 forEach 怎么选？

- **`forEach`**：只关心副作用（写库、推送、通知），不需要返回值。
- **`mapParallel`**：需要每条数据的处理结果，且要按输入顺序收集。

如果两者都需要，先用 `mapParallel` 拿到结果，再用 `result.results()` 做副作用。

### Q9：half-open-max-successes 配置项为什么不生效？

当前底层 `CircuitBreaker.Builder` 采用"单次探测成功即关闭"语义，`half-open-max-successes` 字段保留是为了未来扩展（升级到多次探测模式时无需破坏配置契约）。如果你需要"多次探测成功才关闭"的语义，建议在业务侧自建熔断器或等待本组件升级。

### Q10：如何在测试中替换 RateLimiter / CircuitBreaker 的时间相关行为？

- **RateLimiter**：使用 `RateLimiter.builder()` 创建自定义实例；测试中调用 `close()` 快速释放资源。
- **CircuitBreaker**：使用 `CircuitBreaker.builder().build(LongSupplier)` 注入假时钟，模拟"10 秒后 OPEN→HALF_OPEN"的转换。
- **Debouncer**：使用 `Duration.ofMillis(50)` 短延迟便于测试；用 `CountDownLatch` 等待 action 执行。

### Q11：配置修改后需要重启应用吗？

是的。所有 `platform.concurrency.*` 配置项通过 `ConcurrencyProperties` 在启动时绑定，运行期修改不会自动生效。如果需要动态调整，可以：

1. 通过 JMX 暴露 `RateLimiter.availablePermits()` 等监控指标。
2. 业务侧自行用 `@RefreshScope` 重新创建组件实例（不推荐，影响其他 Bean 的依赖）。
3. 重启应用。

### Q12：为什么 ofTryAcquireTimeout 被标记为已弃用？

自 2.2.0 起，本组件统一了 `try*` 前缀的语义约定：所有 `try*` 方法必须是严格非阻塞的。`ofTryAcquireTimeout(permits, timeout)` 隐式让 `tryAcquire()` 在 `timeout` 内等待，违反该约定。改用 `ofTokensPerDuration(permits, window)` + `tryAcquire(Duration)` 显式表达限时阻塞语义更清晰。

### Q13：本组件依赖了 Spring Boot，哪些版本兼容？

| 组件版本 | 兼容范围 |
|----------|----------|
| Spring Boot | 4.0.x（`provided` 作用域，不强制绑定） |
| JDK | 25+（使用 `StructuredTaskScope`、`ScopedValue`、`Thread.ofVirtual`） |

低于 JDK 25 的环境无法使用本组件（因为依赖 `StructuredTaskScope` preview API）。

### Q14：如何创建并使用多个命名线程池？

在 `application.yml` 中按命名声明即可：

```yaml
platform:
  concurrency:
    thread-pools:
      order-executor:
        core-pool-size: 8
        maximum-pool-size: 16
      notification-executor:
        core-pool-size: 2
        maximum-pool-size: 4
```

代码注入（每个 key 对应一个 Spring Bean，bean name = key，可作为 `@Qualifier` 引用值）：

```java
@Resource(name = "order-executor")
private DynamicExecutor orderExecutor;

@Resource(name = "notification-executor")
private DynamicExecutor notificationExecutor;

// 或批量注入
@Autowired
private Map<String, DynamicExecutor> executors;
```

无需在业务侧手写 `@Bean` 注册方法，`AlgorithmAutoConfiguration` 在 `PostConstruct` 阶段会遍历 `platform.concurrency.thread-pools` Map 并把每个池注册成 Spring 单例 Bean。容器关闭时自动 `shutdown()`。

### Q15：如何与配置中心（Nacos/Etcd）集成实现动态调参？

`PoolResizeEvent` 支持仅传递需要变更的字段，`null` 表示"不调整"——监听器无需关心当前完整配置：

```java
@NacosConfigListener(dataId = "order-executor.yml")
public void onConfigChange(String newConfig) {
    OrderExecutorConfig cfg = parse(newConfig);
    if (cfg.getCorePoolSize() != null || cfg.getMaximumPoolSize() != null) {
        orderExecutor.onResize(PoolResizeEvent.builder()
                .corePoolSize(cfg.getCorePoolSize())
                .maximumPoolSize(cfg.getMaximumPoolSize())
                .keepAliveTime(cfg.getKeepAliveTime())
                .rejectedHandler(cfg.getRejectedHandler())  // 可选
                .build());
    }
}
```

事件本身不可变，可跨线程安全传递。`onResize` 是幂等操作：连续两次同样的事件与调用一次效果相同。建议在监听器外部加去抖（例如两次配置变更间隔 < 30s 视为同一次调参），避免短时间反复调参触发 `ThreadPoolExecutor` 内部 Worker 重建。

### Q16：DynamicExecutor 和普通的 `ThreadPoolExecutor` 有什么区别？

`DynamicExecutor` 继承自标准 `ThreadPoolExecutor`，因此所有 TPE 原生 API（`execute` / `submit` / `shutdown` / `shutdownNow` / `awaitTermination` 等）都不变，可作为 TPE 的直接替换。差异点仅在扩展能力：

- **`onResize` 热更新能力**：不修改代码、不发版即可调整 `corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler`；只更新事件中非 null 的字段，null 字段保持不变。
- **拒绝计数透明包装**：`CountingHandler`（内部 `private static` 包装器）自动统计被拒绝的任务数；`getRejectedExecutionHandler()` 返回用户原始 handler，业务代码完全感知不到包装层的存在。
- **`PoolStatus` 快照**：一站式获取 9 个运行态指标（`poolSize` / `activeCount` / `queueSize` / `queueRemainingCapacity` / `completedTaskCount` / `totalTaskCount` / `rejectedCount` / `corePoolSize` / `maximumPoolSize`），无需多次调用 TPE getter。
- **无侵入**：构造函数签名与标准 TPE 完全一致，老代码切换零成本。
- **不依赖任何配置中心**：`onResize` 只是一个公开方法，事件来源由调用方决定（Nacos/Etcd/Admin API/定时器/JMX），本组件内部不绑定任何外部依赖。

---

## 相关文档

### JDK 官方

- [JDK 25 `StructuredTaskScope` Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 446: Scoped Values (Final)](https://openjdk.org/jeps/446)
- [JEP 480: Structured Concurrency (Third Preview)](https://openjdk.org/jeps/480)

### Spring 官方

- [Spring Boot Virtual Threads Support](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/reference/features/external-config.html)

### 业内参考

- [AWS 架构师 Marc Brooker：全量抖动算法](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
- [Resilience4j CircuitBreaker 文档](https://resilience4j.readme.io/docs/circuitbreaker)
- [Hystrix 熔断器原理](https://github.com/Netflix/Hystrix/wiki/How-it-Works)
- [令牌桶算法 Wikipedia](https://en.wikipedia.org/wiki/Token_bucket)
- [Dynamic-TP（动态线程池）— dromara 开源，功能完备的开源替代方案](https://github.com/dromara/dynamic-tp)

### 项目内文档

- [atlas-richie-component 组件库总览](../README.md)
- [Richie 平台总览](../../README.md)