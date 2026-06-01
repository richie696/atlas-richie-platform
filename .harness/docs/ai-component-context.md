# AI 组件上下文

AI 模块是项目当前的热点路径。最近 8 次提交中有 6 次涉及它。本模块形态变化时，保持本文档更新。

## 模块路径

- `atlas-richie-component/atlas-richie-component-ai/` — 组件库
- `atlas-richie-component-template/sample-ai/` — 示例应用

## 技术栈

- Spring AI `2.0.0-M8`（在根 `pom.xml` 管理）
- Spring AI Alibaba `1.1.2.3`（可选的阿里云特定扩展）
- AgentScope `1.1.0-RC2`（多 Agent 框架）

## 分层设计（截至 2026-06-01 的当前形态）

```
com.richie.component.ai/
├── AiAutoConfiguration.java        # @Configuration，接线 AI 模块
├── AiProperties.java               # @ConfigurationProperties("platform.component.ai")
├── model/                          # DTO / request / response — 版本化契约
│   ├── AiRequest.java              # 包含 scene（路由 key）+ options
│   ├── AiResponse.java
│   ├── AiStreamChunk.java          # 流式响应契约
│   └── AiHealthResult.java
├── routing/                        # scene → model id 解析
│   └── <ModelRouter>               # 决定哪个模型处理给定 scene
├── circuitbreaker/                 # 按模型的弹性
│   └── <CircuitBreaker>            # 给模型调用包装重试 / 熔断
├── options/                        # Spring AI ChatOptions 解析
│   └── <ChatOptionsResolver>       # 将 AiRequest.options 转成 Spring AI ChatOptions
├── streaming/                     # 流式响应处理
├── health/                        # 健康检查探针
├── service/                       # AiService / ChatClient 工厂（最近重构过）
│   └── ChatClientFactory.java      # 750f8d4 期间重构
└── ...
```

## 关键契约类型

这些是**公开表面**——任何变更必须经过 `code-reviewer` 审核，并遵循迁移窗口规则。

- `AiRequest` — 输入。包含 `scene`（路由 key）、`messages`、`options`（类型化）及其他字段
- `AiResponse` — 输出。包装 Spring AI 的 `ChatResponse` 加上平台级元数据
- `AiStreamChunk` — 流式响应的一个 chunk
- `AiHealthResult` — 每个模型的健康检查结果

## 最近工作（提交历史）

- `a1ae481` 更新 Spring 相关基础包的版本
- `c3eead5` test(atlas-richie-component-ai): 新增路由/熔断/选项解析器单元测试并更新示例测试
- `750f8d4` refactor(atlas-richie-component-ai): 重构 ChatClient 工厂和 AI 服务层，集成路由/熔断/流式调用
- `f8acef5` feat(atlas-richie-component-ai): 新增 ChatOptions 解析器/熔断器/模型路由基础设施
- `d5ab4a1` feat(atlas-richie-component-ai): 新增模型路由/熔断/健康检查配置属性
- `48378e0` feat(atlas-richie-component-ai): 新增模型层 - AiHealthResult/AiStreamChunk 及 AiRequest 场景路由字段

## 接触这个模块时先读什么

1. `atlas-richie-component-ai/` 的 `pom.xml` — 依赖了什么
2. `AiAutoConfiguration` — 默认接了什么线
3. `AiProperties` — 有哪些配置开关
4. `model/AiRequest` — 请求里有什么（契约）
5. `routing/` — scene 如何映射到 model
6. `circuitbreaker/` — 弹性策略是什么
7. `sample-ai/src/main/java/com/richie/component/ai/SampleAiApplication.java` + `AiSampleController.java` — 如何驱动

## 示例规范

`sample-ai` 应该：
- 展示每个公开能力（路由、熔断、流式、健康检查、options）
- 用 `spring-boot:run` 能干净地跑起来
- 每个能力至少有一个 controller endpoint
- **不包含**业务专用代码（这是公共示例）

给 AI 组件加新功能时，**同时在 sample-ai 里加一个 endpoint 来调用它**。最近 ChatClientFactory 的重构部分目的就是让 sample-ai 更干净。

## 不要做的事

- 不要引入新的第三方 AI SDK（除 Spring AI / AgentScope 之外），需要先暴露决策
- 不要破坏 `AiRequest` / `AiResponse` / `AiStreamChunk` 字段结构，需要有 `@MigrationWindow`
- 不要让 `routing/` 引用 `circuitbreaker/`——它们是独立的层
- 不要让 `model/` 知道 Spring AI 的细节（它应该是干净的 DTO 层）
