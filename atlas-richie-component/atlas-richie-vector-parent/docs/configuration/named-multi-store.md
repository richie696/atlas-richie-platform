# Named Multi-store 配置示例

## 简单项目：保持现有配置

不需要多库或高级检索能力的项目无需增加 `connections`、`stores` 或能力参数：

```yaml
platform:
  component:
    vector:
      provider: milvus
      default-index: documents
      milvus:
        host: ${VECTOR_MILVUS_HOST}
        port: ${VECTOR_MILVUS_PORT:19530}
```

组件继续提供唯一 default Store 和原基础 API。示例只使用环境变量占位，不应在配置文件中提交凭据。

## 多库项目：按需声明具名拓扑

```yaml
platform:
  component:
    vector:
      connections:
        primary-cluster:
          provider: milvus
          settings:
            host: ${VECTOR_PRIMARY_HOST}
            port: ${VECTOR_PRIMARY_PORT:19530}
        shared-postgres:
          provider: postgresql
          settings:
            jdbc-url: ${VECTOR_PG_JDBC_URL}
            username: ${VECTOR_PG_USERNAME}
            password: ${VECTOR_PG_PASSWORD}
      stores:
        primary-search:
          connection-ref: primary-cluster
          embedding-model-ref: primaryEmbeddingModel
          default-index: primary_documents
          required-capabilities:
            - ACL_FILTER
        normalized-content:
          connection-ref: shared-postgres
          default-index: normalized
          indexes:
            normalized:
              name: normalized_content
              dimension: 1536
              metric: cosine
              index-type: hnsw
              additional-fields:
                schema-name: vector_search
```

`normalized-content` 未填写的字段使用兼容默认值：

- `embedding-model-ref: aiEmbeddingModel`
- `embedding-normalization: UNSPECIFIED`
- `embedding-modalities: [TEXT]`
- `default-index: documents`
- `required: true`
- `required-capabilities: []`
- `indexes: {}`

## 启用规则

- `connections` 和 `stores` 都为空：使用旧单 Provider 模式。
- 任一具名拓扑集合非空：进入 Named Multi-store 模式，两个集合都必须有效。
- `required-capabilities` 为空：不要求高级能力，基础检索仍可使用。
- `embedding-normalization` 与 `embedding-modalities` 是 Store 对模型输出的显式约定；未声明时不猜测归一化且只声明文本模态。
- 声明必需能力：组件在 Store 初始化时验证实际 Adapter/索引是否满足。
- Provider 专属 `settings` 由对应 Provider Factory 严格校验；不识别的键不得静默生效。
- 同一个 Named Connection 下，两个 Store 不得解析到同一物理 Collection、Class、表、Label、索引或 Key 前缀；Provider Factory 会提供物理资源身份并在启动期 fail-fast。组件不会静默改名或自动加 Store 前缀，以免改变已有物理资源映射。
- 新旧拓扑配置不允许混用；迁移时应完整切换并保留可回滚配置。

## 兼容与迁移

- 旧单 Provider 配置在 1.x 兼容期内继续创建原有 `VectorService`；Core 只把这个现有实例投影为 `default` Connection/Store，不会再创建 Client、连接池或 Provider Service。
- Named 拓扑只有一个可路由 Store 时，Core 同时提供指向同一 Store Handle 的 legacy `VectorService` Bean；声明两个及以上 Store 时不再提供全局 Service，调用方必须通过 `VectorServiceRegistry` 显式选取已授权 Store。
- 旧 `provider` 选择配置与 `connections`/`stores` 同时出现时启动失败，禁止按自动装配顺序静默选择。
- 使用旧模式时会输出不含连接信息和凭据的弃用告警。旧配置自动映射计划最早在 2.0.0 删除；1.x 内保留零新增参数的简单用法。
- 迁移时先把原 Provider 连接参数移到一个 Named Connection，再声明一个 Store 并保持原物理索引、Embedding Model 和维度不变；验证读写后再增加第二个 Store 或高级能力。
- 回滚只需恢复旧配置并删除完整的 `connections`/`stores` 块；不要保留一半 Named 拓扑。迁移和回滚都不应自动创建或删除物理索引，除非显式启用对应 Provider 的 `initialize-schema`。

## PGVector Named Store 约束

- 每个 PostgreSQL `connectionId` 创建并拥有一个独立 Hikari DataSource；引用同一连接的 Store 复用该连接池。
- 每个 Named Store 当前绑定一个默认逻辑索引；`indexes.<id>.name` 映射为受控物理表 `vector_<name>`。
- `additional-fields.schema-name` 默认 `public`，只接受小写 PostgreSQL 标识符。
- 同一 PostgreSQL Named Connection 下，`schema-name + vector_<name>` 必须唯一；重复映射在启动期拒绝。需要复用一个数据库时，应为不同 Store 配置不同 schema 或表名。
- Named Store 的运行时入口只接受已声明的逻辑索引 ID；普通请求不能传入任意 schema/table。
- `initialize-schema` 默认 `false`，`vector-table-validations-enabled` 默认 `true`，`max-document-batch-size` 默认 `10000`。
- `pgvector.efSearch` 与 `pgvector.ivfflatProbes` 仅在单次检索显式设置时通过当前 JDBC 事务应用；未填写时不会执行额外调优 SQL。
- 当前 Factory 拒绝尚未实际映射的 `index-params`，避免配置被静默忽略。
- PGVector 当前不声明 `ACL_SAFE_HYBRID`；ACL 过滤只在公开的预计算向量数据面中声明为 Provider 召回前过滤能力。

## Weaviate Named Store 约束

- Named Store 必须声明一个默认逻辑索引，`indexes.<id>.name` 是受控 Weaviate Class 名称。
- 当前 Class 名称要求以大写字母开头；运行时只接受已声明的逻辑索引 ID，不能通过请求选择其他 Class。
- Connection 设置支持 `scheme`、`host`、`api-key` 以及三个毫秒级连接超时；未知字段会在启动期失败。
- `additional-fields.consistency-level` 默认 `QUORUM`；`filter-metadata-fields` 使用 `field:text` 或 `field:number` 逗号分隔格式。
- 当前 Factory 仅接受一个 HNSW 索引，并拒绝未实际映射的 `index-params` 与 shards 设置。
- Weaviate Handle 暴露 `ACL_SAFE_HYBRID`：结构化 ACL `where` 与 `hybrid` 位于同一个 GraphQL `Get` resolver，适用于 BM25 与向量共同召回前过滤。
- `ACL_SAFE_HYBRID` 必须通过类型化窄接口调用；普通基础检索不要求该能力。

## Qdrant Named Store 约束

- 每个 Qdrant `connectionId` 创建并拥有一个独立 Client；引用同一连接的 Store 复用该 Client，并由连接生命周期统一关闭。
- Named Store 当前必须声明一个默认 HNSW 索引，`indexes.<id>.name` 映射到受控 Collection；运行时不能通过请求访问未声明 Collection。
- Connection 设置支持 `host`、`port`、`use-transport-layer-security`、`api-key` 和 `timeout-ms`；未知字段在启动期失败，客户端兼容性网络探测不会在装配期执行。
- 平台 BOM 将 Qdrant Java Client 固定为 `1.19.0`，用于匹配 1.19.x 服务端并避免 Spring AI 2.0.0 传递的 1.17.0 触发跨两个 minor 的兼容性风险。
- Store 的 `additional-fields` 仅支持 `initialize-schema` 与 `content-field-name`。Named 模式默认不创建 Collection，避免仅加载配置就修改外部系统。
- 当前 Factory 拒绝尚未实际映射的 replicas、shards 和 `index-params`，不把被忽略的配置报告成已生效。
- Qdrant Handle 为基础文本向量检索暴露 `NATIVE_FILTER` 与 `ACL_FILTER`：统一结构化 `VectorFilter` 经 Qdrant 专用校验器和 Spring AI 转换后进入原生 gRPC `SearchPoints.filter`，在 Provider 召回前执行。当前精确支持 `EQ`、`IN`、`CONTAINS_ANY`、`RANGE`、`NOT`、`AND`、`OR`；等值与集合值只接受字符串或 64 位整数，区间只接受有限数值。`EXISTS`、布尔/小数等值及混合类型集合会在网络调用前明确拒绝。
- Qdrant Filter 能力操作范围当前仅为 `SEARCH_TEXT`，不宣称 filtered vector、hybrid 或查询级调优；尤其不声明 `ACL_SAFE_HYBRID`。
- Qdrant Filter 不要求预先声明 payload schema；若业务需要大规模过滤性能，payload index 的生命周期与字段策略仍由消费项目或后续专用管理能力负责。

## Redis Named Store 约束

- 每个 Redis `connectionId` 创建并拥有一个独立 Jedis Client；同一 Connection 下多个 Store 复用 Client，但各自绑定不同 RediSearch 索引和 `<physical-index>:` Key 前缀。
- Connection 设置支持主机、端口、TLS、用户名/密码、database、client name 和三类超时；未知字段在启动期失败，配置与异常输出不回显凭证。
- Named Store 当前必须声明一个 HNSW 或 FLAT 索引；HNSW 可选参数仅为 `M`、`efConstruction`、`efRuntime`，FLAT 配置这些参数会失败。
- `additional-fields.initialize-schema` 默认 `false`；`metadata-fields` 使用 `field:text|tag|numeric` 的逗号分隔格式，供结构化过滤和 ACL 字段建模。
- Store Handle 暴露 `NATIVE_FILTER`、`ACL_FILTER` 与 `INDEX_LIFECYCLE`。Redis 当前没有框架级 hybrid 窄接口，因此不声明 `ACL_SAFE_HYBRID`。
- 未启用 Named 拓扑时，原有 JedisConnectionFactory 和 legacy `VectorStore` 自动配置路径保持不变。

## MongoDB Atlas Named Store 约束

- Connection 必须显式提供 `connection-string` 与 `database`；每个 Connection 拥有独立 MongoClient/MongoTemplate，同连接多个 Store 复用客户端并隔离 Collection。
- Named Store 当前使用 `embedding` 向量字段并只接受 cosine；`index-params.numCandidates` 映射到 Spring AI 数据面，未知或未应用参数启动即失败。
- `filter-metadata-fields` 非空时才声明 `NATIVE_FILTER` 与 `ACL_FILTER`，否则简单 Store 只声明 `INDEX_LIFECYCLE`。
- `initialize-schema` 默认 `false`；物理 Collection 与 Vector Search index 均来自受控配置，请求不能任意选择。

## Neo4j Named Store 约束

- 每个 Connection 拥有独立 Driver，并将 database、连接池及超时绑定到 Store 的所有原生 Session；同连接 Store 使用不同 Label 与真实 Vector Index。
- `indexes.<id>.name` 是受控物理基名，派生 `VectorDocument_<name>`、`<name>_idx` 与唯一约束名；请求只接收逻辑索引 ID。
- Neo4j Spring AI 的 Filter 在 `db.index.vector.queryNodes` 之后执行，因此只声明 `NATIVE_FILTER`，不声明 `ACL_FILTER` 或 `ACL_SAFE_HYBRID`。
- `initialize-schema` 默认 `false`，未知 `index-params`、replicas、shards 和不支持的距离度量均启动失败。

## VikingDB Named Store 约束

- `VIKINGDB` 已作为正式 Provider ID；Connection 设置绑定数据面与可选控制面客户端，AK/SK 只作为凭证使用且不进入描述符或异常。
- 物理 Collection 取 `indexes.<id>.name`，VikingDB index、project、description、metadata schema 和 scalar index 通过受控 `additional-fields` 配置。
- 只有声明 `scalar-index` 且字段存在于 `metadata-fields` 时才暴露 `NATIVE_FILTER` 与 `ACL_FILTER`；当前不声明 hybrid、精确读取或索引生命周期。
- `initialize-schema` 默认 `false`，显式启用时必须配置控制面 endpoint；当前 Volcengine SDK 构造器强制 ping 且以 JVM 静态状态缓存结果，这是 live 验证必须覆盖的供应商限制。
- 模块局部锁定 JVM 版 `fastjson2:2.0.58`，避免上游 BOM 选择缺少签名类的 Android 变体。

## Store 级健康与错误分类

- `VectorStoreDiagnostics` 是按需调用的 Core 窄接口；仅在调用 `check`/`checkAll` 时执行 Provider 健康探针，普通检索路径和未配置高级能力的项目没有额外后台任务。
- 每个 Store 独立返回 `UP`、`DOWN` 或 `UNKNOWN`；一个 optional Store 启动失败会作为独立 `STARTUP_FAILURE` 返回，其他 Store 仍可检查。聚合状态只用于 legacy/Actuator 桥接，不替代逐 Store 明细。
- Provider 没有暴露 `VectorIndexStatsOperations` 时返回 `UNKNOWN`，不得把“无法探测”冒充健康，也不会为了健康检查自动创建索引。
- 稳定错误类别包括 `ROUTE_NOT_FOUND`、`CAPABILITY_MISMATCH`、`PROVIDER_UNAVAILABLE`、`HEALTH_CHECK_FAILED` 和 `STARTUP_FAILURE`。快照只包含 Store ID、Provider、required、状态、类别和已检查逻辑索引数量。
- 快照禁止包含查询正文、向量、ACL 主体、凭据、Connection settings、Provider 异常消息和完整物理索引名。Actuator、指标或 Trace 集成应只使用上述脱敏字段和低基数枚举。

Named Store 可选注入一个 `VectorStoreObservationHook`。Core 只在该 Hook 存在时装饰基础 `VectorService`，并在同步及批量操作结束时发送 `VectorStoreObservationEvent`；未配置 Hook 时不增加代理和事件开销。`metricTags()` 固定只返回 Store、Provider、Operation、Result 四类有界标签，`traceAttributes()` 额外返回逻辑索引 SHA-256 截断指纹、能力 ID 摘要和稳定错误类别。事件不保存原始逻辑/物理索引名、查询正文、记录内容、向量或 ACL。消费项目可将 Hook 桥接到自己的 Micrometer/OpenTelemetry 版本，Core 不强制引入观测实现。
