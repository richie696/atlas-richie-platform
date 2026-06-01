# 模块归属

哪个 rein 负责哪个路径。如果任务涉及的路径不在列表里，默认用 `platform-developer`，让他们分包。

## 硬性归属

| 路径 | 归属 |
|---|---|
| `.harness/**`（本目录） | Harness 编排器 + 用户 |
| `atlas-richie-base/atlas-richie-dependencies/**` | `platform-developer`（BOM 变更需要 release note） |
| `atlas-richie-base/atlas-richie-context/**` | `platform-developer` |
| `atlas-richie-base/atlas-richie-contract/**` | `platform-developer` |
| `atlas-richie-component/atlas-richie-component-ai/**` | `ai-component-expert` |
| `atlas-richie-component-template/sample-ai/**` | `ai-component-expert` |
| `atlas-richie-gateway-service/**` | `platform-developer` |
| `atlas-richie-component/atlas-richie-component-cache/**` | `platform-developer` |
| `atlas-richie-component/atlas-richie-component-storage/**`（全部 12 个实现） | `platform-developer` |
| `atlas-richie-component/atlas-richie-component-vector/**`（全部 8 个实现） | `platform-developer` |
| `atlas-richie-component/atlas-richie-component-messaging/**` | `platform-developer` |
| 所有其他 `atlas-richie-component/atlas-richie-component-*/**` | `platform-developer` |
| `**/src/test/**`（任何模块） | `component-tester` |
| `**/src/main/**` 测试基础设施 / `*TestSupport.java` | `component-tester` |
| `README*.md`、`CONTRIBUTING*.md`、`CHANGELOG*.md`、`CODE_OF_CONDUCT*.md`、`SECURITY*.md`、`NOTICE`、`LICENSE` | `docs-template-author` |
| `docs/**`（项目文档树） | `docs-template-author` |
| `atlas-richie-component-template/**`（所有示例应用） | `docs-template-author` |
| `atlas-richie-component/<name>/README.md`（各组件 README） | `docs-template-author` |
| `*.iml`、`.idea/**`、`.vscode/**`、`.opencode/**` | 忽略（本地开发配置） |
| `.worktrees/**` | 忽略（其他进行中的 worktree） |

## 软性重叠——默认选谁

| 场景 | 默认 | 原因 |
|---|---|---|
| 给非 AI 组件加配置开关 | `platform-developer` | 住在 main src 里，即使涉及 `@MigrationWindow` 也一样 |
| 给非 AI 组件写测试 | `component-tester` | 测试路径是他们的，即使组件本身不是 |
| 更新各组件 README 以匹配新 API | `docs-template-author` | 但要请生成的 rein 标记出需要更新文档 |
| 重构多实现族（如 `*-storage-*`） | `platform-developer` | 负责整个族；会经过 `code-reviewer` 审核 |
| 新增 sample-ai 来展示新 AI 特性 | `ai-component-expert` | 他们负责 sample-ai 范围；其他 sample 由 `docs-template-author` 负责 |
| 行为变更的 CHANGELOG 条目 | 生成的 rein | 谁改了行为谁写条目；`docs-template-author` 审核镜像同步 |
| 涉及两个不同组件 main src 的 diff | 主要变更的生成 rein 主导，次要的分包 | 不要双重编辑；一个 rein 主责，另一个审核 |

## "负责"的含义

- **负责的 rein 是该路径的主要实现者**。
- **其他 reins 可以自由阅读**——不需要许可就可以 grep / 理解代码。
- **其他 reins 可以请求分包**——如果收到的任务明显属于另一个 rein，应在一次往返内分包，不拆分工作。
- **跨切变更**（影响 3 个模块的迁移）——由 harness 根据最重模块选主导 rein，其他的分包范围。
