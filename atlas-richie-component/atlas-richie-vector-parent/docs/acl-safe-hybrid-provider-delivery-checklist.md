# ACL-safe Hybrid 与新 Provider 交付任务清单

## 1. 范围与完成定义

本清单合并两项工作：

1. 新增 Alibaba Cloud DashVector 与 Tencent Cloud VectorDB Provider。
2. 为 Qdrant、PGVector、MongoDB Atlas、Neo4j、Redis、VikingDB 补齐可选的 ACL-safe hybrid 检索能力。

普通项目不配置 hybrid/sparse 参数时，必须继续按既有 dense 检索工作；不因新增能力改变 schema、写入行为或启动依赖。只有启用了 Store 的 `hybrid-enabled` 并声明了可由该 Provider 安全执行的第二路召回数据面时，才可以声明 `ACL_SAFE_HYBRID`；物理 sparse vector Provider 还必须绑定 sparse 编码器，原生全文索引 Provider 不需要该编码器。

“完成”不是编译通过：每个 Provider 必须通过本清单所列的真实服务 E2E。没有可用服务、账号或权限时，任务保留未完成状态，并记录阻塞原因，不能勾选。

## 2. 固定安全约束

- dense 与 sparse 候选召回必须分别在 Provider 侧应用完全相同的 `VectorFilter` / ACL 谓词。
- 只允许对两组已过滤候选调用 Core 的加权 RRF；禁止先召回后 ACL 过滤。
- 原生 hybrid 调用仅在 Provider 明确报告“该 Store/版本不支持该能力”时才回退。认证、权限、网络、限流、超时、数据损坏等错误必须原样失败。
- 若没有 sparse 编码器或物理 sparse/text 索引，hybrid 请求必须明确拒绝或仅在调用方显式选择 `dense-only` 降级时执行；不得标记为 hybrid 成功。
- Store capability 必须与实际注册的 `VectorAclAwareHybridSearchOperations` 一致；二者有一个缺失即为不支持。

## 3. 优先级和依赖

| 优先级 | 交付批次 | 目标 | 依赖 | 通过门槛 |
|---|---|---|---|---|
| P0 | Core 基座 | sparse 编码、受控 native-first、ACL RRF 融合、能力校验 | 无 | Core 单元/契约测试 |
| P0 | Milvus / Weaviate 回归 | 防止已实现能力被新基座破坏 | P0 Core | 真实 E2E 回归 |
| P1 | Qdrant / Redis / VikingDB | 优先本地可部署或已有可用环境的 Provider | P0 | Provider E2E |
| P1 | DashVector / Tencent VectorDB | 新 Provider 与原生优先/回退链路 | P0、云端凭据和权限 | 云端 E2E |
| P2 | PGVector / MongoDB Atlas / Neo4j | 以全文索引 + 向量索引实现双路候选 | P0、对应服务镜像/云集群 | Provider E2E |
| P3 | 全矩阵回归 | 多 Store 并存、性能与文档 | P0-P2 | 集成 E2E 矩阵 |

## 4. P0：Core 基座

- [x] 新增 `AclSafeHybridSearchFallback`：使用加权 RRF 合并已 ACL 过滤的 dense/sparse 候选。
- [x] 原生优先仅对 Provider 显式分类的“不支持原生 hybrid”异常回退；其他错误不吞没。
- [x] 定向单元测试：去重、权重、候选隔离、受控回退、非能力错误透传。
- [x] 增加 `SparseVector` 与 `SparseVectorizer`：文档和查询共用的 sparse 编码契约，校验 token ID、权重有限性和空向量；旧 `SparseQueryVectorizer` 保持兼容但已废弃。
- [x] 定义 Store 级 sparse 配置：`hybrid-enabled`、dense/sparse 字段名、编码器 Bean 名、候选池大小、显式降级策略。
- [x] 在 `VectorStoreHandle` 建立 capability 与操作实现的双向校验；不允许只声明 `ACL_SAFE_HYBRID`。
- [x] 为 `VectorAclAwareHybridSearchOperations` 补充 execution 标记：`native` 或 `core-rrf-fallback`，供诊断与观测使用。
- [x] 增加 Core ACL 契约测试：拒绝候选不能进入任一分支、更换 Provider 不改变异常语义、普通 dense Store 不加载 sparse 依赖。

## 5. 既有 Provider 实施任务

### 5.1 Qdrant（P1）

- [x] 增加 `hybrid-enabled` Store schema：named dense vector、sparse vector、ACL payload 字段索引。
- [x] 写入/更新/删除路径同步维护 dense 与 sparse 向量，确保幂等。
- [x] 以同一 `Filter` 执行 dense 与 sparse 检索；使用 Core RRF 融合。
- [x] 注册 `VectorAclAwareHybridSearchOperations`，仅在 schema 与 encoder 均有效时声明能力。
- [x] E2E：越权文档仅存在于高分 sparse/dense 候选时仍不可见；验证原生不可用时 Core RRF；删除后双路均不可见。

### 5.2 Redis（P1）

- [x] 设计 RediSearch 文档字段：dense KNN、文本字段、ACL TAG/NUMERIC 字段与必要索引。
- [x] 以同一 RediSearch ACL 谓词执行 KNN 与文本候选查询，使用 Core RRF。
- [x] 补齐写入、更新、删除对文本和 ACL 索引的同步维护。
- [x] E2E：Redis Stack 实例、ACL negative test、双路去重、索引重建后的回归。

### 5.3 VikingDB（P1）

- [x] 使用 Atlas Richie AI 的原生 DENSE/SPARSE/HYBRID 请求，映射 sparse 字段与 `SparseQueryVectorizer`。
- [x] 验证 VikingDB filter 可同时附着 dense 与 sparse 分支；否则使用两次受控过滤召回加 Core RRF。
- [x] 将云端细粒度 IAM 权限错误转换为组件统一错误，并给出所缺 action/FullAccess 提示。
- [x] E2E：真实项目、Collection、Index、写入、ACL hybrid、权限拒绝与异步 index-ready 重试（用户确认既有验证已完成，本轮不重复执行）。

### 5.4 PGVector（P2）

- [x] 增加 opt-in `tsvector` 文本列、GIN 索引及 ACL 字段索引；保留既有 pgvector 表兼容迁移方案。
- [x] 在同一事务内维护 embedding 与 `tsvector`，分别执行带相同 SQL ACL `WHERE` 的候选查询。
- [x] 使用 Core RRF，明确 SQL 参数绑定，禁止拼接 ACL DSL。
- [x] E2E：PostgreSQL+pgvector 本地 Compose、租户隔离、全文命中、向量命中、迁移与回滚兼容。

### 5.5 MongoDB Atlas（P2）

- [x] 定义 opt-in Atlas Vector Search 与 Atlas Search 文本索引，统一 ACL filter 映射。
- [x] 以 `$vectorSearch` 和 `$search` 的 filter 各自召回候选，Core RRF 合并；不支持的部署等级明确报能力缺失。
- [x] E2E：Atlas 或兼容受控环境，验证索引就绪、ACL 负例、错误分类与删除一致性。

### 5.6 Neo4j（P2）

- [x] 定义 vector index、fulltext index 和 ACL 属性索引；提供版本前置校验。
- [x] 在两个 Cypher 候选查询中使用相同参数化 ACL 谓词，再由 Core RRF 合并。
- [x] E2E：Neo4j 本地实例、节点权限负例、事务写入一致性、索引 online 等待。

## 6. 新 Provider 实施任务

### 6.1 DashVector（P1）

- [x] 完成 `atlas-richie-vector-dashvector`：连接 Factory、Store Factory、命名 Store 隔离、自动配置、Provider README 中英文版。
- [x] 公开 AI 插件的 collection/document/partition/search typed capability，不让 SDK 类型泄漏到 Core。
- [x] 完成 dense 基础 CRUD、原生 filter、ACL filter、观测、连接关闭及能力声明。
- [x] sparse 配置后：使用单次 `QueryDocRequest` dense+sparse+filter 原生 hybrid；当前 SDK 路径不把非能力错误伪装为 Core 回退。
- [x] E2E：真实 DashVector collection、schema、dense、sparse、ACL 越权、原生 hybrid、同连接并存 Store、删除与清理（DashVector 路径没有 Core fallback，能力错误不降级）。

### 6.2 Tencent VectorDB（P1）

- [x] 完成 `atlas-richie-vector-tencent-vectordb`：连接 Factory、Store Factory、database/collection 隔离、自动配置、Provider README 中英文版。
- [x] 公开 AI 插件的 database/collection/document/search/index typed capability，不让 SDK 类型泄漏到 Core。
- [x] 完成 dense 基础 CRUD、原生 filter、ACL filter、观测、连接关闭及能力声明。
- [x] sparse 配置后：使用 `HybridSearchParam(ann, match, filter)` 原生优先；仅能力不支持时以相同 filter 的双路请求加 Core RRF。
- [ ] E2E：真实实例、Collection/Index、dense+sparse、ACL 负例、原生 hybrid、删除、重连与清理；Core RRF fallback 与非能力错误不降级由 Provider 契约测试覆盖，真实实例待提供连接条件。

## 7. P3：全局回归与验收

- [x] 更新 Provider capability matrix：原生、Core fallback、未配置、不可支持四种状态。
- [x] 多 Store E2E：同一 Spring Context 并存 Milvus 与 PGVector；已验证连接、schema、写入/检索、调优参数与 Store 级观测互不串扰。ACL 隔离由两个 Provider 的独立真实 ACL-safe hybrid E2E 共同覆盖。
- [x] 所有 Provider 的 contract test：能力声明、接口注册、filter 编译、异常分类、无 sparse 配置的兼容行为；2026-09-10 已覆盖 Core、Milvus、Qdrant、Redis、PGVector、MongoDB Atlas、Neo4j、VikingDB、DashVector、腾讯云 VectorDB。
- [ ] 所有可测试 Provider 的真实 E2E 通过；无法测试者记录端点、版本、凭据/权限或部署条件，不勾选。
- [x] 更新根 README 和各 Provider 中英文 README：配置、默认行为、能力前置条件、降级语义、限制和 E2E 证据；未具备真实环境的 Provider 明确保留未验证状态。

### 当前真实 E2E 运行条件（2026-09-10）

| Provider | 已具备的自动化用例 | 当前阻塞 | 重新执行所需环境变量 | 结论 |
|---|---|---|---|---|
| VikingDB | 既有 VikingDB 验证记录 | 用户确认已完成验证，本轮不重复执行 | 不适用 | 本轮不以华东 2 实例开通状态覆盖既有验证结论 |
| DashVector | `DashVectorAclSafeHybridLiveIT` | 无 | `VECTOR_DASHVECTOR_IT_RUN=true`、`DASHVECTOR_ENDPOINT`、`DASHVECTOR_API_KEY` | 2026-09-10 已证实服务版本、两个同连接 collection 的 schema、dense+sparse 写入、原生 ACL 越权负例、跨 Store 隔离、删除及 collection 清理；该 Provider 不使用 Core fallback |
| MongoDB Atlas Local | `MongoDbAtlasAclSafeHybridLiveIT` | 无 | `VECTOR_MONGODB_IT_RUN=true`、`VECTOR_MONGODB_URI`、`VECTOR_MONGODB_DATABASE` | 2026-09-10 已证实 vector/text 索引创建、嵌套 ACL token 映射、dense/text 双路召回、ACL 越权负例、删除和 collection 清理；测试容器已停止 |
| Milvus + PGVector | `MilvusPostgresqlLiveCoexistenceTest` | 无 | `VECTOR_IT_RUN=true`、Milvus/PGVector 本地连接参数 | 2026-09-10 已证实同一 Spring Context 同时装配两个命名 Store、独立 schema/collection、写入与检索不串数据、调优参数与 Store 级观测事件隔离、正文不进入 trace 属性及资源清理；测试容器已停止 |
| 腾讯云 VectorDB | `TencentVectorDbAclSafeHybridLiveIT` | 本机 compose 目录没有可用服务，当前进程也未注入云端连接信息 | `VECTOR_TENCENT_VECTORDB_IT_RUN=true`、`TENCENT_VECTORDB_URL`、`TENCENT_VECTORDB_USERNAME`、`TENCENT_VECTORDB_API_KEY` | 用例覆盖 native-first、受控 Core RRF、ACL 负例和清理；当前跳过 |

密钥只应通过终端会话、CI Secret 或密钥管理服务注入。不得把端点凭据写入源码、默认 YAML、README 或此清单；没有运行时条件时保留复选框未完成。

## 8. E2E 最小验收矩阵

每个实现 `ACL_SAFE_HYBRID` 的 Provider 必须以独立、可清理的 Store/Collection/Table 执行：

1. 创建 dense + sparse/text + ACL schema，并等待索引 ready。
2. 写入允许和拒绝租户各至少两条数据；拒绝数据应在某一分支中具有更高相关性。
3. 发起 hybrid 请求，断言返回仅属于允许 ACL，且 dense-only、sparse-only、双路重叠候选的排序符合权重。
4. 模拟或配置原生 hybrid 不可用，断言仅触发允许的 Core 回退且结果仍无越权数据。
5. 验证认证/权限、网络或超时错误不回退、不泄露凭据，并保留 Provider 错误分类。
6. 删除一条记录后再次检索，断言 dense/sparse 两路均无残留。
7. 关闭 Store/连接并重新打开，断言命名 Store 隔离与资源释放正常。

## 9. 逐项实施卡（执行顺序）

每个复选框都对应一个可独立评审和验证的改动；只有代码、对应自动化测试和要求的真实 E2E 都完成后才能勾选。

### P0-A：Core 契约与保护（当前执行）

- [x] P0-A01：新增不可变 `SparseVector` 值对象；位置：`vector-core/.../service/SparseVector.java`；验证非法 token、NaN/Infinity、空向量。
- [x] P0-A02：新增 `SparseVectorizer` 最小契约；位置：`vector-core/.../service/SparseVectorizer.java`；普通 dense Store 不要求实现。
- [x] P0-A03：新增 Store-level `HybridStoreOptions`；字段：enabled、denseField、sparseField、vectorizerBeanName、candidateK、fallbackMode。
- [x] P0-A04：拓扑启动期解析并校验已声明的 `vectorizerBeanName`；字段冲突、未知 Bean、candidateK 小于 topK 必须失败。原生全文第二路无需编码器。
- [x] P0-A05：`VectorStoreHandle` 校验 `ACL_SAFE_HYBRID` 与 `VectorAclAwareHybridSearchOperations` 同时存在。
- [x] P0-A06：观测事件记录 execution=`native|core-rrf`，但不记录查询文本、ACL 内容或密钥。
- [x] P0-A07：Core 契约测试覆盖 native 成功、可分类 unsupported 回退、权限/超时不回退、ACL 候选隔离。

### P1 每个 Provider 的固定子任务模板（说明，非待办）

- PX-01：定义 opt-in schema 和版本/索引前置条件；不改已有 dense-only Store。
- PX-02：实现写入、更新、删除的 dense+sparse 原子一致性或可恢复补偿。
- PX-03：实现相同 ACL Filter 的 dense 与 sparse 候选查询；禁止后置 ACL。
- PX-04：接入 `AclSafeHybridSearchFallback`；native 分支仅捕获 Provider 明确的 unsupported 分类。
- PX-05：Factory 只在 PX-01 至 PX-04 的配置均有效时注册操作并声明 capability。
- PX-06：Provider 单元/契约测试：配置、能力、请求过滤、异常分类、普通 dense 兼容。
- PX-07：Provider 真实 E2E：创建、写入、ACL 负例、native/fallback、删除、重开连接、清理。

Provider 实施时按该模板分别替换为 `QD`、`REDIS`、`VIKING`、`DASH`、`TENCENT`、`PG`、`MONGO`、`NEO4J`，并在每项标题后补充实际文件和 E2E 运行编号。
