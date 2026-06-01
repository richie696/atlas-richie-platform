# INIT_REPORT — `.harness/` 初始化报告

> 由 general agent 生成于 2026-06-01（分支：`feature/init-harness-bootstrap`）。
> **未提交** — 留在工作树中等待 `richie696` 审核。

## 产物清单

```
.harness/
├── agent.md                                   # 编排器（Harness 路由大脑）
├── INIT_REPORT.md                             # 本文件
├── docs/                                      # 项目上下文文档（供 reins 使用）
│   ├── project-overview.md
│   ├── module-ownership.md
│   ├── code-standards.md
│   ├── testing-policy.md
│   ├── release-workflow.md
│   ├── ai-component-context.md
│   ├── migration-window-rules.md
│   └── open-source-discipline.md
├── reins/                                     # 5 个项目 reins
│   ├── platform-developer/agent.md
│   ├── ai-component-expert/agent.md
│   ├── component-tester/agent.md
│   ├── code-reviewer/agent.md
│   └── docs-template-author/agent.md
└── memory/
    └── MEMORY.md                              # 团队共享记忆（占位）
```

共 16 个文件：1 个编排器 + 5 个 reins + 8 个文档 + 1 个报告 + 1 个记忆文件。

另外还有一项对 `.gitignore` 的修改（排除每个 rein 下的自动生成 `opencode/` 运行时目录，见下方"运行时产物"）。

## 团队设计

编排器 + 5 个 reins，共 6 个 agent，在 3-7 的指导范围内，没有多余的占位。

| Agent | 角色 | 负责范围 |
|---|---|---|
| `atlas-richie-platform-harness`（编排器） | 路由大脑；简单的项目 Q&A 直接处理，实质性工作委派 | `.harness/**` |
| `platform-developer` | 非 AI 模块、网关、base 的通用开发 | `atlas-richie-base/**`、所有 `atlas-richie-component/**`（AI 除外）、`atlas-richie-gateway-service/**` |
| `ai-component-expert` | AI 模块和 sample-ai 的唯一 owner | `atlas-richie-component-ai/**`、`sample-ai/**`、Spring AI / AgentScope / Spring AI Alibaba 版本属性 |
| `component-tester` | 补测试覆盖率缺口 | 所有 `**/src/test/**`、surefire / failsafe / jacoco 配置 |
| `code-reviewer` | 按用户的设计规则做质量门 | Diff 审核（不编辑代码） |
| `docs-template-author` | README、docs/、示例应用、语言规范 | `README*.md`、`docs/**`、`atlas-richie-component-template/**` |

### 为什么这样设计

保留了默认的 `developer` / `tester` / `code-reviewer` 三人组，然后针对项目实际热点增加了两个专精：

1. **AI 专精** — 最近 8 次提交中有 6 次涉及这个模块。单独拆出来可以让平台其他工作的上下文更专注。
2. **文档/模板专精** — 公共 1.0.0-SNAPSHOT 版本对外的用户面（README、samples）需要一个专职 owner 来防止文档漂移和 sample 腐化（CI 目前将 template 排除在构建之外）。

### 刻意没有加入的内容

- **没有单独的 `storage-expert` / `vector-expert` / `messaging-expert`**：这些都是多实现的库族；`platform-developer` rein 负责整个族，core / provider 分层写在 `module-ownership.md` 里。
- **没有 `gateway-expert`**：只有 1 个模块，不值得单独一个 rein。
- **没有 `devops-expert`**：dependabot 处理版本依赖更新；发布是单一流程（一个 rein 主导）。
- **没有 `security-expert`**：SECURITY.md 由 `docs-template-author` 负责；安全相关代码（MFA 等）放在 `platform-developer` 下，测试覆盖率标准一致。

## 关键决策

### 1. `pom.xml` 是版本数据的唯一真相

任务描述和 README 都写的是 `Spring Boot 4.0.6`（README.zh.md 里甚至是 `4.0.5`）。实际的 `pom.xml` 写的是 `4.1.0-RC1`。这与用户的规则一致：**始终以构建清单为准，而非文档**。`project-overview.md`、`code-reviewer` 规则 9、`platform-developer` 的 prompt 都强化了这一点。

### 2. `MigrationWindow` 被编纂为规则，而非行为

用户最近给项目加了 `@MigrationWindow`。`migration-window-rules.md` 文档记录了用户正在强制执行的规范。`code-reviewer` 规则 1 在每个 diff 上强制执行。`platform-developer` prompt 在任何新配置开关时引用它。

### 3. 多实现接口边界是硬性审核规则

存储（12 个实现）、向量（8 个实现）、消息（多个）都是多 provider 族。风险在于 provider 特定类型泄露进 `core/`。`code-reviewer` 规则 5 强制执行。`code-standards.md` 文档覆盖了分层模式。

### 4. Apache 2.0 公共发布规范在编排器的热点路径上

用户明确拒绝营销语言。`open-source-discipline.md` 文档和 `code-reviewer` 规则 6/7 将规则编纂成文。`docs-template-author` prompt 是主要撰写者；其他人对任何文档变更都受 `code-reviewer` 把关。

### 5. 测试明确是覆盖率趋势工程，不是英雄主义

31 个测试文件 / 891 个 Java 文件（3.5%），`component-tester` prompt 和 `testing-policy.md` 文档都推动增量、按爆炸半径优先级排序的覆盖率建设。不是"一次 PR 从 3.5% 干到 80%"。

## 对项目的观察

- **技术栈非常前沿**：JDK 25 + Spring Boot 4.1.0-RC1 + Spring AI 2.0.0-M8。用户明确运行 SNAPSHOT 约 8 个月后才升到 RELEASE。这影响版本依赖更新的风险承受度。
- **CI 测试门禁缺失**：`.github/workflows/ci.yml` 运行 `mvn verify -DskipTests`。jacoco 50% 最低覆盖率已定义但未绑定到 `verify`，实际上不生效。补上这个缺口对 `component-tester` 是一个真实的机会。
- **dependabot 活跃度高**：约每周一个 PR 做依赖更新。`release-workflow.md` 文档覆盖了更新协议。
- **两个活跃分支**：`master`（SNAPSHOT 主线，快速迭代）和 `feature/migration-window-guard`（进行中的 worktree，本次 bootstrap 未触碰）。
- **公共版与内部版差距**：README 和很多文档把 1.0.0-SNAPSHOT 作为"首个公共版本"——重要的是不能泄露内部 5.x 的上下文。`open-source-discipline.md` 文档是关卡。
- **测试分布不均**：statemachine (12)、desensitize (10)、ai (4)、vector (3)、gateway (2)、rest (0)。流量最大的模块（cache、storage、mfa）没有测试——这是一个真实的机会。

## 未完成事项（及原因）

- **未提交**。按任务要求：用户审核后提交。
- **未创建 `.harness/hooks/` 或 `.harness/crons/`**。项目没有已建立的 hook/cron 需求场景；有了具体用例再加。
- **bootstrap 本身未写 CHANGELOG 条目**。`.harness/` 的加入是项目元数据，不是用户可见的行为变更。如果用户想要，可以在审核时加一条 "chore: bootstrap .harness"。
- **任何 rein 的 `skills/` 目录下没有 skill 文件**。Reins 继承全局 skill 集合；目前没有识别出 rein 专属 skill。
- **任何 rein 没有 `config.yaml`**。默认模型目前够用；如果特定模型更适合某个角色，可以后续对单个 rein 做特化。
- **没有 `PERSONA.md`**。用户是资深开发者，偏爱简洁的技术指导；没有 agent 需要特定人设。

## 运行时产物（一次性守护进程注册）

写完文件后，我在项目根目录运行了 `mavis harness mount "$(pwd)"` 使 reins 可被发现。守护进程：

- 注册了一个名为 `users-richie696-projects-workspace-atlas-richie-platform` 的 harness（显示名为 `atlas-richie-platform-harness`）
- 在其 SQLite 数据库中自动注册了全部 5 个 reins，名称如 `users-richie696-projects-workspace-atlas-richie-platform--platform-developer`（harness 名称 + `--` 分隔符 + rein 名称）
- 在 `~/.mavis/agents/` 下创建了指向 `<repo>/.harness/reins/<name>/` 的符号链接
- 首次 spawn agent 时，守护进程自动生成 `<repo>/.harness/reins/<name>/opencode/`，包含 `opencode.json`、`package.json`、`node_modules/`、`plugins/`、`tools/` —— 这是**机器本地的运行时状态**，通过 `.gitignore` 中的新规则 `.harness/reins/*/opencode/` 排除在 git 之外。需要提交的只有 `agent.md` 文件。

**提交后验证**：
```bash
mavis harness list
mavis agent list --project "$(pwd)" -H
```

如果 reins 不可见，在项目根目录重新运行 `mavis harness mount "$(pwd)"`。

## 后续扩展方式

- **增加一个 rein**：加载 `create-agent` skill，按 `target=project` 和项目路径执行。
- **增加一个文档**：新建 `.harness/docs/<topic>.md`，从相关 rein 的 prompt 链接它。
- **增加一个 hook**：加载 `mavis` skill，按 `references/hook.md` 执行，用 `mavis hook create`。
- **调整团队**：本次 bootstrap 末尾的产出概要是用户否决任何 rein 的机会。审核通过后，后续扩展应该是响应式的（出现缺口时再加 rein——不是提前预设）。

## 验证 bootstrap

提交后，reins 应该可以被发现为项目 agent：

```bash
mavis agent list --project /Users/richie696/Projects/workspace/atlas-richie-platform --human
```

每个 rein 的 `mavis agent info <name>` 应该打印出其 prompt。

---

**状态：准备就绪，等待审核。未提交。工作树中只有 `.harness/`（未跟踪）。**
