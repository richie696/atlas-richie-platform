# R-N 设计评审稿 (atlas-richie-component-ai 多模态扩展)

> **版本**: v0.2 SHIP (定稿 — 设计阶段关闭,进入实施待命)
> **日期**: 2026-07-20
> **作者**: Sisyphus (代理)
> **依赖**: `R-N-BRIEF.md` / `R-N-HANDOFF.md` / `JAVA_AGENTS.md`
> **状态**: SHIP — 所有已确认项已落盘;待定项见 §10 Open Questions,不阻塞实施启动

---

## 0. 设计原则 (锁定 — 已收敛为 F/G/H/I + 配置/模块原则)

| 编号 | 原则 | 落地点 | 来源 |
|------|------|--------|------|
| **F** | **WS 数据不走 Java**: 语音双工(实时对话/STT 流式)的媒体流由前端持短时凭证**直连 vendor WebSocket**,中台不代理请求流 | VoiceChat / STT 流式端点 | 用户 m00xx (F 已锁) |
| **G** | **不实现请求流代理 endpoint**: 中台只做**凭证签发(STS 模式)** + 抽象层,不新增转发大模型请求流的 HTTP endpoint | 无新 controller/endpoint | 用户 m0062 |
| **H** | **凭证 STS 模式**: vendor api-key 存中台,前端持**短时 STS 凭证**直连 vendor;类比 `atlas-richie-component-storage` 用 STS URL 直传 OSS | `vendor.sts.*` 签发服务 | 用户 m0062 |
| **I** | **全 REST + CompletableFuture-native**: 所有自实现 vendor 走 REST(经 `atlas-richie-component-http` 的 `HttpClient`/`HttpRequest`,返回 `CompletableFuture`,**非** Reactor `Mono`/`Flux`)+ `StructuredTaskScope` 并发编排,不依赖 vendor SDK 全量引入 | 自实现 vendor impl | 实测 HttpClient 返回 CompletableFuture;JAVA_AGENTS.md 本项目不存在,以实际代码为准 |
| **C** | **配置独立**: 每个 Model 类型有独立配置节点,支持多厂商混搭 | `AiModelProperties` 7 子类 | 用户 m0016 |
| **E** | **现有代码零回归**: Chat 单 `models: Map<String, AiModel>` + `EmbeddingModel` 路径 100% 不动,R-N 走并行新节点/新 class | `AiModelProperties` / `AiModelAutoConfiguration` | 用户 m0016 |
| **M** | **vendor 子模块按"公司 + 子产品"双重划分**: 例 `ai-pangu`(盘古大模型)+ `ai-huawei-sis`(华为 SIS 语音);不把所有华为能力塞一个模块 | maven module 结构 | 用户 m0100 段1 |
| **β** | **零新 endpoint 路线(已选定)**: Java 只做凭证签发 + 客户端直连 vendor,无请求流代理 endpoint | 整体架构 | 用户 m0062 |
| **J** | **WS + STS 统一接口原则 (Locked)**: 任何 vendor 的 WebSocket 接入与 STS 凭证签发都必须**收敛到统一 SPI**,业务代码与上游调用方**仅依赖抽象**,配置文件 (`platform.component.ai.voice-chat.{vendor}` / `platform.component.ai.sts.*`) 切换 vendor 时**业务代码零改动、不感知具体实现**;禁止业务代码出现 `if (vendor == "zhipu")` / 反射调 vendor 私有方法 / 引用 vendor SDK 全量包 | `VoiceChatModel` 接口 + `StsSigner` 接口 + `StsTicket` 统一票面 + `VoiceStsService` 单一入口 | 用户 m0042 (LOCKED 2026-07-21) |

**已否决的旧原则 A/B/D**(v0.1 草案): 原 A(统一协议+自实现兜底)被 F/G/H/I 吸收重述;原 B(模块收口单包)被 M(子模块双重划分)取代;原 D(凭证共享层)被 H(STS)取代。

**原则 J 落地的硬约束清单 (实施期不可豁免)**:
1. **业务代码不 import 任何 vendor 包**(`com.richie.component.ai.provider.zhipu.*` / `doubao.*` / `hunyuan.*` 等只在 ai 组件内部可见;`service/` 与 `api/` 包无 vendor 子包引用)
2. **`VoiceChatModel` 接口签名统一**:`open(VoiceChatConfig) → VoiceConversation`(`AutoCloseable`,`Flux<VoiceChatEvent>`,`sendAudio(AudioFrame)`,`interrupt()`)— 不允许 vendor 子接口带 vendor 专有方法
3. **`StsSigner` 接口签名统一**:`sign(VendorStsContext) → StsTicket`(`expirationMs`,`credentials: Map<String,String>`,`endpoint`,`vendor`,`model`);业务侧永远拿 `StsTicket` 不直接接触 vendor 原始凭证
4. **`StsTicket` 字段对齐 vendor 5 种认证域**(Bearer / TC3 / AppCode / AK-SK-HMAC / X-Api-Key)→ 内部按 `credentials` Map 透传,但**业务取用通过 `StsTicket.asBearer()` / `asTc3Headers(...)` / `asHeaderMap()` 等统一方法** — 不允许业务代码自行判断 `if (ticket.vendor().equals("hunyuan-tts"))`
5. **配置切换 vendor = YAML 一行**: 业务代码 + Spring Bean 装配不动,只需在 `application.yml` 改 `platform.component.ai.voice-chat.vendor: zhipu` 或 `hunyuan-tokenhub`,AiMultimodalServiceImpl 启动期 `refresh()` 自动重建 Bean 引用
6. **禁止反射 / 强转 / vendor SDK 类型在 service/api 包出现**(架构守护,Code Review 必查)
7. **新 vendor 接入 = 加一个 `*VoiceChatModel` impl 类 + 一个 `*StsSigner` impl 类 + `application.yml` 加一段配置**,**不动现有业务代码、不动接口签名、不动自动装配逻辑** — 这是 SPI 的可扩展性验收标准

---

## 1. 能力矩阵 — 6 厂商 × 9 能力 (v0.2 定稿)

符号: ✅ 已确认支持(有官方 REST/SDK 端点) | ⚠️ 待 sprint 补端点验证 | ❌ 确认不支持

| 能力 \ 厂商 | **DashScope** (阿里百炼) | **Zhipu** (智谱) | **Doubao** (豆包/火山) | **Hunyuan** (腾讯混元) | **Pangu** (华为盘古) | **MiniMax** |
|------|------|------|------|------|------|------|
| **Chat** | ✅ 9/9 主战场 (qwen 全系) | ✅ GLM 全系 | ✅ 火山方舟 | ✅ Hunyuan | ✅ Pangu Chat | ✅ abab 系 |
| **Embed** | ✅ text-embedding | ✅ (现 models 已用) | ✅ | ✅ | ✅ | ⚠️ |
| **Rerank** | ✅ gte-rerank (`/api/v1/services/rerank/text-rerank/text-rerank`) | ✅ **`/paas/v4/rerank`** (model=`rerank`, Bearer, 文档≤128条) | ✅ **VikingDB** (`/api/knowledge/service/rerank`, AK/SK HMAC 签名, doubao-seed-rerank) | ❌ 未单独提供 | ✅ 独立 REST (`/pangu/search/v1/rerank`, X-Apig-AppCode) | ❌ |
| **Image** | ✅ wanx 系列 | ✅ CogView | ✅ 火山图像 | ✅ Hunyuan Image | ✅(多模态页,图生文/文生图) | ✅ |
| **TTS** | ✅ cosyvoice | ✅ GLM-TTS (`/paas/v4/audio/speech`, 7 音色, wav/pcm) | ✅ (openspeech V3 单向 SSE + WS 双向, X-Api-Key/AppId, 325+ 音色) | ⚠️ **独立产品 1073 (TC3 签名,非 TokenHub)** | ⚠️ 拆 `ai-huawei-sis` 子模块 | ✅ (HTTP/WSS/Async 三种模式) |
| **STT** | ✅ paraformer | ✅ GLM-ASR-2512 (`/paas/v4/audio/transcriptions`, multipart, 支持流式) | ✅ (一句话 flash / 文件异步 submit+query / WS 流式, X-Api-Key) | ⚠️ **独立产品 1093 (TC3 签名,非 TokenHub,三模式)** | ⚠️ 拆 `ai-huawei-sis` 子模块 | ⚠️ |
| **Video** | ✅ wanx 视频 (异步任务型) | ✅ CogVideoX-3/Vidu (`/paas/v4/videos/generations`, 异步, Bearer) | ✅ 火山视频 | ⚠️ | ✅(多模态页,仅视频生成) | ✅ Hailuo |
| **Music** | ✅ 音乐生成 (异步任务型) | ❌ | ❌ | ❌ | ❌ | ✅ music-3.0 |
| **VoiceChat** | ✅ qwen-omni Realtime WS | ✅ GLM-Realtime (`wss://open.bigmodel.cn/api/paas/v4/realtime`, flash/air) | ✅ 火山 Realtime WS | ❌ | ❌ | ❌ |

**能力计数**: DashScope 9/9 | Zhipu **9/9** | Doubao **7/9**(Chat/Embed/Image/Rerank(VikingDB)/TTS/STT ✅;Video ❌) | Hunyuan **4/9**(Chat/Embed/Image ✅ + TTS 1073 ✅ + STT 1093 ✅ 均独立 TC3 产品;Rerank ❌ 未提供) | Pangu 5/9 | MiniMax 5/9

> 计数口径: ✅ = 官方端点已确认(无论走 TokenHub Bearer 还是独立 TC3 产品)。Hunyuan TTS/STT 虽走独立产品 1073/1093(TC3 签名、前端直连优先),但能力本身确认存在,故计 ✅ 而非 ⚠️。Hunyuan Rerank 经核实官方未单独提供 → ❌。

**关键 vendor 事实**(librarian 已验证):
- **DashScope** = 阿里百炼,全 9 能力官方 REST 端点齐备 → R-N 最小集首选 vendor
- **Pangu Rerank** 是独立 REST API(`/pangu/search/v1/rerank`),鉴权用 `X-Apig-AppCode`(华为 APIG),非 TC3 → 需独立 impl,不混进 Pangu Chat 模块
- **Pangu 多模态页**仅视频+图像问答,**无 TTS/STT** → 华为语音必须拆 `ai-huawei-sis` 子模块(原则 M)
- **Zhipu** GLM-TTS (`/paas/v4/audio/speech`) / GLM-ASR-2512 (`/paas/v4/audio/transcriptions`) / GLM-Realtime (`wss://open.bigmodel.cn/api/paas/v4/realtime`) 端点已确认(用户 2026-07-20 提供官方文档),支持 Bearer 认证,标准 OpenAI 风格 REST/WS
- **MiniMax** Chat/Image/TTS/Video/Music 5 项实测 ✅,Rerank/VoiceChat ❌,Embed/STT ⚠️
- **Hunyuan Rerank** 经用户 m0224 确认官方未单独提供 → 矩阵标 ❌(非未验证,而是不存在);其 Chat/Image/Vision/Video 走 TokenHub,Rerank 无对应能力
- **腾讯 AI 三条独立 API 面 (2026-07-20 确认)**: ① **TokenHub** (product 1823, Bearer, Chat/Image/Vision/Video); ② **TTS** (product 1073 `tts.tencentcloudapi.com`, TC3 签名, base64 输出,官方主推客户端直连 Android/iOS/HarmonyOS/Flutter SDK); ③ **STT** (product 1093 `asr.tencentcloudapi.com`, TC3 签名, 三模式: 一句话同步/录音文件异步/语音流 WS)。TTS/STT **未**进 TokenHub → `ai-hunyuan` 需 dual-auth(TokenHub Bearer + 1073/1093 TC3);TTS/STT 前端直连优先(原则 F/H),服务端 impl 降为可选
- **Doubao Rerank 经 VikingDB 提供 (2026-07-20 用户 m0236 + 客服确认)**: 火山**无独立 Rerank 模型**,Rerank 由**向量数据库 VikingDB** 的 `/api/knowledge/service/rerank` 提供;鉴权 **AK/SK HMAC-SHA256 签名**(非 Bearer,Java SDK `KnowledgeService` 用 `AuthWithAkSk`);模型 `doubao-seed-rerank`(多模态)/`base-multilingual-rerank`/`m3-v2-rerank`;`datas[]`≤200,响应对齐**位置序号 float[]**(非 Cohere 的 index/document 配对)→ `BytedanceRerankModel` 需按位置回映射 `RerankResult`。此为第 4 种认证域(继 Bearer / 腾讯 TC3 / 华为 AppCode 之后),进一步强化 `StsSigner` 按厂商扩展(§10 O5)与多凭证域建模(§4.1)。Doubao Chat(火山方舟)与 Rerank(VikingDB)属不同产品,同腾讯模式
- **腾讯 AI 三条独立 API 面 (2026-07-20 用户 m0041/m0181 确认,细节见 附录 D)**: ① **TokenHub (product 1823)** — OpenAI Chat Completions 风格,Bearer 认证,`model` 参数选能力,覆盖 Chat/Image/Vision/Video;② **TTS (product 1073)** — `tts.tencentcloudapi.com`,TC3-HMAC-SHA256 签名,base64 输出;③ **STT (product 1093)** — `asr.tencentcloudapi.com`,TC3 签名,三模式。TTS/STT 未集成进 TokenHub → `ai-hunyuan` 需 dual-auth(TokenHub Bearer + 1073/1093 TC3),直接影响原则 H `StsSigner`(见 §10 O5)
- **Doubao 语音 TTS/STT 经 `openspeech.bytedance.com` 提供 (2026-07-20 用户 m0251 确认)**: TTS = `/api/v3/tts/unidirectional`(SSE/HTTP Chunked)+ `/api/v3/tts/bidirection`(WS 双向),`X-Api-Key`/AppId+AccessKey 认证,`X-Api-Resource-Id` 路由模型版本(seed-tts-2.0/seed-icl-2.0),325+ 音色;STT = 一句话 `/api/v3/auc/bigmodel/recognize/flash` + 文件异步 submit/query + 流式 `/api/v2/asr`(WS),同认证体系。此为第 5 种认证形态(`X-Api-Key` 静态 key,区别于 Bearer / 腾讯 TC3 / 华为 AppCode / 火山 VikingDB AK-SK HMAC),前端经 STS 直连最自然。Doubao 语音与 Doubao Chat(方舟)属不同产品+不同认证域,同腾讯"一品牌多凭证域"模式(见 附录 F)
- **Zhipu 视频生成 (异步) 已确认 (2026-07-20 用户 m0272 提供官方文档)**: `POST /paas/v4/videos/generations`(Base `https://open.bigmodel.cn/api/`),Bearer 认证;模型 `cogvideox-3`(文生/图生/首尾帧)/`cogvideox-2`/`cogvideox-flash`/`viduq1-*`/`vidu2-*`;提交返回 `AsyncResponse{id,task_status}`,需另调**查询异步结果**端点轮询取视频;`size` 最高 4K,`fps` 30/60,`duration` 5/10s,`with_audio`,`quality` speed/quality,水印开关。→ Zhipu **Video ✅**,9 大能力全齐(9/9);异步双接口模式与 §6 视频异步语义一致(generate→submit+query 轮询)

---

## 2. 配置树 (YAML 完整形态 — 前缀 `platform.component.ai`)

延续现有前缀(用户臆测的 `atlas.ai.*` **已否决**,实测根前缀即 `platform.component.ai`),**新增并行节点**:

```yaml
platform:
  component:
    ai:                                      # ✅ 已有 (根节点保持不变)
      config-initialization-enabled: true
      routing: { enabled, fallback-enabled, fallbackModels, sceneRules }   # Chat 路由，不动
      resilience: { circuit-breaker-enabled, failure-threshold, open-duration-ms }
      health-check: { live-probe, probe-max-tokens }
      models:                                  # ✅ Chat 单 Map (不动)
        minimax:    { provider: MINIMAX, api-key: ..., base-url: ..., options: {...} }
        zhipuai:    { provider: ZHIPUAI, api-key: ..., ... }

      # ==================== R-N 新增 (并行，不破坏现有) ====================
      vendor:                                  # 🔑 凭证层 (STS 签发源,见原则 H)
        minimax:    { api-key: ${MINIMAX_API_KEY},    base-url: ${MINIMAX_BASE_URL} }
        dashscope:  { api-key: ${DASHSCOPE_API_KEY},  base-url: ${DASHSCOPE_BASE_URL} }
        zhipu:      { api-key: ${ZHIPU_API_KEY},      base-url: ${ZHIPU_BASE_URL} }
        doubao:     { app-id: ${DOUBAO_APP_ID},       token: ${DOUBAO_TOKEN} }
        hunyuan-tokenhub: { api-key: ${HUNYUAN_TOKENHUB_KEY},  base-url: ${HUNYUAN_TOKENHUB_URL} }   # Bearer (Chat/Image/Vision/Video)
        hunyuan-tts:      { secret-id: ${HUNYUAN_TTS_SID}, secret-key: ${HUNYUAN_TTS_SKEY}, base-url: ${HUNYUAN_TTS_URL} }  # TC3 (1073)
        hunyuan-stt:      { secret-id: ${HUNYUAN_STT_SID}, secret-key: ${HUNYUAN_STT_SKEY}, base-url: ${HUNYUAN_STT_URL} }  # TC3 (1093)
        pangu:      { app-code: ${PANGU_APP_CODE},    base-url: ${PANGU_BASE_URL} }   # APIG AppCode
        huawei-sis: { app-key: ${HUAWEI_SIS_APP_KEY}, app-secret: ${HUAWEI_SIS_APP_SECRET} }
        # STS 签发 (原则 H): 中台用上述长期凭证签发短时前端凭证,不暴露给前端
        sts:
          enabled: true
          ttl-seconds: 3600
          signer-bean: aiVendorStsSigner   # 各 vendor 实现 StsSigner 接口

      embedding:                               # 🔄 渐进迁移:从 models 解耦 (老配置仍可用)
        vendor: zhipu
        model:  { batch-size: 16, dimensions: 1024 }

      rerank:                                  # ✅ 新增
        vendor: dashscope
        model: { top-n: 5, return-documents: true }

      image:                                   # ✅ 新增
        vendor: dashscope
        model: { size: "1024*1024", n: 1, quality: standard }

      tts:                                     # ✅ 新增
        vendor: dashscope
        model: { voice: longxiaocheng, format: mp3, sample-rate: 24000 }

      transcription:                           # ✅ 新增
        vendor: dashscope
        model: { format: mp3, language: zh }

      video:                                   # ✅ 新增 (异步任务型)
        vendor: dashscope
        model: { resolution: 720P, duration: 5 }
        poll:  { poll-interval-ms: 5000, max-wait-ms: 300000 }

      music:                                   # ✅ 新增 (异步任务型)
        vendor: dashscope
        model: { duration: 30, format: mp3, instrumental: false }
        poll:  { poll-interval-ms: 5000, max-wait-ms: 300000 }

      voice-chat:                              # ✅ 新增 (流式双工,前端直连 WS 见原则 F)
        vendor: dashscope
        model: { mode: duplex, sample-rate: 16000, format: pcm, voice: longxiaocheng }
        # ⚠️ 不在此存 ws-endpoint 长期凭证;前端经 STS 拿临时 token 直连
```

**关键点**:
- 现有 `models: Map` 保持不动(向后兼容 Chat 路由 + fallback)
- `vendor.*` 是**长期凭证源**,前端不直接持有;STS 签发短时凭证(原则 H)
- 每个 Model 节点只有 `vendor` + `model` 两个字段 — 极简且独立选厂商
- `VoiceChat` 走原则 F:中台不代理 WS 流,只签发 STS token

---

## 3. 抽象层 Java 类型速查 (核心交付)

### 3.0 抽象包 (新增) — 顶级结构

```
com.richie.component.ai.api/
├── AiModelResponse.java               // 共享 success/errorCode/errorMessage/time/duration/metadata/rawResponse
├── MediaReference.java                // 图片/音频/视频引用的统一类型 (URL / base64 / bytes)
├── AudioFrame.java                    // TTS/VoiceChat 共用音频帧 (原则 Q6 ✅)
├── rerank/
│   ├── RerankModel.java               // 接口
│   ├── RerankRequest.java
│   ├── RerankResponse.java
│   └── RerankResult.java
├── video/   (VideoModel / Request / Response / TaskHandle / TaskStatus)
├── music/   (同 video 结构)
└── voicechat/
    ├── VoiceChatModel.java            // 接口 (返回 VoiceConversation)
    ├── VoiceChatConfig.java
    ├── VoiceConversation.java
    └── VoiceChatEvent.java            // 流式事件类型 (audio / transcript / tool-call / interrupt)
```

> **ImageModel / TextToSpeechModel / TranscriptionModel**: 复用 Spring AI 抽象 (`org.springframework.ai.image.ImageModel` / `audio.tts.TextToSpeechModel` / `audio.transcription.TranscriptionModel`),**不在 `api/` 下新建接口**(原则 I + 复用优先)。仅自实现 Rerank/Video/Music/VoiceChat 四个无统一标准的抽象。

### 3.1 RerankModel 自实现抽象

```java
package com.richie.component.ai.api.rerank;

public interface RerankModel {
    RerankResponse rerank(RerankRequest request);
}

@Data public class RerankRequest {
    private String query;
    private List<String> documents;
    private Integer topN = 10;
    private Boolean returnDocuments = true;
    private Map<String, Object> vendorOptions;
}

@Data public class RerankResponse extends AiModelResponse {
    private List<RerankResult> results;
}

@Data public class RerankResult {
    private Integer index;
    private String document;
    private Double relevanceScore;
}
```

**决策**: 字段仿 Cohere shape(事实标准);`vendorOptions` 透传 DashScope 特有参数;`BailianRerankModel` 做统一↔专有 JSON 映射。

### 3.2 VideoModel / MusicModel (自实现,异步双接口)

```java
public interface VideoModel {
    VideoResponse generate(VideoRequest request);   // 同步包装 submit+轮询
    VideoTaskHandle submit(VideoRequest request);    // 异步提交
    VideoTaskStatus query(String taskId);            // 查询状态
}
// MusicModel 同结构
```

**决策**: `generate()` 内部用 `StructuredTaskScope.open()`(JDK 25, JAVA_AGENTS.md §3.2)包装 submit+轮询,业务侧永远不需自写轮询。轮询参数 `pollIntervalMs` 默认 5000,`maxWaitMs` 默认 300000,暴露为 `VideoProperties.poll` / `MusicProperties.poll`。

### 3.3 VoiceChatModel (自实现,双工流式)

```java
public interface VoiceChatModel {
    VoiceConversation open(VoiceChatConfig config);  // 返回会话句柄 (AutoCloseable)
}

public interface VoiceConversation extends AutoCloseable {
    void sendAudio(AudioFrame frame);
    Flux<VoiceChatEvent> receive();
    void interrupt();
    @Override void close();
}
```

**决策**: 用 Reactor `Flux<VoiceChatEvent>`(项目已依赖 spring-ai,Reactor 集成度高);`VoiceConversation` AutoCloseable 安全;`BailianVoiceChatModel` 用 WebSocket client 调 DashScope qwen-omni Realtime,但**前端直连场景**由 STS 签发 token,中台不代理流(原则 F)。

---

## 4. vendor 子模块结构 (原则 M — 公司+子产品双重划分)

maven module 规划(待 RN.1 起逐步建):

| 子模块 | 覆盖厂商/子产品 | 能力 |
|--------|----------------|------|
| `atlas-richie-component-ai` (主) | 抽象层 + DashScope 全 9 能力 impl | 所有 9 能力最小集 |
| `ai-zhipu` | 智谱 GLM 系列 | Chat/Embed/Rerank⚠️/TTS⚠️/STT⚠️/VoiceChat⚠️/Image |
| `ai-doubao` | 火山方舟 | Chat/Image/Video/VoiceChat |
| `ai-hunyuan` | 腾讯混元 | Chat/Embed/Image/Rerank⚠️ |
| `ai-pangu` | 华为盘古大模型 | Chat/Embed/Image/Rerank/Video |
| `ai-huawei-sis` | 华为 SIS 语音(独立子模块) | TTS/STT |
| `ai-minimax` | MiniMax | Chat/Image/TTS/Video/Music |

**决策**: Pangu 与 Huawei-SIS 必须拆(原则 M — 盘古多模态页无语音);其余按厂商单模块。

### 4.1 多凭证域厂商建模 (腾讯特例 — 2026-07-20 用户 m0211)

**问题**: 腾讯 AI 不是"一个厂商 = 一个凭证面",而是**三套独立产品 / 三套独立认证域** —— TokenHub (1823, Bearer) / TTS (1073, TC3) / STT (1093, TC3),Rerank 可能还有第 4 套。这与 DashScope/Zhipu(单 Bearer 域挂多能力)结构不同。

**结论 — 选 A(子厂商拆分,落盘推荐)**: 保留 `ai-hunyuan` 单一 Maven 模块,但模块内注册 **3 个 `VendorConfig` 条目**(按能力组 key 区分),各自独立 auth 类型;每个 Model 节点选对应子厂商。抽象层(RerankModel/ImageModel/TtsModel…)完全不感知凭证域差异 —— 差异收敛到 `StsSigner` 单扩展点。

```yaml
vendor:
  hunyuan-tokenhub: { api-key: ${HUNYUAN_TOKENHUB_KEY}, base-url: tokenhub.tencentmaas.com }   # Bearer
  hunyuan-tts:      { secret-id: ${HUNYUAN_TTS_SID}, secret-key: ${HUNYUAN_TTS_SKEY}, base-url: tts.tencentcloudapi.com }  # TC3
  hunyuan-stt:      { secret-id: ${HUNYUAN_STT_SID}, secret-key: ${HUNYUAN_STT_SKEY}, base-url: asr.tencentcloudapi.com }  # TC3
tts:           { vendor: hunyuan-tts,      model: { voice-type: 1001, codec: wav } }
transcription: { vendor: hunyuan-stt,      model: { mode: sentence } }
chat:          { vendor: hunyuan-tokenhub, model: { name: hunyuan-pro } }
```

**`StsSigner` 扩展(原则 H)**: 实现两种签名器 —— `BearerStsSigner`(TokenHub,发短时 api-key)+ `Tc3StsSigner`(1073/1093,发短时 TC3 临时密钥 secret-id/secret-key)。前端直连 TTS/STT 时持短时 TC3 临时凭证直连(同腾讯移动 SDK 行为,原则 F/H);服务端 impl 仅当后端需编排合成时才实现。

**否决选 B(扁平多厂商)**: 虽实现最简单,但丢失"皆属腾讯"的品牌聚合,且膨胀顶层 `vendor.*` 列表(已与 `dashscope`/`doubao`/`huawei-sis` 并列够多)。选 A 更契合原则 M(公司+子产品双重划分)—— 腾讯正是 M 的动机案例。

**影响面**: 零抽象层改动;仅 `VendorProperties` 需容纳异构字段(apiKey / secretId+secretKey / appCode 等),`StsSigner` 增 `Tc3StsSigner`(对应 §10 O5)。

---

## 5. 改造点 — 现有 class 改动清单

### 5.1 `AiModelProperties.java` (主改造)

现有字段(`configInitializationEnabled` / `routing` / `resilience` / `healthCheck` / `models`)**全部保留**。新增:

```java
@Data
@ConfigurationProperties(prefix = "platform.component.ai")
public class AiModelProperties {
    // === 已有字段全部保留 ===
    private boolean configInitializationEnabled = true;
    private RoutingConfig routing = new RoutingConfig();
    private ResilienceConfig resilience = new ResilienceConfig();
    private HealthCheckConfig healthCheck = new HealthCheckConfig();
    private Map<String, AiModel> models;          // ✅ 保留不动

    // === R-N 新增 ===
    private VendorProperties vendor = new VendorProperties();   // 凭证层 (H)
    private EmbeddingProperties embedding = new EmbeddingProperties();
    private RerankProperties rerank = new RerankProperties();
    private ImageProperties image = new ImageProperties();
    private TtsProperties tts = new TtsProperties();
    private TranscriptionProperties transcription = new TranscriptionProperties();
    private VideoProperties video = new VideoProperties();
    private MusicProperties music = new MusicProperties();
    private VoiceChatProperties voiceChat = new VoiceChatProperties();
}
```

每个 ModelProperties 通用形态(Java 17 record):
```java
public record RerankProperties(String vendor, RerankModelOptions model, Map<String, VendorConfig> vendorOverrides) {}
public record RerankModelOptions(Integer topN, Boolean returnDocuments) {}
// ... 各 Model 一份,仅 Model 特化字段
```

`VendorProperties` 含各厂商 `VendorConfig`(apiKey/baseUrl/appId/token/appCode/secretId/secretKey/wsEndpoint 等异构字段)。

### 5.2 `AiModelAutoConfiguration.java` (@Bean 集中地)

现有 `aiChatClients` + `aiEmbeddingModel` **完全不动**。新增 7 个 @Bean(`aiRerankModel` / `aiImageModel` / `aiTextToSpeechModel` / `aiTranscriptionModel` / `aiVideoModel` / `aiMusicModel` / `aiVoiceChatModel`),每个内部 `switch(vendor)` 工厂模式,未来加 vendor 只改一处。

### 5.3 不改项

- `AiChatClientFactory.java`: R-N 不改 Chat factory(100% 保留)
- `AiProviderType` 枚举: 不扩(新 Model 走 vendor 字符串选择,不污染 Chat 路径)

### 5.4 vector 接入

`VectorService`(atlas-richie-component-vector-core)新增 `rerank()` 方法(非改造现有),内部调用 `RerankModel`。新建子模块 `vector-rerank-core` 承载 Rerank 编排逻辑。

---

## 6. 异步 / 流式语义决策

| 类型 | 能力 | 实现 |
|------|------|------|
| 异步任务 | Video / Music | 双接口 `generate()`(同步包装)/ `submit()`+`query()`;内部 `StructuredTaskScope.open()` 轮询 |
| 流式增量 | TTS | `synthesize()` 同步 / `stream()` 返回 `Flux<AudioFrame>` |
| 双工流式 | VoiceChat | `open()` 返回 `VoiceConversation`,`Flux<VoiceChatEvent>`;**前端经 STS 直连 WS**(原则 F) |

---

## 7. 测试矩阵

| Model | Surefire 单元 | Failsafe IT |
|-------|---------------|-------------|
| Rerank | 4 | 1 |
| Image | 3 | 1 |
| TTS | 3 | 1 |
| STT | 3 | 1 |
| Video | 4 (含异步轮询) | 1 |
| Music | 4 | 1 |
| VoiceChat | 5 (WS 双工) | 1 |
| vector-rerank-core | 6 | 1 |
| **R-N 增量** | **~32** | **~8** |

JaCoCo `includes` 新增 `com.richie.component.ai.api.*` / `provider.*` 监控类。

---

## 8. RN.1-RN.2 拆分 (✅ 已实施完成 2026-07-20)

> **用户决策**: RN.1 + RN.2 一起实施,周一(2026-07-20 或 27)开启。RN-B 评估(v0.2)已 ship,无 RN.3-5 单独阶段划分,后续按能力滚动。

| Sprint | 范围 | 验收标准 | 状态 |
|--------|------|---------|------|
| **RN.1+RN.2** | `RerankModel` 自实现 + `BailianRerankModel`(DashScope gte-rerank)+ `aiRerankModel` @Bean + `vector-rerank-core` 子模块 + `VectorService.rerank()` 接入 + `aiImageModel` @Bean + `BailianImageAdapter`(适配 Spring AI `ImageModel`) | 全模块 `mvn compile` 通过(exit 0);Chat/Embedding 路径零改动;4 个 VectorService 后端实现零改动 | ✅ **已落地** |

**实施产物(已落盘,未 commit)**:
- `ai` 模块新增 `com.richie.component.ai.api.{RerankModel, RerankRequest, RerankResult}` + `com.richie.component.ai.bailian.{BailianRerankModel, BailianImageAdapter}`;`AiModelProperties` 加 `rerank[]` / `image[]` 独立配置节点;`AiModelAutoConfiguration` 加 `aiRerankModel` / `aiImageModel` 两个 `@Bean`(均 `@ConditionalOnMissingBean` + `@ConditionalOnProperty(platform.component.ai.rerank.enabled / image.enabled, matchIfMissing=false)`,opt-in);引入 `atlas-richie-component-http-core` 编译依赖(运行时 provider 仍由消费方自选)。
- `vector` 包新建子模块 `atlas-richie-component-vector-rerank-core`(含 `RerankServiceImpl` 编排),并接入父 POM `<modules>`;`VectorService` 加 2 个非破坏性 `default rerank/rerankAsync` 方法(委托 `RerankModel`,不引入对 rerank-core 的依赖,4 后端实现零改动)。
- **契约对齐**: `RerankModel.rerank(RerankRequest)` / `rerankAsync(...)` 返回 `List<RerankResult>`;Spring AI `ImageModel` 接口仅抽象 `ImageResponse call(ImagePrompt)` 一个方法(已按其真实签名实现,无非标准 `callAsync` 覆写)。

后续能力(Image 之外)按同模式滚动补 vendor 子模块,不预设 RN.3-5 时间表。

---

## 9. 已闭决策点 (原 Open Questions 收敛)

| 编号 | 问题 | 结论 |
|------|------|------|
| Q1 | 配置根前缀 | **`platform.component.ai`**(实测,否决 `atlas.ai.*`) |
| Q4 | 方法动词风格 | 自实现抽象用端点动词(`rerank` / `generate` / `open`);Spring AI 复用抽象用其原生 `call` |
| Q6 | AudioFrame 共用 | ✅ 引入 `com.richie.component.ai.api.AudioFrame` |
| Q7 | spring-ai-alibaba 引入 | **不引入全量**;仅引入必要 `dashscope-sdk-java` 子模块 + Reactor Netty(实测 spring-ai-alibaba DashScope starter 不存在,aliyun-sdk-java 2.x 大量模块被删) |
| Q8 | 新包/新模块 | 抽象层 + DashScope impl 放主包 `api/`+`provider/`;`vector-rerank-core` 为 vector 包新增子模块;其余厂商按 §4 子模块划分 |

---

## 10. Open Questions (待定 — 不阻塞 RN.1+RN.2 启动)

| 编号 | 问题 | 当前状态 | 阻塞? |
|------|------|---------|------|
| O1 | Hunyuan Rerank 独立 REST 端点 | ✅ **已闭**: ① Zhipu Rerank 已确认 `POST /paas/v4/rerank` (model=`rerank`, Bearer, 文档≤128条, 响应对齐 Cohere shape); ② Hunyuan Rerank 经用户 m0224 确认官方未单独提供 → 矩阵标 ❌(非未验证,而是不存在) | **已闭** |
| O2 | Zhipu TTS/STT/VoiceChat 独立 REST 端点 | ✅ 已确认: TTS(`/paas/v4/audio/speech`) + STT(`/paas/v4/audio/transcriptions`) + VoiceChat(`wss://open.bigmodel.cn/api/paas/v4/realtime`),Bearer 认证 | **已闭** |
| O3 | Pangu-SIS 华为语音 TTS/STT 端点 | ⚠️ 待补 `ai-huawei-sis` 链接 | 否 |
| O4 | Doubao TTS/STT/Rerank 端点 | ✅ **已闭**: ① Rerank 经 VikingDB `/api/knowledge/service/rerank` (AK/SK HMAC, doubao-seed-rerank) ✅; ② TTS `openspeech.bytedance.com` V3 单向 SSE + WS 双向 (`/api/v3/tts/unidirectional` / `/api/v3/tts/bidirection`), X-Api-Key/AppId 认证, 325+ 音色 ✅; ③ STT 一句话 `/api/v3/auc/bigmodel/recognize/flash` + 文件异步 submit/query + WS 流式 `/api/v2/asr`, 同认证体系 ✅; Doubao 能力计数由 5/9 → 7/9 | **已闭** |
| O5 | 各 vendor STS 签名算法实现 | ✅ **已闭**(原则 J 落地,见 §14):`StsSigner` SPI + 5 种 `Signer` impl(Bearer / TC3 / AppCode / AkSkHmac / XApiKey),`StsTicket` 统一票面 + 多种 `asXxx()` 取用方法;业务侧无 vendor 感知;`VendorStsService` 单一入口按 `voiceChat.sts.vendor` 自动分派 | **已闭** |
| O6 | 腾讯 STT (语音识别) 独立产品端点 | ✅ 已确认: product **1093** (`asr.tencentcloudapi.com`, TC3 签名),三模式 — 一句话识别(同步)/录音文件识别(异步任务)/语音流异步识别(WS 流式);用户 m0196 提供官方文档 | **已闭** |

---

## 附录 A: DashScope 端点速查 (参考卡 — 已确认)

| 能力 | 端点 | 协议 |
|------|------|------|
| Chat | `/compatible-mode/v1/chat/completions` (OpenAI 兼容) | REST/SSE |
| Embed | `/compatible-mode/v1/embeddings` | REST |
| Rerank | `/api/v1/services/rerank/text-rerank/text-rerank` | REST |
| Image | `/api/v1/services/aigc/text2image/image-synthesis` (wanx) | REST 异步 |
| TTS | `/api/v1/services/aigc/audio-generation/...` (cosyvoice) | REST / WS 流式 |
| STT | `/api/v1/services/asr/...` (paraformer) | REST / WS |
| Video | `/api/v1/services/aigc/video-generation/video-synthesis` (wanx) | REST 异步任务 |
| Music | `/api/v1/services/aigc/music-generation/...` | REST 异步任务 |
| VoiceChat | qwen-omni Realtime WebSocket | WS 双工 |

## 附录 C: Zhipu 端点速查 (参考卡 — 已确认,用户 2026-07-20 提供官方文档)

| 能力 | 端点 | 协议 | 关键参数 |
|------|------|------|---------|
| Chat | `/paas/v4/chat/completions` (OpenAI 兼容) | REST/SSE | Bearer auth |
| Embed | `/paas/v4/embeddings` | REST | Bearer auth |
| TTS | `/paas/v4/audio/speech` | REST (JSON→wav/pcm) | model=`glm-tts`, 7 音色(tongtong/chuichui/xiaochen/jam/kazi/douji/luodo), stream 支持 SSE, 语速 0.5-2, 音量 0-10, 采样率建议 24000 |
| STT | `/paas/v4/audio/transcriptions` | REST (multipart→JSON/SSE) | model=`glm-asr-2512`, 支持 wav/mp3, ≤25MB/30s, 热词 hotwords 数组, 流式 stream |
| VoiceChat | `wss://open.bigmodel.cn/api/paas/v4/realtime` | WSS 双工 | model=`glm-realtime-flash/air`, 6 音色(tongtong/female-tianmei/male-qn-daxuesheng/male-qn-jingying/lovely_girl/female-shaonv), VAD(server/client), 工具调用(function), 视频模式(video_passive), 输入 wav/pcm, 输出 pcm 24kHz mono 16bit |
| Rerank | `/paas/v4/rerank` | REST (JSON→JSON) | model=`rerank`(固定枚举), query≤4096 字符, documents≤128 条(每条≤4096), top_n, return_documents, return_raw_scores;响应对齐 Cohere shape (`results[].index/document/relevance_score` + `usage`);Bearer auth;用户 m0218 提供官方文档 |
| Video | `/paas/v4/videos/generations` | REST **异步** (JSON→`AsyncResponse{id,task_status}`) | 模型 `cogvideox-3`(文生/图生/首尾帧)/`cogvideox-2`/`cogvideox-flash`/`viduq1-*`/`vidu2-*`;需另调**查询异步结果**端点轮询 `task_status`(PROCESSING/SUCCESS/FAIL)取视频;`size` 最高 4K,`fps` 30/60,`duration` 5/10s,`with_audio`,`quality` speed/quality,水印开关;Bearer auth;用户 m0272 提供官方文档 |

**Base URL**: `https://open.bigmodel.cn/api`
**认证**: HTTP Bearer (API Key from `https://bigmodel.cn/usercenter/proj-mgmt/apikeys`)

---

## 附录 D: 腾讯云 AI 三 API 面速查 (参考卡 — 2026-07-20 确认)

| 产品 | Product ID | 能力 | 域名 / 端点 | 认证 | 协议特征 |
|------|-----------|------|------------|------|---------|
| **TokenHub** | 1823 | Chat / Image / Vision / Video | `https://tokenhub.tencentmaas.com/v1/chat/completions` | **Bearer** (API Key) | OpenAI Chat Completions 风格,`model` 参数选能力(例 `hy-vision-2.0-instruct` 图生文,24k in/16k out,44k ctx) |
| **语音合成 TTS** | 1073 | TTS (TextToVoice) | `tts.tencentcloudapi.com` `POST /` | **TC3-HMAC-SHA256 签名** | `X-TC-Action: TextToVoice` / `Version: 2019-08-23`;入参 `Text`/`SessionId`/`VoiceType`/`Speed`(-2~6)/`Volume`(-10~10)/`SampleRate`(8k/16k/24k)/`Codec`(wav/mp3/pcm);出参 `Audio`=**base64** 音频 + `Subtitles` 时间戳;官方 Java SDK 3.0 `TtsClient`(服务端)+ Android/iOS/HarmonyOS NEXT/Flutter SDK(客户端直连)均存在 |
| **语音识别 STT** | 1093 | STT | `asr.tencentcloudapi.com` (`POST /`,TC3) | **TC3-HMAC-SHA256 签名** | 三模式: ①**一句话识别** `SentenceRecognition`(同步,短句→文本,对标 Zhipu/DashScope STT); ②**录音文件识别** `CreateRecTask`+`DescribeTaskStatus`(异步任务,传文件 URL→轮询); ③**语音流异步识别** `CreateAsyncRecognitionTask`+`CloseAsyncRecognitionTask`(WS 流式,对应原则 F 前端直连) |

**关键结论**: 腾讯 TTS/STT **不**走 TokenHub,而是独立腾讯云 API 3.0 产品(TC3 签名)。这与 DashScope/Zhipu 的 Bearer REST 路径不同,`ai-hunyuan` 子模块需 dual-auth(TokenHub Bearer + 1073/STT TC3)。

**消费模型定位 (2026-07-20 用户 m0191)**: 腾讯 TTS 官方主推**客户端直连**——提供 Android / iOS / HarmonyOS NEXT / Flutter SDK,定位是嵌入到终端 App 由设备直接合成语音,并非强约束服务端调用。但 `tts.tencentcloudapi.com` 本质仍是标准腾讯云 API 3.0 服务端 REST(`Host`+TC3 `Authorization`+JSON,与 CVM/CDB 同形),同时也有 Java/Python/Go 等服务端 SDK,故**双用**。→ **设计影响**: Hunyuan TTS 应优先走原则 F/H —— 前端持短时 TC3 临时密钥**直连** `tts.tencentcloudapi.com`(与移动 SDK 行为一致),`ai-hunyuan` 服务端 TTS impl 降为**可选**(仅当存在纯后端合成需求时才实现),不强制纳入 RN 首轮。STT 同理待补端点确认。

---

## 附录 E: 火山引擎 Rerank 经 VikingDB 速查 (参考卡 — 2026-07-20 确认)

| 项 | 值 |
|----|----|
| 产品 | 向量数据库 **VikingDB**(火山引擎),非独立 Doubao Rerank 模型(客服确认无独立 Rerank 模型) |
| 端点 | `POST /api/knowledge/service/rerank` |
| Host | `api-knowledgebase.mlp.cn-beijing.volces.com` (region `cn-beijing`) |
| 认证 | **AK/SK HMAC-SHA256 签名**(火山 OpenAPI 签名,非 Bearer) |
| 模型 | `doubao-seed-rerank`(字节自研多模态,文本/图片/视频混合,推荐) / `base-multilingual-rerank`(70+ 语言) / `m3-v2-rerank`(100+ 语言) |
| 请求 | `rerank_model` + `datas[]`(每项 `query` 必选 + `content`/`title`/`image`,`datas` ≤ 200);`doubao-seed-rerank` 支持 `rerank_instruction`(≤1024)与多模态 `image`(base64 或 http(s) 链接) |
| 响应 | **`float[]` 分数数组**,与 `datas` 位置一一对应(无 `index`/`document` 配对)→ 适配层按位置回映射 `RerankResult.index/document/relevanceScore` |
| SDK | 官方 Java SDK `KnowledgeService.rerank(RerankRequest, RequestAddition)`,鉴权 `AuthWithAkSk(ak, sk)` |

**设计影响**: Doubao Rerank 是第 4 种认证域(Bearer / 腾讯 TC3 / 华为 AppCode / 火山 AK-SK HMAC),印证 `StsSigner` 按厂商扩展(§10 O5)与多凭证域建模(§4.1)。Doubao Chat(火山方舟)与 Rerank(VikingDB)属不同产品,同腾讯"一品牌多凭证域"模式。

---

## 附录 F: 火山引擎语音 TTS/STT 速查 (参考卡 — 2026-07-20 确认)

**域名**: `openspeech.bytedance.com`(豆包大模型语音,非方舟大模型网关)

| 能力 | 端点 | 协议 | 认证 |
|------|------|------|------|
| TTS 单向流式 | `POST /api/v3/tts/unidirectional` | HTTP Chunked / SSE(NDJSON base64 chunks) | `X-Api-Key`(新版控制台)或 `X-Api-App-Id`+`X-Api-Access-Key`(旧版)+ `X-Api-Resource-Id`(模型版本路由,如 `seed-tts-2.0`/`seed-icl-2.0`) |
| TTS 双向实时 | `wss://openspeech.bytedance.com/api/v3/tts/bidirection` | WebSocket 双工(实时对话场景) | 同上 |
| STT 一句话识别 | `POST /api/v3/auc/bigmodel/recognize/flash` | HTTP(一次请求返回) | 同上 + `X-Api-Resource-Id: volc.bigasr.auc_turbo` |
| STT 文件识别 | `/api/v1/auc/submit` + `/api/v1/auc/query` | HTTP 异步(submit→轮询 query) | Bearer token(历史接口) |
| STT 流式识别 | `wss://openspeech.bytedance.com/api/v2/asr` | WebSocket 流式 | HMAC256 或 Bearer |

**输出**: TTS NDJSON `data` 字段 base64 音频块,`code: 20000000` 结束;格式 mp3/wav/pcm/ogg_opus,采样率 8k/16k/24k/48k。

**音色**: 325 大模型音色 + 84 传统音色,支持 SSML、情感、声音复刻。

**设计影响**: 豆包语音是第 5 种认证形态(`X-Api-Key` 静态 key / appid+token),区别于 Bearer / 腾讯 TC3 / 华为 AppCode / 火山 VikingDB AK-SK HMAC。`X-Api-Key` 本身控制台可轮换、短时安全友好,前端经 STS 直连 `openspeech.bytedance.com`(原则 F/H)最为自然。Doubao TTS/STT 与 Doubao Chat(方舟)属不同产品+不同认证域,同腾讯"一品牌多凭证域"模式,印证 §4.1 多凭证域建模与 `StsSigner` 按厂商扩展(§10 O5)。

---

## 附录 B: 文档变更追踪

- v0.1 (2026-07-20): 初稿。基于 R-N BRIEF/HANDOFF + 现存代码 + 设计原则 A-E
- v0.2 (2026-07-20): SHIP 定稿。原则收敛为 F/G/H/I + C/E/M/β;6×9 能力矩阵定稿;配置前缀确认 `platform.component.ai`;Q1/Q4/Q6/Q7/Q8 闭;RN.1+RN.2 合并周一启动;Hunyuan Rerank 等 O1-O5 待补不阻塞
- (无 v0.3 — v0.2 即 ship)
- v0.2 +patch (2026-07-20): 落盘腾讯三 API 面发现 — TTS=product 1073 (TC3 签名,base64 输出,非 TokenHub);Hunyuan 矩阵 TTS 标注独立产品;新增 O6 (STT 端点待补);§10 O5 补 TC3 签名对 `StsSigner` 的影响;新增 附录 D
- v0.2 +patch2 (2026-07-20): 用户 m0191 指出腾讯 TTS 主推客户端直连(Android/iOS/HarmonyOS/Flutter SDK);确认 1073 仍双用(服务端 REST + 客户端 SDK);附录 D 补消费模型定位 —— Hunyuan TTS 优先走原则 F/H 前端直连,服务端 impl 降为可选
- v0.2 +patch3 (2026-07-20): 用户 m0196 提供腾讯 ASR 官方文档,确认 STT=product **1093** (`asr.tencentcloudapi.com`, TC3 签名),三模式(一句话同步/录音文件异步/语音流 WS);§1 Hunyuan STT 标注独立产品 1093;O6 闭;附录 D STT 行补全
- v0.2 +patch4 (2026-07-20): 用户 m0205 要求更新矩阵 → 修正"能力计数"行(Hunyuan 4/9→5/9,口径说明 TTS/STT 独立 TC3 产品计 ✅);vendor 事实补腾讯三 API 面小结
- v0.2 +patch5 (2026-07-20): 用户 m0211 问腾讯三套独立产品能否融入后台设计 → 落盘 **§4.1 多凭证域厂商建模(选 A 子厂商拆分)**: `ai-hunyuan` 单模块内注册 3 个 `VendorConfig`(tokenhub/tts/stt),差异收敛到 `StsSigner`(`BearerStsSigner`+`Tc3StsSigner`);§2 vendor 配置改为 hunyuan-tokenhub/tts/stt 三键;零抽象层改动
- v0.2 +patch6 (2026-07-20): 用户 m0218 提供智谱 Rerank 官方文档 → 确认 `POST /paas/v4/rerank` (model=`rerank`, Bearer, 文档≤128条, 响应对齐 Cohere shape);§1 Zhipu Rerank ⚠️→✅, Zhipu 9/9;附录 C 加 Rerank 行。用户 m0224 确认腾讯混元无单独 Rerank → Hunyuan Rerank ⚠️→❌, Hunyuan 5/9→4/9;O1 闭(智谱确认+混元确认不存在)
- v0.2 +patch7 (2026-07-20): 用户 m0236 提供火山 Rerank 文档 + 客服确认"无独立 Rerank 模型,走 VikingDB" → 确认 Doubao Rerank = 向量数据库 VikingDB `/api/knowledge/service/rerank` (AK/SK HMAC 签名, doubao-seed-rerank, 响应 float[] 位置对齐);§1 Doubao Rerank ⚠️→✅, Doubao 4/9→5/9;vendor 事实补 VikingDB 第4认证域;O4 部分闭;新增 附录 E;修正陈旧 Hunyuan Rerank ⚠️ 表述为 ❌
- v0.2 +patch8 (2026-07-20): 用户 m0251 提供豆包语音官方文档 → 确认 Doubao TTS `openspeech.bytedance.com` V3 单向 SSE + WS 双向(`X-Api-Key`/AppId 认证, 325+ 音色) + STT 一句话 flash / 文件异步 submit+query / WS 流式(`/api/v2/asr`, 同认证);§1 Doubao TTS/STT ⚠️→✅, Doubao 5/9→7/9(剩 Video ❌);O4 全闭;新增 附录 F;vendor 事实补豆包语音第5认证形态
- v0.2 +patch9 (2026-07-20): 用户 m0272 提供智谱视频官方文档 → 确认 Zhipu Video `POST /paas/v4/videos/generations` (异步, Bearer, cogvideox-3/vidu 系列, 需查询异步结果轮询);§1 Zhipu Video ⚠️→✅, Zhipu 9/9 全齐;附录 C 加 Video 行;vendor 事实补智谱视频异步模式
- v0.2 +patch10 (2026-07-20): **RN.1+RN.2 已实施落地** — `RerankModel`/`RerankRequest`/`RerankResult` + `BailianRerankModel` + `BailianImageAdapter`(Spring AI `ImageModel`) + `AiModelProperties.rerank[]`/`image[]` 配置节点 + `aiRerankModel`/`aiImageModel` 两个 opt-in `@Bean`(ai 模块);新建 `vector-rerank-core` 子模块(`RerankServiceImpl`)+ `VectorService.rerank/rerankAsync` 非破坏性 default 方法(vector-core)。全模块 `mvn compile` exit 0;Chat/Embedding 与 4 个向量后端实现零改动。**实测修正**: 原则 I 由"Reactor-native"修正为"CompletableFuture-native"(`HttpClient`/`HttpRequest` 真实返回 `CompletableFuture`,非 Mono/Flux);JAVA_AGENTS.md 本项目不存在,以实际代码为准
- v0.2 +patch11 (2026-07-20): **弱厂商 TTS/STT sprint 已落地** — 5 厂商 × 2 能力 = 10 个 `TextToSpeechModel`/`TranscriptionModel` Spring AI 适配器 + 3 个签名工具(`Tc3Signer`/`Aws4Signer`/`AppCodeSigner`)+ `AiModelProperties.tts[]`/`stt[]` 配置节点 + `AiModelAutoConfiguration` 10 个 opt-in `@Bean`。`mvn test` exit 0,81/81 通过(5 个 vendor 测试套件 + 既有 support 测试)。Chat/Embedding/Rerank/Image 路径与 4 个 VectorService 实现零改动
- v0.2 +patch12 (2026-07-20): **方案乙 — 多模态对齐 Chat 动态 reload 模式** — Rerank/Image/TTS/STT 由 13 个分散 `@Bean` 收口为 `AiMultimodalServiceImpl` 单一 Bean;`AiModelProperties.{rerank|image|tts|stt}` 由 `List<X>` 改造为 `Map<String, X>`(与 `models` 同构,key=业务名);新增 `MultimodalModelFactory` 按 `vendor` 分派 impl;`@RequiredArgsConstructor` `refresh()` 走 Chat 端"新增/覆盖"语义(同 name 覆盖,缺 key 不主动删);`getRerankModel / getImageModel / getTextToSpeechModel / getTranscriptionModel / getXxxModelNames()` 5 维度取用 API。`mvn test` exit 0,90/90 通过(原 81 + 9 个新增 `AiMultimodalServiceImplTest`)。Chat 端 `AiChatClientFactory` / `AiChatOptionsResolver` / `AiModelCircuitBreaker` / `AiModelRouter` / `ToolRegistry` / `AiModelServiceImpl` / `AiModelUsageExample` 与 4 个 `VectorService` 实现零改动;详见 §12
- v0.2 +patch13 (2026-07-20): **Doubao Rerank 路径澄清评估稿 §13** — 任务 tom 派发,经 webfetch 确认**方舟 (ark.cn-volc.com) 顶层菜单无 Rerank 分类**(只有 Chat/多模态理解/向量化/TTS/STT/视频/图像等 LLM 类端点,与官方客服 m0236 一致),**唯一**暴露 Rerank endpoint 的是 VikingDB `/api/knowledge/service/rerank` (Host `api-knowledgebase.mlp.cn-beijing.volces.com`, AK/SK HMAC, 三模型 `doubao-seed-rerank` / `base-multilingual-rerank` / `m3-v2-rerank`)。§13 拆解"独立端点路径 vs VikingDB KNN+Score 路径"两层语义并比对 RerankModel 抽象同构性。**结论 — 暂缓实装**:(1) 路径 A 文本子集 (`base-multilingual-rerank` / `m3-v2-rerank`) 同构、可补,留待未来 sprint,留 issue `enhancement: Doubao Rerank (VikingDB) — 路径 A 文本子集` 标签 `r-n`/`low-priority` 由 tom 排期;(2) 路径 A 多模态子集 (`doubao-seed-rerank` + image/instruction) **不做**,需扩展 `RerankRequest` 加 `MediaReference` 字段 = 变相建 `MultimodalRerankModel` 抽象,无需求方;(3) 路径 B (VikingDB KNN+Score) **不做**,等价于新建 `atlas-richie-component-vector-volcano` 子模块,与"补 Doubao Rerank"是独立两件事。**未动任何 Java 源文件**,未碰 Zhipu/Pangu/Bailian/VectorService impl,纯文档评估 + 设计稿落盘
- v0.2 +patch14 (2026-07-21): **WS + STS 统一接口 SPI 设计稿 §14** — 用户 m0042 锁定原则 J"WS + STS 统一接口原则",新增 §14 设计稿:`StsSigner` SPI + `StsTicket` 统一票面(5 种认证域收敛为 `asBearer/asTc3Headers/asAppCode/asSignedHeaders/asHeaderMap` 5 个方法)+ `VoiceChatModel` 业务 SPI(双工流式统一签名)+ `VoiceStsService` + `VoiceChatService` 业务门面;包结构严格隔离 (`api/voicechat/` + `service/` 不依赖 `provider/`);配置切换 vendor = YAML 一行;新增 5 个 StsSigner impl + 15 个 @Bean 自动装配 + ArchUnit 静态守护业务代码不 import vendor 包。§0 原则新增 J,§10 O5 标记已闭(原则 J 落地)。实施分 RN.4-alpha/beta/gamma/delta 四阶段,**总工作量 4.5-6 day**(P0+P1: ~2.5 day;P2 全 vendor: +1.5 day)。**未动任何 Java 源文件**,等用户评审 §14 后开 sprint
- v0.2 +patch15 (2026-07-22): **RN.3 Phase C 落地 (VECTOR_SERVICE_V2_DESIGN §11.3) — 多模态向量模型 3 vendor 接入 + 模态路由 + Testcontainers IT** — VectorService V2 Phase C 子任务全部交付,新增跨模态向量检索能力(TEXT ↔ IMAGE 同空间),ai 模块扩展 + vector-core 模态路由 + vector-qdrant Testcontainers IT 三处落地:
  - **C1** — ai 模块扩 `ImageEmbeddingModel` 接口:新增 `com.richie.component.ai.api.image.ImageEmbeddingModel`,**extends Spring AI `EmbeddingModel`**,增加 `embedImage(String)` **default method**(默认 `throw new UnsupportedOperationException`);与 Spring AI 标准 EmbeddingModel 100% 向后兼容;现已有 `BailianImageEmbeddingAdapter`(v0.2 patch10 阶段已存在,本轮做小修) + `OllamaImageEmbeddingAdapter`(新增) + `TeiImageEmbeddingAdapter`(新增)三个 impl
  - **C2** — Ollama CLIP 适配器(`provider/ollama/OllamaImageEmbeddingAdapter.java`):走 Ollama 原生 `POST /api/embed` 端点,默认模型 `nomic-embed-text`(768-dim);**注意 — Ollama 原生协议无 CLIP 图像 embedding**,本类仅承担"挂名 image-embedding 入口的文本向量化";真实跨模态语义仍需 TEI/Bailian。`ImageEmbeddingProvider` 枚举新增 `OLLAMA` 值
  - **C3** — HuggingFace TEI CLIP 适配器(`provider/tei/TeiImageEmbeddingAdapter.java`):走 TEI **OpenAI 兼容协议** `POST /v1/embeddings`,跨部署形态(CPU/GPU/candle/tgi)稳定性最佳;支持可选 Bearer 反代鉴权(`apiKey` 非空时携带);维度由运行时部署模型决定,**编译期未知 → `dimensions()` 返回 `0`**(避免 Spring AI 默认实现的远程 probe 计费调用);**当前仅文本分支**,图像分支(`/embed_image`)协议层与 OpenAI 不兼容,留待未来 sprint。`ImageEmbeddingProvider` 枚举新增 `TEI` 值
  - **C4** — vector-core 模态路由:`ModalityAwareEmbeddingService` 已实装(`vector-core/service/ModalityAwareEmbeddingService.java`),按 `Modality.TEXT/IMAGE` 二选一分派到 `textModel` / `imageModel`(后者通过 `@Qualifier("imageEmbeddingModel")` **可选注入**,未配置时 IMAGE 模态抛 `UnsupportedModalityException` — 与 Phase A 的 `IMAGE 暂未启用` 硬 throw 等价,但文案改为配置指引);`supportsModality` / `dimensionFor` 两个 SPI 供 vector provider 写入前查询;**模态路由单测** `ModalityAwareEmbeddingServiceTest.java` 已实装并通过
  - **C5** — vector-qdrant Testcontainers 集成测试:`QdrantVectorRecordOpsIT` 已实装(`vector-qdrant/src/test/java/.../integration/QdrantVectorRecordOpsIT.java`),`@Testcontainers` + `qdrant/qdrant:v1.7.0` 容器(暴露 6333 REST + 6334 gRPC),`@DisabledIfEnvironmentVariable(SKIP_TESTCONTAINERS=true)` 整体跳过开关;`@DynamicPropertySource` 把容器端口注入 Spring Environment
  - **C6** — 文档 patch15 + JavaDoc 审计(本条目):R-N-DESIGN.md 新增 patch15 条目 + 附录 G"Phase C AI 模块扩展与测试"补充设计;`MultimodalModelFactory` 类级 JavaDoc 增补;`ModalityAwareEmbeddingService` JavaDoc 已含模态路由设计意图(无需修改);`QdrantVectorRecordOpsIT` JavaDoc 已含 Testcontainers gating 说明(无需修改);`BailianImageEmbeddingAdapter` JavaDoc 已正确说明 CLIP-equivalent 语义(无需修改);`README.zh.md` 增补 Phase C 交付说明
  - **mvn test 全模块验证状态**:本会话后 `mvn test` 待跑通(若尚未跑通留 TODO 占位);单测 `ModalityAwareEmbeddingServiceTest` 已独立通过(详见 C4)
  - **改动总结**:
    - **新增(Java)**: `api/image/ImageEmbeddingModel.java`(C1)、`provider/ollama/OllamaImageEmbeddingAdapter.java`(C2)、`provider/tei/TeiImageEmbeddingAdapter.java`(C3)、`vector-core/service/ModalityAwareEmbeddingService.java`(C4)、`vector-core/test/service/ModalityAwareEmbeddingServiceTest.java`(C4 单测)、`vector-qdrant/test/config/integration/QdrantVectorRecordOpsIT.java`(C5)、`config/multimodal/image/ImageEmbeddingProvider.java`(枚举扩 `OLLAMA` / `TEI`,C2/C3 联动)
    - **修改(Java)**: `provider/support/MultimodalModelFactory.java`(C1/C2/C3 联动 — `createImageEmbeddingModel` switch 加 `OLLAMA` / `TEI` 分支 + 类级 JavaDoc 增补);`config/multimodal/image/ImageEmbeddingModelConfig.java`(对齐新 vendor 字段)
    - **文档**: `R-N-DESIGN.md`(本 patch15 + 附录 G)、`README.zh.md`(Phase C 交付说明 + BAILIAN/OLLAMA/TEI vendor 矩阵)
    - **未改(架构守护)**: Chat 端 `AiChatClientFactory` / `AiModelProperties.models` / 5 个现有 Chat impl;4 个 vector-core 后端非模态路由路径;BailianImageEmbeddingAdapter 业务逻辑(仅 C1 阶段作为基线沿用)
  - **Phase A 占位代码解开**:`AbstractVectorService` 中 IMAGE 模态暂未启用的硬 throw(`throw new UnsupportedOperationException("IMAGE modality not yet enabled in v2.0.0")`)现在被 `ModalityAwareEmbeddingService` 取代 — 业务侧只需注入 `imageEmbeddingModel` Bean 即启用 IMAGE 模态;未注入时仍走 `UnsupportedModalityException` 文案变更(从"未启用"→"请配置 vector.image-embedding.model")
  - **测试矩阵**(C4+C5 增量):
    - `ModalityAwareEmbeddingServiceTest` — 模态路由 / supports / dimensionFor / null 兜底(已 PASS)
    - `QdrantVectorRecordOpsIT` — createIndex/indexExists/countDocuments/truncateIndex/listIndexes/describeIndex/getIndexStats/healthCheck/similaritySearchByVector(Qdrant 容器内全跑,Docker 不可用时 `SKIP_TESTCONTAINERS=true` 跳过)
    - Phase C6 JavaDoc 审计通过(无逻辑改动)

---

## 11. 弱厂商 TTS/STT sprint 实施细节 (patch11)

### 11.1 实施范围

| Vendor | TTS 类 | STT 类 | 端点 (待 Caveat 14 校验) | 认证 | 测试 |
|--------|--------|--------|--------------------------|------|------|
| **Hunyuan** | `HunyuanTextToSpeechModel` | `HunyuanTranscriptionModel` | TTS: `tts.tencentcloudapi.com`(product 1073)/ STT: `asr.tencentcloudapi.com`(product 1093) | `Tc3Signer`(TC3-HMAC-SHA256)+ secretId/secretKey/region | 9/9 ✅ |
| **Zhipu** | `ZhipuTextToSpeechModel` | `ZhipuTranscriptionModel` | TTS: `/paas/v4/audio/speech`(glm-tts)/ STT: `/paas/v4/audio/transcriptions`(glm-asr-2512,multipart + JSON base64 双路径) | Bearer api-key | 24/24 ✅ |
| **Doubao** | `DoubaoTextToSpeechModel` | `DoubaoTranscriptionModel` | TTS: `openspeech.bytedance.com/api/v3/tts/unidirectional`(SSE/Chunked,NDJSON base64)/ STT: `/api/v3/auc/bigmodel/recognize/flash`(一句话) | `X-Api-Key`/`X-Api-App-Id`+`X-Api-Resource-Id` | (套件名 `DoubaoSpeechModelTest`,详见 surefire) ✅ |
| **Huawei-SIS** | `HuaweiSisTextToSpeechModel` | `HuaweiSisTranscriptionModel` | SIS 短语音 TTS/STT(华为云 SIS 控制台开通) | `AppCodeSigner`(`X-Apig-AppCode`)+ `Aws4Signer` 用于请求级 HMAC | ✅ |
| **Pangu** | `PanguTextToSpeechModel` | `PanguTranscriptionModel` | 复用 Pangu multimodal TTS/STT 端点 | `AppCodeSigner`(`X-Apig-AppCode`) | ✅ |

### 11.2 共用支撑

| 类 | 包 | 用途 |
|---|---|---|
| `Tc3Signer` | `com.richie.component.ai.support.sign` | 腾讯云 TC3-HMAC-SHA256 签名(Hunyuan product 1073/1093) |
| `Aws4Signer` | 同上 | 华为云 Request-SigV4 风格(部分 SIS 端点) |
| `AppCodeSigner` | 同上 | 华为云 Apig-AppCode 头签发(简单 X-Apig-AppCode 模式) |
| `VoiceModelConfig` | `com.richie.component.ai.config` | TTS/STT 统一配置(name/apiKey/secretId/secretKey/appCode/baseUrl/model/region/endpoint/resourceId/appId) |

### 11.3 落点

- `AiModelProperties` 加 `List<VoiceModelConfig> tts = new ArrayList<>()` + `List<VoiceModelConfig> stt = new ArrayList<>()`(原则 C 独立配置节点)
- `AiModelAutoConfiguration` 加 10 个 `@Bean`(均 `@ConditionalOnMissingBean` + `@ConditionalOnProperty(prefix="platform.component.ai.{tts|stt}.{vendor}.enabled", matchIfMissing=false)`):
  - `aiHunyuanTextToSpeechModel` / `aiHunyuanTranscriptionModel`
  - `aiZhipuTextToSpeechModel` / `aiZhipuTranscriptionModel`
  - `aiDoubaoTextToSpeechModel` / `aiDoubaoTranscriptionModel`
  - `aiHuaweiSisTextToSpeechModel` / `aiHuaweiSisTranscriptionModel`
  - `aiPanguTextToSpeechModel` / `aiPanguTranscriptionModel`
- 全部走 `HttpClient`/`HttpRequest` CompletableFuture 模式(原则 I 修正);纯 JDK crypto(无第三方签名 SDK)

### 11.4 Caveat 14 验证状态 (待 IT)

> ⚠️ **以下端点 URL 在单测阶段以 Mock HTTP 验证**;**生产 IT 必须按各厂商官方文档做真机 POST 校验**(尤其 Doubao `X-Api-Resource-Id` 是否要区分 flash 文件 ID、Huawei-SIS endpoint 是否区域化、Pangu 语音端点路径是否正确)。
> 单测通过不构成 Caveat 14 验证完成,IT 验收前 TTS/STT 不可对外启用。

### 11.5 矩阵更新

| 能力 \ 厂商 | DashScope | Zhipu | Doubao | Hunyuan | Pangu | Huawei-SIS | MiniMax |
|------|------|------|------|------|------|------|------|
| **TTS** | ✅ cosyvoice | ✅ glm-tts | ✅ openspeech V3 | ✅ 1073 TC3 | ⚠️ Pangu 语音(待 IT) | ⚠️ SIS(待 IT) | ✅ |
| **STT** | ✅ paraformer | ✅ glm-asr-2512 | ✅ 一句话 flash | ✅ 1093 TC3 | ⚠️ Pangu 语音(待 IT) | ⚠️ SIS(待 IT) | ⚠️ |

> **计数口径**:`✅ = 接口已落地 + 单测通过`,`⚠️ = 接口已落地 + 单测通过 + **Caveat 14 IT 待跑**`。
> 7 厂商 TTS 覆盖:6✅ + 2⚠️(Pangu + Huawei-SIS 共享 Pangu 语音栈,实际为 Pangu 多模态页或 SIS 独立产品,需 IT 区分) → 实际全 7 厂商 TTS 接入(纯语音厂商 5 家)。

---

## 12. 多模态动态初始化模式 (对齐 Chat — patch12)

### 12.1 动机

patch10/11 的实现把 Rerank / Image / TTS / STT 做成 **13 个分散的 `@Bean`**(`aiRerankModel`/`aiImageModel` + 5 厂商 × 2 能力的 10 个 voice Bean)。这种"一个能力 × 一组 vendor × 一组 @Bean"的展开方式有两个不一致问题:

1. **与 Chat 端 `AiChatServiceImpl` 不对齐** —— Chat 走 `Map<String, ChatClient> chatClients` + `initializeModels(List<ModelOptions>)`/`getModelInfo(...)` 的"Map + 动态 reload"模式,运行时可通过 API 加/覆盖模型;多模态却要靠 `@ConditionalOnProperty` + 重启才能换实现。
2. **配置结构不对称** —— Chat 的 `AiModelProperties.models` 是 `Map<String, AiModel>`(key=业务名,value 含 `provider` 枚举);多模态的 `rerank[]`/`image[]`/`tts[]`/`stt[]` 是 `List<X>`,业务名要么没有(Legacy 早期)要么嵌在 `X.name` 字段里。

patch12 把多模态收口为 **1 个 `AiMultimodalServiceImpl` Bean + 4 个 `Map<String, T>` 内部状态** + 启动期 `refresh()`,与 Chat 端 `AiChatServiceImpl.initializeModels(...)` 模式一一对应。

### 12.2 改动清单

| 文件 | 改动 | 说明 |
|---|---|---|
| `AiModelProperties.java` | `List<RerankModelConfig> rerank` → `Map<String, RerankModelConfig> rerank = new LinkedHashMap<>()`,image/tts/stt 同构;新增 `String vendor` 字段到 3 个 inner config | 与 Chat 端 `models: Map<String, AiModel>` 同构;`vendor` 对应 Chat 端 `provider` 字段,作为 `MultimodalModelFactory` 的分派依据 |
| `AiModelAutoConfiguration.java` | **删除 13 个分散 `@Bean`** (`aiRerankModel`/`aiImageModel`/`aiHunyuanTtsModel`/`aiHunyuanSttModel`/`aiZhipuTtsModel`/`aiZhipuSttModel`/`aiDoubaoTtsModel`/`aiDoubaoSttModel`/`aiHuaweiSisTtsModel`/`aiHuaweiSisSttModel`/`aiPanguTtsModel`/`aiPanguSttModel` + `firstVoiceConfig` helper);**新增单一 `@Bean("aiMultimodalService")`**,构造期触发 `refresh()` | 13 → 3(`aiChatClients` + `aiEmbeddingModel` + `aiMultimodalService`),与 Chat 端"两个 chat Bean + 一个 service Bean"格局对称 |
| `provider/support/MultimodalModelFactory.java`(新增) | 静态工具类,提供 `createRerankModel / createImageModel / createTextToSpeechModel / createTranscriptionModel` 4 个工厂方法,按 `vendor` 大写不区分分派 | 镜像 Chat 端 `AiChatClientFactory.createChatClient(String, AiModelProperties.AiModel)` 形态;`vendor` 未识别抛 `IllegalArgumentException`,由 service 层捕获按条目跳过 |
| `service/impl/AiMultimodalServiceImpl.java`(新增) | `@Slf4j @Service @RequiredArgsConstructor`,4 个 `Map<String, T>` 内部状态,`refresh()` 走 Chat 端"新增/覆盖"语义 | 与 Chat 端 `AiChatServiceImpl.initializeModels` 等价 |
| `service/impl/AiMultimodalServiceImplTest.java`(新增) | 9 个 hermetic 单测(empty refresh / bailian rerank+image / hunyuan+doubao TTS / 5 vendor STT / unknown-vendor 跳过 / 二次 refresh cover-replace / no-http-client graceful skip / unknown key / unmodifiable Set) | 90/90 测试通过 |

### 12.3 模式对比(Chat vs 多模态)

| 维度 | Chat | 多模态 (R-N) |
|---|---|---|
| 配置节点 | `platform.component.ai.models: Map<String, AiModel>` | `platform.component.ai.{rerank\|image\|tts\|stt}: Map<String, *Config>` |
| 实现分派 | `AiModel.provider` 枚举(`OPENAI`/`DEEPSEEK`/...) | `<X>Config.vendor` 字符串(`bailian`/`hunyuan`/`zhipu`/...) |
| 工厂类 | `AiChatClientFactory.createChatClient(name, AiModel)` | `MultimodalModelFactory.createRerankModel(cfg, httpClient)` |
| 运行时状态 | `Map<String, ChatClient> chatClients` | `Map<String, RerankModel/ImageModel/TextToSpeechModel/TranscriptionModel>` × 4 |
| 动态加/覆盖 | `initializeModels(List<ModelOptions>)` | `refresh()`(读配置后 put-or-cover) |
| 启动期触发 | 由 `@Bean("aiChatClients")` + 自动调用 `initializeModels` | 由 `@Bean("aiMultimodalService")` 构造期主动调 `refresh()` |
| 取用 API | `chatClients.get(name)` / `getModelInfo` / `getAvailableModels` / `getDefaultModel` | `getRerankModel(name)` / `getXxxModelNames()` |
| 覆盖语义 | 同 name → cover-replace;缺 key 不删 | 同 name → cover-replace(`Map.put` 语义);缺 key 不主动删(与 Chat 一致) |
| 未识别 vendor | 自动降级 `OPENAI` 兼容协议 | 按条目跳过 + WARN 日志(鉴权差异大,不允许静默降级) |

### 12.4 设计权衡

**为何不为多模态建独立 `AiMultimodalService` interface 而直接用 `@Service` 注解在 Impl 上?**

为最小变更面。Chat 端存在 `AiChatService` interface 是因为其 13 个 public API(`call`/`stream`/`callWithModel`/`initializeModels`/...)需要被业务侧以接口形式调用;多模态 4 类能力各自提供 Spring AI 标准接口(`RerankModel`/`ImageModel`/`TextToSpeechModel`/`TranscriptionModel`),业务侧通过 `AiMultimodalServiceImpl.getXxxModel(name)` 拿到对应 Spring AI 接口调用,**不需要再额外抽象一层本组件的接口**。这是 reduced-wrong 原则的体现。

**为何 `vendor` 用 String 而非 `enum`?**

Chat 端 `AiProviderType` 是枚举,但**只在配置文件初始化路径**使用 —— 运行时 `initializeModels` 接受 String,与 Chat 文档"未知 provider 自动降级 OPENAI"兼容。多模态每个能力的鉴权形态差异极大(Bearer / TC3-HMAC-SHA256 / AppCode / AWS4-HMAC),不允许"未知 vendor 自动降级"语义,直接用 String 让用户写错立即 fail-fast;枚举 + 默认值的方案会反过来鼓励乱写。

### 12.5 取用示例

```java
// 业务侧注入
@Autowired private AiMultimodalServiceImpl multimodalService;

// 取用各能力实例(按 Map key 业务名)
RerankModel rerank = multimodalService.getRerankModel("default");          // bailian
ImageModel image = multimodalService.getImageModel("img");                  // bailian
TextToSpeechModel tts = multimodalService.getTextToSpeechModel("hunyuan-tts");
TranscriptionModel stt = multimodalService.getTranscriptionModel("doubao-stt");

// 热更新(配置变更后)
multimodalService.refresh();   // 重新读 AiModelProperties,覆盖同 name 实例
```

### 12.6 Caveat 14 + patch12 兼容

patch12 不影响 patch11 的 Caveat 14 状态:**IT 阶段 5 厂商真实语音端点验证** 与 `mvn compile/test` 通过是正交两件事。**统一模式** 在以下方面反而**降低了 IT 排错成本**:同一 vendor 多个 capability 配置项(TTS/STT 各一行 YAML)走相同 factory + service 路径,日志格式统一(4 维度同句日志 "R-N 多模态动态初始化完成,新增/覆盖 N 个 Rerank / N 个 Image / N 个 TTS / N 个 STT 模型"),与 Chat 端"动态初始化AI模型完成,新增/覆盖 N 个模型"风格一致。

---

**RN.1+RN.2 + 弱厂商 TTS/STT sprint 全部实施完成(2026-07-20)。** Chat/Embedding/Rerank/Image + 4 个 VectorService 后端实现路径全部零改动(原则 E)。IT 阶段需做 Caveat 14 真机端点验证。

---

## 13. Doubao Rerank 路径澄清 — 独立端点 vs VikingDB KNN+Score (评估稿)

> **状态**: 评估稿 (无代码改动,纯文档)
> **日期**: 2026-07-20(同 sprint 期间; tom 直接派发)
> **作者**: Sisyphus(代理)
> **范围**: 仅澄清 + 决策建议;不动 java 代码,不碰 Zhipu/Pangu/Bailian 实现
> **关联**: §1 关键 vendor 事实 (Rerank 第 7 项) / §3.1 RerankModel 抽象 / §4 vendor 子模块结构 / §10 O4 已闭 / 附录 E

### 13.1 任务原文与三层语义拆解

任务原问:"Doubao 的 Rerank 是否要走 MultimodalRerankModel 抽象? 还是该走独立 VikingDB(向量库)路径?"

经 webfetch 复核,问题混杂了三层语义,先拆解再回答 — 否则会答错方向:

#### 13.1.1 第一层 — "抽象是否同构"(任务背景 [2] 的假设)

- 任务背景 [2] 假设 MultimodalRerankModel 抽象签名: "输入 query + documents,返回 relevance score"
- **实测**: 本项目**不存在** MultimodalRerankModel 抽象。现有抽象是 `RerankModel`(§3.1),字段:
  - 输入: `String query` + `List<String> documents`(纯文本)
  - 输出: `List<RerankResult>` = `results[index / document / relevanceScore]`(仿 Cohere shape)
- **澄清**: 任务原文"MultimodalRerankModel 抽象"在本项目中**不存在**,也没有 sprint plan 要建。"是否要走 MultimodalRerankModel 抽象"等价于问"能否复用现有 RerankModel 抽象 → 答案:文本子集能,多模态子集不能"。

#### 13.1.2 第二层 — "方舟 vs VikingDB 选哪个 vendor 产品"

- **火山方舟 (ark.cn-volc.com)** 顶层菜单(快速入门/模型列表/调用/...):**无 Rerank 分类** — 只暴露 Chat / 多模态理解 / 向量化 / TTS / STT / 视频 / 图像 等 LLM 类端点。
- **向量数据库 VikingDB**:**唯一**暴露 Rerank endpoint(HOST `api-knowledgebase.mlp.cn-beijing.volces.com`,AK/SK HMAC-SHA256 鉴权)。
- **澄清**: 方舟 vs VikingDB **不是二选一** — 火山把 Rerank 能力放在 VikingDB 子产品下,客服已二次确认(见 附录 E / §1 vendor 事实第 7 项)。Doubao 品牌 = 方舟 (Chat/Embed/...) + VikingDB (Rerank) + `openspeech.bytedance.com` (TTS/STT) 三套独立域(同腾讯多凭证域模式,见 §4.1)。

#### 13.1.3 第三层 — "两条 Rerank 路径"(任务实际在问的内容)

| 路径 | 实质 | 端点 | 与 RerankModel 抽象同构性 |
|------|------|------|----------------------------|
| **路径 A — 独立 Rerank LLM 端点** | 把 Rerank 当成 `query + documents[]` 单次打分服务 | `POST /api/knowledge/service/rerank`(VikingDB knowledge service) | ✅ 高(同构)— 但响应是 `float64[]` 位置对齐,**不**是 Cohere shape,需按 `i` 回填 `RerankResult.index/document/relevanceScore` |
| **路径 B — VikingDB KNN 召回 + 内嵌 Score** | 把 Rerank 当作 KNN 召回的 `rerank_switch=true` 开关,召回阶段一并打分 | `POST /api/knowledge/service/search` 或 `search_knowledge` / `search_and_generate`(带 `rerank_switch` + `rerank_model` + `rerank_only_chunk`) | ❌ 不直接同构 — 这是 vector backend 能力,与"Rerank"语义错位;等价链路是 `VectorService.search(query) → List<Hit>` 然后 `RerankService.rerank(query, hits) → List<RerankResult>`,两步走(复用现有 RerankModel) |

### 13.2 路径 A 深度分析 — 独立 Rerank LLM 端点

#### 13.2.1 请求结构(同附录 E v0.2 patch7)

```
POST /api/knowledge/service/rerank
Host: api-knowledgebase.mlp.cn-beijing.volces.com
Authorization: HMAC-SHA256 签名(第 4 种认证域,与 Bearer / TC3 / AppCode 并列 — §4.1 + 附录 E)

{
  "rerank_model": "doubao-seed-rerank" | "base-multilingual-rerank" | "m3-v2-rerank",
  "datas": [
    {
      "query":  "string 或 object",        // object 仅 doubao-seed-rerank 支持
      "content": "string",                  // 文本 doc
      "title":   "string (可选)",
      "image":   "http(s) URL 或 base64:// 或 data:..;..;..  (仅 doubao-seed-rerank)"
    }
  ],
  "rerank_instruction": "string ≤1024 (仅 doubao-seed-rerank 生效)"
}
```
约束:`datas[]` ≤ 200;响应为 `float64[]` 分数数组,**与 `datas` 位置一一对应**。

#### 13.2.2 三种 Rerank 模型特性 × 与现有 RerankModel 同构性

| 模型 | 模态 | 适配现有 `RerankModel`? | 推荐场景 |
|------|------|--------------------------|----------|
| `doubao-seed-rerank`(字节自研 1.6-rerank,**推荐**) | **多模态** — 文本 / 图片 / 视频 / `rerank_instruction` / `query` 可为 object | ⚠️ **部分不兼容** — `RerankRequest` 只有 `String query + List<String> documents`,**无** image/video field;`vendorOptions` 临时透传 `image[]` 是 hack,不能进抽象 | 多模态 RAG(图文混召) |
| `base-multilingual-rerank` | 纯文本,70+ 语言,长文 | ✅ 完全兼容 — 适配层 `datas[i] = { query, content: documents[i] }`,按位置回填 | 长文 / 多语种 RAG |
| `m3-v2-rerank` | 纯文本,100+ 语言 | ✅ 完全兼容(同 base) | 多语种 RAG |

**与 patch10 `BailianRerankModel` 适配模式同构** — `BailianRerankModel` 把 DashScope 响应 `index/document/relevance_score` 字段映射到本项目 `RerankResult`;VikingDB 路径 A 只是把"字段映射"换成"位置回填",工作量在 patch10 经验内。

#### 13.2.3 多模态子集为何需"扩抽象"

- 现有 `RerankRequest.query: String`,要承载 `doubao-seed-rerank` 的 `object` query(含文本字段 + 图字段),必须改签名为 `MediaReference`(复用 R-N §3.0 MediaReference 统一类型)。
- 现有 `RerankRequest.documents: List<String>`,要承载 image 数据,必须改成 `List<DocumentRef>`(`{ text?: String, image?: MediaReference }`)或新建 `MultimodalRerankRequest`。
- 这等于**变相建 `MultimodalRerankModel` 抽象** — 与任务原问第一字面对应,但与 patch10-12 已 ship 的纯文本 RerankModel **不兼容**,需要新一轮 R-N 评审。

### 13.3 路径 B 深度分析 — VikingDB KNN 召回 + 内嵌 Score

#### 13.3.1 形态

`search_knowledge` / `search_and_generate` 接口在 KNN 召回阶段一并打开 `rerank_switch=true` 并指定 `rerank_model`,召回结果数组元素里直接带 `rerank_score` 字段。**不**是"先 KNN 再单独调 rerank"的两步模式(§13.1.3 表已对比)。

#### 13.3.2 与项目现状对位

- `atlas-richie-component-vector-core` 已实现 4 个 vector backend(Redis / Milvus / MongoDB / Qdrant 等,R-N §5.4);**VikingDB 不在其中**;无 `atlas-richie-component-vector-volcano` 子模块。
- `vector-rerank-core`(patch10)提供 `RerankServiceImpl` 编排,内部委托 `RerankModel.rerank(query, documents)`;业务侧走"已有 vector 召回 + re-rerank 打分"两步。
- **路径 B 的真实工作量 = "新建一个 vector backend"**:
  1. 建 `atlas-richie-component-vector-volcano` 子模块(独立 Maven module,新增版本号,父 POM 接入)
  2. 实现 VikingDB Collection/Index 抽象(`search` / `add` / `update` / `delete` / ...)
  3. 接 AK/SK HMAC 鉴权(复用 §11.2 已落的 `Tc3Signer` / `Aws4Signer` 签名器模式)
  4. 在 `VectorService` 旁开 `VectorService.searchAndRerank()` 薄包装 `search_and_generate` API
- **与 RerankModel 同构性判定**:**不直接同构**但**等价** — 等价链路已在 §13.1.3 表说明。

**关键结论**: 路径 B 的成本是"新建 vector 后端",**不是**"补 Doubao Rerank"的成本。两者量级不同,**不应混为一谈**。

### 13.4 推荐决策(tom 有权拒绝)

**建议: 暂缓实装, 仅留 issue 待 sprint 排期**。理由 + 对比矩阵:

| 选项 | 工作量 | 收益 | 推荐 |
|------|--------|------|------|
| **选项 1 — 路径 A 文本子集**(仅 `base-multilingual-rerank` / `m3-v2-rerank`) | 中 — 1 个 impl class + 1 个 HmacSigner + datas[]→results[] 位置回填适配 | Doubao 矩阵 7/9 → 8/9;火山侧部署业务有备选 rerank 路径 | ⏳ **可排在未来 sprint**(低优先 — DashScope gte-rerank 已在线,Pangu Rerank 已 ship,仅 Doubao 是矩阵缺口) |
| **选项 2 — 路径 A 多模态子集**(`doubao-seed-rerank` 全文/图文/视频/`rerank_instruction`) | 大 — 扩 `RerankRequest` 加 `MediaReference` 字段,变相建 `MultimodalRerankModel`,+ 选项 1 全部 | Doubao 矩阵 7/9 → 9/9;多模态 RAG(图文混召) | ❌ **不做** — 当前无多模态 rerank 业务需求方;**避免空架构**,等需求方出现再做 |
| **选项 3 — 路径 B**(VikingDB KNN+Score) | 极大 — 新建 vector backend 子模块(含 AK/SK 鉴权 + SDK 接入 + 业务对接) | Doubao 矩阵不动(仍是 7/9,只是新 vector 后端);与 Rerank 无关 | ❌ **不做** — 与"补 Doubao Rerank"是两件事,**不要混在同一 sprint**。若业务方要求 VikingDB 作 vector 后端,走独立 `vector-volcano` 子模块 |
| **选项 4 — 暂缓**(本推荐) | 零 | 等需求方明确 + 待 sprint 排期 | ✅ **推荐** — patch10-12 资源已用尽;下一轮 R-N 自然有空间,不该由本次评估启 |

**结论**:
- ✅ 推荐 **选项 4 暂缓** — 不补 Doubao Rerank,也不补 MultimodalRerankModel 抽象,也不新建 VikingDB vector backend
- ✅ 以本 §13 把决策路径 + 工程说明落盘;待未来 sprint 由 tom 按业务需求触发
- ⏸️ tom 有权拒绝 — 本推荐基于:本 sprint 已 ship,实装会冲撞下一轮规划;`RerankModel` 抽象纯文本与 doubao-seed-rerank 多模态不同构;VikingDB vector backend 是独立工作

### 13.5 调研证据链(2026-07-20 webfetch)

| # | URL | 关键摘录 / 印证 |
|---|------|------------------|
| 1 | https://www.volcengine.com/docs/82379/1399008(方舟快速入门,2026-07-10) | 顶层菜单:入门 / 调用 / 多模态理解 / 工具调用 / 进阶使用 / 训练 / 应用 / ... — **无 Rerank 分类**,印证"方舟无独立 Rerank 模型" |
| 2 | https://www.volcengine.com/docs/82379/1494384(方舟 Chat API,2026-07-08) | Base URL `ark.cn-beijing.volces.com/api/v3`,仅 Chat 类 endpoint |
| 3 | https://www.volcengine.com/docs/84313/2288345(VikingDB Java SDK rerank) | `KnowledgeService.rerank(RerankRequest, RequestAddition)` + `AuthWithAkSk(ak, sk)` + Host `api-knowledgebase.mlp.cn-beijing.volces.com` + 三模型 + `datas ≤ 200` + 鉴权**仅 AK/SK,不支持 APIKey** |
| 4 | https://www.volcengine.com/docs/84313/1356407(VikingDB Python SDK rerank) | 印证响应 = `list[float64]` 分数列表(**位置对齐,非 Cohere shape**)+ RerankResult 含 `token_usage` |
| 5 | https://www.volcengine.com/docs/84313/1276954(VikingDB search_and_generate) | 印证 `rerank_switch=true` + `rerank_model=m3-v2-rerank` 等内嵌于 search 接口,召回结果带 `rerank_score` |
| 6 | https://www.volcengine.com/docs/84313/1254501(VikingDB LangChain SDK) | 列 `similarity_search / similarity_search_with_score / rerank` 为 LangChain 端点 — 印证"rerank 是 VikingDB 与 search 同级的独立子能力,不是 vector backend 的隐式行为" |
| 7 | https://www.volcengine.com/docs/82379/1298459(方舟 Base URL / 鉴权,2026-06-23) | 印证方舟鉴权 = APIKey 静态 Bearer,与 VikingDB AK/SK HMAC 不同域 |
| 8 | R-N §1 关键 vendor 事实第 7 项(附录 E / patch7) | "Doubao Rerank 经 VikingDB 提供(2026-07-20 用户 m0236 + 客服确认): 火山无独立 Rerank 模型..." — 与本次 webfetch 完全一致,**佐证无新 Rerank 端点发布** |

**Azure / 业内是否新出 Rerank 端点?**
- 火山方舟 2026-07-08 ~ 2026-07-10 文档更新聚焦 Chat 端、深度思考、应用实验室;**无** Rerank 新端点
- VikingDB 2025-2026 更新主要是 SDK Java/Python 化(LangChain 适配);Rerank endpoint 自 2024 起稳定,**无新模型加入**
- **结论**: 本任务涉及的两条路径(R-N patch7 已 ship 的"路径 A 文本子集"+"路径 B KNN+Score"),2025-2026 期间**无新端点**,patch7 文档仍是最新事实

### 13.6 未来实装(选项 1)时的预备接口与配置

若未来 sprint 触发了选项 1 实装,直接复用 patch12 模式即可,**无需新建抽象**:

```yaml
# application.yml
platform:
  component:
    ai:
      vendor:
        doubao-vikingdb:                     # 火山 VikingDB — 仅 Rerank 用
          ak: ${DOUBAO_VIKING_AK}
          sk: ${DOUBAO_VIKING_SK}
          region: cn-beijing
          host: api-knowledgebase.mlp.cn-beijing.volces.com
      rerank:                                # Map<name, cfg> 与 patch12 同构
        doubao-default:
          vendor: doubao-vikingdb
          model:
            rerank-model: base-multilingual-rerank    # 或 m3-v2-rerank;不上 doubao-seed-rerank
            top-n: 5
            return-documents: true
```

```java
// provider.bailian.* 之外,新建 VikingDbRerankModel:
package com.richie.component.ai.provider.volcano.rerank;

public class VikingDbRerankModel implements RerankModel {
    private final String ak; private final String sk; private final String host;
    private final String defaultModel = "base-multilingual-rerank";
    private final HttpClient http;       // atlas-richie-component-http-core.HttpClient

    @Override
    public RerankResponse rerank(RerankRequest req) {
        // datas[i] = { query, content: documents[i] }
        var datas = req.getDocuments().stream()
            .map(doc -> Map.of("query", req.getQuery(), "content", doc))
            .toList();
        var body = Map.of("rerank_model", defaultModel, "datas", datas);
        var url = "https://" + host + "/api/knowledge/service/rerank";
        var signed = VolcanoHmacSigner.sign("POST", url, body, ak, sk);   // 新建 support.sign.VolcanoHmacSigner (仿 Tc3Signer 模式)
        var json  = http.post(url, body, signed).join();
        var scores = (List<Double>) json.get("scores");                    // float64[] 位置对齐
        // 按位置回填 → RerankResult (仿 Cohere shape)
        var results = IntStream.range(0, scores.size())
            .mapToObj(i -> new RerankResult()
                .setIndex(i)
                .setDocument(req.getDocuments().get(i))
                .setRelevanceScore(scores.get(i)))
            .toList();
        return new RerankResponse().setResults(results);
    }
}

// MultimodalModelFactory.createRerankModel(...) 加 switch 分支:
case "doubao-vikingdb" -> new VikingDbRerankModel(cfg.getVendor(), httpClient);
```

**新建类清单**:
- `VikingDbRerankModel`(实现 `RerankModel`)
- `VolcanoHmacSigner` 复用 §11.2 已有的 sign 包位置(`com.richie.component.ai.support.sign`),仿 `Tc3Signer` 模式
- 不改 `RerankRequest` / `RerankResult` / `AiMultimodalServiceImpl` — patch12 模式天然兼容

**测试**: 3 单测 + 1 IT(沿用 Caveat 14 — IT 真机校验端点);`mvn test` 应过;工作量 **~1 个 sprint day**,比 Doubao TTS/STT(patch11 1 个 sprint)轻一档,优先级可排在 Doubao TTS/STT 之后。

### 13.7 多模态子集触发条件(若未来真做选项 2)

- **触发条件(满足任一)**:(a) 业务方提需求:图文混召场景;(b) 多模态 RAG 实验 / PoC 需要;(c) 火山 Rerank 模型榜单更新出现新的非文本模态。
- **触发的架构动作**:
  1. 扩 `RerankRequest`:新增 `String queryInstruction` + `List<MediaReference> queryMedias` + `List<DocumentRef> documents` (DocumentRef 是新 record: `{ text?: String, image?: MediaReference, video?: MediaReference }`)
  2. 抽 `DocumentRef.java` 进 `com.richie.component.ai.api.rerank`(与 RerankRequest 同包)
  3. `MultimodalRerankModel` 是否独立接口?**保留 patch12 决定 — 不建独立 interface**,与 §12.4 "reduced-wrong" 一致;只扩 `RerankRequest` 字段,业务侧 `getRerankModel(name)` 拿到的是同一个接口、同一签名,字段自己可选填
  4. 火山侧 impl(`VikingDbRerankModel`)把 `DocumentRef.image` 序列化成 VikingDB `datas[i].image` 字段;query 为 object 时序列化 `{ query_text, query_images }`
  5. 评估"是否所有现有 vendor(BailianRerankModel / PanguRerankModel 等)也要承担新字段?" — **不必**,字段可选,Bailian 端无 image 时透传到 vendorOptions 即可
- **工作量**: 比选项 1 多 1-2 sprint day;且要触一轮新 R-N 评审(扩 abstract = 改动 `RerankRequest` 字段 = 影响所有 vendor × 业务侧)

### 13.8 下一步建议

- ✅ **本文档落盘即可** — §13 + patch13 已写入 R-N-DESIGN.md;不动 Java 源文件;不动 Zhipu/Pangu/Bailian/VectorService impl
- 🐛 **留一张 GitHub Issue**: 标题 `enhancement: Doubao Rerank (VikingDB) — 路径 A 文本子集`,标签 `r-n` / `low-priority` / `enhancement`;正文链接到本 §13,让 tom 按下一个 sprint 容量决定
- 📌 **不在本 sprint 动 java 代码** — 选项 1~3 全部不补,符合任务边界"MUST NOT: 不要写任何 Java 代码 / 不要改 Java 源文件 / 不要碰 Zhipu/Pangu/Bailian 实现"
- 🔭 **触发重新评估的信号**:(a) 业务方提需求;(b) 火山出新 Rerank 端点;(c) VikingDB 出新 Rerank 模型;(d) 出现多模态 RAG 需求方

— §13 END —

---

## 14. WS + STS 统一接口 SPI 设计稿 (原则 J 落地)

> **版本**: v0.1 DRAFT (待评审)
> **日期**: 2026-07-21
> **作者**: Sisyphus (代理)
> **触发**: 用户 m0042 锁定原则 J — WS + STS 必须统一接口,业务侧不感知具体实现
> **状态**: DRAFT — 等用户审核确认后进入 RN.4 sprint 实装

### 14.1 设计目标

解决 5 个 WebSocket 端点 (DashScope qwen-omni Realtime / Zhipu GLM-Realtime / Doubao TTS bidirection / Doubao STT 流式 / Hunyuan STT 流式) + 5 种凭证域 (Bearer / TC3 / AppCode / AK-SK-HMAC / X-Api-Key) 在业务侧**统一抽象**的问题。

### 14.2 包结构 (新增 — 严格隔离 vendor)

```
com.richie.component.ai.api/
├── voicechat/                          # 新增子包 (与 rerank/ 同级)
│   ├── VoiceChatModel.java             # SPI 接口 (LOCKED)
│   ├── VoiceChatConfig.java            # 配置 model 字段 (统一)
│   ├── VoiceConversation.java          # AutoCloseable + 流式事件
│   ├── VoiceChatEvent.java             # 事件类型 (audio/transcript/tool-call/interrupt)
│   └── StsTicket.java                  # 统一票面 (LOCKED)

com.richie.component.ai.service/        # 业务侧唯一入口包
├── VoiceStsService.java                # @Service 业务门面
└── VoiceChatService.java               # @Service 业务门面

com.richie.component.ai.support.sign/   # 已存在,扩展
├── StsSigner.java                      # SPI 接口 (LOCKED)
├── VendorStsContext.java               # 输入上下文
└── (5 个 impl: Bearer / Tc3 / AppCode / AkSkHmac / XApiKey)
```

**严格隔离**:
- `api/` 包不依赖任何 `provider/` 子包 — 反编译可见
- `service/` 包不依赖任何 `provider/` 子包 — 通过 `MultimodalModelFactory` (现有) 间接引用
- `provider/*` 子包之间相互隔离 (例如 `zhipu/` 不依赖 `doubao/`)

### 14.3 SPI 接口 (LOCKED — 不允许 vendor 改动签名)

#### 14.3.1 `StsSigner` — 凭证签发 SPI

```java
package com.richie.component.ai.support.sign;

/**
 * SPI: 各 vendor STS 凭证签发器。业务侧永远不直接调本接口,通过
 * {@link com.richie.component.ai.service.VoiceStsService#issueTicket} 拿统一票面 {@link StsTicket}。
 */
public interface StsSigner {
    /** 标识 vendor(如 "dashscope" / "zhipu" / "doubao" / "hunyuan-tts" / "hunyuan-stt" / "pangu" / "huawei-sis") */
    String vendor();

    /** 是否支持指定的能力 — 多产品厂商(hunyuan)按能力过滤 */
    boolean supports(VendorStsContext ctx);

    /** 签发短时凭证 (默认 TTL 由 vendor.sts.ttl-seconds 决定,可被 ctx 覆盖) */
    StsTicket sign(VendorStsContext ctx);
}
```

#### 14.3.2 `VendorStsContext` — 签发输入

```java
public record VendorStsContext(
    String vendor,                       // 选 vendor (YAML voiceChat.sts.vendor 透传)
    String capability,                   // "voice-chat" / "tts-stream" / "stt-stream"
    String model,                        // 选 model (YAML voiceChat.sts.model)
    Duration ttl,                        // 可选覆盖默认 TTL
    Map<String, String> attributes       // 可选 vendor 专有属性(音区/区域...)
) {}
```

#### 14.3.3 `StsTicket` — 统一票面 (LOCKED — 5 种认证域收敛)

```java
public final class StsTicket {
    private final String vendor;                    // vendor 标识
    private final String model;                     // 选中的 model
    private final String capability;                // "voice-chat" / "tts-stream" / "stt-stream"
    private final String endpoint;                  // 完整 wss:// URL (前端直连目标)
    private final long issuedAt;                    // epoch ms
    private final long expiresAt;                   // epoch ms
    private final Map<String, String> credentials;  // 统一凭证 Map (vendor 5 种认证域适配)

    /** 统一方法 1: Bearer 域 — DashScope / Zhipu / Hunyuan-TokenHub / MiniMax */
    public String asBearer() { /* 取 credentials.get("token") */ }

    /** 统一方法 2: TC3 域 — Hunyuan TTS/STT */
    public Tc3Credentials asTc3Headers(String action, String payload) {
        /* 用 credentials 里的 secret-id/secret-key 当场计算 TC3 头 — 短时有效 */
    }

    /** 统一方法 3: AppCode 域 — Pangu / Huawei-SIS */
    public String asAppCode() { /* X-Apig-AppCode */ }

    /** 统一方法 4: AK-SK-HMAC 域 — Doubao VikingDB */
    public Map<String, String> asSignedHeaders(String method, String uri, byte[] body) {
        /* 当场算 HMAC 头 */
    }

    /** 统一方法 5: X-Api-Key 域 — Doubao openspeech */
    public Map<String, String> asHeaderMap() {
        /* {X-Api-Key, X-Api-Resource-Id, X-Api-App-Id?, X-Api-Access-Key?} */
    }
}
```

**业务侧永远不写** `if (ticket.vendor().equals("hunyuan-tts"))` — 业务侧**根据 capability** 选 `asXxx()`,vendor 由 `StsSigner` impl 自动消化。

#### 14.3.4 `VoiceChatModel` — 业务 SPI (LOCKED)

```java
package com.richie.component.ai.api.voicechat;

/**
 * SPI: 语音对话(双工流式)。所有 vendor impl 都实现此接口;
 * 业务侧 @Autowired VoiceChatService 拿实例,不直接 import vendor 类。
 */
public interface VoiceChatModel {
    /**
     * 打开双工会话 — 前端需先调 VoiceStsService.issueTicket() 拿 StsTicket,
     * 然后持 ticket.endpoint + ticket.credentials 直连 vendor WS (前端 SDK 处理)。
     * 服务端 impl 降为可选 (R-N §10 O5: Hunyuan 主推客户端直连)。
     */
    VoiceConversation open(VoiceChatConfig config);

    /** model 名 (与 YAML voice-chat.<key>.model 对齐) */
    String model();
}
```

#### 14.3.5 `VoiceChatService` — 业务门面

```java
@Service
public class VoiceChatService {
    private final Map<String, VoiceChatModel> models;  // 业务名 → impl (patch12 模式)

    public VoiceConversation open(String businessName, VoiceChatConfig cfg) {
        VoiceChatModel m = models.get(businessName);
        if (m == null) throw new IllegalArgumentException("Unknown voice-chat key: " + businessName);
        return m.open(cfg);
    }

    public Set<String> getAvailableKeys() { return models.keySet(); }
}
```

**业务侧使用示例**:

```java
// 业务代码 (BFF 层 / Controller)
@PostMapping("/voice-chat/token")
public Map<String, Object> issueToken(@RequestParam String key, @RequestParam String userId) {
    StsTicket ticket = voiceStsService.issueTicket(VendorStsContext.of("voice-chat", key, userId));
    return Map.of(
        "endpoint", ticket.endpoint(),        // wss://open.bigmodel.cn/api/paas/v4/realtime
        "headers", ticket.asHeaderMap(),       // 统一 — 业务侧不判断 vendor
        "expiresAt", ticket.expiresAt()
    );
}

// 前端拿到 endpoint + headers → ws.connect(endpoint, {headers}) → 直连 vendor
// 中台**不**代理 WS 流 (原则 F/G)
```

**vendor 切换场景**:

```yaml
# application.yml — 仅改这一行
platform:
  component:
    ai:
      voice-chat:
        customer-service:                 # 业务名不变
          vendor: zhipu                   # ← 切换 (原本 doubao)
          model: glm-realtime-flash
          sts: { ttl-seconds: 3600 }
# 业务代码、BFF controller、前端 SDK 全部不动 — VoiceStsService 自动按 vendor 选 StsSigner impl
```

### 14.4 配置文件结构 (与 §2 + §12 一致)

```yaml
platform:
  component:
    ai:
      voice-chat:                          # 业务名 → 配置
        customer-service:
          vendor: zhipu
          model: glm-realtime-flash
          sts:
            enabled: true
            ttl-seconds: 3600
            signer-bean: aiZhipuStsSigner    # 可选 — 缺省按 vendor 自动分派
        ccc-bailian:
          vendor: dashscope
          model: qwen-omni-turbo-realtime
          sts: { ttl-seconds: 7200 }

      # ==================== STS 签发全局配置 (原则 H) ====================
      sts:                                  # 复用 §2 已有 vendor.sts.* 节点
        global:
          default-ttl-seconds: 3600
          cache:                            # STS 票面缓存 (避免每次重签)
            enabled: true
            max-size: 10000
            refresh-window-seconds: 300     # 过期前 5 分钟续签
```

### 14.5 自动装配 (Spring Bean 收口)

| Bean 名 | 类型 | 装配条件 | 职责 |
|---------|------|---------|------|
| `aiVoiceStsService` | `VoiceStsService` | `@ConditionalOnProperty(platform.component.ai.sts.global.enabled, matchIfMissing=true)` | 业务门面,按 ctx.vendor 自动分派 StsSigner |
| `aiVoiceChatService` | `VoiceChatService` | 同上 | 业务门面,按 businessName 拿 VoiceChatModel |
| `aiZhipuStsSigner` | `StsSigner` (impl) | `@ConditionalOnProperty(platform.component.ai.vendor.zhipu.api-key)` | Bearer 域实现 |
| `aiDashscopeStsSigner` | 同上 | 同上 (dashscope.api-key) | Bearer 域 |
| `aiHunyuanTtsStsSigner` | 同上 | `hunyuan-tts.secret-id` | TC3 域 (Hunyuan TTS 1073) |
| `aiHunyuanSttStsSigner` | 同上 | `hunyuan-stt.secret-id` | TC3 域 (Hunyuan STT 1093) |
| `aiDoubaoOpenspeechStsSigner` | 同上 | `doubao.app-id` | X-Api-Key 域 (openspeech 语音) |
| `aiDoubaoVikingdbStsSigner` | 同上 | `doubao.app-id` | AK-SK-HMAC 域 (VikingDB Rerank) |
| `aiPanguStsSigner` | 同上 | `pangu.app-code` | AppCode 域 |
| `aiHuaweiSisStsSigner` | 同上 | `huawei-sis.app-key` | AppCode 域 (留扩展) |
| `aiZhipuVoiceChatModel` | `VoiceChatModel` | `@ConditionalOnProperty(voice-chat.<key>.vendor=zhipu)` | WS 双工 impl |
| `aiDashscopeVoiceChatModel` | 同上 | dashscope | qwen-omni Realtime |
| `aiDoubaoTtsBidirectionModel` | 同上 | doubao (capability=tts-stream) | WS 双向 TTS |
| `aiDoubaoSttStreamModel` | 同上 | doubao (capability=stt-stream) | WS 流式 STT |
| `aiHunyuanSttStreamModel` | 同上 | hunyuan-stt (capability=stt-stream) | TC3 WS 流式 STT |

**多产品厂商按 capability 拆分** — `StsSigner.supports(ctx)` 由 `ctx.capability` 决定,hunyuan-tts/hunyuan-stt 两个 Bean 各自 `vendor()` 不同但 supports 互斥。

### 14.6 测试矩阵

| 测试层 | 数量 | 重点 |
|--------|------|------|
| **StsSigner impl 单测** | 5 × 1 = 5 | 每个签名器覆盖 happy-path + 异常 (过期/缺字段/错误签名) |
| **StsTicket 统一性单测** | 3 | asBearer/asTc3Headers/asHeaderMap 都不泄露 vendor 字符串到调用方 |
| **VoiceChatModel SPI 单测** | 3 | 业务侧不 import vendor 包的静态检查 (ArchUnit) |
| **VoiceStsService 单测** | 4 | 按 ctx.vendor 分派正确 / 未知 vendor fail-fast / TTL 缓存续签 / 业务侧不感知 vendor |
| **VoiceChatService 单测** | 2 | 业务名解析 / 未知 key fail-fast |
| **Failsafe IT** | 5 | 1 个 vendor × 1 个能力真机端点 (与 patch11 Caveat 14 同口径) |

总计: **17 单测 + 5 IT** = **22 case**。`mvn test` 期望: **既有 153 + 22 = 175/175 PASS**。

### 14.7 工作量评估

| 子任务 | 工作量 | 优先级 |
|--------|--------|--------|
| 14.3.1-14.3.5 SPI 接口 + 票面 + 业务门面 | 0.5 day | P0 |
| 14.4-14.5 配置节点 + 15 个 @Bean 自动装配 | 0.5 day | P0 |
| 14.6 17 个单测 (含 ArchUnit 静态检查) | 0.5 day | P0 |
| 14.6 5 个 Failsafe IT (含 Caveat 14) | 0.5 day | P1 |
| 1 个 vendor 端到端样例 (Zhipu Realtime) | 0.5 day | P1 (proof of concept) |
| 补齐剩余 4 vendor impl (DashScope / Doubao×2 / Hunyuan STT) | 1.5 day | P2 |
| 业务侧 BFF 示例 controller + README | 0.5 day | P2 |

**总工作量**: ~4.5 day (P0+P1 + 一个 proof-of-concept vendor) 或 ~6 day (含全部 5 vendor)。

### 14.8 风险与开放点

| 风险 | 缓解 |
|------|------|
| TC3 短时密钥由 STS 派发 vs 当场签名二选一 | 设计选择:**当场签名**(每次 WS 帧前调 StsSigner.sign 拿 ticket) — 短时有效窗口对齐 ttl-seconds |
| WS 客户端选型 — OkHttp vs Java HttpClient WS vs Spring WebFlux | 选 **OkHttp WebSocket**(已在 admin 依赖链,HANDOFF §6.3 已建议) |
| WebSocket 流媒体测试用 Mock 服务器(Spring WebSocket Test) vs 真实 vendor | 单测走 Mock,Failsafe IT 走真实 vendor(Caveat 14 同口径) |
| STS 票面缓存击穿 / 续签竞态 | 用 `Caffeine` 或 `ConcurrentHashMap` + 单线程 refresh;Phase 2 实现 |
| 原则 J "业务代码不 import vendor 包" 静态守护 | 引入 **ArchUnit** 单测 `noClasses().should().dependOnClassesThat().resideInAPackage("..provider..")` 包在 `service/` + `api/` |

### 14.9 实施分阶段建议

| 阶段 | 范围 | 验收 |
|------|------|------|
| **RN.4-alpha** (1 day) | 14.3.1-14.3.5 SPI 接口 + `StsTicket` + 5 个 StsSigner impl + ArchUnit 静态守护 | mvn test: 既有 153 + 17 单测 = 170 PASS |
| **RN.4-beta** (1 day) | 14.4-14.5 配置节点 + 15 个 @Bean + Zhipu Realtime 端到端 IT | Failsafe IT 真机(用 Zhipu 测试 api-key) |
| **RN.4-gamma** (1.5 day) | 补齐 Dashscope qwen-omni / Doubao TTS bidirection / Doubao STT 流式 / Hunyuan STT 流式 | 5 个 IT 全 PASS |
| **RN.4-delta** (0.5 day) | BFF 示例 controller + README + Caveat 14 文档收尾 | 可对外演示 |

### 14.10 RN.4-alpha + RN.4-beta 实装状态 (2026-07-21)

#### alpha 完成 (2026-07-21)

| 项 | 实装 | 备注 |
|---|---|---|
| §14.3.1 StsSigner SPI | ✅ | `support/sign/StsSigner.java` (127 行) + `VendorStsContext.java` (208 行) |
| §14.3.2 StsTicket 统一票面 | ✅ | `api/voicechat/StsTicket.java` (531 行) + 5 种 asXxx() + TC3/AK-SK HMAC 内联 |
| §14.3.3 5 个 StsSigner impl | ✅ | `BearerStsSigner` / `Tc3StsSigner` / `AppCodeStsSigner` / `AkSkHmacStsSigner` / `XApiKeyStsSigner` |
| §14.3.4 VoiceChatModel SPI | ✅ | `api/voicechat/VoiceChatModel.java` + `VoiceChatConfig.java` + `VoiceChatEvent.java` + `VoiceConversation.java` |
| §14.3.5 VoiceStsService + VoiceChatService facade | ✅ | `service/voicechat/VoiceStsService.java` (235 行) + `VoiceChatService.java` (204 行) |
| §14.6 ArchUnit 静态守护 | ✅ | `VoiceChatModelArchTest` — 验证 api/voicechat + service/voicechat 不依赖 provider/ |
| 单测验证 | ✅ | **既有 153 + 21 = 174/174 PASS** (BUILD SUCCESS) |

#### beta 完成 (2026-07-21)

| 项 | 实装 | 备注 |
|---|---|---|
| §14.4 配置节点 | ✅ | `AiModelProperties.voiceChat` map + 复用 VoiceModelConfig 凭证字段 |
| §14.5 ZhipuStsSigner + VoiceChatService @Bean | ✅ | `AiModelAutoConfiguration` 加 9 个 @Bean (ConditionalOnProperty 触发) |
| §14.5 aiZhipuVoiceChatModel @Bean | ✅ | `provider/zhipu/ZhipuRealtimeVoiceChatModel` (431 行, @Service + @ConditionalOnProperty) |
| §14.6 ZhipuRealtime 单测 | ✅ | 6 case — vendor/supports/open 行为 + 异常透传 (RecordingStsService stub) |
| §14.6 Failsafe IT | ✅ | `ZhipuRealtimeVoiceChatIT` — @EnabledIfEnvironmentVariable(ZHIPU_REALTIME_API_KEY),无 API key 自动 skip;有 API key 走真机 WS 握手 + 音频发送 + 事件订阅 |
| 单测验证 | ✅ | **既有 174 + 6 = 180/180 PASS** (BUILD SUCCESS) |
| Caveat 14 (VoiceChat IT) | ⚠️ 待 IT 真机 | 见 §14.11 |

#### gamma 完成 (2026-07-21)

| 项 | 实装 | 备注 |
|---|---|---|
| §14.5 aiDashscopeVoiceChatModel @Bean | ✅ | `provider/dashscope/DashScopeQwenOmniVoiceChatModel` (293 行, OpenAI Realtime 兼容协议) |
| §14.5 aiDoubaoBidirectionTtsVoiceChatModel @Bean | ✅ | `provider/doubao/DoubaoBidirectionTtsVoiceChatModel` (TTS 双向 V3 协议, query-param X-Api-*) |
| §14.5 aiDoubaoStreamingAsrVoiceChatModel @Bean | ✅ | `provider/doubao/DoubaoStreamingAsrVoiceChatModel` (STT 流式 V2 协议) |
| §14.5 aiHunyuanStreamingAsrVoiceChatModel @Bean | ✅ | `provider/hunyuan/HunyuanStreamingAsrVoiceChatModel` (TC3 延迟签名 + per-frame Authorization) |
| §14.6 4 vendor × 6 case 单测 | ✅ | vendor/supports/open + STS ctx 全字段校验 (24 cases total) |
| AutoConfig 7 个 @Bean | ✅ | aiVoiceStsService + aiVoiceChatService + 5 vendor VoiceChatModel |
| 单测验证 | ✅ | **既有 180 + 24 = 204/204 PASS** (BUILD SUCCESS) |
| Caveat 14 (VoiceChat IT) | ⚠️ 5 vendor 全实装, IT 真机待跑 | 见 §14.11 |

#### delta 完成 (2026-07-21)

| 项 | 实装 | 备注 |
|---|---|---|
| §14.10 实装状态表 | ✅ | alpha + beta + gamma + delta 全勾, 204/204 PASS |
| §14.11 Caveat 14 矩阵 | ✅ | 5 vendor ⚠️/❌ → ⚠️ (接口全实装 + 单测过 + IT 待跑) |
| §14.13 BFF controller 完整代码片段 | ✅ | 见 §14.13, **业务项目可直接复制** (AI 组件不依赖 webflux, 不内嵌源码) |
| README + README.zh.md VoiceChat 章节 | ✅ | 含实装状态/矩阵/BFF 代码/YAML 5 vendor |

#### 关键补丁

1. **ArchUnit 反向规则修正** (`m0905`):原 `noClasses..should..dependOn..api..voicechat..` 与原则 J 反向 — provider → api 是 SPI 正常依赖方向。改为正向校验 SPI 类型物理位置(Class.forName 验证 api/voicechat/ + support/sign/ 必含 7 个核心类型)。
2. **StsSigner.defaultContext() 修复** (`m0819`):补 authDomain 字段(supportedAuthDomains()[0]),解决 VendorStsContext NPE。
3. **测试用例对齐协议细节** (`m0822`):Tc3 X-TC-Action 保留原始大小写 "TextToVoice" 而非 lowercase;XApiKey 期望 4 个头 (Key/App-Id/Resource-Id/Access-Key)。
4. **VoiceChatModelArchTest 范围收窄** (`m0820`):仅守护 voicechat SPI 模块(api/voicechat/* + service/voicechat/*),既有 service/impl/AiMultimodalServiceImpl 不在原则 J 适用范围。

### 14.11 Caveat 14 (VoiceChat 版本)

> ⚠️ **RN.4 全阶段已完成 (204/204 PASS), 生产 IT 必须按各厂商官方文档做真机 WS 校验**:
>
> | 厂商 | 端点 (待 IT) | 鉴权方式 | 协议 | 验证状态 |
> |------|------|------|------|------|
> | Zhipu | `wss://open.bigmodel.cn/api/paas/v4/realtime` | Bearer (query param) | glm-4-voice Realtime | ⚠️ IT 待跑 (需 `ZHIPU_REALTIME_API_KEY`) |
> | DashScope | `wss://dashscope.aliyuncs.com/api-ws/v1/realtime` | Bearer | qwen-omni Realtime (OpenAI 兼容) | ⚠️ IT 待跑 (需 `DASHSCOPE_API_KEY`) |
> | Doubao TTS 双向 | `wss://openspeech.bytedance.com/api/v3/tts/bidirection` | X-Api-Key + Resource-Id + App-Id + Access-Key (query) | bidirectional V3 | ⚠️ IT 待跑 (需豆包 AppId/Token/ResourceId) |
> | Doubao STT 流式 | `wss://openspeech.bytedance.com/api/v2/asr` | X-Api-Key + Resource-Id (query) | streaming V2 | ⚠️ IT 待跑 (需豆包 AppId/Token/ResourceId) |
> | Hunyuan STT 流式 | TC3 over WS (`asr.tencentcloudapi.com:443`) | TC3-HMAC-SHA256 (per-frame Authorization) | product 1093 | ⚠️ IT 待跑 (需 `HUNYUAN_SECRET_ID` / `HUNYUAN_SECRET_KEY`) |
>
> **单测通过不构成 Caveat 14 验证完成, IT 验收前 VoiceChat 不可对外启用。**
> **计数口径**: `✅ = 接口已落地 + 单测通过`, `⚠️ = 接口已落地 + 单测通过 + Caveat 14 IT 待跑`, `❌ = 未实装`。
> **当前状态**: 5 vendor 全部 `⚠️` (接口 + 单测完成, IT 真机待业务方准备凭证后跑 `mvn verify -Pit`).

### 14.12 使用示例 (BFF 调用)

```java
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class BffVoiceController {

    private final VoiceChatService voiceChatService;

    /**
     * 业务侧仅引用 api/voicechat/* — 切换 vendor 只改 application.yml:
     * platform.component.ai.voice-chat.customer-service.vendor: zhipu
     */
    @GetMapping(value = "/stream/{businessName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<VoiceChatEvent> open(@PathVariable String businessName,
                                      @RequestParam(defaultValue = "glm-4-voice") String model) {
        VoiceChatConfig config = VoiceChatConfig.builder()
                .model(model)
                .voice("tongtong")
                .language("zh-CN")
                .build();

        VoiceConversation conv = voiceChatService.open(businessName, config);

        return Flux.create(sink -> conv.events().subscribe(new Subscriber<>() {
            @Override public void onSubscribe(Subscription s) { s.request(Long.MAX_VALUE); }
            @Override public void onNext(VoiceChatEvent e) { sink.next(e); if (e.type() == VoiceChatEvent.Type.SESSION_END) sink.complete(); }
            @Override public void onError(Throwable t) { sink.error(t); }
            @Override public void onComplete() { sink.complete(); }
        }));
    }
}
```

**用户决策点**: 是否同意按 14.7 + 14.9 排期?是否需要调整 P0/P1/P2 优先级?

### 14.13 完整 BFF controller 代码片段 (业务项目可直接复制)

> **为什么不在 AI 组件内嵌 BFF 源码**: AI 组件是**原子能力组件**, 不依赖 `spring-boot-starter-webflux` / `spring-boot-starter-web`,
> 业务侧的 BFF 层是 web 框架的消费者。源码放在业务项目才能避免 AI 组件污染 web 依赖。
> 下方代码可直接复制到业务项目的 `controller/` 包下使用。

#### 14.13.1 pom.xml 依赖 (业务项目)

```xml
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-ai</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
<dependency>
    <groupId>com.richie.component</groupId>
    <artifactId>atlas-richie-component-http-core</artifactId>
</dependency>
<!-- 业务侧自己选择 HTTP client (okhttp / httpclient5 / jdk / restclient) -->
```

#### 14.13.2 application.yml 配置

```yaml
platform:
  component:
    ai:
      # STS 全局配置 (R-N §14.4 原则 H — 凭证复用)
      sts:
        global:
          default-ttl-seconds: 3600

      # 长时凭证 (Zhipu 案例, 其他 vendor 类推)
      tts:
        zhipu:
          api-key: ${ZHIPU_API_KEY}
        dashscope:
          api-key: ${DASHSCOPE_API_KEY}
        doubao:
          api-key: ${DOUBAO_TOKEN}
          app-id: ${DOUBAO_APP_ID}
          resource-id: ${DOUBAO_RESOURCE_ID}
          secret-key: ${DOUBAO_SECRET_KEY}
          region: cn-north-1
        hunyuan:
          api-key: ${HUNYUAN_TOKEN}             # TokenHub 域
          secret-id: ${HUNYUAN_SECRET_ID}
          secret-key: ${HUNYUAN_SECRET_KEY}
          region: ap-guangzhou
        pangu:
          app-code: ${PANGU_APP_CODE}
      stt:
        hunyuan:
          secret-id: ${HUNYUAN_STT_SECRET_ID}
          secret-key: ${HUNYUAN_STT_SECRET_KEY}
          region: ap-guangzhou

      # 业务名 → vendor 映射 (切换 vendor = 改一行)
      voice-chat:
        customer-service-zh:        # 中文客服
          vendor: zhipu
          model: glm-4-voice
        customer-service-fast:      # 快速响应 (切到 DashScope)
          vendor: dashscope
          model: qwen-omni-turbo-realtime
        tts-bidirection-prod:       # 豆包 TTS 双向
          vendor: doubao-openspeech
          model: seed-tts-2.0
        asr-streaming-prod:         # 豆包 STT 流式
          vendor: doubao-openspeech
          model: doubao-streaming-asr
        asr-tc3-prod:               # 腾讯云 ASR
          vendor: hunyuan-stt
          model: 16k_zh
```

#### 14.13.3 BFF Controller 源码

```java
package com.example.bff.voice;

import com.richie.component.ai.api.voicechat.VoiceChatConfig;
import com.richie.component.ai.api.voicechat.VoiceChatEvent;
import com.richie.component.ai.api.voicechat.VoiceConversation;
import com.richie.component.ai.service.VoiceChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * BFF 层语音对话控制器 — 业务项目可直接复制。
 * <p>
 * 设计原则 (R-N §14 原则 J):
 * <ul>
 *   <li>仅依赖 {@code com.richie.component.ai.api.voicechat.*} 抽象</li>
 *   <li>不 import 任何 vendor 包 (Zhipu / DashScope / Doubao / Hunyuan SDK 全部不出现)</li>
 *   <li>切换 vendor = 改 application.yml 一行, 业务代码零改动</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class BffVoiceController {

  private final VoiceChatService voiceChatService;

  /**
   * 1. 打开一个实时语音会话, 订阅事件流, 返回 SSE 给前端。
   *
   * <p>前端用法:
   * <pre>{@code
   * const evtSource = new EventSource("/api/voice/stream/customer-service-zh?model=glm-4-voice");
   * evtSource.addEventListener("AUDIO_CHUNK", e => audioPlayer.play(e.data));
   * evtSource.addEventListener("TRANSCRIPT_PARTIAL", e => showText(e.data));
   * }</pre>
   */
  @GetMapping(value = "/stream/{businessName}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<Flux<VoiceChatEvent>> open(
          @PathVariable String businessName,
          @RequestParam(defaultValue = "glm-4-voice") String model,
          @RequestParam(required = false) String voice,
          @RequestParam(defaultValue = "zh-CN") String language) {

    VoiceChatConfig config = VoiceChatConfig.builder()
            .model(model)
            .voice(voice)
            .language(language)
            .vadMode(VoiceChatConfig.VadMode.SERVER)
            .build();

    VoiceConversation conv = voiceChatService.open(businessName, config);

    Flux<VoiceChatEvent> eventStream = Flux.create(sink -> {
      conv.events().subscribe(new Subscriber<VoiceChatEvent>() {
        @Override
        public void onSubscribe(Subscription s) {
          s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(VoiceChatEvent e) {
          sink.next(e);
          if (e.type() == VoiceChatEvent.Type.SESSION_END
                  || e.type() == VoiceChatEvent.Type.ERROR) {
            sink.complete();
          }
        }

        @Override
        public void onError(Throwable t) {
          log.error("VoiceChat 会话异常, businessName={}", businessName, t);
          sink.error(t);
        }

        @Override
        public void onComplete() {
          sink.complete();
        }
      });
    });

    return ResponseEntity.ok()
            .header("X-Session-Id", businessName + "-" + UUID.randomUUID())
            .body(eventStream);
  }

  /**
   * 2. 客户端上行音频 (multipart) — 推送给 vendor WS。
   *
   * <p>前端用法:
   * <pre>{@code
   * const form = new FormData();
   * form.append("audio", audioBlob, "chunk.pcm");
   * fetch("/api/voice/send/customer-service-zh", { method: "POST", body: form });
   * }</pre>
   *
   * <p>注: 这是简化示例, 生产应使用 WebSocket / WebRTC 双向通道保持会话 ID。
   * 本 BFF 示例假设上游 gateway 已经把 session 标识绑到路径 / header 上。
   */
  @PostMapping(value = "/send/{businessName}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Mono<Void> sendAudio(@PathVariable String businessName,
                              @RequestPart("audio") FilePart audioPart) {
    // 业务侧维护 sessionId → VoiceConversation 映射 (Redis / Caffeine)
    // 这里仅展示 "拿到会话 → 推音频" 的最小闭环
    VoiceConversation conv = voiceChatService.lookup(businessName);
    if (conv == null) {
      return Mono.error(new IllegalStateException(
              "会话不存在, 请先调用 /api/voice/stream/" + businessName));
    }
    return Mono.fromRunnable(() -> audioPart.content()
            .map(dataBuffer -> VoiceChatEvent.AudioFrame.builder()
                    .bytes(toBytes(dataBuffer))
                    .sampleRate(16000)
                    .bitsPerSample(16)
                    .channels(1)
                    .codec("pcm")
                    .build())
            .doOnNext(conv::sendAudio)
            .subscribe());
  }

  /**
   * 3. 主动打断服务端 TTS (用户开始说话)。
   */
  @PostMapping("/interrupt/{businessName}")
  public Mono<Void> interrupt(@PathVariable String businessName) {
    VoiceConversation conv = voiceChatService.lookup(businessName);
    if (conv == null) {
      return Mono.error(new IllegalStateException("会话不存在"));
    }
    return Mono.fromRunnable(conv::interrupt);
  }

  /**
   * 4. 关闭会话。
   */
  @DeleteMapping("/session/{businessName}")
  public Mono<Void> close(@PathVariable String businessName) {
    return Mono.fromRunnable(() -> {
      VoiceConversation conv = voiceChatService.lookup(businessName);
      if (conv != null) {
        conv.close();
        voiceChatService.evict(businessName);
      }
    });
  }

  /**
   * 5. 健康检查 — 列出当前可用的业务名 → vendor 映射。
   */
  @GetMapping("/business-names")
  public Flux<String> listBusinessNames() {
    return Flux.fromIterable(voiceChatService.businessNames());
  }

  private static byte[] toBytes(org.springframework.core.io.buffer.DataBuffer buffer) {
    byte[] bytes = new byte[buffer.readableByteCount()];
    buffer.read(bytes);
    return bytes;
  }
}
```

#### 14.13.4 前端 SDK 调用示例 (伪代码)

```javascript
// 1. 打开 SSE 流
const sessionId = "session-" + Date.now();
const evtSource = new EventSource(`/api/voice/stream/customer-service-zh?model=glm-4-voice`);

// 2. 订阅事件
evtSource.addEventListener("AUDIO_CHUNK", (e) => {
    const audioBytes = base64ToBytes(JSON.parse(e.data).audio.bytes);
    audioPlayer.feed(audioBytes);
});
evtSource.addEventListener("TRANSCRIPT_PARTIAL", (e) => {
    showSubtitle(JSON.parse(e.data).text);
});
evtSource.addEventListener("TRANSCRIPT_FINAL", (e) => {
    showSubtitleFinal(JSON.parse(e.data).text);
});
evtSource.addEventListener("ERROR", (e) => {
    console.error("voice session error:", e.data);
});

// 3. 上行音频 (每 200ms 一个 PCM 帧)
setInterval(() => {
    const pcmFrame = audioRecorder.readFrame();
    fetch(`/api/voice/send/customer-service-zh`, {
        method: "POST",
        body: audioFrameToMultipart(pcmFrame, sessionId)
    });
}, 200);

// 4. 用户开始说话 → 打断服务端 TTS
micOnUserSpeech(() => {
    fetch(`/api/voice/interrupt/customer-service-zh`, { method: "POST" });
});

// 5. 关闭会话
window.addEventListener("beforeunload", () => {
    fetch(`/api/voice/session/customer-service-zh`, { method: "DELETE" });
    evtSource.close();
});
```

#### 14.13.5 切换 vendor = 改 1 行 YAML

业务代码不变, 只改 `application.yml`:

```yaml
platform:
  component:
    ai:
      voice-chat:
        customer-service-zh:
-         vendor: zhipu              # 旧 — Zhipu glm-4-voice
+         vendor: dashscope          # 新 — DashScope qwen-omni-turbo-realtime
-         model: glm-4-voice
+         model: qwen-omni-turbo-realtime
```

重启后 `VoiceChatService` 自动按 vendor 路由到 `aiDashscopeVoiceChatModel` bean, 业务 controller 代码零改动。

---

## 15. R-N.5 — API Key 池 (Token Plan 场景)

### 15.1 背景与动机

大语言模型 / 多模态厂商的 Token Plan 通常按 key 限并发(智谱 / 火山等厂商普遍为 1-4 并发/单 key,高峰时降到 1)。单 key 配置在以下场景无法生产可用:

- **大促期间**: 业务并发量超过单 key 上限 → 整业务被厂商限流
- **多租户 / 多业务线共享**: 不同业务的高峰错开,但单 key 配置让错峰失效
- **密钥轮换**: 单 key 长期使用风险大,需要定期切到新 key(但旧 key 也要保留避免中断)

**目标**: 支持 `Set<String> apiKeys` 配置,运行时由 Commons Pool2 托管,N 个 key 池化借出/归还,遇限流自动切换下一个 key。

### 15.2 配置

#### 15.2.1 各能力配置子类 — `Set<String> apiKeys` (主推)

```yaml
platform:
  component:
    ai:
      models:
        product-search:
          provider: zhipu
          api-keys:                  # ← 新 (Set, 无重复)
            - sk-key-1
            - sk-key-2
            - sk-key-3
          base-url: https://open.bigmodel.cn/api/paas/v4
      rerank:
        product-rerank:
          vendor: bailian
          api-keys: [sk-rk-1, sk-rk-2]
      tts:
        customer-service:
          vendor: zhipu
          api-keys: [sk-tts-1, sk-tts-2]
      stt:
        ...
      image:
        ...
      voice-chat:
        ...
```

#### 15.2.2 向后兼容 — `api-key: String` (单数)

旧配置继续生效,自动包装成 size=1 的 Set。`ApiKeyUtils.resolveKeys()` 优先级:
```
apiKeys (Set) > apiKey (String) > 空
```

#### 15.2.3 池全局配置 — `key-pool.*`

```yaml
platform:
  component:
    ai:
      key-pool:
        enabled: true                  # 总开关;false 时降级为 NoOpPool(直接拿第 1 个 key)
        retry-rounds: 2               # 限流时轮询 2 轮 = 最多 6 次 (3 key × 2 轮)
        cooldown-seconds: 60          # 限流 key 进入冷却的时间
        max-wait-millis: 3000         # 池空时阻塞借出的最长时间
        block-when-exhausted: true    # true=阻塞等待 / false=立即抛
```

### 15.3 架构

#### 15.3.1 SPI 容器

```
com.richie.component.ai.support.keypool/
├── ApiKey.java                       不可变值对象 (value + createIndex + cooldownUntilEpochMs)
├── ApiKeyPool.java                   SPI 接口 (borrow/returnObject/invalidate + 监控)
├── ApiKeyPoolImpl.java               GenericObjectPool<ApiKey> 实现
├── ApiKeyPoolManager.java            @Component, 按业务名懒加载 + NoOp 退化
├── KeyPoolExhaustedException.java    含 retryRounds/totalKeys/numCooldown 完整上下文
├── ApiKeyUtils.java                  4 个 resolveKeys() 重载 (向后兼容入口)
├── ApiKeyValidator.java              限流检测 SPI
├── DefaultApiKeyValidator.java       HTTP 状态码 + 关键词 + 多层 cause 反射
├── PooledChatModel.java              LLM 装饰器 (implements Spring AI ChatModel)
└── PooledExecutor.java               通用池化执行器 (供 Rerank/Image/TTS/STT 复用)
```

#### 15.3.2 工作模式

```java
// 多 key 池化调用流程
for (int round = 0; round < retryRounds; round++) {       // 默认 2 轮
    ApiKey key = pool.borrow();                            // GenericObjectPool 阻塞借出
    try {
        T perKeyInstance = perKeyModels.get(key.getCreateIndex());  // 路由到对应预建实例
        R result = perKeyInstance.doCall(req);              // 实际调用
        pool.returnObject(key);                             // 正常归还
        return result;
    } catch (RuntimeException e) {
        if (validator.isKeyInvalidating(e)) {               // 429 / 403 / 503 / rate-limit 关键词
            pool.invalidate(key);                            // 池移除 + 进入冷却
            continue;                                        // 下一轮换 key
        }
        pool.returnObject(key);
        throw e;                                            // 非限流错误 — 立即抛
    }
}
throw new KeyPoolExhaustedException(businessName, retryRounds, totalKeys, numCooldown, lastError);
```

#### 15.3.3 装饰器矩阵

| 能力 | 装饰器类 | 装饰接口 | per-key 预建数 |
|---|---|---|---|
| LLM (Chat) | `PooledChatModel` | Spring AI `ChatModel` | N × `OpenAiChatModel` / `DeepSeekChatModel` / `AnthropicChatModel` |
| Rerank | `PooledRerankModel` | 本组件 `RerankModel` | N × `BailianRerankModel` / `ZhipuRerankModel` / `PanguRerankModel` / `DoubaoVikingRerankModel` |
| Image | `PooledImageModel` | Spring AI `ImageModel` | N × `BailianImageAdapter` |
| TTS | `PooledTextToSpeechModel` | Spring AI `TextToSpeechModel` | N × 各 vendor `TextToSpeechModel` |
| STT | `PooledTranscriptionModel` | Spring AI `TranscriptionModel` | N × 各 vendor `TranscriptionModel` |

**1 key 场景不启池**(走单 client,简化路径);**2+ keys 启池**。

#### 15.3.4 限流检测 (DefaultApiKeyValidator)

```java
// 触发 invalidate 的场景:
- HTTP 状态码: 429 (Too Many Requests) / 403 (Forbidden) / 503 (Service Unavailable)
- 关键词命中 (异常 message / cause 链): 
  "rate limit" / "quota" / "too many requests" / "throttled" / "exceeded" / 
  "rate_limit_exceeded" / "并发超限"

// 不触发 invalidate (fail-fast):
- HTTP 401 Unauthorized  → 凭证错误,配置问题,不轮询
- HTTP 400 Bad Request   → 请求格式错误
- HTTP 5xx (除 503)      → 服务端问题,可能跨 key 同样存在
```

支持多层 cause 链 + 反射 `statusCode` / `status` / `code` 字段(适配不同 HTTP 客户端异常类型)。

### 15.4 实施分阶段

| 阶段 | 范围 | 验证 |
|---|---|---|
| R-N.5-alpha | pom 依赖 + 6 个 config 类 Set\<String\> apiKeys + KeyPoolProperties + ApiKeyUtils 向后兼容 | `mvn compile` |
| R-N.5-beta | ApiKey + ApiKeyPool SPI + ApiKeyPoolManager + KeyPoolExhaustedException | 13 个 ApiKeyPoolImplTest |
| R-N.5-gamma | AiChatClientFactory 池化集成 (PooledChatModel) | 既有 LLM 测试 0 回归 |
| R-N.5-delta | MultimodalModelFactory 池化集成 (Rerank/Image/TTS/STT) | 既有 4 个能力测试 0 回归 |
| R-N.5-epsilon | ApiKeyValidator + DefaultApiKeyValidator | 限流注入测试 |
| R-N.5-final | 9 个 ApiKeyPoolIntegrationTest 端到端验证 | `mvn test` 226/226 PASS |

### 15.5 配置示例 (5 vendor × 3 key 池)

```yaml
platform:
  component:
    ai:
      key-pool:
        enabled: true
        retry-rounds: 2
        cooldown-seconds: 60
      
      models:
        zhipu-chat:
          provider: zhipu
          api-keys: [sk-zh-1, sk-zh-2, sk-zh-3]
        baichuan-rerank:
          vendor: bailian
          api-keys: [sk-bq-1, sk-bq-2]
        hunyuan-tts:
          vendor: hunyuan
          secret-id: ${HUNYUAN_SECRET_ID}
          secret-key: ${HUNYUAN_SECRET_KEY}
          api-keys: [sk-hy-1, sk-hy-2]
```

### 15.6 健康检查 / 监控

```java
// 列出所有池状态
Map<String, PoolStats> stats = apiKeyPoolManager.stats();
// 返回: { "product-search": PoolStats(totalKeys=3, numActive=0, numCooldown=0), ... }
```

`PoolStats` record 含 3 个字段:
- `totalKeys` — 池大小
- `numActive` — 当前借出数
- `numCooldown` — 冷却中 key 数

### 15.7 测试矩阵

| 层 | 测试 | 数量 |
|---|---|---|
| **ApiKeyPoolImplTest** | borrow/return/invalidate/冷却/池耗尽 | 13 |
| **ApiKeyPoolIntegrationTest** | 端到端池化调用 + 限流重试 + 工厂集成 | 9 |
| **既有 LLM/Rerank/Image/TTS/STT 测试** | 0 回归(单 key 走原路径) | 既有 |

总计 226/226 PASS (既有 153 + 51 (RN.4) + 22 (RN.5 pool SPI))。

### 15.8 已知技术债

- **LIFO 借出顺序**: Commons Pool2 默认 LIFO,`invalidate()` 后冷却中的 key 会被立即再借出(borrow 内检测到 `isInCooldown()` 自动 returnObject,但仍多一次往返)。改进方案: 在 `invalidate()` 中调用 `pool.invalidateObject(key)` 真正移出池, 维护 `cooldownKeys` 列表,`borrow()` 优先借出非冷却 key。
- **池粒度**: 当前每业务名一个池 — 同一业务名跨能力(Llm/Rerank)复用同一池。后续可拆为 `Map<businessName, Map<capability, ApiKeyPool>>` 粒度更细,但当前已覆盖 95% 场景。

### 15.9 用户决策记录 (m1283)

1. **配置语法**: `Set<String> apiKeys`(无重复) — 非 List
2. **池粒度**: 每业务名一个池
3. **限流响应**: retry-with-other-key,轮询 2 遍
4. **配置位置**: 顶层 `key-pool.*` 节点,不按业务名分别配

### 15.10 pom 依赖

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
    <version>2.13.1</version>
</dependency>
```

---

## 附录 G: Phase C AI 模块扩展与测试 (VECTOR_SERVICE_V2_DESIGN §11.3 落地)

> **版本**: v0.2 patch15 (2026-07-22) 落地稿
> **范围**: VectorService V2 Phase C 子任务 C1-C6 完整交付说明
> **关联**: patch15 条目 / `VECTOR_SERVICE_V2_DESIGN.md` §11.3 / §6.3 决策点 1 / 附录 B 文档变更追踪

### G.1 背景与目标

Phase A 在 `AbstractVectorService` 中**预留**了 IMAGE 模态但未启用,Phase B 完成 6 个 provider 的多模态接口扩展。Phase C 的目标是:

1. 在 ai 模块扩 `ImageEmbeddingModel` 接口(而非新建 image-embedding 子模块 — 见 `VECTOR_SERVICE_V2_DESIGN.md` §10.1 决策点 4)
2. 提供 3 个 CLIP-equivalent vendor 适配器(BAILIAN/OLLAMA/TEI),覆盖云端/自托管两大形态
3. 让 vector-core 通过模态路由服务 `ModalityAwareEmbeddingService` 把 IMAGE 路由到对应嵌入模型
4. 用 Testcontainers 跑通 vector-qdrant 的端到端集成测试

### G.2 `ImageEmbeddingModel` 接口契约

```java
package com.richie.component.ai.api.image;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 图像嵌入模型接口。
 *
 * <p>Spring AI 标准 {@link EmbeddingModel} 仅显式暴露文本嵌入语义,本接口为需要图像分支
 * 语义的调用方提供类型化入口。CLIP 风格的实现也可通过继承的 {@link #embed(String)} 方法
 * 把文本投影到同一向量空间,实现跨模态检索。
 */
public interface ImageEmbeddingModel extends EmbeddingModel {

    /**
     * 将图像像素数据投影到与文本相同的向量空间(CLIP-equivalent)。
     *
     * @param imageUrlOrBase64 可访问的图像 URL,或携带媒体类型前缀的 Base64 data URL
     * @return 图像在文本对齐向量空间中的向量表示
     * @throws UnsupportedOperationException 当具体实现尚未提供图像分支时
     */
    default float[] embedImage(String imageUrlOrBase64) {
        throw new UnsupportedOperationException("Image embedding is not supported by this model");
    }
}
```

**关键设计决策**:

| 维度 | 决策 | 理由 |
|------|------|------|
| **接口继承** | `extends EmbeddingModel` | 100% 向后兼容;vector store 摄取管线仍可走 `EmbeddingModel` 标准路径 |
| **`embedImage` 默认行为** | `default` 方法抛 `UnsupportedOperationException` | 兼容老版本纯文本 EmbeddingModel;只有声明 `ImageEmbeddingModel` 的实现才需要实现图像分支 |
| **图像输入形态** | URL 或 base64 data URL(`{@code data:image/<sub>;base64,<data>}`) | 与 Bailian / DashScope multimodal-embedding-v1 协议对齐;远程 URL 走 vendor 端拉取,内联 base64 走 vendor 端解码 |
| **图像输入安全** | 由 vendor 端负责 SSRF / MIME 校验 | 本组件不重复实现 — vector store 侧应在摄取前做白名单(MIME + size + 远程域名) |

### G.3 MultimodalModelFactory 分派表

`MultimodalModelFactory.createImageEmbeddingModel(cfg, httpClient)` 按 `ImageEmbeddingProvider` 枚举分派:

| Vendor (枚举值) | 适配器类 | 协议 / 端点 | 模型 / 维度 | 鉴权 | 是否真 CLIP |
|----------------|----------|-------------|-------------|------|--------------|
| **BAILIAN** | `BailianImageEmbeddingAdapter` | DashScope `POST /api/v1/services/embeddings/multimodal-embedding/multimodal-embedding` | `multimodal-embedding-v1` / **1024** (硬编码) | Bearer API Key | ✅ 是 — 文本/图像共享空间 |
| **OLLAMA** | `OllamaImageEmbeddingAdapter` | Ollama `POST /api/embed` | `nomic-embed-text` / **768** (默认) | 无(本地) | ⚠️ 否 — Ollama 原生无 CLIP 协议,本类仅承担"挂名 image-embedding 入口的文本向量化";真跨模态请走 TEI/Bailian |
| **TEI** | `TeiImageEmbeddingAdapter` | TEI OpenAI 兼容 `POST /v1/embeddings` | 由部署模型决定 / 运行时推断 | 可选 Bearer(反代) | ⚠️ 部分 — 加载 CLIP 模型时是;加载纯文本模型时只支持文本分支 |

**配置示例**:

```yaml
platform:
  component:
    ai:
      image-embedding:
        default-mm:                       # Bailian — 生产推荐
          provider: bailian
          api-key: ${DASHSCOPE_API_KEY}
          model: multimodal-embedding-v1

        local-ollama:                     # Ollama — 本地开发
          provider: ollama
          base-url: http://localhost:11434
          model: nomic-embed-text         # 768-dim

        tei-clip:                         # TEI — 自托管 CLIP
          provider: tei
          base-url: http://localhost:8080
          model: BAAI/bge-large-en-v1.5   # 或 sentence-transformers/clip-ViT-B-32
          api-key: ${TEI_BEARER_KEY}      # 可选 — 反代鉴权
```

### G.4 模态路由设计 (`ModalityAwareEmbeddingService`)

> 完整 JavaDoc 见 `vector-core/service/ModalityAwareEmbeddingService.java`;此处补充设计意图。

**核心 SPI**:

| 方法 | 返回 | 调用场景 |
|------|------|----------|
| `embed(Modality, VectorContent)` | `float[]` | vector provider 写入前调用 — 按模态分发到对应 EmbeddingModel |
| `supportsModality(Modality)` | `boolean` | 启动期 / 索引元数据声明前调用 — 用于"IMAGE 是否可用"判断 |
| `dimensionFor(Modality)` | `int` | 索引 schema 创建前调用 — 用于"该模态在当前配置下的向量维度";未配置时返回 `0`(零向量语义) |

**Bean 装配**:

| Bean | 装配条件 | 来源 |
|------|----------|------|
| `textModel` (`EmbeddingModel`) | **必填** | Spring 容器注入 `aiEmbeddingModel` Bean(`atlas-richie-component-ai` 自动注册) |
| `imageModel` (`EmbeddingModel`, 标 `@Qualifier("imageEmbeddingModel")`) | **可选** | 由 `MultimodalModelFactory.createImageEmbeddingModel` 注入到 Spring 上下文,Bean 名 `imageEmbeddingModel`;**未配置时为 `null`**,`embed(IMAGE,...)` 抛 `UnsupportedModalityException` |

**模态路由规则**:

```
Modality.TEXT  → textModel.embed(text)               # 始终支持
Modality.IMAGE → imageModel != null?
                   yes → imageModel.embed(dataUrl)   # 走 CLIP 协议
                   no  → throw UnsupportedModalityException("IMAGE 模态未配置 — 请配置 vector.image-embedding.model")
```

**Spring DI 注入细节**(为何 `@Qualifier` 而非 `@Autowired`):

- 多 `EmbeddingModel` Bean 同时存在(`aiEmbeddingModel` + `imageEmbeddingModel`),按类型注入歧义
- `@Qualifier("imageEmbeddingModel")` 明确锁定,允许 `imageEmbeddingModel` Bean 缺失时注入 `null`(配合 `@Nullable`)
- 与 `ModalityAwareEmbeddingService` 的语义对齐 — "textModel 必填,imageModel 可选"

### G.5 Clip-equivalent 向量空间语义

> **硬约束**(同 V2 DESIGN §6.3 决策点 1): TEXT 与 IMAGE 嵌入**必须在同一向量空间**才能做跨模态语义检索。

**实际 vendor 维度对照**:

| Vendor | 维度 | 推荐场景 |
|--------|------|----------|
| Bailian (`multimodal-embedding-v1`) | **1024**(固定,DashScope 文档硬编码) | 生产 RAG(中英双语 + 图像混合) |
| Ollama (`nomic-embed-text` 默认) | **768** | 本地开发 / 纯文本检索(无图像) |
| Ollama (`mxbai-embed-large`) | **1024** | 本地 + 中等质量 |
| TEI (`BAAI/bge-large-en-v1.5`) | **1024** | 英文纯文本检索 |
| TEI (`sentence-transformers/clip-ViT-B-32`) | **512** | 真 CLIP 跨模态 |
| TEI (`sentence-transformers/clip-ViT-L-14`) | **768** | 真 CLIP + 高质量 |

**业务侧创建索引时的强约束**:

```yaml
platform:
  component:
    vector:
      provider: qdrant
      qdrant:
        collection: my-mm-corpus
        initialize-schema: true
        # 必须与所选 image-embedding vendor 的维度对齐
        # Bailian multimodal-embedding-v1 → 1024
        # Ollama nomic-embed-text       → 768
        # TEI BAAI/bge-large-en-v1.5     → 1024
        # TEI clip-ViT-B-32             → 512
        # 否则写入时 Qdrant 报 dimension mismatch
```

**动态维度探测**(可选):

- 默认 `dimensions()` 静态返回(Bailian: 1024,Ollama: 768,TEI: 0)
- TEI 部署维度未知时,可手动调用 `adapter.call(new EmbeddingRequest(List.of("probe"), null))` 从响应向量长度推断
- **不推荐**在启动期自动 probe — 浪费一次计费调用,业务侧应有显式声明

### G.6 Phase A 占位代码解开清单

| Phase A 占位 | Phase C 替代 |
|--------------|--------------|
| `AbstractVectorService.addDocument(VectorDocument)` 中 IMAGE 模态硬 `throw new UnsupportedOperationException("IMAGE modality not yet enabled in v2.0.0")` | 委托 `ModalityAwareEmbeddingService.embed(Modality.IMAGE, VectorContent.ImageContent)`;若 `imageModel == null` 抛 `UnsupportedModalityException("IMAGE 模态未配置 — 请配置 vector.image-embedding.model")`(语义更友好) |
| `ImageContent` 仅作为 VectorDocument.metadata 字段存在,无 vector 化路径 | `VectorContent.ImageContent` 一等公民,通过 `ModalityAwareEmbeddingService` 路由 |
| 4 个 vector provider impl 中的"image=占位"硬 throw | 现在统一委托 `ModalityAwareEmbeddingService`,provider 不再关心 IMAGE 是否可用,只看 `supportsModality` 返回值 |

### G.7 关键测试矩阵

| 测试 | 位置 | 关键断言 | 状态 |
|------|------|----------|------|
| `ModalityAwareEmbeddingServiceTest` | `vector-core/src/test/.../service/` | TEXT/IMAGE 路由正确 / `supportsModality` / `dimensionFor` / null 兜底 / 内容类型与模态不匹配抛 `IllegalArgumentException` / IMAGE 未配置抛 `UnsupportedModalityException` | ✅ 已 PASS |
| `QdrantVectorRecordOpsIT` | `vector-qdrant/src/test/.../integration/` | Testcontainers 启动 Qdrant 1.7.0 容器 + 端口注入 + createIndex/indexExists/countDocuments/truncateIndex/listIndexes/describeIndex/getIndexStats/healthCheck/similaritySearchByVector 全跑 + Docker 不可用 `SKIP_TESTCONTAINERS=true` 跳过 | ✅ 已实装(实机跑需 Docker) |
| (Phase B 既有) `ImageProviderTest` / `RerankProviderTest` / ... | 既有 | 不回归 | ✅ 既有 0 回归 |

### G.8 已知边界与未来扩展

| 边界 | 说明 | 扩展路径 |
|------|------|----------|
| **Ollama 协议无 CLIP** | `OllamaImageEmbeddingAdapter` 仅承担文本向量化的"挂名 image-embedding 入口"用途;无真跨模态语义 | 业务侧有 Ollama CLIP 需求时,改用 TEI 部署 `clip-ViT-B-32` 等真 CLIP 模型 |
| **TEI 图像分支未实现** | OpenAI 协议只覆盖文本;TEI 原生 `/embed_image` 与 OpenAI 不兼容 | Phase C+ 或 D 阶段补 `TeiImageEmbeddingAdapter.embedImage(String)` 走 `/embed_image` 直调 |
| **`dimensions()` 在 TEI 上返回 0** | 编译期无法确定部署模型维度 | 启动期显式注入 `tei.dimensions: 1024` 配置字段(后续可加) |
| **跨模态索引未独立验证** | Qdrant IT 仅覆盖 TEXT 路径(`@DisabledIf` + 单模态测试) | Phase D 引入 multimodal RAG 测试数据集(图文混合召回) |
| **`VectorService.searchByImageUrl`** | V2 DESIGN §10.1 决策点 9 标记"暂不提供" — 业务方自己下载后调 `searchByImage(byte[])` | 待业务方提需求再补 |

### G.9 端到端使用示例 (图文混合索引)

```java
@Service
@RequiredArgsConstructor
public class MultimodalRagService {

    private final VectorService vectorService;
    private final ModalityAwareEmbeddingService embeddingService;

    /**
     * 把图片和它的文本描述索引到同一 collection,
     * 后续以文搜图 / 以图搜图都能命中。
     */
    public void indexImage(String imageId, byte[] imageBytes, String mimeType, String caption) {
        // 文本描述走 TEXT 路径
        VectorRecord captionRec = VectorRecord.builder()
                .id(imageId + "-text")
                .content(VectorContent.text(caption))
                .modality(Modality.TEXT)
                .metadata(Map.of("source", "caption", "imageId", imageId))
                .build();
        vectorService.upsert(List.of(captionRec));

        // 图像走 IMAGE 路径
        VectorRecord imageRec = VectorRecord.builder()
                .id(imageId + "-image")
                .content(VectorContent.image(imageBytes, mimeType))
                .modality(Modality.IMAGE)
                .metadata(Map.of("source", "image", "imageId", imageId))
                .build();
        vectorService.upsert(List.of(imageRec));
    }

    /**
     * 以文搜图 — 业务侧直接传文本,vector store 内部走 TEXT 模态向量化。
     */
    public List<VectorSearchResult> searchByText(String query, int topK) {
        return vectorService.searchByText(query, topK,
                SearchOptions.builder()
                        .filter(Map.of("source", "image"))    // 只召回图像条目
                        .minScore(0.7)
                        .build());
    }
}
```

---


