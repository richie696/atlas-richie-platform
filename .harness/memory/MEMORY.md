# 团队共享记忆 — atlas-richie-platform

跨所有 reins 的共享记忆。单个 rein 作用域专用的教训应写入该 rein 的私有记忆（`~/.mavis/agents/<name>/memory/MEMORY.md`），不写在这里。

本文件目前是占位。随着团队积累经验，按需追加条目。

## 条目格式

```markdown
### <主题> (<日期>)
类型: <规则 | 事实 | 坑 | 模式>
<内容>
原因: <为什么这个教训以后还重要>
```

## 何时追加

满足以下条件时添加记忆条目：
- 你花了超过 5 分钟才发现的关于项目的知识点
- 教训是**持久的**（6 个月后仍然重要）
- 教训是**项目全局的**（不只是某个 rein 专用的）
- 一个新人（人或 LLM）从零开始会受益于知道它

**不要添加**：
- 临时任务状态（"目前正在做 X"）
- rein 私有范围的笔记（写在 rein 的私有记忆里）
- `pom.xml` / 文档里已有的信息（改为链接到来源）

## 当前条目

### 4.x 内部版 vs 1.x 开源版差异速查（2026-06-01）
类型: 事实
来源：对比 `.cursor/skills/java-coding-guidelines/11-atlas-richie-components-usage.md` 与实际代码

**已变更的部分**（写 Harness 文档时不要照搬旧版）：
- `ResultVO` → `ApiResult`，在 `atlas-richie-contract` 模块（不在 `atlas-richie-context`）
- `atlas-richie-context` 现在只含工具类；异常和 ApiResult 在 `atlas-richie-contract`
- HTTP 组件：旧版 OkHttp+HttpClient5 → **开源版 4 个 provider**（OkHttp、RestClient、JDK HttpClient、HttpClient5）
- Messaging 组件：旧版列表 → **新增 Pulsar、Solace**
- Redis Stream 消费 API：旧版直接调用 → **新版本统一通过 `GlobalCache.stream()` 获取 StreamFunction**
- OCR 组件：`atlas-richie-component-ocr` 已从开源版移除
- 没有 Nacos/bootstrap/global-config（那是业务模板项目 `atlas-richie-template` 用的，组件库本身用 `@ConfigurationProperties`）
- Storage：12 个实现（新增了 SMB、SFTP、FTP、Local 等）；Vector：8 个实现
- `GlobalCache` 是静态门面，`@Component` 仅用于注入 `GlobalCacheManager` 实例，不是真正的 Spring Bean 业务对象

**当 .cursor/skills 和实际代码不一致时，以 `pom.xml` 和源码为准**，不要照抄旧版 Skill。
原因: 以后还有更多文档要从旧版迁移过来，这是方法论验证。

### Properties 前缀约定（2026-06-01）
类型: 事实
来源：grep 各组件 `*Properties.java` 文件

**前缀规律**：
- `platform.cache` — cache 组件（早期命名，无 `component`）
- `platform.component.<module>` — 其他所有组件（http、ai、microservice、storage、vector、messaging 等）
- provider 特定配置放在嵌套节点下，如 `platform.component.http.okhttp`、`platform.component.storage.oss`
- 通过 `@AutoConfiguration` + `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 自动注册，无需使用者 `@EnableConfigurationProperties`

**当不确定某个组件的前缀时**，grep 源码中的 `@ConfigurationProperties(prefix = "` 一行。

## 未来值得考虑的条目

- [ ] 等 `component-tester` 开始补 cache 覆盖率缺口时：测试 fixtures 在哪里、Mock Redis client 长什么样
- [ ] 等 `ai-component-expert` 稳定了 ChatClient 工厂后：哪个 Spring AI 版本引入了哪些 breaking change
- [ ] 等某个 `@MigrationWindow` 真正到了截止日期被删除时：删除它的 diff、用户可见的迁移故事
- [ ] 如果 dependabot PR 揭示了有意义的模式：哪些版本更新需要迁移窗口，哪些是安全的直接替换
- [ ] 如果用户加了一个新组件：新组件的规范"模板"是什么（看最近加入的组件）
