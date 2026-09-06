# ADR-0001：向量组件采用 Named Multi-store 拓扑

- 状态：Accepted
- 日期：2026-09-05
- 作用域：`atlas-richie-vector-parent`

## 背景

当前组件以 `platform.component.vector.provider` 选择一个全局 Provider，并由
`VectorMultiProviderGuard` 拒绝同一 Spring `ApplicationContext` 中出现多个
`VectorService`。Provider 自动配置、`VectorStore`、`VectorFilterCompiler`、
`EmbeddingModel` 以及 `KnowledgeBaseVectorService` 也都建立在全局单实例假设上。

该模型可以支持“不同微服务进程分别选择不同 Provider”，但不能支持同一应用同时承载：

- Knowledge RAG 文档检索；
- Prompt 模板归一化检索；
- MCP 工具或增强提示词检索；
- Admin 向量投影写入和索引生命周期操作。

这些用途可能使用不同 Provider、连接、Embedding 模型、维度、物理索引、ACL 和分数语义。

## 决策

向量组件从“每应用一个 Provider”升级为“每应用多个具名逻辑 Store”。

```text
application
  -> VectorServiceRegistry
      -> knowledge-rag   -> milvus-main -> collection/index
      -> prompt-retrieval -> pg-prompt  -> table/index
      -> mcp-retrieval    -> pg-prompt  -> table/index
```

### 身份边界

| 身份 | 含义 | 稳定性与约束 |
|---|---|---|
| `connectionId` | Provider 物理连接或集群配置 | 多个 Store 可共享；不同连接必须资源隔离 |
| `vectorStoreId` | 业务可依赖的稳定逻辑向量库 | 应用内唯一；同一时刻只绑定一个权威 Provider |
| `indexName` | Provider 内的物理 collection/table/index | 由 Store 与业务资源解析，不能由普通请求任意指定 |

### 核心规则

1. 业务代码按 `vectorStoreId` 选择 Store，不按 Provider 名称选择实现。
2. Core 不持有任何消费项目的业务用途类型、环境、租户、资源范围或策略生命周期。
3. 一个 Store 同一时刻只有一个权威 Provider；跨 Provider 双写、迁移和容灾是独立能力。
4. Store 绑定自己的 Embedding、Filter、Score、Capability、Health 和默认物理索引。
5. 用户输入不得直接选择任意 Store、Connection 或物理表名。
6. 跨 Store 聚合与分数融合属于上层编排，不属于 Provider Registry。
7. Registry 首期在启动后只读；不实现运行时热增删 Store。
8. required Store 初始化失败阻断启动；optional Store 只降级自身且不得错误回退。

### 渐进式能力与默认行为

Named Multi-store 和高级检索是可选增强，不是所有消费项目的使用门槛：

1. 未配置 `connections`/`stores` 时，旧单 Provider 配置映射为唯一 default Store，基础 API 和默认检索行为保持不变。
2. 新增参数必须有文档化默认值；未设置时不得为了“调优”额外改写 Provider 请求。
3. 只有声明具名拓扑的应用才启用多 Store Registry；只有调用高级接口或提交类型化高级选项时才启用对应能力。
4. 基础向量检索不要求 ACL-safe hybrid、候选向量、查询调优、索引生命周期或分阶段分数能力。
5. 消费者声明 `required-capabilities` 时启动期校验；未声明的可选能力在调用时通过 Capability 判断。
6. Provider 不支持高级能力时必须明确拒绝该高级调用，但不能影响同一 Store 的基础检索。
7. 参数优先级为 Core 安全默认值、Provider 默认值、Store 配置、单次调用覆盖；执行回执应说明最终来源。

首期 Store 配置默认值：

| 参数 | 默认值 | 语义 |
|---|---|---|
| `embedding-model-ref` | `aiEmbeddingModel` | 沿用现有唯一文本 Embedding Bean |
| `default-index` | `documents` | 沿用现有默认索引名 |
| `required` | `true` | Store 初始化失败时阻止应用带着错误路由启动 |
| `required-capabilities` | 空集合 | 普通 Store 不强制任何高级能力 |
| `indexes` | 空集合 | 不额外声明或管理物理索引 |
| Connection `settings` | 空 Map | 由 Provider Factory 校验其必需连接参数 |

这些默认值是兼容基线，不等于 Provider 的高级调优默认值。后者必须由对应 Adapter
根据 Provider/索引类型公开，并在未显式设置时避免覆盖数据库原生默认行为。

## 目标配置模型

```yaml
platform:
  component:
    vector:
      connections:
        milvus-main:
          provider: milvus
        pg-prompt:
          provider: postgresql
      stores:
        knowledge-rag:
          connection-ref: milvus-main
          embedding-model-ref: knowledgeEmbeddingModel
          default-index: knowledge_documents
          required-capabilities: [ACL_FILTER]
        prompt-retrieval:
          connection-ref: pg-prompt
          embedding-model-ref: promptEmbeddingModel
          default-index: prompt_templates
```

连接配置可以包含 Provider 专属参数和 Secret 引用，但脱敏 Store 描述、Capability 注册和诊断
不得包含连接串、用户名、密码、Token 或证书内容。

## 核心契约方向

```text
VectorProviderFactory
  -> VectorConnectionHandle
  -> VectorStoreHandle
  -> VectorServiceRegistry
  -> store-bound business facade
```

`VectorStoreHandle` 组合现有窄能力接口，而不是重新创造一个必须由所有 Provider 实现的万能接口。
Provider 模块负责把实例配置转为 Handle；Core 负责拓扑校验、注册、查找和生命周期协调。

## 当前实现盘点

### 全局单实例约束

| 位置 | 当前行为 | Multi-store 影响 |
|---|---|---|
| `VectorProperties.provider` | 全局选择一个 Provider | 不能表达多个 Store/Connection |
| `VectorMultiProviderGuard` | 多个 `VectorService` 直接启动失败 | 必须替换为拓扑校验 |
| `VectorAutoConfiguration` | 注入唯一 Service/Compiler/Embedding | 必须改为 Handle/Registry 装配 |
| Provider `@ConditionalOnProperty` | 只有选中的 Provider 创建 Bean | 必须改为 Factory 按配置实例化 |
| Provider 配置前缀 | 每种 Provider 只有一个全局配置对象 | 不能表达同 Provider 多连接 |
| `KnowledgeBaseVectorService` | 构造时绑定唯一 `VectorService` | 必须绑定明确 Store Handle |

### Provider 自动配置盘点

| Provider | 当前配置前缀 | 主要全局资源 | 首轮迁移优先级 |
|---|---|---|---|
| Milvus | `platform.component.vector.milvus` | `MilvusServiceClient`、`VectorStore`、Filter Compiler、Service | P0 |
| PostgreSQL | `platform.component.vector.postgresql` | DataSource/JdbcTemplate、`PgVectorStore`、Precomputed Operations、Service | P0 |
| Weaviate | `platform.component.vector.weaviate` | HTTP Client、Filter Compiler、Service | P1 |
| Qdrant | `platform.component.vector.qdrant` | Client、VectorStore、Service | P2 |
| Redis | `platform.component.vector.redis` | Redis/Search resources、Service | P2 |
| MongoDB Atlas | Provider 专属前缀 | Mongo VectorStore、Service | P2 |
| Neo4j | `platform.component.vector.neo4j` | Driver/VectorStore、Service | P2 |
| VikingDB | `platform.component.vector.vikingdb` | 控制面/数据面 Client、VectorStore、Service | P2 |

### 通用消费模式盘点

| 通用用途 | 推荐入口 | 示例 Store ID | 组件职责 |
|---|---|---|---|
| 文档在线检索 | Store-bound Search/ACL Handle | `knowledge-rag` | 提供过滤、检索和能力契约，不定义文档领域模型 |
| 向量投影写入 | Store-bound Write/Bulk Handle | `knowledge-rag` | 保证写入落到指定 Store，不定义上层同步流程 |
| 索引重建/回收 | 可选 Lifecycle Handle | 由消费方命名 | 按 Capability 暴露，不强制普通项目实现 |
| 模板归一化检索 | Text/Precomputed Search Handle | `prompt-retrieval` | 支持独立 Store 和预计算向量路径 |
| 工具或增强内容检索 | Store-bound Search Handle | `tool-retrieval` | 支持独立 Store，不定义业务分类与策略 |

## 旧配置迁移

| 旧配置 | 兼容映射 | 规则 |
|---|---|---|
| `platform.component.vector.provider=<type>` | `connectionId=default`、`vectorStoreId=default` | 保持当前单 Provider 行为 |
| `platform.component.vector.<provider>.*` | `connections.default` 的 Provider 专属配置 | 兼容期内转换，不复制敏感值到描述对象 |
| `platform.component.vector.default-index` | `stores.default.default-index` | 保持旧默认索引 |
| 全局 `aiEmbeddingModel` | `stores.default.embedding-model-ref=aiEmbeddingModel` | 仅 legacy default Store 使用 |

新旧拓扑配置同时存在时启动失败，不能按配置加载顺序决定优先级。多 Store 模式不创建模糊的
`@Primary VectorService`；旧的直接注入只在唯一 `default` Store 下通过兼容代理保留。

## 不采用的方案

### 仅删除 `VectorMultiProviderGuard`

拒绝。该方案会造成 `VectorService`、`VectorStore`、Filter Compiler 和 Embedding 注入歧义，
且无法表达同 Provider 多连接或 Store 级 Capability。

### 业务层按 Provider 名称路由

拒绝。Provider 是可替换基础设施，业务用途必须绑定稳定 Store ID；否则调优策略、ACL 和迁移都会
与具体数据库耦合。

### 将所有 Provider 放入一个动态万能代理

拒绝。不同 Provider 的连接、过滤、索引生命周期和分数语义不同，应通过 Factory 和窄能力接口组合，
不能用运行时异常伪装统一能力。

## 风险与控制

| 风险 | 控制 |
|---|---|
| 请求路由到错误 Store | 构造期绑定 Store；用户请求不直接指定 Store ID；契约测试捕获目标 Adapter |
| Embedding/维度串用 | Store 级模型引用与物理索引启动校验 |
| ACL Compiler 串用 | Compiler 放入 Handle；Knowledge Store 声明 required capability |
| 连接池和客户端膨胀 | Connection Registry 复用同 Connection 资源；建立资源基线 |
| 旧应用升级失败 | Legacy default Store 映射和明确弃用周期 |
| 单库故障扩大 | required/optional 策略和 Store 级 Health |
| 敏感配置泄漏 | 描述、指标、日志和注册表只输出脱敏身份与指纹 |

## 完成门槛

本 ADR 的落地以 `MULTI_STORE_VECTOR_TUNING_TASKS.md` 中 `GATE-A` 为准。至少必须由真实
Milvus 与真实 PGVector 证明同一应用内的连接、路由、Embedding、Filter、检索和生命周期互不串用。
