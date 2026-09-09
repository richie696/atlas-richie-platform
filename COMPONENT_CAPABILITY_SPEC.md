# Atlas Richie Component 跨语言能力规格（v0.2）

> 状态：**源代码、组件 README 与设计文档交叉校验的能力规格**。本文不是 Java API 翻译，也不是 Go/Rust/Python 的实现方案；它定义的是未来各语言实现必须保持的能力边界、行为承诺与验证方向。
>
> 覆盖范围：`atlas-richie-component/pom.xml` 当前聚合的 30 个顶层组件。Provider、Spring Boot Starter、BOM 与测试包被归入其所属能力，不单独伪装为跨语言产品。

## 1. 目标与非目标

目标是让 Java、Go、Rust、Python 可以按各自惯用方式实现同一套经过生产验证的技术能力：调用方选择语言与运行时，不必放弃重连、幂等、限流、审计、隔离、可观测、兼容升级等工程治理。

本文中的“必须”描述跨语言语义，“可选”描述产品按需裁剪的能力。每一种语言的实现都可以有不同的包、类型、依赖注入和并发模型。

不追求以下事情：

- 不复制 Java 类名、静态门面、注解、Spring 生命周期、Reactive 类型或 Maven 模块形态。
- 不要求每种语言支持同一批第三方 Provider；只要求已声明支持的 Provider 如实声明能力并通过同一份契约测试。
- 不把业务领域模型、Controller、用户表、权限事实、业务调度迁入组件库。
- 不把 `base` 做成跨语言“万能工具箱”。只有无业务语义、无厂商依赖、至少被两个独立能力真实复用的类型或纯函数，才可进入语言的 Core/Base。

## 2. 跨语言规格的统一形态

### 2.1 证据等级与状态标识

README 说明稳定的公开边界与接入方式；设计文档补足状态机、失败策略、默认值、迁移及非目标；源代码和测试则证明当前行为。三者不能混为一谈：设计文档里的路线图、性能目标或待验证项，只有在实现与契约测试确认后，才能升级为跨语言的强制承诺。

因此，后续每项细化规格必须标识以下状态之一：

- **已实现且已验证**：已有实现和测试/真实环境证据，可作为当前兼容契约。
- **已实现待环境验证**：代码或本地契约存在，但云服务、协议互操作、安全或性能尚未在目标环境证明。
- **已设计待实现**：保留为目标能力或 ADR，不得宣称为当前能力。
- **不迁移**：只保留治理原则或兼容约束，不形成跨语言运行时库。

本文件第 8 节列出每个顶层组件的 README/设计文档证据入口；真正落地时仍须按上述等级回查源码与测试。

每个能力族后续都应补齐一份机器可读/可验证的规格，而不是只维护 Markdown：

```text
spec/<capability>/
  api/                 最小同步 API、流式 API、DTO/事件 schema
  configuration/       配置 schema、默认值、兼容与弃用规则
  semantics/           超时、重试、顺序、幂等、一致性、安全边界
  providers/           必选能力与可选能力矩阵
  observability/       日志、指标、Trace、审计事件的稳定字段
  conformance/         跨语言黑盒契约测试
```

所有能力共享以下基础约束：

1. **稳定边界**：公共 API、事件、错误码和配置使用语言无关的数据模型；不得泄漏某语言 SDK 类型。
2. **能力诚实**：Provider 只能声明实际支持的能力；不支持必须是明确的 capability 缺失或受控错误，不能静默降级成空结果。
3. **可观测性默认存在**：关键操作至少可关联 correlation/trace ID、结果、耗时、错误类别和 Provider；敏感内容默认不可记录。
4. **可选能力物理裁剪**：语言产品只依赖被选择的 Provider/Adapter；核心不得反向依赖任何可选实现。
5. **安全默认拒绝**：未启用、身份不完整、租户不明确、来源不可信或配置不合法时，不可无声放宽安全边界。
6. **契约先行**：改变配置默认值、事件字段、错误分类、超时、顺序、重试或幂等语义，都属于兼容性变更，必须以契约测试守护。

## 3. 组件能力总览与迁移定位

| 顶层组件 | 跨语言能力定位 | 初始优先级 | 迁移方式 |
|---|---|---:|---|
| AI | 多模型/多模态统一访问、路由、密钥池与流式会话 | P2 | Core 契约 + Provider Adapter |
| Cache | Redis 数据结构、L2、锁、限流、性能守卫 | P1 | 高价值原生实现 |
| DAO | 关系型持久化治理 | P3 | 仅治理规格，不复刻 ORM |
| Tenant | 租户上下文、隔离策略与可信传递 | P1 | Core 契约 + Web/DB Adapter |
| Component Dependencies | 依赖版本治理 | 不迁移 | 各语言发布/BOM 等价物 |
| HTTP | 统一出站 HTTP、SSE、超时和 TLS 行为 | P1 | Core + 多 Transport Adapter |
| Desensitize | 出口数据脱敏策略 | P1 | 纯 Core + 序列化/日志 Adapter |
| I18n | Locale 解析、消息与字典翻译 | P2 | Core + Catalog/Dictionary Adapter |
| Liquibase | Schema 迁移治理 | P3 | 迁移规范，不移植 Liquibase |
| Messaging | 多 Broker 事件生产/消费治理 | P1 | Core + Broker Adapter |
| MongoDB | 文档库访问与横切治理 | P3 | Mongo 专项 Adapter，非通用 ORM |
| MQTT | 长连接、QoS、弱网恢复和事件流 | P0 | Rust/Go 优先原生实现 |
| MFA | OTP 多因子协议内核 | P2 | Core + 存储/通知 Adapter |
| Secret | 密钥读取、轮换与透明配置刷新 | P1 | Core + Provider Adapter |
| OAuth | OAuth/OIDC 协议内核与资源服务器验证 | P1 | 标准协议 Core + Runtime Adapter |
| MCP | MCP API、协议、传输与鉴权 | P1 | Protocol-first 实现 |
| Microservice | 服务间调用上下文、连接与 TLS 治理 | P2 | 与 HTTP/gRPC 协调，不重复造客户端 |
| Logging | 访问/审计日志采集与投递 | P1 | Core 事件模型 + Sink Adapter |
| Tracing | OpenTelemetry 依赖与语义治理 | P1 | 标准 OTel 约定，不自造追踪协议 |
| Concurrency | 并发编排、限流、重试、熔断、批处理 | P0 | 按语言并发模型重实现语义 |
| Web | 入站请求保护、中间件、热加载与 Hook | P1 | Core SPI + Web Runtime Adapter |
| Storage | 对象/文件存储统一访问与客户端生命周期 | P1 | Core + Provider Adapter |
| Vector | 多 Store 向量数据面与能力发现 | P1 | Core + Provider Adapter |
| StateMachine | 状态转换、历史、一致性与事件 | P2 | Core + 存储/规则 Adapter |
| Redis Stream MQ | Redis Stream 可靠消费语义 | P1 | 专项 Adapter |
| gRPC | gRPC 元数据、安全、错误与可观测拦截 | P1 | Interceptor/Middleware Adapter |
| NATS | Core NATS、JetStream、KV/Object Store | P0 | Rust/Go 优先原生实现 |
| Document Parser | 安全文档解析和背压流 | P1 | Core 契约 + Parser Engine Adapter |
| Document Chunking | 确定性文本切片与流式策略 | P1 | 纯 Core + 可选语义 Adapter |
| OCR | 多 OCR Provider 统一识别 | P2 | Core + Provider/Sidecar Adapter |

P0 表示最适合先用 Go/Rust 验证高性能价值；P1 是跨语言基础能力；P2 是在基础协议稳定后迁移；P3 是保留语义但不应直接复制 Java 技术栈。

## 4. 逐组件能力规格

### 4.1 AI：多模型与多模态访问

- **要解决的问题**：业务被模型厂商、模型类型、鉴权方式、流式协议和临时限流绑定；模型切换需要改业务代码。
- **最小契约**：按名称发现模型；文本生成、Embedding、重排、图像、语音识别/合成、实时语音会话等按能力拆分；结果必须包含内容、模型标识、耗时与用量/错误元数据。
- **必须保持的语义**：模型可由静态配置或运行期注册提供；动态未知 Provider 可走明确的兼容协议路径，但不得默默伪装为原生能力；Key Pool 必须定义轮换、限流识别、耗尽与健康状态；实时语音须以不透明会话和事件流表达音频、转写、结束和失败。
- **扩展与边界**：Provider 通过 Strategy/SPI + Adapter 接入；业务只依赖模型能力接口。模型路由、熔断、健康检查和密钥轮换属于组件；Prompt、业务工具选择、知识权限属于业务层。
- **不迁移的 Java 细节**：Spring AI 类型、Bean 自动注册、Java `Flux`。各语言改用其原生 stream/async 表达，但必须定义背压、取消、错误和资源关闭。

### 4.2 Cache：Redis 缓存与数据结构治理

- **要解决的问题**：业务直接使用底层 Redis 客户端，导致 Key 约定、TTL、序列化、锁、性能和故障处理碎片化。
- **最小契约**：按 KV、对象/Hash、集合、排序集合、Key 元操作、原子脚本、锁、限流、位图、基数、地理、有界队列/栈等能力分组；可靠消息不属于本组件。
- **必须保持的语义**：明确数据编码、Key 命名、TTL、批量上限与原子性；L2 写路径同步更新本地层与 Redis，跨实例刷新依赖底层键空间/失效事件等明确机制，旁路写入只能声明为最终一致；分布式锁的租约、续期、释放所有权和超时；缓存击穿防护；大 Key/高复杂度操作的观测与阻断策略。
- **扩展与边界**：Provider 只隐藏 Redis 客户端差异，不承诺把 Redis Stream 等不可抹平语义抽成普通缓存。跨语言共享数据时禁止 Java 对象序列化，必须选定 JSON/Protobuf 等稳定编码。
- **迁移注意**：Go/Rust 可优先实现连接复用、Pipeline、Lua、分布式锁、L2/本地缓存和性能守卫；静态门面不是契约本身。

### 4.3 DAO：关系型数据访问治理

- **要解决的问题**：常见 CRUD 的分页、排序白名单、ID、审计字段、乐观锁、批量保护、租户隔离和慢 SQL 缺乏一致约束。
- **跨语言规格**：定义持久化治理规则——分页/排序安全、批量写上限、乐观并发、审计字段、租户谓词、全表写保护、慢操作观测与错误分类。
- **边界**：不定义跨语言 ORM 或通用 Repository；不同语言直接使用其最薄、最符合生态的数据库工具。SQL/事务仍是业务持久化事实，不能为“中台化”再包一层万能 DAO。

### 4.4 Tenant：多租户上下文与隔离

- **要解决的问题**：租户身份靠业务层手工传递和拼接条件，遗漏即造成越权/串租户。
- **最小契约**：可信租户上下文、租户身份断言、隔离模式声明（COLUMN/TABLE/SCHEMA/DATABASE/HYBRID）、显式平台/超级管理员路径、租户事件与健康状态。
- **必须保持的语义**：总开关默认关闭；开启后缺失租户上下文的受保护操作必须拒绝，而不是回退到默认租户；租户身份在 HTTP/gRPC/消息间的传递须可验证、带 TTL 且防篡改；同一事务内必须冻结租户与数据源/隔离模式，检测到跨租户或跨库切换即失败并回滚。
- **扩展与边界**：Web、Gateway、数据库过滤/路由是 Adapter；隔离模式选择和租户策略属于 Core。禁止把 tenant ID 当作用户可随意提交且无签名校验的普通 Header。

### 4.5 Component Dependencies：依赖治理

- **能力规格**：定义经验证的三方依赖版本集合、兼容矩阵、CVE 升级策略与发布约束。
- **边界**：不是运行时组件，不移植 Maven BOM；Go 使用 module/workspace，Rust 使用 workspace/Cargo lock，Python 使用受控 lock/constraints，但都应拥有同等的版本收敛与安全升级机制。

### 4.6 HTTP：统一出站请求与 SSE

- **要解决的问题**：不同 HTTP 客户端的超时、TLS、Header、序列化、文件上传、错误和 SSE 行为不一致，业务无法切换 Transport。
- **最小契约**：不可变/可验证请求构建、响应状态/头/正文、同步和异步执行、取消、流式 SSE、Multipart、每请求超时覆盖和受控 TLS 校验。
- **必须保持的语义**：默认严格 TLS；关闭证书校验必须可见、受限并记录告警；连接池与资源关闭属于 Adapter；HTTP 错误、网络错误、超时、取消和反序列化错误必须可区分。
- **扩展与边界**：Transport 用 Adapter/SPI 替换；业务 Header 传播、重试和熔断策略通过最小可组合策略实现。HTTP 能力不重复承担服务发现或业务鉴权。

### 4.7 Desensitize：敏感数据出口治理

- **要解决的问题**：API、日志、审计与异常在不同出口重复编写或遗漏脱敏，造成明文泄露。
- **最小契约**：`MaskRule`、场景（API_RESPONSE/LOG/AUDIT/EXCEPTION）、字段/键规则、权限评估和 `MaskingService`；内置手机号、证件、银行卡、邮箱等策略可扩展。
- **必须保持的语义**：按结构化字段和显式键名处理优先；无法可靠识别的自然语言不得默认正则盲扫；脱敏不是加密、不是 KMS、也不替代字段级存储保护。
- **扩展与边界**：序列化器、日志 Layout、Web 输出是 Adapter；规则与权限决策是 Core。任何语言都必须对 Map/JSON 的键规则与嵌套路径支持范围作明确声明。

### 4.8 I18n：消息与字典国际化

- **要解决的问题**：Locale 解析、消息目录、动态字典翻译和缓存分散，接口响应显示不一致。
- **最小契约**：Locale 解析优先级、消息 key + 参数、回退链、静态 Catalog、动态字典翻译与缓存失效。
- **必须保持的语义**：Locale 来源和默认值可配置且可观测；未知 key、未知语言与字典缺失须有明确回退；翻译结果不得污染原始业务值。
- **边界**：模板引擎、Web 框架 MessageSource、缓存客户端均是语言 Adapter；词条治理与翻译工作流不在运行时组件中实现。

### 4.9 Liquibase：数据库 Schema 迁移治理

- **要解决的问题**：各服务的 Schema 版本、变更顺序、数据库方言和启动校验不一致。
- **跨语言规格**：迁移文件组织、版本 ID 唯一性、校验和、执行记录、启动前校验、多数据源上下文、测试环境策略和失败回滚/人工恢复约束。
- **边界**：不迁移 Liquibase API；各语言选用自己的迁移工具，但必须消费同一份变更资产或遵守同一迁移账本，禁止多个工具并发管理同一 schema。

### 4.10 Messaging：多 Broker 事件消息

- **要解决的问题**：业务被 Kafka、RabbitMQ、RocketMQ、云消息服务等 API 绑定，幂等、重试、延迟和 Header 传播各自实现。
- **最小契约**：不可变消息 envelope（ID、类型、时间、payload、headers、trace/tenant context）、生产、消费、确认、延迟投递、重试、死信与幂等存储。
- **必须保持的语义**：投递保证必须显式（至多一次/至少一次/无法保证恰好一次）；消费处理器幂等；重试次数、退避、DLQ 路由、Header 白名单、序列化和顺序域均可配置且可观测。
- **扩展与边界**：Broker Adapter 负责协议差异；领域事件模型由业务/协议模块拥有。不能把所有消息系统硬抹成相同的事务、顺序和延迟语义。

### 4.11 MongoDB：文档存储横切治理

- **要解决的问题**：文档查询、索引、租户、审计、软删除、TTL、慢查询和熔断被业务反复实现。
- **跨语言规格**：文档存储操作语义、查询/更新/删除安全边界、索引声明、审计字段、租户谓词、软删除与 TTL、慢查询与连接失败错误分类。
- **边界**：当前能力是 MongoDB 专项，不应伪装成所有 NoSQL 的统一抽象；各语言可采用各自驱动和查询表达式。反射 Lambda 字段引用、注解和 Spring Data 回调不跨语言迁移。

### 4.12 MQTT：长连接与弱网恢复

- **要解决的问题**：业务直接处理 MQTT 协议、连接状态、弱网重连、会话恢复、心跳、订阅、网络切换和遗嘱消息，容易形成巨型连接管理类。
- **最小契约**：发布/订阅、共享订阅、QoS、保留消息、遗嘱、会话、连接状态、网络质量、心跳和事件流；订阅注册的注销/关闭句柄。
- **必须保持的语义**：连接状态机、重连退避与快速恢复策略、消息过期、去重、订阅恢复、网络切换、背压和资源关闭必须定义；技术错误由组件处理，业务事实以稳定 DTO/事件流发布。
- **扩展与边界**：协议版本/客户端库为 Adapter；消息去重存储与 client ID 规则为 SPI；业务 topic 语义不进入组件。Rust/Go 是优先实现目标。

### 4.13 MFA：多因子认证

- **要解决的问题**：OTP 绑定、校验、重放保护、失败限制与管理职责混在用户服务或网关中。
- **最小契约**：TOTP/HOTP、挑战/验证、设备绑定/解绑、恢复、失败计数、锁定、审计和可选租户作用域。
- **必须保持的语义**：验证面和管理面分离；验证路径不依赖用户表，且在缓存/验证依赖不可用时安全拒绝，不能回退查询数据库；OTP 秘密须受密钥管理保护；验证码消费、窗口、重放标记、频率限制和锁定必须原子化。
- **边界**：短信/邮件是通知 Adapter；用户资料、登录页、身份目录不属于 MFA Core。RFC 6238/4226 算法向量应成为跨语言契约测试。

### 4.14 Secret：密钥与敏感配置

- **要解决的问题**：业务组件各自连接 Vault/云 Secret Manager，凭证刷新、轮换、回滚和 Provider 选择不可控。
- **最小契约**：Secret Bundle/版本、读取、缓存、刷新、轮换、加密/签名等不可导出密钥操作、Provider 健康与敏感绑定目录。
- **必须保持的语义**：未启用时零网络、零后台任务、零配置优先级变化；启用后 Provider 选择属于最终部署；首次加载失败必须 fail-closed，刷新失败只能保留已验证的旧快照；刷新必须两阶段准备和原子切换，旧客户端在在途请求结束后再释放；不可导出 KMS/HSM 密钥不得伪装成明文配置。
- **扩展与边界**：Provider（Vault、云 KMS、KMIP、PKCS#11 等）为 Adapter；业务组件通过声明绑定而非直接依赖 Provider。密钥值、Token、日志字段永不进入普通错误消息或观测数据。

### 4.15 OAuth：授权协议内核

- **要解决的问题**：认证服务、网关和资源服务各自实现 OAuth，导致 Token、PKCE、刷新、撤销、JWKS 与资源校验不一致。
- **最小契约**：OAuth/OIDC 标准端点语义、Client Registry、授权码/Token/刷新存储、签名/JWKS、Scope/Resource/Audience 校验、Introspection、Revocation、Device Flow 与 Resource Server Principal。
- **必须保持的语义**：PKCE 只接受 S256；refresh token 轮换与重放检测；JWT 标准声明不可被扩展覆盖；密钥轮换保留旧验证公钥至最大 token TTL；认证服务是唯一签发者，网关/资源服务只验证和转发可信 Principal。
- **边界**：协议 Core 与 HTTP Runtime、登录页、用户库、同意页、审计库分离；存储、缓存、签名和 HTTP 端点通过 SPI/Adapter 替换。应以 RFC 行为和安全测试，而非 Java 类签名，定义跨语言一致性。

### 4.16 MCP：模型上下文协议能力

- **要解决的问题**：MCP Client、Server、工具 schema、HTTP/stdio 传输与 OAuth 安全耦在单一应用中，无法跨运行时复用。
- **最小契约**：MCP API、协议消息/schema、Server 生命周期、Client 调用、工具/资源/提示词发现、HTTP 与 stdio transport、OAuth 安全和测试工具包。
- **必须保持的语义**：协议版本、请求 ID、错误、取消、流式/通知、schema 校验、传输关闭和鉴权边界必须与 MCP 协议一致；现代无状态协议与遗留会话协议必须由内部 Dialect/Negotiator 隔离，业务 Tool/Client API 禁止判断协议版本；工具实现与协议 transport 分离。
- **边界**：这是 Protocol-first 能力，应优先生成/维护语言无关 schema 与互操作测试；Spring Starter 只是 Java Adapter。

### 4.17 Microservice：服务间调用治理

- **要解决的问题**：服务调用的连接池、TLS、上下文 Header、日志与错误处理不一致。
- **跨语言规格**：可信 Header 白名单与传播、超时预算、重试边界、TLS/证书策略、连接复用、错误映射和调用观测。
- **边界**：HTTP/gRPC 客户端能力应复用而非重复实现；服务发现、负载均衡和熔断可以是 Adapter。禁止把业务语义塞进通用 RPC 客户端。

### 4.18 Logging：访问与审计日志

- **要解决的问题**：访问日志、操作人、请求/响应截断、审计生命周期和落库/消息投递在业务中重复且易泄露敏感信息。
- **最小契约**：不可变 Audit/Access Event、关联 ID、主体、动作、目标、结果、耗时、错误摘要、内容捕获策略和生命周期 Hook。
- **必须保持的语义**：请求/响应正文默认按大小和敏感规则受控；采集与存储解耦；Sink 可以是文件、数据库、缓存或消息；批量写、失败重试和丢弃/阻塞策略须可观测。
- **边界**：日志框架/Layout 是 Adapter；脱敏必须复用 Desensitize 能力；审计事件不是领域事件的替代品。

### 4.19 Tracing：分布式追踪治理

- **要解决的问题**：应用自由组合 OTel SDK、导出器和版本，导致版本冲突与 trace 语义不一致。
- **跨语言规格**：采用 OpenTelemetry/OTLP、W3C trace context、统一 service/resource 属性、采样与导出配置、Span 命名和敏感字段禁止规则。
- **边界**：当前 Java 模块本质为依赖聚合；Go/Rust/Python 不应复制该包，而应各自锁定兼容 OTel 依赖并用同一语义约定和端到端 trace 测试验证。

### 4.20 Concurrency：并发、弹性与批处理

- **要解决的问题**：汇聚、竞速、超时、分批、重试、限流、熔断、防抖和动态执行资源在业务中反复实现且语义不一。
- **最小契约**：结构化任务组（全部成功、竞速、截止时间、尽力完成）、批处理（并发上限、输入顺序、错误隔离）、Retry、Rate Limit、Circuit Breaker、Debounce、资源状态快照与动态调参事件。
- **必须保持的语义**：取消传播、截止时间、重试条件与抖动、限流等待模式、熔断三态与探测、批量结果顺序、失败隔离和资源饱和行为必须显式定义。
- **边界**：Java 虚拟线程、Go goroutine/channel、Rust async/task 是实现差异；规格只承诺行为。线程池/执行器物理参数不可机械跨语言复制。

### 4.21 Web：入站请求保护与中间件 SPI

- **要解决的问题**：限流、熔断、Trace 传播、慢/挂请求、异常降级、热加载、Hook、幂等、租户与版本协商散落在 Controller 或某一容器中。
- **最小契约**：语言无关 `RequestContext`、有序中间件/拦截器、拒绝/降级响应、Reloadable、生命周期 Hook、请求完成/挂起/重载事件。
- **必须保持的语义**：默认 opt-in，不启用不改变业务行为；中间件顺序、短路、异常隔离、Header/Body 大小限制、SSE/WS 旁路、Trace 与指标字段必须可验证；热加载采用原子配置代际切换。
- **边界**：Tomcat/Jetty、Go HTTP、Rust Tower/Axum 等为 Adapter；Core 不依赖具体容器。限流和熔断应复用 Concurrency 语义。

### 4.22 Storage：对象与文件存储

- **要解决的问题**：业务直接使用云 SDK/FTP/SFTP/SMB，客户端创建销毁、连接池、分片传输、凭证轮换和存储切换不可控。
- **最小契约**：对象/文件 key、metadata、读写流、列举、删除、复制、预签名访问、分段/断点传输、图片转换和受控客户端生命周期。
- **必须保持的语义**：Engine/Client 可并发复用；流必须明确所有权和关闭责任；大对象避免全量载入内存；凭证轮换/热切换以“新客户端就绪→原子切换→在途排空→释放旧客户端”执行；不同协议的原子性、目录语义和版本能力不得被虚假统一。
- **扩展与边界**：S3/各云/本地/FTP/SFTP/SMB 是 Adapter；Provider 能力矩阵必须公开。Rust/Go 优先用于高吞吐流式传输和边缘代理。

### 4.23 Vector：多向量库数据面

- **要解决的问题**：多个向量库、多个 Store、不同索引/过滤/批写能力并存时，业务被 SDK 绑定且安全过滤容易后置导致泄露。
- **最小契约**：Store-bound 服务、写入、按 vector ID 删除、按 ID 读取、语义检索、结构化过滤 AST、能力发现、批量流事件、索引生命周期和可选版本投影。
- **必须保持的语义**：业务只能绑定稳定的 Store ID，不得按可替换 Provider 名称路由；一个 Store 同一时刻只能有一个权威 Provider。核心能力与 hybrid/multi-vector/alias/backup/read/lifecycle 等可选能力分离；Provider 不支持时必须明确；ACL/tenant/visibility 等安全过滤必须在 Provider 查询阶段下推，禁止 Top-K 后内存过滤；批量流有背压和逐项结果。
- **边界**：Embedding、文档解析、切片和权限事实不属于 Vector；Provider 通过 Adapter 负责 AST 编译。跨语言协议应固定 score 语义、过滤语义、批处理幂等和索引就绪状态。

### 4.24 StateMachine：状态与生命周期治理

- **要解决的问题**：订单、任务、审批等状态迁移散布在条件分支中，非法迁移、并发竞争和历史遗漏频发。
- **最小契约**：状态机定义、状态、事件、转移、上下文属性、终态、版本/历史、状态变更事件和规则执行结果。
- **必须保持的语义**：转移原子校验；终态默认不可变；历史可追溯；并发冲突有受控失败；规则优先级/短路、表达式安全、超时和失败策略明确。异步持久化与关键状态/步骤的同步持久化可以并存，但覆盖优先级、严格模式、单调序列、旧消息拒绝、重试与死信语义必须公开。状态权威存储与缓存预热可用不同技术实现。
- **边界**：规则引擎、Redis、Stream 是可替换实现；业务状态图由业务模块拥有，组件不替业务定义状态名称。

### 4.25 Redis Stream MQ：Redis Stream 可靠消费

- **要解决的问题**：Redis Stream 的 Consumer Group、Pending、重试、DLQ、幂等和观测由业务重复实现。
- **最小契约**：Stream Publisher、Consumer、Consumer Group、消息转换、确认、错误策略、重试、DLQ、幂等存储和指标/Trace。
- **必须保持的语义**：至少一次投递；成功确认、失败重试/延迟重投、最大次数进入 DLQ；幂等 key 与 TTL；批量拉取、并发、在途上限、关闭和积压恢复。Trace context 必须作为 envelope 元数据透明注入/提取，不能修改业务 payload。`SKIP/RETRY/DEAD_LETTER/FAIL_FAST` 的错误策略必须跨语言一致。
- **边界**：这是 Redis 专项可靠队列，不应伪装成所有 MQ 的通用抽象；可与 Messaging 共用 envelope 和观测字段。

### 4.26 gRPC：RPC 横切拦截

- **要解决的问题**：gRPC 客户端/服务端的 Metadata、鉴权、异常、日志、指标、Trace、限流和优雅关闭每个服务各自实现。
- **最小契约**：可信 Metadata 白名单、身份 Principal、错误分类到标准 status、客户端/服务端 span/metric 约定、超时/取消与优雅停服。
- **必须保持的语义**：Header 名标准化、认证失败与限流/熔断状态码、W3C trace context、拦截器顺序、错误消息脱敏、Graceful shutdown 截止时间均可测试。
- **边界**：gRPC runtime/ServerBuilder 生命周期属于应用或 Adapter；组件只提供可组合中间件和配置，不接管业务 RPC 实现。

### 4.27 NATS：Core NATS 与 JetStream

- **要解决的问题**：发布订阅、RPC、JetStream、DLQ、幂等、KV/Object Store 和上下文透传的语义散落在 NATS 客户端调用中。
- **最小契约**：Core Bus publish/subscribe/request-reply、Endpoint 注册句柄、JetStream stream/consumer、ack/nak/in-progress、DLQ、Key-Value、Object Store、连接生命周期与观测。
- **必须保持的语义**：Core NATS 是 fire-and-forget，关键消息必须使用 JetStream；JetStream 至少一次，消费端幂等；ack wait、最大在途、最大投递、退避与长任务 in-progress 心跳必须定义；动态注册资源必须可关闭。
- **扩展与边界**：NATS 客户端是 Adapter；连接、TLS、Header/Trace 和错误语义是 Core。Rust/Go 是优先实现目标。

### 4.28 Document Parser：安全的多格式解析

- **要解决的问题**：业务直接绑定解析引擎；远程 URL 解析有 SSRF/DNS rebinding 风险；大文件解析会耗尽内存；扫描 PDF 被静默当成空文本。
- **最小契约**：`DocumentReader`/Parser SPI、文件/流/URL 来源、格式检测、结构化段落/图片、同步结果与背压流式事件、错误类型与 URL 抓取策略。
- **必须保持的语义**：内容嗅探优先、扩展名回退；URL 必须执行 host/IP 校验、协议/HEAD 校验、内容校验并防 DNS rebinding 和跨 host 重定向；图像型 PDF 明确失败；流式解析支持取消/背压，图片字节的内存预算和所有权明确。
- **边界**：Tika、PDF/Office/Excel 引擎均为 internal Adapter；OCR、切片、Embedding、存储不是 Parser 的职责。

### 4.29 Document Chunking：确定性文本切片

- **要解决的问题**：RAG/检索文本切片规则随业务复制，导致定位不可追溯、同文档重复入库结果不稳定、流式内存失控。
- **最小契约**：`ChunkingService`、`ChunkingRule`、有序 `Chunk`（文本、ordinal、字符范围、来源 metadata）、诊断信号、流式 Chunker、Token Counter 与可选语义边界 Advisor。
- **必须保持的语义**：相同文本 + 相同规则快照产生相同文本、顺序和范围；固定、递归、Token、段落、句子、Markdown、HTML、页/幻灯片、语义等策略需显式声明；单文档块上限、最小块、重叠、流式尾部内存和降级策略必须可配置。
- **边界**：纯文本 Core 不依赖解析器、模型或向量库；Parser 与语义模型是可选 Adapter；规则版本必须随向量/索引元数据保存以支持重放。

### 4.30 OCR：多 Provider 文字识别

- **要解决的问题**：云 OCR、本地 CLI、GPU/VLM/异步任务的鉴权、协议、语言、超时和错误各异，业务被厂商 SDK/子进程绑定。
- **最小契约**：`OcrProvider.recognize(input, options)`、Image/URL/Stream 输入、语言集合、版面/表格选项、结构化识别结果（block/line/坐标/置信度）、Provider 健康和错误分类。
- **必须保持的语义**：所有已声明 Provider 返回相同的结果与错误模型；内部轮询可存在但调用方必须明确得到同步最终结果或显式异步 Job，不能混淆；输入大小、媒体类型、超时、GPU/sidecar 不可用、Provider 不可用必须可区分。
- **扩展与边界**：云、本地 CLI、Python sidecar、GPU 服务均为 Adapter；多语言映射、鉴权和轮询封装于 Provider；OCR 只识别，不承担文档解析、切片或向量化。

## 5. 推荐的跨语言产品分层

```text
atlas-richie-platform-spec        # schema、错误、配置、事件、契约测试
├── <lang>-core                   # 小型公共类型、错误、配置与 SPI
├── <lang>-http / grpc / web      # transport 与入站/出站治理
├── <lang>-cache / storage        # 基础设施能力
├── <lang>-mqtt / nats / stream   # 高性能消息与长连接能力
├── <lang>-document / vector / ai # 数据与 AI 能力
└── <lang>-security               # secret、oauth、mfa、tenant
```

Core 只能依赖标准库和语言无关 schema；Provider/Adapter 依赖 Core；应用依赖 Core + 被选择的 Adapter。最终产品通过依赖选择实现物理裁剪，不让 Core 通过反射/扫描加载所有可选 Provider。

## 6. 首批落地顺序与验收

### 第一阶段：规格与测试底座

1. 抽出统一错误模型、配置 schema、事件 envelope、Trace/日志字段和生命周期/关闭语义。
2. 为 Cache、HTTP、NATS、MQTT、Concurrency 建立黑盒契约测试；这些能力最能验证高性能语言的实际收益。
3. 固定跨语言编码（JSON/Protobuf 等）、时间、ID、Decimal、二进制、分页、错误和版本兼容规则。

### 第二阶段：Go/Rust 高性能试点

1. 以 MQTT 或 NATS 为首个 Rust/Go 服务/库试点，验证连接数、吞吐、P99、内存、重连、背压和停服行为。
2. 以 Cache + HTTP/gRPC + Concurrency 组成可复用服务端底座。
3. 以 Document Parser/Chunking 建立流式流水线试点；OCR/AI/Vector 按真实业务需求接入。

### 第三阶段：安全与治理能力

1. Secret、Tenant、OAuth、MFA 必须先跑协议/攻击面/轮换契约测试，再声明可生产使用。
2. Logging、Tracing、Desensitize 必须跨语言产生可聚合、可脱敏、可关联的观测数据。
3. DAO、Liquibase、MongoDB 仅在具体语言的持久化生态已选定后实现 Adapter，避免先造抽象再找使用者。

## 7. 文档与实现的关系

本文是后续详细设计的入口，不取代每个组件自己的详细规格。某个能力进入实现前，必须补齐：公共 schema、Provider 能力矩阵、配置默认值、错误与事件表、并发/安全语义、契约测试用例，并按 `oop-design-guardrails` 的编码前门禁完成设计模式评估和用户沟通。

## 8. 证据索引与源码依据

本规格以源码为行为事实来源，以 README 为公开边界来源，以设计文档为约束和演进来源；不以模块名臆测能力。下表的“深入设计”是阅读入口，不代表其中每一项均已实现；其状态仍应按第 2.1 节核验。

| 组件 | 公开边界（README） | 深入设计/专项说明 |
|---|---|---|
| AI | `atlas-richie-ai/README.zh.md` | — |
| Cache | `atlas-richie-cache/README.zh.md` | `docs/zh/缓存核心能力功能.md`；`docs/zh/Redis-L2与性能守卫设计说明.md` |
| DAO | `atlas-richie-dao/README.zh.md` | — |
| Tenant | `atlas-richie-tenant-parent/README.zh.md` | `docs/多租户方案设计.md`；`docs/上下文模块详细设计.md`；`docs/策略模块详细设计.md`；`docs/模式切换数据迁移方案.md` |
| Component Dependencies | 顶层 POM 的 dependency management | — |
| HTTP | `atlas-richie-http-parent/README.zh.md` | 各 transport/core 子模块 README |
| Desensitize | `atlas-richie-desensitize-parent/README.zh.md` | core/Jackson/logging 子模块 README |
| I18n | `atlas-richie-i18n/README.zh.md` | — |
| Liquibase | `atlas-richie-liquibase/README.zh.md` | — |
| Messaging | `atlas-richie-messaging-parent/README.zh.md` | 各 broker Adapter README |
| MongoDB | `atlas-richie-mongodb/README.zh.md` | — |
| MQTT | `atlas-richie-mqtt/README.zh.md` | — |
| MFA | `atlas-richie-mfa-parent/README.zh.md` | `docs/MFA组件完整设计方案.md`；`docs/MFA组件时序图设计.md` |
| Secret | `atlas-richie-secret-parent/README.zh.md` | `docs/zh/design.md` |
| OAuth | `atlas-richie-oauth-parent/README.zh.md` | `docs/zh/oauth-component-design.md`；`docs/zh/oauth-platform-architecture.md` |
| MCP | `atlas-richie-mcp-parent/README.zh.md` | `docs/zh/mcp-component-design.md`；`docs/zh/mcp-server-starter-business-tool-adaptation-plan.md` |
| Microservice | `atlas-richie-microservice/README.zh.md` | — |
| Logging | `atlas-richie-logging/README.zh.md` | — |
| Tracing | `atlas-richie-tracing/README.zh.md` | — |
| Concurrency | `atlas-richie-concurrency/README.zh.md` | — |
| Web | `atlas-richie-web-parent/README.zh.md` | rate-limiter 子模块 README |
| Storage | `atlas-richie-storage-parent/README.zh.md` | core 与各 Provider 子模块 README |
| Vector | `atlas-richie-vector-parent/README.zh.md` | `docs/adr/0001-named-multi-store-topology.md`；`docs/advanced-query-capabilities.md`；`docs/configuration/named-multi-store.md` |
| StateMachine | `atlas-richie-statemachine/README.zh.md` | `docs/一致性联调清单与配置模板.md`；`docs/多状态机使用指南.md`；`docs/数据库持久化性能分析.md` |
| Redis Stream MQ | `atlas-richie-redis-streammq/README.zh.md` | `docs/zh/Redis-Stream-使用指南.md`；`docs/zh/Redis-Stream-Tracing-透传说明.md`；`docs/zh/Redis-Stream-MQ性能分析.md` |
| gRPC | `atlas-richie-grpc/README.zh.md` | — |
| NATS | `atlas-richie-nats/README.zh.md` | — |
| Document Parser | `atlas-richie-document-parser/README.zh.md` | `src/main/java/cn/richie696/component/parser/internal/README.md`；`docs/application-parser-example.yml` |
| Document Chunking | `atlas-richie-document-chunking-parent/README.md` | — |
| OCR | `atlas-richie-ocr-parent/README.zh.md` | parser Adapter README |

除上表外，本次也以 `atlas-richie-component/pom.xml` 校验聚合范围和模块归属。

后续新增或修订实现时，必须同步修订对应能力规格和契约测试；不能以 Java、Go、Rust 或 Python 的某一个实现作为唯一真相。
