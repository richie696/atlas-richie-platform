# atlas-richie-component-concurrency

> Concurrency utilities for the Richie platform, focused on high-frequency pattern encapsulation for **JDK 25 Structured Concurrency** and **Virtual Threads**.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Environment Requirements](#environment-requirements)
- [Quick Start](#quick-start)
- [Core Concepts at a Glance](#core-concepts-at-a-glance)
- [The Three Modules in Detail](#the-three-modules-in-detail)
  - [Module 1: Structured Concurrency and Virtual Threads (`virtual/`)](#module-1-structured-concurrency-and-virtual-threads-virtual)
    - [1.1 StructuredConcurrency](#11-structuredconcurrency-structured-concurrency)
    - [1.2 VirtualThreadFactory](#12-virtualthreadfactory-virtual-thread-factory)
    - [1.3 BatchProcessor](#13-batchprocessor-batch-processor)
  - [Module 2: Rate Limiting and Fault Tolerance Algorithms (`algorithm/`)](#module-2-rate-limiting-and-fault-tolerance-algorithms-algorithm)
    - [2.1 Retryer](#21-retryer)
    - [2.2 RateLimiter](#22-ratelimiter-token-bucket-rate-limiter)
    - [2.3 CircuitBreaker](#23-circuitbreaker)
    - [2.4 Debouncer](#24-debouncer)
  - [Module 3: Dynamic Thread Pool (`threadpool/`)](#module-3-dynamic-thread-pool-threadpool)
    - [3.0 Implementation Principles](#30-implementation-principles)
      - [3.0.1 Overall Architecture](#301-overall-architecture)
      - [3.0.2 Tuning Workflow](#302-tuning-workflow)
      - [3.0.3 Rejection Counting Principle](#303-rejection-counting-principle)
      - [3.0.4 Positioning Difference from dynamic-tp](#304-positioning-difference-from-dynamic-tp)
    - [3.1 DynamicExecutor](#31-dynamicexecutor-dynamic-thread-pool)
- [Configuration Reference](#configuration-reference)
- [Best Practices](#best-practices)
- [FAQ](#faq)
- [Related Documentation](#related-documentation)

---

## Overview

`atlas-richie-component-concurrency` is the concurrency utilities component for the Richie platform. Rather than wrapping third-party thread pool management libraries, it directly leverages JDK 25 standard concurrency primitives (`StructuredTaskScope`, virtual threads, `ScopedValue`) to encapsulate the 8 most common patterns found in distributed and high-concurrency scenarios into semantically clear utilities.

Design philosophy:

- **Replace boilerplate with semantic naming**: Method names like `gatherAll`, `race`, `withDeadline`, `gatherBatched`, and `gatherAllBestEffort` describe the design intent themselves, so callers immediately know which concurrency pattern they have chosen.
- **Virtual threads first**: All underlying implementations are built on virtual threads, enabling million-level concurrency for I/O-bound scenarios.
- **Zero third-party concurrency dependencies**: No binding to Resilience4j, Guava RateLimiter, Hystrix, etc. Token bucket, circuit breaking, rate limiting, and debouncing are all implemented in-house, making it easier for business teams to evolve and troubleshoot uniformly.
- **Both declarative and imperative APIs**: Core components (`Retryer`, `RateLimiter`, `CircuitBreaker`, `Debouncer`, `BatchProcessor`) provide imperative Builders, while also exposing `@ConfigurationProperties` configuration entries via `ConcurrencyProperties` for out-of-the-box usage.

> Applicable versions: JDK 25, Spring Boot 4.0.x, Spring Framework 7.x.

---

## Key Features

This component is divided into three modules by scenario responsibility. Each module corresponds to one source sub-package:

| Module                                                    | Component               | Problem It Solves                                                                            | One-line Value                                                                                     |
|-----------------------------------------------------------|-------------------------|----------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Structured Concurrency & Virtual Threads** (`virtual/`) | `StructuredConcurrency` | JDK `StructuredTaskScope` API is verbose                                                     | One-line invocation covers 5 modes: gather, race, deadline, batched gather, best-effort gather     |
| **Structured Concurrency & Virtual Threads** (`virtual/`) | `VirtualThreadFactory`  | Virtual threads are hard to observe; context propagation is hard                             | Virtual thread factory with naming prefix + ScopedValue bindings                                   |
| **Structured Concurrency & Virtual Threads** (`virtual/`) | `BatchProcessor`        | Batch concurrent execution requires manual management of semaphores, thread counts, timeouts | Fluent API + error isolation + results returned in input order                                     |
| **Rate Limiting & Fault Tolerance** (`algorithm/`)        | `Retryer`               | Transient failures need manual exponential backoff                                           | Exponential backoff + full jitter + exception filtering + Fallback in one                          |
| **Rate Limiting & Fault Tolerance** (`algorithm/`)        | `RateLimiter`           | `Semaphore` is awkward to use and requires manual timers                                     | Token bucket + three waiting semantics (non-blocking / time-limited blocking / unbounded blocking) |
| **Rate Limiting & Fault Tolerance** (`algorithm/`)        | `CircuitBreaker`        | Downstream failures need manual circuit breaking to prevent avalanches                       | Three-state machine + failure rate/count/time window + single-probe                                |
| **Rate Limiting & Fault Tolerance** (`algorithm/`)        | `Debouncer`             | `ScheduledExecutorService` debouncing boilerplate                                            | One `trigger()` method replaces the `cancel + schedule` pattern                                    |
| **Dynamic Thread Pool** (`threadpool/`)                   | `DynamicExecutor`       | Standard `ThreadPoolExecutor` lacks runtime adjustment                                       | Event-driven resize + rejection counting + status snapshot + `@Qualifier` multi-pool injection     |
| **Dynamic Thread Pool** (`threadpool/`)                   | `PoolResizeEvent`       | Tuning events scattered in config listeners; lacks unified abstraction                       | Immutable event object; only non-null fields are updated; one-line integration with config center  |
| **Dynamic Thread Pool** (`threadpool/`)                   | `PoolStatus`            | `ThreadPoolExecutor` needs multiple getters to assemble a runtime snapshot                   | Immutable snapshot of 9 core metrics in one call; naturally in sync with rejection counter         |

Additional capabilities:

- Spring Boot auto-configuration for the three subsystems: rate limiting, circuit breaking, and dynamic thread pool.
- Complete configuration property binding (`platform.concurrency.*`).
- All source code comes with Javadoc; the component uses `sealed interface` / `record` to express immutable results.
- No runtime reflection; all APIs are statically validated at compile time.

---

## Environment Requirements

| Dependency  | Version | Notes                                                           |
|-------------|---------|-----------------------------------------------------------------|
| JDK         | 25+     | Uses `StructuredTaskScope`, `ScopedValue`, `Thread.ofVirtual()` |
| Spring Boot | 4.0.x   | Auto-configuration                                              |
| Maven       | 3.9.0+  | Build tool                                                      |

---

## Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-concurrency</artifactId>
    <version>${atlas.richie.version}</version>
</dependency>
```

No need to separately introduce Resilience4j, Guava, or other concurrency libraries. This component only depends on Spring Boot (in `provided` scope).

### 2. Minimal Example

```java
import com.richie.component.concurrency.virtual.StructuredConcurrency;
import java.time.Duration;
import java.util.List;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        // Query user and orders in parallel
        var results = StructuredConcurrency.gatherAll(List.of(
            () -> userService.findById(1L),
            () -> orderService.findByUser(1L)
        ));
        User user = results.get(0);
        List<Order> orders = results.get(1);

        // Must return within 500ms; auto-cancel on timeout
        Data data = StructuredConcurrency.withDeadline(
            () -> externalService.query(),
            Duration.ofMillis(500)
        );
    }
}
```

---

## Core Concepts at a Glance

### What is Structured Concurrency

`java.util.concurrent.StructuredTaskScope`, introduced in JDK 25, treats a group of concurrent tasks as a single "unit of work". The parent task forks child tasks via `fork()` and waits for all of them to complete via `join()`. When the parent task ends, regardless of whether the children have finished, the scope automatically ensures they are cancelled or joined. This "parent-child binding + lifecycle consistency" semantics is more suitable than `CompletableFuture` for expressing "a group of mutually independent parallel tasks".

This component's `StructuredConcurrency` utility encapsulates these semantics into 5 methods:

| Method                | Pattern                                  | When to Use                                             |
|-----------------------|------------------------------------------|---------------------------------------------------------|
| `gatherAll`           | All succeed or none                      | Multiple independent queries that need "all-or-nothing" |
| `race`                | First success wins                       | Multi-route disaster recovery, cache penetration        |
| `withDeadline`        | Bounded execution time                   | Any outbound call should have a timeout                 |
| `gatherBatched`       | Concurrent batching                      | Very large task count, concurrency control needed       |
| `gatherAllBestEffort` | Single failure does not affect the whole | Bulk aggregation where a few failures are acceptable    |

### What is a Virtual Thread

Lightweight threads introduced in JDK 21+. The JVM unparks blocking operations onto a pool of OS threads. For I/O-bound tasks, millions of virtual threads can exist simultaneously without exhausting memory. All `forEach`, `mapParallel`, and `fork` operations in this component run on virtual threads, so business code does not need to worry about thread pool size.

### What is ScopedValue

Immutable thread-local variables introduced in JDK 21+, with better performance than `ThreadLocal` and free from inheritance-chain pollution. `VirtualThreadFactory.Builder.scopedValue()` can bind context at virtual thread creation time, avoiding the manual `where().run()` boilerplate.

---

## The Three Modules in Detail

This component is divided into three modules by scenario responsibility, each corresponding to one source sub-package. Readers may skip as needed: read Module 1 for concurrency orchestration, Module 2 for call protection, and Module 3 for thread pool tuning.

### Module 1: Structured Concurrency and Virtual Threads (`virtual/`)

Focused on encapsulating the high-frequency patterns of JDK 25 `StructuredTaskScope` and `Thread.ofVirtual()`, packaging "concurrency orchestration patterns + virtual thread context propagation" into semantically clear static utilities and factory classes. The `virtual/` sub-package contains three components: `StructuredConcurrency` (5 structured concurrency modes: gather, race, deadline, batched gather, best-effort gather), `VirtualThreadFactory` (virtual thread factory with naming prefix and `ScopedValue` bindings), and `BatchProcessor` (concurrency throttling + error isolation + batch processing with results collected in input order). All APIs in this module run on virtual threads, so business code does not need to worry about thread pool size or lifecycle.

#### 1.1 StructuredConcurrency

##### 1.1.1 What It Is

`StructuredConcurrency` is a high-frequency-scenario wrapper around JDK 25 `StructuredTaskScope`. The design goal is to expose "concurrency orchestration patterns" as the API: the caller only needs to choose one of `gatherAll` / `race` / `withDeadline` / `gatherBatched` / `gatherAllBestEffort`, and does not need to worry about `Joiner` selection, scope lifecycle, or the calling order of `Configuration.withTimeout` and other low-level details.

All underlying methods share a static `VirtualThreadFactory` (thread-name prefix `ar-concurrency-`), ensuring that the thread-name counter increments monotonically across multiple `open` calls.

##### 1.1.2 Interface Design Semantics

| Method                                | Caller's Promise                                                                                                                     | Callee's Guarantee                                                                                                                                                                     |
|---------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `gatherAll(tasks)`                    | "I provide a group of tasks; all must succeed for the result to be useful; any failure means I'm fine with the whole thing failing." | Runs all tasks in parallel; returns results in input order if all succeed; cancels the rest and propagates the original exception if any task fails.                                   |
| `gatherAllSuppliers(tasks)`           | Same as above, but tasks are expressed as `Supplier`, no checked-exception handling needed.                                          | Internally adapts `Supplier` to `Callable`; semantics are exactly the same as `gatherAll`.                                                                                             |
| `race(tasks)`                         | "Multiple implementations of the same goal; any one succeeding is enough; the rest of the results are discarded."                    | Runs all tasks in parallel; returns the first successful result; remaining tasks are auto-cancelled after the first success. If all fail, throws the last exception.                   |
| `raceSuppliers(tasks)`                | Same as above, but expressed as `Supplier`.                                                                                          | Same as above, adapted to `Callable`.                                                                                                                                                  |
| `withDeadline(task, timeout)`         | "I have an SLA for this single outbound call; I give up on timeout."                                                                 | Runs the task within `timeout`; on timeout the JDK scope auto-cancels the task and throws `TimeoutException`.                                                                          |
| `gatherBatched(tasks, batchSize)`     | "The task count is huge; I cannot fork all of them at once, or I'll spawn a sea of virtual threads."                                 | Runs `batchSize` tasks in parallel per batch, serially between batches; finally flattens and merges all batch results.                                                                 |
| `gatherAllBestEffort(tasks)`          | "I want as many results as possible; a single failure should not affect the others."                                                 | Runs all tasks in parallel, catches every `Throwable`, and finally returns both the success results and the failure details. Will **not** cancel other tasks on a single-task failure. |
| `gatherAllBestEffortSuppliers(tasks)` | Same as above, but expressed as `Supplier`.                                                                                          | Same as above, adapted to `Callable`.                                                                                                                                                  |

##### 1.1.3 Use Cases

1. **Order detail aggregation query**: query user info, order body, shipping address, and coupon status in parallel; any failure means the whole thing fails.
2. **Multi-level cache penetration**: query Redis and Caffeine in parallel; first hit wins.
3. **External API timeout control**: every external call must return within 500ms, otherwise fall back to fallback logic.
4. **Report data batch fetch**: pull data from 10 data sources in parallel; allow up to 2 failures while still showing partial data.
5. **Million-level ID validation**: split 1 million IDs into 100 batches of 10,000 each, validate concurrently, and start the next batch after each batch completes.

##### 1.1.4 What It Looks Like Without This Component

Writing 5 parallel queries with a timeout looks like this:

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
        pool.shutdownNow();  // Easy to forget; cannot guarantee subtasks actually end
    }
}
```

Pain points:

- Need to manually manage the thread pool and its lifecycle.
- Any timeout only cancels one Future; the other Futures may still be blocked in `f.get()`.
- `shutdownNow` does not guarantee subtasks actually end.
- The timeout in `Future.get(timeout)` means "wait until here", not a "global deadline"; the semantics are unintuitive.

##### 1.1.5 Using This Component

```java
List<Object> results = StructuredConcurrency.gatherAll(tasks);
```

Solved in one line.

##### 1.1.6 Full Code Examples

```java
import com.richie.component.concurrency.virtual.StructuredConcurrency;
import java.time.Duration;
import java.util.List;

public class StructuredConcurrencyExamples {

    // ========== Pattern 1: Gather — gatherAll ==========
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

    // ========== Pattern 2: Race (multi-level cache penetration) ==========
    public String getCacheValue(String key) throws Exception {
        return StructuredConcurrency.race(List.of(
            () -> l1Cache.get(key),
            () -> l2Cache.get(key),
            () -> remoteCache.get(key)
        ));
    }

    // ========== Pattern 3: Deadline — withDeadline ==========
    public String callExternal(Duration timeout) throws Exception {
        return StructuredConcurrency.withDeadline(
            () -> externalApi.query(timeout.dividedBy(2)),
            timeout
        );
    }

    // ========== Pattern 4: Batched — gatherBatched ==========
    public List<Boolean> validateIds(List<Long> ids, int batchSize) throws Exception {
        var tasks = ids.stream()
            .<Callable<Boolean>>map(id -> () -> validator.check(id))
            .toList();
        return StructuredConcurrency.gatherBatched(tasks, batchSize);
    }

    // ========== Pattern 5: Best-Effort Gather — gatherAllBestEffort ==========
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

    // ========== Supplier variant ==========
    public List<String> fetchBySuppliers() throws Exception {
        return StructuredConcurrency.gatherAllSuppliers(List.of(
            () -> userService.findName(1L),
            () -> orderService.findCode(1L)
        ));
    }
}
```

##### 1.1.7 API Quick Reference

| Method | Exception Propagation | Return Value |
|--------|----------------------|--------------|
| `gatherAll(Collection<Callable>)` | Any task throwing an exception propagates it and cancels the others | `List<T>` in input order |
| `gatherAllSuppliers(Collection<Supplier>)` | Same as above | `List<T>` in input order |
| `race(Collection<Callable>)` | Throws the last exception when all fail | `T` first successful result |
| `raceSuppliers(Collection<Supplier>)` | Same as above | `T` |
| `withDeadline(Callable, Duration)` | Throws `TimeoutException` on timeout, or the task's original exception | `T` |
| `gatherBatched(Collection<Callable>, int)` | Any task exception | `List<T>` flattened merge |
| `gatherAllBestEffort(Collection<Callable>)` | No exception (unless interrupted) | `BestEffortResult<T>` |
| `gatherAllBestEffortSuppliers(Collection<Supplier>)` | Same as above | `BestEffortResult<T>` |

`BestEffortResult` fields: `successes()` list of success results, `failures()` list of exceptions, `failedIndices()` list of failure indices, plus helper methods `hasAnySuccess()` / `successCount()` / `failureCount()`.

---

#### 1.2 VirtualThreadFactory

##### 1.2.1 What It Is

`VirtualThreadFactory` implements the standard `ThreadFactory`, but adds two things on top:

1. **Naming convention**: Every virtual thread has a readable prefix (e.g. `async-job-1`), which can be precisely located with JDK 25's `jcmd <pid> Thread.print` or JFR.
2. **ScopedValue bindings**: Use the Builder to "weld" `ScopedValue` key-value pairs onto the factory. From then on, all virtual threads created by this factory automatically carry that context, eliminating manual `where().run()` wrapping at every call site.

Underneath, threads are created via `Thread.ofVirtual().name(name).unstarted(...)` with zero reflection.

##### 1.2.2 Interface Design Semantics

| Method | Caller's Promise | Callee's Guarantee |
|--------|------------------|--------------------|
| `builder()` | "I want to customize a factory." | Returns a blank Builder; all fields have defaults (prefix `vt-`, no ScopedValue bindings). |
| `of(namePrefix)` | "I only need a naming prefix; defaults for the rest." | Creates a simple factory; the virtual thread name returned by `newThread(runnable)` has the format `<prefix><auto-incrementing-suffix>`. |
| `Builder.namePrefix(prefix)` | "My threads should use this prefix." | Sets the prefix; throws `NullPointerException` if the prefix is null. |
| `Builder.scopedValue(key, value)` | "Every virtual thread should carry this context." | Appends the key-value pair to the bindings list. |
| `Builder.scopedValues(bindings...)` | "I have multiple contexts to bind." | Replaces the bindings array wholesale (not appended). |
| `Builder.build()` | "I'm done configuring; give me the factory." | Returns an immutable factory instance; thread-safe. |

##### 1.2.3 Use Cases

1. **Unified thread naming convention**: business-side async tasks use different prefixes (`order-async-`, `report-async-`), making log troubleshooting crystal clear.
2. **Automatic tenant context propagation**: bind a tenant ID from `ScopedValue.newInstance()` to the factory; all virtual threads need no `ScopedValue.where(...)` wrapping.
3. **Spring `@Async` custom executor**: inject the factory into `ThreadPoolTaskExecutor.setThreadFactory(...)`; all `@Async` tasks run on virtual threads with prefixes.
4. **Log traceId propagation**: bind the traceId's `ScopedValue` to the factory; avoid copying `MDC.getCopyOfContextMap`.

##### 1.2.4 What It Looks Like Without This Component

```java
// Want naming + ScopedValue propagation
ThreadFactory factory = r -> {
    var carrier = ScopedValue.where(TRACE_ID, "abc123");
    return Thread.ofVirtual()
        .name("order-async-" + counter.incrementAndGet())
        .unstarted(() -> carrier.run(r));
};
```

This boilerplate has to be copied everywhere, and ScopedValue is easy to get wrong (forgetting the `.run()` call).

##### 1.2.5 Using This Component

```java
ThreadFactory factory = VirtualThreadFactory.builder()
    .namePrefix("order-async-")
    .scopedValue(TRACE_ID, "abc123")
    .build();
```

##### 1.2.6 Full Code Examples

```java
import com.richie.component.concurrency.virtual.VirtualThreadFactory;
import java.lang.ScopedValue;
import java.util.concurrent.ThreadFactory;

public class VirtualThreadFactoryExamples {

    // 1) Simplest usage
    ThreadFactory simple = VirtualThreadFactory.of("job-");
    Thread vt = simple.newThread(() -> System.out.println(Thread.currentThread().getName()));
    vt.start();  // Prints job-1

    // 2) Custom prefix + batch bindings
    static final ScopedValue<String> TENANT = ScopedValue.newInstance();
    static final ScopedValue<String> TRACE = ScopedValue.newInstance();

    ThreadFactory factory = VirtualThreadFactory.builder()
        .namePrefix("order-async-")
        .scopedValue(TENANT, "tenant-42")
        .scopedValue(TRACE, "trace-xyz")
        .build();

    Thread t1 = factory.newThread(() -> {
        // Bindings can be accessed directly inside the thread; no where().run() wrapping needed
        System.out.println(ScopedValue.get(TENANT));  // Prints tenant-42
        System.out.println(ScopedValue.get(TRACE));    // Prints trace-xyz
    });
    t1.start();

    // 3) ScopedValueBinding array style
    var bindings = new VirtualThreadFactory.ScopedValueBinding[] {
        new VirtualThreadFactory.ScopedValueBinding<>(TENANT, "tenant-99"),
        new VirtualThreadFactory.ScopedValueBinding<>(TRACE, "trace-zzz")
    };
    ThreadFactory factory2 = VirtualThreadFactory.builder()
        .namePrefix("report-async-")
        .scopedValues(bindings)
        .build();

    // 4) Combined with Spring ThreadPoolTaskExecutor
    // ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // executor.setThreadFactory(VirtualThreadFactory.of("async-"));
    // executor.initialize();
}
```

##### 1.2.7 API Quick Reference

| Method | Description |
|--------|-------------|
| `builder()` | Returns a Builder (default prefix `vt-`) |
| `of(namePrefix)` | Quick factory, prefix only |
| `Builder.namePrefix(prefix)` | Set the prefix |
| `Builder.scopedValue(key, value)` | Bind a single ScopedValue |
| `Builder.scopedValues(bindings...)` | Replace ScopedValue bindings in bulk |
| `Builder.build()` | Build an immutable factory |
| `newThread(runnable)` | Create a virtual thread (name format `<prefix><suffix>`) |

---

#### 1.3 BatchProcessor

##### 1.3.1 What It Is

`BatchProcessor` is a concurrent batch-processing utility built on top of JDK 25 `StructuredTaskScope`. It encapsulates "concurrency throttling + per-item error isolation + overall timeout + results collected in input order" into a fluent API:

- **Concurrency throttling**: A `Semaphore` controls the number of virtual threads running concurrently.
- **Error isolation**: a single failure does not stop other items; failure details are aggregated into a result object.
- **Overall timeout**: `StructuredTaskScope.withTimeout` controls the total duration; on timeout, returns the partially completed results.
- **Order preservation**: in `mapParallel` mode, the result list strictly corresponds to the input collection by index (failed items correspond to `null`).

##### 1.3.2 Interface Design Semantics

| Method | Caller's Promise | Callee's Guarantee |
|--------|------------------|--------------------|
| `of(items)` | "I have N items to process." | Creates a Builder and makes an immutable copy of the input. |
| `parallelism(n)` | "At most n tasks run at the same time." | `Semaphore(n)` throttling; n must be ≥ 1; default `max(2, CPU*2)`. |
| `timeout(d)` | "The whole batch takes at most d." | Controlled by `withTimeout(d)`; returns partial results on timeout. |
| `forEach(consumer)` | "I only care about side effects (DB writes, push), not return values." | Runs `consumer.accept(item)` concurrently; returns `BatchResult` (contains success/failure counts and exception details). |
| `mapParallel(mapper)` | "I need the result for each item, collected in input order." | Runs `mapper.apply(item)` concurrently; returns `BatchMappingResult` (result list aligned by index, failed items are `null`). |

##### 1.3.3 Use Cases

1. **Batch order processing**: 1,000 orders concurrently call the downstream shipping API; concurrency capped at 20; overall timeout 5 minutes.
2. **Batch ID query**: 10,000 user IDs concurrently queried against the database; results assembled in input order.
3. **Batch file upload**: 500 files uploaded concurrently; failed files retried separately.
4. **Data migration**: million records migrated concurrently; partial failures allowed but full error details must be captured.
5. **Batch message push**: 1,000 users pushed to concurrently; one failure does not affect the others.

##### 1.3.4 What It Looks Like Without This Component

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

Pain points:

- Overall timeout and semaphore timeout are two different dimensions and easily confused.
- Result order must be maintained manually using `AtomicReferenceArray`.
- Thread-pool shutdown and timeout-cancellation logic are verbose.
- Failed items and incomplete items are hard to distinguish.

##### 1.3.5 Using This Component

```java
BatchResult result = BatchProcessor.of(orders)
    .parallelism(20)
    .timeout(Duration.ofMinutes(5))
    .forEach(this::processOrder);

if (result.hasError()) {
    log.warn("Failed {} / Succeeded {}", result.failureCount(), result.successCount());
}
```

##### 1.3.6 Full Code Examples

```java
import com.richie.component.concurrency.virtual.BatchProcessor;
import com.richie.component.concurrency.virtual.BatchResult;
import com.richie.component.concurrency.virtual.BatchMappingResult;
import java.time.Duration;
import java.util.List;

public class BatchProcessorExamples {

    // 1) forEach: no return value; no need to care about order
    public BatchResult batchProcessOrders(List<Order> orders) {
        return BatchProcessor.of(orders)
            .parallelism(20)
            .timeout(Duration.ofMinutes(5))
            .forEach(this::processOrder);
    }

    // 2) mapParallel: collect results in input order (failed items correspond to null)
    public BatchMappingResult<Long, String> batchFormatOrders(List<Long> orderIds) {
        return BatchProcessor.of(orderIds)
            .parallelism(20)
            .timeout(Duration.ofMinutes(5))
            .mapParallel(orderService::formatOrder);
    }

    // 3) Check and handle failures
    public void processWithFailureHandling(List<Long> userIds) {
        BatchMappingResult<Long, UserDTO> result = BatchProcessor.of(userIds)
            .parallelism(50)
            .timeout(Duration.ofSeconds(30))
            .mapParallel(userService::findById);

        List<UserDTO> users = result.results();  // Failed items are null
        for (int i = 0; i < users.size(); i++) {
            UserDTO u = users.get(i);
            if (u == null) {
                Long failedId = userIds.get(i);
                Throwable error = result.errors().get(/* index by failedId */ 0);
                log.warn("User {} fetch failed: {}", failedId, error.getMessage());
            }
        }

        if (result.hasError()) {
            log.warn("Batch complete: success={}, failure={}",
                result.successCount(), result.failureCount());
        }
    }

    // 4) Timeout scenario: returns the partially completed results
    public void processWithTimeout(List<Order> orders) {
        BatchMappingResult<Order, Receipt> result = BatchProcessor.of(orders)
            .parallelism(10)
            .timeout(Duration.ofSeconds(10))
            .mapParallel(orderService::ship);

        // Even on timeout, result.results() still contains the completed part
        long completed = result.results().stream().filter(Objects::nonNull).count();
        log.info("Completed {} / {}, timed-out incomplete {}",
            completed, orders.size(), result.failureCount());
    }
}
```

##### 1.3.7 API Quick Reference

| Method | Default | Description |
|--------|---------|-------------|
| `of(items)` | Required | Creates a Builder; input is copied immutably |
| `parallelism(n)` | `max(2, CPU*2)` | Max concurrency, n ≥ 1 |
| `timeout(d)` | 30 minutes | Overall timeout, d must be positive |
| `forEach(consumer)` | - | Processes each item; returns `BatchResult` |
| `mapParallel(mapper)` | - | Maps each item; returns `BatchMappingResult` |

`BatchResult` fields: `successCount()`, `failureCount()`, `errors()`, `hasError()`, `empty()`.

`BatchMappingResult` extra fields: `results()` result list in input order (failed items are `null`), `resultAt(index)` index-based access (throws `IndexOutOfBoundsException` if out of bounds).

---

### Module 2: Rate Limiting and Fault Tolerance Algorithms (`algorithm/`)

Encapsulates the most common "protection for outbound calls" algorithms in distributed and high-concurrency scenarios into stateless utilities and builders, which the caller composes via the Builder pattern as needed. The `algorithm/` sub-package contains four components: `Retryer` (exponential backoff + full jitter + exception filtering + Fallback in one), `RateLimiter` (token bucket with three waiting semantics), `CircuitBreaker` (three-state-machine circuit-breaker protection), and `Debouncer` (one `trigger()` method for all debounce scheduling). None of these components bind to any third-party libraries, with zero runtime reflection; the business side is free to evolve or replace the implementation.

#### 2.1 Retryer

##### 2.1.1 What It Is

`Retryer` is a universal retry utility for distributed-call scenarios. It packages the "exponential backoff + full jitter + exception filtering + Fallback degradation" quartet into one Builder:

- **Exponential backoff**: after the n-th failure, wait `min(initialBackoff × 2^(n-1), maxBackoff)`.
- **Full jitter**: the actual backoff is randomly picked within `[backoff/2, backoff]` (algorithm proposed by AWS architect Marc Brooker), avoiding the thundering-herd effect caused by many clients retrying simultaneously.
- **Precise exception matching**: only retries on the exception types specified by `retryOn(...)`; business exceptions (e.g. 4xx) are immediately propagated.
- **Interrupt-aware**: when the thread is interrupted, immediately throws `RetryExhaustedException` and restores the interrupt flag, without wasting wait time.
- **Fallback degradation**: optional `execute(task, fallback)` returns a fallback value after retries are exhausted.

The implementation uses `Thread.sleep` + `ThreadLocalRandom`; it does not depend on `ScheduledExecutorService`.

##### 2.1.2 Interface Design Semantics

| Method | Caller's Promise | Callee's Guarantee |
|--------|------------------|--------------------|
| `of(initialBackoff)` | "My first backoff is X." | Returns a Builder; initial backoff is X; X must be non-negative. |
| `maxAttempts(n)` | "I can tolerate at most n attempts (including the first)." | n ≥ 1; n = 1 means no retry. |
| `maxBackoff(d)` | "The backoff should not exceed this ceiling." | Adds `min(..., d)` to the backoff formula; d must be non-negative. |
| `jitter(true)` | "This is a multi-client scenario; enable jitter." | Actual backoff is randomized within `[backoff/2, backoff]`. |
| `retryOn(types...)` | "I only want to retry on these exceptions." | Triggers retry only on the specified types (including subclasses); other exceptions are sneaky-thrown immediately. |
| `execute(task)` | "I want the result; throw if retries are exhausted." | Throws `RetryExhaustedException` on retry exhaustion; cause is the last exception. |
| `execute(task, fallback)` | "I still need a return value when retries are exhausted; don't let the upstream crash." | Returns the fallback on retry exhaustion, interruption, or non-retry exception. Catches `Exception` only; **does not swallow `Error`**. |
| `execute(runnable)` | "I have no return value." | Internally wraps with `Executors.callable(runnable, null)` and reuses the same logic. |

##### 2.1.3 Use Cases

1. **HTTP call transient-failure retry**: network jitter, 503 errors; retry 3 times (interval 100ms / 200ms / 400ms).
2. **Database connection acquisition retry**: connection pool transiently full; wait and retry.
3. **Message-queue send retry**: retry on send failure to avoid losing business data.
4. **Cache warm-up failure degradation**: return `null` after 3 failed retries; does not affect the main flow.
5. **Health check calls**: periodically ping a downstream service; return false on failure instead of throwing.

##### 2.1.4 What It Looks Like Without This Component

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
                Thread.sleep(backoff * (1L << (i - 1)));  // No jitter
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ie);
            }
        }
    }
    throw new IllegalStateException();
}
```

Pain points:

- No jitter; 1,000 clients retrying at the same time may still collide.
- No backoff ceiling; long retries may wait hours.
- Retry exception types cannot be configured; 404 will also be retried.
- This boilerplate must be copied in every piece of business code.

##### 2.1.5 Using This Component

```java
String result = Retryer.of(Duration.ofMillis(100))
    .maxAttempts(3)
    .jitter(true)
    .retryOn(IOException.class, TimeoutException.class)
    .execute(() -> httpClient.get(url));
```

##### 2.1.6 Full Code Examples

```java
import com.richie.component.concurrency.algorithm.Retryer;
import com.richie.component.concurrency.algorithm.RetryExhaustedException;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RetryerExamples {

    // 1) Basic usage: exponential backoff + full jitter + exception filtering
    public String callExternalApi() {
        return Retryer.of(Duration.ofMillis(100))
            .maxAttempts(3)
            .maxBackoff(Duration.ofSeconds(2))
            .jitter(true)
            .retryOn(IOException.class, TimeoutException.class)
            .execute(() -> httpClient.get("/api/data"));
    }

    // 2) With Fallback; return a fallback value on retry exhaustion
    public String callWithFallback() {
        return Retryer.of(Duration.ofMillis(200))
            .maxAttempts(3)
            .execute(() -> httpClient.get("/api/optional"), "default-value");
    }

    // 3) No-return-value variant
    public void publishEvent() {
        Retryer.of(Duration.ofMillis(500))
            .maxAttempts(5)
            .execute(() -> messageQueue.send(event));
    }

    // 4) Explicitly handle retry exhaustion
    public String callWithExplicitHandling() {
        try {
            return Retryer.of(Duration.ofMillis(50))
                .maxAttempts(3)
                .execute(() -> remoteCall());
        } catch (RetryExhaustedException e) {
            Throwable cause = e.getCause();
            log.warn("Retries exhausted, last exception: {}", cause.getMessage(), e);
            throw new BusinessException("Remote call failed", e);
        }
    }

    // 5) Business exceptions are immediately propagated (not retried)
    public String callWithBusinessExceptionFilter() {
        return Retryer.of(Duration.ofMillis(100))
            .maxAttempts(5)
            .retryOn(IOException.class)  // Only retry on IO exceptions
            .execute(() -> httpClient.get("/api/data"));
        // If IllegalArgumentException (business exception) is thrown, it propagates immediately, no retry
    }
}
```

##### 2.1.7 API Quick Reference

| Method | Default | Description |
|--------|---------|-------------|
| `of(initialBackoff)` | Required | Creates a Builder; initial backoff must be non-negative |
| `maxAttempts(n)` | 3 | Max attempts (including the first), n ≥ 1 |
| `maxBackoff(d)` | 30s | Backoff ceiling |
| `jitter(bool)` | false | Full jitter |
| `retryOn(types...)` | `{Exception.class}` | Exception types that trigger retry; at least 1 |
| `execute(Callable)` | - | Executes the task; throws `RetryExhaustedException` on retry exhaustion |
| `execute(Callable, fallback)` | - | Executes the task; returns fallback on failure (does not swallow `Error`) |
| `execute(Runnable)` | - | No-return-value variant |

`RetryExhaustedException` is a subclass of `RuntimeException`; `getCause()` returns the last original exception.

---

#### 2.2 RateLimiter (Token Bucket Rate Limiter)

##### 2.2.1 What It Is

`RateLimiter` implements rate limiting on outbound calls using the classic token-bucket algorithm. The bucket is refilled with tokens at a fixed rate; each call consumes 1 (or N) tokens; when the bucket is empty, it returns `false` or blocks according to the method semantics.

Core design:

- **Three waiting semantics**: `tryAcquire()` is non-blocking, `tryAcquire(Duration)` is time-limited blocking, `acquire()` is unbounded blocking — caller picks as needed.
- **Virtual-thread scheduler**: internally uses `Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())` to refill tokens periodically; very low resource usage.
- **Multi-token granularity**: `tryAcquire(n)` / `acquire(n)` support consuming N tokens at once, ideal for bulk-import scenarios.
- **Lifecycle management**: `close()` idempotently shuts down the scheduler; after shutdown, `tryAcquire` returns false and `acquire` throws `IllegalStateException`.

##### 2.2.2 Interface Design Semantics

| Method | Caller's Promise | Callee's Guarantee |
|--------|------------------|--------------------|
| `ofTokensPerSecond(n)` | "I allow at most n calls per second." | Bucket capacity = n; refills n tokens every 1 second. |
| `ofTokensPerDuration(n, window)` | "I allow at most n calls in any window of time." | Bucket capacity = n; refills once per window. |
| `ofTryAcquireTimeout(n, timeout)` | (Deprecated) | Since 2.2.0 the `try*` prefix strictly means non-blocking. This method is kept for compatibility only. Use `ofTokensPerDuration(n, window)` + `tryAcquire(Duration)` instead. |
| `builder()` | "I want fine-grained configuration." | Returns a Builder. |
| `tryAcquire()` | "Give me a token now; skip if none." | Returns false immediately if the bucket is empty; **does not block**. |
| `tryAcquire(n)` | Same as above but takes n tokens at once. | n ≥ 1; returns false immediately if fewer than n tokens are available. |
| `tryAcquire(Duration)` | "I can wait, but no more than timeout." | Blocks waiting for 1 token within timeout; returns false on timeout; restores interrupt flag and returns false when interrupted. |
| `tryAcquire(n, Duration)` | Same as above but takes n tokens at once. | Same as above. |
| `acquire()` | "I must get a token; I can wait however long it takes." | Blocks until 1 token is acquired; throws `InterruptedException` on interrupt. |
| `acquire(n)` | Same as above but takes n tokens at once. | Blocks until n tokens are acquired. |
| `acquireUninterruptibly(n)` | "I don't respond to interrupts." | Does not throw `InterruptedException`, but preserves the interrupt flag. |
| `availablePermits()` | "Let me see how many tokens are in the bucket." | Returns an instantaneous snapshot (**approximate**, not atomically consistent). |
| `close()` | "I'm done with it." | Shuts down the scheduler thread; idempotent. |

##### 2.2.3 Use Cases

1. **Global rate limiting on external APIs**: call a third-party payment interface; cap at 100 times per second.
2. **Database write rate limiting**: batch writes limited to 500 per second to avoid overloading the database.
3. **Scheduled-task rate limiting**: trigger at most 10 times per minute; drop the rest.
4. **SLA-strict time-limited waiting**: if no token can be obtained within 500ms on a critical path, fall back to fallback logic.
5. **Burst-traffic protection**: allow 1,000 calls within 60 seconds to handle short bursts.

##### 2.2.4 What It Looks Like Without This Component

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

Pain points:

- Bucket capacity and refill rate are hard-coded.
- No multi-token granularity (taking 5 tokens requires looping 5 times).
- No time-limited-blocking variant.
- Thread factory cannot be swapped for virtual threads.
- Shutdown logic is easy to miss.

##### 2.2.5 Using This Component

```java
RateLimiter limiter = RateLimiter.ofTokensPerSecond(100);
if (limiter.tryAcquire()) {
    callExternalService();
}
```

##### 2.2.6 Full Code Examples

```java
import com.richie.component.concurrency.algorithm.RateLimiter;
import java.time.Duration;

public class RateLimiterExamples {

    // 1) Non-blocking: 100 calls per second
    public void callExternalNonBlocking(RateLimiter limiter) {
        if (limiter.tryAcquire()) {
            externalService.call();
        } else {
            log.warn("Rate-limited; skipping this call");
        }
    }

    // 2) Time-limited wait: give up if no token within 500ms
    public boolean callExternalWithTimeout(RateLimiter limiter) {
        if (limiter.tryAcquire(Duration.ofMillis(500))) {
            externalService.call();
            return true;
        } else {
            return false;  // SLA timeout
        }
    }

    // 3) Unbounded blocking: must wait
    public void callExternalBlocking(RateLimiter limiter) throws InterruptedException {
        limiter.acquire();
        externalService.call();
    }

    // 4) Take multiple tokens at once: batch call
    public void callBatch(RateLimiter limiter, List<Request> batch) throws InterruptedException {
        limiter.acquire(batch.size());  // Take batch.size() tokens at once
        externalService.batchCall(batch);
    }

    // 5) Custom period: at most 1,000 calls per 60 seconds
    public RateLimiter createQuotaLimiter() {
        return RateLimiter.ofTokensPerDuration(1000, Duration.ofMinutes(1));
    }

    // 6) Builder pattern custom
    public RateLimiter createCustomLimiter() {
        return RateLimiter.builder()
            .permits(200)
            .period(Duration.ofSeconds(1))
            .build();
    }

    // 7) Resource management: release on Spring Bean shutdown
    // @PreDestroy
    public void shutdown(RateLimiter limiter) {
        limiter.close();  // Shut down the virtual-thread scheduler
    }

    // 8) Monitoring: get current available tokens
    public int monitor(RateLimiter limiter) {
        return limiter.availablePermits();  // Approximate; for monitoring only
    }
}
```

##### 2.2.7 API Quick Reference

| Method | Description |
|--------|-------------|
| `ofTokensPerSecond(n)` | Refill n tokens per second (n ≥ 1) |
| `ofTokensPerDuration(n, window)` | Refill n tokens per window |
| `ofTryAcquireTimeout(n, timeout)` | Deprecated; use `ofTokensPerDuration` + `tryAcquire(Duration)` |
| `builder()` | Custom builder |
| `tryAcquire()` | Non-blocking, take 1 token |
| `tryAcquire(n)` | Non-blocking, take n tokens |
| `tryAcquire(Duration)` | Time-limited blocking, take 1 token |
| `tryAcquire(n, Duration)` | Time-limited blocking, take n tokens |
| `acquire()` | Unbounded blocking, take 1 token |
| `acquire(n)` | Unbounded blocking, take n tokens |
| `acquireUninterruptibly(n)` | Uninterruptible blocking |
| `availablePermits()` | Current available tokens (approximate) |
| `close()` | Shut down the limiter (idempotent) |
| `Builder.period(d)` | Custom period (d must be positive) |
| `Builder.tryAcquireTimeoutEnabled(bool)` | Deprecated; no longer affects `tryAcquire` behavior |

---

#### 2.3 CircuitBreaker

##### 2.3.1 What It Is

`CircuitBreaker` is a three-state-machine circuit breaker inspired by Hystrix / Resilience4j design, used to protect downstream services from being overwhelmed during sustained failures.

Three states:

- **CLOSED**: normal state; requests pass through; success/failure statistics are accumulated.
- **OPEN**: fault state; requests are immediately rejected (throws `CircuitBreakerOpenException`), without calling the downstream.
- **HALF_OPEN**: probe state; entered after waiting `openDuration`; the first probe call is let through — success transitions back to CLOSED, failure returns to OPEN.

Three trigger modes:

- **Failure-rate mode (default)**: monitors the most recent N calls; trips when the failure rate exceeds the threshold. Requires at least 10 calls accumulated in the window before judging, to avoid cold-start false-trips.
- **Absolute-count mode (`ofCount`)**: trips when N failures accumulate in the window, without calculating failure rate.
- **Time-window mode (`ofRate`)**: trips when the failure rate within the most recent `windowDuration` exceeds the threshold (`TIME_BASED` sliding window).

The HALF_OPEN state uses **single-probe** semantics: an `AtomicBoolean halfOpenGate` `compareAndSet` ensures that only one probe call is let through at any moment, while other concurrent threads are rejected as if OPEN.

##### 2.3.2 Interface Design Semantics

| Method | Caller's Promise | Callee's Guarantee |
|--------|------------------|--------------------|
| `ofDefaults()` | "Default parameters are fine." | Failure rate 50% / window 100 / OPEN duration 10s. |
| `of(failurePercent, openDuration)` | "Use failure-rate mode with a 100-call window." | Same as above; parameterized failure rate and OPEN duration. |
| `ofCount(failureCount, openDuration)` | "Trip on absolute failure count." | Trips on N failures; no minimum-sample requirement. |
| `ofRate(failurePercent, window, openDuration)` | "Use the time-window mode." | Failure rate within a time window. |
| `builder()` | "I want fine-grained configuration." | Returns a Builder. |
| `execute(task)` | "Run the task; tell me if the circuit is open." | Throws `CircuitBreakerOpenException` in OPEN state; task exceptions are also propagated. |
| `execute(task, fallback)` | "No failure of any kind should crash the upstream." | Both circuit-open and exceptions return the fallback; no exception thrown. |
| `executeOrThrow(task)` | "Make the semantics explicit: I will throw." | Same as `execute(task)`, but the method name reinforces the "may throw" semantics. |
| `state()` | "I need to monitor the circuit-breaker state." | Returns the current `CLOSED` / `OPEN` / `HALF_OPEN`. |
| `reset()` | "Force the circuit breaker back to CLOSED." | Clears statistics; idempotent. |
| `forceOpen()` | "I want to test the OPEN state." | Forces OPEN; does not affect `openDuration` timing. |

##### 2.3.3 Use Cases

1. **External API call protection**: trip when failure rate of payment or SMS interfaces exceeds 50%, to avoid dragging down the whole system.
2. **Downstream service degradation**: trip when the user service fails; return cached fallback data.
3. **Database fault protection**: trip when primary-database failure rate rises; the application layer switches to read-only database.
4. **Cold-start protection for new services**: use absolute-count mode (e.g. trip on 5 failures) to avoid cold-start misjudgment.
5. **Multi-tenant isolation**: one circuit breaker per tenant; a single-tenant failure does not affect other tenants.

##### 2.3.4 What It Looks Like Without This Component

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
    // ... also need to implement onSuccess / onFailure / state transitions / single-probe gating
}
```

Pain points:

- The single-probe gate's `compareAndSet` is easy to forget.
- The HALF_OPEN transition double-checked locking is easy to get wrong.
- Failure-rate statistics require a sliding window.
- Cold-start protection (minimum sample count) is easy to omit.

##### 2.3.5 Using This Component

```java
CircuitBreaker breaker = CircuitBreaker.ofDefaults();
String result = breaker.execute(() -> callRemoteService(), "default");
```

##### 2.3.6 Full Code Examples

```java
import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.CircuitBreakerOpenException;
import java.time.Duration;

public class CircuitBreakerExamples {

    // 1) Default config + with Fallback
    public String callWithDefault(String url) {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        return breaker.execute(() -> httpClient.get(url), "fallback");
    }

    // 2) Custom failure rate + window
    public CircuitBreaker createAggressiveBreaker() {
        return CircuitBreaker.builder()
            .failurePercent(60)
            .windowSize(200)
            .openDuration(Duration.ofSeconds(30))
            .build();
    }

    // 3) Absolute-count mode (good for cold-start scenarios)
    public CircuitBreaker createCountBasedBreaker() {
        return CircuitBreaker.ofCount(5, Duration.ofSeconds(15));
    }

    // 4) Time-window mode
    public CircuitBreaker createTimeWindowBreaker() {
        return CircuitBreaker.ofRate(50,
            Duration.ofSeconds(10),
            Duration.ofSeconds(5));
    }

    // 5) Explicitly handle circuit-breaker exception
    public String callWithExplicitHandling(String url) {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        try {
            return breaker.executeOrThrow(() -> httpClient.get(url));
        } catch (CircuitBreakerOpenException e) {
            log.warn("Circuit open; skipping remote call");
            return getCachedValue(url);
        } catch (Exception e) {
            log.error("Remote call failed", e);
            throw new BusinessException("Call failed", e);
        }
    }

    // 6) Monitor circuit-breaker state
    public void monitorState(CircuitBreaker breaker) {
        CircuitBreaker.State state = breaker.state();
        if (state == CircuitBreaker.State.OPEN) {
            metrics.gauge("circuit_breaker.state", 1);  // Expose to Prometheus
        } else if (state == CircuitBreaker.State.HALF_OPEN) {
            metrics.gauge("circuit_breaker.state", 0.5);
        } else {
            metrics.gauge("circuit_breaker.state", 0);
        }
    }

    // 7) Test scenario: force OPEN
    public void simulateOpen(CircuitBreaker breaker) {
        breaker.forceOpen();
        // Test fallback logic
        String result = breaker.execute(() -> callRemote(), "fallback");
        assert "fallback".equals(result);
        breaker.reset();  // Restore to CLOSED
    }
}
```

##### 2.3.7 API Quick Reference

| Method | Description |
|--------|-------------|
| `ofDefaults()` | Default config (50% / 100 window / 10s) |
| `of(failurePercent, openDuration)` | Failure-rate mode, 100-call window |
| `ofCount(failureCount, openDuration)` | Absolute-count mode |
| `ofRate(failurePercent, window, openDuration)` | Time-window mode |
| `builder()` | Fine-grained Builder |
| `execute(task)` | Run the task; throws `CircuitBreakerOpenException` if OPEN |
| `execute(task, fallback)` | Run the task; returns fallback on failure |
| `executeOrThrow(task)` | Same as `execute`, but the method name emphasizes "may throw" |
| `state()` | Current state (`CLOSED` / `OPEN` / `HALF_OPEN`) |
| `reset()` | Force reset to CLOSED |
| `forceOpen()` | Force to OPEN (for testing) |
| `Builder.failurePercent(p)` | Failure-rate threshold (1-100) |
| `Builder.failureCount(n)` | Absolute failure count (n ≥ 1) |
| `Builder.windowSize(n)` | Counting window size (n ≥ 10; default 100) |
| `Builder.windowDuration(d)` | Time-window length (only `TIME_BASED`) |
| `Builder.openDuration(d)` | OPEN duration |
| `Builder.slidingWindowType(type)` | Sliding-window type (`COUNT_BASED` / `TIME_BASED`) |

`CircuitBreaker.State` enum: `CLOSED` / `OPEN` / `HALF_OPEN`.

---

#### 2.4 Debouncer

##### 2.4.1 What It Is

`Debouncer` wraps the "reset timer on repeated trigger" debounce pattern into a single `trigger()` method. It uses a `ScheduledExecutorService` (virtual thread factory) under the hood; every `trigger()` cancels the old timer and starts a new one; if no new `trigger()` arrives within `delay`, the pending action is executed.

Design intent: the caller does not need to keep a `ScheduledFuture` reference, does not need to try/catch `InterruptedException`, and does not need to remember the two-step `cancel + schedule` pattern. A single `trigger()` call site completes all the logic.

##### 2.4.2 Interface Design Semantics

| Method | Caller's Promise | Callee's Guarantee |
|--------|------------------|--------------------|
| `of(delay, action)` | "I have a debounced action with delay d." | Creates a debouncer; delay must be positive; scheduler uses virtual threads. |
| `trigger()` | "I triggered again; reset the countdown." | Cancels the currently pending task and creates a new one; repeated `trigger` within delay keeps resetting; no effect after the debouncer is closed. |
| `flush()` | "Execute the pending action right now; no more waiting." | Cancels the timer and immediately runs `action.run()`; no effect if nothing is pending; swallows action exceptions. |
| `cancel()` | "Cancel the pending action, but keep the debouncer." | Cancels the timer; does not affect subsequent `trigger()` calls. |
| `isPending()` | "I want to know whether there is a pending operation." | Returns true if there is a pending and unfinished operation; returns false if none or if closed. |
| `close()` | "I'm done with it; release the scheduler." | Shuts down the scheduler thread; idempotent. |

##### 2.4.3 Use Cases

1. **Search-box input debounce**: avoid hitting the backend on every keystroke; trigger the search only after 300ms of inactivity.
2. **Form auto-save**: keep triggering "save" on every edit; actually save only after 1s of inactivity.
3. **Window resize events**: resize events fire at high frequency; debounce so layout runs only after resizing stops.
4. **Button double-submit prevention**: even if the user clicks repeatedly, the actual submit fires only 500ms after the last click.
5. **Editor content sync**: editor changes fire frequently; debounce 2 seconds before syncing to the server.

##### 2.4.4 What It Looks Like Without This Component

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

Pain points:

- Every implementation needs the `cancel + schedule` template.
- Scheduler shutdown and cancel logic are easy to miss.
- Multi-thread concurrent triggering requires synchronization.
- `flush()` (immediate execution) must be implemented separately.

##### 2.4.5 Using This Component

```java
Debouncer debouncer = Debouncer.of(Duration.ofMillis(300), this::save);
onChange(() -> debouncer.trigger());
debouncer.close();
```

##### 2.4.6 Full Code Examples

```java
import com.richie.component.concurrency.algorithm.Debouncer;
import java.time.Duration;

public class DebouncerExamples {

    // 1) Search-box input debounce
    private final Debouncer searchDebouncer = Debouncer.of(
        Duration.ofMillis(300),
        () -> {
            String keyword = searchBox.getValue();
            searchService.search(keyword);
        }
    );

    public void onUserInput(String input) {
        searchBox.setValue(input);
        searchDebouncer.trigger();  // Triggered on every keystroke; searches 300ms after typing stops
    }

    // 2) Form auto-save
    private final Debouncer saveDebouncer = Debouncer.of(
        Duration.ofSeconds(1),
        () -> formService.save(currentForm)
    );

    public void onFormChange() {
        saveDebouncer.trigger();  // Reset on every edit; saves 1s after editing stops
    }

    // 3) Execute the pending action immediately (flush)
    public void onSubmitClick() {
        saveDebouncer.flush();  // User clicks submit; save immediately, don't wait 1s
    }

    // 4) Cancel the pending action (cancel)
    public void onResetClick() {
        saveDebouncer.cancel();  // User clicks reset; cancel the pending save
    }

    // 5) Check state
    public void onSaveButtonHover() {
        if (saveDebouncer.isPending()) {
            statusBar.show("Save pending...");
        }
    }

    // 6) Resource management
    // @PreDestroy
    public void cleanup() {
        searchDebouncer.close();
        saveDebouncer.close();
    }
}
```

##### 2.4.7 API Quick Reference

| Method | Description |
|--------|-------------|
| `of(delay, action)` | Create a debouncer (delay must be positive) |
| `trigger()` | Trigger the debounce (repeated triggers reset the timer) |
| `flush()` | Execute the pending action immediately |
| `cancel()` | Cancel the pending action (does not affect later triggers) |
| `isPending()` | Whether there is a pending operation |
| `close()` | Shut down the debouncer (idempotent) |

---

### Module 3: Dynamic Thread Pool (`threadpool/`)

Extends the standard `ThreadPoolExecutor` with "event-driven resize + rejection counting + one-stop runtime snapshot", allowing thread pool parameters to be adjusted at runtime without restarting the application. The `threadpool/` sub-package contains three components: `DynamicExecutor` (a tunable thread pool extending `ThreadPoolExecutor`), `PoolResizeEvent` (a hot-update event that updates only non-null fields), and `PoolStatus` (an immutable runtime snapshot of 9 core metrics). The multi-pool scenario is configuration-driven via the `platform.concurrency.thread-pools` Map, and injected by name with `@Resource(name = "<poolName>")`, without writing any `@Bean` methods on the business side.

### 3.0 Implementation Principles

This section deconstructs the dynamic thread pool's runtime mechanism at the source-code level, helping readers judge the extension boundaries and room for modification. Section 3.1 describes the API for callers; this section describes the design for implementers.

#### 3.0.1 Overall Architecture

The mechanism is split into three layers: the core layer is responsible for hot-update capability itself; the auto-configuration layer is responsible for registering multiple pools into the Spring container; the config-center integration layer is responsible for listening to external configuration changes and triggering the core layer. The three layers have strictly separated responsibilities; the core layer does not depend on the Spring container at all, so unit tests can run without Spring.

```
┌──────────────────────────────────────────────────────────────────┐
│  Layer 1: Core (threadpool/)                                      │
│                                                                  │
│   DynamicExecutor                                                │
│     ├─ extends ThreadPoolExecutor                                │
│     ├─ onResize(PoolResizeEvent)   Event-driven resize          │
│     └─ snapshot() → PoolStatus     One-stop runtime snapshot     │
│                                                                  │
│   PoolResizeEvent  (record, immutable event object)              │
│     └─ corePoolSize / maximumPoolSize /                          │
│        keepAliveTime / rejectedHandler   all optional            │
│                                                                  │
│   PoolStatus  (record, immutable snapshot)                       │
│     └─ poolSize / activeCount / queueSize / ... / rejectedCount  │
└──────────────────────────────────────────────────────────────────┘
                              ▲
                              │  resize
┌──────────────────────────────────────────────────────────────────┐
│  Layer 2: Auto-configuration (config/)                           │
│                                                                  │
│   AlgorithmAutoConfiguration                                     │
│     ├─ Bind ConcurrencyProperties.thread-pools                   │
│     ├─ Iterate Map<String, PoolProperties> to register beans     │
│     └─ Bean name = Map key, supports @Qualifier / @Resource / Map injection │
└──────────────────────────────────────────────────────────────────┘
                              ▲
┌──────────────────────────────────────────────────────────────────┐
│  Layer 3: Config Center Integration (config/ThreadPoolConfigRefresher.java) │
│                                                                  │
│   @ConditionalOnClass(name = "org.springframework.cloud.context" │
│                        .refresh.event.EnvironmentChangeEvent)    │
│                                                                  │
│   ThreadPoolConfigRefresher                                      │
│     ├─ @EventListener(EnvironmentChangeEvent.class)              │
│     ├─ Filter keys with prefix platform.concurrency.thread-pools.*│
│     ├─ Binder rebinds the latest PoolProperties Map              │
│     └─ Calls DynamicExecutor.onResize(...) for each changed pool │
└──────────────────────────────────────────────────────────────────┘
```

Key points of each layer:

- **Core layer has zero Spring dependency**: `DynamicExecutor` only extends `java.util.concurrent.ThreadPoolExecutor`; it can be used in any Java process.
- **Auto-configuration layer only does "config → Bean" translation**: at startup, `AlgorithmAutoConfiguration` iterates the `platform.concurrency.thread-pools` Map, registering each key as a `DynamicExecutor` singleton Bean; bean name = key, making `@Qualifier` references easy.
- **Integration layer activates on demand**: it is only effective when Spring Cloud's `EnvironmentChangeEvent` class is on the classpath; if that dependency is missing, this Bean will not appear in the project. It works with any config center that can trigger this event — Nacos, Apollo, Config, ZK, Consul, etc.

#### 3.0.2 Tuning Workflow

Taking Nacos push as an example, the complete chain is as follows:

```
Nacos console changes config
        │
        ▼
Nacos Client detects change via long polling
        │
        ▼
Spring Cloud refreshes Environment
        │
        ▼
Publishes EnvironmentChangeEvent
        │
        ▼
ThreadPoolConfigRefresher.onEnvironmentChange()
        │
        ▼
Filter keys: platform.concurrency.thread-pools.* hits
        │
        ▼
Binder rebinds ConcurrencyProperties.thread-pools Map
        │
        ▼
Diffs against local old PoolProperties
        │
        ▼
Builds PoolResizeEvent for changed pools only (non-null fields filled, null means "do not adjust")
        │
        ▼
DynamicExecutor.onResize(event)
        │
        ▼
Internally calls setCorePoolSize / setMaximumPoolSize /
        setKeepAliveTime / setRejectedExecutionHandler by field
```

Key source structure of `ThreadPoolConfigRefresher` (located in `com.richie.component.concurrency.config.ThreadPoolConfigRefresher`):

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

**Key design trade-offs**:

- **Diff is done in the auto-configuration layer**: `ThreadPoolConfigRefresher` caches the most recent `PoolProperties` snapshot and only issues `onResize` when fields actually change, avoiding pointless triggers.
- **Null fields keep their original values**: All `PoolProperties` fields are wrapper types (`Integer` / `Duration`); leaving them out of the config means "do not touch" — they will not be force-overwritten with defaults.
- **`queueCapacity` and `threadNamePrefix` cannot be dynamically changed**:
  - **`queueCapacity`** adjustment requires creating a new `LinkedBlockingQueue` and "moving" tasks from the old queue, which the JDK `ThreadPoolExecutor` does not expose a public API for at runtime. This component chooses not to support it in `onResize`; configure it at startup via `platform.concurrency.thread-pools.<poolName>.queue-capacity`. If you really need to adjust, please restart.
  - **`threadNamePrefix`** only affects "Workers newly created from then on"; existing thread names cannot be retroactively changed. If thread names appear inconsistent immediately after a tuning change, that is the expected behavior.
  - If the above two items appear in a config change, this component will log a WARN and silently ignore them, without throwing an exception to interrupt the whole batch of refreshes.

#### 3.0.3 Rejection Counting Principle

`ThreadPoolExecutor.rejectedExecution(Runnable, ThreadPoolExecutor)` is a package-private method and cannot be overridden by subclasses to count rejections. `DynamicExecutor` solves this with a "decorator + delegation" pattern:

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
        // super has already stored userHandler in the parent's field;
        // we then "steal" it and wrap a layer
        this.countingHandler = new CountingHandler(userHandler);
        // Re-assign to the parent, replacing the original handler
        super.setRejectedExecutionHandler(countingHandler);
    }

    @Override
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        // Business code calling getRejectedExecutionHandler() still gets userHandler
        return countingHandler.getDelegate();
    }

    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        // onResize switches policy here, atomically replacing the delegate
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

Key points:

- **TPE internally calls `CountingHandler.rejectedExecution`** during `execute()`: it first calls `incrementAndGet()` and then delegates to the user's handler; both counting and the user's original behavior are preserved.
- **`getRejectedExecutionHandler()` is overridden to return the user's original handler**: monitoring code, third-party tools, and business-side debugging all still get the originally `new`'d object, with no awareness of the wrapper.
- **`onResize` switches the rejection policy via `setRejectedExecutionHandler`**: the overridden setter does not directly modify the parent's field, but instead calls `countingHandler.setDelegate(newHandler)` to atomically replace the delegate, keeping the TPE's internal handler reference consistent with the new delegate.

> Note: the `CountingHandler` above is a simplified example used to illustrate the principle; in the actual implementation, the counter is an instance field of `DynamicExecutor` rather than a static variable, so each pool counts independently.

#### 3.0.4 Positioning Difference from dynamic-tp

In one sentence: **this component is a "just enough" JDK 25 + Spring Boot native implementation; dynamic-tp is a "feature-complete" open-source platform solution**.

For a complete difference comparison, decision tree, and repo URL see [FAQ Q1](#q1-why-not-keep-using-dynamic-tp). Below is a quick-look version of the positioning difference:

| Dimension | This Component (`DynamicExecutor`) | dynamic-tp |
|-----------|-----------------------------------|------------|
| Design Goal | Lightweight alternative; covers 80% of platform thread pool scenarios | Platform-grade thread pool management framework; covers all advanced capabilities |
| Dependency Footprint | 0 extra dependencies (only comes with this component) | Need to introduce `dynamic-tp-spring-boot-starter` and its transitive dependencies |
| Auto-Configuration | Zero config (just include this component) | Need to introduce the starter and configure the `dynamic-tp` namespace |
| Core Capabilities | resize + rejection counting + snapshot | resize + rejection counting + snapshot + alerting + web admin + task wrapping + persistence |
| Spring Cloud Adaptation | Indirectly adapts to all config centers via `EnvironmentChangeEvent` | Built-in dedicated adapters for Nacos / Apollo / Etcd, etc. |
| Learning Curve | Read this README and you're up and running | Need to be familiar with its config model and extension points |
| Suitable Projects | Small-to-medium projects where simple tuning + basic monitoring is enough | Medium-to-large projects needing alerting / web admin / task wrapping / multi-env management |

**When to choose which**:

1. **Choose this component**: multiple pools + tuning + rejection counting + basic snapshot + already using Spring Cloud config center integration (no need to write extra listener code).
2. **Choose this component**: don't want to add yet another starter and its transitive dependencies just for thread pool management.
3. **Choose this component**: thread pool count is controllable (generally < 10), manual tuning + monitoring alerting is enough.
4. **Choose dynamic-tp**: need multi-channel alerting (DingTalk / WeCom / Feishu / email).
5. **Choose dynamic-tp**: need a visual web admin to manage thread pool parameters and runtime metrics.
6. **Choose dynamic-tp**: need task wrapping like MDC / TransmittableThreadLocal, or thread pool data persistence.
7. **Both together**: core pools use this component (lightweight, zero deps); pools with special requirements (MDC propagation, alerting persistence) hook into dynamic-tp separately (mix at the pool level).

dynamic-tp repo: <https://github.com/dromara/dynamic-tp>; detailed capability matrix and usage docs are in that repo's README.

#### 3.1 DynamicExecutor

##### 3.1.1 What It Is

`DynamicExecutor` extends the standard `ThreadPoolExecutor` and exposes two sets of capabilities that standard TPE lacks:

1. **Event-driven resize**: `onResize(PoolResizeEvent)` can adjust `corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler` at runtime without restarting the application. The event source can be Nacos, Etcd, Admin API, a timer, or JMX — this component does not bind to any specific event source.
2. **Rejection counting + status snapshot**: at construction time, the user-supplied `RejectedExecutionHandler` is automatically wrapped into an internal `CountingHandler`, fully transparent to `getRejectedExecutionHandler()` callers. `snapshot()` returns an immutable snapshot of 9 monitoring metrics in one call.

The underlying TPE behavior is exactly the same as JDK `ThreadPoolExecutor` (`execute` / `submit` / `shutdown` / `shutdownNow` / `awaitTermination`, etc., all unchanged); it can serve as a drop-in replacement for TPE.

##### 3.1.2 Interface Design Semantics

| Method | Caller's Promise | Callee's Guarantee |
|--------|------------------|--------------------|
| `DynamicExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue)` | "I want to create a tunable thread pool." | Constructor params match standard TPE; the default handler is `AbortPolicy`, which is automatically wrapped as `CountingHandler` to support rejection counting. |
| `DynamicExecutor(..., threadFactory, handler)` | "I want a custom thread factory and rejection policy." | Full-arg constructor; the handler is also transparently wrapped in `CountingHandler`; `getRejectedExecutionHandler()` returns the original user-supplied handler. |
| `onResize(PoolResizeEvent)` | "I want to hot-update the thread pool parameters." | Updates only the non-null fields in the event (`corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler`); null fields mean "do not adjust". A null event is ignored with a WARN log. |
| `snapshot()` | "I want to take a snapshot for monitoring." | Returns an immutable `PoolStatus` (with 9 fields including `poolSize` / `activeCount` / `queueSize` / `queueRemainingCapacity` / `completedTaskCount` / `totalTaskCount` / `rejectedCount`, etc.). |
| `getRejectionCount()` | "I want the total rejection count." | Returns the cumulative count of tasks rejected since the thread pool was created (long; cross-restart persistence is your responsibility). |
| `resetRejectionCount()` | "I want to reset the rejection counter." | Zeros out the internal counter (stays in sync with `snapshot()`'s `rejectedCount`). |
| `PoolResizeEvent.builder()` | "I want to build a hot-update event." | All fields are optional (null means "do not adjust"); the rejection policy is optional; `queueCapacity` is not included (see design decisions below). |
| `PoolStatus.builder()` | "I want to manually construct a status snapshot." | 9 fields assignable independently, for testing or third-party monitoring integration. |

##### 3.1.3 Use Cases

1. **Config-center-driven tuning**: when a Nacos/Etcd config-change listener receives parameter changes, build a `PoolResizeEvent` and call `onResize` — "tune without stopping".
2. **Multi-tenant / multi-business-line isolation**: one `DynamicExecutor` instance per business scenario (order processing, notification push, report export, each in its own pool), injected by name via `@Qualifier`.
3. **Rejection counting and alerting**: trigger an alert when `snapshot().getRejectedCount()` keeps climbing; expose it to Prometheus along with `queueSize` / `activeCount`.
4. **Burst-traffic protection**: when an upstream API fault causes task backlog, switch `rejectedHandler` from `AbortPolicy` to `CallerRunsPolicy` at runtime, using the caller's thread to absorb some of the pressure.
5. **Production fault-injection testing**: use `forceResize` to directly scale the thread pool up/down and validate the downstream service's behavior at various concurrency levels.

##### 3.1.4 What It Looks Like Without This Component

Adjusting `ThreadPoolExecutor` parameters requires holding the original reference, and there is no way to batch-tune at runtime without restarting:

```java
// 1) Must restart after modifying
@Bean
public ThreadPoolExecutor orderExecutor() {
    return new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000));
}

// 2) Want "dynamic adjustment" — you need to reinvent the wheel: listen to config center + deserialize + call setter
@NacosConfigListener(dataId = "order-executor.yml")
public void onConfigChange(String newConfig) {
    PoolConfig cfg = parse(newConfig);
    orderExecutor.setCorePoolSize(cfg.getCore());
    orderExecutor.setMaximumPoolSize(cfg.getMax());
    // ...also need to handle that setRejectedExecutionHandler can't count
    // ...also need to write snapshot serialization logic
}
```

Pain points:

- Tuning logic is scattered across config listeners; no unified event API.
- After calling `setRejectedExecutionHandler`, the user handler is overwritten, so monitoring/alerting cannot count "how many times was this rejected".
- `ThreadPoolExecutor` has no one-stop snapshot; you must call `getPoolSize` / `getActiveCount` / `getQueue().size()` separately, with no cross-thread consistency guarantee.
- Multi-pool config must write `@Bean` methods one by one; cannot be driven by `Map<String, PoolProperties>` config.

##### 3.1.5 Using This Component

```java
DynamicExecutor orderExecutor = new DynamicExecutor(
    4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000)
);

// One line after config-center changes
orderExecutor.onResize(PoolResizeEvent.builder()
    .corePoolSize(16)
    .maximumPoolSize(32)
    .build());

// Monitoring reads the snapshot directly
PoolStatus status = orderExecutor.snapshot();
```

##### 3.1.6 Full Code Examples

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

  // ========== 1) Basic creation and usage ==========
  private final DynamicExecutor orderExecutor = new DynamicExecutor(
          4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000));

  public void submitOrderTask(Runnable task) {
    orderExecutor.execute(task);
  }

  // ========== 2) Runtime onResize hot update ==========
  public void onNacosConfigChange(int newCore, int newMax, Duration newKeepAlive) {
    // Only pass the fields that need changing; others keep their original values
    orderExecutor.onResize(PoolResizeEvent.builder()
            .corePoolSize(newCore)
            .maximumPoolSize(newMax)
            .keepAliveTime(newKeepAlive)
            .build());
  }

  // ========== 3) PoolStatus snapshot monitoring ==========
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

  // ========== 4) CountingHandler rejection counting ==========
  public void onRejectionThresholdExceeded() {
    if (orderExecutor.snapshot().getRejectedCount() > 100) {
      // Rejections exceeded the threshold; temporarily switch policy to CallerRunsPolicy
      orderExecutor.onResize(PoolResizeEvent.builder()
              .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
              .build());
    }
  }

  public long getRejectionCount() {
    return orderExecutor.getRejectionCount();  // Cumulative
  }

  public void resetDailyRejectionCount() {
    orderExecutor.resetRejectionCount();  // Reset
  }

  // ========== 5) Spring multi-pool injection ==========

  // Style 1: Get a specific thread pool by name (@Resource + name)
  // Config: platform.concurrency.thread-pools.order-executor.*
  @jakarta.annotation.Resource(name = "order-executor")
  private DynamicExecutor orderExecutorByName;

  // Style 2: @Qualifier + @Autowired
  @Qualifier("notification-executor")
  @Autowired
  private DynamicExecutor notificationExecutor;

  // Style 3: Bulk-inject all thread pools
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

  // ========== 6) Graceful shutdown ==========
  @PreDestroy
  public void shutdown() throws InterruptedException {
    orderExecutor.shutdown();
    if (!orderExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
      orderExecutor.shutdownNow();
    }
  }
}
```

##### 3.1.7 Design Decisions

- **Rejection policy as a runtime event parameter**: rejection policy is essentially a "runtime policy choice"; when the initial configuration is unreasonable, it can be hot-updated via `onResize` without modifying code or re-releasing.
- **Does not include `queueCapacity`**: hot-update of queue capacity cannot solve the "producer-consumer rate mismatch" problem; instead, it can accumulate more unconsumed tasks on instance crash, expanding the fault blast radius. `queueCapacity` can only be configured at startup via `platform.concurrency.thread-pools.*.queue-capacity`.
- **Transparent counting in CountingHandler**: TPE's `rejectedExecution` is not an overridable protected method, so subclasses cannot count by overriding. `DynamicExecutor` wraps the user handler as an internal `CountingHandler` (with an `AtomicLong` counter) at construction, then passes it to `super(...)`. `getRejectedExecutionHandler()` is overridden to return the original user handler, so monitoring code never notices the wrapper.
- **`onResize` only updates non-null fields**: callers only need to care about "the one I want to change", avoiding the "tune one parameter means pass all 4" boilerplate. The event itself is immutable, safe to pass across threads.
- **Constructor signatures match standard TPE**: the 4 / 5 / 6 / 7-arg constructors cover common usage scenarios; IDE hints and docs are inherited from `ThreadPoolExecutor`, with zero learning cost.

##### 3.1.8 API Quick Reference

| Method | Description |
|--------|-------------|
| `DynamicExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue)` | 4-arg constructor; default thread factory + `AbortPolicy` |
| `DynamicExecutor(..., workQueue, threadFactory)` | 5-arg constructor; custom thread factory + `AbortPolicy` |
| `DynamicExecutor(..., workQueue, handler)` | 6-arg constructor; default thread factory + custom handler |
| `DynamicExecutor(..., workQueue, threadFactory, handler)` | Full 7-arg constructor |
| `onResize(event)` | Runtime adjustment (updates only non-null fields) |
| `snapshot()` | One-stop runtime snapshot (`PoolStatus`, with 9 fields) |
| `getRejectionCount()` | Cumulative rejection count |
| `resetRejectionCount()` | Reset rejection counter |
| `PoolResizeEvent.builder()` | Create a hot-update event |
| `PoolResizeEvent.Builder.corePoolSize(Integer)` | Set core pool size (null = do not adjust) |
| `PoolResizeEvent.Builder.maximumPoolSize(Integer)` | Set maximum pool size (null = do not adjust) |
| `PoolResizeEvent.Builder.keepAliveTime(Duration)` | Set keep-alive time (null = do not adjust) |
| `PoolResizeEvent.Builder.rejectedHandler(handler)` | Set rejection policy (null = do not adjust) |
| `PoolStatus.getPoolSize()` | Current worker count (including idle) |
| `PoolStatus.getActiveCount()` | Current active thread count |
| `PoolStatus.getQueueSize()` | Queue used capacity |
| `PoolStatus.getQueueRemainingCapacity()` | Queue remaining capacity |
| `PoolStatus.getCompletedTaskCount()` | Completed task count |
| `PoolStatus.getTotalTaskCount()` | Total submitted task count |
| `PoolStatus.getRejectedCount()` | Cumulative rejection count |
| `PoolStatus.getCorePoolSize()` | Current core pool size |
| `PoolStatus.getMaximumPoolSize()` | Current maximum pool size |

##### 3.1.9 PoolResizeEvent — Hot-Update Event

`PoolResizeEvent` is the immutable event object consumed by `DynamicExecutor.onResize`. It packages "the thread pool parameters I want to change" into a value object so the config-center listener only needs to care about "the changed parts", without sending back the complete current configuration. The event has 4 fields: `corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler`, all of which may be `null` (meaning "do not adjust this field").

Built via `PoolResizeEvent.builder()`; all fields are independently optional; the event itself is immutable and safe to pass across threads. After `onResize` receives the event, it updates only the non-null fields and keeps the rest unchanged. `queueCapacity` is not part of the event fields — hot-update of queue capacity cannot solve the "producer-consumer rate mismatch" problem; instead, it can accumulate more unconsumed tasks on instance crash, expanding the fault blast radius. `queueCapacity` can only be configured at startup via `platform.concurrency.thread-pools.<poolName>.queue-capacity` (see [Configuration Reference](#configuration-reference)). See [FAQ Q15](#q15-how-to-integrate-with-config-centers-nacosetcd-for-dynamic-tuning) for an example of integrating `PoolResizeEvent` with config centers such as Nacos / Etcd.

```java
PoolResizeEvent event = PoolResizeEvent.builder()
    .corePoolSize(16)
    .maximumPoolSize(32)
    .keepAliveTime(Duration.ofMinutes(2))
    .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
    .build();
orderExecutor.onResize(event);
```

##### 3.1.10 PoolStatus — Runtime Snapshot

`PoolStatus` is the immutable runtime snapshot returned by `DynamicExecutor.snapshot()`. It aggregates 9 core monitoring metrics (`poolSize` / `activeCount` / `queueSize` / `queueRemainingCapacity` / `completedTaskCount` / `totalTaskCount` / `rejectedCount` / `corePoolSize` / `maximumPoolSize`) into a single value object, avoiding the cross-thread consistency issues that arise from calling multiple `ThreadPoolExecutor` getters separately from business code. `PoolStatus.builder()` provides 9 independent field setters, useful for mocking arbitrary states in unit tests or for serializing to third-party monitoring systems.

All fields are immutable `int` / `long`, safe to pass across threads without synchronization. `PoolStatus.getRejectedCount()` and `DynamicExecutor.getRejectionCount()` share the same internal counter; after calling `resetRejectionCount()`, the next `snapshot()`'s `rejectedCount` will be reset to zero in sync. See the `reportStatus()` example in [Module 3.1.6](#316-full-code-examples) for typical usage.

```java
PoolStatus status = orderExecutor.snapshot();
metrics.gauge("threadpool.pool_size", status.getPoolSize());
metrics.gauge("threadpool.rejected_count", status.getRejectedCount());
```

---

## Configuration Reference

This section expands the configuration by the three subsystems corresponding to the three modules: `rate-limiter` for the `RateLimiter` in [Module 2.2](#22-ratelimiter-token-bucket-rate-limiter), `circuit-breaker` for the `CircuitBreaker` in [Module 2.3](#23-circuitbreaker), and `thread-pools` for the `DynamicExecutor` in [Module 3.1](#31-dynamicexecutor-dynamic-thread-pool). The components in the other modules (`StructuredConcurrency` / `VirtualThreadFactory` / `BatchProcessor` / `Retryer` / `Debouncer`) have no runtime configuration entries and are only provided via imperative Builders.

### 1. Unified Configuration Entry

All configuration is mounted under the unified prefix `platform.concurrency.*`, bound by `ConcurrencyProperties`:

```yaml
platform:
  concurrency:
    rate-limiter:    # Token bucket rate limiter (Module 2.2)
      ...
    circuit-breaker: # Circuit breaker (Module 2.3)
      ...
    thread-pools:    # Dynamic thread pools (Module 3.1; multi-pool; key = pool name = bean name)
      <poolName>:
        ...
```

### 2. Complete YAML Example

```yaml
spring:
  threads:
    virtual:
      enabled: true   # Determines whether to use virtual threads or platform thread pools

platform:
  concurrency:
    # ========== Token bucket rate limiter (Module 2.2) ==========
    rate-limiter:
      # Whether to register a RateLimiter Bean in the Spring container (default: false)
      # When enabled, close() is automatically called on container shutdown to release the scheduler thread
      enabled: true

      # Number of tokens to refill per second (default: 100), also the bucket capacity
      permits-per-second: 200

    # ========== Circuit breaker (Module 2.3) ==========
    circuit-breaker:
      # Whether to register a CircuitBreaker Bean in the Spring container (default: false)
      enabled: true

      # Failure rate threshold (0.0 ~ 1.0, default: 0.5)
      # Internally multiplied by 100 and converted to integer percent
      failure-rate-threshold: 0.6

      # Sliding window size (default: 10, minimum 10)
      # Larger windows smooth the failure-rate statistics, but slow reaction to burst failures
      sliding-window-size: 100

      # Duration of the OPEN state (default: 30s)
      # After this, transitions to HALF_OPEN for probing
      wait-duration: 15s

      # Number of consecutive successes required in the HALF_OPEN probe phase (default: 3)
      # The current underlying breaker uses single-probe-then-switch semantics; this field is reserved for extension
      half-open-max-successes: 3

    # ========== Dynamic thread pools (Module 3.1; multi-pool; key = pool name = bean name) ==========
    # A config entry creates the corresponding DynamicExecutor Bean; no entry means no bean
    # Business code can inject via @Resource(name = "<poolName>") or @Qualifier("<poolName>")
    thread-pools:
      order-executor:
        # Core pool size (default: 4)
        core-pool-size: 8
        # Maximum pool size (default: 8)
        maximum-pool-size: 16
        # Idle thread keep-alive time (default: 60s)
        keep-alive-time: 30s
        # Work queue capacity; LinkedBlockingQueue is created based on this value (default: 2000)
        queue-capacity: 500
        # Thread-name prefix; uses "<poolName>-" as the prefix if empty
        thread-name-prefix: order-worker-
        # Rejection policy (case-insensitive, default: AbortPolicy)
        # Options: AbortPolicy / CallerRunsPolicy / DiscardPolicy / DiscardOldestPolicy
        rejected-handler: CallerRunsPolicy

      notification-executor:
        core-pool-size: 2
        maximum-pool-size: 4
        keep-alive-time: 60s
        queue-capacity: 200
        rejected-handler: AbortPolicy
```

### 3. Field Reference

#### 3.1 Token Bucket Rate Limiter (`platform.concurrency.rate-limiter.*`, Module 2.2)

| Config Item | Type | Default | Description |
|-------------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether to register a `RateLimiter` Bean in the Spring container |
| `permits-per-second` | int | `100` | Tokens refilled per second (also the bucket capacity) |

#### 3.2 Circuit Breaker (`platform.concurrency.circuit-breaker.*`, Module 2.3)

| Config Item | Type | Default | Description |
|-------------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether to register a `CircuitBreaker` Bean in the Spring container |
| `failure-rate-threshold` | double | `0.5` | Failure-rate threshold (0.0-1.0) |
| `sliding-window-size` | int | `10` | Sliding window size (minimum 10) |
| `wait-duration` | Duration | `30s` | Duration of the OPEN state |
| `half-open-max-successes` | int | `3` | Successes required in HALF_OPEN probe (reserved; not used in current calculation) |

#### 3.3 Dynamic Thread Pools (`platform.concurrency.thread-pools.*`, Module 3.1)

Multi-pool config; each key is a named thread pool and is also the name of the corresponding Spring Bean. Business code injects via `@Resource(name = "<poolName>")` / `@Qualifier("<poolName>")`, or via bulk `Map<String, DynamicExecutor>` injection.

Per-pool (`PoolProperties`) configurable items:

| Config Item | Type | Default | Description |
|-------------|------|---------|-------------|
| `{poolName}.core-pool-size` | int | `4` | Core pool size (kept even when there are no tasks) |
| `{poolName}.maximum-pool-size` | int | `8` | Maximum pool size (new threads are created up to this cap when the task queue is full) |
| `{poolName}.keep-alive-time` | Duration | `60s` | Idle threads above the core size are reclaimed after this duration |
| `{poolName}.queue-capacity` | int | `2000` | Work queue capacity; `LinkedBlockingQueue` is created based on this value |
| `{poolName}.thread-name-prefix` | String | `""` (=`<poolName>-`) | Thread-name prefix |
| `{poolName}.rejected-handler` | String | `AbortPolicy` | Rejection policy, case-insensitive; options `AbortPolicy` / `CallerRunsPolicy` / `DiscardPolicy` / `DiscardOldestPolicy` |

> On container shutdown, `shutdown()` is automatically called on each registered `DynamicExecutor`, with a maximum wait of 5 seconds; if not terminated, it falls back to `shutdownNow()`.

### 4. Key Design Decisions

- **`failure-rate-threshold` exposed as `double`**: configuration readability is more intuitive as `0.5` than `50`; internally, when building `CircuitBreaker.Builder`, it is multiplied by 100 to convert to integer percent.
- **`permits-per-second` is the only tunable RateLimiter configuration**: the default factory is `RateLimiter.ofTokensPerSecond`, suitable for "out-of-the-box" scenarios; complex scenarios (custom period, capacity) recommend overriding with a `@Primary` Bean on the business side.
- **`half-open-max-successes` reserved field**: the current underlying implementation uses "single-probe-success-then-close" semantics; the field is preserved so future extensions don't break the config contract.
- **`thread-pools` is a `Map<String, PoolProperties>`**: the key is the pool name (also the bean name); with config, a `DynamicExecutor` Bean is registered; without config, there is none. Runtime tuning is not done through this config item, but through the `DynamicExecutor.onResize(PoolResizeEvent)` API (see [Module 3.1.9](#319-poolresizeevent--hot-update-event) and [Module 3.1](#31-dynamicexecutor-dynamic-thread-pool)).

---

## Best Practices

### General Principles

1. **Prefer semantically-named methods**: `gatherAll`, `race`, `withDeadline` express intent at a glance and are more maintainable than `CompletableFuture.allOf().get()`.
2. **Virtual threads first**: JDK 25 projects should default to `spring.threads.virtual.enabled=true` so I/O-bound tasks enjoy million-level concurrency.
3. **Zero-dependency philosophy**: this component does not bind to third-party concurrency libraries; if you really need Resilience4j, Hystrix, etc., please introduce them yourself in your business modules.
4. **Configuration-driven**: if config can solve it, don't write code. `platform.concurrency.*` config items cover 90% of scenarios.

### Module 1: Structured Concurrency and Virtual Threads

#### StructuredConcurrency (Section 1.1)

1. **Prefer `gatherAll` over manual `CompletableFuture`**: error propagation is more accurate and cancellation semantics are clearer.
2. **Race mode is for disaster recovery only**: for multiple implementations of the same goal, any one succeeding is enough (multi-level cache, multi-region disaster recovery).
3. **Timeout mode protects external calls**: every external API call must have a timeout (recommend 200-500ms).
4. **Batch execution for large tasks**: when there are more than 100 concurrent tasks, use `gatherBatched` to control concurrency and avoid forking too many virtual threads.
5. **Best-effort gather for fault-tolerant aggregation**: when the goal is "get as many results as possible" rather than "all must succeed", use `gatherAllBestEffort` instead of `gatherAll`.

#### VirtualThreadFactory (Section 1.2)

1. **Always set a thread-name prefix**: helps with troubleshooting (JFR, Micrometer).
2. **ScopedValue instead of ThreadLocal**: ThreadLocal has performance issues with virtual threads; prefer `ScopedValue`.
3. **Don't pool virtual threads**: virtual threads are lightweight (creation cost ~1KB), creating a new thread is simpler than pooling.
4. **Avoid CPU-intensive computation inside virtual threads**: virtual threads are for I/O-blocking scenarios; CPU-bound work should still use platform thread pools.

#### BatchProcessor (Section 1.3)

1. **Set parallelism reasonably**: 50-200 for I/O-bound; `CPU count ± 2` for CPU-bound.
2. **Set an appropriate timeout**: prevent one slow task from blocking the whole batch; partial results are still usable after timeout.
3. **Always check `result.hasError()`**: do compensation processing for failed items.
4. **No need to pool under virtual threads**: `BatchProcessor` leverages the lightweight nature of virtual threads; no extra thread pool is needed.

### Module 2: Rate Limiting and Fault Tolerance Algorithms

#### Retryer (Section 2.1)

1. **Always set a reasonable backoff**: start `initialBackoff` from 50-200ms to avoid dense retries that knock down the downstream.
2. **Always enable jitter in multi-client scenarios**: `jitter(true)` avoids the server receiving a flood of retries simultaneously.
3. **Precisely limit retry exceptions**: `retryOn(IOException.class, TimeoutException.class)` avoids retrying business exceptions (e.g. 4xx).
4. **Use Fallback for critical business**: `execute(task, fallback)` returns a fallback after retries are exhausted, avoiding upstream crashes.
5. **Don't retry `Error`**: `Retryer` only catches `Exception`; `OutOfMemoryError` and others are propagated.

#### RateLimiter (Section 2.2)

1. **Choose rate-limit granularity based on downstream SLA**: external APIs use `ofTokensPerSecond`; batch tasks use `ofTokensPerDuration`.
2. **Pick `tryAcquire` variant by scenario**:
   - Business-critical path → `tryAcquire()` (strictly non-blocking)
   - Strict SLA → `tryAcquire(Duration)` (time-limited blocking)
   - Background task → `acquire()` (unbounded blocking)
3. **Multi-token granularity to control bursts**: for scenarios like batch imports, use `tryAcquire(n)` to consume multiple tokens at once.
4. **Avoid deprecated APIs**: don't use `ofTryAcquireTimeout` or `Builder.tryAcquireTimeoutEnabled`; switch to `ofTokensPerDuration` + `tryAcquire(Duration)`.
5. **Resource management**: call `close()` in a `@PreDestroy` hook to release the scheduler thread.

#### CircuitBreaker (Section 2.3)

1. **Granularity by service**: one circuit breaker per downstream service; avoid one fault polluting all calls.
2. **Failure rate vs failure count**: high QPS uses failure-rate mode; low QPS uses absolute-count mode (to avoid cold-start misjudgment).
3. **OPEN duration > downstream average recovery time**: usually 5-30 seconds; too short causes repeated flapping, too long delays recovery.
4. **Don't omit Fallback**: always use `execute(task, fallback)` instead of `executeOrThrow`, to avoid the circuit breaker causing HTTP 500.
5. **Don't break the circuit inside transactions**: fast-fail bypasses the transaction rollback path; circuit breaking fits external services better.
6. **Monitor `state()`**: expose `CircuitBreaker.State` to Prometheus for real-time observation.

#### Debouncer (Section 2.4)

1. **Adjust delay per scenario**: search box 300ms; form save 1s; button double-submit 500ms.
2. **Call `close()` when the Spring Bean is destroyed**: release the virtual-thread scheduler.
3. **`flush()` for user-initiated actions**: when the user clicks "Save Now", call `flush()` to bypass the wait.
4. **`cancel()` for undo**: when the user clicks "Cancel Edit", call `cancel()` to cancel the pending operation.
5. **Don't do heavy work inside the debounced action**: debouncing is meant to delay execution; the debounced action itself should remain async.

### Module 3: Dynamic Thread Pool

#### DynamicExecutor (Section 3.1)

1. **One independent pool per business scenario**: order processing, notification push, report export, etc. — each with its own load characteristics, each its own pool; one slow call should not drag down all tasks.
2. **Integrate `onResize` with the config center**: in a Nacos/Etcd config listener, build a `PoolResizeEvent` and call `onResize` for "tune without stopping". `PoolResizeEvent.builder()` supports passing only the fields that need to change.
3. **Monitor `PoolStatus.rejectedCount` trend**: a steady rise indicates the pool or queue is too small; trigger an increase in thread count or switch to `CallerRunsPolicy`.
4. **Don't call `onResize` too frequently**: over-adjustment causes the `ThreadPoolExecutor` to repeatedly rebuild Workers; recommend at least 1 minute between adjustments.
5. **Keep the default queue capacity to avoid producer-consumer imbalance**: frequent queue-size adjustments amplify system jitter; queue capacity is configured at startup via `platform.concurrency.thread-pools.<poolName>.queue-capacity` and is not part of `onResize` at runtime.

### Error Handling Philosophy

| Component | Recommended Error Handling |
|-----------|----------------------------|
| `StructuredConcurrency.gatherAll` | Let exceptions propagate; let upper layers handle errors uniformly |
| `StructuredConcurrency.gatherAllBestEffort` | Inspect `outcome.failures()`; compensate failed tasks |
| `Retryer.execute(task)` | `catch (RetryExhaustedException)` and propagate upward or wrap as a business exception |
| `Retryer.execute(task, fallback)` | Use the fallback directly; no need to catch |
| `BatchProcessor` | Check `result.hasError()`; retry or compensate failed items separately |
| `RateLimiter` | Check return value in non-blocking scenarios; `InterruptedException` decides in blocking scenarios |
| `CircuitBreaker` | Critical business uses `execute(task, fallback)`; monitoring scenarios use `executeOrThrow` |
| `Debouncer` | Internal `action` exceptions are swallowed (by debouncer design); critical operations should bring their own try/catch |

### Testing Recommendations

| Component | Testing Recommendation |
|-----------|------------------------|
| `StructuredConcurrency` | Use `Mockito` to mock each task; build mixed success/failure scenarios to verify order guarantees |
| `VirtualThreadFactory` | Verify the thread-name prefix; verify context propagation with `ScopedValue.get(key)` |
| `Retryer` | Use an `AtomicInteger` counter to control failure count; verify backoff timing matches expectations |
| `BatchProcessor` | Verify `successCount` / `failureCount`; verify `results()` aligns with input order |
| `RateLimiter` | After high-frequency calls, assert `availablePermits()` is close to 0; verify behavior after `close()` |
| `CircuitBreaker` | Inject a fake clock with `Builder.build(LongSupplier)`; build scenarios where the failure rate exceeds the threshold |
| `Debouncer` | Use `CountDownLatch` to wait for the action to run; verify `trigger()` resets the timer |

### Monitoring and Observability

1. **Virtual-thread monitoring**: JDK 25 JFR natively supports virtual-thread events (`jdk.VirtualThreadStart`, `jdk.VirtualThreadPinned`); you can observe in real time with `jfr` or JMC.
2. **Micrometer metrics**: recommend exposing the following metrics:
   - `circuit_breaker.state`: current state (0 = CLOSED / 0.5 = HALF_OPEN / 1 = OPEN)
   - `rate_limiter.available_permits`: current available tokens
   - `batch_processor.success_count` / `failure_count`: batch success/failure counts
3. **Circuit-breaker state alerting**: send an alert when `CircuitBreaker.State` transitions to OPEN; escalate to a severe alert if OPEN persists too long.
4. **Batch-processing failure details**: periodically scan `BatchResult.errors()`; trigger an alert when the failure rate exceeds a threshold.

---

## FAQ

### Q1: Why Not Keep Using Dynamic-TP?

This component's `DynamicExecutor` is a **lightweight dynamic thread-pool implementation**, aiming to cover 80% of platform thread pool scenarios (multi-pool management, runtime tuning, rejection counting, runtime snapshot) with zero extra dependencies. It does not aim for feature completeness; instead, it does the "most commonly used, would be painful to miss" capabilities right and well. dynamic-tp ([dromara/dynamic-tp](https://github.com/dromara/dynamic-tp)) is a feature-complete platform-grade thread pool management framework. The capability boundary between them is as follows.

#### Feature Comparison

| Feature | This Component (`DynamicExecutor`) | dynamic-tp |
|---------|-----------------------------------|------------|
| Config-center integration (Nacos/Apollo/Config/ZK/Consul) | Indirectly via Spring Cloud `EnvironmentChangeEvent`; covers all config centers that can trigger this event | Built-in dedicated adapters for Nacos / Apollo / Etcd / ZK |
| Runtime tuning (core/max/keepAlive/handler) | Supported (`onResize` event-driven; see [Module 3.0.2](#302-tuning-workflow)) | Supported (admin backend or config-center push) |
| Rejection counting | Supported (transparent `CountingHandler` wrapping; see [Module 3.0.3](#303-rejection-counting-principle)) | Supported |
| Runtime snapshot | Supported (`PoolStatus` returns 9 metrics in one call) | Supported |
| Alerting (DingTalk / WeCom / Feishu / Email) | **Not supported** (business side must hook into Prometheus / monitoring alerting themselves) | Built-in multi-channel alerting |
| Web admin backend | **Not supported** | Built-in visual interface |
| Task wrapping (MDC / Ttl / Transmittable) | **Not supported** | Built-in `MdcRunnable` / `TtlRunnable` / `TransmittableThreadLocal` wrapping |
| Thread-pool data persistence | **Not supported** | Built-in DB / Redis-based persistence |
| Dependency footprint | 0 extra dependencies (only comes with this component) | Need to introduce `dynamic-tp-spring-boot-starter` and its transitive dependencies |
| Auto-configuration | Zero config (just include this dependency and write `platform.concurrency.thread-pools.*` config) | Need to introduce the starter and configure the `dynamic-tp` namespace |

#### Decision Guide

- **Want only "multi-pool + tuning + basic monitoring"**: use this component; zero extra dependencies; the API in [Module 3.1](#31-dynamicexecutor-dynamic-thread-pool) gets a thread pool running in one line; tuning goes through `onResize` + config-center integration.
- **Need alerting / web admin / task wrapping / data persistence**: introduce [dynamic-tp](https://github.com/dromara/dynamic-tp); it has already turned these capabilities into out-of-the-box features.
- **Already using a Spring Cloud config center**: this component can directly reuse `EnvironmentChangeEvent`; no listener code is needed (see [Module 3.0.2](#302-tuning-workflow)); in this case, integration cost is lower.
- **Don't want to add another starter and its transitive dependencies just for thread pool management**: use this component; it only depends on Spring Boot (`provided` scope) and won't bloat the project.
- **The two are not in conflict**: use this component for core pools; pools with special needs (MDC propagation, alerting persistence) hook into dynamic-tp separately; mixing at the pool level is fine.

#### Summary

> **This component = lightweight + enough**; dynamic-tp = **feature-complete + completeness means heavier dependencies**. Start with this component, and only upgrade to dynamic-tp when you hit a real need — that's the reasonable evolution path for most projects.

If you need to use dynamic-tp, please introduce the dependency in your business modules yourself; this component does not enforce the dependency, and they are not mutually exclusive.

### Q2: What's the Difference Between StructuredConcurrency and TenantStructuredTaskScope?

- `StructuredConcurrency`: generic concurrency-semantic wrapper (gather, race, deadline, batched gather, best-effort gather), not bound to any specific context.
- `TenantStructuredTaskScope`: a tenant-context-aware concurrency utility based on `ScopedValue`, providing factory methods like `awaitAll` / `anySuccessful`.

The two are complementary, not replacements. You can call `StructuredConcurrency`'s static methods inside a `TenantStructuredTaskScope`.

### Q3: How to Monitor Virtual Threads?

1. JDK 25 JFR: use `jcmd <pid> JFR.start name=vthreads` to start JFR recording; events `jdk.VirtualThreadStart` / `jdk.VirtualThreadPinned` let you observe virtual threads.
2. Micrometer: use the `jvm.threads.*` metrics, which include platform and virtual thread statistics.
3. JMX: `ThreadMXBean.getThreadInfo(long[])` supports retrieving virtual thread information.

### Q4: Should RateLimiter or CircuitBreaker Be Called First?

Usually, rate limit first, then circuit break:

- **Rate limiting** controls the rate of concurrent requests (protecting your own threads from being saturated).
- **Circuit breaking** protects downstream services (fast-fail during sustained failures).

A typical chain: `RateLimiter.tryAcquire() → CircuitBreaker.execute(task, fallback) → business logic`.

### Q5: When Does the Circuit Breaker Auto-Recover After Tripping?

After the OPEN state lasts for `openDuration` (default 10 seconds), it automatically transitions to HALF_OPEN; the next call is treated as a "probe":

- Probe success → back to CLOSED (normal recovery)
- Probe failure → back to OPEN and re-timing

### Q6: What's the Difference Between Debouncer and RateLimiter?

- `Debouncer`: delays action execution; repeated triggers reset the timer; used for "stop operating X seconds, then execute Y".
- `RateLimiter`: controls the maximum number of calls per unit time; used for "at most N calls per second".

They solve different problems and should not be mixed.

### Q7: When Should I Use Retryer, and When Should I Not?

**Should use**:

- Transient network failures (503, timeouts).
- Database connection acquisition failures.
- Distributed-lock transient contention.

**Should not use**:

- Business exceptions (e.g. 4xx): filter them out with `retryOn`.
- Transactional operations: retry may cause duplicate execution (e.g. payment).
- Long-running tasks: retry cost is too high.

### Q8: How to Choose Between mapParallel and forEach in BatchProcessor?

- **`forEach`**: only care about side effects (DB writes, push, notification); no return value needed.
- **`mapParallel`**: need each item's processing result, collected in input order.

If you need both, first use `mapParallel` to get the results, then use `result.results()` for side effects.

### Q9: Why Doesn't the half-open-max-successes Config Item Take Effect?

The underlying `CircuitBreaker.Builder` currently uses "single-probe-success-then-close" semantics; the `half-open-max-successes` field is reserved for future extension (when upgrading to multi-probe mode, no need to break the config contract). If you need "multiple successful probes to close" semantics, consider building your own circuit breaker on the business side, or wait for this component to upgrade.

### Q10: How to Replace Time-Related Behavior of RateLimiter / CircuitBreaker in Tests?

- **RateLimiter**: use `RateLimiter.builder()` to create a custom instance; call `close()` in tests for quick resource release.
- **CircuitBreaker**: use `CircuitBreaker.builder().build(LongSupplier)` to inject a fake clock, simulating the "OPEN → HALF_OPEN after 10 seconds" transition.
- **Debouncer**: use `Duration.ofMillis(50)` short delay to ease testing; use `CountDownLatch` to wait for the action to run.

### Q11: Do I Need to Restart the Application After Config Changes?

Yes. All `platform.concurrency.*` config items are bound through `ConcurrencyProperties` at startup; runtime modifications will not take effect automatically. If dynamic adjustment is needed, you can:

1. Expose monitoring metrics like `RateLimiter.availablePermits()` via JMX.
2. Recreate component instances on the business side with `@RefreshScope` (not recommended; affects other Beans' dependencies).
3. Restart the application.

### Q12: Why Is ofTryAcquireTimeout Marked as Deprecated?

Since 2.2.0, this component has unified the `try*` prefix convention: all `try*` methods must be strictly non-blocking. `ofTryAcquireTimeout(permits, timeout)` implicitly makes `tryAcquire()` wait within `timeout`, violating this convention. Switch to `ofTokensPerDuration(permits, window)` + `tryAcquire(Duration)` to make the time-limited-blocking semantics explicit and clearer.

### Q13: Which Spring Boot Versions Are Compatible?

| Component Version | Compatibility |
|-------------------|---------------|
| Spring Boot | 4.0.x (`provided` scope; not forced) |
| JDK | 25+ (uses `StructuredTaskScope`, `ScopedValue`, `Thread.ofVirtual`) |

Environments below JDK 25 cannot use this component (because it depends on `StructuredTaskScope` preview API).

### Q14: How to Create and Use Multiple Named Thread Pools?

Just declare them by name in `application.yml`:

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

Injection in code (each key corresponds to one Spring Bean; bean name = key, usable as a `@Qualifier` value):

```java
@Resource(name = "order-executor")
private DynamicExecutor orderExecutor;

@Resource(name = "notification-executor")
private DynamicExecutor notificationExecutor;

// Or bulk-inject
@Autowired
private Map<String, DynamicExecutor> executors;
```

No need to write `@Bean` registration methods on the business side. `AlgorithmAutoConfiguration` iterates the `platform.concurrency.thread-pools` Map during `PostConstruct` and registers each pool as a Spring singleton Bean. `shutdown()` is called automatically on container shutdown.

### Q15: How to Integrate with Config Centers (Nacos/Etcd) for Dynamic Tuning?

`PoolResizeEvent` supports passing only the fields that need to change; `null` means "do not adjust" — the listener does not need to know the full current configuration:

```java
@NacosConfigListener(dataId = "order-executor.yml")
public void onConfigChange(String newConfig) {
    OrderExecutorConfig cfg = parse(newConfig);
    if (cfg.getCorePoolSize() != null || cfg.getMaximumPoolSize() != null) {
        orderExecutor.onResize(PoolResizeEvent.builder()
                .corePoolSize(cfg.getCorePoolSize())
                .maximumPoolSize(cfg.getMaximumPoolSize())
                .keepAliveTime(cfg.getKeepAliveTime())
                .rejectedHandler(cfg.getRejectedHandler())  // Optional
                .build());
    }
}
```

The event itself is immutable and safe to pass across threads. `onResize` is idempotent: two consecutive identical events have the same effect as one. Recommend adding debounce outside the listener (e.g. two config changes less than 30s apart count as one tuning), to avoid triggering `ThreadPoolExecutor` internal Worker rebuilds in short bursts.

### Q16: What's the Difference Between DynamicExecutor and a Plain `ThreadPoolExecutor`?

`DynamicExecutor` extends the standard `ThreadPoolExecutor`, so all native TPE APIs (`execute` / `submit` / `shutdown` / `shutdownNow` / `awaitTermination`, etc.) remain unchanged — it can be a drop-in replacement for TPE. The differences are only in extended capabilities:

- **`onResize` hot-update capability**: adjust `corePoolSize` / `maximumPoolSize` / `keepAliveTime` / `rejectedHandler` at runtime without modifying code or releasing; updates only the non-null fields in the event; null fields stay unchanged.
- **Transparent rejection counting via wrapper**: a `CountingHandler` (an internal `private static` wrapper) automatically counts rejected tasks; `getRejectedExecutionHandler()` returns the user's original handler, so business code is completely unaware of the wrapper.
- **`PoolStatus` snapshot**: one-stop access to 9 runtime metrics (`poolSize` / `activeCount` / `queueSize` / `queueRemainingCapacity` / `completedTaskCount` / `totalTaskCount` / `rejectedCount` / `corePoolSize` / `maximumPoolSize`), without calling multiple TPE getters.
- **Non-intrusive**: constructor signatures match standard TPE exactly; zero migration cost from old code.
- **Independent of any config center**: `onResize` is just a public method; the event source is determined by the caller (Nacos / Etcd / Admin API / timer / JMX); no external dependencies are bound inside this component.

---

## Related Documentation

### JDK Official

- [JDK 25 `StructuredTaskScope` Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 446: Scoped Values (Final)](https://openjdk.org/jeps/446)
- [JEP 480: Structured Concurrency (Third Preview)](https://openjdk.org/jeps/480)

### Spring Official

- [Spring Boot Virtual Threads Support](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html)
- [Spring Boot Configuration Properties](https://docs.spring.io/spring-boot/reference/features/external-config.html)

### Industry References

- [AWS Architect Marc Brooker: Full Jitter Algorithm](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
- [Resilience4j CircuitBreaker Documentation](https://resilience4j.readme.io/docs/circuitbreaker)
- [Hystrix Circuit Breaker Principles](https://github.com/Netflix/Hystrix/wiki/How-it-Works)
- [Token Bucket Algorithm (Wikipedia)](https://en.wikipedia.org/wiki/Token_bucket)
- [Dynamic-TP (Dynamic Thread Pool) — dromara open-source; a feature-complete open-source alternative](https://github.com/dromara/dynamic-tp)

### Project Documentation

- [atlas-richie-component Library Overview](../README.md)
- [Richie Platform Overview](../../README.md)