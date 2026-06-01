# 发布流程

## 版本模型

- `1.0.0-SNAPSHOT` 是公共发布线
- 内部走另一条线（`5.0.0-SNAPSHOT`）——公共版本是从它派生的
- 根 `pom.xml` 里的 `${revision}` 占位符是版本唯一真相
- `flatten-maven-plugin`（OSS 模式）在构建时展开 `${revision}`，所有子 POM 看到的是解析后的版本

## 版本更新

**根 `pom.xml` 里的 properties 是版本钉住的唯一地方**：

- `revision` — 项目版本
- `spring-boot-dependencies.version` — Spring Boot BOM
- `spring-cloud-dependencies.version` — Spring Cloud BOM
- `spring-cloud-alibaba-dependencies.version`
- `spring-cloud-azure-dependencies.version`
- `spring-ai.version`（由 `ai-component-expert` 负责）
- `spring-ai-alibaba.version`（由 `ai-component-expert` 负责）
- `agentscope.version`（由 `ai-component-expert` 负责）
- `lombok.version`、`mapstruct.version`
- 所有 Maven plugin 版本（enforcer、surefire、failsafe、checkstyle、spotbugs、pmd、jacoco……）

**更新步骤**：
1. 在根 `pom.xml` 里更新 properties——不要编辑子 POM 的 `<parent>` / `<version>` 块
2. 运行 `mvn -B clean verify -DskipTests -pl '!atlas-richie-component-template' -am` 确认解析正常
3. 更新 README 徽章（项目用 shields.io）
4. 如果是大版本有 breaking API 变更，按 `.harness/docs/migration-window-rules.md` 执行迁移窗口

## Snapshot vs Release

- 项目两边都用阿里云 Maven：snapshot 仓库（`2149740-snapshot-ZYP0VW`）和 release 仓库（`2149740-release-cGAgN8`）
- `mvn deploy` 会根据版本是否以 `-SNAPSHOT` 结尾发到对应仓库
- `maven-release-plugin` 配置了 `autoVersionSubmodules=true`、`pushChanges=true`，tag 格式 `v@{project.version}`

## 切发布

```bash
# 1. 确保 master 是干净的
git status

# 2. 准备发布（会提示输入 release 版本和下一个开发版本）
mvn release:prepare

# 3. 执行发布（构建、测试、部署、打 tag）
mvn release:perform

# 或者一条命令搞定
mvn release:prepare release:perform
```

插件会：
- 将所有 POM 中的 `${revision}` 替换为 release 版本
- 给 commit 打 tag 为 `v<version>`
- 部署到 release 仓库
- 将 `${revision}` 升到下一个 `-SNAPSHOT` 版本

## 本地完整性构建（与 CI 对齐）

```bash
# 与 .github/workflows/ci.yml 运行的命令一致
mvn -B clean verify -DskipTests -pl '!atlas-richie-component-template' -am
```

注意：CI 里跳过了测试。用户合并前本地跑测试；不要依赖 CI 来抓测试失败。

## 分支

- `master` — SNAPSHOT 主线，快速迭代
- `x.y.z` 或 `x.y.z-RELEASE` — 稳定 / release tag
- 功能分支：`feature/<name>` 或 `fix/<name>`（按 CONTRIBUTING.zh.md）
- Worktree（如 `.worktrees/feature-migration-window-guard/`）——进行中功能，不污染主树

## 不要做的事

- 不要编辑子 POM 的 `<version>` 块——它们用 `${revision}` 是有原因的
- 不要在子 POM 加新的 plugin 版本——先提到根 `pluginManagement`
- 不要直接推 master；所有变更走功能分支 + review
- 不要 force-push release tag
- 不要带未提交变更切发布（release 插件会阻止，尊重它）
