# PostgreSQL / pgvector Provider

hybrid 为可选能力：在 pgvector embedding 旁增加生成式 `tsvector` 列与 GIN 索引。Provider 对 dense KNN 和全文候选 SQL 使用同一份 JSONB ACL `WHERE` 参数谓词，再由 Core 加权 RRF；ACL 值均为绑定参数，禁止拼接 DSL。

未显式开启 hybrid 的既有 dense 表保持兼容；文本/ACL 索引仅为该 Store 建立。第二路由 PostgreSQL 全文索引产生，因此不需要 sparse 编码器。本地 pgvector E2E 已验证租户隔离、全文/向量候选及删除（`PostgresqlAclSafeHybridLiveIT`）。
