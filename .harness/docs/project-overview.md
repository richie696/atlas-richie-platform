# 项目概览 — atlas-richie-platform

> 真相来源：根目录 `pom.xml`。README 和徽章会落后——引用前先核验。

## 项目定位

企业 Java 技术中台（技术中台）——一个多模块库，为下游业务服务提供统一的技术抽象 API。Apache 2.0。公共版本是 **1.0.0-SNAPSHOT**，派生自内部 **5.0.0-SNAPSHOT**，已清除业务代码。

## 技术栈（2026-06-01 核验自 `pom.xml`）

| 项目 | 值 | 备注 |
|---|---|---|
| JDK | 25 | surefire/failsafe argLine 中启用 preview features |
| Spring Boot | `4.0.6` | README 写的是 `4.0.5`——错了。永远引用 pom。 |
| Spring Cloud | `2025.1.1` | |
| Spring Cloud Alibaba | `2025.1.0.0` | |
| Spring Cloud Azure | `7.0.0` | |
| Spring AI | `2.0.0-M8` | |
| Spring AI Alibaba | `1.1.2.3` | |
| AgentScope | `1.1.0-RC2` | |
| Maven | `3.9.0+` | enforcer 强制执行 |
| Lombok | `1.18.46` | |
| MapStruct | `1.6.3` | |

## 模块布局

```
atlas-richie-platform/                          (parent pom, packaging=pom)
├── atlas-richie-base/                          (基础包)
│   ├── atlas-richie-dependencies/              (BOM — 版本管理)
│   ├── atlas-richie-context/                   (utils, context, response, exception)
│   └── atlas-richie-contract/                  (跨服务共享契约)
├── atlas-richie-component/                     (组件库，22 个组件)
│   ├── atlas-richie-component-cache/           (Redis；redis/perf/ 有兜底的典范实现)
│   ├── atlas-richie-component-dao/             (MyBatis Plus)
│   ├── atlas-richie-component-tenant/      (多租户 DAO)
│   ├── atlas-richie-component-http/            (OkHttp / HttpClient5)
│   ├── atlas-richie-component-web/             (CORS, i18n, exception handler)
│   ├── atlas-richie-component-i18n/            (resource bundles)
│   ├── atlas-richie-component-logging/         (access log, method trace)
│   ├── atlas-richie-component-threadpool/      (动态线程池)
│   ├── atlas-richie-component-tracing/         (OpenTelemetry)
│   ├── atlas-richie-component-skywalking/       (APM)
│   ├── atlas-richie-component-liquibase/        (DB migration)
│   ├── atlas-richie-component-microservice/     (OpenFeign/RestClient)
│   ├── atlas-richie-component-storage/          (StorageEngine + 12 个 provider 实现)
│   ├── atlas-richie-component-vector/           (VectorService + 8 个 provider 实现)
│   ├── atlas-richie-component-messaging/        (MessageService)
│   ├── atlas-richie-component-ai/             (Spring AI / AgentScope；**热点路径**)
│   ├── atlas-richie-component-statemachine/    (Easy Rules)
│   ├── atlas-richie-component-mfa/             (TOTP / MFA)
│   ├── atlas-richie-component-mongodb/
│   ├── atlas-richie-component-mqtt/
│   ├── atlas-richie-component-search/           (Elasticsearch / Solr)
│   └── atlas-richie-component-desensitize/
├── atlas-richie-component-template/            (15 个示例应用；CI 排除)
└── atlas-richie-gateway-service/              (gateway，含 auth、rate-limit 等)
```

## "热点路径"是什么意思

最近的提交历史（截至 2026-06-01 的最近 8 次提交）：

- 8 次中有 6 次涉及 `atlas-richie-component-ai/` —— 模型路由、熔断、选项解析器、ChatClient 工厂重构、集成测试
- 1 次更新 Spring 基础包版本
- 1 次更新 Maven enforcer

所以 **AI 模块是当前开发重点**。其他组件处于维护 / dependabot 更新模式。

## 这个仓库里**没有**什么（公共版本已清除）

- POS、KDS、美团 / 淘宝 / 京东外卖集成
- 自营小程序代码
- 内部业务抽象 / 绑定到连锁餐饮的业务领域模型
- 内部团队 / 私有基础设施引用

如果在仓库里看到看起来是业务专用的代码，标记出来。这是内部 5.x 的泄露。

## 30 秒了解规范

- Maven 多模块，parent BOM 控制所有版本
- `${revision}` 占位符 + `flatten-maven-plugin`（OSS flatten 模式）
- Spring Boot 4.0.6，JDK 25 加 `--enable-preview`
- `*IT.java` / `*ITCase.java` / `*IntegrationTest.java` → failsafe
- `*Test.java`（其他）→ surefire
- License: Apache 2.0，header 由 `license-maven-plugin` 管理
- 分支策略：`master` 是 SNAPSHOT 主线；`x.y.z` 或 `x.y.z-RELEASE` 是稳定 tag

## 相关文档

- `.harness/docs/module-ownership.md` — 哪个 rein 负责哪个路径
- `.harness/docs/code-standards.md` — checkstyle 规范之外的代码风格（含 Javadoc 规范）
- `.harness/docs/testing-policy.md` — surefire / failsafe / jacoco + Service 层测试原则
- `.harness/docs/release-workflow.md` — 版本更新、快照、发布
- `.harness/docs/ai-component-context.md` — AI 模块深度介绍
- `.harness/docs/migration-window-rules.md` — `@MigrationWindow` 使用规范
- `.harness/docs/open-source-discipline.md` — 公共发布的语言规范
- `.harness/docs/component-usage.md` — 22 个组件的选型与使用方式
- `.harness/docs/design-patterns.md` — Service 层设计模式速查表
- `.harness/docs/service-architecture.md` — 组件内部架构设计原则
- `.harness/docs/concurrency-patterns.md` — 并发与性能规范
- `.harness/docs/configuration-guide.md` — `@ConfigurationProperties` 配置策略
