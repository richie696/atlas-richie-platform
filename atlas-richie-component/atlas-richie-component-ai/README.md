# RichieAI组件 (richie-component-ai)

## 概述

`richie-component-ai` 是基于 Spring AI 的统一大模型接入组件，目标是让业务系统以统一方式管理和调用不同模型。

当前组件同时支持两种初始化模式：

- **配置文件初始化**：通过 `application.yml` 固定配置模型
- **代码动态初始化**：业务系统从数据库读取模型配置后，运行时注入组件

这两种方式可并存，运行时动态初始化可覆盖同名模型。

## 核心能力

- 统一调用接口：同一个 `AiModelService` 调用不同厂商模型
- 多模型管理：支持查看可用模型、切换默认模型、按模型名调用
- 动态模型注册：支持 `initializeModels(List<ModelOptions>)` 运行时装载
- 兼容协议兜底：动态注册遇到未知 provider 时，自动降级为 OpenAI 协议
- 统一响应结构：包含内容、耗时、模型信息、token 使用统计

## 当前支持模型

### 1) 组件内置 Provider（配置文件模式）

`AiModelProperties.AiProviderType` 当前支持：

- `OPENAI`
- `DEEPSEEK`
- `ZHIPUAI`（通过 OpenAI 兼容协议接入）
- `ANTHROPIC`
- `OLLAMA`
- `MINIMAX`
- `MOONSHOT`（通过 OpenAI 兼容协议接入）

### 2) 动态模式的未知 Provider 兜底

当调用 `initializeModels(List<ModelOptions>)` 时，`ModelOptions.provider` 是字符串。

- 若可识别为上述内置 Provider，则按内置逻辑初始化
- 若不可识别，则自动降级为 `OPENAI` 协议初始化，不会因枚举未定义而失败

## 依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ai</artifactId>
    <version>5.0.0-SNAPSHOT</version>
</dependency>
```

## EmbeddingModel 自动注入

从当前版本开始，`richie-component-ai` 在配置文件初始化模式下会自动注册 `EmbeddingModel` Bean（Bean 名称：`aiEmbeddingModel`）。

- 默认策略：**跟随默认模型**（即 `platform.component.ai.models` 中第一个模型）
- 适用场景：`richie-component-vector` 与各 provider 子组件需要 `EmbeddingModel` 时可直接复用
- 覆盖机制：业务侧若自行声明 `EmbeddingModel` Bean，会覆盖默认自动注入

注意：

- 当 `config-initialization-enabled: false` 时，组件不会创建默认 `EmbeddingModel`
- 当未配置任何模型时，也不会创建默认 `EmbeddingModel`
- 上述场景请在业务侧手工声明 `EmbeddingModel` Bean

## 使用方式一：配置文件初始化

```yaml
platform:
  component:
    ai:
      models:
        gpt-4o:
          provider: OPENAI
          api-key: ${OPENAI_API_KEY:}
          base-url: ${OPENAI_BASE_URL:https://api.openai.com}
          options:
            model: gpt-4o
            max-tokens: 2000
            temperature: 0.7

        deepseek-chat:
          provider: DEEPSEEK
          api-key: ${DEEPSEEK_API_KEY:}
          base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
          options:
            model: deepseek-chat
            temperature: 0.7

        zhipu-glm:
          provider: ZHIPUAI
          api-key: ${ZHIPUAI_API_KEY:}
          base-url: ${ZHIPUAI_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
          options:
            model: glm-4

        claude-sonnet:
          provider: ANTHROPIC
          api-key: ${ANTHROPIC_API_KEY:}
          base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}
          options:
            model: claude-3-5-sonnet-20240620
            max-tokens: 2000
            temperature: 0.7
            top-k: 40

        minimax-main:
          provider: MINIMAX
          api-key: ${MINIMAX_API_KEY:}
          base-url: ${MINIMAX_BASE_URL:https://api.minimax.chat}
          options:
            model: abab6.5g-chat

        moonshot-main:
          provider: MOONSHOT
          api-key: ${MOONSHOT_API_KEY:}
          base-url: ${MOONSHOT_BASE_URL:https://api.moonshot.cn/v1}
          options:
            model: moonshot-v1-8k

        ollama-local:
          provider: OLLAMA
          base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
          options:
            model: qwen2.5:7b
```

## 使用方式二：数据库配置 + 动态初始化（推荐业务场景）

### 0) 关闭配置文件初始化（仅动态初始化模式）

当你希望模型全部来自数据库，而不是 `application.yml`，请先关闭启动时 `@Bean` 初始化：

```yaml
platform:
  component:
    ai:
      config-initialization-enabled: false
```

行为说明：

- 组件启动时不会构造任何 `ChatClient`
- 必须先调用 `initializeModels(List<ModelOptions>)` 才可执行 AI 调用
- 若在初始化前调用 `AiModelService.call/callAsync/callWithModel`，将返回：
  - `errorCode = MODEL_NOT_INITIALIZED`
  - `errorMessage = AI模型尚未初始化，无法执行调用，请先通过配置文件或 initializeModels 完成初始化`

### 1) 业务系统构造模型配置列表

```java
import config.com.richie.component.ai.AiModelProperties;
import model.com.richie.component.ai.ModelOptions;

List<ModelOptions> options = List.of(
        new ModelOptions()
                .setModelName("tenant-a-openai")
                .setProvider("OPENAI")
                .setBaseUrl("https://api.openai.com")
                .setApiKey("sk-xxx")
                .setOptions(new AiModelProperties.AiModelOptions()
                        .setModel("gpt-4o")
                        .setTemperature(0.6)
                        .setMaxTokens(2000)),

        new ModelOptions()
                .setModelName("tenant-a-custom-compat")
                .setProvider("MY_PRIVATE_PROVIDER") // 未定义provider
                .setBaseUrl("https://your-openai-compatible-endpoint/v1")
                .setApiKey("ak-yyy")
                .setOptions(new AiModelProperties.AiModelOptions()
                        .setModel("private-model"))
);
```

### 2) 调用组件动态初始化

```java


aiModelService.initializeModels(options);
```

执行后：

- `tenant-a-openai` 按 OpenAI 逻辑初始化
- `tenant-a-custom-compat` 因 provider 未识别，自动按 OpenAI 兼容协议初始化

## 统一调用示例

```java
import model.com.richie.component.ai.AiRequest;
import model.com.richie.component.ai.AiResponse;

// 1. 默认模型调用
AiResponse r1 = aiModelService.call(AiRequest.ofUserMessage("请简要介绍你自己"));

        // 2. 指定模型调用
        AiResponse r2 = aiModelService.callWithModel(
                "tenant-a-openai",
                AiRequest.ofSystemAndUser("你是Java架构师", "请解释DDD分层")
        );

// 3. 异步调用
aiModelService.

        callAsync(AiRequest.ofUserMessage("生成一个Spring Boot Controller示例"))
        .

        thenAccept(resp ->{
        if(resp.

        isSuccess()){
        System.out.

        println(resp.getContent());
        }
        });
```

## AiModelService 接口

```java
AiResponse call(AiRequest request);
CompletableFuture<AiResponse> callAsync(AiRequest request);
AiResponse callWithModel(String modelName, AiRequest request);

List<AiModelInfo> getAvailableModels();
AiModelInfo getModelInfo(String modelName);
boolean isModelAvailable(String modelName);

String getDefaultModel();
void setDefaultModel(String modelName);

void initializeModels(List<ModelOptions> modelOptionsList);
```

## 关键数据结构

### 1) `AiRequest`

- `modelName`：可选，指定调用模型名
- `messages`：对话消息
- `options`：请求级参数（如温度、最大 token）

便捷工厂方法：

- `AiRequest.ofUserMessage(...)`
- `AiRequest.ofSystemAndUser(...)`
- `AiRequest.ofMessages(...)`

### 2) `ModelOptions`（动态初始化输入）

- `modelName`：组件内部模型唯一标识
- `provider`：字符串 provider（支持未知值）
- `baseUrl`：模型 API 地址
- `apiKey`：模型 APIKey
- `options`：模型参数（`AiModelProperties.AiModelOptions`）

### 3) `AiResponse`

- `success`：是否成功
- `content`：模型文本输出
- `modelName` / `provider`：实际调用模型信息
- `duration`：耗时（毫秒）
- `usage`：token 统计
- `errorMessage` / `errorCode`：失败信息

## 参数说明（AiModelOptions）

常用参数：

- `model`
- `maxTokens`
- `temperature`
- `topP`
- `frequencyPenalty`
- `presencePenalty`
- `stop`

特定模型参数：

- `topK`（Anthropic）
- `logprobs`、`topLogprobs`（OpenAI/DeepSeek 等支持时）
- `enableThinking`、`thinkingBudgetTokens`（预留思考参数）

## 默认与覆盖规则

- 启动时：先加载配置文件模型
- 运行时：调用 `initializeModels(...)` 后新增/覆盖同名模型
- 默认模型：
  - 如果未主动设置，使用当前可用模型集合中的第一个
  - 若当前默认模型被覆盖或不可用，会自动回退到可用模型

## 生产建议

- API Key 不要写死在代码或明文配置中，建议使用配置中心/密钥管理
- 动态初始化前建议先做连通性探测，再落库并生效
- 业务可按租户、业务线、场景维护模型路由策略（模型名映射）
- 对调用失败增加熔断/降级策略，避免外部模型波动影响主链路

# RichieAI组件 (richie-component-ai)

## 📖 概述

RichieAI组件是一个基于Spring AI的统一AI模型调用组件，完全屏蔽了不同AI提供商之间的接口差异，提供统一的配置方式和调用接口。支持同时配置多个AI模型，可以像GitHub Copilot一样通过选择框动态切换不同的AI模型。

## ✨ 特性

- **🔄 统一接口**：屏蔽不同AI提供商的接口差异，提供统一的调用方式
- **🎯 多模型支持**：同时支持OpenAI、DeepSeek、智谱AI、Anthropic、Ollama等主流AI模型
- **⚙️ 配置化切换**：通过配置文件即可切换不同的AI模型，无需修改代码
- **🚀 动态选择**：支持运行时动态选择AI模型，类似GitHub Copilot的模型选择
- **📊 详细监控**：提供完整的调用监控、性能统计和错误处理
- **🛡️ 异常处理**：完善的异常处理机制，确保系统稳定性
- **📝 中文文档**：详细的中文注释和使用说明

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    RichieAI组件架构                            │
├─────────────────────────────────────────────────────────────┤
│  AiModelService (统一服务接口)                               │
│  ├── 同步调用                                                │
│  ├── 异步调用                                                │
│  ├── 模型管理                                                │
│  └── 动态切换                                                │
├─────────────────────────────────────────────────────────────┤
│  AiModelServiceImpl (服务实现)                               │
│  ├── 请求处理                                                │
│  ├── 模型选择                                                │
│  ├── 响应转换                                                │
│  └── 异常处理                                                │
├─────────────────────────────────────────────────────────────┤
│  AiModelAutoConfiguration (自动配置)                         │
│  ├── ChatClient创建                                          │
│  ├── 模型初始化                                              │
│  └── 配置验证                                                │
├─────────────────────────────────────────────────────────────┤
│  Spring AI (底层实现)                                        │
│  ├── OpenAI                                                 │
│  ├── DeepSeek                                               │
│  ├── 智谱AI                                                 │
│  ├── Anthropic                                              │
│  └── Ollama                                                 │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ai</artifactId>
    <version>5.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置AI模型

在`application.yml`中添加配置：

```yaml
platform:
  component:
    ai:
      models:
        # OpenAI GPT-4
        gpt-4:
          provider: OPENAI
          api-key: ${OPENAI_API_KEY:your-openai-api-key}
          base-url: ${OPENAI_BASE_URL:https://api.openai.com}
          options:
            model: gpt-4
            max-tokens: 2000
            temperature: 0.7
            top-p: 0.9

        # DeepSeek
        deepseek-chat:
          provider: DEEPSEEK
          api-key: ${DEEPSEEK_API_KEY:your-deepseek-api-key}
          base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
          options:
            model: deepseek-chat
            max-tokens: 2000
            temperature: 0.7
            top-p: 0.9

        # 智谱AI
        zhipu-chatglm:
          provider: ZHIPUAI
          api-key: ${ZHIPUAI_API_KEY:your-zhipuai-api-key}
          base-url: ${ZHIPUAI_BASE_URL:https://open.bigmodel.cn}
          options:
            model: glm-4
            max-tokens: 2000
            temperature: 0.7
            top-p: 0.9

        # Anthropic Claude
        claude-3:
          provider: ANTHROPIC
          api-key: ${ANTHROPIC_API_KEY:your-anthropic-api-key}
          base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}
          options:
            model: claude-3-sonnet-20240229
            max-tokens: 2000
            temperature: 0.7
            top-p: 0.9
            top-k: 40

        # Ollama本地模型
        ollama-llama2:
          provider: OLLAMA
          base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
          options:
            model: llama2
            max-tokens: 2000
            temperature: 0.7
            top-p: 0.9
```

### 3. 使用AI服务

```java
@Autowired
private AiModelService aiModelService;

// 简单调用（使用默认模型）
AiRequest request = AiRequest.ofUserMessage("你好，请介绍一下自己");
AiResponse response = aiModelService.call(request);

if (response.isSuccess()) {
    System.out.println("AI回复: " + response.getContent());
    System.out.println("使用模型: " + response.getModelName());
    System.out.println("响应时间: " + response.getDuration() + "ms");
} else {
    System.err.println("调用失败: " + response.getErrorMessage());
}

// 指定模型调用
AiResponse response = aiModelService.callWithModel("gpt-4", request);

// 异步调用
CompletableFuture<AiResponse> future = aiModelService.callAsync(request);
future.thenAccept(response -> {
    if (response.isSuccess()) {
        System.out.println("异步调用成功: " + response.getContent());
    }
});
```

## 📋 API文档

### AiModelService 接口

| 方法 | 描述 | 参数 | 返回值 |
|------|------|------|--------|
| `call(AiRequest)` | 同步调用AI模型 | AI请求对象 | AI响应对象 |
| `callAsync(AiRequest)` | 异步调用AI模型 | AI请求对象 | CompletableFuture<AiResponse> |
| `callWithModel(String, AiRequest)` | 使用指定模型调用 | 模型名称, AI请求对象 | AI响应对象 |
| `getAvailableModels()` | 获取所有可用模型 | 无 | List<AiModelInfo> |
| `getModelInfo(String)` | 获取指定模型信息 | 模型名称 | AiModelInfo |
| `isModelAvailable(String)` | 检查模型是否可用 | 模型名称 | boolean |
| `getDefaultModel()` | 获取默认模型名称 | 无 | String |
| `setDefaultModel(String)` | 设置默认模型 | 模型名称 | void |

### AiRequest 请求对象

```java
// 简单用户消息
AiRequest request = AiRequest.ofUserMessage("你好");

// 带系统提示的对话
AiRequest request = AiRequest.ofSystemAndUser(
    "你是一个专业的Java工程师", 
    "请解释Spring Boot的自动配置"
);

// 多轮对话
List<AiRequest.Message> messages = Arrays.asList(
    new AiRequest.Message().setRole("user").setContent("你好"),
    new AiRequest.Message().setRole("assistant").setContent("你好！有什么可以帮助你的？"),
    new AiRequest.Message().setRole("user").setContent("我想学习Java")
);
AiRequest request = AiRequest.ofMessages(messages);

// 带自定义参数的请求
AiRequest.ModelOptions options = new AiRequest.ModelOptions()
    .setTemperature(0.3)
    .setMaxTokens(500)
    .setTopP(0.8);

AiRequest request = new AiRequest()
    .setMessages(List.of(new AiRequest.Message().setRole("user").setContent("生成代码")))
    .setOptions(options);
```

### AiResponse 响应对象

```java
AiResponse response = aiModelService.call(request);

if (response.isSuccess()) {
    String content = response.getContent();           // 响应内容
    String modelName = response.getModelName();       // 使用的模型名称
    String provider = response.getProvider();         // 模型提供商
    Long duration = response.getDuration();           // 响应时间（毫秒）
    AiResponse.Usage usage = response.getUsage();     // 令牌使用情况
    
    // 令牌使用详情
    Integer promptTokens = usage.getPromptTokens();      // 提示令牌数
    Integer completionTokens = usage.getCompletionTokens(); // 完成令牌数
    Integer totalTokens = usage.getTotalTokens();        // 总令牌数
} else {
    String errorMessage = response.getErrorMessage();  // 错误信息
    String errorCode = response.getErrorCode();        // 错误代码
}
```

## 🔧 配置说明

### 支持的AI提供商

| 提供商 | 配置值 | 说明 |
|--------|--------|------|
| OpenAI | `OPENAI` | ChatGPT系列模型 |
| DeepSeek | `DEEPSEEK` | DeepSeek大语言模型 |
| 智谱AI | `ZHIPUAI` | 智谱AI大语言模型 |
| Anthropic | `ANTHROPIC` | Claude系列模型 |
| Ollama | `OLLAMA` | 本地部署的模型 |

### 模型参数配置

| 参数 | 类型 | 说明 | 支持范围 |
|------|------|------|----------|
| `model` | String | 模型名称 | 各提供商的具体模型名称 |
| `maxTokens` | Integer | 最大输出令牌数 | 1-8192（取决于模型） |
| `temperature` | Double | 温度参数 | 0.0-2.0（DeepSeek支持到2.0） |
| `topP` | Double | Top P参数 | 0.0-1.0 |
| `topK` | Integer | Top K参数 | 1-100（Anthropic特有） |
| `frequencyPenalty` | Double | 频率惩罚 | -2.0-2.0 |
| `presencePenalty` | Double | 存在惩罚 | -2.0-2.0 |
| `stop` | List<String> | 停止词列表 | 最多4个停止词 |
| `logprobs` | Boolean | 是否返回对数概率 | true/false |
| `topLogprobs` | Integer | Top对数概率数量 | 0-20 |
| `enableThinking` | Boolean | 启用思考模式 | true/false（Anthropic特有） |
| `thinkingBudgetTokens` | Integer | 思考预算令牌数 | 正整数（Anthropic特有） |

### 环境变量配置

```bash
# OpenAI
export OPENAI_API_KEY=sk-your-openai-api-key
export OPENAI_BASE_URL=https://api.openai.com

# DeepSeek
export DEEPSEEK_API_KEY=sk-your-deepseek-api-key
export DEEPSEEK_BASE_URL=https://api.deepseek.com

# 智谱AI
export ZHIPUAI_API_KEY=your-zhipuai-api-key
export ZHIPUAI_BASE_URL=https://open.bigmodel.cn

# Anthropic
export ANTHROPIC_API_KEY=sk-ant-your-anthropic-api-key
export ANTHROPIC_BASE_URL=https://api.anthropic.com

# Ollama
export OLLAMA_BASE_URL=http://localhost:11434
```

## 🎯 使用场景

### 1. 代码生成

```java
String systemPrompt = "你是一个专业的Java开发工程师，请生成简洁高效的代码。";
String userMessage = "请生成一个Spring Boot REST API控制器，用于用户管理";

AiRequest request = AiRequest.ofSystemAndUser(systemPrompt, userMessage);
AiResponse response = aiModelService.callWithModel("gpt-4", request);
```

### 2. 文档生成

```java
String prompt = "请为以下Java类生成详细的API文档：\n" + javaCode;
AiRequest request = AiRequest.ofUserMessage(prompt);
AiResponse response = aiModelService.call(request);
```

### 3. 代码审查

```java
String systemPrompt = "你是一个资深的代码审查专家，请从代码质量、安全性、性能等方面进行审查。";
String userMessage = "请审查以下代码：\n" + codeToReview;

AiRequest request = AiRequest.ofSystemAndUser(systemPrompt, userMessage);
AiResponse response = aiModelService.callWithModel("claude-3", request);
```

### 4. 多模型对比

```java
String question = "请解释什么是微服务架构";
List<String> models = Arrays.asList("gpt-4", "claude-3", "deepseek-chat");

for (String model : models) {
    if (aiModelService.isModelAvailable(model)) {
        AiRequest request = AiRequest.ofUserMessage(question);
        AiResponse response = aiModelService.callWithModel(model, request);
        
        if (response.isSuccess()) {
            System.out.println("模型 " + model + " 的回答：");
            System.out.println(response.getContent());
            System.out.println("---");
        }
    }
}
```

## 🔍 监控和调试

### 日志配置

```yaml
logging:
  level:
    com.richie.component.ai: DEBUG
    org.springframework.ai: INFO
```

### 性能监控

```java
// 监控响应时间
long startTime = System.currentTimeMillis();
AiResponse response = aiModelService.call(request);
long duration = System.currentTimeMillis() - startTime;

// 监控令牌使用
AiResponse.Usage usage = response.getUsage();
if (usage != null) {
    log.info("令牌使用情况 - 提示: {}, 完成: {}, 总计: {}", 
        usage.getPromptTokens(), 
        usage.getCompletionTokens(), 
        usage.getTotalTokens());
}
```

### 错误处理

```java
try {
    AiResponse response = aiModelService.call(request);
    if (!response.isSuccess()) {
        log.error("AI调用失败: {} - {}", response.getErrorCode(), response.getErrorMessage());
        // 处理错误逻辑
    }
} catch (Exception e) {
    log.error("AI调用异常", e);
    // 处理异常逻辑
}
```

## 🚨 注意事项

1. **API密钥安全**：请妥善保管API密钥，建议使用环境变量或配置中心
2. **请求频率限制**：注意各AI提供商的请求频率限制
3. **成本控制**：监控令牌使用量，控制API调用成本
4. **模型选择**：根据具体需求选择合适的模型和参数
5. **错误处理**：实现完善的错误处理机制，确保系统稳定性
