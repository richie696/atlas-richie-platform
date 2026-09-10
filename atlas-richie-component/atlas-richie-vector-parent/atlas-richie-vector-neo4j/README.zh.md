# Neo4j Provider

Neo4j 的 vector/full-text procedure 对这一用例没有通用、安全的 Filter 下推契约。因此 Provider 对 dense 相似度与文本匹配都执行 ACL-first 的参数化 Cypher 候选查询，再由 Core 做加权 RRF；这里刻意优先授权正确性，而不是走无法证明安全的 procedure 快捷路径。

`hybrid-enabled` 创建/校验 vector、full-text 和 ACL 属性索引，dense-only Store 不变。查询必须显式提供结构化 ACL，禁止召回后过滤。本地 Neo4j E2E 已验证拒绝节点隔离和删除（`Neo4jAclSafeHybridLiveIT`）。
