# 腾讯云 VectorDB Provider

该可选 Provider 将一个命名 Store 绑定到一个腾讯云 VectorDB database/collection。连接句柄负责 Client 与凭据脱敏；Core 不暴露腾讯 SDK 类型。

## ACL-safe hybrid 工作方式

默认仍是 dense-only。只有 `hybrid-enabled: true` 且配置命名 `SparseVectorizer` 时，组件才创建/校验同时含有 `vector` 和 `sparse_vector` 的 collection，在一次 upsert 中写入两种向量，并注册 `VectorAclAwareHybridSearchOperations` 与 `ACL_SAFE_HYBRID`。

请求严格按以下规则执行：

1. 构造包含 `AnnOption`、`MatchOption` 和同一个强制 ACL filter 的 `HybridSearchParam`。
2. 原生 hybrid 成功时直接使用服务端结果。
3. 仅当腾讯明确返回 hybrid/rerank 不支持，且显式设置 `hybrid-fallback-mode: core-rrf`，才对 dense 与 sparse 分别以相同 Filter 召回，再由 Core 做加权 RRF。
4. 鉴权、IAM、配额、超时、网络和普通数据错误直接失败，绝不降级。

因此未授权文档无法进入任一路候选集，也无法参与融合排序。

## 配置要点

连接使用 `url`、`username`、`api-key`，可选 `timeout-seconds`、`connect-timeout-seconds`、`read-consistency`。hybrid 索引使用 `database-name`、`initialize-schema`、`hybrid-enabled`、`sparse-vectorizer`、`hybrid-candidate-limit`、`hybrid-fallback-mode`。

腾讯 SDK 的 schema 固定使用 `vector`、`sparse_vector`，字段别名会被明确拒绝。`initialize-schema=true` 会创建 database/collection 的主键、content/filter、dense、sparse 倒排与 filter-all 能力。既有 dense collection 不做原地迁移；应新建 hybrid collection 并重新写入。

## 验证状态

Factory 测试已覆盖 opt-in capability、编码器缺失和字段别名拒绝。真实 E2E 仍受 `VECTOR_TENCENT_VECTORDB_IT_RUN=true`、URL、用户名和 API Key 控制；需要验证建表就绪、原子写入、ACL 负例、原生路径、分类回退、删除、重连与清理，完成前不得标记为云端通过。
