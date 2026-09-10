# DashVector Provider

`atlas-richie-vector-dashvector` 是可选的命名 Store Provider：一个平台 Store 对应一个 DashVector collection；一个连接可服务多个彼此隔离的 Store。未配置 DashVector 的项目不会创建 Client，也不需要 sparse 编码器。

## 能力与安全边界

| 能力 | 默认 dense Store | `hybrid-enabled: true` |
|---|---:|---:|
| dense 写入/检索 | 支持 | 支持 |
| 类型化 Provider Filter / ACL Filter | 支持 | 支持 |
| `ACL_SAFE_HYBRID` | 不声明 | 配置校验通过后声明 |
| 执行方式 | 不适用 | DashVector 原生 dense+sparse 请求 |

hybrid 写入会对同一文本生成 dense embedding 与 `SparseVectorizer` 产生的 sparse 向量，并在**同一次 DashVector upsert**中写入。按 ID 删除的是同一文档。若 Store 只能写 dense 向量，就不会被允许声明 hybrid 能力。

每一次 hybrid 查询都必须传入结构化 `VectorFilter`。该 Filter 被编译后随 DashVector 请求在候选召回前下推；缺少 ACL Filter 的重载会被拒绝，绝不以 JVM 后置过滤代替鉴权。

## 配置要点

连接参数为 `endpoint`、`api-key`、可选 `timeout-seconds`。索引额外参数包括 `initialize-schema`、`partition`、`metadata-fields`，以及可选的 `hybrid-enabled`、`sparse-vectorizer`、`hybrid-candidate-limit`、`hybrid-fallback-mode`。

`hybrid-enabled` 默认 `false`，已有 dense-only Store 的建表、写入、检索和启动行为不变。DashVector 的 `sparse_vector` 仅支持 collection metric 为 `dotproduct`，因此 hybrid Store 必须配置 `metric: ip`（或 `dot`）。当前 SDK 绑定使用 DashVector 根 `vector` / `sparse_vector` 表示，`dense-field`、`sparse-field` 不能改名，避免配置看似生效但实际被忽略。ACL 使用到的 metadata 字段必须写入 `metadata-fields`；凭据只能由密钥环境注入。

```text
VectorRecord -> embedding + SparseVectorizer -> 单次 upsert(dense,sparse,metadata)
查询 + 强制 VectorFilter -> 原生 dense+sparse 请求（召回前下推 filter）
                         -> 只返回授权候选
```

## 验证状态

Factory 配置、保留字段写入映射与 capability 注册测试已通过。真实云端 E2E 受 `VECTOR_DASHVECTOR_IT_RUN=true`、`DASHVECTOR_ENDPOINT`、`DASHVECTOR_API_KEY` 控制；2026-09-10 已针对 DashVector `2.4.12.3-20-gb6fd1a8f` 验证同一连接下两个 collection 的 schema 创建、dense+sparse 写入、原生 ACL 越权负例、跨 Store 隔离、删除与 collection 清理。DashVector 路径仅走原生 hybrid；原生能力不支持时会明确报错，不会静默降级成不安全的回退。未提供环境变量时用例跳过，不能用 Factory 测试替代云端验证。
