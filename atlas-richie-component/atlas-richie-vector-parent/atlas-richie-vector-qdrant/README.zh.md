# Qdrant Provider

Qdrant hybrid 为按需能力。hybrid Store 会创建命名 dense/sparse 向量及 ACL payload 索引；同一次 upsert 为同一 point 写入两种向量，按 ID 删除也同时移除两者。

`VectorAclAwareHybridSearchOperations` 分别执行 dense 与 sparse 两次 Qdrant 召回，将同一个结构化 `VectorFilter` 转为 gRPC Filter 并在候选召回前附着到两路请求，随后由 Core 做加权 RRF。因此声明的是 `ACL_SAFE_HYBRID`、`execution=core-rrf`，不是服务端原生融合。

需开启 `hybrid-enabled`、配置 `sparse-vectorizer` 并声明 ACL payload 字段/索引。默认 dense-only 行为不变。缺少 ACL Filter 会明确失败，禁止大范围召回后过滤。本地 Qdrant E2E 已验证写入、ACL 负例、融合和删除，测试为 `QdrantAclSafeHybridLiveIT`。
