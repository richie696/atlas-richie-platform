# 开源规范

公共 1.0.0-SNAPSHOT 版本从内部 5.0.0-SNAPSHOT 派生，已清除业务代码。这是项目的对外脸面，语言和内容选择是经过深思熟虑的。

## 不可妥协的规则

### 1. 代码自证；不营销

用户明确拒绝营销文案。立场：真实用户看代码；看代码的人自然懂。不要加那种试图说服不看代码的人的 callout。

**不要写**：
- "经过生产验证！" / "经过实战！" / "生产级！" / "企业级！" / "高性能！"
- "保证 0-FullGC！" / "P99 < 10ms！"——没有测量上下文的性能数字
- "被 [X 公司] 信任" 部分
- "Sponsor" / "Backed by" / "Powered by" 部分
- README 里营销风格的徽章
- Javadoc 里的宣传语言

**要写**：
- 实事求是地描述代码做了什么
- 引用版本、Spring Boot 版本、JDK 版本
- 链接到文档 / 示例 / 源码，让用户可以自行验证
- 语气中立、信息翔实

### 2. 公共 1.0 清理规则

审核或写代码时，警惕不属于公共版本的内部专有上下文：

- ❌ POS、KDS、美团 / 淘宝 / 京东 / 饿了么引用
- ❌ 自营小程序代码
- ❌ 内部业务领域模型（如 `OrderFlow`、`KdsTicket`、`StoreShift`）
- ❌ 内部团队约定（"我们团队用 X"、"我们统一用 Y"并有团队特定理由）
- ❌ 内部 5.x 特定代码路径的引用
- ❌ 真实客户名、内部基础设施 URL、真实密钥 / API key（即使已轮换）
- ❌ 内部监控 / 告警 / on-call 轮班引用

在公共版本中发现以上任何内容，**是 PR 的 blocker**。作为 `code-reviewer` 规则违规上报。

### 3. 仓库命名（坑）

- GitHub 仓库是 `richie696/richie-platform`（按用户 profile）
- Maven artifact 是 `atlas-richie-platform`（项目名）
- pom `<scm>` URL 是 `git@github.com:richie696/atlas-richie-platform.git`
- pom 里的不一致是**故意的**。不要"修正" pom 来匹配 GitHub 仓库名。pom 对 Maven 是规范；GitHub URL 对 git 是规范。

### 4. 英文优先，中文镜像

- 默认 README 是英文（`README.md` 而不是 `README.zh.md`）
- 中文是翻译镜像（`README.zh.md`）
- 默认语言在 Apache-2.0 迁移期间改成了英文；这是对公共开源友好的有意选择
- 更新文档时先更新英文，再镜像到中文。不要让两者分叉。

### 5. Apache 2.0 + NOTICE

- `LICENSE` 是 Apache 2.0
- `NOTICE` 是必需的（Apache 2.0 § 4d）——列出任何需要的归属
- 新文件通过 `mvn license:update-file-header` 获得 Apache 2.0 license header
- 不要切换到其他 license。不要加 `LICENSE-THIRD-PARTY` 等，除非先暴露决策。

### 6. CHANGELOG 规范

- CHANGELOG 记录**影响用户的行为变更**
- 内部重构无用户可见影响 → 不进 CHANGELOG
- Deprecation / 迁移窗口 → 进 CHANGELOG，挂在 "Deprecation" 下
- 用户会注意到的 bug 修复 → 进 CHANGELOG
- "chore: 清理" / "chore: 错字" → 不进 CHANGELOG

## 实践中长什么样

### README 开篇（好）

> Atlas Richie Platform is an enterprise Java tech middle platform that provides a unified component library and best practices for building microservices. It abstracts storage, vector, messaging, and AI capabilities behind stable interfaces, so business code depends on abstractions, not SDKs.

### README 开篇（差——营销腔）

> Atlas Richie Platform is the **production-grade**, **battle-tested** tech middle platform **trusted by leading restaurant chains** to power their **mission-critical** operations with **0-FullGC** guarantees and **sub-millisecond** latency!

好版本是 README 实际写的。差版本是我们不写的。

### 组件 README（好）

> ## Cache Component
>
> Unified Redis API for KV, Hash, List, Set, ZSet, distributed locks, and Redis Streams. Multi-tier guards (`redis/perf/`) inspect payload size and command complexity at runtime.

### 组件 README（差）

> ## Cache Component
>
> **The fastest, most reliable Redis client for Spring Boot!** Used in production by **major enterprise customers**, with **99.999% uptime** and **microsecond-level latency**! Our **proprietary** payload inspector catches bugs before they happen!

不要写差版本。

## 谁执行

- `code-reviewer` 是门禁。code-reviewer 规则目录里的规则 6 覆盖了文档规范角度。
- `docs-template-author` 是主要撰写者。他们了解本文档。
- 在 PR 里提议营销文案的会被礼貌地引导到这里。

## 拿不准时

如果文档变更处于边界（"这是营销还是有用的事实？"），默认少写。用户随时可以加；进了 README 就不容易删了。
