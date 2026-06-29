# Redis 二级缓存、分布式锁与性能守卫设计说明

本文描述 `richie-component-cache` 5.0 在 **业务查询**、**分布式锁**、**数据访问治理** 上的统一设计。

---

## 1. 二级缓存（L2）— 业务接口查询加速

### 1.1 目标

热点读优先落在 **JVM 本地缓存（L2）**，未命中再访问 Redis。写路径由 `GlobalCache` 双写 L2 + Redis；跨实例一致性依赖 Redis **键空间通知**。

### 1.2 配置

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

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `enable-l2-caching` | `false` | 总开关 |
| `l2-caching-data` | 空 | 仅列出的 `KeyTypeEnum` 走 L2 |

独立本地区域（`spring.data.local`，与 GlobalCache L2 可并存）：

| 枚举 | Cache 名 | 用途 |
|------|----------|------|
| `GLOBAL_CACHE` | `global_cache` | `GlobalCache` 统一区域 |
| `ACCESS_LOG` | `access_log` | 访问日志等 |

### 1.3 读路径

```
getXxxCache(key)
  → LocalCache.get(GLOBAL_CACHE) 命中则返回
  → 未命中：*Manager 读 Redis
  → 非 null 则 LocalCache.put 回写
```

- 普通读：`value().get` / `struct().get` / `field().get`（内部经 L2 加速层）
- 防击穿读：`value().getWithLock` / `struct().getWithLock` / `field().getWithLock`

**`*WithLock` 与 L2**（`RedisStringManager` / `RedisHashManager`）：

1. `GlobalCache` 先查 L2；
2. Manager：布隆（可选）→ Redis → 乐观锁 `lock:{key}` → DB；
3. 加锁失败时 **再次查 L2**（持锁线程可能已写入）；
4. 回写由 `GlobalCache` 统一 `LocalCache.put`。

```java
String v = GlobalCache.value().get("user:1", String.class);
String v2 = GlobalCache.value().getWithLock("user:1", 3_600_000L,
        () -> userRepository.findName(1L));
```

### 1.4 写路径

经 `GlobalCache` 写操作在对应 `KeyTypeEnum` 启用时同步 L2（`put` + `expiry`）。TTL = 业务 timeout + `CacheFunction.getRandomExtraMillis()`（1~10 分钟随机，防雪崩）。

### 1.5 跨实例同步（CacheSyncListener）

`enable-l2-caching=true` 时设置 `notify-keyspace-events KEA`，订阅：

- `del` / `unlink` / `expired` → `LocalCache.remove`
- `set` / `expire` → 按类型从 Redis 刷新 L2

**注意**：需 Redis 支持键空间通知；旁路 `RedisTemplate` 写 Redis 可能导致 L2 短暂不一致。Stream 幂等键事件会被忽略。

---

## 2. 分布式锁 — 本地二级锁 + Redis

### 2.1 获取顺序（RedisLockManager）

```
[1] enable-local-lock → CacheLockManager.LOCK_POOL（同 JVM）
[2] Redisson FencedLock 获取（可重入、看门狗）
```

### 2.2 配置

```yaml
spring.data.redis:
  enable-local-lock: false
```

| 项 | 说明 |
|----|------|
| 本地锁 | 同 key 高频争用先 JVM 内竞争，减轻 Redis |
| Redisson | FencedLock 实现，可重入、看门狗续期 |
| Perf | 加锁经 `RedisPerfGuard` + `LOCK_TRY` |

```java
try (var lock = GlobalCache.lock().optimisticWithRenewal("lock:order:1", 30L)) {
    if (lock.success()) { /* 临界区 */ }
}
```

数据防击穿锁 `lock:{businessKey}` 与业务锁 key 分离；持锁失败路径会回读 L2。

---

## 3. Redis 性能守卫（Perf Guard）

### 3.1 能力

- 非 O(1) WARN（`warn-non-o1`）
- 耗时 `toc-soft-ms` / `toc-hard-ms`
- `block-forbidden-tiers` 阻断 `LINEAR_N` / `WORSE`
- String 写入反模式（集合/Map 整包、超大 JSON 等）
- BIGKEY 探测建议（HLEN/LLEN/SCARD…）

### 3.2 配置

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

### 3.3 接入

各 `redis.manage.*Manager` 通过 `redisPerfGuard.execute(...)` 包装；`RedisStringManager` 写入前 `checkStringWritePayload`。

`GlobalCache` 静态方法 `@apiNote` 标注复杂度与 ToC 建议；**L2 命中不改变 Redis 复杂度，仅降低耗时**。

---

## 4. 运维清单

- [ ] L2 开启时确认 `notify-keyspace-events` 含 `KEA`
- [ ] `l2-caching-data` 按需选型，控制 JVM 内存
- [ ] 热点读写走 `GlobalCache`，避免旁路 Redis
- [ ] `perf.enabled` 预发灰度后再全量
- [ ] ToC 禁用 `KEYS`、无界 `HGETALL`/`SMEMBERS`

---

## 5. 源码索引

| 类 | 职责 |
|----|------|
| `GlobalCache` | L2、`getWithLocalCache*`、@apiNote |
| `CacheSyncListener` | 键空间 → L2 |
| `RedisLockManager` / `CacheLockManager` | 分布式锁 + 本地锁池 |
| `RedisPerfGuard` | 守卫 |
| `RedisOperationCatalog` | 复杂度元数据 |
| `RichieRedisProperties` | perf / L2 / 锁开关 |
