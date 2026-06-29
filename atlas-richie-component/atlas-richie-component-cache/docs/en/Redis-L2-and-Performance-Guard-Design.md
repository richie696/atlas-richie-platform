# Redis L2 Secondary Cache, Distributed Lock, and Performance Guard Design Notes

This document describes the unified design of `richie-component-cache` 5.0 for **business query**, **distributed lock**, and **data access governance**.

---

## 1. L2 Secondary Cache — Business Interface Query Acceleration

### 1.1 Goal

Hot reads land in the **JVM local cache (L2)** first; on a miss, Redis is consulted. Write operations go through `GlobalCache` to dual-write L2 and Redis. Cross-instance consistency relies on Redis **keyspace notifications**.

### 1.2 Configuration

```yaml
spring:
  data:
    redis:
      enable-l2-caching: true
      l2-caching-data:
        - STRING
        - HASH
        - LIST
        - SET
```

| Config | Default | Description |
|--------|---------|-------------|
| `enable-l2-caching` | `false` | Master switch |
| `l2-caching-data` | empty | Only the listed `KeyTypeEnum` values go through L2 |

Independent local region (`spring.data.local`, can coexist with GlobalCache L2):

| Enum | Cache name | Purpose |
|------|------------|---------|
| `GLOBAL_CACHE` | `global_cache` | `GlobalCache` unified region |
| `ACCESS_LOG` | `access_log` | Access logs, etc. |

### 1.3 Read Path

```
getXxxCache(key)
  → LocalCache.get(GLOBAL_CACHE) hit, return
  → miss: *Manager reads Redis
  → if non-null, LocalCache.put writes back
```

- Normal read: `value().get` / `struct().get` / `field().get` (internally goes through the L2 acceleration layer)
- Stampede prevention read: `value().getWithLock` / `struct().getWithLock` / `field().getWithLock`

**`*WithLock` and L2** (`RedisStringManager` / `RedisHashManager`):

1. `GlobalCache` checks L2 first;
2. Manager: Bloom (optional) → Redis → optimistic lock `lock:{key}` → DB;
3. On lock failure, **check L2 again** (the thread holding the lock may have already written);
4. Write-back is performed by `GlobalCache` with a unified `LocalCache.put`.

```java
String v = GlobalCache.value().get("user:1", String.class);
String v2 = GlobalCache.value().getWithLock("user:1", 3_600_000L,
        () -> userRepository.findName(1L));
```

### 1.4 Write Path

Writes through `GlobalCache` synchronously update L2 (with `put` + `expiry`) when the corresponding `KeyTypeEnum` is enabled. TTL = business timeout + `CacheFunction.getRandomExtraMillis()` (1 to 10 minute random offset, anti-avalanche).

### 1.5 Cross-Instance Sync (CacheSyncListener)

When `enable-l2-caching=true`, the component sets `notify-keyspace-events KEA` and subscribes to:

- `del` / `unlink` / `expired` → `LocalCache.remove`
- `set` / `expire` → refresh L2 from Redis by type

**Note**: Redis must support keyspace notifications. Bypassing `RedisTemplate` to write to Redis can cause brief L2 inconsistency. Stream idempotent-key events are ignored.

---

## 2. Distributed Lock — Local Secondary Lock + Redis

### 2.1 Acquisition Order (RedisLockManager)

```
[1] enable-local-lock → CacheLockManager.LOCK_POOL (same JVM)
[2] Redisson FencedLock acquisition (reentrant, watchdog)
```

### 2.2 Configuration

```yaml
spring.data.redis:
  enable-local-lock: false
```

| Item | Description |
|------|-------------|
| Local lock | Same-key high-frequency contention competes within the JVM first, easing Redis pressure |
| Redisson | FencedLock implementation, reentrant, watchdog renewal |
| Perf | Lock acquisition is wrapped by `RedisPerfGuard` + `LOCK_TRY` |

```java
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:1", 30L)) {
    if (lock.success()) { /* critical section */ }
}
```

The data stampede prevention lock `lock:{businessKey}` is separated from the business lock key; on lock failure, the path re-reads L2.

---

## 3. Redis Performance Guard (Perf Guard)

### 3.1 Capabilities

- Non-O(1) WARN (`warn-non-o1`)
- Latency `toc-soft-ms` / `toc-hard-ms`
- `block-forbidden-tiers` blocks `LINEAR_N` / `WORSE`
- String write anti-patterns (Collection / Map blobs, oversized JSON, etc.)
- BIGKEY probe hints (HLEN / LLEN / SCARD, etc.)

### 3.2 Configuration

```yaml
spring.data.redis.perf:
  enabled: false
  warn-non-o1: true
  toc-soft-ms: 8
  toc-hard-ms: 50
  block-forbidden-tiers: false
  warn-string-payload-anti-patterns: true
  block-string-payload-violations: false
```

### 3.3 Integration

Each `redis.manage.*Manager` wraps its calls with `redisPerfGuard.execute(...)`; `RedisStringManager` calls `checkStringWritePayload` before writing.

`GlobalCache` static methods are annotated with `@apiNote` to indicate complexity and ToC recommendations; **an L2 hit does not change the Redis complexity, it only reduces latency**.

---

## 4. Operations Checklist

- [ ] When L2 is enabled, confirm that `notify-keyspace-events` includes `KEA`
- [ ] Pick `l2-caching-data` types on demand, control JVM memory usage
- [ ] Hot read/write must go through `GlobalCache` to avoid bypassing Redis
- [ ] Roll out `perf.enabled` in pre-production canary before going full
- [ ] Disallow `KEYS` and unbounded `HGETALL` / `SMEMBERS` on ToC paths

---

## 5. Source Code Index

| Class | Responsibility |
|-------|----------------|
| `GlobalCache` | L2, `getWithLocalCache*`, @apiNote |
| `CacheSyncListener` | Keyspace → L2 |
| `RedisLockManager` / `CacheLockManager` | Distributed lock + local lock pool |
| `RedisPerfGuard` | Guard |
| `RedisOperationCatalog` | Complexity metadata |
| `RichieRedisProperties` | perf / L2 / lock switches |
