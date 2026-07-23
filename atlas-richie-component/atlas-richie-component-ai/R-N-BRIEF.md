# R-N Sprint 速查 Brief（粘到中台工程新会话开头）

> **完整文档**：`/Users/richie696/Projects/workspace/atlas-foundry-ai/docs/R-N-HANDOFF.md`（必读）

## 任务一句话

**atlas-richie-component-ai 加 7 个新 Model `@Bean` + 厂商实现 + vector 包加 rerank-core 子模块**。详见 handoff 文档的"第 5.2 节 R-N 必新建文件清单"（21 + 4 个新文件）。

## 必读

- `/Users/richie696/Projects/workspace/atlas-foundry-ai/docs/R-N-HANDOFF.md` — 完整任务清单
- `/Users/richie696/Projects/workspace/atlas-foundry-ai/SESSION_LOG.md` — 第 9-13 节 R-I/J/K/M/L/T 上下文

## 7 个新 `@Bean`（atlas-richie-component-ai/AiModelAutoConfiguration）

| # | Model 抽象 | Spring AI 提供? | 中台 bean 名 | 厂商实现 |
|---|-----------|----------------|------------|---------|
| 1 | `RerankModel` | ❌ 自实现 | `aiRerankModel` | 阿里百炼 gte-rerank / 智谱 glm-rerank |
| 2 | `ImageModel` | ✅ | `aiImageModel` | 阿里百炼 qwen-image / 智谱 CogViewX |
| 3 | `TextToSpeechModel` | ✅ | `aiTextToSpeechModel` | 阿里百炼 cosyvoice / sambert |
| 4 | `TranscriptionModel` | ✅ | `aiTranscriptionModel` | 阿里百炼 paraformer-v2 / sensevoice |
| 5 | `VideoModel` | ❌ 自实现 | `aiVideoModel` | 阿里百炼 wanx-video（文生视频 + 视频理解）|
| 6 | `MusicModel` | ❌ 自实现 | `aiMusicModel` | 阿里百炼音乐生成 |
| 7 | `VoiceChatModel` | ❌ 自实现 | `aiVoiceChatModel` | 豆包 / qwen-omni-turbo（端到端语音对话）|

**关键决策**（用户已定）：
- 所有 `@Bean` 都在 `AiModelAutoConfiguration.kt`（无论 Spring AI 提供还是自实现）
- 自实现抽象仍放 `atlas-richie-component-ai/api/`（与 `ChatModel` / `EmbeddingModel` 平级）
- 厂商实现放 `atlas-richie-component-ai/provider/`
- 业务包通过 `@Autowired *Model` 注入
- "音频生音频"重新定义为**语音对话**（端到端语音 in/out），NEW 抽象 `VoiceChatModel`

## 复用凭证（真实集成测试）

- `DASHSCOPE_API_KEY = sk-ws-H.RXDEREH.Co3U.MEQCIBnLfSBNBxSzm52UzkE-F-v_lYkUH--GDGQAYSp6HJH5AiA43XyhtcPPVai4LgwAcX4zCMUZj2XDsv_vQsJCb4tUsA`
- `DASHSCOPE_BASE_URL = https://dashscope.aliyuncs.com/compatible-mode/v1`

## 主要改造点

| 路径 | 改动 |
|------|------|
| `atlas-richie-component-ai/src/main/java/com/richie/component/ai/config/AiModelAutoConfiguration.kt` | **加 7 个 `@Bean`**（line 78-94 已有 aiEmbeddingModel 写法，镜像）|
| `atlas-richie-component-ai/src/main/java/com/richie/component/ai/config/AiModelProperties.kt` | 加 7 个 default model 字段 |
| `atlas-richie-component-ai/src/main/java/com/richie/component/ai/config/AiChatClientFactory.kt` | 加 7 个 `create*Model(name, AiModel)` 工厂方法 |
| `atlas-richie-component-vector/atlas-richie-component-vector-core/src/main/java/com/richie/component/vector/service/VectorService.java` | **加 `rerank(query, docs)` 业务方法** |
| `atlas-richie-component-vector/atlas-richie-component-vector-milvus/src/main/java/com/richie/component/vector/service/impl/MilvusVectorServiceImpl.java` | 加 `searchAndRerank()` 便捷方法（line 90-97 已有 `@Qualifier("aiEmbeddingModel")` 镜像） |
| **新子模块** `atlas-richie-component-vector/atlas-richie-component-vector-rerank-core/` | 加 4 文件（`RerankRequest/Response/Result/Model` + `RerankServiceImpl`）|

## 镜像写法（参考现有 aiEmbeddingModel）

```java
@Bean("aiImageModel")
@Primary
@ConditionalOnMissingBean(ImageModel.class)
public ImageModel aiImageModel(AiModelProperties properties, AiChatClientFactory aiChatClientFactory) {
    if (!properties.isConfigInitializationEnabled()) {
        return null;
    }
    if (properties.getModels() == null || properties.getModels().isEmpty()) {
        return null;
    }
    Entry<String, AiModelProperties.AiModel> defaultModelEntry = properties.getModels().entrySet().iterator().next();
    return aiChatClientFactory.createImageModel(defaultModelEntry.getKey(), defaultModelEntry.getValue());
}
```

## 状态

- 后端 v31 跑通（admin-service 8898 LISTEN，scheduler 干净）
- 33 个 R-T 测试全 pass
- 暂不提交 git
- R-N 在中台仓库进行；完成后再回 admin-service 集成（另起 Sprint）

## R-N 子任务拆分（推荐 5 天）

1. RN.1 RerankModel 自实现 + 厂商（1 天）
2. RN.2 vector-rerank-core 子模块 + 集成（1 天）
3. RN.3 ImageModel + TTS + STT（Spring AI 已提供，1 天）
4. RN.4 VideoModel + MusicModel + VoiceChatModel 自实现（1.5 天）
5. RN.5 mvn verify + 真实集成测试（0.5 天）
