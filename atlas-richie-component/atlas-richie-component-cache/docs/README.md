# richie-component-cache 文档索引

本目录为组件**专题文档**；模块总览、配置前缀与 API 速查见上级 [README.md](../README.md)。

## 按主题阅读

| 文档 | 适用场景 |
|------|----------|
| [Redis-L2与性能守卫设计说明.md](./Redis-L2与性能守卫设计说明.md) | **业务查询 L2**、**分布式锁本地二级锁**、**Perf 守卫**、键空间同步、配置与运维清单 |
| [Redis-Stream-使用指南.md](./Redis-Stream-使用指南.md) | 声明式消费者、`@RedisStreamConsumer`、YAML 绑定、ACK/重试/DLQ |
| [Redis-Stream-架构对比分析.md](./Redis-Stream-架构对比分析.md) | Stream 与 Kafka/RabbitMQ 等方案选型 |
| [Redis-Stream-MQ性能分析.md](./Redis-Stream-MQ性能分析.md) | 吞吐、批量、并发与堆积调优 |
| [Redis-Stream-Actuator-结构说明.md](./Redis-Stream-Actuator-结构说明.md) | Actuator 端点、健康检查、监控指标结构 |
| [Redis-Stream-Tracing-透传说明.md](./Redis-Stream-Tracing-透传说明.md) | W3C Trace 在 Stream 消息中的封装与透传 |
| [OpenTelemetry-快速开始.md](./OpenTelemetry-快速开始.md) | OTLP 指标导出、与 Spring Boot 观测集成 |
| [OpenTelemetry-依赖架构说明.md](./OpenTelemetry-依赖架构说明.md) | OTel 依赖分层、与组件内 `MeterRegistry` 的关系 |
| [OpenTelemetry-方案总结.md](./OpenTelemetry-方案总结.md) | 观测方案整体设计摘要 |
| [application-otel.yml.example](./application-otel.yml.example) | OTLP / Stream 监控配置样例（可直接复制片段） |

## 配置前缀速查

| 前缀 | 绑定类 | 说明 |
|------|--------|------|
| `spring.data.redis` | `RichieRedisProperties` | 连接、Lettuce、L2、锁、幂等窗口、性能守卫 |
| `spring.data.local` | `LocalCacheProperties` | Caffeine / Ehcache 本地缓存定义 |
| `platform.cache` | `CacheProperties` | 缓存提供者、布隆过滤器 |
| `platform.cache.redis.stream.consumers` | `RedisStreamProperties` | Stream 消费者 YAML 与清理策略 |
| `platform.cache.redis.stream.tracing` | `RedisStreamTracingProperties` | Stream 链路追踪开关 |
| `platform.cache.redis.stream.monitoring` | `RedisStreamMonitoringProperties` | Stream 指标与 Actuator |
| `management.otlp.metrics` | Spring Boot | OTLP 导出（见 OTel 文档） |

## 源码包与职责

```
com.richie.component.cache
├── config/              CacheAutoConfiguration、CacheProperties、布隆配置
├── GlobalCache.java     静态门面（委托 GlobalCacheManager）
├── GlobalCacheManager   Spring Bean，按数据结构注入 *Function
├── function/            各 Redis 能力接口（String/List/Set/…）
├── local/               JSR-107 本地缓存（Caffeine/Ehcache）
├── bloom/               Guava / Redisson 布隆实现
├── redis/
│   ├── config/          自动配置（base / stream / tracing / monitor）
│   ├── manage/          *Manager 实现（对接 RedisTemplate）
│   ├── stream/          消费者、Reactor、幂等、清理、控制总线
│   ├── monitor/         Metrics、Actuator、健康指示器
│   ├── perf/            Redis 调用复杂度与慢查询守卫
│   └── tracing/         TraceableMessageWrapper、工具类
└── enums/               KeyTypeEnum、CacheProvider 等
```
