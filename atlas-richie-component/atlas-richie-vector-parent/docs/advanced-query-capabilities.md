# 通用高级向量检索能力

本文描述向量组件公开的通用高级数据面。它不包含知识库、提示词、MCP、租户模型、发布审批或任何消费项目领域对象。

## 1. 渐进式使用

- 普通项目继续注入 `VectorService`；不配置 `query-defaults`、不获取高级 Capability 时，行为与旧单 Provider 用法一致。
- 高级项目先从 `VectorServiceRegistry` 获取已授权的 `VectorStoreHandle`，再通过 `requireCapability(...)` 获取窄接口。
- 请求只能使用 Handle 已绑定的逻辑索引；高级命令不接受 Connection、凭据或任意物理 Collection/Table/Class。
- Provider 不支持某能力时，Handle 明确拒绝；不会静默降级，也不会影响基础 `VectorService`。

## 2. 类型化查询与默认值

`VectorAdvancedSearchOperations` 接受 `VectorQueryRequest`。Core 通用字段与默认值如下：

| 字段 | Core 默认值 | 安全上限 | 说明 |
|---|---:|---:|---|
| `topK` | 10 | 1000 | 最终返回数量 |
| `minScore` | 0.0 | 0.0～1.0 | Adapter 最终分数过滤阈值 |
| `candidateLimit` | 10，且不小于 `topK` | 2000 | Provider 候选召回数量 |
| `timeout` | 30 秒 | 2 分钟 | 高级调用的调用方可见超时 |
| `consistency` | `PROVIDER_DEFAULT` | — | Provider 未开放覆盖时明确拒绝 |
| `returnFields` | 空集合 | 64 个 | Provider 未开放投影时明确拒绝 |
| `includeVectors` | `false` | 向量维度 4096 | 默认不返回原始向量 |
| `mmrEnabled` | `false` | — | 默认不执行 MMR |
| `lambda` | 0.5 | 0.0～1.0 | MMR 相关性/多样性权衡 |

覆盖顺序固定为：`Core 安全默认值 < Provider 默认值 < Store query-defaults < 单次请求`。

Store 可选配置示例：

```yaml
platform:
  component:
    vector:
      stores:
        search-store:
          connection-ref: primary
          default-index: documents
          query-defaults:
            top-k: 20
            min-score: 0.2
            candidate-limit: 80
            timeout: 15s
```

只有暴露 `VectorAdvancedSearchOperations` 的 Store 可以配置 `query-defaults`；否则启动期拒绝，避免配置看似成功但实际未生效。

Provider 扩展是版本化类型，不使用公开的任意 Map：

```java
VectorStoreHandle handle = registry.require(VectorStoreId.of("search-store"));
VectorAdvancedSearchOperations advanced =
        handle.requireCapability(VectorAdvancedSearchOperations.class);

VectorSearchExecution execution = advanced.search(new VectorQueryRequest(
        "query text",
        10,
        0.2,
        filter,
        Set.of(),
        50,
        Duration.ofSeconds(10),
        VectorConsistencyPreference.PROVIDER_DEFAULT,
        null,
        new MilvusQueryOptions(64, null)));
```

当前类型化 Provider 扩展：

- Milvus HNSW：`MilvusQueryOptions.ef`；IVF 系列：`nprobe`。两者不可同时提交，且必须与 Store 绑定的索引类型一致。
- PGVector HNSW：`PostgresqlQueryOptions.hnswEfSearch`；IVFFlat：`ivfflatProbes`。参数在执行查询的同一 JDBC Connection/Transaction 中通过 `set_config(..., true)` 应用。
- Weaviate hybrid 权重继续通过 `HybridSearchOptions` 的专用 ACL-safe hybrid 窄接口提供，不属于上述 Milvus/PG 查询扩展。

## 3. 执行回执

高级调用返回 `VectorSearchExecution`，其中结果列表与 `VectorSearchExecutionReceipt` 分离。回执记录：

- Core 契约、Adapter、Capability、Provider 扩展版本；
- 逻辑 Store 与脱敏索引指纹；
- 每个参数的 `requested/configured/effective/applied`、来源层、处理状态和请求证据；
- 不包含查询正文、向量、Filter/ACL 主体、连接参数、凭据或物理索引名。

只有已进入实际 SDK 请求、SQL 事务或框架执行阶段的值才标记为 applied。不支持或冲突参数抛稳定错误，不伪造 applied 回执。

## 4. 候选向量与 MMR

PGVector Store 暴露 `CANDIDATE_VECTOR`；通过 `VectorDiversificationOptions` 显式启用后，Adapter 才增加原始向量投影。未启用时 SQL 不读取、结果不携带原始向量。

Core 提供确定性的客户端 MMR：先使用 Provider 已完成 Filter/ACL 的候选，再进行多样化选择。限制包括：候选最多 2000、向量维度最多 4096、候选向量载荷最多 4 MiB、整个高级响应估算最多 8 MiB、每 Store 高级调用默认最多并发 16。`includeVectors=false` 时，MMR 可使用候选向量但在最终结果中移除它们。

`SERVER_SIDE_MMR` 是独立 Capability；当前验收范围内没有 Adapter 声明它，不能把客户端 MMR 冒充为 Provider 服务端能力。

## 5. ACL-safe hybrid

Weaviate Store，以及显式启用原生 BM25 hybrid 的 Milvus Store，声明 `ACL_SAFE_HYBRID`。调用必须提供非空结构化 `VectorFilter`。

- Weaviate 将同一个编译后的 `where` 与 `hybrid` 放在单一 GraphQL Get 请求中。
- Milvus 将同一个编译后的表达式附着在 dense `vector` 与 BM25 `sparse_vector` 的每一个 `AnnSearchReq` 上，再由同一个 V2 `HybridSearchReq` 融合。因此两路候选都在 Provider 召回前受到 ACL 约束。

Milvus 是可选增强，不改变普通 Store：默认 `hybrid-enabled=false`，仍使用原有 dense schema 与 `VectorService`。需要 ACL-safe hybrid 时，在新建逻辑索引的 `additional-fields` 中设置 `hybrid-enabled: true`；组件会创建 `content`、dense `vector`、BM25 `sparse_vector` 和 Milvus server-side BM25 Function 所需 schema。既有的纯 dense Collection 不能原地变成该 schema，必须新建/rebuild 后切换 Store。

以下情况在发送 Provider 请求前拒绝：空 ACL、空主体集合、非法字段、显式 ACL 与 `HybridSearchOptions.searchOptions.filter` 冲突、非法权重。Filter 值由编译器和客户端双层转义。

PGVector、Qdrant、Redis、MongoDB 与 Neo4j 当前不声明 `ACL_SAFE_HYBRID`。它们仍可使用各自已声明的基础检索或过滤能力。

## 6. 分数语义

`VectorScoreSemantics` 可分别描述 `RAW_DISTANCE`、`VECTOR_SCORE`、`LEXICAL_SCORE`、`FUSED_SCORE`、`RERANK_SCORE` 与 `FINAL_SCORE`，包含方向、范围、归一化方式、转换和跨查询可比性。

现有 Adapter 只声明自己能证明的最终分数；未启用融合或重排时 `VectorSearchResult.score` 的旧行为不变。所有现有 Provider 最终分数默认 `comparableAcrossQueries=false`。跨 Store 混排必须先通过 `VectorScoreFusionGuard.requireComparable(...)`；未知、未归一化或契约不一致时明确拒绝。

## 7. 稳定错误与资源保护

`VectorQueryValidationException.code()` 可能返回：

- `INVALID_VALUE`
- `LIMIT_EXCEEDED`
- `TIMEOUT`
- `CONCURRENCY_LIMIT`
- `UNSUPPORTED_OPTION`
- `CONFLICTING_OPTIONS`

高级超时和并发由 Store 级 Guard 隔离。高级能力失败不会替换或污染基础 `VectorService`。指标使用固定 Operation 分类区分 `SEARCH_TEXT`、`SEARCH_RERANK`、`SEARCH_TUNED`、`SEARCH_MMR` 与 `SEARCH_HYBRID`；指标标签只有 Store、Provider、Operation、Result，Trace 才附加脱敏索引指纹、能力摘要与错误类别。

## 8. Provider 能力与验证矩阵

符号：`是`=当前 Adapter 已暴露；`条件`=取决于 Store 声明；`否`=高级调用明确不可用。VikingDB 按本轮范围排除。

| Provider | Native Filter | ACL Filter | ACL-safe Hybrid | Typed Query Tuning | Candidate Vector | Score Stages | Index Lifecycle | 真实 Provider 证据 |
|---|---|---|---|---|---|---|---|---|
| Milvus | 是 | 是 | 条件：`hybrid-enabled=true` 的原生 BM25 schema | 是：HNSW `ef` / IVF `nprobe` | 是，默认关闭，客户端 MMR | 是 | 是 | collection 创建、写入、默认/调优检索、ACL 负例 hybrid、候选向量投影、清理；与 PG 同进程 |
| PGVector | 是 | 是 | 否 | 是：事务级 HNSW/IVFFlat | 是，默认关闭 | 是 | 是 | schema/table 创建、过滤/调优/候选向量检索、同连接恢复默认、清理；与 Milvus 同进程 |
| Qdrant | 是（仅 `SEARCH_TEXT`） | 是（仅 `SEARCH_TEXT`） | 否 | 否 | 否 | 是 | 是 | 原生 gRPC Filter 请求、跨租户负例、collection 写入/过滤检索/清理 |
| Redis Stack | 条件 | 条件 | 否 | 否 | 否 | 是 | 是 | index、写入、检索、清理 |
| MongoDB Local | 条件 | 条件 | 否 | 否 | 否 | 是 | 是 | collection/search index、写入、检索、清理 |
| Neo4j | 候选后过滤 | 否 | 否 | 否 | 否 | 是 | 是 | vector index、节点、检索、清理；不宣称 ACL 安全 |
| Weaviate | 是 | 是 | 是 | hybrid 权重专用入口 | 否 | 是 | 是 | class、写入、真实 ACL-safe hybrid、清理；请求级负例 |

验证层级严格区分：

1. 单元测试：参数范围、默认值、Filter 编译、分数和 MMR 算法。
2. 请求契约测试：实际 SDK 参数、GraphQL `where + hybrid`、PG `set_config + SELECT` 同连接。
3. 真实 Provider：动态创建唯一测试资源，执行写入/检索/effect 断言并精确清理。
4. 兼容回归：旧配置/default Store 与基础 API 不使用任何高级选项。

真实测试默认关闭，通过通用 `VECTOR_IT_RUN=true` 或 Provider 专用开关（例如 `VECTOR_QDRANT_IT_RUN=true`）显式启用；测试自行创建模拟 Store/collection/schema/table/index，不依赖消费项目业务数据。
