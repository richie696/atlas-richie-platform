# Cache Module — Cache Core Capabilities Analysis

> Analysis date: 2026-06-05
> Scope: core cache capabilities (excludes Redis Stream MQ; includes the `queue()` / `stack()` bounded List structures)
> Code version: atlas-richie-component-cache

---

## Contents

1. [KV / String Storage](#1-kv--string-storage)
2. [Hash Operations](#2-hash-operations)
3. [Bounded List (queue / stack)](#3-bounded-list-queue--stack)
4. [Set Operations](#4-set-operations)
5. [ZSet Operations](#5-zset-operations)
6. [Key Management](#6-key-management)
7. [Batch Operations](#7-batch-operations)
8. [Distributed Lock](#8-distributed-lock)
9. [Cache Stampede Prevention (Locked Read with Source Fallback)](#9-cache-stampede-prevention-locked-read-with-source-fallback)
10. [L2 Secondary Cache](#10-l2-secondary-cache)
11. [Bloom Filter](#11-bloom-filter)
12. [Performance Guard](#12-performance-guard)
13. [Multi-Redis Instance Routing](#13-multi-redis-instance-routing)
14. [Lua Scripts](#14-lua-scripts)
15. [Rate Limiting](#15-rate-limiting)
16. [Geographic Location GEO](#16-geographic-location-geo)
17. [HyperLogLog](#17-hyperloglog)
18. [Bitmap](#18-bitmap)
19. [Local Cache (Independent from L2)](#19-local-cache-independent-from-l2)
20. [Pub/Sub and Keyspace Notification](#20-pubsub-and-keyspace-notification)
21. [Migration Window Validation](#21-migration-window-validation)
22. [Bounded Queue queue()](#22-bounded-queue-queue)
23. [Bounded Stack stack()](#23-bounded-stack-stack)
24. [Feature Summary](#24-feature-summary)

---

## 1. KV / String Storage

### Design Rationale

Build a business-oriented wrapper over the Redis String data structure, packaging native `SET/GET/INCR/DECR` commands into clean Java static methods while integrating the L2 local cache, Bloom filter, and performance guard.

Layered capability flow:

```
GlobalCache.value().set(k, v, ttl)
  ├── RedisPerfGuard.checkStringWritePayload()   ← anti-pattern check before write
  ├── L2 local cache write (if enabled)
  └── Redis SET
```

### Problems Solved

- Business code that talks to `RedisTemplate` directly has to handle serialization, deserialization, key composition, and exception plumbing as boilerplate.
- Different developers each writing their own Redis utility classes leads to scattered, inconsistent usage.
- Inconsistent write governance (big-key detection, String anti-pattern detection, slow-query monitoring).
- Repeated serialization and deserialization for numeric and boolean types.

### Configuration

```yaml
spring:
  data:
    redis:
      # Basic connection: inherits from Spring Boot DataRedisProperties
      host: localhost
      port: 6379
      password:
      database: 0
      timeout: 3s

      # L2 secondary cache (read path checks the local cache first)
      enable-l2-caching: true
      l2-caching-data:
        - STRING          # String types go through L2
        - HASH

      # Performance guard
      perf:
        enabled: true
        warn-string-payload-anti-patterns: true   # detect String value anti-patterns
        string-payload-max-chars-warn: 100000     # WARN threshold by char count
        string-payload-max-chars-error: 1000000   # ERROR threshold by char count
        block-string-payload-violations: true     # block writes that exceed the threshold
```

### Recommended Configuration

```yaml
spring.data.redis:
  enable-l2-caching: true
  l2-caching-data: [STRING, HASH]
  perf:
    enabled: true
    warn-string-payload-anti-patterns: true
    block-string-payload-violations: true        # recommended in production to block big keys
```

### Design Wins

- **Unified facade**: all String operations go through `GlobalCache`, never scattered across business code.
- **Governance instrumentation**: natural write-time governance point that prevents big keys.
- **L2 acceleration**: with L2 enabled, the read path hits the local cache first, cutting Redis network overhead.
- **Transparent complexity**: each method is annotated with its time complexity so callers understand the performance impact.

### Test Cases

```java
// Basic write/read
GlobalCache.value().set("test:key", "hello", 60_000L);
String val = GlobalCache.value().get("test:key", String.class);
assert "hello".equals(val);

// Numeric operation with expiry
GlobalCache.value().set("test:counter", 100, 60_000L);
long newVal = GlobalCache.value().increment("test:counter", 1L);
assert newVal == 101;

// Conditional write (only when the key is absent)
boolean added = GlobalCache.value().setIfAbsent("test:token", "abc", 60_000L);
assert added;
boolean notAdded = GlobalCache.value().setIfAbsent("test:token", "xyz", 60_000L);
assert !notAdded;
```

---

## 2. Hash Operations

### Design Rationale

Wrap the full set of Redis Hash operations, supporting:

- Single-field read/write (`hset` / `hget`)
- Whole-object access (`putAll` / `entries`)
- Batch read
- Locked source fallback (cache stampede prevention)

### Problems Solved

- Hashes are a natural fit for structured objects, but reading or writing the whole object via JSON in a String is an anti-pattern (no partial update, big-key risk).
- Hash field-level storage enables partial reads and updates.
- Cache stampede: under high concurrency, many requests for a non-existent key at once all fall through to the DB.

### Configuration

Shares the `spring.data.redis.*` configuration with String. Hash-specific options:

```yaml
spring.data.redis:
  l2-caching-data:
    - HASH               # Hash type enables L2 local cache
  perf:
    warn-string-payload-anti-patterns: true  # detect large objects stuffed into a Hash field
```

### Recommended Configuration

```yaml
spring.data.redis.enable-l2-caching: true
spring.data.redis.l2-caching-data: [STRING, HASH]
```

### Design Wins

- **Object-friendly**: `struct().set` / `struct().get` serialize and deserialize Java objects automatically.
- **Partial updates**: `field().set(key, field, value)` updates a single field without re-serializing the whole object.
- **Key-level TTL**: each key expires as a whole, in line with the Hash structure.
- **Stampede prevention**: `struct().getWithLock` ensures only one thread falls back to the source on a cache miss.

### Test Cases

```java
// Single-field operations
GlobalCache.field().set("user:1", "name", "Tom");
GlobalCache.field().set("user:1", "age", 25);
String name = GlobalCache.field().get("user:1", "name", String.class);
assert "Tom".equals(name);

// Object operations
UserInfo user = new UserInfo("Tom", 25);
GlobalCache.struct().set("user:2", user, 3600_000L);
UserInfo cached = GlobalCache.struct().get("user:2", UserInfo.class);
assert "Tom".equals(cached.getName());

// Locked source fallback
UserInfo result = GlobalCache.struct().getWithLock(
    "user:999", UserInfo.class, 3600_000L,
    () -> userRepository.findById("999")
);
```

---

## 3. Bounded List (`queue()` / `stack()`)

### Design Rationale

The native unbounded List API has been removed. List scenarios are served uniformly by **`GlobalCache.queue()`** (FIFO traffic shaping) and **`GlobalCache.stack()`** (LIFO, latest N entries), with capacity governed by `maxLen` (1 to 4999).

> **Boundary with Stream MQ**: bounded queues and stacks are **actively pulled**, with no consumer group, no ACK, and no dead-letter handling. They are **not** message queues. For reliable delivery, use the standalone **`StreamMQ`** module (`atlas-richie-component-redis-streammq`). See [§22](#22-bounded-queue-queue) / [§23](#23-bounded-stack-stack).

### Problems Solved

- Unbounded Lists grow without limit under high concurrency → big keys, blocking, memory risk.
- Need predictable traffic-shaping buffering: drop the head of the queue when full (`queue`) or reject pushes (`stack`).
- Lightweight async channel when PaaS Redis does not support the full Stream feature set (accepts active pull and at-most-once semantics).

### Configuration

Shares the `spring.data.redis.*` connection with Redis; no independent switch. Capacity is decided at creation time by `maxLen`.

### Recommended Configuration

```yaml
spring.data.redis:
  host: localhost
  port: 6379
  perf:
    enabled: true    # recommended in production to surface abnormal List operations
```

### Design Wins

- **Hard capacity ceiling**: companion meta key + Lua-atomic write path.
- **Clear semantics**: JDK Queue / Stack style API (`offer`/`poll`, `push`/`pop`).
- **Batched pull**: `drain(count)` caps at 20 to control per-call Redis latency.

### Test Cases

```java
var queue = GlobalCache.queue().getOrCreate("task:pending", 500L, Task.class);
queue.offer(new Task("t1"));
Task t = queue.poll();
List<Task> batch = queue.drain(10);

var stack = GlobalCache.stack().create("audit:recent", 100L, Event.class);
stack.push(new Event("login"));
Event latest = stack.peek();
```

---

## 4. Set Operations

### Design Rationale

Wrap the Redis Set data structure, supporting add, remove, query, batch operations, and random pop on a non-duplicating set.

### Problems Solved

- Set provides built-in deduplication, ideal for tags, blacklists / whitelists, online users, etc.
- Need to test whether an element exists (`existsInSet`).
- Random pop an element (`popDataFromSet`) for lottery, random assignment, and similar scenarios.

### Configuration

```yaml
spring.data.redis:
  l2-caching-data:
    - SET                # Set type enables L2 local cache
```

### Recommended Configuration

```yaml
spring.data.redis.l2-caching-data: [STRING, HASH]
# Set is generally NOT recommended for L2: Sets usually hold large amounts of data and putting them in local memory stresses the JVM heap
```

### Design Wins

- Set operations are atomic: add, remove, existence check, union / intersection / difference.
- Batch operations: batch add and batch pop reduce the number of network round trips.
- Pairs with the Bloom filter: `getSetCacheWithLock` prevents stampede.

### Test Cases

```java
GlobalCache.collection().add("tags", "java");
GlobalCache.collection().add("tags", "redis");
boolean exists = GlobalCache.collection().exists("tags", "java");
assert exists;
Set<String> all = GlobalCache.collection().get("tags", String.class);
assert all.contains("redis");
```

---

## 5. ZSet Operations

### Design Rationale

Wrap the full set of Redis ZSet (sorted set) operations, supporting score-based ordering, rank queries, range queries, and score updates.

### Problems Solved

- Leaderboard scenarios: real-time score updates, top-N queries, member rank queries.
- Priority queue: ordering by timestamp or weight.
- Range queries: filter by score interval.

### Configuration

ZSet is currently not in the `l2-caching-data` `KeyTypeEnum` (which only includes STRING / HASH / LIST / SET). ZSet does not go through L2 local cache.

### Recommended Configuration

No extra configuration required. ZSet data volumes tend to be large and are not a good fit for L2 local cache.

### Design Wins

- **Transparent complexity annotation**: most ZSet operations are O(log n), and the documentation calls this out explicitly.
- **Rank queries**: `ranking().reverseRank` wraps `ZREVRANK`.
- **Range pop**: `ranking().popMin` supports batch pop.

### Test Cases

```java
GlobalCache.ranking().set("rank", "user1", 99.5);
GlobalCache.ranking().set("rank", "user2", 88.0);
long reverseRank = GlobalCache.ranking().reverseRank("rank", "user1");
assert reverseRank == 0;

Set<String> top = GlobalCache.ranking().range("rank", 0, 9, new TypeReference<String>(){});
assert top.contains("user1");
```

---

## 6. Key Management

### Design Rationale

Wrap Redis key metadata operations: delete, existence check, TTL set, rename, copy, and similar.

### Problems Solved

- Batch deletion requires iterating the key set, and the native API is cumbersome.
- TTL operations need manual handling of time-unit conversions.
- Rename operations need `RENAMENX` (rename only when the target does not exist).

### Configuration

No special configuration. Key management operations depend on the base Redis connection.

### Recommended Configuration

No extra configuration required.

### Design Wins

- **Batch delete wrapper**: `key().removeCache(Collection)` deletes a batch in one call.
- **Batch existence check**: `key().countExistingKeys(Collection)` returns the number of keys that exist.
- **Unified time unit**: all TTL parameters use milliseconds to avoid mixing `TimeUnit` values.

### Test Cases

```java
GlobalCache.value().set("k1", "v1");
GlobalCache.value().set("k2", "v2");
boolean exists = GlobalCache.key().hasKey("k1");
assert exists;
long count = GlobalCache.key().countExistingKeys(List.of("k1", "k2", "k3"));
assert count == 2;
GlobalCache.key().removeCache(List.of("k1", "k2"));
assert !GlobalCache.key().hasKey("k1");
```

---

## 7. Batch Operations

### Design Rationale

Combine multiple Redis operations into a single network round trip (pipeline mode), reducing latency in batch scenarios.

### Problems Solved

- Per-key writes for N values mean N network round trips; total latency = N × RTT.
- Pipeline combines N operations into one send and one receive.

### Configuration

```yaml
spring.data.redis:
  lettuce:
    pipeline:
      enabled: true  # enable Lettuce pipeline support
```

### Recommended Configuration

Pipeline's batch behavior is enabled by default through the Lettuce connection pool; no extra configuration is required.

### Design Wins

- **Batch write**: `value().batchSet(Map)` uses pipeline internally to send as a batch.
- **Batch read**: `value().getMap(Collection, TypeReference)` retrieves multiple keys in a single call.
- **Conditional batch write**: `value().batchSetIfAbsent(Map)` combines `SET NX` semantics with batching.

### Test Cases

```java
Map<String, String> data = Map.of("b1", "v1", "b2", "v2", "b3", "v3");
GlobalCache.value().batchSet(data, 3600_000L);
Map<String, String> values = GlobalCache.value().getMap(
    List.of("b1", "b2", "b3"),
    new TypeReference<String>(){}
);
assert values.size() == 3;
```

---

## 8. Distributed Lock

### Design Rationale

A distributed lock implementation built on Redisson FencedLock. Before reaching Redisson, an extra JVM local lock pool (`CacheLockManager.LOCK_POOL`) is added, forming a three-layer acquisition order: "local lock → reentrant check → Redisson". Optimistic and pessimistic modes, lock renewal, and reentrancy are supported.

**Why a local lock pool is needed**: every Redisson lock acquire and release is a Redis network RTT (about 1 to 5 ms). When many threads in the same JVM contend on the same lock key, the contention can be resolved in JVM memory (about 0.01 ms) before any of them reaches Redisson. `CacheLockManager` maintains a local lock registry backed by `ConcurrentHashMap` that records which threads in the current JVM hold which locks. Acquisition and release check the registry first; if a thread in the same JVM already holds the lock, subsequent threads can perceive the state locally and do not need to hit Redis every time. This mechanism is fully transparent to business code and does not change the semantics of the distributed lock (Redisson still guarantees cross-instance mutual exclusion).

**Lock acquisition chain**:

```
optimisticLock / pessimisticLock(key, seconds)
  → GlobalCache.lock().optimistic* / pessimistic*
```

### Problems Solved

- **Mutual exclusion in a distributed environment**: write operations on the same resource across JVM instances must be exclusive. Redisson FencedLock provides a fencing-token mechanism that guarantees distributed consistency.
- **Redis pressure under high contention**: when dozens of threads in the same JVM fight for the same lock key, hitting Redis RTT for each attempt magnifies the concurrent load on Redis. The local lock pool resolves the first round of contention inside the JVM; only the actual winner reaches Redisson, eliminating wasted network round trips within the same JVM.
- **Avoiding lock expiry while business logic is still in flight**: `lockWithRenewal` uses the Redisson watchdog to auto-renew, so the lock is not released prematurely when the business work exceeds the lease.
- **Reentrancy for the same thread, no self-deadlock**: when the same thread re-acquires the same lock, `CacheLockManager` detects it locally and increments the counter without going through Redis.

### Configuration

```yaml
spring.data.redis:
  # Local lock (JVM lock pool) — recommended for high-contention scenarios
  enable-local-lock: true

  # Redisson configuration (auto-configured by redisson)
  # Lock TTL / watchdog renewal policy is managed by Redisson automatically
```

### Recommended Configuration

```yaml
spring.data.redis:
  enable-local-lock: true    # enabled by default; significantly reduces Redisson pressure under high contention
```

The local lock pool is enabled by default. In the vast majority of scenarios, there is no need to turn it off. Only in extreme cases (an enormous number of distinct lock keys with a very low hit rate, causing `ConcurrentHashMap` bloat) should you consider disabling it. In real-world workloads, this almost never happens.

### Design Wins

- **Three-layer lock fallback**: local lock → reentrant → Redisson, with each layer reducing Redis pressure. The local lock pool resolves the first round of contention inside the JVM; under high contention, a large batch of threads in the same JVM does not need to serialize through Redis RTT, they perceive the lock state in the O(1) `ConcurrentHashMap` lookup phase.
- **Quantified impact**: a local lock hit is about 0.01 ms (pure JVM memory); Redisson lock acquisition is about 1 to 5 ms (one network RTT). When 100 threads compete for the same lock concurrently, the local lock pool reduces 99 network RTTs to 0, and only the thread that actually wins the lock issues a Redisson request.
- **Lock renewal**: `lockWithRenewal` uses the built-in Redisson watchdog to auto-renew, preventing premature lock expiry while business work is still in progress.
- **Optimistic and pessimistic modes**: optimistic lock `tryLock(0, time)` returns after a single attempt without blocking; pessimistic lock `lock(time)` blocks until acquisition or timeout. Choose by scenario.
- **AutoCloseable**: `CacheLock` implements `AutoCloseable`, so try-with-resources releases the lock automatically, eliminating deadlocks caused by forgotten `unlock` calls.
- **Reentrancy detection**: when the same thread re-acquires the same lock, `tryReentrant` recognizes the situation locally via `CacheLockManager.existLock()` and increments the counter without going through Redis, keeping the reentry count accurate.

### Test Cases

```java
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) {
        orderService.processOrder("123");
    }
}

try (var lock = GlobalCache.lock().pessimistic("lock:order:456", 10L)) {
    if (lock.success()) {
        orderService.processOrder("456");
    }
}
```

---

## 9. Cache Stampede Prevention (Locked Read with Source Fallback)

### Design Rationale

Hot-key cache reads get a four-layer protection chain: "L2 → Bloom filter → Redis → distributed lock → DB fallback". The core methods are the `getXxxWithLock` family.

**Full chain** (using `value().getWithLock` as the example):

```
value().getWithLock(key, timeout, dbLoader)
  ├── Bloom filter → Redis GET → distributed lock → DB fallback → write back to cache
```

### Problems Solved

- **Cache penetration**: requests for keys that do not exist in Redis or the DB fall through to the DB every time.
- **Cache stampede**: the instant a hot key expires, a large wave of concurrent requests all hit the DB.
- Non-blocking failure handling: threads that fail to acquire the lock do not block waiting for the lock to be released; they retry reading the cache (which the successful thread has already written).

### Configuration

```yaml
platform:
  cache:
    bloom-filter:                          # Bloom filter (anti-penetration)
      enable: true
      type: REDISSON                       # REDISSON / GUAVA
      expected-insertions: 10000000        # expected number of insertions
      false-probability: 0.001             # false-positive rate

spring.data.redis:
  enable-l2-caching: true                  # L2 local cache
  l2-caching-data: [STRING, HASH]
```

### Recommended Configuration

```yaml
platform.cache.bloom-filter:
  enable: true                             # recommended for hot data with more than 10k entries
  type: REDISSON                           # REDISSON recommended for multi-instance (shared across processes)
  expected-insertions: 10000000
  false-probability: 0.001

spring.data.redis.enable-l2-caching: true
spring.data.redis.l2-caching-data: [STRING, HASH]
```

### Design Wins

- **Four-layer defense**: L2 → Bloom → Redis → lock + DB. Each layer absorbs what it can and the next is bypassed.
- **Zero stampede impact**: even if lock acquisition fails, the request does not fall through to the DB directly; it retries reading the cache that the successful thread just wrote.
- **Automatic DB write-back**: data loaded from the source is automatically written back to the cache and the Bloom filter.
- **Unified API**: `value()` / `struct()` / `field()` all provide `getWithLock`, with a consistent usage pattern.

### Test Cases

```java
String value = GlobalCache.value().getWithLock(
    "config:app:pay_ratio", 3600_000L,
    () -> configService.getConfig("pay_ratio")
);

UserInfo user = GlobalCache.struct().getWithLock(
    "user:profile:123", UserInfo.class, 3600_000L,
    () -> userService.findById("123")
);

String name = GlobalCache.field().getWithLock(
    "user:profile:123", "name", String.class, 3600_000L,
    () -> userService.findById("123").getName()
);
```

---

## 10. L2 Secondary Cache

### Design Rationale

Add a JVM local cache layer (Caffeine / Ehcache / cache2k) in front of Redis, forming a three-tier cache hierarchy: L1 (local) + L2 (Redis) + DB.

**Read and write paths**:

```
Read: GlobalCache.value().get(key) / struct().get(key)
  ├── LocalCache.get(key)                    ← L1 local
  ├── Redis GET                                 ← L2 Redis
  └── miss → getWithLock source fallback

Write: GlobalCache.value().set(key, value, ttl)
  ├── Redis SET
  ├── LocalCache.put                            ← dual-write L1
  └── keyspace notification → invalidate L1 on other instances
```

### Problems Solved

- **Cache avalanche**: a large batch of keys expire at once and all hit Redis. The L1 local cache absorbs the read pressure.
- **Hot key in Redis**: a single key is read by many instances at the same time, creating a Redis CPU bottleneck. L1 local hits return directly.
- **Network latency**: every Redis read costs a network round trip. L1 hits cost zero network.

### Configuration

```yaml
spring.data.redis:
  # L2 master switch
  enable-l2-caching: true

  # Which data types go through L2 (options: STRING, HASH, LIST, SET)
  l2-caching-data:
    - STRING
    - HASH

  # L2 TTL random offset (anti-avalanche): a 1 to 10 minute offset is appended automatically
```

### Recommended Configuration

```yaml
spring.data.redis:
  enable-l2-caching: true
  l2-caching-data:
    - STRING                          # hot strings should go through L2
    - HASH                            # high-frequency object caching should go through L2
    # LIST and SET: use with care; large data volumes put pressure on local memory
```

**Multi-instance consistency notes**:

Cross-instance L2 sync relies on Redis keyspace notifications (`notify-keyspace-events KEA`):

```bash
# Required on the Redis server (off by default, must be turned on manually)
notify-keyspace-events KEA
```

- Instance A writes or deletes a Redis key → Redis fires a keyspace event → instance B receives the notification → it invalidates its local cache.
- **Short dirty-read window**: between the Redis write and the moment the notification arrives, instance B's local cache still holds the old value.
- **Recommended tolerance**: even if no invalidation notification arrives, the L2 local cache TTL should not exceed `original TTL × 2`.

### Design Wins

- **Read performance**: an L1 hit has about 0.1 ms latency (JVM memory), far lower than Redis's 1 to 5 ms.
- **Avalanche protection**: even when a large batch of Redis keys expire at once, the L1 layer still holds the data.
- **Cross-instance sync**: `CacheSyncListener` keeps each instance's L1 consistent via Redis keyspace notifications.
- **Fine-grained configuration**: control L2 routing per data type (STRING / HASH / LIST / SET).

### Test Cases

```java
GlobalCache.value().set("l2:test", "value", 60_000L);
String val = GlobalCache.value().get("l2:test", String.class);
assert "value".equals(val);
```

---

## 11. Bloom Filter

### Design Rationale

Provide Bloom filter capability, paired with the cache stampede prevention methods, to **efficiently decide whether a key is "definitely absent"** and intercept invalid queries before they fall through to the DB.

Two implementations are supported:

| Implementation | Scenario | Characteristics |
|----------------|----------|-----------------|
| `GuavaBloomFilter` | Single instance | JVM memory, no network overhead, cannot be shared across processes |
| `RedissonBloomFilter` | Multi-instance | Stored in Redis, shared across processes, every check costs a network call |

### Problems Solved

- Cache penetration: many requests for non-existent keys (for example, malicious traffic) pass through to the DB. The Bloom filter intercepts them before L1 / L2.
- Pairs with `value().getWithLock`: when the Bloom filter says "absent", skip the Redis query and lock acquisition and fall back to the source directly.

### Configuration

```yaml
platform:
  cache:
    bloom-filter:
      enable: false                   # off by default; evaluate before going live
      type: REDISSON                  # REDISSON (recommended for multi-instance) / GUAVA (single instance)
      expected-insertions: 10000000   # expected number of inserted elements
      false-probability: 0.001        # false-positive rate (0.1%)
```

### Recommended Configuration

```yaml
platform.cache.bloom-filter:
  enable: true                        # recommended when cache penetration risk is high
  type: REDISSON                      # recommended for multi-instance production
  expected-insertions: 10000000
  false-probability: 0.001
```

**When to enable**:
- Cache data set has more than 10k entries and there is a penetration risk → enable.
- Data structure is well bounded (e.g., user IDs or product IDs with a known range) → the effect is best.
- A 0.1% false-positive rate means 1 in 1000 queries will fall back to the source unnecessarily, which is acceptable.

### Design Wins

- **Zero false-negatives**: "absent" is 100% absent, so penetration queries are intercepted directly.
- **Tunable low false-positive rate**: trade space for accuracy through `false-probability`.
- **Pairs with the cache**: the Bloom filter is updated automatically on cache writes (`bloomFilter.add(key)`), keeping it consistent.

### Test Cases

```java
String key = "test:bloom:exists";
GlobalCache.value().set(key, "value", 60_000L);

String val = GlobalCache.value().getWithLock(
    "test:bloom:notexists", 60_000L,
    () -> "loadedFromDB"
);
```

---

## 12. Performance Guard

### Design Rationale

`RedisPerfGuard` is a runtime Redis operation governance layer that sits in front of every Redis call in every Manager, monitoring and optionally blocking along three dimensions: **complexity tier**, **latency threshold**, and **String payload detection**.

**Governance dimensions**:

```
RedisPerfGuard.execute(manager, method, tier, supplier)
  │
  ├── Complexity allow-list check (tocAllowedComplexities)
  │     └── not in the allow-list → ERROR + optional block
  │
  ├── Non-O(1) WARN
  │     └── tier != O1 → warn log
  │
  ├── Slow query tiering
  │     ├── >= tocSoftMs → WARN
  │     └── >= tocHardMs → ERROR
  │
  └── String write anti-pattern detection (checkStringWritePayload)
        ├── Collection / Map / array blob → WARN
        ├── oversized text → ERROR + optional block
        └── suspected JSON blob → WARN
```

### Problems Solved

- Developers inadvertently call `KEYS *` or full `HGETALL` on ToC core paths, which are O(n) operations.
- Large values cause network bottlenecks and JVM GC pressure, with no early warning mechanism.
- Slow queries have no tiered alerts; problems only surface after users complain.

### Configuration

```yaml
spring.data.redis:
  perf:
    enabled: false                              # off by default; enable via canary
    warn-non-o1: true                          # WARN on non-O(1) operations
    toc-soft-ms: 8                             # soft threshold (ms); over this is WARN
    toc-hard-ms: 50                            # hard threshold (ms); over this is ERROR
    block-forbidden-tiers: false               # block LINEAR_N / WORSE operations
    toc-allowed-complexities:                  # ToC complexity allow-list (empty = no restriction)
      - O1
      - LOG_N
      - SCRIPT_OR_UNKNOWN
    warn-string-payload-anti-patterns: true    # detect String anti-patterns
    block-string-payload-violations: false     # block String anti-pattern writes
    string-payload-max-chars-warn: 100000      # String char count WARN
    string-payload-max-chars-error: 1000000    # String char count ERROR
```

### Recommended Configuration

**Canary phase**:

```yaml
spring.data.redis.perf:
  enabled: true
  warn-non-o1: true
  toc-soft-ms: 8
  toc-hard-ms: 50
  block-forbidden-tiers: false       # canary phase: warn only, do not block
  warn-string-payload-anti-patterns: true
  block-string-payload-violations: false
```

**Production phase** (after all warnings have been addressed):

```yaml
spring.data.redis.perf:
  enabled: true
  block-forbidden-tiers: true        # block O(n) / O(n²) operations
  block-string-payload-violations: true  # block oversized value writes
```

### Design Wins

- **Complexity governance made real**: instead of a document saying "do not use KEYS", the runtime detects the call and emits WARN or blocks it.
- **Performance degradation is observable**: `toc-soft-ms` / `toc-hard-ms` automatically detect slow queries and output `[RedisPerf]` logs.
- **BIGKEY prevention**: `checkStringWritePayload` detects Collection, Map, and oversized text stored as String before the write goes through.
- **Progressive canary**: `enabled` goes from false → canary true (warn only) → production true (block), avoiding a one-shot blast radius.

### Test Cases

```java
// Test slow query detection (enable perf)
// Manually set toc-soft-ms: 1, then run an operation that takes 2ms
// Expected output: [RedisPerf] latency soft threshold RedisStringManager setValue O1 2ms (>=1ms)

// Test String anti-pattern detection
// Write a 1MB String
String bigValue = "x".repeat(2_000_000);
// Expected: when block-string-payload-violations=true, throw IllegalStateException
try {
    GlobalCache.value().set("test:big", bigValue, 60_000L);
    if (perf.blockStringPayloadViolations) {
        fail("should have been blocked");
    }
} catch (IllegalStateException e) {
    assert e.getMessage().contains("String value anti-pattern");
}

// Test complexity blocking
// When block-forbidden-tiers=true, calls to O(n) operations are expected to be blocked
```

---

## 13. Multi-Redis Instance Routing

### Design Rationale

`MultiRedisTemplate` and `MultiStringRedisTemplate` support **named child Redis instances**, routing to different Redis nodes by key prefix or business name.

### Problems Solved

- Different businesses need to be isolated to different Redis instances (e.g., order Redis, user Redis).
- Read-write split: writes go to the primary Redis, reads to a replica.
- Multi-environment sharing: a single application connects to multiple Redis clusters.

### Configuration

```yaml
spring.data.redis:
  # Primary Redis configuration
  host: primary.redis.com
  port: 6379

  # Named child Redis instances
  slaves:
    order-redis:
      host: order.redis.com
      port: 6379
      password: order-pass
    user-cache:
      host: user.redis.com
      port: 6380
```

### Recommended Configuration

```yaml
# Enable when multi-instance isolation is needed
spring.data.redis.slaves:
  order-redis:
    host: ${ORDER_REDIS_HOST}
    port: ${ORDER_REDIS_PORT}
  user-cache:
    host: ${USER_REDIS_HOST}
    port: ${USER_REDIS_PORT}
```

### Design Wins

- **Named routing**: routes to a specific Redis by key prefix or business name, no hard-coding required.
- **Independent connection pools**: the primary Redis and child Redis instances each have their own connection pools, with no interference.
- **Transparent switching**: business code is unaware; the routing logic is encapsulated inside `MultiRedisTemplate`.

### Note

`MultiRedisTemplate`'s routing table is initialized statically at configuration time. Adding or removing a child Redis instance at runtime requires a restart.

---

## 14. Lua Scripts

### Design Rationale

Wrap the Redis Lua script execution capability, supporting atomic execution of complex business logic through `script().eval`.

### Problems Solved

- Need to guarantee atomic execution of multiple Redis commands.
- Complex conditional logic runs on the server side, reducing network round trips.
- Scenarios such as rate limiting, counters, and transactional operations.

### Configuration

No extra configuration required. Lua scripts are sent to Redis for execution via `RedisTemplate`'s `execute` method.

### Recommended Configuration

Manage Lua scripts as resource files (`.lua`) rather than concatenating strings in code.

### Design Wins

- **Atomicity**: Lua scripts run serially on the Redis server, atomic by design.
- **Reduced network round trips**: multiple commands sent in one go.
- **Complexity classification**: encapsulated under the `SCRIPT_OR_UNKNOWN` tier, not classified as O(1) / O(n).

### Test Cases

```java
String result = GlobalCache.script().eval(
    "return ARGV[1]",
    List.of(),
    List.of("ok"),
    String.class
);
assert "ok".equals(result);
```

---

## 15. Rate Limiting

### Design Rationale

A sliding-window rate limiting algorithm built on Redis plus a Lua script. `limiter().tryAcquire(key, maxCount, windowSeconds)` decides whether a request exceeds the threshold within the current window.

### Problems Solved

- Interface anti-scraping.
- API rate limiting (e.g., 100 calls per user per minute).
- Sudden-traffic control.

### Configuration

No rate-limiting-specific configuration is required. Rate-limiting parameters are passed at the call site:

```java
// Up to 100 times within a 60-second window
boolean pass = GlobalCache.limiter().tryAcquire("rl:/api/pay", 100, 60);
```

### Recommended Configuration

Rate-limiting parameters should be set by the business side based on interface capacity. The recommended windows and thresholds:

```yaml
# It is recommended to manage rate-limiting thresholds dynamically in the config center (not hard-coded in code)
rate-limiter:
  /api/pay: { max-count: 100, window-seconds: 60 }
  /api/order: { max-count: 50, window-seconds: 60 }
  /api/login: { max-count: 5, window-seconds: 60 }
```

### Design Wins

- **Lightweight**: a single Lua script, no extra dependencies.
- **Distributed effect**: built on Redis, so all instances share the same rate-limiting state.
- **Lua atomicity**: `INCR + EXPIRE` runs atomically inside the Lua script; no race conditions.
- **Performance guard integration**: rate-limiting calls go through `RedisPerfGuard` governance.

### Test Cases

```java
String limitKey = "rl:test:api";
// Up to 5 times within the window
for (int i = 0; i < 5; i++) {
    assert GlobalCache.limiter().tryAcquire(limitKey, 5, 60);
}
assert !GlobalCache.limiter().tryAcquire(limitKey, 5, 60);
```

---

## 16. Geographic Location GEO

### Design Rationale

Wrap the Redis GEO data structure, supporting storage of geographic locations, distance calculation, and nearby range queries.

### Problems Solved

- Nearby merchants / POI queries.
- Delivery distance calculation.
- Geofencing.

### Configuration

No extra GEO configuration required. Depends on the base Redis connection.

### Recommended Configuration

```yaml
spring.data.redis:
  # When the GEO data volume is large, plan your key naming carefully
  # For example: geo:store, geo:warehouse, geo:delivery-man
```

### Design Wins

- **Unified interface**: `geo().add` / `geo().distance` / `geo().radius` wrap GEOADD / GEODIST / GEORADIUS.
- **Distance unit handling**: automatic conversion between meters and kilometers.
- **Result encapsulation**: `GeoPointResult` carries coordinates, distance, and other metadata.

### Test Cases

```java
GlobalCache.geo().add("geo:store", 116.39, 39.9, "beijing");
GlobalCache.geo().add("geo:store", 121.47, 31.23, "shanghai");
Distance distance = GlobalCache.geo().distance("geo:store", "beijing", "shanghai");
assert distance.getValue() > 1000;

List<GeoPointResult> nearby = GlobalCache.geo().radius("geo:store", 116.4, 39.9, 50.0);
assert nearby.stream().anyMatch(r -> "beijing".equals(r.getMember()));
```

---

## 17. HyperLogLog

### Design Rationale

Wrap the Redis HyperLogLog data structure, providing efficient deduplication cardinality estimation.

### Problems Solved

- UV statistics (daily active users, monthly active users).
- Large-scale deduplication statistics (billions of records).
- Approximate statistics scenarios (error rate around 0.81%).

### Configuration

No special configuration required. All operations are based on Redis PFADD / PFCOUNT / PFMERGE commands.

### Recommended Configuration

```yaml
# Use the key name to distinguish statistical periods for HyperLogLog
# Recommended naming: {business}:hll:{dimension}:{period}
# Example: uv:hll:page:/home:2025-01-01
```

### Design Wins

- **Very low memory**: 12KB can count up to 2^64 elements, far less than a Set.
- **Fast merge**: `PFMERGE` supports merging across periods and dimensions.
- **Acceptable error**: the 0.81% error rate is acceptable for large-scale statistics.

### Test Cases

```java
String hllKey = "hll:test:uv";
GlobalCache.hyperLog().add(hllKey, "user1", "user2", "user3");
long count = GlobalCache.hyperLog().count(hllKey);
assert count >= 3;  // cardinality estimate, may be slightly larger than the actual value
```

---

## 18. Bitmap

### Design Rationale

Wrap Redis Bitmap operations, supporting bit-level set and get.

### Problems Solved

- Sign-in / check-in (one bit per day).
- User tags / attribute flags.
- Online status recording.
- Maximum-compression storage of boolean data.

### Configuration

No special configuration required. All operations are based on Redis SETBIT / GETBIT / BITCOUNT commands.

### Recommended Configuration

```yaml
# Recommended Bitmap naming: {business}:bitmap:{ID}:{dimension}
# Example: sign:bitmap:user:123:202501
```

### Design Wins

- **Extreme memory efficiency**: 1 bit per day for 100M users is about 12MB.
- **Fast statistics**: `BITCOUNT` aggregates all marked bits in seconds.
- **Cross-day computation**: `BITOP` supports AND / OR / XOR / NOT across bitmaps.

### Test Cases

```java
String bitKey = "bit:test:sign:202501";
GlobalCache.bitmap().set(bitKey, 1, true);
GlobalCache.bitmap().set(bitKey, 2, true);
boolean day1 = GlobalCache.bitmap().get(bitKey, 1);
assert day1;
boolean day3 = GlobalCache.bitmap().get(bitKey, 3);
assert !day3;
```

---

## 19. Local Cache (Independent from L2)

### Design Rationale

Provide JVM local cache capability independent of Redis (built on the JSR-107 standard, with Caffeine / Ehcache / cache2k as providers). Unlike L2, this local cache is a **business-explicit** independent region (`spring.data.local`); L2 is a transparent acceleration layer that `GlobalCache` read paths pass through automatically.

### Problems Solved

- Pure local cache scenarios that do not need Redis (such as config items and routing tables).
- L2 is enabled automatically per data type; business developers may need an independent named cache region.
- JSR-107 standard interface makes the underlying implementation swappable.

### Configuration

```yaml
spring.data.local:
  provider: CAFFEINE      # CAFFEINE / EHCACHE / CACHE2K
  cache-definitions:
    - name: localConfig
      max-size: 10000
      expiry-policy: CREATED
      ttl: 600_000        # 10 minutes
    - name: routeTable
      max-size: 5000
      expiry-policy: ACCESSED
      ttl: 3_600_000      # 1 hour
```

### Recommended Configuration

```yaml
spring.data.local:
  provider: CAFFEINE              # Caffeine gives the best performance
  cache-definitions:
    # Config cache: short TTL, small capacity
    - name: appConfig
      max-size: 1000
      expiry-policy: CREATED
      ttl: 300_000
    # Routing table cache: medium TTL, medium capacity
    - name: routeTable
      max-size: 5000
      expiry-policy: ACCESSED
      ttl: 3_600_000
```

### Design Wins

- **Standard interface**: based on JSR-107 (`javax.cache.Cache`), implementation is swappable.
- **Independent from Redis**: usable without a Redis connection.
- **Region isolation**: different cache regions distinguished by `CacheName` with independent capacity and TTL.

---

## 20. Pub/Sub and Keyspace Notification

### Design Rationale

Wrap Redis Pub/Sub, used primarily for two scenarios:

1. **Keyspace Notification**: `CacheSyncListener` subscribes to Redis's `__keyevent@*__` events for multi-instance L2 cache sync; business-defined listeners use `GlobalCache.event().subscribeKeyEvent(pattern, listener)`.
2. **Business topic Pub/Sub**: lightweight broadcasting via `GlobalCache.notification().publish(topic, message)`.

### Problems Solved

- L2 local cache consistency sync across multiple instances.
- Lightweight business message broadcasting (when the persistence and ACK guarantees of Stream MQ are not required).

### Configuration

```yaml
# Redis must enable keyspace notifications (required for L2 multi-instance sync)
# Redis configuration (redis.conf):
notify-keyspace-events KEA
```

### Recommended Configuration

```bash
# Redis server configuration (all environments)
notify-keyspace-events KEA
# K = keyspace events
# E = keyevent events
# A = all event types (del, set, expired, etc.)
```

### Design Wins

- **L2 consistency**: `CacheSyncListener` receives write / delete notifications from other instances and invalidates the local cache automatically.
- **Lightweight broadcasting**: scenarios without persistent messaging can use Pub/Sub, which is lighter than Stream.

### Note

Pub/Sub is a "fire-and-forget" mode: if the consumer is offline, the message is lost. For reliable messaging, use **`StreamMQ`** (`atlas-richie-component-redis-streammq`).

---

## 21. Migration Window Validation

### Design Rationale

`MigrationWindowValidator` scans the fields annotated with `@MigrationWindow` in `AtlasRedisProperties` at Spring startup and checks whether the deadline has passed. If the deadline has passed and the value is still `false`, an ERROR log is emitted and the application is prevented from starting.

### Problems Solved

- Configuration defaults tend to be the "safest" option (e.g., `perf.enabled: false`), but if never turned on they never deliver value.
- The "soft switch nobody ever flips" problem: a hard deadline forces teams to push configuration migration forward.

### Configuration

Fields annotated with `@MigrationWindow`:

```yaml
spring.data.redis.perf:
  enabled: true                           # deadline: 2026-12-01
  block-forbidden-tiers: true             # deadline: 2026-12-01
  block-string-payload-violations: true    # deadline: 2026-12-01
```

### Recommended Configuration

```yaml
spring.data.redis.perf:
  enabled: true
  block-forbidden-tiers: true
  block-string-payload-violations: true
```

### Design Wins

- **Forced execution**: failing to migrate before the deadline prevents the application from starting, ensuring configuration governance does not get left behind.
- **Smooth canary**: before the deadline, you can roll out gradually (warn first, then block), without forcing a hard cutover.
- **Fail-fast escape**: if startup fails after the deadline, ops is immediately aware and can ship the config in under a minute.

### Note

For production services where startup failures have a large blast radius, complete the canary validation and configuration migration **before** the deadline, rather than waiting for the startup failure and then changing the config.

---

## 22. Bounded Queue queue()

### Design Rationale

A **bounded FIFO queue** (`BoundedQueue`) built on top of the Redis List. A companion meta key (`{key}:meta`) persists `maxLen`, and the write path atomically reads the meta and maintains the List length via Lua. Compared with the removed unbounded List API, the core is **capacity governance** plus a **JDK Queue-style API with clear semantics**.

**Positioning (important)**:

- **Active pull**: business code must call `poll()` / `drain(count)` to consume. Nothing is consumed without a pull, and there is no push, no consumer group.
- **Traffic-shaping buffer**: high-load `offer` to buffer; workers pull at their own pace. When full, the **head is dropped** (oldest entry) so the system is not dragged down.
- **Lightweight fallback**: when cloud PaaS Redis does not support the full Stream feature set and you do not want to bring in RocketMQ / RabbitMQ, this is a List-based option.
- **Not a message queue**: for reliable delivery, ACK, dead-letter, and consumer monitoring, use **`StreamMQ`** (`atlas-richie-component-redis-streammq`).

**Redis data model**:

```
{queueKey}        → Redis LIST (sequence of elements)
{queueKey}:meta   → String, stores maxLen (1 to 4999)
```

**Write path (offer)**:

```
BoundedQueue.offer(item)
  ├── assertMetaPresent()                    ← meta presence check
  └── Lua: GET meta → RPUSH → LTRIM -maxLen,-1   ← atomic enqueue + trim
```

**Growth (grow)**:

```
grow() / queue().grow(key)
  ├── Lua GROW_MAX_LEN (×2, capped at 4999)
  ├── queue instance: TRIM list to match meta on success
  └── already at the cap returns false; missing / invalid meta throws an exception
```

**Read path and deserialization**:

```
poll / peek / peekTail / drain
  └── BoundedListElementConverter
        ├── Redis has no elements → silent null (not an error)
        └── deserialization failure → WARN + null (for poll, the element has already been removed from the List)
```

**Core classes**: `BoundedQueueFunction` → `RedisBoundedQueueManager` → `BoundedQueue`; sharing `BoundedListRedisSupport`, `BoundedListRedisScripts`, `BoundedListCapacityLimits`.

### Problems Solved

- The native List API has **no maxLen**, so under high concurrency the List grows without bound → big keys, blocking, memory risk.
- `addListItem` + `leftPop` semantics are scattered and mixed with the "cache the whole List" use case, making team-wide governance hard.
- Need **predictable traffic shaping**: when overloaded, drop the oldest task rather than dragging Redis or the JVM down.
- In PaaS environments where Stream capability is limited, a **lightweight async channel over existing Redis** is still needed (accepts active pull and at-most-once semantics).
- Misuses such as `maxLen` mismatch across instances on multi-instance `getOrCreate`, sharing a key with `rawList()`, or non-List types occupying the key, must be caught at creation time.

### Configuration

Shares the `spring.data.redis.*` connection with the List; there is **no independent `queue.*` switch**. Capacity is constrained at creation time by `maxLen` and platform constants:

| Constant | Value | Meaning |
|----------|-------|---------|
| `LIST_BIGKEY_RECOMMENDED_MAX_ELEMENTS` | 5000 | Recommended upper bound for business Lists in README |
| `BOUNDED_MAX_LEN_CEILING` | 4999 | Upper bound of legal `maxLen` (strictly below the big-key red line) |
| `MIN_MAX_LEN` | 1 | Minimum capacity |
| `MAX_BATCH_COUNT` | 20 | Single-call upper bound for `drain(count)` |

```yaml
spring.data.redis:
  host: localhost
  port: 6379
  # The bounded queue has no extra switch; it depends on JSON serialization (jsonTemplate)
```

### Recommended Configuration

- **maxLen**: estimate by peak buffer volume. Start with a small capacity (e.g., 500 to 2000) and only call `grow()` when necessary. It doubles at most once up to the cap; do not jump straight to 4999.
- **Consumer side**: scheduled tasks or dedicated workers should **actively** `poll` / `drain(≤20)`. For multi-instance competing consumers, evaluate whether "first to grab consumes" is acceptable.
- **Key convention**: `{business}:bq:{scenario}:{id}`. **Do not** share the key with `rawList()`, Stream keys, or String cache.
- **TTL**: short-lived buffers can call `expire(ms)` (both list and meta keys get PEXPIRE atomically).
- **Stable types**: the `Class<T>` of elements must match the serialization format at write time, to avoid deserialization WARN and silent drops.

### Design Wins

- **Hard capacity ceiling**: capped at 4999, aligned with List big-key governance, avoiding unbounded Lists.
- **Atomic write**: `offer` runs in a single Lua script (RPUSH + LTRIM), so length and meta stay consistent under concurrency.
- **Clear traffic-shaping semantics**: when full, drop the head, suitable for scenarios where "dropping the old task is better than taking the system down".
- **Creation-time validation**: `TYPE` only allows `none` / `list`; `setIfAbsent` only counts `TRUE` as success; `getOrCreate` checks `maxLen` consistency.
- **Observability**: the read path emits a unified WARN on deserialization failure (including key, operation, failure).
- **Clean layering with Stream**: does not compete with Stream's "real MQ" positioning, reducing misuse.

### Test Cases

```java
// Create, enqueue, dequeue
BoundedQueue<Task> q = GlobalCache.queue().create("order:bq:retry", 1000, Task.class);
q.offer(new Task("t1"));
q.offer(new Task("t2"));
Task first = q.poll();
assert "t1".equals(first.getId());

// Batch drain (count must be in 1 to 20)
List<Task> batch = q.drain(10);

// getOrCreate: when the queue already exists, maxLen must be consistent
BoundedQueue<Task> same = GlobalCache.queue().getOrCreate("order:bq:retry", 1000, Task.class);

// Growth: only ×2, capped at 4999; throws IllegalArgumentException when the key does not exist
boolean grown = GlobalCache.queue().grow("order:bq:retry");

// Do not share a key with rawList(); creating on a non-List key throws IllegalStateException
```

---

## 23. Bounded Stack stack()

### Design Rationale

A **bounded LIFO stack** (`BoundedStack`) built on top of the Redis List, sharing the meta key pattern (`{key}:meta`) and `BoundedListCapacityLimits` / `BoundedListRedisScripts` / `BoundedListRedisSupport` with the queue, but with **different overflow policy and consumption semantics**.

**Positioning**:

- Another **bounded List utility**, **actively pulled** (`pop` / `latest`), **not** a message queue.
- Suitable for LIFO scenarios that only care about the latest N entries (recent operation log, preview of the latest pending batch, etc.).
- Compared with `queue()`: when full, the stack **rejects the push** (returns `false`) and does not auto-evict old elements.

**Redis data model** (same as the queue):

```
{stackKey}        → Redis LIST (bottom-to-top order matches RPUSH, top is on the right)
{stackKey}:meta   → String maxLen
```

**Write path (push)**:

```
BoundedStack.push(item)
  └── Lua: GET meta → LLEN >= maxLen ? return 0 : RPUSH
        └── returns 1 on success, 0 means full and rejected
```

**Read path**:

```
pop()      → RIGHTPOP (pop from the top)
peek()     → LINDEX -1
latest(n)  → LRANGE -n,-1 (n ∈ [1,20], read-only, does not delete)
```

**Growth**: only the meta is updated (`grow` Lua). Unlike the queue, the stack does not run LTRIM after grow; when not full, LLEN ≤ maxLen already holds.

**Core classes**: `BoundedStackFunction` → `RedisBoundedStackManager` → `BoundedStack`.

### Problems Solved

- Need a **fixed-depth** LIFO structure, where overflow should **fail explicitly** rather than silently overwrite (contrasting with the queue's "drop the oldest").
- The native `rightPush` / `rightPop` has no `maxLen` and cannot express "keep at most the latest N entries".
- `latest(count)` is a read-only batch peek at the top, avoiding the need to `pop` (and thus damage the data) just to glance at the top.

### Configuration

Same as [§22 Bounded Queue](#22-bounded-queue-queue): no independent configuration item, `maxLen` is constrained by `BoundedListCapacityLimits`.

### Recommended Configuration

- **Scenario selection**: only use `stack()` when the business semantics are LIFO. Use `queue()` for FIFO traffic shaping.
- **maxLen**: usually smaller than the queue (e.g., 50 to 500), representing a "latest N entries" window.
- **Handling a full stack**: when `push` returns `false`, the business side should degrade (discard this entry, alert, or call `grow()` and retry).
- **latest**: use `latest(count)` for preview, `pop()` for actual consumption. `count` must not exceed 20.

### Design Wins

- **Reject on full**: protects the N entries already on the stack from being silently overwritten. Predictable semantics.
- **O(1) top operations**: `peek` / `pop` / `push` all touch the ends of the List.
- **Shared governance infrastructure**: meta, grow, expire, destroy, type checks, and deserialization WARN are all shared with the queue.
- **API aligned with JDK Deque**: reduces the learning curve when migrating from a local stack to a distributed one.

### Test Cases

```java
BoundedStack<Event> stack = GlobalCache.stack().create("audit:stack:svc1", 100, Event.class);
boolean ok = stack.push(new Event("e1"));
assert ok;
Event top = stack.peek();
assert "e1".equals(top.getName());

// Reject when full
for (int i = 0; i < 200; i++) {
    stack.push(new Event("fill-" + i));
}
boolean rejected = stack.push(new Event("overflow"));
assert !rejected;

// Batch peek at the top (no deletion)
List<Event> recent = stack.latest(5);

Event popped = stack.pop();
```

### queue() vs stack() Selection Guide

| Dimension | `queue()` FIFO | `stack()` LIFO |
|-----------|----------------|----------------|
| Overflow policy | Drop the oldest (LTRIM) | `push` fails when full |
| Typical scenario | Traffic shaping, lightweight task buffering | Latest N entries, stack-style processing |
| Consumption | `poll` / `drain` | `pop` / `latest` |
| Compared with Stream MQ | Complementary, not a replacement | Complementary, not a replacement |
| Active pull | Yes | Yes |

---

## 24. Feature Summary

| # | Feature | Core class | Pattern | Config prefix | Complexity governance |
|---|---------|-----------|---------|---------------|----------------------|
| 1 | KV / String storage | `RedisStringManager` | Facade + delegate | `spring.data.redis` | O(1) |
| 2 | Hash operations | `RedisHashManager` | Facade + delegate | `spring.data.redis` | O(1) |
| 3 | List operations (native) | `RedisListManager` | Facade + delegate (deprecated step by step) | `spring.data.redis` | O(1) / O(n) |
| 22 | Bounded queue queue() | `RedisBoundedQueueManager` / `BoundedQueue` | Bounded FIFO + meta + Lua | `spring.data.redis` | O(1) + SCRIPT |
| 23 | Bounded stack stack() | `RedisBoundedStackManager` / `BoundedStack` | Bounded LIFO + meta + Lua | `spring.data.redis` | O(1) + SCRIPT |
| 4 | Set operations | `RedisSetManager` | Facade + delegate | `spring.data.redis` | O(1) |
| 5 | ZSet operations | `RedisZSetManager` | Facade + delegate | `spring.data.redis` | O(log n) |
| 6 | Key management | `RedisKeyManager` | Facade + delegate | `spring.data.redis` | O(1) / O(n) |
| 7 | Batch operations | `RedisStringManager` | Pipeline batch | `spring.data.redis` | (inherits) |
| 8 | Distributed lock | `RedisLockManager` | Three-layer lock + Redisson | `spring.data.redis` | — |
| 9 | Cache stampede prevention | `RedisStringManager` | L2 → Bloom → Redis → Lock → DB | `platform.cache.bloom-filter` | (inherits) |
| 10 | L2 secondary cache | `LocalCache` + `CacheSyncListener` | Local cache + keyspace sync | `spring.data.redis.enable-l2-caching` | — |
| 11 | Bloom filter | `BloomFilterFacade` | Strategy pattern (Guava / Redisson) | `platform.cache.bloom-filter` | — |
| 12 | Performance guard | `RedisPerfGuard` | Decorator + complexity tiering | `spring.data.redis.perf` | Runtime governance |
| 13 | Multi-Redis routing | `MultiRedisTemplate` | Routing table + named key | `spring.data.redis.slaves` | — |
| 14 | Lua scripts | `RedisLuaManager` | Script executor | — | SCRIPT_OR_UNKNOWN |
| 15 | Rate limiting | `RedisLimiterManager` | Sliding window + Lua | — | SCRIPT_OR_UNKNOWN |
| 16 | GEO | `RedisGeoManager` | Wraps GEO commands | — | O(log n) |
| 17 | HyperLogLog | `RedisHyperLogManager` | Wraps PF commands | — | O(1) |
| 18 | Bitmap | `RedisBitmapManager` | Wraps BIT commands | — | O(1) |
| 19 | Local cache | `LocalCacheManager` + `LocalCache` | JSR-107 standard | `spring.data.local` | — |
| 20 | Pub/Sub | `RedisEventManager` + `NotificationFunction` | Pub/Sub | — | — |
| 21 | Migration window | `MigrationWindowValidator` | Startup-time forced check | `spring.data.redis.perf.*` | — |

### Diagrams

From the source code, there is a `principle.jpg` in the resources directory; it is the module architecture diagram.

---

## Capability Matrix: Coverage

| Scenario | Recommended feature | Configuration highlights |
|----------|---------------------|--------------------------|
| Fast KV access | String / Hash | Basic Redis connection |
| Hot read acceleration | L2 + stampede prevention | `enable-l2-caching: true` + Bloom |
| Concurrent dedup write | Locked read | `*WithLock` methods |
| High-contention locks | Distributed lock + local lock | `enable-local-lock: true` |
| Leaderboard | ZSet | No extra configuration |
| Dedup statistics | Set / HyperLogLog | Use Set for small data, HLL for large data |
| Rate limiting | Sliding window | Custom threshold |
| Nearby search | GEO | No extra configuration |
| Sign-in / check-in | Bitmap | No extra configuration |
| Atomic operations | Lua | Script resource management |
| Cross-instance cache consistency | L2 + keyspace notification | Redis must enable `notify-keyspace-events KEA` |
| Performance governance | PerfGuard | `perf.enabled: true` + canary validation |
| Forced config migration | MigrationWindow | Enable configuration before the deadline |
| Traffic shaping / lightweight buffering | queue() | maxLen 1 to 4999; active poll/drain; do not treat as MQ |
| Latest N entries LIFO | stack() | `push` fails when full; `latest` count ≤ 20 |
| Reliable message queue | Redis Stream | See the Stream docs, not covered in this chapter |

> For a quick README reference on bounded queues / stacks and the comparison with Stream, see the `README.md` at the project root (sections "Redis Stream" and "Bounded Queue for Big Keys").
