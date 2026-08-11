# MCP Server Starter 传统业务接口 MCP 化能力改造方案

> 文档类型：中台组件实施任务书  
> 目标组件：`atlas-richie-mcp-parent`  
> 重点模块：`atlas-richie-mcp-api`、`atlas-richie-mcp-server-core`、`atlas-richie-mcp-server-spring-boot-starter`、`atlas-richie-mcp-schema`、`atlas-richie-mcp-testkit`  
> 基线协议：MCP `2026-07-28`  
> 编写日期：2026-08-10  
> 状态：待中台会话实施

---

## 1. 任务背景

当前 `atlas-richie-mcp-parent` 已具备 MCP Server/Client 的协议、传输、Schema、Registry、Dispatcher、OAuth 和 Spring Boot Starter 基础能力。

Server 侧目前已经支持：

- `@McpTool`、`@McpArgument`、`@McpHeader` 注解；
- `McpAnnotatedToolRegistrar` 扫描 Spring Bean 并注册 Tool；
- `McpToolRegistration` 手工注册；
- `McpToolRegistry` 管理 Tool、revision 和可见性；
- `McpToolDispatcher` 完成输入/输出 Schema 校验与执行；
- Starter 自动暴露 `/mcp` Endpoint。

但当前能力还不足以让传统业务系统做到“引入 Starter、增加少量注解和配置，即可稳定暴露 MCP Tool”。主要问题如下：

1. `McpAnnotatedToolRegistrar` 参数转换仅支持少量基础类型，不能可靠处理 DTO、集合、枚举、Kotlin data class、泛型和复杂返回值。
2. Tool 元数据主要固化在注解或业务手写注册代码中，缺少统一配置覆盖机制。
3. 复杂 Tool 仍需要业务方手工组装 `McpToolDescriptor`、JSON Schema 和 `McpToolRegistration`。
4. `McpToolRegistry` 当前只有 `register/unregister`，热更新若逐项注销再注册，会产生中间态。
5. 缺少标准 Handler SPI、调用拦截器链、配置源 SPI 和刷新生命周期。
6. 注解扫描遍历整个 Spring 容器，缺少扫描包、Bean 范围和启停控制。
7. 缺少面向传统 Java/Kotlin 业务方法的系统化使用规范与测试工具。

本次改造目标不是重做 MCP Server Starter，而是在现有 Starter 上扩展“传统业务能力快速 MCP 化”的产品级能力。

---

## 2. 最终目标

传统业务系统完成 MCP 化时，理想改造量应收敛为：

1. 引入 `atlas-richie-mcp-server-spring-boot-starter`；
2. 在业务 Service 或 MCP Facade 方法上增加 `@McpTool`、`@McpArgument`；
3. 在配置文件中启用 MCP Server，并按需覆盖 Tool 描述、启停和策略；
4. 无需手写 JSON-RPC、HTTP Controller、Tool Registry、JSON Schema 或协议报文。

最小 Java 示例：

```java
@Service
public class InventoryApplicationService {

    @McpTool(
            name = "inventory.query",
            title = "库存查询",
            description = "按门店和商品查询可用库存",
            readOnly = true,
            requiredScopes = "inventory:read")
    public InventoryResult query(
            @McpArgument(name = "storeId", description = "门店 ID") String storeId,
            @McpArgument(name = "itemCode", description = "商品编码", required = false) String itemCode,
            McpCallContext context) {
        return queryInventory(context.tenantId(), storeId, itemCode);
    }
}
```

最小 Kotlin 示例：

```kotlin
@Service
class InventoryApplicationService {
    @McpTool(
        name = "inventory.query",
        description = "按门店和商品查询可用库存",
        readOnly = true,
    )
    fun query(
        @McpArgument(name = "request") request: InventoryQuery,
        context: McpCallContext,
    ): InventoryResult = queryInventory(context.tenantId(), request)
}
```

配置示例：

```yaml
platform:
  component:
    mcp:
      server:
        enabled: true
        path: /mcp
        name: inventory-mcp-server
        version: 1.0.0
        tools:
          scan-packages:
            - com.example.inventory.application
          defaults:
            timeout: 30s
            audit-enabled: true
          overrides:
            inventory.query:
              enabled: true
              description: 按门店和商品查询实时可用库存
              timeout: 10s
```

原有 REST、gRPC、定时任务和内部 Service 调用方式保持不变。

---

## 3. 设计原则

### 3.1 注解优先，配置覆盖

- 注解负责声明代码拥有的业务能力和默认契约；
- YAML/Nacos/数据库配置负责启停、描述覆盖、分组、超时和治理策略；
- 外部配置不能凭空执行任意 Java 类或任意方法；
- 复杂场景允许通过受控 Handler SPI 注册。

### 3.2 Handler 必须是受控代码

配置文件可以描述 Tool，但不应承载业务执行脚本、SQL、SpEL 或任意 Bean 方法表达式。

禁止支持以下高风险配置：

```yaml
handler: "#{applicationContext.getBean(...)}"
sql: "select * from ..."
script: "Runtime.getRuntime().exec(...)"
```

配置中的 `handler-ref` 只能解析到显式注册的 `McpToolHandlerProvider`，或者由 `@McpTool` 扫描得到的安全方法。

### 3.3 中台负责通用运行时，业务负责业务语义

中台负责：

- 扫描与注册；
- JSON Schema 生成与验证；
- 参数转换；
- 调用上下文；
- 超时、取消、审计和可观测；
- 错误映射；
- 配置刷新；
- MCP 协议与传输。

业务服务负责：

- 库存、订单、营收等真实业务逻辑；
- 业务数据权限策略；
- 事务边界；
- 业务错误码；
- 对外部系统的业务级防腐适配。

### 3.4 兼容现有能力

以下能力必须继续可用：

- 现有 `@McpTool` 注解代码无需修改即可运行；
- 现有 `McpToolRegistration` Bean 继续被 Starter 自动加载；
- 现有业务自定义 `McpToolRegistry` Bean 继续覆盖默认 Registry；
- 未配置 `tools` 节点时保持当前行为；
- Public API 不暴露官方 MCP SDK 类型。

---

## 4. 范围与非目标

### 4.1 本次范围

1. 增强注解扫描和方法调用能力。
2. 引入配置化 Tool Definition 和配置覆盖。
3. 引入受控 Handler SPI。
4. 增加 Registry 原子快照替换。
5. 增加 Tool 调用拦截器链。
6. 增加配置刷新和变更通知。
7. 完善 Java/Kotlin、同步/异步调用测试。
8. 迁移 `foundry-mcp-mock-service` 验证真实业务接入体验。

### 4.2 非目标

1. 不在中台组件实现任何库存、订单或营收业务逻辑。
2. 不实现通用 SQL Tool 或脚本 Tool。
3. 不把 MCP Gateway、Discovery 或 Admin 控制面合并到 Server Starter。
4. 不要求业务系统废弃原有 REST/gRPC 接口。
5. 不在本次改造中实现完整 OAuth Authorization Server。
6. 不让 Classpath YAML 自动具备远程热更新能力；热更新由配置源事件触发。

---

## 5. 建议模块职责

### 5.1 `atlas-richie-mcp-api`

新增或增强业务可见的稳定契约：

- `McpToolDefinition`；
- `McpToolHandlerProvider`；
- `McpToolDefinitionSource`；
- `McpToolInvocationInterceptor`；
- `McpToolInvocation`；
- `McpToolDefinitionChangeEvent`；
- 注解扩展字段；
- DTO/异步返回值支持契约。

该模块禁止依赖 Spring、Nacos、数据库实现或官方 MCP SDK。

### 5.2 `atlas-richie-mcp-server-core`

负责：

- Registry 不可变快照；
- 原子 `replaceAll`；
- Definition 编译为 Registration；
- Handler 解析；
- 拦截器链；
- 输入输出校验；
- revision 和 Tool list change 事件。

### 5.3 `atlas-richie-mcp-server-spring-boot-starter`

负责：

- `@ConfigurationProperties`；
- 指定包扫描；
- Spring Bean Handler Resolver；
- Jackson 参数绑定；
- 注解与配置合并；
- Spring 配置刷新事件适配；
- 自动配置拦截器；
- MVC/WebFlux Endpoint 继续复用现有实现。

### 5.4 `atlas-richie-mcp-schema`

负责：

- 方法参数与 DTO 的 JSON Schema 生成；
- Draft 2020-12 校验；
- Schema 复杂度和 `$ref` 安全限制；
- Schema 编译缓存；
- 注解显式 Schema 与自动生成 Schema 的合并规则。

### 5.5 `atlas-richie-mcp-testkit`

负责：

- 注解 Tool 测试夹具；
- 配置 Tool 测试夹具；
- Schema 快照断言；
- 热更新一致性测试；
- Java/Kotlin 参数绑定测试；
- 协议端到端测试。

---

## 6. Public API 建议

### 6.1 `McpToolDefinition`

建议放入 `mcp-api`，保持无 Spring 依赖：

```java
public record McpToolDefinition(
        String name,
        String title,
        String description,
        boolean enabled,
        String handlerRef,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations,
        Set<String> requiredScopes,
        Duration timeout,
        String group,
        Map<String, Object> policies) {
}
```

要求：

- 构造时完成不可变复制；
- `name` 遵守 MCP Tool 命名限制；
- `handlerRef` 仅用于配置型 Tool；
- 注解型 Tool 的 Handler 由扫描器绑定；
- 配置覆盖不能修改为未注册的任意方法。

### 6.2 `McpToolHandlerProvider`

```java
public interface McpToolHandlerProvider {
    String handlerRef();

    McpToolHandler handler();
}
```

业务复杂 Handler 示例：

```java
@Component
public class RevenueQueryToolHandler implements McpToolHandlerProvider {
    @Override
    public String handlerRef() {
        return "revenue.query";
    }

    @Override
    public McpToolHandler handler() {
        return (arguments, context) -> revenueService.query(arguments, context);
    }
}
```

### 6.3 `McpToolDefinitionSource`

```java
public interface McpToolDefinitionSource {
    String sourceId();

    int order();

    Collection<McpToolDefinition> load();
}
```

中台默认提供：

- 注解 Definition Source；
- Spring ConfigurationProperties Definition Source；
- `McpToolRegistration` Bean 兼容 Source。

Nacos、数据库或 Admin 推送适配器作为可选实现，不进入 `mcp-api`。

### 6.4 `McpToolInvocationInterceptor`

```java
public interface McpToolInvocationInterceptor {
    int order();

    CompletionStage<McpToolResponse> intercept(
            McpToolInvocation invocation,
            McpToolInvocationChain chain);
}
```

中台可选适配：

- Deadline/Timeout；
- CancellationToken；
- Tracing；
- Metrics；
- Audit；
- Idempotent；
- Rate Limit；
- Circuit Breaker；
- 敏感数据脱敏。

禁止在 `mcp-server-core` 重新实现这些技术能力，应通过中台已有组件 Adapter 接入。

---

## 7. 注解能力增强

### 7.1 `@McpTool`

现有字段继续保留，建议增加：

- `enabled`：代码默认是否启用；
- `group`：Tool 分组；
- `timeout` 或 `timeoutMs`：默认超时；
- `outputType`/显式 Schema 资源位置（若确有需要）；
- `audit`：是否记录调用审计；
- `idempotentKey` 不建议用表达式，应由 SPI 解析。

字段增加必须提供安全默认值，保证二进制和源码兼容。

### 7.2 `@McpArgument`

建议增强：

- `name`；
- `description`；
- `required`；
- `defaultValue`；
- `format`；
- `example`；
- `enumValues`；
- 数字与字符串边界；
- 是否敏感，仅用于审计脱敏。

复杂 DTO 优先从 Java/Kotlin 类型生成 Schema，而不是在注解中重复描述所有字段。

### 7.3 扫描约束

扫描器必须支持：

- `scan-packages` 白名单；
- Bean 名称过滤；
- 排除包；
- 重复 Tool 名检测；
- Spring AOP Proxy 的目标方法解析；
- Bridge method、Kotlin synthetic method 过滤；
- 不实例化无关 lazy Bean；
- 不扫描中台 MCP 自身包。

默认扫描策略需要兼容现状；后续大版本可考虑默认仅扫描显式包。

---

## 8. 参数绑定与返回值适配

### 8.1 参数绑定

当前基础类型手工转换应替换为统一的 `McpArgumentBinder`，Spring Starter 默认使用 Jackson：

```java
public interface McpArgumentBinder {
    Object bind(Object rawValue, Type targetType, McpArgumentMetadata metadata);
}
```

必须支持：

- String、Boolean、整数、浮点数；
- enum；
- Java record；
- 普通 Java Bean；
- Kotlin data class；
- List、Set、Map；
- 泛型集合；
- Optional 和 nullable；
- 日期时间类型；
- 嵌套 DTO；
- `McpCallContext`、CancellationToken、ProgressReporter 等上下文参数。

参数绑定失败必须映射为安全、明确的 Tool 输入错误，不得返回 Jackson 堆栈。

### 8.2 返回值

支持：

- 普通 DTO；
- Java record；
- Kotlin data class；
- `McpToolResponse`；
- `CompletionStage<T>`；
- Kotlin `suspend`（若 Starter 明确支持 Kotlin 反射/协程）；
- `Mono<T>`（WebFlux 可选）；
- 空返回值。

返回值统一转换为：

- `structuredContent`；
- 可选的 text content；
- `isError`；
- `resultType`。

输出 Schema 存在时必须执行输出校验。

---

## 9. 配置模型

建议扩展 `platform.component.mcp.server.tools`：

```yaml
platform:
  component:
    mcp:
      server:
        tools:
          enabled: true
          scan-packages:
            - com.example.inventory.application
          exclude-packages:
            - com.example.inventory.internal
          fail-fast: true
          refresh-enabled: false
          defaults:
            timeout: 30s
            audit-enabled: true
          definitions:
            external-price-query:
              enabled: true
              handler-ref: price.query
              title: 商品价格查询
              description: 查询商品当前价格
              group: product
              required-scopes:
                - product:read
              input-schema:
                type: object
                required: [itemCode]
                properties:
                  itemCode:
                    type: string
          overrides:
            inventory.query:
              enabled: true
              timeout: 10s
              description: 查询实时可用库存
```

### 9.1 合并优先级

建议采用以下确定性顺序：

1. 注解或 `McpToolRegistration` 提供基础 Definition 和 Handler；
2. 本地配置覆盖元数据和启停；
3. 外部配置源按 `order` 覆盖；
4. 最终 Definition 完整校验；
5. 编译为不可变 Registry Snapshot。

同一优先级出现重复 Tool 名时必须失败，不得静默覆盖。

### 9.2 配置允许修改的内容

允许：

- enabled；
- title/description；
- group；
- timeout；
- required scopes；
- annotations；
- policies；
- Schema（需重新完整校验）。

受限：

- handler-ref 只能指向显式注册 Provider；
- 注解 Tool 的 Handler 默认不可被远程配置替换；
- 不允许配置任意 Java class/method；
- 不允许配置任意 SQL、脚本或 URL 自动执行。

---

## 10. Registry 原子刷新

### 10.1 当前问题

当前 `McpToolRegistry` 使用 `ConcurrentSkipListMap`，`register` 通过 `putIfAbsent` 注册，`unregister` 单项删除。

热更新若执行：

```text
unregister old A
unregister old B
register new A
register new B
```

并发请求可能观察到不完整 Tool 集合。

### 10.2 目标设计

Registry 应持有不可变快照：

```java
record McpToolRegistryState(
        long revision,
        NavigableMap<String, McpResolvedTool> tools) {
}
```

通过 `AtomicReference<McpToolRegistryState>` 原子切换：

1. 从所有 Definition Source 加载候选 Definition；
2. 解析 Handler；
3. 编译所有输入/输出 Schema；
4. 校验重复、权限、配置和安全限制；
5. 构造完整新快照；
6. 单次 CAS/`set` 切换；
7. revision +1；
8. 发布 Tool list changed 事件。

刷新失败时：

- 保留旧快照；
- 记录失败原因和来源 revision；
- 不产生部分更新；
- 健康指标标记配置刷新失败，但业务调用继续使用旧快照。

### 10.3 兼容方法

保留：

- `register`；
- `unregister`；
- `snapshot`；
- `requireAuthorized`。

新增：

- `replaceAll(Collection<McpToolRegistration>)`；
- `replace(McpToolRegistration)`；
- `state()` 或只读 revision/snapshot API；
- 刷新结果对象，包括 oldRevision/newRevision/changedTools。

单项方法内部也应基于快照 CAS 实现。

---

## 11. 热更新机制

### 11.1 核心与配置中心解耦

Server Core 只定义配置变更 SPI，不直接依赖 Nacos：

```java
public interface McpToolDefinitionRefresher {
    McpToolRefreshResult refresh(Collection<McpToolDefinition> definitions);
}
```

Spring Starter 可监听：

- `EnvironmentChangeEvent`；
- 中台 config 组件事件；
- 自定义 `McpToolDefinitionChangeEvent`。

Nacos 适配由配置组件或部署项目提供。

### 11.2 刷新语义

- Classpath YAML 默认只在启动时加载；
- Nacos/数据库配置变更触发显式 refresh；
- refresh 必须幂等；
- 相同内容 hash/revision 不重复刷新；
- 仅元数据变化也需要更新 Tool list revision；
- 正在执行的调用继续使用其开始时解析到的 Registration；
- 新调用使用新快照。

### 11.3 变更通知

Registry revision 变化后，Endpoint 应通过现有订阅/通知机制发布 Tool list changed 语义，Client 可据此失效 `tools/list` 缓存。

---

## 12. 调用链设计

建议执行顺序：

```text
MCP request
  -> protocol/transport validation
  -> resolve immutable Tool registration
  -> visibility and scope policy
  -> input schema validation
  -> argument binding
  -> timeout/cancellation interceptor
  -> tracing/metrics/audit interceptor
  -> idempotent/rate-limit interceptor (optional)
  -> business handler
  -> return value adaptation
  -> output schema validation
  -> safe error mapping
  -> MCP response
```

约束：

- Tool 不存在、输入格式错误和业务异常必须分类；
- CancellationToken 必须能够传到业务 Handler；
- Timeout 不等同于取消，能取消时应触发 token；
- 未知异常必须脱敏；
- 审计不得记录密钥、Token 和敏感参数原文。

---

## 13. 中台能力复用

本次改造必须遵守“已有中台能力优先复用”：

| MCP 运行时需求 | 建议复用 |
|---|---|
| 超时、重试、熔断 | `atlas-richie-concurrency` |
| 幂等 | `atlas-richie-idempotent`（以实际源码 API 为准） |
| 限流 | `atlas-richie-limiter`（以实际源码 API 为准） |
| 审计事件 | 中台审计/消息组件，由业务配置启停 |
| Trace/指标 | `atlas-richie-tracing` / metrics 能力 |
| 配置刷新 | 中台 config 组件 |
| 多租户上下文 | tenant/context 组件 |
| JSON | 中台统一 Jackson/Json Adapter，不在业务层手写多套解析 |

编码前必须读取对应组件源码，不能仅根据组件名称推断 API。

MCP 核心模块保持框架无关；Spring Starter 通过可选 Bean Adapter 接入这些能力。

---

## 14. 安全要求

1. 外部配置不能执行任意 Bean、方法、SQL 或脚本。
2. Handler Provider 必须显式注册并具有稳定 `handlerRef`。
3. Tool 名、Schema、描述长度和复杂度必须有上限。
4. Schema `$ref` 禁止任意远程访问。
5. required scopes 必须由 `McpCallContext` 和 Visibility Policy 校验。
6. 配置刷新前完整验证，失败继续使用旧快照。
7. 敏感参数支持标记和审计脱敏。
8. 异常不得泄露堆栈、SQL、Token、内部 URL 和凭证。
9. 破坏性 Tool 默认应要求显式 `destructive=true` 与相应权限。
10. 自动扫描不得意外暴露未标注业务方法。

---

## 15. `foundry-mcp-mock-service` 迁移验证

目标文件：

```text
atlas-foundry-ai/foundry-mcp-mock-service/
  src/main/kotlin/ai/atlasfoundry/mcp/mock/config/McpComponentConfiguration.kt
```

当前 `mcpToolRegistry()` 手工注册通用工具和营收工具，是本次改造的真实验收样本。

### 15.1 简单 Tool

库存、订单、天气、计算、时间和随机数等 Tool 优先改为：

- 在独立 MCP Facade 方法增加 `@McpTool`；
- 参数使用 `@McpArgument`；
- 业务 Service 保持不变；
- 描述和启停允许配置覆盖。

### 15.2 复杂 Tool

营收工具可以选择：

- 使用 request DTO 作为单一 `@McpArgument`；或
- 实现 `McpToolHandlerProvider`，由 YAML 提供复杂 Schema 和 Tool 描述。

### 15.3 业务模式

现有 `REVENUE/GENERIC/ALL` 硬编码条件迁移为 Tool group 配置：

```yaml
platform:
  component:
    mcp:
      server:
        tools:
          enabled-groups:
            - revenue
```

### 15.4 迁移完成标准

- 删除 Mock 自定义 `mcpToolRegistry()`；
- Mock 不再手工构造 `McpToolDescriptor`；
- Mock 不再手工编写通用 Schema helper；
- Tool 执行业务逻辑和 DTO 仍保留在 Mock；
- `tools/list` 和 `tools/call` 返回与迁移前兼容；
- PUBLIC、STATIC_CREDENTIAL、MCP_OAUTH 三种鉴权行为不受影响。

---

## 16. 实施阶段

### Phase A：稳定 API 与参数绑定

- [ ] 增加 `McpToolDefinition`。
- [ ] 增加 `McpToolHandlerProvider`。
- [ ] 增加 `McpToolDefinitionSource`。
- [ ] 增加 `McpArgumentBinder`。
- [ ] 使用 Jackson 支持 DTO、集合、枚举、Java record。
- [ ] 支持 Kotlin data class。
- [ ] 支持 `CompletionStage`，评估 Kotlin `suspend` 和 Reactor。
- [ ] 保持现有注解二进制兼容。

### Phase B：配置化与注册编译

- [ ] 扩展 `McpServerProperties.tools`。
- [ ] 实现注解 Definition Source。
- [ ] 实现 Properties Definition Source。
- [ ] 实现 Handler Resolver。
- [ ] 实现 Definition 合并和优先级。
- [ ] 启动阶段 fail-fast 校验。
- [ ] 支持扫描包白名单。

### Phase C：Registry 原子快照

- [ ] Registry 改为不可变 State + AtomicReference。
- [ ] 实现 `replaceAll`。
- [ ] 保留 register/unregister 兼容 API。
- [ ] 并发调用只观察完整旧版本或完整新版本。
- [ ] 发布 revision 和 list changed 事件。
- [ ] 刷新失败回滚到旧快照。

### Phase D：治理拦截器

- [ ] 增加 Invocation Interceptor SPI。
- [ ] 接入 timeout/cancellation。
- [ ] 接入 tracing/metrics。
- [ ] 接入审计脱敏。
- [ ] 评估 limiter/idempotent/circuit-breaker Adapter。
- [ ] 所有能力支持配置开关。

### Phase E：热更新与业务迁移

- [ ] 实现 Spring 配置刷新适配。
- [ ] 接入中台 config/Nacos 变更事件。
- [ ] 相同 revision/hash 不重复刷新。
- [ ] 迁移 `foundry-mcp-mock-service`。
- [ ] 运行三鉴权矩阵。
- [ ] 运行 Gateway/Discovery/Orchestrator E2E。

### Phase F：文档与发布

- [ ] 更新组件 README 和详细设计。
- [ ] 增加 Java/Kotlin 快速接入示例。
- [ ] 增加迁移指南。
- [ ] 增加配置元数据和 IDE 提示。
- [ ] 更新 BOM、sources、javadocs。
- [ ] 发布并在下游项目验证 JAR/sources 一致。

---

## 17. 测试矩阵

| 类别 | 必测场景 |
|---|---|
| 注解扫描 | 指定包、排除包、AOP Proxy、重复名称、lazy Bean |
| 参数绑定 | 基础类型、enum、record、DTO、Kotlin data class、集合、嵌套对象、nullable |
| 返回值 | DTO、空值、CompletionStage、McpToolResponse、业务异常 |
| Schema | 自动生成、显式覆盖、非法 Schema、输入失败、输出失败、复杂度限制 |
| 配置合并 | annotation + local override + external override、同优先级冲突 |
| Handler | 未知 handlerRef、重复 Provider、显式 Provider、注解方法 |
| Registry | register、unregister、replaceAll、revision、并发快照 |
| 热更新 | 成功切换、失败保留旧版、相同 revision 忽略、执行中调用不受影响 |
| 权限 | tenant、principal、scope、不可见 Tool、destructive Tool |
| 治理 | timeout、cancellation、audit 脱敏、metrics、trace |
| 兼容性 | 现有 McpToolRegistration、现有自定义 Registry、无 tools 配置 |
| 协议 | server/discover、tools/list、tools/call、list changed |
| 下游 E2E | Mock → Gateway → Discovery → Orchestrator |

并发测试至少应验证：

- 100 个并发 `tools/list/tools/call`；
- 同时反复切换两个完整 Registry Snapshot；
- 任何请求看到的 Tool 集合必须属于完整 revision A 或完整 revision B；
- 不允许出现部分集合、重复 Tool 或空窗期。

---

## 18. 验收标准

### 18.1 开发体验

新建传统 Spring Boot 服务后：

- 只引入 Starter；
- 增加一个 `@McpTool` 方法；
- 增加最小 Server 配置；
- 即可通过 `/mcp` 完成 `server/discover`、`tools/list` 和 `tools/call`。

业务方不得需要：

- 编写 MCP Controller；
- 构造 JSON-RPC；
- 构造 `McpToolRegistry`；
- 手工处理协议 Header；
- 手工序列化 DTO；
- 手写基础 JSON Schema。

### 18.2 功能

- Java/Kotlin 复杂参数绑定通过；
- 注解 Tool 和配置 Tool 可共存；
- 配置可覆盖启停与元数据；
- Registry 热更新原子化；
- required scopes 和可见性有效；
- timeout/cancellation 可传递；
- 错误映射不泄露内部信息。

### 18.3 兼容性

- 现有组件测试全部通过；
- 现有 `McpToolRegistration` 调用方无需修改；
- `foundry-mcp-mock-service` 迁移前后 Tool 协议结果兼容；
- Gateway、Discovery、Orchestrator 无协议回归；
- sources、JAR 与 BOM 版本一致。

### 18.4 质量门禁

- 所有新增 Public API 有 Javadoc；
- 配置项生成 Spring configuration metadata；
- 新增 API 通过二进制兼容检查；
- `git diff --check` 无格式问题；
- 不引入业务项目依赖；
- 不引入任意脚本/SQL 执行风险。

---

## 19. 已知风险与实施注意事项

1. 自动生成 JSON Schema 容易受到 Java/Kotlin 泛型、nullable 和 Jackson 注解差异影响，必须以真实序列化模型为准。
2. Spring AOP Proxy 上的方法和参数注解需要解析目标类，不能只读取代理类。
3. 热更新不能使用逐项 `unregister/register` 模拟原子替换。
4. `@RefreshScope` 重建 Bean 不等于 Registry 热更新，需要显式刷新管理器。
5. Tool 描述变化也会影响模型选择，需要更新 revision 和通知 Client 失效缓存。
6. 远程配置修改 Schema 属于高风险操作，必须完整编译验证后再切换。
7. 动态配置不能改变业务 Handler 为任意 Bean 方法。
8. 不应在本次改造中把 Gateway/Discovery 职责塞回 Server Starter。

---

## 20. 建议的中台会话执行提示

可以将以下内容作为新会话的首条任务指令：

> 请阅读 `atlas-richie-component/atlas-richie-mcp-parent/docs/zh/mcp-server-starter-business-tool-adaptation-plan.md`，按照文档的 Phase A → Phase F 顺序改造现有 `atlas-richie-mcp-server-spring-boot-starter`。必须基于当前源码扩展，保留 `@McpTool`、`McpToolRegistration` 和自定义 `McpToolRegistry` 的兼容性。先完成 API、参数绑定、配置化注册和 Registry 原子快照，再实现热更新与治理适配。每个阶段必须补单元测试；完成后重新安装中台组件，并在 `atlas-foundry-ai/foundry-mcp-mock-service` 上做迁移及完整 MCP E2E 验证。禁止通过任意 Bean 表达式、脚本或 SQL 实现配置型 Handler。

---

## 21. 交付物清单

- [ ] `mcp-api` 新增稳定定义、Handler、Source 和 Interceptor API。
- [ ] `mcp-server-core` 原子 Registry 与调用链。
- [ ] `mcp-server-spring-boot-starter` 注解增强、配置绑定和刷新适配。
- [ ] `mcp-schema` DTO Schema 生成/验证增强。
- [ ] `mcp-testkit` 新增接入和热更新测试夹具。
- [ ] Java/Kotlin 示例项目或测试 Fixture。
- [ ] Mock Server 迁移结果。
- [ ] 三鉴权矩阵和全链路 E2E 报告。
- [ ] README、配置说明、迁移指南和版本发布记录。

