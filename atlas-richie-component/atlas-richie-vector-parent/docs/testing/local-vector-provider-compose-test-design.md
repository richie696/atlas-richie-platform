# 本地向量 Provider Compose 联调测试设计

> 范围：/Users/richie696/Development/docker-scripts 下各 provider/provider-local/docker-compose.yml 提供的本地进程、端点和组件连接验证。  
> 不包含：Foundry 业务编排、Embedding/Rerank 模型实现、生产容量或高可用结论。

## Provider 矩阵

| ID | Provider | 最低验证 | 组件验证 | 结果边界 |
|---|---|---|---|---|
| LOCAL-001 | Qdrant | /readyz、HTTP 6333、gRPC 6334 | Named Store 创建 collection、写入、检索、删除 | readiness 不能证明索引参数已应用 |
| LOCAL-002 | Weaviate | /v1/.well-known/ready、宿主 HTTP 18080、宿主 gRPC 15051 | class schema、metadata filter、ACL-safe hybrid 请求 | 需要同一请求同时出现 where 与 hybrid |
| LOCAL-003 | Redis Stack | redis-cli ping、MODULE LIST | FT index、向量写入、原生过滤与 key/index 隔离 | 必须确认是 Redis Stack，不是 plain Redis |
| LOCAL-004 | Neo4j | Browser HTTP 7474、Bolt 7687 | database/label/index 白名单、向量索引和候选后过滤边界 | 当前能力不声明 ACL-safe hybrid |
| LOCAL-005 | MongoDB Local | mongosh ping、replica set、mongot | Search/Vector Search index、collection 隔离、向量查询 | 官方本地镜像是开发/测试用途；先确认当前 adapter API 兼容 |
| LOCAL-006 | 多 Provider 并存 | profile 选择、端口可配置、卷隔离 | 同一应用同时连接两个以上 Named Store | 不要求 Provider 共享物理 schema |
| LOCAL-007 | 模型契约 | 外部 Embedding/Rerank endpoint 可达 | dimension、metric、normalization、候选数和 rerank 超时 | 模型密钥只通过环境变量/密钥管理注入 |
| CLOUD-001 | DashVector | 云端 endpoint 与 API Key 可达 | 唯一 collection、dense+sparse 同次写入、同一 ACL filter 的原生 hybrid、删除清理 | 没有 `VECTOR_DASHVECTOR_IT_RUN=true` 与运行时凭据时必须跳过，不得把配置测试记为 E2E |
| CLOUD-002 | 腾讯云 VectorDB | 云端 URL、用户名与 API Key 可达 | 唯一 collection/index、原生 hybrid、仅“不支持”错误的 Core RRF 回退、ACL 负例、删除清理 | 没有 `VECTOR_TENCENT_VECTORDB_IT_RUN=true` 与运行时凭据时必须跳过，不得把配置测试记为 E2E |

## 2026-09-05 实测结果

| Provider | 真实结果 | 容器处理 | 结论 |
|---|---|---|---|
| Qdrant | collection、3 条写入、payload tenant filter 返回 2 条 | 本次容器已停止 | 通过基础向量检索与过滤；未宣称 ACL-safe hybrid |
| Weaviate | schema、3 条写入、同一 GraphQL `where + hybrid + vector` 返回 2 条且租户均为 `tenant-a` | 本次容器已停止 | 通过 ACL-safe hybrid；外部 EmbeddingModel 向量已下推 |
| Redis Stack | RediSearch HNSW、HASH、TAG filter 返回 2 条、前缀全量 3 条 | 本次容器已停止 | 通过原生过滤与索引隔离；未宣称 ACL-safe hybrid |
| Neo4j | vector index `ONLINE`、向量召回 3 条、候选后 tenant filter 2 条 | 本次容器已停止 | 通过向量检索；仅候选后过滤，不是 ACL-safe hybrid |
| MongoDB Local | replica set、Vector Search index `READY/queryable`、`$vectorSearch` + tenant filter 返回 2 条 | 本次容器已停止 | 通过本地预览数据面；结论不外推到生产 MongoDB Atlas |
| Milvus | health 200、collection/insert/index/load/search/filter 成功，临时 collection 已删除 | 用户已有容器保留 | 通过真实数据面；未改变用户容器生命周期 |
| PGVector | `vector 0.8.6`、HNSW、事务 `hnsw.ef_search=40`、tenant filter 2 条、cosine 排序 `1,3` | 用户已有容器保留 | 通过真实数据面；临时表已删除 |

模型实测：阿里百炼 OpenAI-compatible Embedding `text-embedding-v3` 返回 HTTP 200 和 1024 维；Rerank `gte-rerank` 请求到达服务但返回 HTTP 403 `AccessDenied`，因此只确认 endpoint/请求协议已触达，未确认账号具备 Rerank 模型权限。

最终回归：`mvn -q -pl atlas-richie-component/atlas-richie-vector-parent -am -DargLine='--enable-preview -javaagent:/Users/richie696/.m2/repository/net/bytebuddy/byte-buddy-agent/1.18.10/byte-buddy-agent-1.18.10.jar' test` 退出码为 0；7 个可本地联调 Provider 均按单实例顺序完成数据面验证，测试容器已停止。

DashVector 与腾讯云 VectorDB 的环境门控 E2E 已纳入组件测试：测试会创建随机 `atlas_acl_` collection，写入允许/拒绝租户的数据，断言拒绝文档不会从任一候选通道泄漏，并在 `finally` 中精确删除该 collection。当前运行环境没有注入它们的测试开关和凭据，因此该两条云端 E2E 被明确跳过；不将此描述为已通过。

注意：Weaviate 的 ACL-safe hybrid 只有在 Store 创建时声明 `filter-metadata-fields`，并由受控索引初始化生成 `meta_` 字段及 `field` tokenization 后才成立；未声明字段或复用不兼容旧 schema 时必须拒绝能力或先迁移 schema。

## 通用框架测试资源约定

本组件不依赖任何消费项目的业务 Store、领域表、用户、角色或 JWT。真实 Provider 联调必须在测试运行时创建唯一且可识别的合成资源：Milvus 使用 `atlas_vector_contract_` 前缀的 collection，PGVector 使用同前缀的 schema/table，其他 Provider 使用等价的 collection/class/index 前缀。测试数据只使用合成记录和合成 tenant/ACL 值；创建成功后由 `finally` 执行精确删除，不得按通配符或共享 schema 清理。

测试配置只通过进程环境变量或一次性 JVM 参数注入本机端点和凭据，禁止将凭据、业务物理资源名或业务路由写入测试源码、默认配置或文档。未提供运行时环境时，真实 Provider 测试必须明确跳过；Factory、配置绑定、参数编译和拒绝路径仍由常规自动化测试覆盖。

## 执行步骤

1. 复制 .env.example 为 .env，按本机端口冲突情况调整；检查 .env 未被 Git 跟踪。
2. 只启动目标 profile，执行 docker compose ps，确认目标服务为 healthy。
3. 通过宿主端口执行进程级 readiness；服务未 ready 时不进入组件断言。
4. 启动最小 Spring 消费应用，只装配一个 Named Store；确认不配置高级参数仍可启动。
5. 使用已提供的 EmbeddingModel 写入至少三条带不同租户/可见性 metadata 的记录。
6. 使用同一 Store 读取、检索、删除；再用另一个 Store 的逻辑索引名做负向请求，必须在网络请求前失败。
7. 配置 RerankService 后固定候选集，记录原始候选顺序、重排序顺序、超时和降级结果。
8. 结束容器但保留卷重启，确认数据仍在；清理卷只在显式数据清理场景执行。

## 证据要求

- docker compose ps 和 readiness 只作为进程证据。
- 物理 schema/index 创建响应、真实写入/检索请求和返回结果才是 Provider 数据面证据。
- ACL-safe hybrid 必须证明过滤条件进入 Provider 原生请求；“返回结果数量正确”不足以证明安全下推。
- 需要记录 Provider、Connection ID、Store ID、物理 index 名、模型维度和 metric；不得记录模型密钥、完整查询文本或敏感业务内容。
