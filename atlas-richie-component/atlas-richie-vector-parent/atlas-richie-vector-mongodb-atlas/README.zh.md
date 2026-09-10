# MongoDB Atlas Provider

可选 hybrid Store 同时使用 Atlas `$vectorSearch` 与 Atlas Search `$search`。Provider 为两个 pipeline 编译同一份结构化 ACL filter，未授权文档无法进入 dense 或全文候选，之后才由 Core 加权 RRF 合并。

hybrid 需要 vector-search 索引、Atlas Search 文本索引和可过滤 ACL metadata 字段；Provider 会将每个声明的 ACL metadata 字段映射为嵌套 Atlas Search `token` 字段，使全文分支可以执行与向量分支相同的等值/集合 ACL 谓词。dense-only 配置不受影响。Atlas Local E2E 已验证索引就绪、ACL 负例与删除（`MongoDbAtlasAclSafeHybridLiveIT`）。生产环境仍需具备 Atlas Search/vector-search 服务和相应索引权限。
