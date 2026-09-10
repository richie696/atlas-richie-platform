# Redis Stack Provider

Redis Stack hybrid 使用 RediSearch dense KNN 与全文检索两路召回。两路请求在候选产生前都附带同一份编译后的 TAG/NUMERIC ACL 谓词，Core 仅对已授权候选做加权 RRF。

第二路是原生全文索引，因此不需要 sparse 编码器。`hybrid-enabled` 为按需开启，dense-only Store 的 schema 和行为保持不变。文档映射在写入/更新/删除时同步 content、ACL 字段和 dense 向量；缺失 ACL 会拒绝。本地 Redis Stack E2E 已验证 ACL 负例、候选去重和删除（`RedisAclSafeHybridLiveIT`）。
