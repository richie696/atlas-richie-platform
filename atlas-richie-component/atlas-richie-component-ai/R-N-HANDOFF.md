# R-N Sprint Handoff 文档（atlas-richie-component-ai 多模态扩展）

> **目标**：把当前会话确定的中台 AI 模型能力扩展任务，移交到中台工程（`atlas-richie-platform`）的独立开发会话。
> **适用读者**：在 atlas-richie-platform 仓库新开会话接手此任务。
> **创建时间**：2026-07-20
> **来源会话**：atlas-foundry-ai R-T Sprint 收官时

---

## 0. 上下文来源（先读这些）

1. `atlas-foundry-ai/SESSION_LOG.md` — 第 9-13 节有 R-I / R-J / R-K / R-M / R-L / R-T 完整记录
2. `atlas-foundry-ai/docs/HANDOFF.md` — 项目整体交接文档
3. `atlas-richie-platform` 仓库当前 AI / Vector / Rerank 包结构

**关键事实**：
- 当前 admin-service 后端 v31 在跑（Jetty 8898 LISTEN，scheduler 干净）
- 33 个 R-T 测试全 pass（32 单测 + 1 真实 LLM 集成测试）
- application-local.yaml 已配 `platform.component.http.provider: jdk`（绕开 OkHttp 4.x/5.x 与 milvus-sdk-java 3.0.1 的 okio 冲突）
- 暂不提交 git（用户未明确"提交"指令）

---

## 1. R-N Sprint 总目标

**atlas-richie-component-ai 扩展 7 个新 Model 抽象的 `@Bean` 创建 + 厂商实现**：

| # | Model 抽象 | Spring AI 提供? | 厂商实现 | 中台 bean 名 |
|---|----------|----------------|---------|------------|
| 1 | `RerankModel` | ❌ 自实现 | 阿里百炼 gte-rerank / 智谱 glm-rerank | `aiRerankModel` |
| 2 | `ImageModel` | ✅ `org.springframework.ai.image.ImageModel` | 通义万相 / qwen-vl | `aiImageModel` |
| 3 | `TextToSpeechModel` | ✅ `org.springframework.ai.audio.tts.TextToSpeechModel` | cosyvoice / sambert | `aiTextToSpeechModel` |
| 4 | `TranscriptionModel` | ✅ `org.springframework.ai.audio.transcription.TranscriptionModel` | paraformer-v2 / sensevoice | `aiTranscriptionModel` |
| 5 | `VideoModel` | ❌ 自实现 | wanx-video（文生视频 + 视频理解）| `aiVideoModel` |
| 6 | `MusicModel` | ❌ 自实现 | 阿里音乐生成 | `aiMusicModel` |
| 7 | `VoiceChatModel` | ❌ 自实现 | 豆包 / qwen-omni-turbo（端到端语音对话）| `aiVoiceChatModel` |

**关键架构原则**（用户前一条定下）：
- **所有 `*Model` 的 `@Bean` 都在 `AiModelAutoConfiguration.kt`**（无论 Spring AI 提供还是自实现）
- **所有厂商实现在 `provider/` 子包**
- **业务包通过 `@Autowired *Model` 注入使用**
- **用户定义"音频生音频"= 语音对话**（端到端语音 in/out），不是音频风格转换

---

## 2. 现状参考

### 2.1 现有 ai 包结构（atlas-richie-platform/atlas-richie-component/atlas-richie-component-ai）

```
src/main/java/com/richie/component/ai/
├── config/
│   ├── AiModelAutoConfiguration.kt        ← 主要改造点
│   ├── AiModelProperties.kt
│   ├── AiChatClientFactory.kt
│   └── ...
├── support/
│   ├── AiModelCircuitBreaker.kt
│   ├── AiModelRouter.kt
│   ├── ToolRegistry.kt
│   └── AiChatOptionsResolver.kt
├── model/
│   ├── AiResponse.kt
│   ├── AiHealthResult.kt
│   └── AiModelInfo.kt
└── (无 provider/ 子包 — R-N 新增)
```

**关键现状**：

#### `AiModelAutoConfiguration.kt` 已有 2 个 `@Bean`（line 58-69 / 78-94）

```java
@Bean("aiChatClients")
public Map<String, ChatClient> aiChatClients(...) { ... }

@Bean("aiEmbeddingModel")
@Primary
@ConditionalOnMissingBean(EmbeddingModel.class)
public EmbeddingModel aiEmbeddingModel(...) { ... }  // 现有
```

R-N 要**追加 7 个 `@Bean`**，全部镜像这个写法（带 `@Bean("aiXxxModel")` + `@ConditionalOnMissingBean(XxxModel.class)` + `@Primary` 视情况）。

#### `AiChatClientFactory.kt` 是现有 ChatModel 工厂

- `createChatClient(name, AiModel)` 返回 `ChatClient`（注：Spring AI 的 `ChatClient` 包装，不是 `ChatModel`）
- `createEmbeddingModel(name, AiModel)` 返回 `EmbeddingModel`（直接用 Spring AI 接口）
- 私有方法 `createOpenAiEmbeddingModel(...)` 走 OpenAI 兼容协议创建 Spring AI `EmbeddingModel`
- R-N 的 `RerankModel` / `VideoModel` / `MusicModel` / `VoiceChatModel` 可以走同样的 OpenAI 兼容协议模式（阿里千帆 OpenAI 兼容）

### 2.2 现有 vector 包结构（atlas-richie-component-vector）

```
atlas-richie-component-vector/
├── atlas-richie-component-vector-core/           ← VectorService 接口 + RerankService 入口
├── atlas-richie-component-vector-milvus/         ← 已有 Milvus 客户端集成
├── atlas-richie-component-vector-weaviate/
├── atlas-richie-component-vector-redis/
└── (R-N 新增 atlas-richie-component-vector-rerank-core)
```

**关键现状**：
- `vector-core/service/VectorService.java` 是用户调用的业务入口：`addDocument(text)` / `search(query)` / `rerank(query, docs)` （R-N 新增 rerank 方法）
- `vector-milvus/MilvusVectorServiceImpl` 内部 `@Autowired @Qualifier("aiEmbeddingModel")` — **不创建 EmbeddingModel，只消费**
- 这印证了"AI 抽象归 ai 包，业务接口归 vector 包"的分层

### 2.3 测试已跑通的凭证（admin-service 端）

env.txt 提取（admin-service 真实集成测试已用）：
- `DASHSCOPE_API_KEY = sk-ws-H.RXDEREH.Co3U.MEQCIBnLfSBNBxSzm52UzkE-F-v_lYkUH--GDGQAYSp6HJH5AiA43XyhtcPPVai4LgwAcX4zCMUZj2XDsv_vQsJCb4tUsA`
- `DASHSCOPE_BASE_URL = https://dashscope.aliyuncs.com/compatible-mode/v1`
- R-N 厂商实现**直接复用**这些凭证调 OpenAI 兼容端点

---

## 3. R-N 详细任务清单（按优先级）

### 任务 1：新建 `atlas-richie-component-vector-rerank-core` 子模块

**理由**：用户决策"Rerank 业务入口放 vector 包，AI 抽象放 ai 包"（镜像 Embedding 模式）

**步骤**：
1. 在 `atlas-richie-component-vector/pom.xml` 加 `<modules>` 引入新子模块
2. 创建 `atlas-richie-component-vector-rerank-core/`
3. pom.xml 依赖 `atlas-richie-component-vector-core` + `atlas-richie-component-ai`
4. 创建 `src/main/java/com/richie/component/vector/rerank/`
   - `api/RerankModel.kt`（自实现抽象，`extends Model<RerankRequest, RerankResponse>`）
   - `api/RerankRequest.kt`（query + documents + topN）
   - `api/RerankResponse.kt`（results: List<RerankResult> 按 score 降序）
   - `impl/RerankServiceImpl.kt`（`@Service` + `@Autowired RerankModel` + fallback 0.7+0.3 占位）
5. `vector-core/service/RerankService.java` 新增业务接口
6. `MilvusVectorServiceImpl` 增加 `searchAndRerank()` 便捷方法（先 `search()` 后 `rerank()`）
7. 配置 `application-*.yaml` 加 `platform.component.vector.rerank.provider: bailian`

**AI 包层**（atlas-richie-component-ai）：

1. `api/RerankModel.kt` 抽象接口
2. `provider/BailianRerankModel.kt`（阿里百炼 gte-rerank，OpenAI 兼容）
3. `provider/ZhipuRerankModel.kt`（智谱 glm-rerank，OpenAI 兼容）
4. `config/AiModelAutoConfiguration.kt` 加 `@Bean("aiRerankModel")`

### 任务 2：ai 包 6 个多模态 Model `@Bean` 创建

#### 2.1 `ImageModel` (文生图/图生文)

```java
// 镜像 aiEmbeddingModel 写法
@Bean("aiImageModel")
@Primary
@ConditionalOnMissingBean(ImageModel.class)
public ImageModel aiImageModel(AiModelProperties properties, AiChatClientFactory aiChatClientFactory) {
    if (!properties.isConfigInitializationEnabled() || properties.getModels().isEmpty()) {
        return null;
    }
    Entry<String, AiModel> defaultModelEntry = properties.getModels().entrySet().iterator().next();
    return aiChatClientFactory.createImageModel(defaultModelEntry.getKey(), defaultModelEntry.getValue());
}
```

`AiChatClientFactory` 新增 `createImageModel(name, AiModel)` 内部用 Spring AI OpenAI ImageModel（qwen-vl 兼容）。

#### 2.2 `TextToSpeechModel` (TTS)

同上模式。阿里百炼 `cosyvoice` / `sambert` 通过 DashScope TTS API（HTTP REST 返回 mp3）。

#### 2.3 `TranscriptionModel` (STT/ASR)

同上模式。阿里百炼 `paraformer-v2` 通过 DashScope ASR API（multipart 上传音频）。

#### 2.4 `VideoModel` (文生视频/视频理解) - **自实现**

抽象：
```kotlin
interface VideoRequest {
    fun prompt(): String?              // 文生视频用
    fun videoUrl(): String?             // 视频理解用
    fun durationSeconds(): Int          // 5/10
    fun resolution(): String            // "720P" / "1080P"
}
interface VideoResponse {
    fun videoUrl(): String              // 异步任务返回 jobId
    fun jobStatus(): String             // PENDING/RUNNING/SUCCESS/FAILED
}
interface VideoModel : Model<VideoRequest, VideoResponse> {
    override fun call(request: VideoRequest): VideoResponse
}
```

厂商实现：阿里百炼 `wanx-video`（HTTP REST，返回 job_id，轮询或回调）。

#### 2.5 `MusicModel` (文生音乐) - **自实现**

抽象：参考 `VideoModel` 模式。阿里百炼音乐生成 API。

#### 2.6 `VoiceChatModel` (端到端语音对话) - **自实现**（**用户重新定义"音频生音频"**）

抽象：
```kotlin
interface VoiceChatRequest {
    fun inputAudio(): InputStream       // 用户语音流
    fun voice(): String                  // 音色 (male/female/child/custom)
    fun systemPrompt(): String?
}
interface VoiceChatResponse {
    fun outputAudio(): OutputStream      // AI 合成语音流
    fun textTranscript(): String?
}
interface VoiceChatModel : Model<VoiceChatRequest, VoiceChatResponse> {
    override fun call(request: VoiceChatRequest): VoiceChatResponse
}
```

厂商实现：
- `BailianVoiceChatModel`（qwen-omni-turbo，WebSocket 流式）
- 字节豆包语音（WebSocket 流式）
- OpenAI Realtime API（WebSocket 流式）

### 任务 3：统一镜像配置

`AiModelProperties.kt` 加 7 个 model type 默认配置：
```java
private String defaultRerankModel;    // e.g. "gte-rerank"
private String defaultImageModel;     // e.g. "qwen-vl-plus"
private String defaultTtsModel;       // e.g. "cosyvoice-v1"
private String defaultSttModel;       // e.g. "paraformer-v2"
private String defaultVideoModel;     // e.g. "wanx-video"
private String defaultMusicModel;     // e.g. "music-generation"
private String defaultVoiceChatModel; // e.g. "qwen-omni-turbo"
```

`AiModelRouter.kt` 加 7 个路由方法（如果现有 router 是按 model type 路由）。

---

## 4. 验收标准

### 4.1 中台层面

- ✅ `atlas-richie-component-ai/AiModelAutoConfiguration.kt` 9 个 `@Bean`（原 2 + R-N 新 7）
- ✅ 7 个 Model 抽象文件（4 自实现 + 3 镜像 Spring AI）
- ✅ 7 个厂商实现文件（`BailianXxxModel.kt` / `ZhipuXxxModel.kt`）
- ✅ `atlas-richie-component-vector-rerank-core` 子模块完整（含 RerankServiceImpl + VectorService.rerank 业务入口）
- ✅ mvn test 跑通（每个新抽象至少 1 个单测 + 1 个真实集成测试复用 admin 凭证）

### 4.2 admin-service 集成层面（待 R-N 后由 admin 单独 sprint 处理）

- ⏸ 改 `@Autowired` 中台 `RerankServiceImpl` 替代 R-K 占位
- ⏸ `RagConfig` 接 `RerankingDocumentPostProcessor`
- ⏸ 业务代码用 `vectorService.rerank(query, docs)`

### 4.3 用户验收

- 用户决策：先在 R-N 中台完成 7 个新 Model + Rerank 子模块
- 后续 admin-service 集成另外起 Sprint

---

## 5. 关键文件清单（必看）

### 5.1 现状参考（atlas-richie-platform）

| 路径 | 用途 |
|------|------|
| `atlas-richie-component/atlas-richie-component-ai/src/main/java/com/richie/component/ai/config/AiModelAutoConfiguration.kt` | **主要改造点**：加 7 个 `@Bean` |
| `atlas-richie-component/atlas-richie-component-ai/src/main/java/com/richie/component/ai/config/AiModelProperties.kt` | 加 7 个 default model 字段 |
| `atlas-richie-component/atlas-richie-component-ai/src/main/java/com/richie/component/ai/config/AiChatClientFactory.kt` | 现有 ChatClient/EmbeddingModel 工厂；R-N 加 7 个 `create*Model` 方法 |
| `atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-core/src/main/java/com/richie/component/vector/service/VectorService.java` | 加 `rerank(query, docs)` 方法 |
| `atlas-richie-component/atlas-richie-component-vector/atlas-richie-component-vector-milvus/src/main/java/com/richie/component/vector/service/impl/MilvusVectorServiceImpl.java` | line 90-97 已有 `@Qualifier("aiEmbeddingModel")` 镜像写法 |
| `atlas-richie-component/atlas-richie-component-vector/pom.xml` | 加 `<modules>` 引入 vector-rerank-core |

### 5.2 R-N 必新建文件清单

**atlas-richie-component-ai**（R-N 新增 21 个文件）：

```
src/main/java/com/richie/component/ai/
├── api/
│   ├── RerankModel.kt               (新增)
│   ├── VideoModel.kt                (新增, 自实现)
│   ├── MusicModel.kt                (新增, 自实现)
│   └── VoiceChatModel.kt            (新增, 自实现)
├── provider/
│   ├── BailianRerankModel.kt        (新增)
│   ├── BailianImageModel.kt         (新增)
│   ├── BailianTextToSpeechModel.kt  (新增)
│   ├── BailianTranscriptionModel.kt (新增)
│   ├── BailianVideoModel.kt         (新增, 自实现)
│   ├── BailianMusicModel.kt         (新增, 自实现)
│   ├── BailianVoiceChatModel.kt     (新增, 自实现)
│   ├── ZhipuRerankModel.kt          (新增)
│   └── ZhipuImageModel.kt           (新增)
├── support/
│   └── (AiModelRouter.kt 加 7 个路由方法)
└── config/
    ├── AiModelAutoConfiguration.kt  (改: 加 7 个 @Bean)
    └── AiModelProperties.kt         (改: 加 7 个 default 字段)
```

**atlas-richie-component-vector-rerank-core**（R-N 新子模块）：

```
atlas-richie-component-vector/atlas-richie-component-vector-rerank-core/
├── pom.xml                          (依赖 vector-core + ai)
└── src/main/java/com/richie/component/vector/rerank/
    ├── api/
    │   ├── RerankRequest.kt
    │   ├── RerankResponse.kt
    │   ├── RerankResult.kt
    │   └── RerankModel.kt            (与 ai 包 api/RerankModel.kt 一致，或 re-export)
    └── impl/
        └── RerankServiceImpl.kt      (@Autowired RerankModel, fallback 占位)
```

**vector-core**（R-N 改 1 个文件）：

```
src/main/java/com/richie/component/vector/service/
└── VectorService.java               (改: 加 rerank(query, docs) 方法)
```

---

## 6. 开发流程建议

### 6.1 推荐 Sprint 拆分（在中台工程内）

1. **RN.1**（1 天）：RerankModel 抽象 + 厂商实现 + `aiRerankModel` @Bean + 单元测试
2. **RN.2**（1 天）：vector-rerank-core 子模块 + RerankServiceImpl + VectorService.rerank + 集成测试
3. **RN.3**（1 天）：ImageModel + TextToSpeechModel + TranscriptionModel（Spring AI 已提供）+ 3 个 @Bean + 单元测试
4. **RN.4**（1.5 天）：VideoModel + MusicModel + VoiceChatModel 自实现 + 3 个 @Bean + 单元测试（含 WebSocket 流式）
5. **RN.5**（0.5 天）：mvn verify + 真实集成测试（复用 admin 凭证）

**总工作日**：5 天（不含 admin 集成）

### 6.2 关键决策

| 决策 | 用户已定下 | 备注 |
|------|----------|------|
| Rerank 业务接口在 vector 包 | ✅ | 镜像 Embedding |
| Rerank AI 抽象在 ai 包 | ✅ | 与 Chat/Embedding 平级 |
| 所有 `@Bean` 在 `AiModelAutoConfiguration` | ✅ | bean 创建者唯一 |
| "音频生音频"= 语音对话 | ✅ | NEW 抽象 `VoiceChatModel` |
| 真实集成测试用阿里千帆 DashScope 凭证 | ✅ | env.txt 已有 |
| admin-service 集成另起 sprint | ✅ | 不在当前任务 |
| 暂不提交 git | ✅ | 整个项目约束 |

### 6.3 风险点

1. **OpenAI 兼容端点限制**：阿里千帆 DashScope 不是所有 Model 都支持 OpenAI 兼容协议
   - Rerank (gte-rerank): ✅ 走 `/v1/services/aigc/text-rerank` (DashScope 专有 API)
   - 文生图 (qwen-vl, qwen-image): 部分 OpenAI 兼容 (`/v1/images/generations`)，部分 DashScope 专有
   - TTS (cosyvoice): DashScope 专有 API（`/api/v1/services/aigc/multimodal-generation/generation`）
   - STT (paraformer-v2): DashScope 专有 API（`/api/v1/services/audio/asr/transcription`）
   - Video (wanx-video): DashScope 专有异步 API
   - VoiceChat (qwen-omni-turbo): WebSocket 流式

2. **WebSocket 客户端选型**：VoiceChatModel 需要 WebSocket 客户端
   - 建议用 OkHttp WebSocket（已在 admin 依赖链）
   - 或 Java 11 HttpClient WebSocket API（实验性）
   - 或 Spring WebFlux WebSocketClient

3. **Spring AI 2.0 兼容性**：自实现 Model 时镜像 Spring AI `Model<Req, Resp>` 接口（不是新建完全独立的抽象）

---

## 7. 上下文链接

- 项目根：`/Users/richie696/Projects/workspace/atlas-foundry-ai/`
- 中台根：`/Users/richie696/Projects/workspace/atlas-richie-platform/`
- SESSION_LOG：第 13 节有 R-T Sprint 收官完整记录
- 当前会话用户决策序列：
  1. R-T Sprint 跑通 32 单测 + 1 真实 LLM 集成测试
  2. 真实 LLM 测试只用阿里千帆（用户决策：与厂商无关）
  3. Rerank 业务接口放 vector 包（用户决策）
  4. Rerank AI 抽象放 ai 包（用户决策）
  5. 7 个新 Model 在 ai 包（用户决策）
  6. "音频生音频" = 语音对话 NEW 抽象（用户决策）

---

## 8. 一句话总结 R-N 任务

**在 `atlas-richie-component-ai` 镜像 `aiEmbeddingModel` 写法，加 7 个 `@Bean`（`aiRerankModel` / `aiImageModel` / `aiTextToSpeechModel` / `aiTranscriptionModel` / `aiVideoModel` / `aiMusicModel` / `aiVoiceChatModel`），其中 4 个用 Spring AI 已有抽象、4 个自实现抽象（自实现仍放 ai 包），厂商实现在 `provider/` 子包；vector 包加 `vector-rerank-core` 子模块提供 `RerankServiceImpl`（`@Autowired RerankModel`）和 `VectorService.rerank()` 业务入口。**

---

**完成日期**：在新会话里完成所有 5 个 sprint 后，回 atlas-foundry-ai 写 SESSION_LOG §14（"R-N Sprint handoff 完成回 admin-service 集成"），再做 R-O admin-service 集成。
