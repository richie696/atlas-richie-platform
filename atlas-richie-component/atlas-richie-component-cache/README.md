# Richie Cache Component

基于 Redis 的完整缓存与中间件解决方案，提供统一、便捷、线程安全的静态 API，涵盖 KV 存储、数据结构操作、分布式锁、消息队列、地理位置、基数统计、限流等全量能力。

## 📋 目录

- [功能特性](#-功能特性)
- [模块结构](#-模块结构)
- [配置参考](#️-配置参考)
- [快速开始](#-快速开始)
- [平台能力设计（5.0）](#-平台能力设计50)
- [核心功能](#-核心功能)
- [Redis Stream 消息队列](#redis-stream-消息队列)
- [专题文档](#-专题文档)
- [最佳实践](#-最佳实践)
- [常见问题](#-常见问题)

---

## ✨ 功能特性

### 基础缓存能力

- ✅ **KV 存储**：String、数值（Int/Long/Float/Double）、布尔类型，支持过期时间、条件写入
- ✅ **Hash 操作**：对象缓存、字段读写、批量操作
- ✅ **List 操作**：队列、列表、批量弹出/插入
- ✅ **Set 操作**：不重复集合、交集并集差集、成员判断
- ✅ **ZSet 操作**：有序集合、排行榜、分数排序、范围查询
- ✅ **Key 管理**：删除、查询、过期时间、重命名、移动等

### 高级特性

- ✅ **二级缓存（L2）**：`GlobalCache` 读先本地 `global_cache`，写双写；`*WithLock` 与 L2 协同防击穿；`CacheSyncListener` 键空间同步
- ✅ **分布式锁**：乐观/悲观 + 续期；`enable-local-lock` 时 JVM 二级锁池再落 Redis；可选 Redisson 可重入
- ✅ **防缓存击穿**：`getStringCacheWithLock` 等，链路 L2 → 布隆 → Redis → `lock:{key}` → DB
- ✅ **Redis 性能守卫**：各 Manager 经 `RedisPerfGuard` 包装；复杂度分级、慢查询阈值、String 反模式检测
- ✅ **批量操作**：批量读写，减少网络往返，提升性能

### 消息队列

- ✅ **Redis Stream**：可靠消息队列，支持消费组、ACK、重试、死信队列
- ✅ **自动 ACK**：消息处理成功后自动确认
- ✅ **手动 ACK**：精确控制消息确认时机
- ✅ **消息重试**：可配置重试次数和延迟
- ✅ **死信队列**：处理失败的消息自动发送到死信队列
- ✅ **监控统计**：实时统计处理数量、ACK 数量、失败数量

### 扩展能力

- ✅ **地理位置（GEO）**：存储、查询、距离计算
- ✅ **HyperLogLog**：基数估算，适合 UV 统计
- ✅ **Bitmap**：位图操作，适合签到、标签等场景
- ✅ **Lua 脚本**：原子性执行复杂业务逻辑
- ✅ **限流**：滑动窗口限流，防止接口滥用
- ✅ **布隆过滤器**：`platform.cache.bloom-filter`，Guava / Redisson，与 String/Set 读写联动防穿透
- ✅ **多 Redis 实例**：`spring.data.redis.slaves` 子节点 + `MultiRedisTemplate` 路由
- ✅ **发布订阅 / 键空间通知**：L2 失效同步、业务 Topic
- ✅ **性能守卫**：`spring.data.redis.perf`，非 O(1) 告警、慢查询、String 大载荷检测
- ✅ **Stream 可观测性**：Tracing 透传、Actuator、OTLP 指标（见 [docs](./docs/README.md)）

### 架构要点

- **门面**：业务代码优先使用 `GlobalCache` 静态方法；需要按类型注入时使用 `GlobalCacheManager`（`string()`、`hash()`、`stream()` 等）。
- **实现分层**：`*Function` 接口 → `redis.manage.*Manager` → Spring `RedisTemplate` / Lettuce。
- **自动配置**：`CacheAutoConfiguration` 扫描组件；`RedisBaseAutoConfiguration` 连接与序列化；`RedisStreamAutoConfiguration` 等按条件装配。

---

## 📦 模块结构

| 包路径 | 职责 |
|--------|------|
| `config` | `platform.cache`、布隆过滤器、`CacheAutoConfiguration` |
| `function` | 对外能力接口（String、Hash、Stream、Lock…） |
| `redis.manage` | Redis 各数据结构 Manager 实现 |
| `redis.config` | base / stream / tracing / monitor 自动配置与 Properties |
| `redis.stream` | 消费者抽象、幂等、Reactor、清理、控制总线 |
| `redis.perf` | 命令复杂度分级、`RedisPerfGuard` |
| `redis.monitor` | Stream 指标、Actuator、健康检查 |
| `redis.tracing` | 消息 Trace 包装与解析 |
| `local` | `spring.data.local`，Caffeine / Ehcache（JSR-107） |
| `bloom` | `BloomFilterFacade` 及 Guava / Redisson 实现 |

源码约 **106** 个 Java 文件，专题说明见 [docs/README.md](./docs/README.md)。

---

## ⚙️ 配置参考

> **注意**：旧文档中的 `cache.redis.*` 前缀已废弃，请使用下表前缀。

| 配置前缀 | 主要项 | 说明 |
|----------|--------|------|
| `spring.data.redis` | `host`、`port`、`lettuce`、`server-type` | 继承 Spring Boot `DataRedisProperties`；扩展见 `RichieRedisProperties` |
| `spring.data.redis` | `enable-l2-caching`、`l2-caching-data` | Redis 前加本地 L2（按 `KeyTypeEnum`） |
| `spring.data.redis` | `enable-local-lock` | 本地锁竞争（Redisson 分布式锁默认启用） |
| `spring.data.redis` | `slaves` | 命名子 Redis，`MultiRedisTemplate` 按名路由 |
| `spring.data.redis` | `stream-idempotency` | Stream 幂等键 TTL 与前缀 |
| `spring.data.redis` | `perf` | 性能守卫（默认关闭） |
| `spring.data.local` | `provider`、`cache-definitions` | 独立本地缓存区域（与 L2 可并存） |
| `platform.cache` | `cache-provider`、`bloom-filter` | 组件级开关与布隆参数 |
| `platform.cache.redis.stream.consumers` | `configs`、`cleanup` | 声明式 Stream 消费者 |
| `platform.cache.redis.stream.tracing` | — | W3C 透传（见专题文档） |
| `platform.cache.redis.stream.monitoring` | — | 指标与 Actuator |

---

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-cache</artifactId>
</dependency>
```

### 2. 配置 Redis 连接

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:
      database: 0
      timeout: 3s
      # 可选：二级缓存（本地 + Redis）
      enable-l2-caching: true
      l2-caching-data:
        - STRING
        - HASH
      # 分布式锁：JVM 内二级锁，减少同 key 打 Redis（Redisson 默认启用）
      enable-local-lock: false
      # 性能守卫（生产建议先 false，灰度开启）
      perf:
        enabled: false
        warn-non-o1: true
        toc-soft-ms: 8
        toc-hard-ms: 50

platform:
  cache:
    cache-provider: REDIS
    bloom-filter:
      enable: false
      type: REDISSON
      expected-insertions: 10000000
      false-probability: 0.001
```

多实例示例：`spring.data.redis.slaves.order-redis.host=...`，业务侧通过 Manager 指定 slave 名称（见 `MultiRedisTemplate`）。

### 3. 使用 GlobalCache 静态 API

```java
import com.richie.component.cache.GlobalCache;

@Service
public class UserService {

    // 基础 KV 操作
    public void cacheUser(String userId, String userName) {
        GlobalCache.addStringCache("user:" + userId, userName, 3600_000L); // 1小时过期
    }

    public String getUser(String userId) {
        return GlobalCache.getStringCache("user:" + userId);
    }

    // Hash 操作（对象缓存）
    public void cacheUserInfo(String userId, UserInfo userInfo) {
        GlobalCache.addObjectToHash("user:info:" + userId, userInfo, 3600_000L);
    }

    // 防缓存击穿（带锁读取）
    public String getUserWithLock(String userId) {
        return GlobalCache.getStringCacheWithLock(
                "user:" + userId,
                3600_000L,
                () -> userRepository.findById(userId).getName() // 从数据库加载
        );
    }

    // 分布式锁
    public void updateUser(String userId) {
        try (var lock = GlobalCache.optimisticLockWithRenewal("lock:user:" + userId, 5L)) {
            if (lock.success()) {
                // 临界区代码
                userRepository.update(userId);
            }
        }
    }
}
```

---

## 🏗 平台能力设计（5.0）

以下三项为当前 Redis 层的**核心新设计**，业务接口查询、分布式锁、数据访问均默认经过统一门面治理。完整设计见 **[Redis-L2与性能守卫设计说明.md](./docs/Redis-L2与性能守卫设计说明.md)**。

### 二级缓存（L2）— 业务查询

| 项 | 说明 |
|----|------|
| **开关** | `spring.data.redis.enable-l2-caching` + `l2-caching-data`（`STRING`/`HASH`/`LIST`/`SET`） |
| **读** | `GlobalCache.getXxx` → 先 `LocalCache`（区域 `global_cache`），未命中再 Redis，命中回写 L2 |
| **写** | 经 `GlobalCache` 的写操作双写 L2；TTL 含 1~10 分钟随机偏移防雪崩 |
| **防击穿** | `getStringCacheWithLock` / `getObjectFromHashWithLock` 等：L2 → 布隆 → Redis → `lock:{key}` → DB；持锁失败线程会**再次读 L2** |
| **多实例** | `CacheSyncListener` 订阅 `notify-keyspace-events KEA`（`del`/`set`/`expired` 等）同步 L2 |

```yaml
spring.data.redis.enable-l2-caching: true
spring.data.redis.l2-caching-data: [STRING, HASH]
```

```java
// 热点读：L2 → Redis
String v = GlobalCache.getStringCache("product:sku:1");

// 回源读：L2 → Redis+锁+DB（推荐接口查询）
String v = GlobalCache.getStringCacheWithLock("product:sku:1", 600_000L,
        () -> productDao.findName("1"));
```

### 分布式锁 — 本地二级锁 + Redisson

| 项 | 说明 |
|----|------|
| **顺序** | `enable-local-lock` 时先 `CacheLockManager`（JVM 锁池）→ Redisson 获取 |
| **后端** | Redisson FencedLock，可重入、看门狗续期 |
| **配置** | `enable-local-lock` |
| **守卫** | 加锁路径经 `RedisPerfGuard`（`LOCK_TRY`） |
| **续期** | `optimisticLockWithRenewal` / `pessimisticLockWithRenewal`，虚拟线程池定时 `EXPIRE` |

```yaml
spring.data.redis.enable-local-lock: true
```

```java
try (var lock = GlobalCache.optimisticLockWithRenewal("lock:order:" + orderId, 30L)) {
    if (lock.success()) { /* 更新订单 */ }
}
```

### Redis 性能守卫 — 数据访问治理

| 项 | 说明 |
|----|------|
| **开关** | `spring.data.redis.perf.enabled`（默认 `false`，建议灰度） |
| **复杂度** | 非 O(1) WARN；`LINEAR_N`/`WORSE` 可 `block-forbidden-tiers` 抛异常 |
| **耗时** | `toc-soft-ms` / `toc-hard-ms` 慢查询分级 |
| **String** | 写入前检测集合/Map 整包、超大 JSON、大对象；可 `block-string-payload-violations` |
| **覆盖** | 各 `*Manager` 的 `redisPerfGuard.execute`；`GlobalCache` 方法带 ToC 复杂度 @apiNote |

```yaml
spring.data.redis.perf:
  enabled: true
  warn-non-o1: true
  toc-soft-ms: 8
  toc-hard-ms: 50
  block-forbidden-tiers: false
  warn-string-payload-anti-patterns: true
```

**注意**：旁路直接使用 `RedisTemplate` 不经 `GlobalCache` 时，**不会**更新 L2、也不会触发 String 载荷检测；生产热点路径应统一走 `GlobalCache`。

---

## 🔧 核心功能

### 基础能力与开关

```java
// 二级缓存总开关与按类型开关
boolean l2 = GlobalCache.enableL2Caching();
boolean hashL2 = GlobalCache.enableKeyTypeCache(KeyTypeEnum.HASH);

// 过期时间、连接信息
Long ttl = GlobalCache.getExpiredTime("user:123");
String connStr = GlobalCache.getConnectionString();
```

**L2 注意**：跨实例依赖 Redis 键空间通知；旁路写 Redis 可能导致短暂脏读。详见 [设计说明](./docs/Redis-L2与性能守卫设计说明.md)。

**Perf 注意**：开启 `perf.enabled` 后关注 `[RedisPerf]` 日志；ToC 核心链路避免 `KEYS`、全量 `HGETALL`/`SMEMBERS`。

### String/数值/布尔（KV）

```java
// 写入（带过期时间）
GlobalCache.addStringCache("key", "value", 10_000L);
GlobalCache.addIntCache("count", 100, 5_000L);
GlobalCache.addBooleanCache("flag", true, 3_600_000L);

// 写入（仅当不存在）
boolean added = GlobalCache.addStringCacheIfAbsent("token", "abc", 60_000L);

// 读取
String value = GlobalCache.getStringCache("key");
Integer count = GlobalCache.getIntCache("count");
Boolean flag = GlobalCache.getBooleanCache("flag");

// 递增/递减
Long newValue = GlobalCache.increment("counter", 1L);
Long newValue2 = GlobalCache.decrement("counter", 1L);
```

**使用注意事项**：
- 永不过期缓存（无 timeout 参数）慎用，可能导致内存持续增长
- IfAbsent 操作在高并发下仍可能重复写入，如需严格防重复请使用分布式锁
- 数值类型缓存会自动序列化，大对象建议使用 Hash 结构

**性能优化建议**：
- 频繁更新的计数器使用 increment/decrement，避免先读后写
- 大字符串（>1KB）考虑压缩或分片存储
- 批量操作使用 batchAddToString，减少网络往返

### Hash（对象与字段）

```java
// 写入单个字段
GlobalCache.addCache2Hash("user:1", "name", "Tom");
GlobalCache.addCache2Hash("user:1", "age", 25);

// 写入整个对象
UserInfo user = new UserInfo("Tom", 25);
GlobalCache.addObjectToHash("user:1", user, 3600_000L);

// 批量写入
Map<String, Object> fields = Map.of("name", "Tom", "age", 25);
GlobalCache.addCacheAllHash("user:1", fields, 3600_000L);

// 读取单个字段
String name = GlobalCache.getHashCache("user:1", "name", String.class);

// 读取整个对象
UserInfo user = GlobalCache.getObjectFromHash("user:1", UserInfo.class);

// 读取多个字段
Map<String, String> fields = GlobalCache.getHashCache("user:1", String.class);

// 删除字段
GlobalCache.removeHashItem("user:1", "name", "age");
```

**使用注意事项**：
- Hash 结构适合存储对象属性，避免序列化整个对象
- 单个 Hash 的 field 数量不宜过多（建议<1000），影响查询性能
- 删除 Hash 字段后，空 Hash 仍占用内存，需要手动清理

**性能优化建议**：
- 批量读取 Hash 字段使用 `getHashCache(key, Collection<String>, clazz)`
- 大 Hash 考虑分片存储，如 `user:1:basic`、`user:1:detail`
- 频繁更新的字段单独存储，减少序列化开销

### List（列表）

```java
// 添加元素（队列追加操作）
GlobalCache.addListItem("queue", "item1");
GlobalCache.addListItem("queue", "item2");

// 完整写入列表（必须使用防缓存击穿方法）
// 场景：从数据库加载完整列表并写入缓存
List<UserInfo> users = GlobalCache.getListCacheWithLock(
    "user:list:active",           // 缓存键
    -1,                           // -1 表示获取全部列表
    UserInfo.class,               // 元素类型
    3600_000L,                    // 过期时间（1小时）
    () -> userRepository.findAllActiveUsers() // 数据库加载器
);

// 弹出元素（队列操作）
String first = GlobalCache.leftPopListElement("queue", String.class);
String last = GlobalCache.rightPopListElement("queue", String.class);

// 批量弹出
List<String> batch = GlobalCache.leftPopListElement("queue", 5, String.class);

// 读取列表
List<String> all = GlobalCache.getListCache("queue", String.class);
String first = GlobalCache.getFirstFromList("queue", String.class);
String last = GlobalCache.getLastFromList("queue", String.class);

// 获取长度
Long size = GlobalCache.getListSize("queue");
```

**重要说明：完整写入列表的安全机制**

**为什么必须使用防缓存击穿方法？**

在高并发场景下，如果多个线程同时发现缓存为空，都去数据库查询并直接写入，会导致 List 中出现大量重复数据：

```java
// ❌ 错误示例（已移除，不再支持）
// 多个线程同时执行以下代码会导致重复写入：
GlobalCache.addListCache("user:list", usersFromDB, 3600_000L);
// 结果：List 中可能变成 [user1, user2, user3, user1, user2, user3, ...]

// ✅ 正确示例（使用防缓存击穿方法）
// 通过分布式锁确保只有一个线程写入，其他线程等待并读取已写入的数据
List<UserInfo> users = GlobalCache.getListCacheWithLock(
    "user:list:active",
    -1,
    UserInfo.class,
    3600_000L,
    () -> userRepository.findAllActiveUsers()
);
```

**工作原理**：
1. 第一个线程获取分布式锁，从数据库加载数据并写入缓存
2. 其他线程等待锁释放后，直接从缓存读取，避免重复查询数据库
3. 使用 `addAndReplaceList()` 替换整个列表，确保数据一致性

**使用场景区分**：
- **`addListItem()`**：用于队列追加操作，允许多个线程追加不同元素（如消息队列、日志记录）
- **`getListCacheWithLock()`**：用于完整列表写入，确保线程安全，避免重复数据

**使用注意事项**：
- List 操作在 pipeline/transaction 中返回值可能不准确
- 大 List（>10000 元素）查询性能下降，考虑分页或分片
- 频繁的中间插入操作性能较差，建议使用 ZSet
- 完整列表写入必须使用 `getListCacheWithLock()`，避免并发重复写入

**应用场景**：
- 消息队列：leftPop + rightPush
- 最新记录：rightPush + leftPop（保持最新 N 条）
- 任务列表：批量获取任务，处理完成后删除

### Set（不重复集合）

```java
// 添加元素
GlobalCache.addSetItem("tags", "java");
GlobalCache.addSetItem("tags", "redis");

// 批量添加
Set<String> tags = Set.of("java", "redis", "spring");
GlobalCache.addSetCache("tags", tags, 3600_000L);

// 判断是否存在
boolean exists = GlobalCache.existsInSet("tags", "java");

// 读取集合
Set<String> all = GlobalCache.getSetCache("tags", String.class);

// 弹出元素
String popped = GlobalCache.popDataFromSet("tags", String.class);
Set<String> batch = GlobalCache.popMembersFromSet("tags", 3, String.class);

// 获取大小
Long size = GlobalCache.getSetSize("tags");
```

**使用注意事项**：
- Set 适合去重场景，但内存占用较大
- 大 Set（>10000 元素）的 existsInSet 性能下降
- pop 操作会修改 Set，注意并发安全

**应用场景**：
- 用户标签：`user:tags:123`
- 黑名单：`blacklist:users`
- 在线用户：`online:users`

### ZSet（有序集合）

```java
// 添加元素（带分数）
GlobalCache.addZSetItem("rank", "user1", 99.5);
GlobalCache.addZSetItem("rank", "user2", 88.0);

// 批量添加
TreeSet<ZSetItem<String>> items = new TreeSet<>();
items.add(new ZSetItem<>("user1", 99.5));
items.add(new ZSetItem<>("user2", 88.0));
GlobalCache.addZSet("rank", items);

// 获取排名
Long rank = GlobalCache.getZSetRank("rank", "user1");        // 正序排名
Long reverseRank = GlobalCache.getZSetReverseRank("rank", "user1"); // 倒序排名

// 范围查询
Set<String> top10 = GlobalCache.reverseScoreRangeFromZSet("rank", 0, 9, new TypeReference<String>(){});
Set<String> range = GlobalCache.scoreRangeFromZSet("rank", 80.0, 100.0, new TypeReference<String>(){});

// 弹出最小元素
ZSetItem<String> min = GlobalCache.popMinFromZSet("rank", new TypeReference<String>(){});
List<ZSetItem<String>> batch = GlobalCache.popMinFromZSet("rank", 5, new TypeReference<String>(){});

// 更新分数
Double newScore = GlobalCache.incrementScore("rank", "user1", 10.0);

// 获取大小
Long size = GlobalCache.getZSetSize("rank");
```

**使用注意事项**：
- Score 精度问题：浮点数比较时注意精度误差
- 大 ZSet（>10000 元素）范围查询性能下降
- 频繁的 score 更新会影响排序性能

**应用场景**：
- 排行榜：实时更新分数，定期清理过期数据
- 时间排序：使用时间戳作为 score
- 权重队列：按优先级处理任务

### Key 管理

```java
// 删除
GlobalCache.removeCache("key1");
GlobalCache.removeCache(List.of("key1", "key2", "key3"));

// 查询
boolean exists = GlobalCache.hasKey("key1");
int count = GlobalCache.countExistingKeys(List.of("key1", "key2"));

// 过期时间
GlobalCache.setExpiredTime("key1", 10_000L);
GlobalCache.expireAt("key1", LocalDateTime.now().plusHours(1));
GlobalCache.persist("key1"); // 移除过期时间

// 元数据
GlobalCache.copy("source", "target", true);
GlobalCache.rename("old", "new");
boolean renamed = GlobalCache.renameIfAbsent("old", "new");
```

**使用注意事项**：
- 批量删除时 keys 数量不宜过多（建议<1000），避免阻塞
- rename 操作会阻塞其他操作，大 key 谨慎使用
- move 操作会改变数据库，注意数据一致性

### 批量操作

```java
// 批量写入
Map<String, String> data = Map.of("k1", "v1", "k2", "v2", "k3", "v3");
GlobalCache.batchAddToString(data, 3600_000L);

// 批量读取
Map<String, String> values = GlobalCache.getValueMap(
    List.of("k1", "k2", "k3"),
    new TypeReference<String>(){}
);

// 批量更新（仅当不存在）
GlobalCache.batchUpdateIfAbsent(data, 3600_000L);
```

**使用注意事项**：
- 批量操作非原子性，可能出现部分成功部分失败
- 单次批量操作 key 数量建议<1000，避免超时
- 批量操作失败时，需要手动处理部分成功的数据

### 分布式锁

```java
// 乐观锁（推荐）
try (var lock = GlobalCache.optimisticLockWithRenewal("lock:order:123", 5L)) {
    if (lock.success()) {
        // 临界区代码
        orderService.processOrder("123");
    } else {
        log.warn("获取锁失败");
    }
}

// 悲观锁
try (var lock = GlobalCache.pessimisticLock("lock:order:123", 10L)) {
    if (lock.success()) {
        // 临界区代码
    }
}
```

**使用注意事项**：
- 锁的过期时间要大于业务执行时间
- 避免在锁内执行耗时操作，可能导致锁失效
- 锁的 key 要具有业务含义，便于排查问题

**最佳实践**：
- 使用 try-with-resources 自动释放锁
- 合理设置锁超时时间，避免死锁
- 考虑使用锁续期机制（`WithRenewal` 方法）

### 防缓存击穿（带锁读）

```java
// String 类型
String value = GlobalCache.getStringCacheWithLock(
    "config:app",
    3600_000L,
    () -> configRepository.findByKey("app").getValue() // 从数据库加载
);

// Hash 对象
UserInfo user = GlobalCache.getObjectFromHashWithLock(
    "user:123",
    UserInfo.class,
    3600_000L,
    () -> userRepository.findById("123") // 从数据库加载
);

// Hash 字段
String name = GlobalCache.getHashCacheWithLock(
    "user:123",
    "name",
    String.class,
    3600_000L,
    () -> userRepository.findById("123").getName() // 从数据库加载
);
```

**使用注意事项**：
- dbLoader 要幂等，避免重复执行
- 锁的超时时间要大于数据库查询时间
- 避免在 dbLoader 中执行耗时操作

**应用场景**：
- 配置信息：系统配置、用户配置
- 热点数据：排行榜、统计数据
- 业务规则：风控规则、计费规则

### 地理位置（GEO）

```java
// 添加地理位置
GlobalCache.addGeo("store:geo", 116.39, 39.9, "beijing");
GlobalCache.addGeo("store:geo", 117.20, 39.13, "tianjin");

// 计算距离
Distance distance = GlobalCache.geoDist("store:geo", "beijing", "tianjin");
double km = distance.getValue(); // 距离（千米）

// 附近查询
List<GeoPointResult> nearby = GlobalCache.geoRadius("store:geo", 116.4, 39.9, 50.0); // 50km 范围内
```

**使用注意事项**：
- 经纬度精度：建议使用 6 位小数，精度约 1 米
- 大 GEO 集合（>10000 成员）查询性能下降
- 距离计算使用米为单位，注意单位转换

**应用场景**：
- 附近商家：根据用户位置查询
- 配送范围：计算配送距离
- 地理围栏：判断用户是否在指定区域

### HyperLogLog（基数估算）

```java
// 添加元素
GlobalCache.pfAdd("uv:2025-01-01", "uid1", "uid2", "uid3");

// 获取基数
long uv = GlobalCache.pfCount("uv:2025-01-01");
```

**使用注意事项**：
- 基数估算有误差，误差率约 0.81%
- 适合大基数场景（>10000），小基数误差较大
- 不支持删除操作，只能重置整个 key

**应用场景**：
- UV 统计：用户访问量统计
- 去重统计：避免重复计算
- 大数据分析：近似统计

### Bitmap

```java
// 设置位
GlobalCache.setBit("sign:202501", 1, true);  // 第 1 天签到
GlobalCache.setBit("sign:202501", 2, true);  // 第 2 天签到

// 获取位
boolean day1 = GlobalCache.getBit("sign:202501", 1);
```

**使用注意事项**：
- offset 从 0 开始，注意边界处理
- 大 offset 会占用大量内存，建议合理规划
- 不支持负数 offset

**应用场景**：
- 签到打卡：每天一个 bit
- 用户标签：每个标签一个 bit
- 在线状态：用户在线状态记录

### Lua 脚本

```java
// 执行 Lua 脚本
String result = GlobalCache.evalLua(
    "return ARGV[1]",
    List.of(),                    // keys
    List.of("ok"),                // args
    String.class                  // 返回类型
);
```

**使用注意事项**：
- Lua 脚本要幂等，避免副作用
- 脚本执行时间不宜过长，避免阻塞
- 注意脚本的原子性，适合复杂业务逻辑

### 限流（滑动窗口）

```java
// 尝试获取许可
boolean pass = GlobalCache.tryAcquire("rl:/api/pay", 100, 60); // 60秒内最多100次
if (pass) {
    // 允许访问
} else {
    // 限流，拒绝访问
}
```

**使用注意事项**：
- 限流粒度要合理，避免误杀正常请求
- 时间窗口要适中，避免限流效果不明显
- 限流失败时要有降级策略

**应用场景**：
- API 限流：防止接口被滥用
- 用户限流：防止用户操作过于频繁
- 业务限流：防止业务异常

---

## 📨 Redis Stream 消息队列

### 概述

基于 Redis Stream 的可靠消息队列功能，支持消费组、消息确认、回溯与堆积，适合可靠消息/任务队列场景。

**与发布订阅（Pub/Sub）的区别**：
- Stream：可持久化与 ACK，消息不丢失
- Pub/Sub：仅在线广播，无 ACK，消息可能丢失

### 主要特性

1. **消息顺序**：Redis Stream 保证同一消费者组内的消息顺序
2. **消息持久化**：消息会持久化到 Redis，重启后不会丢失
3. **消费者组**：同一消费者组内的消费者会分摊消息
4. **自动 ACK**：消息处理成功后自动确认消费
5. **手动 ACK**：精确控制消息确认时机
6. **消息重试**：可配置重试次数和延迟
7. **死信队列**：处理失败的消息自动发送到死信队列
8. **监控统计**：实时统计处理数量、ACK 数量、失败数量

### 生产消息

```java
// 发布消息到 Stream
GlobalCache.stream().publish("stream:orders", Map.of("orderId", "1001", "amount", "99.99"));

// 发布对象消息
OrderEvent event = new OrderEvent("1001", "99.99");
GlobalCache.stream().publish("stream:orders", event);
```

### 消费消息

#### 自动 ACK 模式（推荐）

```java
StreamConsumerConfig config = StreamConsumerConfig.builder()
    .streamKey("stream:orders")
    .group("order-processors")
    .consumer("processor-1")
    .count(10)                           // 每次拉取10条消息
    .pollIntervalMillis(5000)            // 5秒轮询一次
    .autoAck(true)                       // 启用自动ACK
    .maxRetryCount(3)                    // 最大重试3次
    .retryDelayMs(1000)                  // 重试延迟1秒
    .enableDeadLetterQueue(true)         // 启用死信队列
    .messageHandler(this::processMessages)  // 消息处理函数
    .errorHandler(this::handleError)        // 错误处理函数
    .build();

// 注册消费者
String consumerId = GlobalCache.stream().subscribe(config);

// 消息处理函数
private Boolean processMessages(List<StreamMessage> messages) {
    try {
        for (StreamMessage message : messages) {
            // 处理消息
            OrderEvent event = parseMessage(message);
            orderService.processOrder(event);
        }
        return true; // 处理成功，自动ACK
    } catch (Exception e) {
        log.error("处理消息失败", e);
        return false; // 处理失败，不ACK，将触发重试
    }
}
```

#### 手动 ACK 模式（精确控制）

```java
StreamConsumerConfig config = StreamConsumerConfig.builder()
    .autoAck(false)  // 禁用自动ACK
    .messageHandlerWithAck((messages, ack) -> {
        for (var m : messages) {
            // 只ACK满足条件的消息
            if (shouldAck(m)) {
                ack.accept(m.id());
            }
        }
        return false; // 整体不自动ACK，仅保留回调内已手工ACK的结果
    })
    .build();
```

#### 自动 ACK + 局部手工 ACK

```java
StreamConsumerConfig config = StreamConsumerConfig.builder()
    .autoAck(true)
    .messageHandlerWithAck((messages, ack) -> {
        for (var m : messages) {
            if (mustAckImmediately(m)) {
                ack.accept(m.id()); // 该条立即ACK，其余成功消息走批量自动ACK
            }
        }
        return true; // 整体成功
    })
    .build();
```

**关键区别**：
- **自动 ACK 模式**：回调返回 `true` 时，系统对"未手工 ACK"的成功消息执行批量 ACK
- **手动 ACK 模式**：由回调内调用 `ack.accept(recordId)` 精确 ACK；回调返回 `false` 时，仅保留已手工 ACK 的结果

### ACK 设计原则

#### 1. 单一职责
- `messageHandler` 负责消息处理和 ACK 决策
- `StreamConsumerManager` 负责消息拉取、重试、死信队列等基础设施
- 避免在外部手动调用 ACK 方法，保持职责清晰

#### 2. 声明式 ACK
- 通过返回值声明消息处理结果
- `true` = 处理成功，需要 ACK
- `false` = 处理失败，不 ACK，触发重试
- 异常 = 处理异常，不 ACK，触发重试

#### 3. 自动重试机制
- 当 `messageHandler` 返回 `false` 或抛出异常时，自动重试
- 重试次数和延迟可配置
- 超过重试次数后自动发送到死信队列

#### 4. 性能优化
- 批量 ACK 提高性能
- 批量 ACK 失败时自动回退到单个 ACK
- 支持配置批量大小和轮询间隔

### 监控与统计

```java
// 获取消费者状态
Map<String, ConsumerStatus> status = streamConsumerManager.getConsumerStatus();

// 获取统计信息
Map<String, Object> stats = streamConsumerManager.getStatistics();
// 包含：
// - totalConsumersRegistered: 总注册消费者数
// - totalConsumersStopped: 总停止消费者数
// - activeConsumers: 活跃消费者数
// - totalMessagesProcessed: 总处理消息数
// - totalMessagesAcked: 总ACK消息数
// - totalMessagesFailed: 总失败消息数
// - threadPoolSize: 线程池大小
// - activeThreads: 活跃线程数
// - queueSize: 队列大小
```

### StreamConsumerConfig 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| streamKey | String | - | Stream 键名（必填） |
| group | String | - | 消费者组名（必填） |
| consumer | String | - | 消费者名（必填） |
| count | int | 10 | 每次拉取的消息数量 |
| pollIntervalMillis | long | 5000 | 轮询间隔（毫秒） |
| autoAck | boolean | true | 是否启用自动 ACK |
| maxRetryCount | int | 3 | 最大重试次数 |
| retryDelayMs | long | 1000 | 重试延迟（毫秒） |
| enableDeadLetterQueue | boolean | true | 是否启用死信队列 |
| deadLetterQueueSuffix | String | ":dlq" | 死信队列后缀 |
| enabled | boolean | true | 是否启用消费者 |

### 故障排查

#### 常见问题

1. **消息处理失败**
   - 检查业务逻辑是否正确
   - 查看重试日志
   - 检查死信队列

2. **消费者停止**
   - 检查消费者配置
   - 查看错误日志
   - 检查 Redis 连接

3. **性能问题**
   - 调整批量大小
   - 优化消息处理逻辑
   - 调整线程池配置

#### 日志级别

建议在生产环境中设置合适的日志级别：
- DEBUG：详细的处理日志
- INFO：重要的状态变更
- WARN：警告信息
- ERROR：错误信息

---

## 📚 专题文档

深度内容（L2/锁/Perf 守卫、Stream、Tracing、OTLP）见 **[docs/README.md](./docs/README.md)**。

| 主题 | 文档 |
|------|------|
| **L2 / 分布式锁 / 性能守卫** | [Redis-L2与性能守卫设计说明.md](./docs/Redis-L2与性能守卫设计说明.md) |
| Stream 使用与 YAML | [Redis-Stream-使用指南.md](./docs/Redis-Stream-使用指南.md) |
| 架构选型 | [Redis-Stream-架构对比分析.md](./docs/Redis-Stream-架构对比分析.md) |
| 性能调优 | [Redis-Stream-MQ性能分析.md](./docs/Redis-Stream-MQ性能分析.md) |
| 监控端点 | [Redis-Stream-Actuator-结构说明.md](./docs/Redis-Stream-Actuator-结构说明.md) |
| 链路追踪 | [Redis-Stream-Tracing-透传说明.md](./docs/Redis-Stream-Tracing-透传说明.md) |
| OpenTelemetry | [OpenTelemetry-快速开始.md](./docs/OpenTelemetry-快速开始.md) |

---

## 🎯 最佳实践

### 1. Key 命名规范

- 使用冒号分隔：`{业务}:{对象}:{ID}:{字段}`
- 示例：`user:info:123`、`order:status:456`、`cache:config:app`
- 避免特殊字符：只使用字母、数字、冒号、下划线
- 长度适中：建议<100 字符，便于管理和调试

### 2. 过期时间策略

- **热点数据**：短过期时间（秒级），减少缓存不一致
- **配置数据**：中等过期时间（分钟级），平衡性能和一致性
- **基础数据**：长过期时间（小时级），减少数据库压力
- **历史数据**：设置合理的过期时间，避免内存泄漏

### 3. 内存优化

- 合理设置过期时间，避免内存泄漏
- 大对象考虑压缩或分片存储
- 定期清理无用的 key
- 监控 Redis 内存使用率

### 4. 性能优化

- 使用批量操作减少网络往返
- 合理设置连接池大小
- 避免在 Redis 中存储过大的数据
- 使用 pipeline 提升批量操作性能
- 热点读开启 `enable-l2-caching`，接口回源用 `*WithLock`，写删走 `GlobalCache` 保证 L2 一致
- 高争用锁开启 `enable-local-lock`；预发灰度 `perf.enabled`，消除 `[RedisPerf]` 非 O(1) 与 BIGKEY 告警后再上生产

### 5. 监控告警

- 监控 Redis 内存使用率
- 监控 key 数量变化
- 监控慢查询和连接数
- 监控 Stream 消费者状态和消息堆积

### 6. 故障处理

- 设置合理的超时时间
- 实现降级策略
- 建立回滚机制
- 定期备份重要数据

---

## ❓ 常见问题

### 1. 缓存穿透

**问题**：查询不存在的 key，每次都访问数据库

**解决方案**：
- 使用防击穿方法：`getStringCacheWithLock`
- 使用布隆过滤器预判断
- 空值缓存：将空结果也缓存起来（设置较短过期时间）

### 2. 缓存雪崩

**问题**：大量 key 同时过期，导致数据库压力激增

**解决方案**：
- 设置随机过期时间：`baseTime + random(0, 600)` 秒
- 使用本地缓存（L2）作为二级缓存
- 实现降级策略，缓存失效时返回默认值

### 3. 缓存击穿

**问题**：热点 key 过期，大量请求同时访问数据库

**解决方案**：
- 使用分布式锁：`getStringCacheWithLock`
- 热点 key 设置永不过期，通过后台任务更新
- 使用本地缓存（L2）减少 Redis 访问

### 4. 内存不足

**问题**：Redis 内存不足，影响性能

**解决方案**：
- 设置合理的过期时间
- 清理无用数据
- 大对象考虑压缩或分片存储
- 扩容 Redis 实例

### 5. 连接数过多

**问题**：连接池耗尽，影响性能

**解决方案**：
- 优化连接池配置
- 使用连接复用
- 负载均衡分散连接
- 监控连接数变化

### 6. Stream 消息堆积

**问题**：消费者处理速度慢，消息堆积

**解决方案**：
- 增加消费者并发数（`concurrency`）
- 优化消息处理逻辑
- 增加批量处理大小（`count`）
- 监控消息堆积情况，及时告警

---

## 📊 性能调优指南

### 1. 网络优化

- 使用 pipeline 减少网络往返
- 合理设置批量大小
- 使用连接池复用连接
- 减少不必要的网络调用

### 2. 内存优化

- 设置合理的过期时间
- 使用压缩算法（如 gzip）
- 定期清理无用数据
- 监控内存使用率

### 3. 并发优化

- 使用合适的锁粒度
- 避免热点 key 竞争
- 使用本地缓存减少竞争
- 合理设置线程池大小

### 4. 监控优化

- 设置合理的告警阈值
- 监控关键指标（内存、连接、QPS）
- 建立性能基线
- 定期性能测试

---

## 📝 总结

GlobalCache 提供了完整的缓存解决方案，涵盖了从基础 KV 操作到高级特性的各个方面。通过合理使用这些 API，可以构建高性能、高可用的缓存系统。

**关键要点**：

1. **选择合适的缓存策略**：根据业务场景选择合适的数据结构和过期策略
2. **注意性能优化**：使用批量操作、合理设置过期时间、避免热点 key
3. **建立监控体系**：监控关键指标，及时发现和解决问题
4. **制定故障预案**：建立降级策略和回滚机制
5. **持续优化**：根据实际使用情况持续优化配置和策略

通过遵循这些最佳实践，可以充分发挥 GlobalCache 的性能优势，为业务系统提供稳定可靠的缓存支持。
