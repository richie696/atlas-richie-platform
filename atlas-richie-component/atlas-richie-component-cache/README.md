# Richie Cache Component

Redis-backed cache and data structure component. Provides KV, Hash, Set/ZSet, distributed locks, bounded queues / stacks, rate limiting, and more through the **`GlobalCache` + Ops accessor** API. L2 caching, Bloom filter, and performance guard are built in.

> **Message queue**: the reliable Stream MQ has been split out into a standalone module, [`atlas-richie-component-redis-streammq`](../atlas-richie-component-redis-streammq), with the entry point `StreamMQ.stream()`. The `queue()` accessor in this module is a bounded buffer for traffic shaping, not a message queue.

> **Audience**: business service developers. If you want to know "what problems this component solves and how to use it", this is the doc for you.
> **Deep design**: see [docs/](./docs/README.md) for the full design notes on L2 caching, distributed locks, and the performance guard.

---

## 📖 Contents

- [🚀 Quick Start](#-quick-start)
  - [1. Add the Dependency](#1-add-the-dependency)
  - [2. Configuration](#2-configuration)
  - [3. Write Code](#3-write-code)
  - [Ops Accessor Reference](#ops-accessor-reference)
- [🔧 Core Features](#-core-features)
  - [String / Number / Boolean (`value()`)](#string--number--booleanvalue)
  - [Hash (`struct()` / `field()`)](#hashstruct--field)
  - [Bounded Queue / Stack (`queue()` / `stack()`)](#bounded-queue--stackqueue--stack)
  - [Set / ZSet (`collection()` / `ranking()`)](#set--zsetcollection--ranking)
  - [Key Management (`key()`)](#key-managementkey)
  - [Distributed Lock (`lock()`)](#distributed-locklock)
  - [Lua / Rate Limit / GEO / HyperLogLog / Bitmap](#lua--rate-limit--geo--hyperloglog--bitmap)
  - [Batch Operations (`value()`)](#batch-operationsvalue)
- [📨 Redis Stream Message Queue (Standalone Module)](#-redis-stream-message-queue-standalone-module)
- [🛡 Advanced Capabilities](#-advanced-capabilities)
  - [Distributed Lock (`lock()`)](#distributed-locklock-1)
    - [Batch Lock (`lock().batch()`)](#batch-locklockbatch)
  - [Cache Stampede Prevention (`getWithLock`)](#cache-stampede-preventiongetwithlock)
  - [L2 Secondary Cache](#l2-secondary-cache)
  - [Bloom Filter](#bloom-filter)
  - [Performance Guard (RedisPerfGuard)](#performance-guardredisperfguard)
  - [Multi-Redis Instance Routing](#multi-redis-instance-routing)
- [📦 Local Cache](#-local-cache)
- [⚙️ Complete Configuration Reference](#-complete-configuration-reference)
  - [1. Redis Connection (`spring.data.redis`)](#1-redis-connectionspringdataredis)
    - [1.1 Multi-Redis Instance Routing (`spring.data.redis.slaves`)](#11-multi-redis-instance-routingspringdataredisslaves)
    - [1.2 Lettuce Extensions (`spring.data.redis.lettuce.*`)](#12-lettuce-extensionsspringdataredislettuce)
      - [1.2.1 Why Lettuce (and not Jedis)](#121-why-lettuce-and-not-jedis)
      - [1.2.2 Connection Pool Configuration (`spring.data.redis.lettuce.pool.*`)](#122-connection-pool-configurationspringdataredislettucepool)
      - [1.2.3 Epoll Connection Mode (Linux High-Performance IO)](#123-epoll-connection-modelinux-high-performance-io)
      - [1.2.4 Keep-Alive Configuration](#124-keep-alive-configuration)
      - [1.2.5 Memory Release Policy (Netty Direct Buffer Management)](#125-memory-release-policynetty-direct-buffer-management)
    - [1.3 RESP Protocol Version (`spring.data.redis.protocol-version`)](#13-resp-protocol-versionspringdataredisprotocol-version)
  - [2. L2 Secondary Cache (`spring.data.redis`)](#2-l2-secondary-cachespringdataredis)
  - [3. Distributed Lock (`spring.data.redis`)](#3-distributed-lockspringdataredis)
  - [4. Performance Guard (`spring.data.redis.perf`)](#4-performance-guardspringdataredisperf)
  - [5. Local Cache (`spring.data.local`)](#5-local-cachespringdatalocal)
    - [6.1 Top-Level Configuration](#61-top-level-configuration)
    - [6.2 Cache Definitions (`spring.data.local.cache-definitions[].*`)](#62-cache-definitionsspringdatalocalcache-definitions)
  - [6. Component-Level Configuration (`platform.cache`)](#6-component-level-configurationplatformcache)
    - [6.1 Bloom Filter (`platform.cache.bloom-filter`)](#61-bloom-filterplatformcachebloom-filter)
  - [7. Stream MQ Configuration (Standalone Module)](#7-stream-mq-configurationstandalone-module)
  - [8. Configuration Overview (By Function)](#8-configuration-overviewby-function)
- [🎯 Best Practices](#-best-practices)
  - [Key Naming Conventions](#key-naming-conventions)
  - [Cache Strategy Selection](#cache-strategy-selection)
  - [Expiration Strategy](#expiration-strategy)
  - [Performance Tuning](#performance-tuning)
  - [Big Key Threshold Reference](#big-key-threshold-reference)
    - [Collection Type Thresholds](#collection-type-thresholds)
    - [String Value Thresholds (aligned with `RedisPerfGuard`)](#string-value-thresholds-aligned-with-redisperfguard)
    - [Key Names](#key-names)
    - [Big Key Detection Methods](#big-key-detection-methods)
    - [Big Key Remediation](#big-key-remediation)
    - [Bounded Queue / Stack (`queue()` / `stack()`)](#bounded-queue--stackqueue--stack-1)
  - [Migration Window](#migration-window)
- [❓ FAQ](#-faq)
  - [How do I prevent cache penetration / avalanche / stampede?](#how-do-i-prevent-cache-penetration--avalanche--stampede)
  - [How does the local lock pool relate to the distributed lock?](#how-does-the-local-lock-pool-relate-to-the-distributed-lock)
  - [What do I do when Stream messages pile up?](#what-do-i-do-when-stream-messages-pile-up)
  - [Startup fails with a migration window error. What now?](#startup-fails-with-a-migration-window-error-what-now)
- [📚 Topic Documents](#-topic-documents)



---

## 🚀 Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

### 2. Configuration

This is the **production-recommended configuration** (with L2 cache, local lock, and performance guard enabled). Copy it, change the Redis address, and you are good to go:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0
      timeout: 3s

      # ---------- L2 secondary cache ----------
      enable-l2-caching: true               # enable L1 local cache
      l2-caching-data: [STRING, HASH]       # route String / Hash through L2

      # ---------- Distributed lock ----------
      enable-local-lock: true               # JVM-local lock pool (on by default)

      # ---------- Performance guard ----------
      perf:
        enabled: true                       # enable runtime checks
        warn-non-o1: true                   # WARN on non-O(1) operations
        toc-soft-ms: 8                      # WARN when latency exceeds 8ms
        toc-hard-ms: 50                     # ERROR when latency exceeds 50ms
        block-forbidden-tiers: false        # canary phase: warn only, do not block
        warn-string-payload-anti-patterns: true
        block-string-payload-violations: false

platform:
  cache:
    bloom-filter:
      enable: false                         # enable when cache penetration is a risk
      type: REDISSON
      expected-insertions: 10000000
      false-probability: 0.001
```

### 3. Write Code

Every capability is reached through **`GlobalCache.<ops>()`**. No bean injection needed: the framework auto-binds `GlobalCacheManager` after Spring starts.

```java
import com.richie.component.cache.GlobalCache;

// KV
GlobalCache.value().set("user:123", "Tom", 3_600_000L);
String name = GlobalCache.value().get("user:123", String.class);

// Hash: whole object / field
GlobalCache.struct().set("user:info:123", user, 3_600_000L);
GlobalCache.field().set("user:1", "name", "Tom");

// Stampede prevention
String cfg = GlobalCache.value().getWithLock("config:pay", 3_600_000L,
        () -> configService.load("pay"));

// Distributed lock
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) {
        orderService.process("123");
    }
}

// Bounded queue (traffic shaping, FIFO, drop oldest when full)
var q = GlobalCache.queue().getOrCreate("order:pending", 1000L, Task.class);
q.offer(task);
Task t = q.poll();

// Keyspace event subscription (requires notify-keyspace-events)
GlobalCache.event().subscribeKeyEvent("__keyevent@0__:expired", listener);

// Reliable message queue: depends on atlas-richie-component-redis-streammq
// StreamMQ.stream().publish("order-events", event);
```

### Ops Accessor Reference

| Accessor                                 | Purpose                            |
|------------------------------------------|------------------------------------|
| `value()`                                | String / Number / Boolean KV, counters |
| `struct()`                               | Hash: store whole objects          |
| `field()`                                | Hash: field-level read / write     |
| `collection()`                           | Set                                |
| `ranking()`                              | ZSet / leaderboard                 |
| `lock()`                                 | Distributed lock                   |
| `queue()` / `stack()`                    | Bounded FIFO / LIFO (not MQ)       |
| `key()`                                  | Expire, delete, inspect type       |
| `script()`                               | Lua atomic scripts                 |
| `limiter()`                              | Sliding-window rate limit          |
| `bitmap()` / `hyperLog()` / `geo()`      | Bitmaps / UV / geo location        |
| `notification()`                         | Pub/Sub publish                    |
| `event()`                                | Keyspace event subscription        |

> All write paths integrate L2 (configurable), the Bloom filter (configurable), and the performance guard (recommended: turn on `perf.enabled=true` in production).

---

## 🔧 Core Features

### String / Number / Boolean (`value()`)

```java
GlobalCache.value().set("key", "value", 10_000L);
String val = GlobalCache.value().get("key", String.class);
GlobalCache.value().setIfAbsent("token", "abc", 60_000L);
GlobalCache.value().set("count", 100, 5_000L);
long n = GlobalCache.value().increment("counter", 1L);
GlobalCache.value().set("flag", true, 3_600_000L);
```

---

### Hash (`struct()` / `field()`)

```java
GlobalCache.field().set("user:1", "name", "Tom");
String name = GlobalCache.field().get("user:1", "name", String.class);
GlobalCache.struct().set("user:2", user, 3_600_000L);
UserInfo cached = GlobalCache.struct().get("user:2", UserInfo.class);
UserInfo loaded = GlobalCache.struct().getWithLock("user:999", UserInfo.class,
        3_600_000L, () -> userRepository.findById("999"));
```

**Note**: prefer Hash (`struct` / `field`) for whole objects. Do not stuff large JSON blobs into a String value.

---

### Bounded Queue / Stack (`queue()` / `stack()`)

The native unbounded List API has been removed. Use `queue()` for FIFO traffic shaping, and `stack()` for "latest N" LIFO.

```java
var queue = GlobalCache.queue().getOrCreate("task:pending", 500L, Task.class);
queue.offer(task);
Task t = queue.poll();
List<Task> batch = queue.drain(10);

var stack = GlobalCache.stack().create("audit:recent", 100L, Event.class);
stack.push(event);
Event latest = stack.peek();
```

> For reliable messaging, use `StreamMQ` from **`atlas-richie-component-redis-streammq`**, not `queue()`.

---

### Set / ZSet (`collection()` / `ranking()`)

```java
GlobalCache.collection().add("tags", "java");
Set<String> tags = GlobalCache.collection().get("tags", String.class);
GlobalCache.ranking().set("rank", "user1", 99.5);
Long rank = GlobalCache.ranking().reverseRank("rank", "user1");
```

---

### Key Management (`key()`)

```java
GlobalCache.key().hasKey("k1");
GlobalCache.key().setExpiredTime("k1", 60_000L);
GlobalCache.key().removeCache("k1");
GlobalCache.key().getExpire("k1");
```

---

### Distributed Lock (`lock()`)

```java
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) { /* ... */ }
}
```

---

### Lua / Rate Limit / GEO / HyperLogLog / Bitmap

```java
GlobalCache.script().eval(script, keys, args, Long.class);
boolean pass = GlobalCache.limiter().tryAcquire("rl:/api/pay", 100, 60);
GlobalCache.geo().add("geo:store", 116.39, 39.9, "beijing");
GlobalCache.hyperLog().add("uv:2025-01-01", "uid1");
GlobalCache.bitmap().set("sign:202501", 1, true);
```

---

## 📨 Redis Stream Message Queue (Standalone Module)

Stream MQ has been split out into **`atlas-richie-component-redis-streammq`**, with `StreamMQ` as the entry point:

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-redis-streammq</artifactId>
</dependency>
```

```java
StreamMQ.stream().publish("order-events", event);
// consumption: @RedisStreamConsumer + see the streammq module README
```

In this module, **`GlobalCache.queue()`** is a bounded List that you pull from actively, used for traffic shaping. When you need consumer groups, ACKs, or dead-letter handling, use StreamMQ.

> The `docs/Redis-Stream-*.md` files in this directory are historical design notes. The current implementation lives in the **streammq module**.

---

### Batch Operations (`value()`)

```java
Map<String, String> data = Map.of("k1", "v1", "k2", "v2");
GlobalCache.value().batchSet(data, 3_600_000L);
Map<String, String> values = GlobalCache.value().getMap(List.of("k1", "k2"),
        new TypeReference<String>() {});
```

---

## 🛡 Advanced Capabilities

### Distributed Lock (`lock()`)

**What problem it solves**: cross-JVM mutual exclusion for shared resources (inventory deduction, order placement, coupon issuance).

**Three-layer architecture**: JVM local lock pool, then reentrant check, then Redisson FencedLock.

```java
// Optimistic lock + watchdog renewal (recommended)
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) {
        orderService.processOrder("123");
    }
}

// Pessimistic lock (blocks until acquired or timed out)
try (var lock = GlobalCache.lock().pessimistic("lock:order:456", 10L)) {
    if (lock.success()) {
        orderService.processOrder("456");
    }
}
```

**Recommended configuration**: `spring.data.redis.enable-local-lock: true` (enabled by default)

#### Batch Lock (`lock().batch()`)

```java
Collection<String> keys = Set.of("lock:stock:1001", "lock:stock:1002", "lock:stock:1003");
try (var lock = GlobalCache.lock().batch(keys, 20L, TimeUnit.MINUTES)) {
    if (!lock.isSuccess()) {
        return ResultVO.getError("获取批量锁失败。");
    }
    stockService.batchDeduct(keys);
}
```

> If any single key fails to acquire, the already-held locks are released immediately to avoid a partial-lock deadlock.

---

### Cache Stampede Prevention (`getWithLock`)

Interception chain: L2, then Bloom filter, then Redis, then distributed lock, then DB.

```java
String value = GlobalCache.value().getWithLock("config:app:pay_ratio",
    3600_000L, () -> configService.getConfig("pay_ratio"));

UserInfo user = GlobalCache.struct().getWithLock("user:profile:123",
    UserInfo.class, 3600_000L, () -> userService.findById("123"));
```

---

### L2 Secondary Cache

Read path: `value().get()` / `struct().get()` -> L1 local -> Redis -> `getWithLock` falls through to DB.

Write path: `set()` -> Redis SET -> dual-write L1 -> keyspace notification invalidates L1 on other instances.

```yaml
spring.data.redis:
  enable-l2-caching: true
  l2-caching-data: [STRING, HASH]   # LIST / SET with care: high data volume stresses the JVM
```

For the deep design see [Redis-L2与性能守卫设计说明.md](docs/zh/Redis-L2与性能守卫设计说明.md).

---

### Bloom Filter

When enabled, `value().getWithLock` automatically consults the Bloom filter to short-circuit "definitely absent" keys and reduce cache penetration.

```yaml
platform.cache.bloom-filter:
  enable: true
  type: REDISSON
  expected-insertions: 10000000
  false-probability: 0.001
```

---

### Performance Guard (RedisPerfGuard)

| Dimension     | Detection                                                              | Optional blocking                            |
|---------------|------------------------------------------------------------------------|----------------------------------------------|
| Complexity    | WARN on non-O(1) operations                                            | `block-forbidden-tiers: true`                 |
| Latency       | WARN over `toc-soft-ms`, ERROR over `toc-hard-ms`                       | —                                            |
| String payload| Collection / Map blobs written as String, oversized JSON               | `block-string-payload-violations: true`       |

`MigrationWindow`-constrained items (`perf.enabled`, and so on) **must** be set to `true` before **2026-12-01**, or startup fails.

---

### Multi-Redis Instance Routing

```yaml
spring.data.redis:
  host: primary.redis.com
  slaves:
    order-redis:
      host: order.redis.com
      port: 6379
```

---

## 📦 Local Cache

JVM-local cache independent from Redis. This is a business-explicit region, not L2. Built on the JSR-107 standard; Caffeine / Ehcache / Cache2k can be swapped as the underlying provider.

**What problem it solves**: business code needs to cache things that do not belong in Redis, such as config items or routing tables. Or business code wants a named cache region separate from the data-type-driven L2.

```yaml
spring.data.local:
  provider: CAFFEINE          # CAFFEINE / EHCACHE / CACHE2K
  cache-definitions:
    - name: appConfig
      expiry-policy: CREATED
      ttl: 300_000             # 5 minutes
    - name: routeTable
      expiry-policy: ACCESSED
      ttl: 3_600_000           # 1 hour
```

Cache2k additionally supports refresh-ahead (asynchronous refresh before expiry):

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

## ⚙️ Complete Configuration Reference

> Below is organized along two dimensions, **config prefix** and **feature**. Every option lists its default, accepted range, and impact on functionality.
> Items marked with ⚠️ are subject to a `@MigrationWindow` constraint. Failing to migrate by the deadline prevents the application from starting.

### 1. Redis Connection (`spring.data.redis`)

| Config         | Type      | Default     | Description                                                                 |
|----------------|-----------|-------------|-----------------------------------------------------------------------------|
| `host`         | String    | `localhost` | Redis host                                                                 |
| `port`         | int       | `6379`      | Redis port                                                                 |
| `password`     | String    | —           | Redis password                                                             |
| `database`     | int       | `0`         | Redis database index                                                       |
| `timeout`      | Duration  | `3s`        | Operation timeout                                                          |
| `server-type`  | enum      | `STANDALONE`| Deployment type: `STANDALONE` / `SENTINEL` / `CLUSTER`                      |
| `slaves`       | Map       | —           | Named child Redis instances (for read-write split / multi-business isolation), see §1.1 below |

#### 1.1 Multi-Redis Instance Routing (`spring.data.redis.slaves`)

```yaml
spring.data.redis:
  host: primary.redis.com       # primary Redis
  port: 6379
  slaves:                       # named child Redis, each with its own connection
    order-redis:
      host: order.redis.com
      port: 6379
      password: order-pass
    user-cache:
      host: user.redis.com
      port: 6380
```

Child Redis instances are routed by name in `MultiRedisTemplate`. Adding or removing a child Redis at runtime requires a restart.

#### 1.2 Lettuce Extensions (`spring.data.redis.lettuce.*`)

> These extension settings exist because Spring Boot's stock Lettuce configuration is not enough for **production**. They cover three dimensions: **connection pool tuning**, **Socket connection mode (epoll)**, and **Netty direct memory management**.

---

##### 1.2.1 Why Lettuce (and not Jedis)

The project defaults to **Lettuce** as the Redis client. Jedis is essentially a direct-connection model (one connection per instance, not thread-safe, must be paired with a connection pool to simulate reuse), whereas Lettuce is a **Netty-based asynchronous driver**. The two differ in important ways:

| Comparison           | Lettuce                                | Jedis                                  |
|----------------------|----------------------------------------|----------------------------------------|
| Thread safety        | ✅ Yes, single-instance reuse          | ❌ No, requires a connection pool      |
| Connection mode      | Async + sync + reactive               | Sync, blocking                         |
| Underlying I/O       | Netty epoll (Linux high-performance)   | BIO / NIO                              |
| Redis cluster        | Native support                        | Requires Sentinel / cluster adapter    |
| Keep-alive (epoll)   | ✅ Supports `TCP_USER_TIMEOUT`        | ❌ JDK `SO_KEEPALIVE` only (coarse-grained) |

Conclusion: Lettuce is strictly better than Jedis for connection reuse, multi-thread concurrency, and Linux kernel feature utilization. Jedis is easier to pick up, but Lettuce is the right call for production.

---

##### 1.2.2 Connection Pool Configuration (`spring.data.redis.lettuce.pool.*`)

> This is the standard connection pool configuration inherited from Spring Boot's `DataRedisProperties.Lettuce`.

Lettuce is asynchronous under the hood, but in sync-call mode it still uses a connection pool to bound the number of concurrent connections. **There is no universal best-value for the pool**, because the optimum depends on Redis server capacity, network bandwidth, and application concurrency. The recommended tuning loop is:

1. Start with a rough estimate of concurrency and peak access from the business side.
2. Pick an initial set of values.
3. Run load tests, and watch for `RedisPool Exhausted` exceptions and connection-wait time.
4. Adjust iteratively until the balance is right.

| Config                              | Type      | Default | Description                                              |
|-------------------------------------|-----------|---------|----------------------------------------------------------|
| `pool.max-active`                   | int       | `8`     | Max active connections (the key tuning knob for load tests) |
| `pool.max-idle`                     | int       | `8`     | Max idle connections                                     |
| `pool.min-idle`                     | int       | `0`     | Min idle connections                                     |
| `pool.max-wait`                     | Duration  | `-1ms`  | Max wait time when acquiring a connection (-1 = forever) |
| `pool.time-between-eviction-runs`   | Duration  | —       | Idle connection eviction thread run interval            |

---

##### 1.2.3 Epoll Connection Mode (Linux High-Performance I/O)

> ⚠️ **This feature is only supported on Linux x86_64 / aarch64 / riscv64 systems.** On non-Linux systems Lettuce emits a warning log and the config is a no-op.

**What is epoll?**

epoll is a Linux kernel **improved I/O multiplexing interface**, the modern replacement for the classic `select` / `poll`. The core differences:

| Feature                            | select / poll                                                | epoll                                                                       |
|------------------------------------|--------------------------------------------------------------|-----------------------------------------------------------------------------|
| **FD upper bound**                 | Limited (default 2048, must recompile the kernel to expand)  | None (only limited by `/proc/sys/fs/file-max`; ~100K per 1GB of memory)     |
| **Scaling with connection count**  | Linear scan of all FDs; the more connections, the slower    | Only "active" FDs are touched; idle ones are not scanned                    |
| **Kernel communication**           | User-space ↔ kernel-space message copy                       | **mmap shared memory**, no user/kernel data copy                            |
| **Trigger mode**                   | Level-triggered only (notifies until state changes)         | Level-triggered + **edge-triggered** (notifies once on state change)        |
| **Use cases**                      | Few active connections                                       | Many connections + few active (typical WAN scenarios)                       |

**Why does this matter?**

In production, a single microservice instance may need to maintain dozens to hundreds of Redis connections simultaneously (primary Redis + Sentinels + child Redis instances). With `select` / `poll`, every I/O multiplexing pass has to scan all FDs linearly, even when the vast majority are idle. epoll only fires callbacks on active connections; idle ones do not consume CPU. In WAN scenarios (lots of idle + a few active), **epoll is dramatically more efficient than select / poll**.

---

##### 1.2.4 Keep-Alive Configuration

**Problem solved**: long-lived Redis connections traverse cloud load balancers (SLB / ALB / NLB), NAT gateways, and firewalls. These devices have idle-timeout policies (typically 60 to 300 seconds). Once a connection is idle longer than the device timeout, the device **silently closes the TCP connection** and the client has no idea. The next request discovers the dead connection, triggering a rebuild. In the worst case, this drains the connection pool.

**Why extend the default?**
- JDK standard `SO_KEEPALIVE` is just a boolean on/off, with no fine control over `idle`, `interval`, or `count`.
- Linux sysctl (`/proc/sys/net/ipv4/tcp_keepalive_time`) affects every process on the host.
- Netty epoll native transport supports per-connection `TCP_KEEPIDLE`, `TCP_KEEPINTVL`, `TCP_KEEPCNT`, and **`TCP_USER_TIMEOUT` (RFC 5482)**.

**What is `TCP_USER_TIMEOUT`?**

A TCP option defined in RFC 5482, it specifies how long TCP waits for the peer's ACK before forcibly closing the connection. It is more reliable than keep-alive probes alone: keep-alive probes only check whether the connection is reachable, whereas `TCP_USER_TIMEOUT` also checks **whether data has been acknowledged by the peer**. Recommended formula:
```
tcpUserTimeout ≈ idle + interval × count
```

**Production-recommended configuration**:

```yaml
spring:
  data:
    redis:
      lettuce:
        keep-alive:
          enabled: true                             # enable keep-alive (Linux only)
          count: 6                                  # 6 consecutive missed responses = dead
          idle: 90                                  # start probing after 90s of idle
          idle-unit: seconds
          interval: 60                              # probe interval 60s
          interval-unit: seconds
          tcp-user-timeout: 450                     # TCP user timeout (RFC 5482)
          tcp-user-timeout-unit: seconds             # 90 + 6 * 60 = 450 seconds
```

**Physical meaning of each parameter**:

| Parameter           | Meaning                                              | Default | Prod. recommended | Notes                                       |
|---------------------|------------------------------------------------------|---------|-------------------|---------------------------------------------|
| `idle`              | Idle time before keep-alive probes start             | 2h      | 90s               | Linux kernel default of 2h is too long for cloud |
| `interval`          | Interval between consecutive probes                  | 75s     | 60s               | Too dense adds server load; too sparse delays detection |
| `count`             | Consecutive failures before declaring the connection dead | 9   | 6                 | Combined with `interval` to compute total detection time |
| `tcp-user-timeout`  | Max time TCP waits for peer ACK                      | 7875s   | 450s              | Recommended ≈ idle + interval × count       |

**Non-Linux fallback**: on non-Linux systems epoll is unavailable, the keep-alive config is not applied to the socket channel, Lettuce emits a ⚠️ warning log, and the program continues to run.

---

##### 1.2.5 Memory Release Policy (Netty Direct Buffer Management)

**Problem solved**: Lettuce runs on Netty, and Netty uses a **reference-counted direct buffer pool**. Pooling reduces allocation / release overhead, but it has a cost: **the pool only grows, it does not shrink on its own**. Direct buffers Netty allocates during traffic peaks do not return to the OS during off-peak hours. They pile up in the pool, and once `-XX:MaxDirectMemorySize` (default = `-Xmx`) is hit, the JVM throws `OutOfMemoryError: Direct buffer memory`.

```yaml
spring:
  data:
    redis:
      lettuce:
        memory-release-policy: USAGE_RADIO    # memory release policy (default)
        memory-release-ratio: 3               # trigger release at 75% utilization (default)
```

**Allowed values for `memory-release-policy`**:

| Policy                       | Behavior                                                              | Use case                          |
|------------------------------|-----------------------------------------------------------------------|-----------------------------------|
| `USAGE_RADIO` (default)      | Release old buffers when pool utilization reaches `ratio/(ratio+1)`   | General scenarios, balanced CPU and memory |

**Impact of `memory-release-ratio`** (only meaningful for `USAGE_RADIO`):

| ratio        | Trigger threshold | Memory tendency | CPU tendency                              | Recommended scenario                |
|--------------|-------------------|-----------------|-------------------------------------------|-------------------------------------|
| 1            | 50%               | Low             | Higher CPU (frequent release / re-alloc)   | Low QPS, memory-sensitive           |
| 3 (default)  | 75%               | Medium          | Medium                                    | General scenarios                   |
| 5            | 83%               | Higher          | Lower                                     | High-QPS services                   |
| 10           | 90%               | High            | Low                                       | Very high QPS, CPU-sensitive        |

**Benefits**:
- Direct memory converges during off-peak, avoiding `OutOfDirectMemoryError`.
- High-QPS services use a high ratio (less release / re-alloc CPU overhead); low-QPS services use a low ratio (memory savings).
- Tuned via config rather than indirect JVM flags.

---

#### 1.3 RESP Protocol Version (`spring.data.redis.protocol-version`)

| Value   | Applies to  | Characteristics                                                              |
|---------|-------------|------------------------------------------------------------------------------|
| `RESP2` | Redis 2 to 5| Legacy protocol                                                              |
| `RESP3` | Redis 6+    | Better performance, client-side caching, avoids double conversion of response data |

Redis 6+ strongly recommends RESP3. If Redis has the `HELLO` command disabled, fall back to RESP2 to avoid connection failures.

---

### 2. L2 Secondary Cache (`spring.data.redis`)

| Config                | Type                | Default | Related feature   | Description                                                                                  |
|-----------------------|---------------------|---------|-------------------|----------------------------------------------------------------------------------------------|
| `enable-l2-caching`   | boolean             | `false` | L2 secondary cache | Whether to enable the L1 local cache                                                         |
| `l2-caching-data`     | List<KeyTypeEnum>   | `[]`    | L2 secondary cache | Which data types go through L2. Options: `STRING` / `HASH` / `LIST` / `SET`                  |

> TTLs automatically get a 1 to 10 minute random offset to prevent cache avalanche. When `enable-l2-caching=false`, the config has no effect, but the local-cache lookup path used by `*WithLock` still works.

**Multi-instance consistency**: depends on Redis keyspace notifications

```bash
# Required on the Redis server
notify-keyspace-events KEA
```

There is a short dirty-read window: between the Redis write and the moment the notification arrives, cross-instance L1 still holds the old value. It is recommended to keep L1 TTL at no more than `original TTL × 2`.

---

### 3. Distributed Lock (`spring.data.redis`)

| Config                | Type      | Default                 | Related feature | Description                                                                                            |
|-----------------------|-----------|-------------------------|-----------------|--------------------------------------------------------------------------------------------------------|
| `enable-local-lock`   | boolean   | **`true`** ⚠️ enabled by default | JVM local lock pool | Within the same JVM, the same lock key is resolved through a `ConcurrentHashMap` lookup. If disabled, every call goes directly to Redisson. |

> **When to consider disabling it**: in extreme cases with very many lock keys, a very low hit rate, and `ConcurrentHashMap` memory bloat. This almost never happens in real workloads.

---

### 4. Performance Guard (`spring.data.redis.perf`)

> Related feature: [Performance Guard](#performance-guardredisperfguard). ⚠️ = `@MigrationWindow` constraint. The marked flags must be set to `true` before 2026-12-01, or the application fails to start.

| Config                                    | Type           | Default               | Description                                                                                              |
|-------------------------------------------|----------------|-----------------------|----------------------------------------------------------------------------------------------------------|
| `enabled` ⚠️                              | boolean        | `false`               | Master switch. When off, the entire perf governance is disabled                                          |
| `warn-non-o1`                             | boolean        | `true`                | WARN on non-O(1) operations                                                                              |
| `toc-soft-ms`                             | long           | `8`                   | Soft threshold (ms); over this, WARN                                                                     |
| `toc-hard-ms`                             | long           | `50`                  | Hard threshold (ms); over this, ERROR                                                                    |
| `block-forbidden-tiers` ⚠️                | boolean        | `false`               | Block operations whose complexity is `LINEAR_N` or worse (throw an exception)                           |
| `log-big-key-probe-hints`                 | boolean        | `true`                | When non-O(1) is detected, output BIGKEY probe hints                                                     |
| `toc-allowed-complexities`                | List<String>   | `[]`                  | Complexity allow-list (`O1` / `LOG_N` / `SCRIPT_OR_UNKNOWN`); when non-empty, anything outside is ERROR |
| `warn-string-payload-anti-patterns`       | boolean        | `true`                | Detect anti-patterns on String writes (Collection / Map blobs, oversized JSON)                           |
| `warn-json-like-string-blob`              | boolean        | `true`                | Enable the JSON-shape heuristic                                                                          |
| `json-like-min-chars-for-warn`            | int            | `128`                 | JSON blob WARN threshold (whitespace-stripped character count)                                          |
| `string-payload-max-chars-warn`           | int            | `100_000`             | String character-count WARN threshold                                                                    |
| `string-payload-max-chars-error`          | int            | `1_000_000`           | String character-count ERROR threshold                                                                   |
| `string-payload-max-bytes-warn`           | int            | `262_144` (256KB)     | byte[] write WARN threshold                                                                              |
| `string-payload-max-bytes-error`          | int            | `1_048_576` (1MB)     | byte[] write ERROR threshold                                                                             |
| `block-string-payload-violations` ⚠️     | boolean        | `false`               | Block String payload violations (throw an exception)                                                     |

**Production-recommended** (canary done):

```yaml
spring.data.redis.perf:
  enabled: true
  block-forbidden-tiers: true
  block-string-payload-violations: true
```

---

### 5. Local Cache (`spring.data.local`)

> Related feature: [Local Cache](#-local-cache). A business-explicit region, independent from L2, built on JSR-107.

#### 6.1 Top-Level Configuration

| Config               | Type                   | Default   | Description                                                                |
|----------------------|------------------------|-----------|----------------------------------------------------------------------------|
| `provider`           | enum                   | `EHCACHE` | Underlying implementation: `EHCACHE` / `CAFFEINE` / `CACHE2K`              |
| `cache-definitions`  | Set<CacheDefinition>   | `[]`      | List of cache definitions. See §6.2 for details                            |

#### 6.2 Cache Definitions (`spring.data.local.cache-definitions[].*`)

Each `CacheDefinition` defines an independent local cache region (distinguished by `name`):

| Config                          | Type       | Default             | Description                                                                                                  |
|---------------------------------|------------|---------------------|--------------------------------------------------------------------------------------------------------------|
| `name`                          | String     | —                   | Cache region name (required; business looks up by name)                                                      |
| `expiry-policy`                 | enum       | —                   | Expiry policy: `CREATED` / `ACCESSED` / `ETERNAL` / `MODIFIED` / `TOUCHED`                                  |
| `expiry`                        | int        | `5`                 | Expiry duration value (no effect when `ETERNAL`)                                                            |
| `expiry-unit`                   | TimeUnit   | `MINUTES`           | Expiry duration unit (no effect when `ETERNAL`)                                                             |
| `statistics-enabled`            | boolean    | `false`             | Enable hit-rate / operation-count statistics                                                                 |
| `store-by-value`                | boolean    | `false`             | `true` stores by value (deep copy); `false` stores by reference                                              |
| `read-through`                  | boolean    | `false`             | Enable read-through; on miss, call CacheLoader to load (requires `cache-loader-class-name`)                  |
| `cache-loader-class-name`       | String     | —                   | Fully qualified class name of `javax.cache.integration.CacheLoader`                                        |
| `write-through`                 | boolean    | `false`             | Enable write-through; on put/remove, call CacheWriter (requires `cache-writer-class-name`)                  |
| `cache-writer-class-name`       | String     | —                   | Fully qualified class name of `javax.cache.integration.CacheWriter`                                        |
| `cache2k-refresh-ahead`         | boolean    | `false`             | **CACHE2K only**: refresh asynchronously before expiry (requires `read-through=true`)                       |
| `cache2k-loader-thread-count`   | Integer    | cache2k default     | **CACHE2K only**: number of background refresh threads                                                      |

**Example**:

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
      provider: CACHE2K          # per-cache provider (must be added to CacheDefinition)
      expiry-policy: ACCESSED
      expiry: 30
      expiry-unit: SECONDS
      read-through: true
      cache-loader-class-name: com.example.MyCacheLoader
      cache2k-refresh-ahead: true
      cache2k-loader-thread-count: 2
```

---

### 6. Component-Level Configuration (`platform.cache`)

| Config           | Type   | Default | Related feature | Description                          |
|------------------|--------|---------|-----------------|--------------------------------------|
| `cache-provider` | enum   | `REDIS` | Global choice   | Current implementation: `REDIS` only |
| `bloom-filter.*` | —      | —       | Bloom filter    | See §6.1 for details                 |

#### 6.1 Bloom Filter (`platform.cache.bloom-filter`)

> Related feature: [Bloom Filter](#bloom-filter). When `enable=false`, the rest of the config has no effect.

| Config                 | Type      | Default                           | Description                                                                                  |
|------------------------|-----------|-----------------------------------|----------------------------------------------------------------------------------------------|
| `enable`               | boolean   | `false`                           | Master switch                                                                                |
| `type`                 | enum      | `REDISSON`                        | Implementation: `REDISSON` (shared across instances) / `GUAVA` (single instance, zero network) |
| `key`                  | String    | `platform:cache:bloom:global`     | Bloom data key (Redisson mode)                                                               |
| `expected-insertions`  | long      | `10_000_000`                      | Expected number of insertions (recommend ~20% to 50% above the actual maximum)              |
| `false-probability`    | double    | `0.001`                           | False-positive rate (0.001 = 0.1%; lowest 0.0001 for very low false positives)              |

**Memory estimate** (formula: `bits ≈ -n × ln(p) / (ln 2)²`):

| n             | p     | Memory  |
|---------------|-------|---------|
| 1_000_000     | 0.001 | 1.8 MB  |
| 10_000_000    | 0.001 | 18 MB   |
| 100_000_000   | 0.001 | 180 MB  |

---

### 7. Stream MQ Configuration (Standalone Module)

Consumer, idempotent dedup, monitoring, and tracing config have all moved to **`atlas-richie-component-redis-streammq`**. This module no longer binds `platform.cache.redis.stream.*` or `spring.data.redis.stream-idempotency`.

- Dependency and usage: see [Redis Stream Message Queue (Standalone Module)](#-redis-stream-message-queue-standalone-module) above
- Historical reference: [docs/Redis-Stream-使用指南.md](../atlas-richie-component-redis-streammq/docs/Redis-Stream-使用指南.md) (refer to the streammq source for the actual implementation)

---

### 8. Configuration Overview (By Function)

> Not sure where to look? Use this table to navigate in reverse.

| What you want to change  | Config prefix                                | Section |
|--------------------------|----------------------------------------------|---------|
| Redis connection         | `spring.data.redis`                          | §1      |
| Multi-Redis instances    | `spring.data.redis.slaves`                   | §1.1    |
| Lettuce tuning           | `spring.data.redis.lettuce`                  | §1.2    |
| L2 secondary cache       | `spring.data.redis.enable-l2-caching`        | §2      |
| JVM local lock pool      | `spring.data.redis.enable-local-lock`        | §3      |
| Performance guard        | `spring.data.redis.perf`                     | §4      |
| Local cache              | `spring.data.local`                          | §5      |
| Bloom filter             | `platform.cache.bloom-filter`                | §6.1    |
| Stream MQ                | streammq module `platform.cache.redis.stream.*` | §7   |

---

## 🎯 Best Practices

### Key Naming Conventions

```
{业务}:{object}:{id}:{field}
user:info:123
order:status:456
lock:order:789
```

### Cache Strategy Selection

| Scenario                       | Recommended approach                  | Configuration                                       |
|--------------------------------|---------------------------------------|-----------------------------------------------------|
| Hot KV, read-heavy, write-light | L2 + stampede prevention              | `enable-l2-caching: true` + `*WithLock`             |
| High-concurrency lock contention | Distributed lock + local lock       | `enable-local-lock: true` (default)                 |
| Large list cache               | `*WithLock` for dedup                 | Do not route through L2; use stampede prevention     |
| Leaderboard                    | ZSet                                  | No extra config                                     |
| Dedup stats (large)            | HyperLogLog                           | 12KB for 2^64 elements                              |
| Dedup stats (small)            | Set                                   | O(1) membership check                               |
| Rate limiting                  | `limiter().tryAcquire`                | Atomic sliding-window Lua                           |
| Nearby search                  | `geo()`                               | No extra config                                     |
| Sign-in / check-in             | `bitmap()`                            | ~12MB per day for 100M users                        |
| Traffic shaping                | `queue()` / `stack()`                 | Bounded list, not MQ                                |
| Reliable MQ                    | `StreamMQ` (standalone module)        | Consumer group / ACK / DLQ                          |

### Expiration Strategy

- **Hot data**: short TTL (seconds), paired with L2 read amplification.
- **Config data**: medium TTL (minutes).
- **Base data**: long TTL (hours), with a random offset to prevent avalanche.

### Performance Tuning

- Hot reads must turn on L2 + `*WithLock` to keep Redis from becoming a bottleneck.
- Use batch operations instead of looping per-key reads and writes; this cuts down on network RTT.
- Roll out `perf.enabled` in pre-production until all `[RedisPerf]` warnings are gone, then promote to production.
- Avoid `KEYS *` and full `HGETALL` / `SMEMBERS` on ToC core paths.

### Big Key Threshold Reference

Big keys cause Redis main-thread blocking, replication lag, AOF rewrite stalls, cluster migration hangs, and similar issues. The thresholds below combine **Redis internal encoding switch-over points** with **the component's performance guard defaults**, aligned and cross-validated.

#### Collection Type Thresholds

| Type   | Encoding threshold                                 | Recommended upper bound | Impact of crossing the line                          | Typical dangerous commands                                                                                                                                       |
|--------|----------------------------------------------------|-------------------------|------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| list   | 512 elements (quicklist node switch)               | **5,000** elements      | Nodes fall back from ziplist to linked list; per-node memory grows | `LRANGE 0 -1` (O(N) full scan); **Lists have no de-duplication, so any dedup scheme is O(N) and must use a companion Set to filter on write**                |
| set    | 512 elements (intset → hashtable)                  | **5,000** elements      | Memory grows 2x to 3x, rehash stalls                | `SMEMBERS` (O(N) full scan)                                                                                                                                      |
| zset   | 128 elements (ziplist → skiplist)                  | **5,000** elements      | Memory grows 2x to 3x                                | `ZRANGE 0 -1` (O(N)), `ZREMRANGEBYRANK` (O(log N + M))                                                                                                          |
| hash   | 512 fields (ziplist → hashtable)                   | **1,000** fields        | Memory grows 2x to 3x, rehash stalls                | `HGETALL` (O(N)), `HKEYS` (O(N))                                                                                                                                 |

> **Bounded Queue / Stack** (not Stream MQ): `queue()` is FIFO with **active pull**, suitable for traffic shaping and lightweight buffering; `stack()` is LIFO, refusing pushes when full. Neither is a replacement for the [StreamMQ standalone module](#-redis-stream-message-queue-standalone-module). `maxLen` must be in **1 to 4,999**; the default is immutable, and `grow()` doubles it only once up to the cap. On creation, the Redis type is validated to be `none` or `list`. The `count` of `drain` is **1 to 20**; the read path logs **WARN** on deserialization failure (skips bad elements in batch mode; `poll` / `pop` already popped but failed elements return `null`).

> **Note**: the "encoding threshold" is the point at which Redis's internal encoding switches from compact to relaxed, and crossing it causes a step change in memory and CPU cost. The "recommended upper bound" is a heuristic safety margin on top of the compact encoding; above this, full-scan commands show noticeably higher latency. The actual red line is the **smaller** of the two.

#### String Value Thresholds (aligned with `RedisPerfGuard`)

| Level  | Character threshold       | Byte threshold            | Config                                                                  | Behavior                                                                                            |
|--------|---------------------------|---------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| OK     | < 100,000 (~100KB)        | < 256KB                   | —                                                                       | Normal write                                                                                        |
| WARN   | ≥ 100,000                 | ≥ 262,144 (256KB)         | `string-payload-max-chars-warn` / `string-payload-max-bytes-warn`       | Emit WARN log                                                                                       |
| ERROR  | ≥ 1,000,000 (~1MB)        | ≥ 1,048,576 (1MB)         | `string-payload-max-chars-error` / `string-payload-max-bytes-error`     | Emit ERROR log; if `block-string-payload-violations=true`, throw an exception and block the write   |

In addition, `RedisStringPayloadInspector` also detects the following anti-patterns and immediately reports ERROR:
- Serializing a whole `Collection` / `Map` / array into a String (use Hash / List / ZSet instead).
- Stuffing an arbitrary JavaBean as a whole-JSON blob into a single key (prefer Hash field modeling).
- Strings starting with `{` or `[` and longer than or equal to 128 characters, which look like JSON blobs (WARN).

#### Key Names

| Item               | Recommendation                                                                |
|--------------------|-------------------------------------------------------------------------------|
| Key length         | < 128 bytes (Redis limit is 512MB, but long keys add memory and comparison overhead) |
| Naming convention  | `{business}:{entity}:{id}`, avoid spaces and special characters                |

#### Big Key Detection Methods

| Method                                                                              | Use case                                                                                                                                                                                                                                |
|-------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `redis-cli --bigkeys`                                                               | Offline scan; quickly locate Top N big keys                                                                                                                                                                                              |
| `MEMORY USAGE key`                                                                  | Online check of single-key memory usage (Redis 4.0+)                                                                                                                                                                                     |
| `redis-cli --scan --pattern '*' \| xargs -I{} redis-cli MEMORY USAGE {}`            | Bulk scan (use with care; sample in production)                                                                                                                                                                                          |
| Component-built-in `RedisPerfGuard`                                                  | Runtime auto-detection: non-O(1) calls log WARN + BIGKEY probe hints (HLEN / LLEN / SCARD / ZCARD); controlled by `spring.data.redis.perf.log-big-key-probe-hints=true`                                                                  |
| Slow query log `SLOWLOG GET`                                                         | Find suspicious operations with latency ≥ `toc-hard-ms` (default 50ms)                                                                                                                                                                   |

#### Big Key Remediation

- **Use `UNLINK` rather than `DEL`**: `GlobalCache.key().removeCache()` already uses `UNLINK` internally (asynchronous memory release), avoiding main-thread blocking. Business code must not call `DEL` directly.
- **Sharding instead of a single key**: split a large Hash into `key:shard:{0..N}`; large Lists into multiple segments; large Sets by business dimension into buckets.
- **Incremental scans**: use `HSCAN` / `SSCAN` / `ZSCAN` in place of `HGETALL` / `SMEMBERS` / `ZRANGE 0 -1`, taking bounded batches each time.
- **Move out of Redis**: any single key consistently exceeding the limit (> 10MB or > 50,000 elements) should migrate to MongoDB / HBase / ES, leaving Redis to do the hot small-data caching.

#### Bounded Queue / Stack (`queue()` / `stack()`)

The native unbounded List API has been removed. List scenarios now go through the bounded structure, with capacity governed by `maxLen` (1 to 4,999).

| Operation             | Underlying command                                    | Complexity | Description                            |
|-----------------------|-------------------------------------------------------|------------|----------------------------------------|
| `offer` / `push`      | RPUSH + LTRIM (queue) / RPUSH reject (stack)          | O(1)       | Atomic Lua write path                  |
| `poll` / `pop`        | LPOP / RPOP                                           | O(1)       | Single-element pop                     |
| `peek` / `peekTail`   | LINDEX                                                | O(1)       | Read-only, no pop                      |
| `drain(count)`        | LPOP count                                            | O(count)   | `count` upper bound 20                 |
| `size`                | LLEN                                                  | O(1)       | Current length                         |

**How to pick**: use `collection()` (Set) for "set / dedup" semantics, `ranking()` (ZSet) for "leaderboard", `queue()` for "FIFO traffic shaping", and `stack()` for "latest N, LIFO". For consumer group / ACK / DLQ, use **StreamMQ**.

### Migration Window

`perf.enabled`, `blockForbiddenTiers`, and `blockStringPayloadViolations` must be set to `true` before `2026-12-01`. Failing to migrate by the deadline prevents the application from starting. Complete the canary validation before the cutoff.

---

## ❓ FAQ

### How do I prevent cache penetration / avalanche / stampede?

- **Penetration** (non-existent keys repeatedly hit the DB): Bloom filter + `*WithLock` + null-value caching.
- **Avalanche** (many keys expire at once): TTL random offset + L2 local cache.
- **Stampede** (high concurrency on a hot key at the moment it expires): the `*WithLock` methods automatically take a distributed lock.

### How does the local lock pool relate to the distributed lock?

The local lock pool is a JVM-internal request-coalescing optimization. When multiple threads within the same JVM compete for the same lock, they observe the lock state at the `ConcurrentHashMap` level and do not have to go through Redisson every time. Cross-JVM mutual exclusion is still guaranteed by Redisson FencedLock. Completely transparent to business code.

### What do I do when Stream messages pile up?

Stream MQ has been split out into `atlas-richie-component-redis-streammq`. Increase the consumer `concurrency`, optimize `handle()`, tune `count`, and monitor backlog via the streammq module's Actuator endpoint (see `docs/Redis-Stream-Actuator-结构说明.md`).

### Startup fails with a migration window error. What now?

```
[MigrationWindow] violation(s) detected
```

Set the following config to `true` and restart:

```yaml
spring.data.redis.perf:
  enabled: true
  block-forbidden-tiers: true
  block-string-payload-violations: true
```

---

## 📚 Topic Documents

| Topic                                       | Document                                                                                                                                  |
|---------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| L2 / Distributed Lock / Performance Guard design | [Redis-L2-and-Performance-Guard-Design.md](docs/en/Redis-L2-and-Performance-Guard-Design.md)                                                  |
| Cache core capabilities analysis            | [Cache-Core-Capabilities.md](docs/en/Cache-Core-Capabilities.md)                                                                              |

