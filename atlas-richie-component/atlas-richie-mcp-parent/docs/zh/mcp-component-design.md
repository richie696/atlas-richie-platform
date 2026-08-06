# Atlas Richie MCP Server/Client Adapter 组件详细设计

> 状态：P0-P3 可运行基础能力已落地；P4 为部署侧 conformance、业务迁移与生产观测验收
> 基线协议：MCP `2026-07-28`
> 兼容协议：MCP `2025-11-25`（legacy）
> 更新时间：2026-08-06
> 组件目录：`atlas-richie-component/atlas-richie-mcp-parent`

---

## 1. 决策摘要

### 1.1 最终决策

在技术中台新增 `atlas-richie-mcp-parent`，同时封装 MCP Server 与 MCP Client。

业务系统只面向中台稳定 API：

- Server 业务方声明 Tool、Resource、Prompt 和业务处理函数；
- Client 业务方调用 `McpOperations`；
- 业务方不构造 JSON-RPC 报文；
- 业务方不处理协议版本、`_meta`、HTTP 标准头、SSE、STDIO 分帧；
- 业务方不依赖 MCP 官方 SDK 类型；
- 业务方不自行实现 OAuth discovery、token、scope step-up；
- 业务方不感知 modern/legacy 协议切换。

组件内部以 Adapter/Strategy 隔离协议版本。未来 MCP 发布新协议时：

1. 增加新的 `ProtocolDialect`；
2. 更新协议 Schema 快照和 conformance tests；
3. 在组件内部调整协商优先级；
4. 发布中台组件新版本；
5. 业务代码无需修改或重新实现 Tool。

### 1.2 为什么必须同时封装 Server 与 Client

`2026-07-28` 相比 `2025-11-25` 是协议范式切换：

- 无状态请求替代连接级会话；
- `server/discover` 替代 `initialize`；
- 每个请求携带协议版本和 Client Capabilities；
- `resultType` 成为结果必填字段；
- MRTR 替代 Server 主动发起 JSON-RPC Request；
- `subscriptions/listen` 替代独立 GET SSE 和资源订阅方法；
- Streamable HTTP 删除 Session ID、GET stream、SSE resume；
- OAuth discovery、issuer、resource indicator、scope step-up 更严格；
- list/read 结果新增强制缓存提示；
- HTTP 新增标准化镜像 Header。

只升级 Server 会导致旧 Client 无法访问；只升级 Client 会导致无法调用旧 Server。因此中台必须提供 dual-era 双端实现。

官方依据：

- [Key Changes](https://modelcontextprotocol.io/specification/2026-07-28/changelog)
- [Versioning and Compatibility](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning)

---

## 2. 目标、非目标与约束

### 2.1 目标

1. MCP `2026-07-28` modern 协议完整适配。
2. MCP `2025-11-25` legacy 协议双时代兼容。
3. Server 与 Client 共用同一套规范模型、验证器和兼容策略。
4. 对业务提供协议无关、传输无关、SDK 无关的稳定 API。
5. OAuth、HTTP、Cache、Tracing、Concurrency、Tenant 等能力复用中台组件。
6. 通过官方 MCP Conformance Suite，且 CI 不允许新增未基线化失败。
7. 协议升级只影响 Adapter 内部。
8. 支持 Java 与 Kotlin 业务服务。
9. 支持 Streamable HTTP 和 STDIO；将 HTTP+SSE 隔离为 legacy adapter。
10. 提供可配置的安全边界、超时、取消、限流、熔断和审计。

### 2.2 非目标

1. 不在 MCP 组件中实现库存、订单、数据库查询等业务。
2. 不提供 `database.query(sql)` 一类任意 SQL Tool。
3. 不把 MCP Gateway、Discovery 控制面塞进本组件。
4. 不把 OAuth Authorization Server 的全部能力重复实现到 MCP 组件。
5. 不向业务暴露官方 SDK 的 Request、Result、Transport 类型。
6. 不承诺对未声明的自定义 Transport 自动兼容。
7. 第一阶段不实现已从 Core 移出的 Tasks；Tasks 作为独立 Extension Adapter。

### 2.3 设计约束

- 官方 TypeScript Schema 是 wire model 的事实来源。
- 中台 Public API 是业务编译期契约，协议 Schema 是组件内部契约。
- 任何网络 URL 解析、远程 `$ref`、icon 拉取、OAuth metadata 拉取都视为不可信输入。
- modern 请求不依赖连接、进程、Session 或本地内存保存协议上下文。
- legacy 会话状态只能存在于 legacy adapter。
- 所有版本判断必须集中在 `ProtocolNegotiator`，业务 handler 禁止判断协议版本。

---

## 3. 组件分层与 Maven 模块

### 3.1 模块规划与当前状态

```text
atlas-richie-mcp-parent/
├── mcp-api                 # P0 已实现
├── mcp-protocol            # P0 已实现
├── mcp-schema              # P1 已实现首版
├── mcp-server-core         # P1 Registry/Dispatcher 已实现
├── mcp-transport-http      # P1 request validator 已实现
├── mcp-transport-stdio         # P3 首版已实现
├── mcp-security-oauth          # P2 Adapter 首版已实现
├── mcp-server-spring-boot-starter # P1 MVC/WebFlux 首版已实现
├── mcp-client-spring-boot-starter # P2 协商/缓存首版已实现
├── mcp-testkit             # P0 已建立
└── docs/
```

### 3.2 模块职责

| 模块 | 可被业务直接依赖 | 职责 |
|---|---:|---|
| `mcp-api` | 是 | 稳定注解、业务 DTO、Handler、Client Operations、异常 |
| `mcp-protocol` | 否 | JSON-RPC、Schema、Dialect、协商、modern/legacy codec |
| `mcp-schema` | 否 | 稳定 JSON Schema Adapter、Draft 2020-12 编译与验证、安全限制 |
| `mcp-server-core` | 否 | 无 Spring 的 Registry、Dispatcher、授权可见性与调用内核 |
| `mcp-transport-http` | 否 | Streamable HTTP、request-scoped SSE、标准 Header |
| `mcp-transport-stdio` | 否 | 子进程、newline framing、EOF 与生命周期首版 |
| `mcp-security-oauth` | 否 | MCP OAuth metadata/token SPI、Bearer challenge 与 URI 安全策略首版 |
| `mcp-server-spring-boot-starter` | 是 | Server 自动配置、注册表、Spring MVC/WebFlux JSON/SSE Endpoint |
| `mcp-client-spring-boot-starter` | 是 | Client 自动配置、HTTP 调用、版本 discovery 协商与 list TTL 缓存 |
| `mcp-testkit` | 测试作用域 | 契约测试、fixture、双时代测试、conformance launcher |

### 3.3 依赖方向

```text
业务 Server ──> server-starter ──> server-core ──> api / protocol / schema
                         ├───────────────────────> transport-http / transport-stdio
                         └───────────────────────> security-oauth

业务 Client ──> client-starter ──> api
                         ├───────> protocol
                         ├───────> transport-http / transport-stdio
                         └───────> security-oauth

protocol / transport / security ──X──> 业务代码
```

官方 Java SDK若使用，只能位于 `protocol` 或 transport 实现模块，必须声明为实现细节，禁止出现在 `mcp-api` 的方法签名和异常 cause contract 中。
同理，JSON Schema 引擎仅存在于 `mcp-schema` 内部；业务与 `server-core` 只依赖
`McpJsonSchemaValidator`、`McpCompiledSchema`、`McpSchemaValidationResult` 等中台稳定类型。

---

## 4. 面向业务的稳定 API

### 4.1 Server Tool API

业务方的最小实现：

```java
@Component
public class InventoryTools {

    private final InventoryApplicationService inventoryService;

    public InventoryTools(InventoryApplicationService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @McpTool(
        name = "inventory.query",
        title = "库存查询",
        description = "按门店和商品查询可用库存",
        requiredScopes = "inventory:read"
    )
    public InventoryResult query(
            @McpArgument(description = "门店 ID") String storeId,
            @McpArgument(description = "商品编码", required = false) String itemCode,
            McpCallContext context
    ) {
        return inventoryService.query(context.tenantId(), storeId, itemCode);
    }
}
```

稳定注解：

- `@McpTool`
- `@McpArgument`
- `@McpHeader`：业务希望某参数镜像到 HTTP Header 时使用，中台内部生成 `x-mcp-header`
- `@McpResource`
- `@McpResourceTemplate`
- `@McpPrompt`
- `@McpCompletion`

稳定上下文：

```java
public interface McpCallContext {
    Optional<String> principalId();
    Optional<String> tenantId();
    Set<String> grantedScopes();
    Deadline deadline();
    CancellationToken cancellation();
    ProgressReporter progress();
    TraceContext trace();
}
```

`McpCallContext` 不暴露：

- JSON-RPC id；
- `_meta` 原始 Map；
- 协议版本；
- HTTP Servlet Request；
- 官方 SDK Context。

需要透传扩展元数据时，提供带 namespace allowlist 的稳定 `McpExtensionContext`，不得直接暴露保留的 `io.modelcontextprotocol/*` key。

### 4.2 Tool 返回模型

常规业务方法可以直接返回 DTO，由组件转换为：

- `structuredContent`：结构化 JSON；
- `content`：可配置生成文本摘要；
- `isError=false`；
- `resultType=complete`。

显式高级返回：

```java
public sealed interface McpToolOutcome<T> {
    record Success<T>(T value, List<McpContent> content) implements McpToolOutcome<T> {}
    record BusinessError<T>(String code, String message, T details) implements McpToolOutcome<T> {}
    record InputRequired<T>(List<McpInputRequest> requests, OpaqueRequestState state)
            implements McpToolOutcome<T> {}
}
```

约束：

- Tool 业务校验失败映射为 Tool execution error（`isError=true`），不是 JSON-RPC protocol error。
- JSON 格式错误、方法不存在、协议字段错误才映射为 JSON-RPC error。
- 未捕获异常不得把堆栈、SQL、Token 或内部 URL返回客户端。

### 4.3 Client API

业务 Client 只调用：

```java
public interface McpOperations {
    List<McpToolDescriptor> listTools(McpServerRef server, McpRequestOptions options);

    <T> McpToolResponse<T> callTool(
            McpServerRef server,
            String toolName,
            Object arguments,
            Class<T> responseType,
            McpRequestOptions options
    );

    List<McpResourceDescriptor> listResources(McpServerRef server, McpRequestOptions options);
    McpResourceContent readResource(McpServerRef server, URI uri, McpRequestOptions options);
    List<McpPromptDescriptor> listPrompts(McpServerRef server, McpRequestOptions options);
    McpPromptContent getPrompt(
            McpServerRef server,
            String promptName,
            Map<String, String> arguments,
            McpRequestOptions options
    );
}
```

`McpOperations` 内部自动完成：

- era probe；
- 版本选择；
- capability 校验；
- `_meta`；
- HTTP Header；
- OAuth；
- pagination；
- cache；
- scope step-up；
- MRTR；
- retry/cancellation；
- wire/result 映射。

### 4.4 API 稳定性规则

1. `mcp-api` 遵循语义化版本。
2. 使用 `japicmp`/Revapi 检查二进制兼容。
3. 使用 ArchUnit 禁止 API package import `io.modelcontextprotocol.*`。
4. 枚举对未知值必须提供 `UNKNOWN` 或保留 raw value，避免协议新增值导致反序列化失败。
5. Public DTO 使用中台自己的类型。
6. official SDK 版本升级不得要求业务重新编译。

---

## 5. 内部协议抽象

### 5.1 ProtocolDialect

```java
interface McpProtocolDialect {
    ProtocolVersion version();
    Era era();
    McpWireRequest encode(NormalizedRequest request);
    NormalizedRequest decode(McpWireRequest request);
    McpWireResponse encode(NormalizedResponse response);
    NormalizedResponse decode(McpWireResponse response);
    ValidationResult validateRequest(RequestEnvelope envelope);
    CapabilityRules capabilityRules();
}
```

首批实现：

- `Mcp20260728Dialect`
- `Mcp20251125LegacyDialect`

### 5.2 归一化模型

内部执行链只处理：

- `NormalizedRequest`
- `NormalizedResponse`
- `NormalizedCapabilities`
- `NormalizedContent`
- `NormalizedError`
- `NormalizedAuthContext`

Dialect 负责将不同协议时代映射到归一化模型。例如：

| 语义 | 2026-07-28 | 2025-11-25 |
|---|---|---|
| 能力发现 | `server/discover` | `initialize` |
| 请求上下文 | 每请求 `_meta` | initialize 后 session |
| 普通结果 | `resultType=complete` | 无 `resultType` |
| Server 需要 Client 输入 | MRTR `input_required` | Server initiated request |
| 变更订阅 | `subscriptions/listen` | GET SSE/旧通知 |
| 健康探测 | Transport/应用健康 | `ping` |

### 5.3 Schema 快照

`mcp-protocol` 必须内置：

```text
src/main/resources/mcp-schema/
├── 2026-07-28/schema.json
├── 2026-07-28/source-metadata.json
└── 2025-11-25/schema.json
```

`source-metadata.json` 记录：

- 官方仓库 URL；
- commit SHA；
- 协议版本；
- 下载时间；
- SHA-256；
- 生成器版本。

禁止构建时在线拉取 Schema，保证可重复构建。

---

## 6. Base Protocol 封装细节

官方页面：

- [Base Protocol Overview](https://modelcontextprotocol.io/specification/2026-07-28/basic)
- [Schema Reference](https://modelcontextprotocol.io/specification/2026-07-28/schema)

### 6.1 JSON-RPC Request

组件处理规则：

1. `jsonrpc` 必须为 `"2.0"`。
2. Request 必须有 string 或 integer `id`。
3. Request `id` 不得为 `null`。
4. 同一发送端未完成请求的 ID 不得重复。
5. `method` 必须为 string。
6. `params` 若存在必须符合对应方法 Schema。
7. 解析错误返回 `-32700`。
8. 非法请求返回 `-32600`。
9. 非法参数返回 `-32602`。
10. 业务 Handler 永远拿不到未经 Schema 验证的参数。

Client 使用单调序列或 UUID 产生 ID；retry 必须生成新 ID。

### 6.2 JSON-RPC Result

modern Result：

- 必须有与 Request 相同的 `id`；
- 必须存在 `result`；
- `result.resultType` 必须存在；
- `complete` 表示完成；
- `input_required` 表示 MRTR 中间结果；
- 未识别 resultType 视为非法；
- Client 读取 legacy 结果缺少 resultType 时归一化为 `complete`。

Server 每个 modern result SHOULD 写入：

```json
{
  "_meta": {
    "io.modelcontextprotocol/serverInfo": {
      "name": "...",
      "version": "..."
    }
  }
}
```

`serverInfo` 仅用于显示、日志和调试，禁止参与鉴权或行为分支。

### 6.3 JSON-RPC Error

统一错误表：

| Code | 语义 | HTTP 状态 | 组件处理 |
|---:|---|---:|---|
| `-32700` | Parse error | 400 | `McpMalformedMessageException` |
| `-32600` | Invalid Request | 400 | 拒绝进入 dispatcher |
| `-32601` | Method not found | 404（modern HTTP） | 能力/方法不存在 |
| `-32602` | Invalid params | 400 | 参数、cursor、resource not found |
| `-32603` | Internal error | 500 | 脱敏后返回 |
| `-32020` | HeaderMismatch | 400 | Header/body 不一致 |
| `-32021` | MissingRequiredClientCapability | 400 | data 返回所需 capability |
| `-32022` | UnsupportedProtocolVersion | 400 | data 返回 supported/requested |

规则：

- `-32000..-32019` 为 legacy/实现保留区，新实现不分配；
- `-32020..-32099` 仅使用官方定义；
- 新增平台业务错误使用 JSON-RPC 保留区之外的整数，或优先映射 Tool error；
- modern 的 resource not found 使用 `-32602`；
- Client 仍接受 legacy `-32002` 并归一化。

### 6.4 Notification

1. Notification 不得包含 `id`。
2. 接收方不得返回 JSON-RPC response。
3. HTTP Adapter 返回协议规定的无 JSON-RPC body 状态。
4. malformed/unknown notification 默认记录并忽略，避免破坏 fire-and-forget。
5. Notification handler 必须限流，防止 progress/log flooding。

### 6.5 `_meta`

modern Client 每个 Request 自动写入：

- `io.modelcontextprotocol/protocolVersion`：必填；
- `io.modelcontextprotocol/clientCapabilities`：必填；
- `io.modelcontextprotocol/clientInfo`：默认写入，可关闭；
- `io.modelcontextprotocol/logLevel`：Client 显式选择接收请求级日志时写入；
- `progressToken`：业务请求选择 progress 时写入；
- `traceparent`、`tracestate`、`baggage`：Tracing Adapter 注入；
- Extension metadata：仅协商成功后写入。

Server 验证：

- 缺少必填 meta：HTTP 400 + `-32602`；
- 请求所需 capability 未声明：HTTP 400 + `-32021`；
- 不依赖前一请求保存的 capabilities；
- 不用 clientInfo 做安全判断；
- 保留 namespace 不允许业务覆盖。

### 6.6 Icon

Tool、Prompt、Resource、Implementation 的 icon 由中台模型统一承载：

- 仅允许 `https:` 或 `data:`；
- 禁止 `javascript:`、`file:`、`ftp:`、`ws:` 和本地应用 scheme；
- 不跟随跨 origin redirect；
- 拉取 icon 不携带 cookie、Authorization 或 Client 凭据；
- 默认限制同 origin；
- 限制字节、像素、帧数；
- MIME 与 magic bytes 双校验；
- SVG 默认关闭；开启时必须 sanitize；
- Client UI 不可信任 title、description、icon。

---

## 7. 无状态、版本协商与 Dual-era

官方页面：

- [Versioning and Compatibility](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning)
- [Server Discovery](https://modelcontextprotocol.io/specification/2026-07-28/server/discover)

### 7.1 modern 无状态原则

1. Server 不从连接推断 protocol version、capabilities、client identity。
2. 同一连接可交错多个 task/thread/conversation。
3. STDIO 进程生命周期不等于会话生命周期。
4. 跨请求状态使用显式 handle。
5. Handle 由 Server 生成，由 Client 在后续请求中显式携带。
6. Handle 不得暗含未经保护的权限上下文。
7. 请求可落到任意 Server 实例，不依赖粘性会话。

### 7.2 `server/discover`

Server 必须实现，返回：

- `supportedVersions`
- `capabilities`
- `_meta.io.modelcontextprotocol/serverInfo`
- 可选 `instructions`
- `resultType=complete`
- `ttlMs`
- `cacheScope`

Client：

- 可以直接调用具体方法并处理 `-32022`；
- STDIO dual-era Client SHOULD 先 discover；
- HTTP dual-era Client 发送 modern 请求并检查 400 JSON-RPC body；
- era 结果按 STDIO process 或 HTTP origin 缓存；
- cached era 后续失败时重新 probe。

### 7.3 Server 兼容判定

```text
收到请求
  ├─ 存在 modern required _meta
  │    ├─ 支持版本 -> modern dialect
  │    └─ 不支持版本 -> -32022 + supported
  ├─ method == initialize
  │    ├─ legacy enabled -> legacy dialect
  │    └─ legacy disabled -> 错误中明确列出 supported modern versions
  └─ 缺少 modern meta
       └─ 400 / -32602
```

### 7.4 Client 兼容判定

STDIO：

1. `server/discover` + preferred modern meta；
2. DiscoverResult：modern；
3. recognized modern error：modern，按 supported 重试；
4. 其他错误或合理超时：legacy，执行 initialize；
5. 不可只根据某一个 legacy error code 判定。

Streamable HTTP：

1. 发送 modern POST；
2. 成功或 recognized modern error：modern；
3. 4xx 且没有 recognized modern JSON-RPC error：legacy fallback；
4. HTTP+SSE fallback 仅由显式兼容配置开启。

### 7.5 兼容策略配置

```yaml
platform:
  component:
    mcp:
      protocol:
        preferred-version: 2026-07-28
        compatibility-mode: dual
        legacy-versions:
          - 2025-11-25
        era-probe-timeout: 2s
        persist-era-cache: false
```

业务不应填写协议版本；上述配置面向平台运维和兼容测试，默认值由组件提供。

---

## 8. Transport 封装

官方页面：

- [Transport Overview](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports)
- [Streamable HTTP](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http)
- [STDIO](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/stdio)

### 8.1 Transport SPI

```java
interface McpTransport {
    TransportKind kind();
    CompletionStage<TransportResponse> exchange(TransportRequest request);
    Flow.Publisher<TransportMessage> stream(TransportRequest request);
    void cancel(RequestHandle request);
}
```

Transport 只负责 framing、delivery、metadata carrier、cancel、shutdown，不解释业务语义。

### 8.2 Streamable HTTP

modern 规则：

1. Server 暴露单个 POST endpoint，默认 `/mcp`。
2. 每个 JSON-RPC Request/Notification 是独立 POST。
3. Request 返回单个 JSON Object，或该请求专属 SSE stream。
4. SSE stream 中只能出现该请求相关 notification，最终结束于 response。
5. 长期变更通知使用 `subscriptions/listen` 的 response stream。
6. 删除独立 GET stream。
7. 删除 `Mcp-Session-Id`。
8. 删除 `Last-Event-ID` 与 SSE event id resume。
9. stream 断开代表 in-flight request 丢失；Client 以新 ID 重新发起。
10. Server 验证 Origin；非法 Origin 返回 403。
11. 本地 Server 默认只绑定 `127.0.0.1`。
12. Server 支持 `application/json` 和 `text/event-stream` 内容协商。

### 8.3 HTTP 标准 Header

每个 POST：

- `MCP-Protocol-Version`：必须存在；
- 值必须和 body `_meta.io.modelcontextprotocol/protocolVersion` 一致；
- `Mcp-Method`：必须镜像 JSON-RPC method；
- `Mcp-Name`：对 tool/prompt/resource 请求按规范镜像 name 或 URI；
- `Mcp-Param-{Name}`：按 Tool Schema 的 `x-mcp-header` 镜像。

Server 校验 body/header 不一致：

- HTTP 400；
- JSON-RPC `-32020 HeaderMismatch`。

Header 名大小写不敏感，Header 值大小写敏感。

当前 `mcp-transport-http` 已落地无 Servlet/WebFlux 依赖的请求验证内核；Server Starter 另外提供 MVC 与 WebFlux 框架绑定：

- 只接受 POST；GET/DELETE 由框架层映射为 405；
- `Content-Type` 必须为 `application/json`，`Accept` 必须同时包含
  `application/json` 与 `text/event-stream`；
- Origin 存在时必须通过注入的 `McpOriginPolicy`，否则 403；
- Header 名大小写不敏感，重复的安全 Header 按非法请求拒绝；
- `MCP-Protocol-Version`、`Mcp-Method`、必要的 `Mcp-Name` 与 body 交叉验证；
- `Mcp-Name` 支持规范 Base64 sentinel，并严格拒绝非法 Base64/UTF-8；
- 不支持版本返回 HTTP 400 + `UnsupportedProtocolVersion(-32022)`；
- 镜像 Header 失败返回 HTTP 400 + `HeaderMismatch(-32020)`；
- 非法 JSON-RPC 请求返回 HTTP 400，并保留对应 JSON-RPC error。

WebFlux/MVC 绑定按 `Accept` 进行 JSON/SSE 内容协商；普通请求为 request-scoped 响应，
`subscriptions/listen` 使用 response stream 保持长连接，并不恢复旧的 Last-Event-ID 语义。

`Mcp-Param-*` 与 `x-mcp-header` 的参数值提取、大小写不敏感匹配、Base64 sentinel、
重复/未知参数、primitive 类型和 body/header 交叉验证已由
`McpStreamableHttpRequestValidator` 实现；对象或数组参数不会镜像到 Header。

### 8.4 `x-mcp-header`

Server Tool 参数注解：

```java
@McpArgument
@McpHeader("Region")
String region
```

组件生成：

```json
{
  "region": {
    "type": "string",
    "x-mcp-header": "Region"
  }
}
```

限制：

- 仅 string、integer、boolean；
- integer 必须在 JavaScript safe integer 范围；
- header token 合法；
- Schema 内大小写不敏感唯一；
- null/缺失参数不发送 Header；
- Server 必须校验 Header 与 body exact property path 值一致；
- 中间 Gateway 对未知 `Mcp-Param-*` 必须转发。

值编码：

- 安全 ASCII 直接发送；
- 非 ASCII、控制符、首尾空白使用 UTF-8 Base64；
- 格式固定为 `=?base64?{value}?=`；
- 原值本身匹配 sentinel 也必须 Base64；
- Server 解码后比较 body。

### 8.5 STDIO

modern framing：

- stdin/stdout；
- 每条 JSON-RPC message 单独一行；
- newline-delimited；
- message 内不得包含真实换行；
- stdout 只能写 MCP message；
- 日志只能写 stderr；
- Client 不向 Server 写 JSON-RPC response；
- Server 不向 Client写 JSON-RPC request，MRTR 通过 result；
- EOF 是首选 graceful shutdown；
- 超时后按 TERM/KILL 平台机制升级。

重要兼容：

- 禁止沿用 LSP `Content-Length` framing 作为 standard STDIO；
- 如需兼容历史私有实现，将其命名为 `legacy-content-length` custom transport，默认关闭；
- `server/discover` 用于 STDIO era probe。

### 8.6 取消

- HTTP：关闭 request-scoped SSE response stream；
- STDIO：发送 `notifications/cancelled`；
- Server 尽快停止任务并释放资源；
- Server 不再对已取消请求响应；
- Client 忽略取消后迟到的 response；
- 所有请求有 soft timeout 和 hard maximum timeout；
- progress 可以延长 soft timeout，但不能突破 hard timeout。

官方页面：[Cancellation](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/cancellation)

### 8.7 Deprecated HTTP+SSE 与 Custom Transport

- HTTP+SSE 已 Deprecated，新实现不作为默认能力；
- Gateway 可提供隔离的 legacy adapter；
- WebSocket 属于 custom transport，不得标记为 MCP standard transport；
- custom transport 必须复用相同协议语义与验证器；
- 控制面必须明确标注 standard、deprecated、custom。

---

## 9. Server Feature 封装

### 9.1 Tools

官方页面：[Tools](https://modelcontextprotocol.io/specification/2026-07-28/server/tools)

组件封装：

- `tools/list`
- `tools/call`
- `notifications/tools/list_changed`
- Tool Registry
- Schema generation/validation
- deterministic ordering
- pagination/cache
- authorization filtering
- MRTR
- structured output

Tool Descriptor 支持：

- `name`
- `title`
- `description`
- `inputSchema`
- `outputSchema`
- `icons`
- annotations/meta

Tool name：

- 组件启动时校验格式和唯一性；
- 默认建议 `{domain}.{action}`；
- 版本不编码在 Tool name，版本由业务契约管理；
- 不允许两个 Bean 声明同名 Tool；
- 输出按 name 稳定排序。

Schema：

- input/output 使用 JSON Schema 2020-12；
- 无参数 Tool 仍生成 object schema；
- 参数默认值来自显式注解/DTO，不猜测 Kotlin default；
- `outputSchema` 存在时必须验证 structuredContent；
- `structuredContent` 可为任意 JSON value；
- content 支持 text、image、audio、resource_link、embedded resource；
- binary 内容有大小和 MIME allowlist。

Tool 错误分层：

| 错误 | Wire 形式 |
|---|---|
| malformed arguments envelope | JSON-RPC `-32602` |
| Tool 不存在 | JSON-RPC `-32602` 或方法语义约定 |
| Tool 参数业务校验失败 | Tool result `isError=true` |
| Tool 执行业务失败 | Tool result `isError=true` |
| Adapter 内部失败 | JSON-RPC `-32603`，脱敏 |
| scope 不足 | HTTP 403 + WWW-Authenticate |

人机安全：

- Tool 支持 `readOnly`、`destructive`、`idempotent`、`openWorld` 中台注解；
- Gateway/Host 可依据注解决定人工确认；
- destructive Tool 默认需要策略确认；
- 注解仅为提示，不能代替服务端权限验证。

### 9.2 Resources

官方页面：[Resources](https://modelcontextprotocol.io/specification/2026-07-28/server/resources)

组件封装：

- `resources/list`
- `resources/templates/list`
- `resources/read`
- `notifications/resources/list_changed`
- resource update notification（通过 subscriptions）

Resource Descriptor：

- `uri`
- `name`
- `title`
- `description`
- `mimeType`
- `size`
- `icons`
- annotations/meta

Resource Content：

- text；
- blob/Base64；
- URI 与 MIME；
- 最大字节限制；
- binary MIME allowlist；
- URI scheme allowlist；
- canonicalization 后再做授权。

modern 差异：

- `resources/subscribe`/`resources/unsubscribe` 被移除；
- 使用 `subscriptions/listen.resourceSubscriptions`；
- resource not found 返回 `-32602`；
- Client 兼容 legacy `-32002`。

### 9.3 Prompts

官方页面：[Prompts](https://modelcontextprotocol.io/specification/2026-07-28/server/prompts)

组件封装：

- `prompts/list`
- `prompts/get`
- `notifications/prompts/list_changed`
- 参数 Schema/required；
- message role/content；
- pagination/cache；
- MRTR。

Prompt 输出中的 text/image/audio/resource 均经过统一 Content Mapper 和大小限制。

### 9.4 Completion

官方页面：[Completion](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/completion)

组件封装：

- `completion/complete`
- `ref/prompt`
- `ref/resource`
- argument/context；
- values/total/hasMore；
- capability `completions`。

安全与稳定性：

- 限制候选数量；
- 限制查询时长；
- 不因自动补全绕过 Resource/Prompt 的权限；
- context arguments 同样做 Schema validation。

---

## 10. Pagination 与 Caching

### 10.1 Pagination

官方页面：[Pagination](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/pagination)

支持分页的方法：

- `tools/list`
- `prompts/list`
- `resources/list`
- `resources/templates/list`

规则：

- cursor 是 opaque string；
- Client 不解析、不修改；
- 空字符串是有效 cursor；
- page size 由 Server 决定；
- 无 `nextCursor` 才表示结束；
- invalid cursor 返回 `-32602`；
- cursor 应稳定、短期有效、带完整性保护；
- cursor 若包含 principal/filter/sort 信息，必须 HMAC/AEAD；
- Client 可配置最大页数和最大总项数，防无限分页。

业务 API 默认自动翻页；高级 API提供 `McpPage<T>`，但不暴露协议 DTO。

### 10.2 Caching

官方页面：[Caching](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/caching)

modern complete result 必须带 cache hints 的操作：

- `server/discover`
- `tools/list`
- `prompts/list`
- `resources/list`
- `resources/templates/list`
- `resources/read`

字段：

- `ttlMs >= 0`
- `cacheScope = public | private`

规则：

- `input_required` 不缓存；
- 带 `inputResponses` 或 `requestState` 的 retry 不缓存；
- key 至少包含 server identity、method、影响结果的 params、protocol dialect；
- paginated cache key 包含 cursor；
- private cache key 还包含 authorization context fingerprint；
- fingerprint 不直接保存 access token；
- public cache 可共享；
- private cache 不跨 principal/token context；
- TTL 是 freshness hint，不是后台轮询周期；
- `ttlMs=0` 立即 stale；
- legacy 缺失 TTL 按 0；
- 收到 list_changed 后相关 cache 立即失效；
- re-fetch 失败是否 stale-if-error 由策略配置；
- 缓存复用中台 Cache API，不在 MCP 组件直接操作 Redis。

---

## 11. Subscriptions、Progress、Cancellation

### 11.1 Subscriptions

官方页面：[Subscriptions](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/subscriptions)

modern：

- Client 发送 `subscriptions/listen`；
- 声明感兴趣的 `toolsListChanged`、`promptsListChanged`、`resourcesListChanged`、`resourceSubscriptions`；
- Server 保持该 Request 的 response stream；
- notification `_meta` 必须携带 `io.modelcontextprotocol/subscriptionId`；
- 订阅只绑定该 listen request，不绑定底层连接；
- stream 断开后 Client 重建订阅；
- Server 终止订阅时发送只针对 listen request 的 cancelled notification；
- 不恢复 SSE event id；
- backpressure、队列上限、慢消费者断开由 transport adapter 管理。

legacy：

- GET SSE 和旧 resource subscribe 只存在于 legacy adapter；
- 统一映射为内部 `SubscriptionSpec`；
- 业务 listener 不感知协议时代。

### 11.2 Progress

官方页面：[Progress](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/progress)

- Client 通过 `_meta.progressToken` opt in；
- token 为 string/integer；
- active requests 内唯一；
- Server MAY 发送 `notifications/progress`；
- progress 必须单调递增；
- total 可缺失；
- progress/total 可为浮点；
- message 为用户可读；
- 请求结束后停止；
- Server 做频率限制和 coalescing；
- 未提供 token 时禁止发送 progress。

业务 Handler 只使用 `context.progress().report(...)`。

### 11.3 Cancellation

组件统一提供 `CancellationToken`：

- HTTP disconnect；
- STDIO cancelled notification；
- Gateway deadline；
- Spring request cancellation；
- 业务主动取消；

均映射到同一 token。阻塞业务调用需要显式检查 token，异步调用需传播 cancellation signal。

---

## 12. MRTR：Multi Round-Trip Requests

官方页面：[Multi Round-Trip Requests](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/mrtr)

### 12.1 适用方法

只有以下请求允许 `InputRequiredResult`：

- `tools/call`
- `resources/read`
- `prompts/get`

其他方法返回 `input_required` 视为协议错误。

### 12.2 Server Adapter

业务通过：

```java
return McpToolOutcome.inputRequired(
    List.of(McpInputRequest.elicit(...)),
    requestStateCodec.protect(state)
);
```

Adapter 输出：

- `resultType=input_required`
- `inputRequests`
- `requestState`

Server 必须：

- 只请求 Client 声明支持的 capability；
- input request key 在当前 request 内唯一；
- 至少返回 inputRequests 或 requestState 之一；
- 不假设 Client 一定 retry；
- retry 是新的独立 JSON-RPC request；
- 缺失必要 input response 时再次 input_required，而不是依赖内存状态。

### 12.3 Request State 安全

`requestState` 是 attacker-controlled：

- Client 不得解析或修改；
- Server 必须验证完整性；
- 默认使用中台加密能力实现 AEAD；
- payload 包含 principal fingerprint；
- tenant；
- 原 method；
- salient params digest；
- issuedAt/expiresAt；
- nonce/version；
- 跨 principal、跨 tenant、跨 method、过期全部拒绝；
- 一次性语义必须使用 Server side consume store；
- 不把 access token、数据库凭据或敏感业务全文放入 state。

### 12.4 Client Adapter

- 识别 `input_required`；
- 逐项路由到 Elicitation/Sampling/Roots capability handler；
- 原样保存 requestState；
- 收集 inputResponses；
- 以新 Request ID重试原 method；
- 不把 requestState 用到其他并发请求；
- 限制最大 MRTR 轮次，防循环；
- 用户拒绝/取消映射为明确结果。

### 12.5 Deprecated Client Features

Roots、Sampling、Logging 在 `2026-07-28` 已 deprecated：

- 新业务默认不启用；
- legacy/互操作场景由可选 adapter 支持；
- modern 下若使用 Roots/Sampling，必须走 MRTR；
- 未来移除不影响 Tool/Resource/Prompt API；
- Logging 使用 OTel 替代，STDIO 日志写 stderr。

官方页面：[Deprecated Features](https://modelcontextprotocol.io/specification/2026-07-28/deprecated)

---

## 13. JSON Schema 封装与安全

官方页面：

- [Base Protocol JSON Schema Usage](https://modelcontextprotocol.io/specification/2026-07-28/basic)
- [Schema Reference](https://modelcontextprotocol.io/specification/2026-07-28/schema)
- [Tools](https://modelcontextprotocol.io/specification/2026-07-28/server/tools)

### 13.1 Dialect

- 缺少 `$schema` 时默认 JSON Schema 2020-12；
- Client/Server 必须支持 2020-12；
- 可显式声明其他 dialect；
- 不支持的 dialect 返回清晰错误；
- Schema 本身先对 meta-schema 校验；
- Tool input/output 再做实例校验。

### 13.2 `$ref`

- 默认禁止网络 dereference；
- unresolved external `$ref` 拒绝，不能按 permissive 处理；
- 可选远程模式默认关闭；
- 开启后要求 host allowlist；
- 拒绝 loopback、link-local、private network；
- 每次 DNS 解析后再次校验；
- 禁止跨 scheme redirect；
- 设置连接/读取 timeout、最大响应字节、最大 redirect；
- 记录 URI，不记录其中 secret；
- 复用 OAuth DCR 的 SSRF SPI，补齐 DNS rebinding/redirect 防护。

### 13.3 复杂度限制

默认配置：

- max schema bytes；
- max depth；
- max properties；
- max `$defs`；
- max total subschemas；
- max oneOf/anyOf/allOf branches；
- max validation time；
- max regex length；
- 可中断验证；
- 超限返回 Schema rejected，不进入业务。

防止组合关键字造成 CPU/内存 DoS。

### 13.4 DTO Schema 生成

- 仅从稳定注解和 DTO 类型生成；
- Bean Validation 映射 minimum/maximum/pattern/size；
- nullable 与 required 分离；
- enum 保持 wire value；
- description/title 不从字段名猜测；
- generic/recursive type 有显式深度限制；
- 生成结果在启动期校验；
- Tool Registry 启动失败优于运行期发布非法 Schema。

### 13.5 当前 Adapter 实现（P1 首版）

`mcp-schema` 已提供稳定中台边界：

- `McpJsonSchemaValidators.secureDefaults()`：创建安全默认验证器；
- `McpJsonSchemaValidator.compile(...)`：启动/注册阶段编译 Schema；
- `McpCompiledSchema.validate(...)`：运行期验证实例；
- `McpSchemaValidationResult` / `McpSchemaViolation`：稳定、可排序的错误模型；
- `McpSchemaDefinitionException`：非法 Schema 在 Tool 发布前失败。

内部当前使用 `com.networknt:json-schema-validator:3.0.2`，仅作为可替换实现细节。
该依赖、Jackson 类型及原生校验异常均不进入 `mcp-api`、`mcp-server-core` 的公开签名。

安全默认值：

- 固定 Draft 2020-12，缺少 `$schema` 时也按该 dialect 解释；
- format assertions 开启，不启用宽松类型转换；
- Schema 先经过 Draft 2020-12 meta-schema 校验，再编译并缓存；
- 关闭远程资源获取；
- `$ref`、`$dynamicRef`、`$recursiveRef` 只允许 `#` 开头的同文档引用；
- Java Schema 对象图禁止环、禁止非字符串对象键；
- 默认最大深度 64、最大节点数 10000；
- 第三方错误被归一化并确定性排序。

本首版尚未开放其他 dialect、远程 `$ref` allowlist、验证时间预算、正则长度限制等
扩展配置；这些继续保持“默认拒绝或不可配置”，在后续安全增强中通过中台 API 增加，
不会把第三方实现暴露给业务。

---

## 14. OAuth 2.1 与 MCP Authorization Adapter

官方页面：

- [Authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization)
- [Authorization Server Discovery](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/authorization-server-discovery)
- [Client Registration](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/client-registration)
- [Authorization Security Considerations](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/security-considerations)
- [OAuth Client Credentials Extension](https://modelcontextprotocol.io/extensions/auth/oauth-client-credentials)
- [Enterprise-Managed Authorization Extension](https://modelcontextprotocol.io/extensions/auth/enterprise-managed-authorization)

### 14.1 边界

`mcp-security-oauth` 不重新实现 OAuth Server，而是：

- 将 MCP Resource/Tool/Transport 语义适配到中台 OAuth；
- 调用中台 HTTP 做 metadata discovery；
- 调用中台 OAuth 完成 PKCE、token、refresh、introspection；
- 调用中台 Cache 保存 discovery/registration/token；
- 提供 MCP 标准 401/403 challenge；
- 做 issuer/resource/audience/scope binding。

### 14.2 角色

- protected MCP Server：OAuth Resource Server；
- MCP Client：OAuth Client；
- Authorization Server：独立或同部署，由中台 OAuth/企业 IdP提供。

### 14.3 Protected Resource Metadata

Server 必须发布 RFC 9728 metadata：

- canonical `resource`；
- `authorization_servers` 至少一个；
- `scopes_supported`；
- 其他 RFC 9728 字段。

Endpoint 按 RFC 9728 的 well-known 规则生成，不由业务硬编码。

401 challenge 包含：

```http
WWW-Authenticate: Bearer
  resource_metadata="https://.../.well-known/oauth-protected-resource",
  scope="minimum required scopes"
```

### 14.4 Authorization Server Discovery

Client：

1. 从 401 的 `resource_metadata` 或 canonical location 获取 PRM；
2. 验证 metadata URI 与 resource 关系；
3. 读取 `authorization_servers`；
4. 按策略选择 AS；
5. 支持 RFC 8414 metadata；
6. 支持 OIDC Discovery；
7. 验证 issuer exact match；
8. discovery 文档缓存有 TTL；
9. redirect、DNS、scheme 经过 SSRF policy；
10. 多 AS 的注册和 token 状态完全隔离。

### 14.5 Client Registration 顺序

按规范：

1. 已预注册 Client；
2. Client ID Metadata Document（CIMD）；
3. Dynamic Client Registration fallback；
4. 无可用方式时交由平台交互获取配置。

DCR 已 deprecated，只保留兼容。

CIMD：

- `client_id` 为 HTTPS URL且有 path；
- document 的 client_id 必须和 URL exact match；
- 至少包含 client_id、client_name、redirect_uris；
- document 获取使用 SSRF policy；
- 凭据按 issuer 绑定；
- Authorization Server 变化时重新注册。

### 14.6 Authorization Code + PKCE

用户委托 Profile：

- Authorization Code；
- PKCE S256；
- state；
- redirect URI exact match；
- 记录授权前 metadata issuer；
- 回调校验 state；
- 按 RFC 9207 校验 `iss`；
- metadata 声明支持 iss 但响应缺失时拒绝；
- 响应含 iss 时无论声明与否都 exact compare；
- iss mismatch 时不得向任何 token endpoint 发送 code。

### 14.7 Resource Indicator

Client 在 authorization request 和 token request 都必须发送：

```text
resource=<canonical MCP server URI>
```

canonical resource：

- absolute URI；
- scheme/host 规范化；
- 无 fragment；
- 可包含 path 以区分同 host 多 MCP Server；
- 组件内部统一 canonicalizer；
- Gateway、PRM、Token audience 使用同一结果。

Server：

- token 必须为自身 resource 签发；
- 验证 audience；
- 不接受为其他 MCP Server 签发的 token；
- 禁止 token passthrough。

### 14.8 Bearer Token

- 每个 HTTP request 都携带 `Authorization: Bearer`；
- 禁止 token query parameter；
- invalid/expired token 返回 401；
- scope/permission 不足返回 403；
- Token、refresh token 不写日志、trace、metric tag；
- refresh token 加密存储；
- access token cache 不直接使用 serverName 作为唯一 key。

Token cache key：

```text
issuer
+ clientRegistrationId/clientId
+ canonicalResource
+ subject/principal
+ normalizedGrantedScopes
+ tenant/securityRealm
```

key 中使用不可逆 fingerprint，不保存明文 token。

### 14.9 Scope

Server：

- `@McpTool(requiredScopes=...)` 声明静态 scope；
- `McpScopeResolver` 根据 arguments/context 增加动态 scope；
- 一次 challenge 返回当前操作需要的完整最小 scope 集；
- `tools/list` 可按当前授权过滤，因此通常 `cacheScope=private`；
- authorization 不是仅靠 list filter，tools/call 再次强制校验。

Client：

- 初次 401 优先使用 challenge 的 scope；
- 无 challenge scope 时使用 PRM 的 minimal `scopes_supported`；
- runtime 403 `insufficient_scope` 触发 step-up；
- step-up scopes = 之前 scopes ∪ 当前 required scopes；
- 限制最大 step-up 次数；
- 用户拒绝后不循环弹窗；
- 新 token 替换相同授权上下文缓存。

### 14.10 Refresh Token

- confidential storage；
- Client metadata SHOULD 声明 refresh_token grant；
- AS 支持时可请求 offline_access；
- 不假设一定返回 refresh token；
- PRM 和 WWW-Authenticate 不把 offline_access 当资源所需 scope；
- refresh rotation；
- replay 检测；
- refresh 失败回到完整 authorization flow。

### 14.11 M2M Client Credentials

M2M 使用官方 OAuth Client Credentials Extension，而不是伪装成 Core 用户委托流：

- Extension capability/config 显式启用；
- 只用于 machine identity；
- 不产生虚假 user subject；
- resource/audience/scope 仍强制绑定；
- secret 由 Secret Provider 管理；
- workload identity/private_key_jwt 优先于长期明文 secret；
- Token cache 同样按 issuer/resource/scopes 隔离。

### 14.12 Enterprise Managed Authorization

预留官方 Enterprise-Managed Authorization Extension Adapter：

- 企业 IdP 策略和 MCP OAuth 流分离；
- 控制面配置 trust domain；
- 不由业务 Tool 感知 IdP；
- 未协商 Extension 时回退 Core OAuth 行为。

### 14.13 与现有中台 OAuth 的复用与缺口

已确认可复用：

- `TokenEndpoint` token 签发/刷新/校验基础；
- `verifyAccessToken(token, expectedAudience)`；
- `AuthorizationCodeGrant`；
- `PKCESupport`；
- `AuthorizationServerMetadata`；
- `ClientIdMetadataDocument`；
- `SSRFProtection`；
- OAuth AutoConfiguration；
- GlobalCache/TokenStore。

需要在 OAuth 中台增强，不在 MCP 业务仓自实现：

1. token 签发 API 增加 `resource`；
2. token 签发 API 接收 requested scopes，而非总取 Client 全量 scopes；
3. token model 增加 subject/issuer/audience/expiresAt；
4. authorization request/token request 的 resource 全链路；
5. RFC 9207 authorization response iss；
6. RFC 9728 Protected Resource Metadata 通用模型与 endpoint；
7. RFC 8414/OIDC discovery Client；
8. refresh rotation/reuse detection；
9. 凭据按 issuer/resource/subject/scopes 持久化；
10. M2M Extension Profile；
11. redirect/DNS rebinding 更严格验证；
12. Secret Provider SPI。

---

## 15. Extensions、Deprecated 与未知字段

官方页面：

- [Extensions Overview](https://modelcontextprotocol.io/extensions/overview)
- [Deprecated Features](https://modelcontextprotocol.io/specification/2026-07-28/deprecated)

### 15.1 Capability Extensions

- Client/Server capabilities 包含 `extensions` map；
- Extension identifier 必须带合法 namespace；
- 未协商 Extension 不得发送其行为/字段；
- 支持方遇到对端不支持时按 Extension 定义 fallback，否则拒绝；
- Extension payload 保存 raw JSON，映射到独立 Extension Adapter；
- Core API 不为某个 Extension 增加破坏性字段。

### 15.2 Tasks

Tasks 已移出 Core：

- 第一阶段不实现；
- 后续新增 `mcp-extension-tasks`；
- 通过 capability extension 协商；
- 不污染 Tool API；
- Gateway 不把 legacy experimental tasks 与新版 Tasks 混用。

### 15.3 Deprecated Registry

组件维护 feature lifecycle 表：

- Roots：Deprecated；
- Sampling：Deprecated；
- Logging：Deprecated；
- DCR：Deprecated；
- HTTP+SSE：Deprecated；
- includeContext legacy values：Deprecated。

策略：

- 新部署默认关闭 deprecated；
- legacy compatibility 可显式开启；
- 启动日志输出使用中的 deprecated feature；
- metric 统计使用量；
- removal 前至少一个组件 minor version 给出告警；
- 删除只发生在中台 major version。

### 15.4 Forward Compatibility

- Wire decoder 对未知可选字段宽容并保留；
- 必填字段缺失严格拒绝；
- 未知 resultType 严格拒绝；
- 未知 content type 不交给业务，返回 unsupported；
- 未知 capability 保存 raw，但不自动启用；
- 未知 error code 保留 code/data；
- 不以 enum exhaustive switch 造成新版本崩溃。

---

## 16. Observability、稳定性和多租户

### 16.1 OpenTelemetry

官方页面：

- [Base Protocol `_meta`](https://modelcontextprotocol.io/specification/2026-07-28/basic)
- [SEP-414 Trace Context](https://modelcontextprotocol.io/seps/414-request-meta)

组件复用 `atlas-richie-tracing`：

- HTTP 使用标准 trace headers，并在 MCP `_meta` 同步 traceparent/tracestate/baggage；
- 收到两处 trace context 时执行一致性检查；
- 不信任外部 baggage；
- baggage key/value allowlist 和大小限制；
- span attributes 不记录 arguments 全文、token、resource content；
- tool name/server id 可作为低基数属性；
- trace context 不用于鉴权。

建议 span：

- `mcp.client.request`
- `mcp.server.request`
- `mcp.tool.invoke`
- `mcp.oauth.discover`
- `mcp.oauth.authorize`
- `mcp.subscription.listen`

### 16.2 Metrics

- request count/duration/error；
- protocol version/era；
- transport；
- method；
- tool call success/business error/protocol error；
- active request/SSE/subscription；
- era fallback；
- cache hit/miss/stale；
- OAuth discovery/token refresh/step-up；
- cancellation/timeout；
- Schema rejection；
- MRTR rounds；
- deprecated feature use。

禁止把 tenantId、userId、token、URI全文作为 metric tag。

### 16.3 Logging/Audit

- structured log；
- correlation/request fingerprint；
- method/tool/server；
- protocol era；
- duration/outcome；
- arguments 按字段脱敏；
- OAuth token 永不记录；
- Tool 调用审计包含 principal、tenant、tool、policy decision、result class；
- destructive Tool 单独审计；
- STDIO stdout 零日志。

### 16.4 Timeout、Retry、Circuit Breaker

复用中台 concurrency/web：

- connection timeout；
- response/header timeout；
- per-request deadline；
- hard maximum；
- subscription idle timeout；
- OAuth metadata/token timeout；
- schema validation budget。

Retry：

- discover/list/read 可按策略重试；
- tools/call 默认不自动重试；
- 只有声明 idempotent 且请求未得到服务端处理证据时才允许重试；
- stream 断开重试使用新 Request ID；
- backoff+jitter；
- 401 refresh/step-up 不计入通用网络 retry。

Circuit Breaker 维度：

- server + transport + canonical endpoint；
- OAuth AS 单独 breaker；
- 不同 MCP Server 不共享 breaker。

### 16.5 Tenant

复用 `atlas-richie-tenant-parent`：

- tenant 从受信任认证上下文解析；
- 不直接信任 Tool argument 中的 tenantId；
- private cache key 包含 tenant security context；
- Tool handler context 提供 tenant；
- OAuth resource/scope 可按 tenant policy 收敛；
- listTools authorization filter 按 tenant；
- MRTR state 绑定 tenant；
- audit 强制 tenant。

---

## 17. Spring Boot 自动配置

### 17.1 Server Starter

自动发现：

- `@McpTool`
- `@McpResource`
- `@McpPrompt`
- provider SPI Beans
- `McpScopeResolver`
- `McpAuthorizationPolicy`

自动创建：

- Tool/Resource/Prompt Registry；
- Schema Generator/Validator；
- ProtocolNegotiator；
- modern/legacy Dialect；
- Streamable HTTP endpoint；
- Origin validator；
- OAuth Resource Server filter；
- PRM endpoint；
- metrics/tracing/audit interceptors；
- cancellation/progress/subscription runtime。

### 17.2 Client Starter

自动创建：

- `McpOperations`
- Server connection factory；
- era/version negotiator；
- Streamable HTTP/STDIO transport；
- OAuth discovery/registration/token manager；
- list/resource cache；
- scope step-up coordinator；
- MRTR coordinator；
- retry/circuit breaker；
- tracing/metrics。

### 17.3 建议配置

```yaml
platform:
  component:
    mcp:
      enabled: true

      protocol:
        preferred-version: 2026-07-28
        compatibility-mode: dual
        era-probe-timeout: 2s

      server:
        enabled: true
        name: inventory-mcp
        version: ${spring.application.version:unknown}
        endpoint: /mcp
        instructions: classpath:/mcp/instructions.md
        legacy-enabled: true
        tools:
          default-cache-ttl: 5m
          cache-scope: private
        origin:
          allowed-origins:
            - https://foundry.example.com

      client:
        enabled: false
        request-timeout: 30s
        hard-timeout: 2m
        max-pages: 100
        max-mrtr-rounds: 5

      oauth:
        enabled: true
        profile: delegated
        resource-uri: https://inventory.example.com/mcp
        required: true

      schema:
        default-dialect: https://json-schema.org/draft/2020-12/schema
        remote-ref-enabled: false
        max-bytes: 1MB
        max-depth: 64
        validation-timeout: 200ms

      observability:
        metrics-enabled: true
        tracing-enabled: true
        audit-enabled: true
```

业务通常只配置 server name、endpoint 与 OAuth resource；协议细节保留默认。

---

## 18. 测试与验收

### 18.1 测试层次

1. Schema fixture tests；
2. Dialect codec golden tests；
3. Header/body validation tests；
4. Tool/Resource/Prompt contract tests；
5. modern/legacy negotiation matrix；
6. Streamable HTTP transport tests；
7. STDIO newline framing tests；
8. OAuth discovery/PKCE/resource/audience/scope tests；
9. MRTR/security tests；
10. cache/pagination/subscription tests；
11. official conformance；
12. Foundry Gateway + Mock Server E2E。

### 18.2 双时代矩阵

| Client | Server | 预期 |
|---|---|---|
| modern | modern | `2026-07-28` |
| dual | modern | modern |
| dual | dual | modern |
| dual | legacy | probe 后 `2025-11-25` |
| legacy | dual | initialize legacy |
| modern-only | legacy | 明确不兼容错误 |
| legacy | modern-only | 错误中提示 supported modern versions |

### 18.3 OAuth 必测

- PRM canonical path；
- 多 authorization_servers；
- RFC 8414 与 OIDC discovery；
- CIMD/pre-registration/DCR priority；
- PKCE S256；
- state mismatch；
- iss present/missing/mismatch；
- resource authorization/token request；
- audience mismatch；
- token passthrough rejection；
- 401 invalid token；
- 403 insufficient scope；
- scope union step-up；
- refresh rotation/replay；
- issuer credential isolation；
- SSRF/DNS rebinding/redirect；
- M2M Extension。

### 18.4 Conformance

使用官方：

- [modelcontextprotocol/conformance](https://github.com/modelcontextprotocol/conformance)

CI：

- Server mode 测 server starter sample；
- Client mode 测 client starter executable；
- modern active suite 必须全通过；
- legacy suite 建立明确 baseline；
- expected failures 必须写原因和 issue；
- stale expected failure 视为失败；
- 协议 Schema 或 Dialect 更新必须跑全量。

### 18.5 兼容性门禁

- API binary compatibility；
- official SDK 不泄露检查；
- wire golden diff；
- generated Schema diff；
- protocol page traceability 检查；
- dependency convergence；
- no deprecated transport enabled by default。

---

## 19. 从 Foundry Mock Server 迁移

### 19.1 第一步：冻结行为

为现有 mock server 建立：

- 13 个 Tool descriptor golden；
- tools/list response golden；
- tools/call request/response fixtures；
- resources/prompts fixtures；
- OAuth challenge fixtures；
- E2E。

### 19.2 第二步：组件承接协议

迁移到中台：

- JSON-RPC；
- Endpoint/HTTP status；
- protocol negotiation；
- Tool Registry/Schema；
- Resource/Prompt registry；
- OAuth filter/metadata；
- notification/subscription；
- tracing/metrics。

Mock server 保留：

- mock Tool 方法；
- mock 数据；
- server name/config；
- 业务 DTO。

### 19.3 第三步：Dual-era

- modern 默认；
- legacy 初始化兼容；
- Gateway modern client；
- 第三方 legacy server fallback；
- 通过 conformance。

### 19.4 第四步：正式业务 MCP Server

以组件化 mock server 为骨架：

```text
MCP Tool Adapter
    -> Application Service
        -> 中台 DAO / Tenant / Audit
            -> 业务数据库
```

Python 查询逻辑迁移要求：

- 先建立输入/输出/异常 golden；
- 禁止逐行翻译后直接放进 Tool；
- SQL/DAO 在 infrastructure；
- tenant/permission 在受信任 context；
- read-only Tool 先行；
- 与 Python 旧实现做 shadow compare；
- 结果一致后切流；
- 不开放任意 SQL Tool。

---

## 20. 协议升级流程

未来发布 `20xx-xx-xx`：

1. 读取官方 changelog、deprecated registry、schema；
2. 固化官方 schema commit/hash；
3. 新增 Dialect，不修改旧 Dialect；
4. 映射到 Normalized Model；
5. 更新 compatibility matrix；
6. 更新 Client preferred version；
7. Server `supportedVersions` 增加新版；
8. 运行各时代 conformance；
9. 发布组件；
10. 业务服务只升级依赖版本。

只有当协议新增无法用现有稳定语义表达的业务能力时，才扩展 `mcp-api`；新增 wire 字段不构成扩展 Public API 的理由。

---

## 21. 官方规范追踪矩阵

| 规范领域 | 官方页面 | 中台处理位置 |
|---|---|---|
| 总体变更 | [Key Changes](https://modelcontextprotocol.io/specification/2026-07-28/changelog) | upgrade policy |
| Base/JSON-RPC | [Base Overview](https://modelcontextprotocol.io/specification/2026-07-28/basic) | `mcp-protocol` |
| Schema source | [Schema Reference](https://modelcontextprotocol.io/specification/2026-07-28/schema) | schema snapshot/codegen |
| Version/dual-era | [Versioning](https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning) | negotiator/dialects |
| Server discover | [Discovery](https://modelcontextprotocol.io/specification/2026-07-28/server/discover) | discovery adapter |
| Message patterns | [Patterns Overview](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns) | protocol runtime |
| MRTR | [MRTR](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/mrtr) | MRTR coordinator |
| Subscriptions | [Subscriptions](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/subscriptions) | subscription runtime |
| Cancellation | [Cancellation](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/cancellation) | cancellation bridge |
| Progress | [Progress](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/progress) | progress reporter |
| Transport | [Transport Overview](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports) | transport SPI |
| Streamable HTTP | [Streamable HTTP](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http) | HTTP adapter |
| STDIO | [STDIO](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/stdio) | STDIO adapter |
| Authorization | [Authorization](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization) | OAuth adapter |
| AS discovery | [Authorization Server Discovery](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/authorization-server-discovery) | metadata resolver |
| Client registration | [Client Registration](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/client-registration) | registration manager |
| Auth security | [Security Considerations](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization/security-considerations) | OAuth/security policy |
| M2M | [OAuth Client Credentials](https://modelcontextprotocol.io/extensions/auth/oauth-client-credentials) | optional auth extension |
| Enterprise auth | [Enterprise-Managed Authorization](https://modelcontextprotocol.io/extensions/auth/enterprise-managed-authorization) | optional auth extension |
| Tools | [Tools](https://modelcontextprotocol.io/specification/2026-07-28/server/tools) | tool registry/dispatcher |
| Resources | [Resources](https://modelcontextprotocol.io/specification/2026-07-28/server/resources) | resource registry |
| Prompts | [Prompts](https://modelcontextprotocol.io/specification/2026-07-28/server/prompts) | prompt registry |
| Caching | [Caching](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/caching) | client/server cache |
| Completion | [Completion](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/completion) | completion provider |
| Pagination | [Pagination](https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/pagination) | cursor/page adapter |
| Deprecated | [Deprecated Features](https://modelcontextprotocol.io/specification/2026-07-28/deprecated) | legacy isolation |
| Extensions | [Extensions](https://modelcontextprotocol.io/extensions/overview) | extension SPI |
| Tasks | [Tasks Extension](https://modelcontextprotocol.io/extensions/tasks/overview) | future module |
| OTel meta | [SEP-414](https://modelcontextprotocol.io/seps/414-request-meta) | tracing adapter |
| Schema 2020-12 | [SEP-1613](https://modelcontextprotocol.io/seps/1613-establish-json-schema-2020-12-as-default-dialect-f) | schema validator |
| HTTP headers | [SEP-2243](https://modelcontextprotocol.io/seps/2243-http-standardization) | HTTP header codec |
| Cache TTL | [SEP-2549](https://modelcontextprotocol.io/seps/2549-TTL-for-list-results) | cache policy |
| Sessionless | [SEP-2567](https://modelcontextprotocol.io/seps/2567-sessionless-mcp) | explicit handles |
| Stateless | [SEP-2575](https://modelcontextprotocol.io/seps/2575-stateless-mcp) | modern dialect |
| Feature lifecycle | [SEP-2596](https://modelcontextprotocol.io/seps/2596-spec-feature-lifecycle-and-deprecation) | deprecation policy |
| Conformance | [Conformance Framework](https://github.com/modelcontextprotocol/conformance) | `mcp-testkit` |

---

## 22. 实施顺序

### P0：协议与 API

- [x] 创建 Maven 多模块并接入 component reactor/BOM；
- [x] stable API；
- [x] normalized model；
- [x] 官方 2026-07-28 schema snapshot（固定 commit + SHA-256）；
- [x] modern/legacy dialect 基础骨架；
- [x] JSON-RPC/error/meta/version 基础校验；
- [x] discover request/result codec；
- [x] dual-era transport probe 状态机。

P0 当前实现验证：API 2 个、Protocol 31 个，共 33 个单元测试通过。协议实现保持纯 Java，
不依赖 Spring、JSON 库或官方 SDK；JSON codec 将由 transport 边界根据 Schema 快照接入。

2026-07-28 实现已按官方 Schema 校准：

- modern `_meta` 使用 `io.modelcontextprotocol/*` 全限定键；
- `protocolVersion`、`clientCapabilities` 必填，`clientInfo` 可选；
- Header/body 版本不一致映射 `HeaderMismatch(-32020)`；
- 不支持的版本映射 `UnsupportedProtocolVersion(-32022)`，data 使用 `supported/requested`；
- 核心结果类型为 `complete/input_required`；
- `DiscoverResult.ttlMs` 按生成 JSON Schema 校验为非负整数。

### P1：Server + HTTP

- [x] JSON Schema 2020-12 Validator Adapter（稳定中台 API、meta-schema、离线引用与复杂度限制）；
- [x] Tool Registry 首轮实现（名称/schema root/重复校验、Schema 预编译、确定性排序、请求级授权过滤）；
- [x] Tool Dispatcher 首轮实现（输入/输出校验、取消、Tool Error/协议错误分流、异常脱敏）；
- [x] Spring MVC JSON Endpoint Starter 首版；
- [x] Streamable HTTP SSE/Reactive 框架绑定（MVC/WebFlux request-scoped 首版）；
- [x] Header validation（版本/Method/Name 与 `Mcp-Param-*`）；
- [x] Resources/Prompts/Completion Server registry 与 Endpoint；
- [x] HMAC opaque cursor、客户端分页上限与 cache hints；
- [x] Origin/security baseline（Allow-list、Bearer/PRM adapter）；

当前实现已通过 MCP 聚合 Maven reactor 的全量单元测试；测试覆盖 Schema、Dialect、
Tool/Resource/Prompt、HTTP Header/分页、OAuth、STDIO、Starter 自动配置与客户端缓存。
Dispatcher 的错误语义遵循 Tools 规范：

- 输入不符合 `inputSchema` 时，不进入业务 Handler，返回 `isError=true` 的可修复 Tool Error；
- 业务可预期失败通过 `McpToolExecutionException` 返回 Tool Error；
- 未知 Tool、协议异常、非法成功输出及非预期服务异常走 JSON-RPC error；
- 非预期异常的 wire message 固定脱敏，原始 cause 仅保留在进程内；
- 声明 `outputSchema` 的成功结果必须验证 `structuredContent`；
- 取消在 Handler 前检查，并在异步完成后再次检查。

### P2：Client + OAuth

- [x] `McpOperations` HTTP Starter 首版（Tool/Resource/Prompt 操作）；
- [x] `server/discover` 首次协议协商与本地 TTL 缓存（仅覆盖当前支持的 wire versions）；
- [x] OAuth protected-resource/authorization-server discovery、Bearer challenge、token provider SPI 与 URI policy；
- [x] list/discovery 进程内 TTL cache；
- [x] OAuth registration/PKCE/token refresh/introspection、client credentials 与 token manager；
- [x] 进程内 private cache、按服务器失效与 OAuth principal 安全降级；分布式 Cache/TokenStore 由中台注入；
- Gateway migration。

### P3：高级模式

- [x] subscriptions（modern response stream，MVC/WebFlux）；
- [x] progress/cancellation；
- [x] MRTR；
- [x] STDIO newline framing 与子进程生命周期首版；
- [x] legacy dialect 与可选 STDIO Content-Length framing；legacy HTTP session 仍隔离为部署侧 adapter。

### P4：生产验收

- conformance；
- mock migration；
- official business MCP Server；
- observability/audit；
- deprecated usage telemetry。

---

## 23. 当前需要拍板的实现决策

以下默认建议已写入设计，编码前仍应形成 ADR：

1. Public API 使用 Java，保证 Java/Kotlin 双端消费。
2. modern protocol 优先，Server/Client 默认 dual-era。
3. 官方 Java SDK 2.0.0 仅作为 `2025-11-25` legacy adapter；在其支持新版前，modern wire adapter 根据官方 Schema 实现。
4. Streamable HTTP 为默认远程 Transport。
5. STDIO 使用 newline framing；历史 Content-Length 仅作为 custom legacy。
6. OAuth delegated 为远程用户场景默认；M2M 使用官方 Client Credentials Extension。
7. Protocol Schema 固化，不在构建时联网。
8. Remote `$ref` 默认关闭。
9. Tasks 不进入第一阶段 Core。
10. 不在设计阶段修改现有 OAuth/Web 用户变更；后续按独立任务增强。
