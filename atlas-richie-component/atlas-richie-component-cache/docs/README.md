# richie-component-cache 文档索引

本目录为组件**专题文档**；模块总览、Ops API 与配置见上级 [README.md](../README.md)。

> **版本 1.0.0**：`GlobalCache` 已重构为 **Ops 访问器**（`value()`、`struct()`、`queue()` 等）。旧版扁平静态 API 已移除。
>
> **Stream MQ** 已独立为 [`atlas-richie-component-redis-streammq`](../../atlas-richie-component-redis-streammq)，门面 `StreamMQ`。

## 按主题阅读

| 文档 | 适用场景 |
|------|----------|
| [缓存核心能力功能.md](./缓存核心能力功能.md) | 各 Ops 能力的设计思路、配置、测试用例 |
| [Redis-L2与性能守卫设计说明.md](./Redis-L2与性能守卫设计说明.md) | L2、本地锁、Perf 守卫、键空间同步 |
| [Redis-Stream-使用指南.md](./Redis-Stream-使用指南.md) | **历史文档** → 请以 streammq 模块为准 |

## 配置前缀速查

| 前缀 | 绑定类 | 说明 |
|------|--------|------|
| `spring.data.redis` | `AtlasRedisProperties` | 连接、L2、本地锁、性能守卫 |
| `spring.data.local` | `LocalCacheProperties` | Caffeine / Ehcache 本地缓存 |
| `platform.cache` | `CacheProperties` | 缓存提供者、布隆过滤器 |

## 源码包结构（1.0.0）

```
com.richie.component.cache
├── GlobalCache.java          静态门面 → Ops 访问器
├── GlobalCacheManager.java   Spring 聚合 Bean
├── ops/                      ValueOps、FieldOps、LockOps…
├── function/                 底层 Redis 能力契约
├── operations/               有界 queue/stack 治理
├── redis/manage/             *Manager 实现
├── redis/perf/               性能守卫
├── local/                    JSR-107 L2
└── bloom/                    布隆过滤器
```
