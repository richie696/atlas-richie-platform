# Richie Cache Component

基于 Redis 的完整缓存与中间件解决方案。通过 `GlobalCache` 统一门面提供 KV 存储、数据结构操作、分布式锁、消息队列、限流等全量能力，内置二级缓存、布隆过滤器、性能守卫等企业级特性。

> **目标读者**：业务服务开发者。如果你想知道"这个组件能帮我解决什么问题、怎么用"，这是你要的文档。
> **深度设计**：L2/分布式锁/性能守卫的完整设计思路见 [docs/](./docs/README.md)。

---

## 📖 目录

[TOC]



---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

### 2. 配置

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

### 3. 写代码

所有能力通过 `GlobalCache` 静态门面调用，无需注入任何 Bean：

```java
import com.richie.component.cache.GlobalCache;

// KV 读写
GlobalCache.addStringCache("user:123", "Tom", 3600_000L);
String name = GlobalCache.getStringCache("user:123");

// Hash 对象缓存
UserInfo user = new UserInfo("Tom", 25);
GlobalCache.addObjectToHash("user:info:123", user, 3600_000L);

// 防缓存击穿（带锁读）
String value = GlobalCache.getStringCacheWithLock("config:pay_ratio",
    3600_000L, () -> configService.getConfig("pay_ratio"));

// 分布式锁
try (var lock = GlobalCache.optimisticLockWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) orderService.processOrder("123");
}
```

> 所有方法均已集成 L2 二级缓存、布隆过滤器、性能守卫——零额外配置，开箱即用。

---

## 🔧 核心功能

### String / 数值 / 布尔（KV 存储）

**解决什么问题**：业务代码直接操作 `RedisTemplate` 需要处理序列化、异常、key 拼接等模板代码。不同开发者各自写工具类导致代码散乱，且无法统一做写入治理（大 Key 检测、慢查询监控）。

```java
// 基础写读
GlobalCache.addStringCache("key", "value", 10_000L);
String val = GlobalCache.getStringCache("key");

// 条件写入（不存在才写）
boolean added = GlobalCache.addStringCacheIfAbsent("token", "abc", 60_000L);

// 数值操作（自动序列化/反序列化）
GlobalCache.addIntCache("count", 100, 5_000L);
Long newVal = GlobalCache.increment("counter", 1L);

// 布尔
GlobalCache.addBooleanCache("flag", true, 3_600_000L);
```

**带来的好处**：
- 统一门面，代码风格一致，不再散落各种 Redis 工具类
- 自动集成性能守卫——写入大 Value 会自动告警/阻断
- 数值/布尔无需手动序列化，省去模板代码

---

### Hash 操作（对象缓存）

**解决什么问题**：把整个对象序列化为 JSON 存入 String 是反模式——无法部分更新、读取整个大 JSON 有 BIGKEY 风险。Hash 按字段存储可以按需读写、部分更新。

```java
// 单字段操作（部分更新）
GlobalCache.addCache2Hash("user:1", "name", "Tom");
GlobalCache.addCache2Hash("user:1", "age", 25);
String name = GlobalCache.getHashCache("user:1", "name", String.class);

// 对象存取（自动序列化）
UserInfo user = new UserInfo("Tom", 25);
GlobalCache.addObjectToHash("user:2", user, 3600_000L);
UserInfo cached = GlobalCache.getObjectFromHash("user:2", UserInfo.class);

// 批量写入
GlobalCache.addCacheAllHash("user:3", Map.of("name", "Jerry", "age", 30), 3600_000L);

// 防击穿回源
UserInfo result = GlobalCache.getObjectFromHashWithLock("user:999",
    UserInfo.class, 3600_000L, () -> userRepository.findById("999"));

// 删除字段
GlobalCache.removeHashItem("user:1", "name", "age");
```

**带来的好处**：
- 对象字段级读写，无需序列化整个对象
- 部分更新只改一个 field，不产生 BIGKEY
- 配合 L2 本地缓存，高频读取的 Hash 字段零网络开销

**注意事项**：
- **优先使用 `addObjectToHash`**，不要用 `addObjectCache` 整包序列化对象为 String 存入 Redis，后者已废弃（`@Deprecated` since 4.4.0）。Hash 存储支持按字段读写，避免反序列化整个对象。
- **不要存储复杂嵌套对象**到 Redis Hash。嵌套结构序列化后数据膨胀，存取时序列化/反序列化开销大，建议拆解后存入 MongoDB 等文档数据库，Redis 只存文档 ID。
- **单字段读写**：如果仅需获取或更新对象中的某几个字段，使用 `getHashCache(key, field, Class)` 和 `addCache2Hash(key, field, value)`，避免 `getObjectFromHash` 全量反序列化。

---

### List 操作

**解决什么问题**：List 有两种使用场景——队列场景（pop 即删除）和全量列表缓存场景（防并发重复写入）。不同场景有不同的复杂度特征和并发风险，见下文「[List 结构专项注意](#list-结构专项注意)」。

```java
// ========== 队列场景（Push/Pop） ==========
// 尾部追加
GlobalCache.addListItem("queue", "task1");
GlobalCache.rightPushListElement("queue", "task2");
GlobalCache.leftPushListElement("queue", "urgent_task");

// 头部弹出（消费即删除）
String task = GlobalCache.leftPopListElement("queue", String.class);
List<String> batch = GlobalCache.leftPopListElement("queue", 5, String.class);

// 尾部弹出（栈模式）
String last = GlobalCache.rightPopListElement("queue", String.class);
List<String> lastBatch = GlobalCache.rightPopListElement("queue", 3, String.class);

// ========== 全量列表缓存场景 ==========
// 获取全部（O(N)，N 为列表长度，大数据量警惕 BIGKEY）
List<User> users = GlobalCache.getListCache("user:list:active", User.class);

// 防缓存击穿全量读（带锁回源，推荐）
List<User> usersSafe = GlobalCache.getListCacheWithLock("user:list:active",
    -1, User.class, 3600_000L, () -> userRepository.findAllActiveUsers());

// ========== 单元素访问（O(1)，推荐替代全量读的场景） ==========
// 获取首个元素（LINDEX 0）
User first = GlobalCache.getFirstFromList("user:list:active", User.class);

// 获取最后一个元素（LINDEX -1）
User last = GlobalCache.getLastFromList("user:list:active", User.class);

// 获取指定下标元素（LINDEX index，支持负数下标）
User specific = GlobalCache.getLastFromListIndex("user:list:active", 5, User.class);

// ========== 更新/替换 ==========
// 替换整条列表（Lua 原子，DEL + RPUSH）
GlobalCache.replaceListCache("user:list:active", newUserList);

// 更新指定索引位元素（LSET，O(1)）
GlobalCache.replaceListItem("user:list:active", 3, updatedUser);

// 删除指定值（LREM，O(N) 遍历删除，大数据量慎用）
GlobalCache.removeListItem("user:list:active", "duplicate_value", 1);

// ========== 元信息 ==========
Long size = GlobalCache.getListSize("queue");

// ========== 批量初始化（非原子，仅预热场景使用） ==========
GlobalCache.batchAddToList(Map.of("list:a", listA, "list:b", listB));
```

**推荐配置**：List 数据量大时不推荐走 L2（本地内存压力大）。

---

### Set / ZSet 操作

**解决什么问题**：Set 天然去重，适合标签、黑白名单；ZSet 按分数排序，适合排行榜。

```java
// Set
GlobalCache.addSetItem("tags", "java");
GlobalCache.addSetCache("tags", Set.of("java", "redis"), 3600_000L);
boolean exists = GlobalCache.existsInSet("tags", "java");
Set<String> all = GlobalCache.getSetCache("tags", String.class);

// ZSet（排行榜）
GlobalCache.addZSetItem("rank", "user1", 99.5);
Long rank = GlobalCache.getZSetRank("rank", "user1");
Set<String> top10 = GlobalCache.reverseScoreRangeFromZSet("rank", 0, 9,
    new TypeReference<String>(){});
Double newScore = GlobalCache.incrementScore("rank", "user1", 10.0);
```

---

### Key 管理

```java
// 批量删除
GlobalCache.removeCache(List.of("k1", "k2", "k3"));

// 存在判断
boolean exists = GlobalCache.hasKey("key1");
int count = GlobalCache.countExistingKeys(List.of("k1", "k2", "k3"));

// 过期时间
GlobalCache.setExpiredTime("key1", 10_000L);
GlobalCache.rename("old", "new");
```

---

### 批量操作

**解决什么问题**：逐 key 读写 N 个值 → N 次网络往返。批量操作合并为一次 pipeline，延迟从 N × RTT 降为 1 × RTT。

```java
// 批量写入
Map<String, String> data = Map.of("k1", "v1", "k2", "v2");
GlobalCache.batchAddToString(data, 3600_000L);

// 批量读取
Map<String, String> values = GlobalCache.getValueMap(
    List.of("k1", "k2"), new TypeReference<String>(){});
```

---

## 🛡 高级能力

### 分布式锁

**解决什么问题**：跨 JVM 实例的资源互斥访问（扣库存、下单、发放优惠券）。没有分布式锁，高并发下会出现超卖、重复发放等数据一致性问题。

**三层架构**：JVM 本地锁池 → 可重入检测 → Redisson FencedLock。

- **本地锁池**：同一 JVM 内线程争抢同一把锁时，在 `ConcurrentHashMap` 层面就感知到了，不需要穿透到 Redis（0.01ms vs 1-5ms）。对业务代码完全透明。
- **看门狗续期**：Redisson 自动续期，业务处理超时不会意外释放锁。
- **乐观/悲观双模式**：乐观锁试一次即返回；悲观锁阻塞直到超时。

```java
// 乐观锁（推荐——试一次，不阻塞）
try (var lock = GlobalCache.optimisticLockWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) {
        orderService.processOrder("123");
    }
}

// 悲观锁（阻塞直到获取或超时）
try (var lock = GlobalCache.pessimisticLock("lock:order:456", 10L)) {
    if (lock.success()) {
        orderService.processOrder("456");
    }
}
```

**推荐配置**：

```yaml
spring.data.redis:
  enable-local-lock: true    # 已默认开启
```

**带来的好处**：
- 100 个线程并发抢同一把锁，本地锁池把 99 次网络 RTT 降为 0
- `AutoCloseable` —— try-with-resources 自动释放，不会忘记 unlock
- 可重入检测——同一线程重复加锁只在本地计数，不走 Redis

#### 批量锁（CacheBatchLock）

**解决什么问题**：某些业务场景需要同时锁定多个资源执行原子操作（如批量扣库存、批量状态变更）。如果逐 key 循环加锁，存在死锁风险（锁顺序不一致时互相等待），且单个 key 加锁失败后难以回滚已持有锁。

`CacheBatchLock` 提供一次性锁定多个 key 的能力——要么全部锁定成功，要么全部释放。避免循环加锁的死锁和部分失败问题。

```java
Collection<String> keys = Set.of("lock:stock:1001", "lock:stock:1002", "lock:stock:1003");
long timeout = 20000L;
TimeUnit unit = TimeUnit.MINUTES;
try (var lock = GlobalCache.batchLock(keys, timeout, unit)) {
    if (!lock.isSuccess()) {
        // 至少有一个 key 未获取到，已自动释放所有已获取的锁
        return ResultVO.getError("获取批量锁失败。");
    }
    // 所有 key 已锁定，执行批量业务逻辑
    stockService.batchDeduct(keys);
}
```

> 内部实现：逐 key 尝试获取乐观锁，任何一个 key 失败后立即释放所有已获取锁，返回空的 `CacheBatchLock`。注意锁超时建议按业务最大执行时间预估，避免锁提前释放或过期残留。
>

---

### 防缓存击穿（带锁回源）

**解决什么问题**：高并发下热点 key 过期瞬间，大量请求同时穿透到数据库，瞬间打垮 DB。拦截链路：L2 → 布隆过滤器 → Redis → 分布式锁 → DB。

```java
// String 防击穿
String value = GlobalCache.getStringCacheWithLock("config:app:pay_ratio",
    3600_000L, () -> configService.getConfig("pay_ratio"));

// Hash 防击穿
UserInfo user = GlobalCache.getObjectFromHashWithLock("user:profile:123",
    UserInfo.class, 3600_000L, () -> userService.findById("123"));
```

**推荐配置**：

```yaml
platform.cache.bloom-filter:
  enable: true                          # 数据量 > 1 万的热点推荐开启
  type: REDISSON                        # 多实例推荐 REDISSON
  expected-insertions: 10000000
  false-probability: 0.001

spring.data.redis.enable-l2-caching: true
spring.data.redis.l2-caching-data: [STRING, HASH]
```

**带来的好处**：
- 四层防护——每一层命中就直接返回，只有最终层才到 DB
- 锁获取失败的线程不会空等，重试读已有缓存
- DB 回填数据自动写入缓存和布隆过滤器

---

### L2 二级缓存

**解决什么问题**：Redis 热点 key 被大量实例并发读取 → Redis CPU 瓶颈。缓存雪崩时大量 key 同时过期 → 全部打到 Redis。L2 在 Redis 前加一层 JVM 本地缓存，读路径 `L1 → L2 → DB`。

**读写路径**：

```
读：GlobalCache.getXxx(key)
  ├── LocalCache.get(key)          ← L1 本地，约 0.1ms
  │     └── 命中 → return
  ├── Redis GET(key)                ← L2 Redis，约 1-5ms
  │     └── 命中 → 回写 L1 → return
  └── 未命中 → *WithLock 回源 DB

写：GlobalCache.addXxx(key, value, ttl)
  ├── Redis SET
  ├── 双写 L1 本地缓存
  └── 键空间通知 → 其他实例失效 L1
```

**推荐配置**：

```yaml
spring.data.redis:
  enable-l2-caching: true
  l2-caching-data:
    - STRING                          # 热点 String 推荐走 L2
    - HASH                            # 高频对象推荐走 L2
    # LIST/SET 谨慎——数据量大时本地内存压力大
```

**带来的好处**：
- L1 命中 ≈ 0.1ms，远低于 Redis 的 1-5ms
- 即使 Redis 大量 key 同时过期，L1 层仍有数据兜底
- 跨实例通过键空间通知同步失效，短暂脏读窗口在可接受范围

---

### 布隆过滤器

**解决什么问题**：大量请求查询不存在的 key（如恶意攻击）→ 缓存穿透到 DB。布隆过滤器高效判定 key "一定不存在"，直接在入口拦截。

```java
// 配置开启后，getStringCacheWithLock 自动联动布隆
// 布隆判定"不存在" → 直接回源，不查 Redis
String value = GlobalCache.getStringCacheWithLock("config:app",
    3600_000L, () -> configRepo.findByKey("app"));
```

**推荐配置**：

```yaml
platform.cache.bloom-filter:
  enable: true
  type: REDISSON            # 多实例用 REDISSON，单实例用 GUAVA
  expected-insertions: 10000000
  false-probability: 0.001  # 0.1% 误判率
```

---

### 性能守卫（RedisPerfGuard）

**解决什么问题**：开发者在 ToC 核心链路上无意调用 `KEYS *`、全量 `HGETALL` 等 O(n) 操作；大 Value 导致网络瓶颈和 GC 压力，没有预警机制。性能守卫在运行时按三个维度治理：

| 维度 | 检测内容 | 可选阻断 |
|------|---------|----------|
| 复杂度 | 非 O(1) 操作（`KEYS *`、全量 `HGETALL` 等）打 WARN | `block-forbidden-tiers: true` |
| 延迟 | 超过 `toc-soft-ms` 软阈值 WARN，超过 `toc-hard-ms` ERROR | — |
| String 载荷 | 集合/Map 整包写入 String、超大 JSON、大对象 | `block-string-payload-violations: true` |

**推荐配置**：

```yaml
spring.data.redis.perf:
  enabled: true                    # 开启运行时治理
  warn-non-o1: true                # 非 O(1) 操作告警
  toc-soft-ms: 8                   # 超过 8ms WARN
  toc-hard-ms: 50                  # 超过 50ms ERROR
```

**带来的好处**：
- 运行时检测，不依赖代码评审发现慢查询
- 阶梯式灰度：先只告警不阻断 → 消除告警后开启阻断
- `MigrationWindow` 到期后强制开启，防止治理被遗忘

---

### 多 Redis 实例路由

```yaml
spring.data.redis:
  host: primary.redis.com
  slaves:
    order-redis:
      host: order.redis.com
      port: 6379
    user-cache:
      host: user.redis.com
      port: 6380
```

**解决什么问题**：不同业务隔离到不同 Redis 实例；读写分离；多环境共享。

---

### Lua 脚本 / 限流 / GEO / HyperLogLog / Bitmap

```java
// Lua（原子执行多条命令）
String result = GlobalCache.evalLua(
    "return ARGV[1]", List.of(), List.of("ok"), String.class);

// 限流（滑动窗口，60s 内最多 100 次）
boolean pass = GlobalCache.tryAcquire("rl:/api/pay", 100, 60);

// 地理位置
GlobalCache.addGeo("geo:store", 116.39, 39.9, "beijing");
Distance d = GlobalCache.geoDist("geo:store", "beijing", "shanghai");

// HyperLogLog（UV 统计，12KB 统计 2^64 元素）
GlobalCache.pfAdd("uv:2025-01-01", "uid1", "uid2");
long uv = GlobalCache.pfCount("uv:2025-01-01");

// Bitmap（签到打卡，极致压缩）
GlobalCache.setBit("sign:202501", 1, true);
boolean day1 = GlobalCache.getBit("sign:202501", 1);
```

---

## 📨 Redis Stream 消息队列

基于 Redis Stream 的**真正消息队列**（组件内首选 MQ 能力）。适用于需要**可靠投递、消费组、消费者失效恢复**但不想引入 Kafka/RabbitMQ 的场景。

> **与 `GlobalCache.queue()` 的区别**：`queue()` 是有界 Redis List + **主动拉**（`poll` / `drain`），不拉不消费，无消费组/ACK/死信，满时丢最老——本质是**削峰缓冲工具**与轻量异步通道，不是 MQ。可靠投递、多消费者协作、可观测消费链路请用本节 Stream；仅在极轻量场景，或**云 PaaS Redis 不支持 Stream 部分特性**、又不愿为此上 RocketMQ/RabbitMQ 时，才将 `queue()` 作为退而求其次的 List 方案。

### 解决什么问题

- 业务上需要一个轻量级消息队列，但不想维护 Kafka/RabbitMQ 集群
- 项目已经依赖 Redis，零额外基础设施成本
- 需要消费组、ACK 机制、死信队列、幂等去重
- 需要监控积压、处理耗时、错误率等可观测性

### 架构总览

```
Producer → GlobalCache.stream().publish(key, message)
                ↓
         Redis Stream (XADD)
                ↓
    RedisStreamReactor (自适应轮询: 有消息 50ms 拉取 / 无消息长轮询)
                ↓
    RedisStreamEventBus (Reactor 多播, 1000 容量背压缓冲)
                ↓
    AbstractStreamConsumer (过滤 → 反序列化 → 幂等去重 → Tracing → handle())
                ↓
    EventContext.ack() / onError() / 死信队列
```

### 快速使用

#### 1. YAML 配置消费者

```yaml
platform:
  cache:
    redis:
      stream:
        consumers:
          enabled: true
          cleanup:
            interval: 1h
            default-max-len: 3000
          configs:
            order-events:
              stream-key: "order-events"
              group: "order-processors"
              consumer: "order-consumer-1"
              target-type: "com.example.domain.OrderEvent"
              auto-ack: true
              concurrency: 2
              error-strategy: RETRY
              idempotency-enabled: true
```

#### 2. 编写消费者

```java
@RedisStreamConsumer("order-events")
public class OrderEventConsumer extends AbstractStreamConsumer<OrderEvent> {

    @Override
    protected void handle(OrderEvent payload, EventContext ctx) {
        orderService.processOrder(payload);
        // autoAck=true 时框架自动 ACK
    }

    @Override
    protected void onError(Throwable e, OrderEvent payload, EventContext ctx) {
        log.error("处理订单事件失败", e);
        sendToDeadLetterQueue(payload, e, ctx);       // 可选：发到死信队列
    }

    @Override
    protected String buildIdempotencyKey(OrderEvent payload, String recordId) {
        return payload.getOrderId();  // 覆盖幂等键（默认 recordId）
    }
}
```

#### 3. 发布消息

```java
// 发布 Map
GlobalCache.stream().publish("order-events", Map.of("orderId", "1001"));

// 发布对象（需实现 BaseStreamMessage）
OrderEvent event = new OrderEvent("1001", "99.99");
GlobalCache.stream().publish("order-events", event);
```

### 核心特性

| 特性 | 说明 |
|------|------|
| **错误策略** | `SKIP`（跳过继续）、`RETRY`（重试后失败记录）、`NO_ACK`（留待后续） |
| **幂等去重** | 内存 ConcurrentHashMap + Redis SETNX 双层，Redis 不可用时降级到纯内存 |
| **死信队列** | GLOBAL / BY_MESSAGE_TYPE / BY_SOURCE_STREAM / HYBRID 四种策略 |
| **消息清理** | 定时 XTRIM + 分布式锁互斥，多实例只有一个执行清理 |
| **可观测性** | Actuator 端点 `/actuator/redis-stream` + Micrometer 指标 + 积压监控 |

完整配置项和监控指标见 [docs/Redis-Stream-使用指南.md](./docs/Redis-Stream-使用指南.md)。

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

### 1. Redis 连接（`spring.data.redis`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `host` | String | `localhost` | Redis 主机 |
| `port` | int | `6379` | Redis 端口 |
| `password` | String | — | Redis 密码 |
| `database` | int | `0` | Redis 库索引 |
| `timeout` | Duration | `3s` | 操作超时时间 |
| `server-type` | enum | `STANDALONE` | 部署类型：`STANDALONE` / `SENTINEL` / `CLUSTER` |
| `slaves` | Map | — | 命名子 Redis 实例（用于读写分离 / 多业务隔离），详见下文 §1.1 |

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

| 对比维度 | Lettuce | Jedis |
|---------|---------|-------|
| 线程安全 | ✅ 是，单例复用 | ❌ 否，需连接池配合 |
| 连接模式 | 异步 + 同步 + 响应式 | 同步阻塞 |
| 底层 IO | Netty epoll（Linux 高性能） | BIO / NIO |
| Redis 集群 | 原生支持 | 需要额外的哨兵/集群适配 |
| 连接保活（epoll） | ✅ 支持 TCP_USER_TIMEOUT | ❌ JDK SO_KEEPALIVE 粗粒度 |

结论：Lettuce 在连接复用、多线程并发、Linux 内核特性利用上优于 Jedis。Jedis 的优势仅在于上手简单，但生产环境推荐 Lettuce。

---

##### 1.2.2 连接池配置（`spring.data.redis.lettuce.pool.*`）

> 这是继承自 Spring Boot `DataRedisProperties.Lettuce` 的标准连接池配置。

Lettuce 虽然是异步驱动，但在同步调用模式下仍然使用连接池来限制并发连接数。**连接池没有通用标准配置**，因为最优值取决于 Redis 服务器性能、网络带宽、应用并发量等因素。推荐的调整方式：

1. 根据从业务侧统计到的并发量、访问峰值做一个初步估算
2. 设置一组初始值
3. 通过压测工具压测，观察 `RedisPool Exhausted` 异常、连接等待耗时等指标
4. 反复调整直到找到平衡点

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `pool.max-active` | int | `8` | 最大活跃连接数（压测重点调整项）|
| `pool.max-idle` | int | `8` | 最大空闲连接数 |
| `pool.min-idle` | int | `0` | 最小空闲连接数 |
| `pool.max-wait` | Duration | `-1ms` | 获取连接的最大等待时间（-1 = 无限等待）|
| `pool.time-between-eviction-runs` | Duration | — | 空闲连接驱逐线程运行间隔 |

---

##### 1.2.3 Epoll 连接模式（Linux 高性能 IO）

> ⚠️ **本节特性仅 Linux x86_64 / aarch64 / riscv64 系统支持**。非 Linux 系统下会收到 Lettuce 的警告日志且配置不会生效。

**什么是 epoll？**

epoll 是 Linux 内核提供的**改进型 IO 多路复用接口**，是传统 `select` / `poll` 的增强替代。核心区别：

| 特性 | select / poll | epoll |
|------|-------------|-------|
| **FD 上限** | 有限（默认 2048，需重新编译内核扩大） | 无上限（仅受 `/proc/sys/fs/file-max` 限制，1GB 内存约 10 万）|
| **效率与连接数的关系** | 线性扫描全部 FD，连接越多越慢 | 仅操作"活跃的"FD，空闲连接不参与扫描 |
| **内核通信方式** | 用户空间与内核空间消息拷贝 | **mmap 共享内存**，避免内核态/用户态数据拷贝 |
| **触发模式** | 仅水平触发（持续通知直到状态变更） | 支持水平触发 + **边缘触发**（仅状态变化时通知一次） |
| **适用场景** | 少量活跃连接 | 大量连接 + 少量活跃（典型 WAN 场景） |

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

| 参数 | 含义 | 默认值 | 生产推荐 | 备注 |
|------|------|--------|---------|------|
| `idle` | 连接空闲多久后开始发 keep-alive 探针 | 2 小时 | 90s | Linux 内核默认 2h，对云环境过长 |
| `interval` | 相邻探针的间隔 | 75s | 60s | 过于密集增加服务器负担，过于稀疏检测太慢 |
| `count` | 连续无响应多少次后判定死亡 | 9 | 6 | 结合 interval 计算总检测时间 |
| `tcp-user-timeout` | TCP 等待对端 ACK 的最长时间 | 7875s | 450s | 推荐 ≈ idle + interval × count |

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

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| `USAGE_RADIO`（默认） | 池使用率达到 `ratio/(ratio+1)` 时释放旧缓存 | 通用场景，平衡 CPU 和内存 |

**`memory-release-ratio` 的影响**（仅 `USAGE_RADIO` 模式生效）：

| ratio | 触发阈值 | 内存倾向 | CPU 倾向 | 推荐场景 |
|-------|---------|---------|---------|---------|
| 1 | 50% | 低内存 | 较高 CPU（频繁释放/再分配） | 低 QPS、内存敏感 |
| 3（默认）| 75% | 中等 | 中等 | 通用场景 |
| 5 | 83% | 较高 | 较低 | 高 QPS 服务 |
| 10 | 90% | 高 | 低 | 极高 QPS、CPU 敏感 |

**带来的好处**：
- 直接内存在低峰期可收敛，避免 `OutOfDirectMemoryError`
- 高 QPS 服务用高 ratio（减少释放/再分配的 CPU 开销），低 QPS 服务用低 ratio（节省内存）
- 通过配置调节，无需堆 JVM 参数间接调优

---

#### 1.3 RESP 协议版本（`spring.data.redis.protocol-version`）

| 取值 | 适用 | 特点 |
|------|------|------|
| `RESP2` | Redis 2 - 5 | 传统协议 |
| `RESP3` | Redis 6+ | 性能更好、支持客户端缓存、避免响应数据二次转换 |

Redis 6+ 强烈建议 RESP3。Redis 禁用 HELLO 命令时需用 RESP2 避免连接失败。

---

### 2. L2 二级缓存（`spring.data.redis`）

| 配置项 | 类型 | 默认值 | 关联功能 | 说明 |
|--------|------|--------|----------|------|
| `enable-l2-caching` | boolean | `false` | L2 二级缓存 | 是否启用 L1 本地缓存 |
| `l2-caching-data` | List<KeyTypeEnum> | `[]` | L2 二级缓存 | 哪些数据类型走 L2，可选 `STRING` / `HASH` / `LIST` / `SET` |

> TTL 自动追加 1~10 分钟随机偏移防雪崩。`enable-l2-caching=false` 时配置无效但 `*WithLock` 的本地查路径仍会工作。

**多实例一致性**：依赖 Redis 键空间通知

```bash
# Redis 服务端必须开启
notify-keyspace-events KEA
```

短暂脏读窗口：写入 Redis 到收到通知之间，跨实例 L1 仍是旧值。建议 L1 TTL 不超过「原始 TTL × 2」。

---

### 3. 分布式锁（`spring.data.redis`）

| 配置项 | 类型 | 默认值 | 关联功能 | 说明 |
|--------|------|--------|----------|------|
| `enable-local-lock` | boolean | **`true`** ⚠️ 已默认开启 | JVM 本地锁池 | 同一 JVM 内对同一锁 key 走 `ConcurrentHashMap` 查表，关闭则每次直接打 Redisson |

> **何时考虑关闭**：极端情况下锁 key 极多、命中率极低、`ConcurrentHashMap` 内存膨胀。实际业务中几乎不会出现。

---

### 4. 性能守卫（`spring.data.redis.perf`）

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

### 5. Stream 幂等去重（`spring.data.redis.stream-idempotency`）

> 关联功能：[幂等去重](#-redis-stream-消息队列)。仅在 Stream 消费者启用且 `idempotency-enabled=true` 时生效。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `redis-ttl` | Duration | `24h` | Redis 幂等键 TTL（跨实例兜底）|
| `memory-ttl` | Duration | `1h` | 内存幂等键 TTL（进程内快速去重）|
| `key-prefix` | String | `idemp:stream:` | 幂等键前缀 |

**降级策略**：Redis 不可用时自动降级为纯内存去重，防止全量拒绝。

---

### 6. 本地缓存（`spring.data.local`）

> 关联功能：[本地缓存](#-本地缓存)。独立于 L2 的业务显式使用区域，基于 JSR-107。

#### 6.1 顶层配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `provider` | enum | `EHCACHE` | 底层实现：`EHCACHE` / `CAFFEINE` / `CACHE2K` |
| `cache-definitions` | Set<CacheDefinition> | `[]` | 缓存定义列表，详见 §6.2 |

#### 6.2 缓存定义（`spring.data.local.cache-definitions[].*`）

每条 `CacheDefinition` 定义一个独立的本地缓存区域（按 `name` 区分）：

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `name` | String | — | 缓存区域名称（必填，业务按名获取）|
| `expiry-policy` | enum | — | 过期策略：`CREATED` / `ACCESSED` / `ETERNAL` / `MODIFIED` / `TOUCHED` |
| `expiry` | int | `5` | 过期时间数值（`ETERNAL` 时无效）|
| `expiry-unit` | TimeUnit | `MINUTES` | 过期时间单位（`ETERNAL` 时无效）|
| `statistics-enabled` | boolean | `false` | 是否启用命中率/操作数统计 |
| `store-by-value` | boolean | `false` | `true` 按值存（深拷贝）、`false` 按引用存 |
| `read-through` | boolean | `false` | 启用 read-through，miss 时调用 CacheLoader 回源（需配合 `cache-loader-class-name`）|
| `cache-loader-class-name` | String | — | `javax.cache.integration.CacheLoader` 全类名 |
| `write-through` | boolean | `false` | 启用 write-through，put/remove 时回调 CacheWriter（需配合 `cache-writer-class-name`）|
| `cache-writer-class-name` | String | — | `javax.cache.integration.CacheWriter` 全类名 |
| `cache2k-refresh-ahead` | boolean | `false` | **仅 CACHE2K** 生效：过期前异步刷新（需 `read-through=true`）|
| `cache2k-loader-thread-count` | Integer | cache2k 默认 | **仅 CACHE2K** 生效：后台刷新线程数 |

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

### 7. 组件级配置（`platform.cache`）

| 配置项 | 类型 | 默认值 | 关联功能 | 说明 |
|--------|------|--------|----------|------|
| `cache-provider` | enum | `REDIS` | 全局选择 | 当前实现：仅 `REDIS` |
| `bloom-filter.*` | — | — | 布隆过滤器 | 详见 §7.1 |

#### 7.1 布隆过滤器（`platform.cache.bloom-filter`）

> 关联功能：[布隆过滤器](#布隆过滤器)。`enable=false` 时其他配置无效。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enable` | boolean | `false` | 总开关 |
| `type` | enum | `REDISSON` | 实现：`REDISSON`（跨实例共享）/ `GUAVA`（单实例，零网络）|
| `key` | String | `platform:cache:bloom:global` | 布隆数据 key（Redisson 模式）|
| `expected-insertions` | long | `10_000_000` | 预期插入数量（建议略大于实际最大值 20~50%）|
| `false-probability` | double | `0.001` | 误判率（0.001 = 0.1%，最低 0.0001 极低误判）|

**内存估算**（公式：`bits ≈ -n × ln(p) / (ln2)²`）：

| n | p | 内存 |
|---|----|------|
| 1_000_000 | 0.001 | 1.8 MB |
| 10_000_000 | 0.001 | 18 MB |
| 100_000_000 | 0.001 | 180 MB |

---

### 8. Stream 消费者（`platform.cache.redis.stream.consumers`）

> 关联功能：[Redis Stream 消息队列](#-redis-stream-消息队列)。

#### 8.1 顶层配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用 YAML 自动装配消费者（关闭后需手动在构造函数初始化）|
| `configs` | Map<String, ConsumerConfig> | `{}` | 消费者配置映射，key 对应 `@RedisStreamConsumer("xxx")` |
| `cleanup.*` | — | — | 消息清理服务配置，详见 §8.3 |

#### 8.2 单个消费者（`configs.<name>.*`）

| 配置项 | 类型 | 默认值 | 必填 | 说明 |
|--------|------|--------|:----:|------|
| `stream-key` | String | — | ✅ | Stream 键名 |
| `group` | String | — | ✅ | 消费者组名 |
| `consumer` | String | `default-consumer` | — | 消费者名（多实例需不同）|
| `target-type` | Class | — | ✅ | 消息负载类型全类名（DLQ 消费者可省略）|
| `auto-ack` | boolean | `true` | — | 自动确认 |
| `concurrency` | int | `1` | — | 并发处理数 |
| `count` | int | `1` | — | 单次拉取消息数（量大可调 10-50）|
| `error-strategy` | enum | `SKIP` | — | `SKIP` / `RETRY` / `NO_ACK` |
| `max-retries` | int | `3` | — | `RETRY` 策略下最大重试次数 |
| `retry-delay` | Duration | `1s` | — | `RETRY` 策略下重试间隔 |
| `idempotency-enabled` | boolean | `true` | — | 启用幂等去重 |
| `auto-start` | boolean | `true` | — | 自动启动消费者 |
| `max-len` | Long | `0` | — | Stream 最大保留消息数（0 用全局 `cleanup.defaultMaxLen`）|

**错误策略对比**：

| 策略 | 行为 | 适用 |
|------|------|------|
| `SKIP` | 跳过错误消息并 ACK | 数据可丢弃的日志类 |
| `RETRY` | 自动重试一次，失败后记录 | 大部分业务 |
| `NO_ACK` | 不确认消息留待后续 | 不允许数据丢失 |

#### 8.3 消息清理（`consumers.cleanup`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `interval` | Duration | `1h` | 清理任务执行间隔（Spring Boot Duration 格式：`1h` / `30m` / `PT1H`）|
| `default-max-len` | Long | `3000` | 全局默认最大保留消息数（消费者 `maxLen=0` 时使用）|

> 清理服务使用分布式锁 `redis:stream:cleanup:lock:{serviceName}`，多实例只有一个执行。

---

### 9. Stream 监控（`platform.cache.redis.stream.monitoring`）

> 关联功能：[监控与可观测性](#9-stream-监控platformcacheredisstreammonitoring)。`enabled=false` 时所有子配置失效。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 监控总开关 |
| `health-check.*` | — | — | 健康检查，详见 §9.1 |
| `metrics.*` | — | — | 指标采集，详见 §9.2 |
| `performance.*` | — | — | 性能指标，详见 §9.3 |
| `error-monitoring.*` | — | — | 错误监控，详见 §9.4 |
| `business-monitoring.*` | — | — | 业务监控，详见 §9.5 |

#### 9.1 健康检查（`health-check`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用健康检查（汇总到 `/actuator/health`）|
| `interval` | Duration | `30s` | 检查间隔（建议 30s-60s）|
| `timeout` | Duration | `5s` | 单次检查超时（建议 3s-5s）|

#### 9.2 指标采集（`metrics`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 上报 Micrometer 指标 |
| `detailed` | boolean | `true` | 直方图 / 分位数（P50/P95/P99）。高 QPS 建议关 |
| `sampling-rate` | double | `1.0` | 采样率 0.0-1.0，高 QPS 建议 0.05-0.3 |

#### 9.3 性能指标（`performance`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用性能统计 |
| `record-processing-time` | boolean | `true` | 记录消息处理耗时 |
| `record-polling-time` | boolean | `true` | 记录拉取操作耗时 |
| `record-publishing-time` | boolean | `true` | 记录消息发布耗时 |

#### 9.4 错误监控（`error-monitoring`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用错误指标采集 |
| `classify-by-type` | boolean | `true` | 按异常类型分类（便于差异化告警）|
| `record-stack-trace` | boolean | `false` | 记录堆栈（开销大，仅定位时临时开）|

#### 9.5 业务监控（`business-monitoring`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用业务计数 |
| `record-message-count` | boolean | `true` | 记录发布/消费/ACK 计数 |
| `record-retry-count` | boolean | `true` | 记录重试计数 |
| `record-ack-count` | boolean | `true` | 记录确认计数 |

---

### 10. Stream 链路追踪（`platform.cache.redis.stream.tracing`）

> 关联功能：[监控与可观测性](#10-stream-链路追踪platformcacheredisstreamtracing)。W3C TraceContext 透传到 Stream 消息。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用追踪 |
| `service-name` | String | `redis-stream-service` | 服务名称 |
| `service-version` | String | `1.0.0` | 服务版本 |
| `sampling.probability` | double | `1.0` | 采样率 0.0-1.0 |
| `sampling.adaptive` | boolean | `false` | 启用自适应采样 |
| `attributes.record-message-content` | boolean | `false` | 记录消息内容（注意敏感数据）|
| `attributes.record-message-metadata` | boolean | `true` | 记录消息元数据 |
| `attributes.record-processing-time` | boolean | `true` | 记录处理时间 |
| `attributes.record-error-details` | boolean | `true` | 记录错误详情 |
| `events.record-message-received` | boolean | `true` | 记录消息接收事件 |
| `events.record-processing-started` | boolean | `true` | 记录处理开始事件 |
| `events.record-processing-completed` | boolean | `true` | 记录处理完成事件 |
| `events.record-message-acknowledged` | boolean | `true` | 记录 ACK 事件 |
| `events.record-retry-events` | boolean | `true` | 记录重试事件 |
| `error-tracing.record-stack-trace` | boolean | `false` | 记录错误堆栈 |
| `error-tracing.record-error-context` | boolean | `true` | 记录错误上下文 |
| `error-tracing.max-error-message-length` | int | `1000` | 错误消息最大长度 |
| `otlp.*` | — | — | OTLP 导出器，详见 §10.1 |
| `zipkin.*` | — | — | Zipkin 导出器，详见 §10.2 |
| `logging.*` | — | — | 日志导出器，详见 §10.3 |

#### 10.1 OTLP 导出器（`tracing.otlp`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用 OTLP 导出（兼容 Jaeger / Zipkin / Elastic APM / New Relic / DataDog）|
| `endpoint` | String | `http://localhost:4317` | OTLP 端点 |
| `service-name` | String | `redis-stream-service` | 服务名 |
| `protocol` | String | `http` | 协议：`grpc` / `http` |
| `timeout-seconds` | int | `30` | 超时时间 |

#### 10.2 Zipkin 导出器（`tracing.zipkin`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用 Zipkin 导出 |
| `endpoint` | String | `http://localhost:9411/api/v2/spans` | Zipkin 端点 |
| `service-name` | String | `redis-stream-service` | 服务名 |

#### 10.3 日志导出器（`tracing.logging`）

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 启用日志导出（默认开）|
| `level` | String | `INFO` | 日志级别 |

---

### 11. OTLP 指标导出（`management.otlp.metrics`）

> 关联功能：[OpenTelemetry 指标导出](#11-otlp-指标导出managementotlpmetrics)。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 启用 OTLP 指标导出 |
| `endpoint` | String | — | OTLP 端点 |
| `service-name` | String | `redis-stream-service` | 服务名 |
| `timeout-seconds` | int | `30` | 超时时间 |

---

### 12. 配置总览（按功能定位）

> 不确定去哪儿查？按这个表反过来定位。

| 你想改什么 | 配置前缀 | 章节 |
|------------|----------|------|
| Redis 连接 | `spring.data.redis` | §1 |
| 多 Redis 实例 | `spring.data.redis.slaves` | §1.1 |
| Lettuce 调优 | `spring.data.redis.lettuce` | §1.2 |
| L2 二级缓存 | `spring.data.redis.enable-l2-caching` | §2 |
| JVM 本地锁池 | `spring.data.redis.enable-local-lock` | §3 |
| 性能守卫 | `spring.data.redis.perf` | §4 |
| Stream 幂等 | `spring.data.redis.stream-idempotency` | §5 |
| 本地缓存 | `spring.data.local` | §6 |
| 布隆过滤器 | `platform.cache.bloom-filter` | §7.1 |
| Stream 消费者 | `platform.cache.redis.stream.consumers` | §8 |
| Stream 消息清理 | `platform.cache.redis.stream.consumers.cleanup` | §8.3 |
| Stream 监控 | `platform.cache.redis.stream.monitoring` | §9 |
| Stream 链路追踪 | `platform.cache.redis.stream.tracing` | §10 |
| OTLP 指标导出 | `management.otlp.metrics` | §11 |

---

## 🎯 最佳实践

### Key 命名规范

```
{business}:{object}:{id}:{field}
user:info:123
order:status:456
lock:order:789
```

### 缓存策略选择

| 场景 | 推荐方案 | 配置 |
|------|---------|------|
| 热点 KV 读多写少 | L2 + 防击穿 | `enable-l2-caching: true` + `*WithLock` |
| 高并发锁争用 | 分布式锁 + 本地锁 | `enable-local-lock: true`（默认）|
| 大列表缓存 | `*WithLock` 防重复 | 不走 L2，用防击穿方法 |
| 排行榜 | ZSet | 无需额外配置 |
| 去重统计（大量） | HyperLogLog | 12KB 统计 2^64 元素 |
| 去重统计（少量） | Set | O(1) 成员判断 |
| 限流 | `tryAcquire` | Lua 原子实现 |
| 附近查询 | GEO | 无需额外配置 |
| 签到打卡 | Bitmap | 1 亿用户每天约 12MB |
| 轻量 MQ | Redis Stream | 零额外基础设施 |

### 过期时间策略

- **热点数据**：短过期（秒级），配合 L2 读放大
- **配置数据**：中等过期（分钟级）
- **基础数据**：长过期（小时级），TTL 加随机偏移防雪崩

### 性能优化

- 热点读必须开 L2 + `*WithLock`，避免 Redis 成为瓶颈
- 批量操作代替循环逐 key 读写，减少网络 RTT
- 预发环境灰度 `perf.enabled`，消除 `[RedisPerf]` 告警后再上生产
- ToC 核心链路避免 `KEYS *`、全量 `HGETALL`/`SMEMBERS`

### 大 Key 阈值参考

大 Key 会导致 Redis 主线程阻塞、主从同步延迟、AOF 重写卡顿、集群迁移卡死等隐患。以下阈值综合了 **Redis 内部编码优化分界线**与**组件性能守卫默认值**，两者对齐、互为验证。

#### 集合类型阈值

| 类型   | 编码优化阈值                      | 推荐业务上限       | 越过编码线的影响                  | 典型隐患命令                                                                   |
|------|-----------------------------|--------------|---------------------------|--------------------------------------------------------------------------|
| list | 512 元素（quicklist 节点切换）      | **5,000** 元素 | 节点从 ziplist 退化为链表，单节点内存增大 | `LRANGE 0 -1`（O(N) 全量）；**List 无去重能力，任何去重方案都需 O(N) 遍历，必须借助伴生 Set 在写入前拦截** |
| set  | 512 元素（intset → hashtable）  | **5,000** 元素 | 内存暴增 2~3 倍，rehash 卡顿      | `SMEMBERS`（O(N) 全量）                                                      |
| zset | 128 元素（ziplist → skiplist）  | **5,000** 元素 | 内存暴增 2~3 倍                | `ZRANGE 0 -1`（O(N)）、`ZREMRANGEBYRANK`（O(log N+M)）                        |
| hash | 512 字段（ziplist → hashtable） | **1,000** 字段 | 内存暴增 2~3 倍，rehash 卡顿      | `HGETALL`（O(N)）、`HKEYS`（O(N)）                                            |

> **有界队列 / 栈**（非 Stream MQ）：`queue()` FIFO、**主动拉**消费，适合削峰与轻量缓冲；`stack()` LIFO、满则拒绝压入。二者均不能替代 [Redis Stream 消息队列](#-redis-stream-消息队列)。`maxLen` 合法 **1～4,999**；默认不可变，仅 `grow()` 单次 ×2 至封顶。勿与 `rawList()` 共 key；创建时校验 Redis 类型为 none/list。`drain` / `latest` 的 `count` 为 **1～20**；读路径反序列化失败打 **WARN**（批量跳过坏元素，`poll`/`pop` 已弹出但失败时返回 `null`）。

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

#### Key 名称

| 项目 | 建议 |
|------|------|
| Key 长度 | < 128 字节（Redis 限制 512MB，但长 key 增加内存与比较开销） |
| 命名规范 | `{业务}:{实体}:{ID}`，避免空格与特殊字符 |

#### 大 Key 检测手段

| 方式 | 适用场景 |
|------|----------|
| `redis-cli --bigkeys` | 离线扫描，快速定位 Top N 大 Key |
| `MEMORY USAGE key` | 在线查看单 key 内存占用（Redis 4.0+） |
| `redis-cli --scan --pattern '*' \| xargs -I{} redis-cli MEMORY USAGE {}` | 批量扫描（慎用，生产建议采样） |
| 组件内置 `RedisPerfGuard` | 运行时自动检测：非 O(1) 调用 WARN + BIGKEY 探测建议（HLEN/LLEN/SCARD/ZCARD），受 `spring.data.redis.perf.log-big-key-probe-hints=true` 控制 |
| 慢查询日志 `SLOWLOG GET` | 发现延迟 ≥ `toc-hard-ms`（默认 50ms）的可疑操作 |

#### 大 Key 治理建议

- **删除用 UNLINK 而非 DEL**：`GlobalCache.removeCache()` 内部已使用 `UNLINK`（异步释放内存），避免主线程阻塞。业务代码禁止自行调用 `DEL`。
- **分片代替单 Key**：大 Hash 拆成 `key:shard:{0..N}`，大 List 拆成多段，大 Set 按业务维度分桶。
- **渐进式扫描**：用 `HSCAN` / `SSCAN` / `ZSCAN` 代替 `HGETALL` / `SMEMBERS` / `ZRANGE 0 -1`，每次取有界批次。
- **超限迁移**：单 key 体量持续超标（> 10MB 或元素 > 50,000）应迁至 MongoDB / HBase / ES 等专用存储，Redis 只做热点小数据缓存。

#### List 结构专项注意

List 是 Redis 五种基础类型中行为最特殊的结构——**唯一没有天然去重**（Set 拒绝重复、Hash 同 field 覆盖、ZSet 同 member 覆盖），所有写入操作都是**无条件追加**。同时 List 操作的时间复杂度跨度极大（O(1) ~ O(N)），错误使用在高并发下极易引发性能问题。

##### 各方法时间复杂度总览

| 方法 | 底层命令 | 时间复杂度 | 风险等级 | 说明 |
|------|---------|-----------|---------|------|
| `addListItem` / `rightPushListElement` / `leftPushListElement` | RPUSH / LPUSH | **O(1)** | ✅ 安全 | 单元素追加，原子 |
| `rightPopListElement` / `leftPopListElement` (单个) | RPOP / LPOP | **O(1)** | ✅ 安全 | 单元素弹出，原子 |
| `rightPopListElement(key, count)` / `leftPopListElement(key, count)` | RPOP count / LPOP count | **O(N)** | ⚠️ N=count | count 很大时可能阻塞；建议 ≤ 20 |
| `indexOfList` | LINDEX | **O(1)** | ✅ 安全 | 单下标读，推荐替代 `getFromList` 单元素场景 |
| `getFirstFromList` | LINDEX 0 | **O(1)** | ✅ 安全 | 取首元素，委托 `indexOfList` |
| `getLastFromList` | LINDEX -1 | **O(1)** | ✅ 安全 | 取尾元素，委托 `indexOfList` |
| `getLastFromListIndex` | LINDEX index | **O(1)** | ✅ 安全 | 取指定下标，委托 `indexOfList` |
| `replaceListItem` | LSET | **O(1)** | ✅ 安全 | 按索引更新，原子 |
| `getListSize` | LLEN | **O(1)** | ✅ 安全 | 取列表长度 |
| `getListCache` / `getFromList(key, -1, ...)` | LLEN + LRANGE 0~N-1 | **O(N)** | 🔴 **BIGKEY 风险** | N=列表长度。全量读取，大量元素时延迟陡增 |
| `getListCacheWithLock` | 同 `getListCache` + 分布式锁 | **O(N)** | 🔴 **BIGKEY 风险** | 同上，带防击穿保护但全量读取风险仍在 |
| `removeListItem` | LREM | **O(N)** | 🔴 **BIGKEY 风险** | N=列表长度。遍历整表删除匹配值 |
| `replaceListCache` / `addAndReplaceList` | Lua: DEL + RPUSH 循环 | **O(N)** | ⚠️ N=新列表大小 | 已 Lua 原子化，但替换期间列表不可读 |
| `batchAddToList` | pipeline 多 RPUSH | 多条 O(1) | ⚠️ **非原子** | 多条之间无原子性保证，仅预热使用 |

> **核心原则**：读取单元素永远用 `indexOfList`（O(1)），不要用 `getListCache` 全量读再取一个（O(N)）。只在你真的需要全量数据时才用 `getListCache` / `getListCacheWithLock`。

##### 非 O(1) 方法详解与风险

以下方法因时间复杂度与列表长度挂钩，在 List 元素数千以上时风险显著：

**1. `getListCache(key, clazz)` / `getFromList(key, -1, ...)` — O(N) 全量读取**

内部实现为 `LLEN + LRANGE 0 size-1`（两条独立命令）。元素越多，反序列化 + 网络传输耗时越大。**ToC 高并发链路严禁直接全量读**。如需获取单个元素，务必改用 `indexOfList`、`getFirstFromList`、`getLastFromList`。

此外，`LLEN` 与 `LRANGE` 之间非原子——在此期间其他线程可能 `RPUSH`/`LPOP`，导致 LRANGE 读到的元素数与 `size` 不完全对应。对于读场景此窗口通常可接受，但需要知晓。

**2. `removeListItem(key, value, count)` — O(N) LREM**

LREM 需要遍历整个列表删除匹配值。大于数千元素的 List 执行 LREM 会阻塞 Redis 主线程，建议：
- 如果业务语义允许，改用 Set/Hash 存储
- 或在低峰期执行，或通过 Lua 限速

**3. `leftPopListElement(key, count)` / `rightPopListElement(key, count)`** — O(N) count

count 参数控制一次性弹出的元素数量。count 过大（如 10,000+）会占用 Redis 主线程处理该命令的时间。组件已通过 `BATCH_SIZE = 20` 上限约束（位于底层实现），恶意传大值会被拒绝。

##### 并发场景风险矩阵

| 风险场景 | 说明 | 影响级别 | 推荐做法 |
|---------|------|---------|---------|
| **并发重复 push** | 多个线程/实例同时 `addListItem(key, value)`，同一元素被追加多次 | 🟡 数据膨胀 | 维护伴生 Set `dedup:{key}`，写入前 `SADD dedup:{key} {item}`——返回 1 再 RPUSH，返回 0 则跳过（O(1)） |
| **重试导致膨胀** | 消息重试 / 接口重入，每次都 push 同一条数据 | 🟡 数据膨胀 | 同上，用伴生 Set 拦截；或改用 `replaceListCache` 整体替换（已 Lua 原子化） |
| **只增不减** | 日志型 List 持续 RPUSH 但从不做 LTRIM / LPOP | 🟡 内存泄漏 | 配合 `LTRIM key start stop` 限定窗口大小，并同步清理伴生 Set；或设置 TTL 自动回收 |
| **全量读 TOCTOU** | `getListCache`（LLEN + LRANGE）之间另一线程写入 | 🟢 短暂不一致 | 全量读场景下可接受；精确读需加锁或改用 `getListCacheWithLock` |
| **`addAndReplaceList` 并发窗口** | ✅ **已修复**：底层使用 Lua 脚本（DEL + RPUSH 循环），原子执行 | ✅ 无竞态 | 不再需要外部锁保护，`replaceListCache` 可直接使用 |
| **`getFromList(key, index, clazz)` 单下标场景** | ✅ **已修复**：基于 `LINDEX`（O(1) 原子），之前为 `LLEN` + 边界检查 + `getFromList` 两步 | ✅ 无竞态 | 单元素访问直接使用 `indexOfList` / `getLastFromList` / `getLastFromListIndex` |

##### 去重原则

List 去重不能靠自身遍历（O(N) 本身就是大 Key 风险），正确做法是 **伴生 Set 前置拦截**（O(1)）：

```
写入路径：SADD dedup:list_key item_value → 返回 1（新元素）→ RPUSH list_key item_value
                                      → 返回 0（已存在）→ 跳过
```

> **选型建议**：如果业务语义本质是「集合/去重」，直接用 Set 或 ZSet；只有语义确实是「有序队列/时间线」时才用 List，且必须配合伴生 Set + LTRIM 限定长度 + TTL 兜底回收。

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

### Stream 消息堆积了怎么办？

- 增加消费者 `concurrency`（如从 1 调为 3）
- 优化 `handle()` 处理逻辑
- 调整 `count` 批量大小
- 通过 `GET /actuator/redis-stream/metrics/backlog` 监控积压

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

| 主题 | 文档 |
|------|------|
| L2 / 分布式锁 / 性能守卫 设计 | [Redis-L2与性能守卫设计说明.md](./docs/Redis-L2与性能守卫设计说明.md) |
| 缓存核心能力功能分析 | [缓存核心能力功能分析.md](./docs/缓存核心能力功能分析.md) |
| Stream 使用与 YAML | [Redis-Stream-使用指南.md](./docs/Redis-Stream-使用指南.md) |
| 架构选型 | [Redis-Stream-架构对比分析.md](./docs/Redis-Stream-架构对比分析.md) |
| 性能调优 | [Redis-Stream-MQ性能分析.md](./docs/Redis-Stream-MQ性能分析.md) |
| 监控端点 | [Redis-Stream-Actuator-结构说明.md](./docs/Redis-Stream-Actuator-结构说明.md) |
| 链路追踪 | [Redis-Stream-Tracing-透传说明.md](./docs/Redis-Stream-Tracing-透传说明.md) |
| OpenTelemetry | [OpenTelemetry-快速开始.md](./docs/OpenTelemetry-快速开始.md) |
