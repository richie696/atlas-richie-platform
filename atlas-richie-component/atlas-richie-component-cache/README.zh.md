# Atlas Richie Cache组件 (atlas-richie-component-cache)

基于 Redis 的缓存与数据结构组件。通过 **`GlobalCache` + Ops 访问器** 提供 KV、Hash、Set/ZSet、分布式锁、有界队列/栈、限流等能力；内置 L2、布隆过滤器、性能守卫。

> **消息队列**：可靠 Stream MQ 已独立为 [`atlas-richie-component-redis-streammq`](../atlas-richie-component-redis-streammq)，门面为 `StreamMQ.stream()`。本模块的 `queue()` 仅为削峰缓冲，不是 MQ。

> **目标读者**：业务服务开发者。如果你想知道"这个组件能帮我解决什么问题、怎么用"，这是你要的文档。
> **深度设计**：L2/分布式锁/性能守卫的完整设计思路见 [docs/](./docs/README.md)。

---

## 📖 目录

- [🚀 快速开始](#-快速开始)
  - [1. 添加依赖](#1-添加依赖)
  - [2. 配置](#2-配置)
  - [3. 写代码](#3-写代码)
  - [Ops 访问器一览](#ops-访问器一览)
- [🔧 核心功能](#-核心功能)
  - [String / 数值 / 布尔（`value()`）](#string--数值--布尔value)
  - [Hash（`struct()` / `field()`）](#hashstruct--field)
  - [有界队列 / 栈（`queue()` / `stack()`）](#有界队列--栈queue--stack)
  - [Set / ZSet（`collection()` / `ranking()`）](#set--zsetcollection--ranking)
  - [Key 管理（`key()`）](#key-管理key)
  - [分布式锁（`lock()`）](#分布式锁lock)
  - [Lua / 限流 / GEO / HyperLogLog / Bitmap](#lua--限流--geo--hyperloglog--bitmap)
  - [批量操作（`value()`）](#批量操作value)
- [📨 Redis Stream 消息队列（独立模块）](#-redis-stream-消息队列独立模块)
- [🛡 高级能力](#-高级能力)
  - [分布式锁（`lock()`）](#分布式锁lock-1)
    - [批量锁（`lock().batch()`）](#批量锁lockbatch)
  - [防缓存击穿（`getWithLock`）](#防缓存击穿getwithlock)
  - [L2 二级缓存](#l2-二级缓存)
  - [布隆过滤器](#布隆过滤器)
  - [性能守卫（RedisPerfGuard）](#性能守卫redisperfguard)
  - [多 Redis 实例路由](#多-redis-实例路由)
- [📦 本地缓存](#-本地缓存)
- [⚙️ 完整配置参考](#-完整配置参考)
  - [1. Redis 连接（`spring.data.redis`）](#1-redis-连接springdataredis)
    - [1.1 多 Redis 实例路由（`spring.data.redis.slaves`）](#11-多-redis-实例路由springdataredisslaves)
    - [1.2 Lettuce 扩展（`spring.data.redis.lettuce.*`）](#12-lettuce-扩展springdataredislettuce)
      - [1.2.1 为什么选择 Lettuce（而非 Jedis）](#121-为什么选择-lettuce而非-jedis)
      - [1.2.2 连接池配置（`spring.data.redis.lettuce.pool.*`）](#122-连接池配置springdataredislettucepool)
      - [1.2.3 Epoll 连接模式（Linux 高性能 IO）](#123-epoll-连接模式linux-高性能-io)
      - [1.2.4 保活配置（Keep-Alive）](#124-保活配置keep-alive)
      - [1.2.5 内存释放策略（Netty Direct Buffer 管理）](#125-内存释放策略netty-direct-buffer-管理)
    - [1.3 RESP 协议版本（`spring.data.redis.protocol-version`）](#13-resp-协议版本springdataredisprotocol-version)
  - [2. L2 二级缓存（`spring.data.redis`）](#2-l2-二级缓存springdataredis)
  - [3. 分布式锁（`spring.data.redis`）](#3-分布式锁springdataredis)
  - [4. 性能守卫（`spring.data.redis.perf`）](#4-性能守卫springdataredisperf)
  - [5. 本地缓存（`spring.data.local`）](#5-本地缓存springdatalocal)
    - [6.1 顶层配置](#61-顶层配置)
    - [6.2 缓存定义（`spring.data.local.cache-definitions[].*`）](#62-缓存定义springdatalocalcache-definitions)
  - [6. 组件级配置（`platform.cache`）](#6-组件级配置platformcache)
    - [6.1 布隆过滤器（`platform.cache.bloom-filter`）](#61-布隆过滤器platformcachebloom-filter)
  - [7. Stream MQ 配置（独立模块）](#7-stream-mq-配置独立模块)
  - [8. 配置总览（按功能定位）](#8-配置总览按功能定位)
- [🎯 最佳实践](#-最佳实践)
  - [Key 命名规范](#key-命名规范)
  - [缓存策略选择](#缓存策略选择)
  - [过期时间策略](#过期时间策略)
  - [性能优化](#性能优化)
  - [大 Key 阈值参考](#大-key-阈值参考)
    - [集合类型阈值](#集合类型阈值)
    - [String 值阈值（与 `RedisPerfGuard` 对齐）](#string-值阈值与-redissperfguard-对齐)
    - [Key 名称](#key-名称)
    - [大 Key 检测手段](#大-key-检测手段)
    - [大 Key 治理建议](#大-key-治理建议)
    - [有界队列 / 栈（`queue()` / `stack()`）](#有界队列--栈queue--stack-1)
  - [迁移窗口](#迁移窗口)
- [❓ 常见问题](#-常见问题)
  - [缓存穿透 / 雪崩 / 击穿 怎么防？](#缓存穿透--雪崩--击穿-怎么防)
  - [本地锁池和分布式锁什么关系？](#本地锁池和分布式锁什么关系)
  - [Stream 消息堆积了怎么办？](#stream-消息堆积了怎么办)
  - [迁移窗口报错导致启动失败？](#迁移窗口报错导致启动失败)
- [📚 专题文档](#-专题文档)



---

## 🚀 快速开始

### 1) 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

### 2) 配置

这是 **生产推荐配置**（带二级缓存 + 本地锁 + 性能守卫），复制后改 Redis 地址即可：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0
      timeout: 3s

      # ---------- 二级缓存 ----------
      enable-l2-caching: true               # 开启 L2 本地缓存
      l2-caching-data: [STRING, HASH]       # String/Hash 走 L2 加速

      # ---------- 分布式锁 ----------
      enable-local-lock: true               # JVM 锁池（默认已开启）

      # ---------- 性能守卫 ----------
      perf:
        enabled: true                       # 开启运行时检测
        warn-non-o1: true                   # 非 O(1) 操作打 WARN
        toc-soft-ms: 8                      # 超过 8ms 告警
        toc-hard-ms: 50                     # 超过 50ms ERROR
        block-forbidden-tiers: false        # 灰度阶段仅告警不阻断
        warn-string-payload-anti-patterns: true
        block-string-payload-violations: false

platform:
  cache:
    bloom-filter:
      enable: false                         # 有缓存穿透风险时开启
      type: REDISSON
      expected-insertions: 10000000
      false-probability: 0.001
```

### 3) 写代码

所有能力通过 **`GlobalCache.<ops>()`** 访问，无需注入 Bean（Spring 启动后自动绑定 `GlobalCacheManager`）：

```java
import com.richie.component.cache.GlobalCache;

// KV
GlobalCache.value().set("user:123", "Tom", 3_600_000L);
String name = GlobalCache.value().get("user:123", String.class);

// Hash 对象 / 字段
GlobalCache.struct().set("user:info:123", user, 3_600_000L);
GlobalCache.field().set("user:1", "name", "Tom");

// 防击穿
String cfg = GlobalCache.value().getWithLock("config:pay", 3_600_000L,
        () -> configService.load("pay"));

// 分布式锁
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) {
        orderService.process("123");
    }
}

// 有界队列（削峰，FIFO，满则丢最老）
var q = GlobalCache.queue().getOrCreate("order:pending", 1000L, Task.class);
q.offer(task);
Task t = q.poll();

// Key 事件订阅（需 notify-keyspace-events）
GlobalCache.event().subscribeKeyEvent("__keyevent@0__:expired", listener);

// 可靠消息队列 → 依赖 atlas-richie-component-redis-streammq
// StreamMQ.stream().publish("order-events", event);
```

### `Ops` 访问器一览

| 访问器                                 | 用途                      |
|-------------------------------------|-------------------------|
| `value()`                           | String / 数值 / 布尔 KV、计数器 |
| `struct()`                          | Hash 存取整对象              |
| `field()`                           | Hash 字段级读写              |
| `collection()`                      | Set                     |
| `ranking()`                         | ZSet / 排行榜              |
| `lock()`                            | 分布式锁                    |
| `queue()` / `stack()`               | 有界 FIFO / LIFO（非 MQ）    |
| `key()`                             | 过期、删除、类型                |
| `script()`                          | Lua 原子脚本                |
| `limiter()`                         | 滑动窗口限流                  |
| `bitmap()` / `hyperLog()` / `geo()` | 位图 / UV / 地理位置          |
| `notification()`                    | Pub/Sub 发布              |
| `event()`                           | Key 空间事件订阅              |

> 所有写路径已集成 L2（可配置）、布隆过滤器（可配置）、性能守卫（建议生产开启 `perf.enabled=true`）。

---

## 🔧 核心功能

### String / 数值 / 布尔（`value()`）

```java
GlobalCache.value().set("key", "value", 10_000L);
String val = GlobalCache.value().get("key", String.class);
GlobalCache.value().setIfAbsent("token", "abc", 60_000L);
GlobalCache.value().set("count", 100, 5_000L);
long n = GlobalCache.value().increment("counter", 1L);
GlobalCache.value().set("flag", true, 3_600_000L);
```

---

### Hash（`struct()` / `field()`）

```java
GlobalCache.field().set("user:1", "name", "Tom");
String name = GlobalCache.field().get("user:1", "name", String.class);
GlobalCache.struct().set("user:2", user, 3_600_000L);
UserInfo cached = GlobalCache.struct().get("user:2", UserInfo.class);
UserInfo loaded = GlobalCache.struct().getWithLock("user:999", UserInfo.class,
        3_600_000L, () -> userRepository.findById("999"));
```

**注意**：对象优先用 Hash（`struct`/`field`），不要把大 JSON 塞进 String value。

---

### 有界队列 / 栈（`queue()` / `stack()`）

原生无界 List API 已移除。FIFO 削峰用 `queue()`，LIFO 最近 N 条用 `stack()`。

```java
var queue = GlobalCache.queue().getOrCreate("task:pending", 500L, Task.class);
queue.offer(task);
Task t = queue.poll();
List<Task> batch = queue.drain(10);

var stack = GlobalCache.stack().create("audit:recent", 100L, Event.class);
stack.push(event);
Event latest = stack.peek();
```

> 可靠消息队列请用 **`atlas-richie-component-redis-streammq`** 的 `StreamMQ`，不是 `queue()`。

---

### Set / ZSet（`collection()` / `ranking()`）

```java
GlobalCache.collection().add("tags", "java");
Set<String> tags = GlobalCache.collection().get("tags", String.class);
GlobalCache.ranking().set("rank", "user1", 99.5);
Long rank = GlobalCache.ranking().reverseRank("rank", "user1");
```

---

### Key 管理（`key()`）

```java
GlobalCache.key().hasKey("k1");
GlobalCache.key().setExpiredTime("k1", 60_000L);
GlobalCache.key().removeCache("k1");
GlobalCache.key().getExpire("k1");
```

---

### 分布式锁（`lock()`）

```java
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) { /* ... */ }
}
```

---

### `Lua` / 限流 / `GEO` / `HyperLogLog` / `Bitmap`

```java
GlobalCache.script().eval(script, keys, args, Long.class);
boolean pass = GlobalCache.limiter().tryAcquire("rl:/api/pay", 100, 60);
GlobalCache.geo().add("geo:store", 116.39, 39.9, "beijing");
GlobalCache.hyperLog().add("uv:2025-01-01", "uid1");
GlobalCache.bitmap().set("sign:202501", 1, true);
```

---

## 📨 Redis Stream 消息队列（独立模块）

Stream MQ 已拆至 **`atlas-richie-component-redis-streammq`**，门面 **`StreamMQ`**：

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-redis-streammq</artifactId>
</dependency>
```

```java
StreamMQ.stream().publish("order-events", event);
// 消费：@RedisStreamConsumer + 见 streammq 模块 README
```

本模块 **`GlobalCache.queue()`** 是有界 List + 主动拉，用于削峰；需要消费组、ACK、死信时请用 StreamMQ。

> 本目录 `docs/Redis-Stream-*.md` 为历史设计文档，实现以 **streammq 模块** 为准。

---

### 批量操作（`value()`）

```java
Map<String, String> data = Map.of("k1", "v1", "k2", "v2");
GlobalCache.value().batchSet(data, 3_600_000L);
Map<String, String> values = GlobalCache.value().getMap(List.of("k1", "k2"),
        new TypeReference<String>() {});
```

---

## 🛡 高级能力

### 分布式锁（`lock()`）

**解决什么问题**：跨 JVM 实例的资源互斥访问（扣库存、下单、发放优惠券）。

**三层架构**：JVM 本地锁池 → 可重入检测 → Redisson FencedLock。

```java
// 乐观锁 + 看门狗续期（推荐）
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) {
        orderService.processOrder("123");
    }
}

// 悲观锁（阻塞直到获取或超时）
try (var lock = GlobalCache.lock().pessimistic("lock:order:456", 10L)) {
    if (lock.success()) {
        orderService.processOrder("456");
    }
}
```

**推荐配置**：`spring.data.redis.enable-local-lock: true`（默认已开启）

#### 批量锁（`lock().batch()`）

```java
Collection<String> keys = Set.of("lock:stock:1001", "lock:stock:1002", "lock:stock:1003");
try (var lock = GlobalCache.lock().batch(keys, 20L, TimeUnit.MINUTES)) {
    if (!lock.isSuccess()) {
        return ResultVO.getError("获取批量锁失败。");
    }
    stockService.batchDeduct(keys);
}
```

> 任一 key 获取失败会立即释放已持有的锁，避免部分锁定导致死锁。

---

### 防缓存击穿（`getWithLock`）

拦截链路：L2 → 布隆过滤器 → Redis → 分布式锁 → DB。

```java
String value = GlobalCache.value().getWithLock("config:app:pay_ratio",
    3600_000L, () -> configService.getConfig("pay_ratio"));

UserInfo user = GlobalCache.struct().getWithLock("user:profile:123",
    UserInfo.class, 3600_000L, () -> userService.findById("123"));
```

---

### `L2` 二级缓存

读路径：`value().get()` / `struct().get()` → L1 本地 → Redis → `getWithLock` 回源 DB。

写路径：`set()` → Redis SET → 双写 L1 → 键空间通知失效其他实例 L1。

```yaml
spring.data.redis:
  enable-l2-caching: true
  l2-caching-data: [STRING, HASH]   # LIST/SET 谨慎，数据量大时 JVM 压力大
```

深度设计见 [docs/Redis-L2与性能守卫设计说明.md](docs/zh/Redis-L2与性能守卫设计说明.md)。

---

### 布隆过滤器

开启后，`value().getWithLock` 自动联动布隆判定，拦截「一定不存在」的 key，减少穿透。

```yaml
platform.cache.bloom-filter:
  enable: true
  type: REDISSON
  expected-insertions: 10000000
  false-probability: 0.001
```

---

### 性能守卫（`RedisPerfGuard`）

| 维度        | 检测内容                                      | 可选阻断                                    |
|-----------|-------------------------------------------|-----------------------------------------|
| 复杂度       | 非 O(1) 操作打 WARN                           | `block-forbidden-tiers: true`           |
| 延迟        | 超过 `toc-soft-ms` WARN，`toc-hard-ms` ERROR | —                                       |
| String 载荷 | 集合/Map 整包写入 String、超大 JSON                | `block-string-payload-violations: true` |

`MigrationWindow` 约束项（`perf.enabled` 等）在 **2026-12-01** 前须设为 `true`，否则启动失败。

---

### 多 `Redis` 实例路由

```yaml
spring.data.redis:
  host: primary.redis.com
  slaves:
    order-redis:
      host: order.redis.com
      port: 6379
```

---

## 📦 本地缓存

独立于 Redis 的 JVM 本地缓存（与 L2 不同，这是业务显式使用的独立区域）。基于 JSR-107 标准，支持切换 Caffeine / Ehcache / Cache2k。

**解决什么问题**：业务需要缓存一些配置项、路由表等不需要 Redis 的数据。或者 L2 是按数据类型自动启用的，业务需要独立的命名缓存区域。

```yaml
spring.data.local:
  provider: CAFFEINE          # CAFFEINE / EHCACHE / CACHE2K
  cache-definitions:
    - name: appConfig
      expiry-policy: CREATED
      ttl: 300_000             # 5 分钟
    - name: routeTable
      expiry-policy: ACCESSED
      ttl: 3_600_000           # 1 小时
```

Cache2k 额外支持 refresh-ahead（过期前异步刷新）：

```yaml
spring.data.local:
  provider: CACHE2K
  cache-definitions:
    - name: hotData
      expiry-policy: ACCESSED
      ttl: 30_000
      read-through: true
      cache-loader-class-name: com.example.MyCacheLoader
      cache2k-refresh-ahead: true
      cache2k-loader-thread-count: 2
```

---

## ⚙️ 完整配置参考

> 下方按 **配置前缀 + 功能** 双维度组织，每个配置项都标注默认值、取值范围及对功能的影响。
> 标注 ⚠️ 的项有 @MigrationWindow 约束，到期未迁移会阻止应用启动。

### 1) Redis 连接（`spring.data.redis`）

| 配置项           | 类型       | 默认值          | 说明                                         |
|---------------|----------|--------------|--------------------------------------------|
| `host`        | String   | `localhost`  | Redis 主机                                   |
| `port`        | int      | `6379`       | Redis 端口                                   |
| `password`    | String   | —            | Redis 密码                                   |
| `database`    | int      | `0`          | Redis 库索引                                  |
| `timeout`     | Duration | `3s`         | 操作超时时间                                     |
| `server-type` | enum     | `STANDALONE` | 部署类型：`STANDALONE` / `SENTINEL` / `CLUSTER` |
| `slaves`      | Map      | —            | 命名子 Redis 实例（用于读写分离 / 多业务隔离），详见下文 §1.1     |

#### 1.1 多 Redis 实例路由（`spring.data.redis.slaves`）

```yaml
spring.data.redis:
  host: primary.redis.com       # 主 Redis
  port: 6379
  slaves:                       # 命名子 Redis，可独立配置连接
    order-redis:
      host: order.redis.com
      port: 6379
      password: order-pass
    user-cache:
      host: user.redis.com
      port: 6380
```

子 Redis 在 `MultiRedisTemplate` 中按名称路由。运行时新增/删除子 Redis 需要重启。

#### 1.2 Lettuce 扩展（`spring.data.redis.lettuce.*`）

> 本节扩展配置是为了解决 Spring Boot 原生 Lettuce 配置在**生产环境**中不够用的问题。涉及三个维度：**连接池调优**、**Socket 连接模式（epoll）**、**Netty 直接内存管理**。

---

##### 1.2.1 为什么选择 Lettuce（而非 Jedis）

项目默认采用 **Lettuce** 作为 Redis 客户端。Jedis 本质上属于直连模式（每个实例对应一个 Redis 连接，非线程安全，需通过连接池来模拟复用），而 Lettuce 是**基于 Netty 的异步驱动客户端**，具有以下差异：

| 对比维度        | Lettuce                | Jedis                  |
|-------------|------------------------|------------------------|
| 线程安全        | ✅ 是，单例复用               | ❌ 否，需连接池配合             |
| 连接模式        | 异步 + 同步 + 响应式          | 同步阻塞                   |
| 底层 IO       | Netty epoll（Linux 高性能） | BIO / NIO              |
| Redis 集群    | 原生支持                   | 需要额外的哨兵/集群适配           |
| 连接保活（epoll） | ✅ 支持 TCP_USER_TIMEOUT  | ❌ JDK SO_KEEPALIVE 粗粒度 |

结论：Lettuce 在连接复用、多线程并发、Linux 内核特性利用上优于 Jedis。Jedis 的优势仅在于上手简单，但生产环境推荐 Lettuce。

---

##### 1.2.2 连接池配置（`spring.data.redis.lettuce.pool.*`）

> 这是继承自 Spring Boot `DataRedisProperties.Lettuce` 的标准连接池配置。

Lettuce 虽然是异步驱动，但在同步调用模式下仍然使用连接池来限制并发连接数。**连接池没有通用标准配置**，因为最优值取决于 Redis 服务器性能、网络带宽、应用并发量等因素。推荐的调整方式：

1. 根据从业务侧统计到的并发量、访问峰值做一个初步估算
2. 设置一组初始值
3. 通过压测工具压测，观察 `RedisPool Exhausted` 异常、连接等待耗时等指标
4. 反复调整直到找到平衡点

| 配置项                               | 类型       | 默认值    | 说明                     |
|-----------------------------------|----------|--------|------------------------|
| `pool.max-active`                 | int      | `8`    | 最大活跃连接数（压测重点调整项）       |
| `pool.max-idle`                   | int      | `8`    | 最大空闲连接数                |
| `pool.min-idle`                   | int      | `0`    | 最小空闲连接数                |
| `pool.max-wait`                   | Duration | `-1ms` | 获取连接的最大等待时间（-1 = 无限等待） |
| `pool.time-between-eviction-runs` | Duration | —      | 空闲连接驱逐线程运行间隔           |

---

##### 1.2.3 Epoll 连接模式（Linux 高性能 IO）

> ⚠️ **本节特性仅 Linux x86_64 / aarch64 / riscv64 系统支持**。非 Linux 系统下会收到 Lettuce 的警告日志且配置不会生效。

**什么是 epoll？**

epoll 是 Linux 内核提供的**改进型 IO 多路复用接口**，是传统 `select` / `poll` 的增强替代。核心区别：

| 特性            | select / poll         | epoll                                           |
|---------------|-----------------------|-------------------------------------------------|
| **FD 上限**     | 有限（默认 2048，需重新编译内核扩大） | 无上限（仅受 `/proc/sys/fs/file-max` 限制，1GB 内存约 10 万） |
| **效率与连接数的关系** | 线性扫描全部 FD，连接越多越慢      | 仅操作"活跃的"FD，空闲连接不参与扫描                            |
| **内核通信方式**    | 用户空间与内核空间消息拷贝         | **mmap 共享内存**，避免内核态/用户态数据拷贝                     |
| **触发模式**      | 仅水平触发（持续通知直到状态变更）     | 支持水平触发 + **边缘触发**（仅状态变化时通知一次）                   |
| **适用场景**      | 少量活跃连接                | 大量连接 + 少量活跃（典型 WAN 场景）                          |

**为什么这个重要？**

生产环境一个微服务实例可能需要同时维护数十到数百个 Redis 连接（主 Redis + 哨兵 + 各子 Redis 实例）。使用 `select` / `poll` 时，每次 IO 多路复用都要线性扫描全部 FD，即使绝大多数连接处于空闲状态。epoll 只会在活跃连接上有回调，空闲连接不消耗 CPU。在 WAN 场景（大量空闲 + 少量活跃）下，**epoll 的效率远高于 select/poll**。

---

##### 1.2.4 保活配置（Keep-Alive）

**解决的问题**：Redis 长连接经过云负载均衡（SLB/ALB/NLB）、NAT 网关、防火墙时，这些设备有空闲超时策略（通常 60-300 秒）。一旦连接空闲超过设备超时时间，设备会**静默断开 TCP 连接**而客户端毫不知情。下次请求时才发现连接已死，触发连接重建，极端情况下会耗尽连接池。

**为什么扩展配置？**
- JDK 标准 `SO_KEEPALIVE` 只提供开启/关闭（boolean），无法细调 `idle`、`interval`、`count`
- Linux sysctl 全局参数（`/proc/sys/net/ipv4/tcp_keepalive_time`）影响主机上所有进程
- Netty epoll 原生传输支持逐连接设置 `TCP_KEEPIDLE`、`TCP_KEEPINTVL`、`TCP_KEEPCNT` + **`TCP_USER_TIMEOUT`（RFC 5482）**

**`TCP_USER_TIMEOUT` 是什么？**
RFC 5482 定义的 TCP 选项，指定 TCP 在未收到对端 ACK 的情况下，最多等待多久后强制关闭连接。它比单纯依赖 keep-alive 探针更可靠——keep-alive 探针只检测连接是否可达，而 `TCP_USER_TIMEOUT` 同时检测**数据是否被对端确认**。推荐公式：
```
tcpUserTimeout ≈ idle + interval × count
```

**生产推荐配置**：

```yaml
spring:
  data:
    redis:
      lettuce:
        keep-alive:
          enabled: true                             # 启用保活（仅 Linux 有效）
          count: 6                                  # 连续 6 次无响应判定连接死亡
          idle: 90                                  # 空闲 90 秒后开始发送探针
          idle-unit: seconds
          interval: 60                              # 探针间隔 60 秒
          interval-unit: seconds
          tcp-user-timeout: 450                     # TCP 用户超时（RFC 5482）
          tcp-user-timeout-unit: seconds             # 90 + 6 × 60 = 450 秒
```

**各参数的物理含义**：

| 参数                 | 含义                       | 默认值   | 生产推荐 | 备注                           |
|--------------------|--------------------------|-------|------|------------------------------|
| `idle`             | 连接空闲多久后开始发 keep-alive 探针 | 2 小时  | 90s  | Linux 内核默认 2h，对云环境过长         |
| `interval`         | 相邻探针的间隔                  | 75s   | 60s  | 过于密集增加服务器负担，过于稀疏检测太慢         |
| `count`            | 连续无响应多少次后判定死亡            | 9     | 6    | 结合 interval 计算总检测时间          |
| `tcp-user-timeout` | TCP 等待对端 ACK 的最长时间       | 7875s | 450s | 推荐 ≈ idle + interval × count |

**非 Linux 系统降级**：非 Linux 下 epoll 不可用，保活配置不会应到 Socket 通道，Lettuce 会输出 ⚠️ 警告日志，不影响程序运行。

---

##### 1.2.5 内存释放策略（Netty Direct Buffer 管理）

**解决的问题**：Lettuce 基于 Netty，Netty 使用**引用计数的 Direct Buffer（直接内存/堆外内存）池**。池化可以减少分配/释放开销，但有一个代价——**池只涨不缩**。流量高峰时 Netty 分配的 Direct Buffer 在低峰期不会自动释放回操作系统，堆积在池中。最终触达 `-XX:MaxDirectMemorySize`（默认 = `-Xmx`）上限后抛出 `OutOfMemoryError: Direct buffer memory`。

```yaml
spring:
  data:
    redis:
      lettuce:
        memory-release-policy: USAGE_RADIO    # 内存释放策略（默认）
        memory-release-ratio: 3               # 使用率达到 75% 时触发释放（默认）
```

**`memory-release-policy` 可选值**：

| 策略                | 行为                              | 适用场景            |
|-------------------|---------------------------------|-----------------|
| `USAGE_RADIO`（默认） | 池使用率达到 `ratio/(ratio+1)` 时释放旧缓存 | 通用场景，平衡 CPU 和内存 |

**`memory-release-ratio` 的影响**（仅 `USAGE_RADIO` 模式生效）：

| ratio | 触发阈值 | 内存倾向 | CPU 倾向           | 推荐场景          |
|-------|------|------|------------------|---------------|
| 1     | 50%  | 低内存  | 较高 CPU（频繁释放/再分配） | 低 QPS、内存敏感    |
| 3（默认） | 75%  | 中等   | 中等               | 通用场景          |
| 5     | 83%  | 较高   | 较低               | 高 QPS 服务      |
| 10    | 90%  | 高    | 低                | 极高 QPS、CPU 敏感 |

**带来的好处**：
- 直接内存在低峰期可收敛，避免 `OutOfDirectMemoryError`
- 高 QPS 服务用高 ratio（减少释放/再分配的 CPU 开销），低 QPS 服务用低 ratio（节省内存）
- 通过配置调节，无需堆 JVM 参数间接调优

---

#### 1.3 RESP 协议版本（`spring.data.redis.protocol-version`）

| 取值      | 适用          | 特点                      |
|---------|-------------|-------------------------|
| `RESP2` | Redis 2 - 5 | 传统协议                    |
| `RESP3` | Redis 6+    | 性能更好、支持客户端缓存、避免响应数据二次转换 |

Redis 6+ 强烈建议 RESP3。Redis 禁用 HELLO 命令时需用 RESP2 避免连接失败。

---

### 2) L2 二级缓存（`spring.data.redis`）

| 配置项                 | 类型                | 默认值     | 关联功能    | 说明                                               |
|---------------------|-------------------|---------|---------|--------------------------------------------------|
| `enable-l2-caching` | boolean           | `false` | L2 二级缓存 | 是否启用 L1 本地缓存                                     |
| `l2-caching-data`   | List<KeyTypeEnum> | `[]`    | L2 二级缓存 | 哪些数据类型走 L2，可选 `STRING` / `HASH` / `LIST` / `SET` |

> TTL 自动追加 1~10 分钟随机偏移防雪崩。`enable-l2-caching=false` 时配置无效但 `*WithLock` 的本地查路径仍会工作。

**多实例一致性**：依赖 Redis 键空间通知

```bash
# Redis 服务端必须开启
notify-keyspace-events KEA
```

短暂脏读窗口：写入 Redis 到收到通知之间，跨实例 L1 仍是旧值。建议 L1 TTL 不超过「原始 TTL × 2」。

---

### 3) 分布式锁（`spring.data.redis`）

| 配置项                 | 类型      | 默认值                 | 关联功能     | 说明                                                          |
|---------------------|---------|---------------------|----------|-------------------------------------------------------------|
| `enable-local-lock` | boolean | **`true`** ⚠️ 已默认开启 | JVM 本地锁池 | 同一 JVM 内对同一锁 key 走 `ConcurrentHashMap` 查表，关闭则每次直接打 Redisson |

> **何时考虑关闭**：极端情况下锁 key 极多、命中率极低、`ConcurrentHashMap` 内存膨胀。实际业务中几乎不会出现。

---

### 4) 性能守卫（`spring.data.redis.perf`）

> 关联功能：[性能守卫](#性能守卫redisperfguard)。⚠️ = @MigrationWindow 约束，2026-12-01 前必须设为 `true`，否则启动失败。

| 配置项                                  | 类型           | 默认值               | 说明                                                           |
|--------------------------------------|--------------|-------------------|--------------------------------------------------------------|
| `enabled` ⚠️                         | boolean      | `false`           | 总开关。关闭则 perf 治理全部失效                                          |
| `warn-non-o1`                        | boolean      | `true`            | 非 O(1) 操作打 WARN                                              |
| `toc-soft-ms`                        | long         | `8`               | 软阈值（毫秒），超过 WARN                                              |
| `toc-hard-ms`                        | long         | `50`              | 硬阈值（毫秒），超过 ERROR                                             |
| `block-forbidden-tiers` ⚠️           | boolean      | `false`           | 阻断 LINEAR_N / WORSE 复杂度操作（抛异常）                               |
| `log-big-key-probe-hints`            | boolean      | `true`            | 非 O(1) 时输出 BIGKEY 探测建议                                       |
| `toc-allowed-complexities`           | List<String> | `[]`              | 复杂度白名单（`O1` / `LOG_N` / `SCRIPT_OR_UNKNOWN`），非空则不在列表内即 ERROR |
| `warn-string-payload-anti-patterns`  | boolean      | `true`            | String 写入前检测反模式（集合/Map 整包、超大 JSON）                           |
| `warn-json-like-string-blob`         | boolean      | `true`            | 启用 JSON 形状启发式                                                |
| `json-like-min-chars-for-warn`       | int          | `128`             | JSON 整包 WARN 阈值（去空白字符数）                                      |
| `string-payload-max-chars-warn`      | int          | `100_000`         | String 字符数 WARN 阈值                                           |
| `string-payload-max-chars-error`     | int          | `1_000_000`       | String 字符数 ERROR 阈值                                          |
| `string-payload-max-bytes-warn`      | int          | `262_144` (256KB) | byte[] 写入 WARN 阈值                                            |
| `string-payload-max-bytes-error`     | int          | `1_048_576` (1MB) | byte[] 写入 ERROR 阈值                                           |
| `block-string-payload-violations` ⚠️ | boolean      | `false`           | 阻断 String 载荷违规写入（抛异常）                                        |

**生产推荐**（灰度完成）：

```yaml
spring.data.redis.perf:
  enabled: true
  block-forbidden-tiers: true
  block-string-payload-violations: true
```

---

### 5) 本地缓存（`spring.data.local`）

> 关联功能：[本地缓存](#-本地缓存)。独立于 L2 的业务显式使用区域，基于 JSR-107。

#### 6.1 顶层配置

| 配置项                 | 类型                   | 默认值       | 说明                                      |
|---------------------|----------------------|-----------|-----------------------------------------|
| `provider`          | enum                 | `EHCACHE` | 底层实现：`EHCACHE` / `CAFFEINE` / `CACHE2K` |
| `cache-definitions` | Set<CacheDefinition> | `[]`      | 缓存定义列表，详见 §6.2                          |

#### 6.2 缓存定义（`spring.data.local.cache-definitions[].*`）

每条 `CacheDefinition` 定义一个独立的本地缓存区域（按 `name` 区分）：

| 配置项                           | 类型       | 默认值        | 说明                                                                         |
|-------------------------------|----------|------------|----------------------------------------------------------------------------|
| `name`                        | String   | —          | 缓存区域名称（必填，业务按名获取）                                                          |
| `expiry-policy`               | enum     | —          | 过期策略：`CREATED` / `ACCESSED` / `ETERNAL` / `MODIFIED` / `TOUCHED`           |
| `expiry`                      | int      | `5`        | 过期时间数值（`ETERNAL` 时无效）                                                      |
| `expiry-unit`                 | TimeUnit | `MINUTES`  | 过期时间单位（`ETERNAL` 时无效）                                                      |
| `statistics-enabled`          | boolean  | `false`    | 是否启用命中率/操作数统计                                                              |
| `store-by-value`              | boolean  | `false`    | `true` 按值存（深拷贝）、`false` 按引用存                                               |
| `read-through`                | boolean  | `false`    | 启用 read-through，miss 时调用 CacheLoader 回源（需配合 `cache-loader-class-name`）     |
| `cache-loader-class-name`     | String   | —          | `javax.cache.integration.CacheLoader` 全类名                                  |
| `write-through`               | boolean  | `false`    | 启用 write-through，put/remove 时回调 CacheWriter（需配合 `cache-writer-class-name`） |
| `cache-writer-class-name`     | String   | —          | `javax.cache.integration.CacheWriter` 全类名                                  |
| `cache2k-refresh-ahead`       | boolean  | `false`    | **仅 CACHE2K** 生效：过期前异步刷新（需 `read-through=true`）                            |
| `cache2k-loader-thread-count` | Integer  | cache2k 默认 | **仅 CACHE2K** 生效：后台刷新线程数                                                   |

**示例**：

```yaml
spring.data.local:
  provider: CAFFEINE
  cache-definitions:
    - name: appConfig
      expiry-policy: CREATED
      expiry: 5
      expiry-unit: MINUTES
      statistics-enabled: true
    - name: hotData
      provider: CACHE2K          # 单独 provider（需要在 CacheDefinition 中加）
      expiry-policy: ACCESSED
      expiry: 30
      expiry-unit: SECONDS
      read-through: true
      cache-loader-class-name: com.example.MyCacheLoader
      cache2k-refresh-ahead: true
      cache2k-loader-thread-count: 2
```

---

### 6) 组件级配置（`platform.cache`）

| 配置项              | 类型   | 默认值     | 关联功能  | 说明             |
|------------------|------|---------|-------|----------------|
| `cache-provider` | enum | `REDIS` | 全局选择  | 当前实现：仅 `REDIS` |
| `bloom-filter.*` | —    | —       | 布隆过滤器 | 详见 §6.1        |

#### 6.1 布隆过滤器（`platform.cache.bloom-filter`）

> 关联功能：[布隆过滤器](#布隆过滤器)。`enable=false` 时其他配置无效。

| 配置项                   | 类型      | 默认值                           | 说明                                     |
|-----------------------|---------|-------------------------------|----------------------------------------|
| `enable`              | boolean | `false`                       | 总开关                                    |
| `type`                | enum    | `REDISSON`                    | 实现：`REDISSON`（跨实例共享）/ `GUAVA`（单实例，零网络） |
| `key`                 | String  | `platform:cache:bloom:global` | 布隆数据 key（Redisson 模式）                  |
| `expected-insertions` | long    | `10_000_000`                  | 预期插入数量（建议略大于实际最大值 20~50%）              |
| `false-probability`   | double  | `0.001`                       | 误判率（0.001 = 0.1%，最低 0.0001 极低误判）       |

**内存估算**（公式：`bits ≈ -n × ln(p) / (ln2)²`）：

| n           | p     | 内存     |
|-------------|-------|--------|
| 1_000_000   | 0.001 | 1.8 MB |
| 10_000_000  | 0.001 | 18 MB  |
| 100_000_000 | 0.001 | 180 MB |

---

### 7) `Stream` `MQ` 配置（独立模块）

消费者、幂等去重、监控、链路追踪等配置已迁移至 **`atlas-richie-component-redis-streammq`**，本模块不再绑定 `platform.cache.redis.stream.*` 或 `spring.data.redis.stream-idempotency`。

- 依赖与用法：见上文 [Redis Stream 消息队列（独立模块）](#-redis-stream-消息队列独立模块)
- 历史参考：[docs/Redis-Stream-使用指南.md](../atlas-richie-component-redis-streammq/docs/Redis-Stream-使用指南.md)（以 streammq 源码为准）

---

### 8) 配置总览（按功能定位）

> 不确定去哪儿查？按这个表反过来定位。

| 你想改什么      | 配置前缀                                        | 章节   |
|------------|---------------------------------------------|------|
| Redis 连接   | `spring.data.redis`                         | §1   |
| 多 Redis 实例 | `spring.data.redis.slaves`                  | §1.1 |
| Lettuce 调优 | `spring.data.redis.lettuce`                 | §1.2 |
| L2 二级缓存    | `spring.data.redis.enable-l2-caching`       | §2   |
| JVM 本地锁池   | `spring.data.redis.enable-local-lock`       | §3   |
| 性能守卫       | `spring.data.redis.perf`                    | §4   |
| 本地缓存       | `spring.data.local`                         | §5   |
| 布隆过滤器      | `platform.cache.bloom-filter`               | §6.1 |
| Stream MQ  | streammq 模块 `platform.cache.redis.stream.*` | §7   |

---

## 🎯 最佳实践

### `Key` 命名规范

```
{business}:{object}:{id}:{field}
user:info:123
order:status:456
lock:order:789
```

### 缓存策略选择

| 场景         | 推荐方案                   | 配置                                      |
|------------|------------------------|-----------------------------------------|
| 热点 KV 读多写少 | L2 + 防击穿               | `enable-l2-caching: true` + `*WithLock` |
| 高并发锁争用     | 分布式锁 + 本地锁             | `enable-local-lock: true`（默认）           |
| 大列表缓存      | `*WithLock` 防重复        | 不走 L2，用防击穿方法                            |
| 排行榜        | ZSet                   | 无需额外配置                                  |
| 去重统计（大量）   | HyperLogLog            | 12KB 统计 2^64 元素                         |
| 去重统计（少量）   | Set                    | O(1) 成员判断                               |
| 限流         | `limiter().tryAcquire` | Lua 原子滑动窗口                              |
| 附近查询       | `geo()`                | 无需额外配置                                  |
| 签到打卡       | `bitmap()`             | 1 亿用户每天约 12MB                           |
| 削峰缓冲       | `queue()` / `stack()`  | 有界 List，非 MQ                            |
| 可靠 MQ      | `StreamMQ`（独立模块）       | 消费组 / ACK / 死信                          |

### 过期时间策略

- **热点数据**：短过期（秒级），配合 L2 读放大
- **配置数据**：中等过期（分钟级）
- **基础数据**：长过期（小时级），TTL 加随机偏移防雪崩

### 性能优化

- 热点读必须开 L2 + `*WithLock`，避免 Redis 成为瓶颈
- 批量操作代替循环逐 key 读写，减少网络 RTT
- 预发环境灰度 `perf.enabled`，消除 `[RedisPerf]` 告警后再上生产
- ToC 核心链路避免 `KEYS *`、全量 `HGETALL`/`SMEMBERS`

### 大 `Key` 阈值参考

大 Key 会导致 Redis 主线程阻塞、主从同步延迟、AOF 重写卡顿、集群迁移卡死等隐患。以下阈值综合了 **Redis 内部编码优化分界线**与**组件性能守卫默认值**，两者对齐、互为验证。

#### 集合类型阈值

| 类型   | 编码优化阈值                      | 推荐业务上限       | 越过编码线的影响                  | 典型隐患命令                                                                   |
|------|-----------------------------|--------------|---------------------------|--------------------------------------------------------------------------|
| list | 512 元素（quicklist 节点切换）      | **5,000** 元素 | 节点从 ziplist 退化为链表，单节点内存增大 | `LRANGE 0 -1`（O(N) 全量）；**List 无去重能力，任何去重方案都需 O(N) 遍历，必须借助伴生 Set 在写入前拦截** |
| set  | 512 元素（intset → hashtable）  | **5,000** 元素 | 内存暴增 2~3 倍，rehash 卡顿      | `SMEMBERS`（O(N) 全量）                                                      |
| zset | 128 元素（ziplist → skiplist）  | **5,000** 元素 | 内存暴增 2~3 倍                | `ZRANGE 0 -1`（O(N)）、`ZREMRANGEBYRANK`（O(log N+M)）                        |
| hash | 512 字段（ziplist → hashtable） | **1,000** 字段 | 内存暴增 2~3 倍，rehash 卡顿      | `HGETALL`（O(N)）、`HKEYS`（O(N)）                                            |

> **有界队列 / 栈**（非 Stream MQ）：`queue()` FIFO、**主动拉**消费，适合削峰与轻量缓冲；`stack()` LIFO、满则拒绝压入。二者均不能替代 [StreamMQ 独立模块](#-redis-stream-消息队列独立模块)。`maxLen` 合法 **1～4,999**；默认不可变，仅 `grow()` 单次 ×2 至封顶。创建时校验 Redis 类型为 none/list。`drain` 的 `count` 为 **1～20**；读路径反序列化失败打 **WARN**（批量跳过坏元素，`poll`/`pop` 已弹出但失败时返回 `null`）。

> **说明**：「编码优化阈值」是 Redis 内部从紧凑编码切换到宽松编码的分界线，越过即导致内存与 CPU 代价跳变；「推荐业务上限」是在紧凑编码之上留出安全余量的经验值，超过后全量扫描类命令延迟明显上升。两者取**较小值**作为实际红线。

#### String 值阈值（与 `RedisPerfGuard` 对齐）

| 级别    | 字符数阈值             | 字节数阈值            | 配置项                                                                 | 行为                                                              |
|-------|-------------------|------------------|---------------------------------------------------------------------|-----------------------------------------------------------------|
| OK    | < 100,000（~100KB） | < 256KB          | —                                                                   | 正常写入                                                            |
| WARN  | ≥ 100,000         | ≥ 262,144（256KB） | `string-payload-max-chars-warn` / `string-payload-max-bytes-warn`   | 输出 WARN 日志                                                      |
| ERROR | ≥ 1,000,000（~1MB） | ≥ 1,048,576（1MB） | `string-payload-max-chars-error` / `string-payload-max-bytes-error` | 输出 ERROR 日志；若 `block-string-payload-violations=true` 则直接抛异常阻断写入 |

此外，`RedisStringPayloadInspector` 还会检测以下反模式并直接报 ERROR：
- 将 `Collection` / `Map` / 数组整包序列化进 String（应改用 Hash / List / ZSet）
- 将任意 JavaBean 整包 JSON 塞进单 key（建议 Hash 字段建模）
- 以 `{` 或 `[` 开头且长度 ≥ 128 字符的疑似整包 JSON（WARN）

#### `Key` 名称

| 项目     | 建议                                        |
|--------|-------------------------------------------|
| Key 长度 | < 128 字节（Redis 限制 512MB，但长 key 增加内存与比较开销） |
| 命名规范   | `{业务}:{实体}:{ID}`，避免空格与特殊字符                |

#### 大 `Key` 检测手段

| 方式                                                                       | 适用场景                                                                                                                   |
|--------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `redis-cli --bigkeys`                                                    | 离线扫描，快速定位 Top N 大 Key                                                                                                  |
| `MEMORY USAGE key`                                                       | 在线查看单 key 内存占用（Redis 4.0+）                                                                                             |
| `redis-cli --scan --pattern '*' \| xargs -I{} redis-cli MEMORY USAGE {}` | 批量扫描（慎用，生产建议采样）                                                                                                        |
| 组件内置 `RedisPerfGuard`                                                    | 运行时自动检测：非 O(1) 调用 WARN + BIGKEY 探测建议（HLEN/LLEN/SCARD/ZCARD），受 `spring.data.redis.perf.log-big-key-probe-hints=true` 控制 |
| 慢查询日志 `SLOWLOG GET`                                                      | 发现延迟 ≥ `toc-hard-ms`（默认 50ms）的可疑操作                                                                                     |

#### 大 `Key` 治理建议

- **删除用 UNLINK 而非 DEL**：`GlobalCache.key().removeCache()` 内部已使用 `UNLINK`（异步释放内存），避免主线程阻塞。业务代码禁止自行调用 `DEL`。
- **分片代替单 Key**：大 Hash 拆成 `key:shard:{0..N}`，大 List 拆成多段，大 Set 按业务维度分桶。
- **渐进式扫描**：用 `HSCAN` / `SSCAN` / `ZSCAN` 代替 `HGETALL` / `SMEMBERS` / `ZRANGE 0 -1`，每次取有界批次。
- **超限迁移**：单 key 体量持续超标（> 10MB 或元素 > 50,000）应迁至 MongoDB / HBase / ES 等专用存储，Redis 只做热点小数据缓存。

#### 有界队列 / 栈（`queue()` / `stack()`）

原生无界 List API 已移除。List 场景统一走有界结构，容量由 `maxLen`（1～4999）治理。

| 操作                  | 底层命令                                  | 复杂度      | 说明          |
|---------------------|---------------------------------------|----------|-------------|
| `offer` / `push`    | RPUSH + LTRIM（queue）/ RPUSH 拒绝（stack） | O(1)     | 写路径 Lua 原子  |
| `poll` / `pop`      | LPOP / RPOP                           | O(1)     | 单元素弹出       |
| `peek` / `peekTail` | LINDEX                                | O(1)     | 只读不弹出       |
| `drain(count)`      | LPOP count                            | O(count) | count 上限 20 |
| `size`              | LLEN                                  | O(1)     | 当前长度        |

**选型**：语义是「集合/去重」用 `collection()`（Set）；「排行榜」用 `ranking()`（ZSet）；「削峰 FIFO」用 `queue()`；「最近 N 条 LIFO」用 `stack()`。需要消费组 / ACK / 死信用 **StreamMQ**。

### 迁移窗口

`perf.enabled`、`blockForbiddenTiers`、`blockStringPayloadViolations` 在 `2026-12-01` 前必须设为 `true`，到期未迁移会阻止应用启动。建议在截止日期前完成灰度验证。

---

## ❓ 常见问题

### 缓存穿透 / 雪崩 / 击穿 怎么防？

- **穿透**（不存在 key 反复打到 DB）：布隆过滤器 + `*WithLock` + 空值缓存
- **雪崩**（大量 key 同时过期）：TTL 随机偏移 + L2 本地缓存
- **击穿**（热点 key 过期瞬间高并发）：`*WithLock` 方法自动加分布式锁

### 本地锁池和分布式锁什么关系？

本地锁池是 JVM 内的请求合并优化。同一 JVM 内多个线程抢同一把锁时，在 `ConcurrentHashMap` 层面感知到锁状态，不需要每次都打 Redisson。跨 JVM 的互斥仍然由 Redisson FencedLock 保障。对业务代码完全透明。

### `Stream` 消息堆积了怎么办？

Stream MQ 已独立为 `atlas-richie-component-redis-streammq`。增加消费者 `concurrency`、优化 `handle()`、调整 `count`，并通过 streammq 模块的 Actuator 端点监控积压（见 `docs/Redis-Stream-Actuator-结构说明.md`）。

### 迁移窗口报错导致启动失败？

```
[MigrationWindow] violation(s) detected
```

将以下配置设为 `true` 后重启：

```yaml
spring.data.redis.perf:
  enabled: true
  block-forbidden-tiers: true
  block-string-payload-violations: true
```

---

## 📚 专题文档

| 主题                  | 文档                                                   |
|---------------------|------------------------------------------------------|
| L2 / 分布式锁 / 性能守卫 设计 | [Redis-L2与性能守卫设计说明.md](docs/zh/Redis-L2与性能守卫设计说明.md) |
| 缓存核心能力功能分析          | [缓存核心能力功能.md](docs/zh/缓存核心能力功能.md)                   |
