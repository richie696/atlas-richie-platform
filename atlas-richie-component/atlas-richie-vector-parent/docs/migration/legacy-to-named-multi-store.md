# 从旧单 Provider 配置迁移到 Named Multi-store

## 适用范围

本文面向所有使用向量组件的项目。Named Store 可分别承载文档检索、提示词或模板归一化、工具检索、MCP 增强内容检索等用途；这些业务用途与授权规则由消费项目定义，向量组件只提供 Store-bound 数据面、能力发现和资源隔离。

## 兼容周期

- 1.x：旧 `provider` 配置与原 `VectorService` 注入继续可用，不要求简单项目增加任何高级参数。
- 1.x：旧模式会被只读投影为 `default` Connection/Store，并输出不含凭据的弃用告警。
- 最早 2.0.0：计划删除旧配置自动映射；实际升级说明应再次确认删除窗口。
- 新旧配置禁止同时出现。冲突时启动失败，不按 Bean 或配置加载顺序选择。

## 路径 A：简单项目保持不变

只需要一个向量库的项目可在 1.x 继续使用原配置：

```yaml
platform:
  component:
    vector:
      provider: milvus
      default-index: documents
      milvus:
        host: ${VECTOR_HOST}
        port: ${VECTOR_PORT:19530}
```

组件继续提供原 `VectorService`，并在 `VectorServiceRegistry` 中把同一个 Service 实例暴露为 `default` Store；不会创建第二个客户端或连接。

## 路径 B：等价迁移为一个 Named Store

先保持原物理索引、Embedding Model、维度和 Metric 不变，只改变拓扑表达：

```yaml
platform:
  component:
    vector:
      connections:
        primary:
          provider: milvus
          settings:
            host: ${VECTOR_HOST}
            port: ${VECTOR_PORT:19530}
      stores:
        default:
          connection-ref: primary
          embedding-model-ref: aiEmbeddingModel
          default-index: documents
          indexes:
            documents:
              name: documents
              dimension: 1536
              metric: cosine
              index-type: hnsw
```

只有一个 Named Store 时仍提供兼容 `VectorService` Bean，且它与 Registry Handle 中的 Service 是同一个运行时对象。增加第二个 Store 后，全局 Service 不再创建，调用方必须显式选择 Store。

## 路径 C：共享连接、多 Embedding 与 optional Store

```yaml
platform:
  component:
    vector:
      connections:
        shared-pg:
          provider: postgresql
          settings:
            jdbc-url: ${VECTOR_PG_JDBC_URL}
            username: ${VECTOR_PG_USERNAME}
            password: ${VECTOR_PG_PASSWORD}
        optional-weaviate:
          provider: weaviate
          settings:
            scheme: https
            host: ${VECTOR_WEAVIATE_HOST}
            api-key: ${VECTOR_WEAVIATE_API_KEY}
      stores:
        document-search:
          connection-ref: shared-pg
          embedding-model-ref: documentEmbeddingModel
          default-index: documents
        prompt-normalization:
          connection-ref: shared-pg
          embedding-model-ref: promptEmbeddingModel
          default-index: prompts
        tool-retrieval:
          connection-ref: optional-weaviate
          embedding-model-ref: aiEmbeddingModel
          default-index: tools
          required: false
```

同一 `connection-ref` 的 Store 复用一个物理 Client/连接池，但 Service、Embedding 绑定、索引白名单与能力 Handle 分开。`required: false` 只允许该 Store 初始化失败时降级，不允许请求静默回退到其他 Store。

## 调用与能力发现

```java
VectorStoreHandle store = registry.require(VectorStoreId.of("document-search"));
List<VectorSearchResult> hits = store.service()
        .searchByText("documents", query, 10, SearchOptions.builder().build());

VectorAclAwareHybridSearchOperations aclHybrid = store.requireCapability(
        VectorAclAwareHybridSearchOperations.class);
```

- `require` 找不到 Store：`ROUTE_NOT_FOUND`。
- `requireCapability` 不满足：`CAPABILITY_MISMATCH`，不会自动降级为不安全实现。
- 可选高级参数未填写：使用 Core/Provider 兼容默认值，基础 API 行为不变。
- 参数优先级为 Core 安全默认值 < Provider 默认值 < Store 配置 < 单次类型化调用覆盖；Adapter 不支持的覆盖必须拒绝，不能静默忽略。
- `required-capabilities` 只声明业务必须拥有的能力；Adapter 未真实暴露时启动失败。
- 普通请求不得直接接受 Connection ID 或物理索引名。用户/租户到 Store ID 的映射与授权由消费项目完成。

## 迁移验证

1. 在测试环境只迁移一个 Store，保持物理索引与模型不变。
2. 验证基础写入、检索、删除及结果契约。
3. 通过 `VectorStoreDiagnostics` 检查 Store 状态；没有健康能力时 `UNKNOWN` 不等于 `UP`。
4. 通过能力描述确认 configured/effective 能力，不按 Provider 品牌推测。
5. 增加第二个 Store 后验证无全局 `VectorService`，并做跨 Store 索引、Filter、ACL 和 Embedding 负例。
6. 只有在请求或 Provider 响应能证明时，才把调优参数记为 applied。

## 回滚

1. 停止使用新增 Store 和高级能力入口。
2. 恢复旧 `provider` 与 Provider 专属配置。
3. 删除完整的 `connections` 和 `stores` 配置块，禁止保留半套 Named 拓扑。
4. 不自动删除 Named Store 使用的物理索引；确认没有数据依赖后再通过运维流程处理。

## 已知限制

- 当前 live Provider 验证尚未覆盖所有数据库；Factory/请求契约测试不等于真实服务生效证据。
- 目前只有 Weaviate Adapter 暴露 `ACL_SAFE_HYBRID`，其他 Provider 不会因品牌理论能力而自动声明。
- PGVector 查询级参数已保证同事务调用时序，但仍需真实 PostgreSQL/pgvector 验证作用域和执行计划。
- Redis 旧 Mockito 套件在 JDK 25 下受 Byte Buddy 自附加限制；无 Mockito 的 Named/legacy 契约测试已通过。
