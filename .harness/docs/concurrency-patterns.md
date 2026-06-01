# 并发与性能规范

本规范约束组件内部的高性能编码模式，聚焦 **JDK 25 虚拟线程 + 结构化并发**的使用场景。

---

## 1. 何时考虑并发优化

**适用**：
- 需要并发请求多个下游（Redis、HTTP、MQ 等）的聚合逻辑
- 需要并发执行多个独立计算任务
- 单次调用中涉及多步骤 IO 操作，延迟累积明显

**不适用**：
- 简单单次 IO 操作（直接同步调用更简单）
- CPU-bound 任务（非 IO 瓶颈，并发提升有限）
- 纯内存计算

**原则**：先 profiling，找到真正的瓶颈再优化；不要提前优化。

---

## 2. 虚拟线程（Virtual Threads）

atlas-richie-platform 运行在 **JDK 25**，天然支持虚拟线程。

### 2.1 默认策略

- Spring Boot 4.x + JDK 25 默认启用虚拟线程
- 使用 `@Async` 方法或 `CompletableFuture.supplyAsync()` 时，线程池默认跑在虚拟线程上
- 不要在组件内自行创建线程池——用框架提供的异步机制

### 2.2 注意事项

- **不要在虚拟线程中阻塞时创建新线程**：如在虚拟线程中调用 `Thread.startNew()` 做 CPU 密集任务，会适得其反
- **TOCTOU（Time-of-check to Time-of-use）问题**：分布式锁的"检查-执行"间隙在高并发下可能放大；用 Redis 原子操作（如 Lua 脚本）替代先检查再操作
- **`ThreadLocal` 在虚拟线程中仍然安全**，但要小心虚拟线程的 pinned 问题——持有 synchronized 锁的同步块会 pinned 住载体线程

---

## 3. 结构化并发（Structured Concurrency）

JDK 21+ 的 `StructuredTaskScope` 适合将多个并发任务绑定到一个清晰的控制流。

### 3.1 标准模式

```java
// 聚合场景：并发请求多个数据源，汇总后返回
public UserProfile getUserProfile(Long userId) {
    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<>(UserProfile.class)) {
        Future<UserVO> userFuture = scope.fork(() -> userService.getUser(userId));
        Future<List<OrderVO>> ordersFuture = scope.fork(() -> orderService.getOrders(userId));

        scope.join(); // 等待所有任务完成

        // 如果某个任务失败，且是强依赖，抛出异常；如果是弱依赖，降级
        return scope.result();
    } catch (ExecutionException e) {
        throw new RuntimeException("获取用户画像失败", e.getCause());
    }
}
```

### 3.2 强依赖 vs 弱依赖任务

在并发任务拆分时，先区分依赖关系：

| 类型 | 含义 | 失败处理 |
|---|---|---|
| **强依赖** | 任一失败则整体失败 | `scope.join()` 后检查失败任务，整体抛异常 |
| **弱依赖** | 失败可降级，主流程继续 | 吞掉异常，记录日志，保留 null 或默认值 |

**决策应在方法入口处明确**，不要埋在子任务里。

### 3.3 封装建议

不要在每个方法里直接操作 `StructuredTaskScope`——封装一个工具：

```java
public interface ConcurrentTasks {
    <T> List<T> runAll(List<Callable<T>> tasks, Duration timeout);
}
```

业务方法只负责定义任务列表和聚合逻辑，底层的 scope 管理由工具统一处理。

---

## 4. 高性能编码流程（组件视角）

组件内高性能方法的推荐编码顺序：

1. **校验入参** — 失败抛 `IllegalArgumentException`，不继续
2. **并发执行可并行的子任务** — 用 `ConcurrentTasks` 工具或 `StructuredTaskScope`
3. **合并结果** — 主线程聚合，决策
4. **收尾** — 释放资源（try-with-resources 自动处理）

```java
public AggregatedData aggregate(Long id) {
    // 1. 校验
    if (id == null) throw new IllegalArgumentException("id 为空");

    // 2. 并发获取（弱依赖可降级）
    List<Future<DataPiece>> futures = IntStream.range(0, 3)
        .mapToObj(i -> CompletableFuture.supplyAsync(() -> fetchData(id, i)))
        .toList();

    // 3. 合并结果（主线程）
    List<DataPiece> pieces = futures.stream()
        .map(f -> {
            try { return f.join(); }
            catch (Exception e) { return DataPiece.empty(); } // 降级
        })
        .toList();

    // 4. 聚合返回
    return new AggregatedData(pieces);
}
```

---

## 5. 缓存操作的并发安全

### 5.1 先检查后使用（Check-Then-Act）

**禁止**在并发场景下这样做：

```java
// ❌ TOCTOU — 高并发下会多次查库
if (GlobalCache.getStringCache(key) == null) {
    Object data = loadFromDb(key); // 多线程都会执行这里
    GlobalCache.addStringCache(key, data, ttl);
}
```

**正确做法**：用组件提供的原子操作或锁：

```java
// ✅ 原子操作：Redis SETNX
String result = GlobalCache.string().setIfAbsent(key, value, timeout);

// ✅ 或分布式锁（见 GlobalCache.lock()）
try (var lock = GlobalCache.lock().tryLock(key, 5L)) {
    if (lock.success()) {
        // 进入临界区
    }
}
```

### 5.2 缓存击穿（Cache Penetration）

用 `GlobalCache` 提供的防缓存击穿封装：

```java
String value = GlobalCache.string().getWithLock(
    key,
    ttl,
    () -> loadFromDb(key) // loader 是同步的，框架自动处理并发
);
```

---

## 6. 不要做的事

- 不要用 `Thread.sleep` 模拟等待（用 Awaitility）
- 不要在并发任务里捕获异常后静默吞掉（至少 log.warn）
- 不要在虚拟线程里用 `ThreadLocal` 缓存大对象
- 不要在组件内创建无界线程池（内存溢出风险）
- 不要在并发路径中混入同步阻塞操作（会 pinned 虚拟线程）
