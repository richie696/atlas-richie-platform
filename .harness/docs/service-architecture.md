# 组件架构设计规范

本规范针对**在 atlas-richie-platform 上编写组件**的场景（即开发 `atlas-richie-component-*` 模块），约束组件的内部设计原则。

> 注意：本规范**不是**给业务服务开发者用的（业务服务用 Application Service / Domain Service 的分层，那是他们的架构决策）。
> 本规范的受众是在平台上**新增/维护组件本身**的开发者。

---

## 1. 组件的内部层次

组件内部遵循统一的分层（见 `docs/code-standards.md` 中的"组件内部的分层"），核心原则：

- **`core/`** 放公开 API 接口和模型——这是组件对外的契约，**语义不可随意破坏**
- **`perf/`** 放兜底逻辑（guard、inspector）——这是平台层为业务代码守的下限
- **`config/`** 放 Spring 配置相关（Properties、AutoConfiguration）
- **`manage/`** 或各能力子包放具体实现

**跨层导入禁止**：从 `core/` 导入 `<provider>/` 中的实现类是 bug。

---

## 2. 错误处理：ApiResult + 异常体系

### 2.1 组件对外返回什么

组件的公开方法：
- **优先返回数据或 `null`**，不用 `ApiResult` 包装（`ApiResult` 是 Controller 层对 HTTP 响应的包装，组件内部不需要）
- 遇到错误时，**优先抛异常**，而不是返回错误码

```java
// ✅ 组件公开方法：返回数据，错误抛异常
public User findById(Long id) {
    if (id == null) {
        throw new IllegalArgumentException("id 不能为空");
    }
    return repository.findById(id).orElse(null);
}

// ❌ 不要在组件内部用 ApiResult
public ApiResult<User> findById(Long id) { ... }
```

### 2.2 异常体系

组件内的异常分类：

| 类型 | 类 | 使用场景 |
|---|---|---|
| 业务异常 | `BusinessException` | 业务前置条件不满足（如"用户不存在"） |
| 平台运行时异常 | `PlatformRuntimeException` | 平台层基础设施错误 |
| 平台数据访问异常 | `PlatformDataAccessException` | 数据库/缓存访问失败 |

规则：
- 业务异常用 `BusinessException`，带**错误码 + 消息**
- 基础设施层异常（超时、连接失败）向上传播，让调用方决定如何处理
- 不要捕获异常后吞掉（用 `log.error` 记录再抛，或者抛一个包装后的异常）

### 2.3 入参校验

- 公开方法在入口做参数校验，`null` / 非法值直接抛 `IllegalArgumentException`
- 不要用返回值（如 `null`、`-1`）来表示错误——抛异常更清晰

---

## 3. 幂等性

组件自身不管理业务幂等键，但**要支持调用方实现幂等**：

- Redis 操作：利用 Redis 天然原子性（如 `SETNX`），提供 `IfAbsent` 变体
- MQ 发送：组件内部通过消息 ID 做幂等去重（由 `MessageService` 统一处理）
- 幂等策略由组件提供基础设施，业务侧负责注入幂等键

---

## 4. 事务边界意识

组件本身**不控制事务**，但要**感知事务上下文**：

- 组件中的写操作依赖调用方开启的事务
- **不要在组件内自己开新事务**（如手动 `TransactionTemplate`），除非是组件的明确职责
- 如果组件内部需要事务保障，在 Javadoc 中**明确说明**
- 多数据源操作（如写 MySQL + 写 Redis）要注意事务语义——这两者本身不在同一事务中，组件应通过设计降低不一致风险（如写 MySQL 成功、Redis 失败的补偿逻辑）

---

## 5. 可观测性（日志、指标、追踪）

组件应在关键路径打日志和指标，便于排查问题：

### 5.1 日志

- 入口：TRACE/DEBUG 级别，记录 traceId、业务主键、关键入参（注意脱敏）
- 出口：INFO 级别，记录成功/失败、耗时
- 异常：ERROR 级别，记录异常信息，**不要吞掉**

```java
public void sendMessage(String topic, Object body) {
    log.debug("[MQ] 发送消息 topic={}, body={}", topic, sanitize(body));
    long start = System.currentTimeMillis();
    try {
        messageService.sendMessage(topic, body);
        log.info("[MQ] 发送成功 topic={}, 耗时={}ms", topic, System.currentTimeMillis() - start);
    } catch (Exception e) {
        log.error("[MQ] 发送失败 topic={}", topic, e);
        throw e;
    }
}
```

### 5.2 指标

核心指标：
- QPS（方法调用频率）
- RT（响应时间 p95/p99）
- 成功率 / 异常率

通过 `atlas-richie-component-tracing` 接入 OpenTelemetry 时，组件内部调用自动串联 span。

### 5.3 追踪

- 入口方法生成或延续 traceId
- 内部调用（Redis、MQ、外部 HTTP）在 span 中记录子调用
- 通过组件内拦截器/AOP 统一处理，减少在业务方法里显式打点

---

## 6. 公开 API 的稳定性

组件 `core/` 中的类型是公开契约，破坏后影响下游消费者：

- **不随意修改** `core/` 中类的字段、方法签名
- 如必须破坏，向后兼容：新增方法，不删除旧方法
- 大版本 breaking change 必须经过 `code-reviewer` 并有迁移方案
- 使用 `@since` 标注首次引入版本：

```java
/**
 * 根据 ID 查询缓存。
 *
 * @param id 缓存 ID
 * @return 缓存值，不存在返回 null
 * @since 1.2.0
 */
String findById(Long id);
```
