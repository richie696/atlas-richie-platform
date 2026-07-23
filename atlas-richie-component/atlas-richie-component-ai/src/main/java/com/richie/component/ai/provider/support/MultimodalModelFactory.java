/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.provider.support;

import com.richie.component.ai.api.image.ImageEmbeddingModel;

import com.richie.component.ai.support.keypool.ApiKey;

import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageEmbeddingModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageEmbeddingProvider;
import com.richie.component.ai.config.multimodal.image.ImageModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageProvider;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;
import com.richie.component.ai.config.multimodal.rerank.RerankProvider;
import com.richie.component.ai.config.multimodal.stt.SttModelConfig;
import com.richie.component.ai.config.multimodal.stt.SttProvider;
import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;
import com.richie.component.ai.config.multimodal.tts.TtsProvider;
import com.richie.component.ai.provider.bailian.BailianImageAdapter;
import com.richie.component.ai.provider.bailian.BailianImageEmbeddingAdapter;
import com.richie.component.ai.provider.bailian.BailianRerankModel;
import com.richie.component.ai.provider.doubao.DoubaoTextToSpeechModel;
import com.richie.component.ai.provider.doubao.DoubaoTranscriptionModel;
import com.richie.component.ai.provider.doubao.DoubaoVikingRerankModel;
import com.richie.component.ai.provider.hunyuan.HunyuanTextToSpeechModel;
import com.richie.component.ai.provider.hunyuan.HunyuanTranscriptionModel;
import com.richie.component.ai.provider.pangu.PanguRerankModel;
import com.richie.component.ai.provider.pangu.PanguTextToSpeechModel;
import com.richie.component.ai.provider.pangu.PanguTranscriptionModel;
import com.richie.component.ai.provider.tei.TeiImageEmbeddingAdapter;
import com.richie.component.ai.provider.ollama.OllamaImageEmbeddingAdapter;
import com.richie.component.ai.provider.zhipu.ZhipuRerankModel;
import com.richie.component.ai.provider.zhipu.ZhipuTextToSpeechModel;
import com.richie.component.ai.provider.zhipu.ZhipuTranscriptionModel;
import com.richie.component.ai.support.keypool.ApiKeyPool;
import com.richie.component.ai.support.keypool.ApiKeyPoolManager;
import com.richie.component.ai.support.keypool.ApiKeyUtils;
import com.richie.component.ai.support.keypool.ApiKeyValidator;
import com.richie.component.ai.support.keypool.DefaultApiKeyValidator;
import com.richie.component.ai.support.keypool.MultimodalConfigCloner;
import com.richie.component.ai.support.keypool.PooledExecutor;
import com.richie.component.http.core.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * R-N 多模态工厂。
 *
 * <p>以各能力专属的 vendor 枚举为分派依据(类型安全)。当前支持 Rerank 的 Bailian/Zhipu/Pangu/Doubao、
 * Image 的 Bailian，以及 Image Embedding 的 Bailian/TEI/Ollama；TTS/STT 支持
 * Hunyuan/Zhipu/Doubao/Pangu。
 *
 * <h2>能力 × Vendor 分派总表</h2>
 * <table border="1" style="border-spacing:0; padding:4px">
 *   <caption>R-N 多模态工厂分派矩阵</caption>
 *   <tr><th>能力</th><th>Vendor</th><th>枚举</th><th>适配器</th><th>备注</th></tr>
 *   <tr>
 *     <td rowspan="4">Rerank</td>
 *     <td>阿里百炼 (DashScope)</td><td>{@code BAILIAN}</td>
 *     <td>{@link com.richie.component.ai.provider.bailian.BailianRerankModel BailianRerankModel}</td>
 *     <td>gte-rerank</td>
 *   </tr>
 *   <tr><td>智谱</td><td>{@code ZHIPU}</td><td>{@code ZhipuRerankModel}</td><td>/paas/v4/rerank</td></tr>
 *   <tr><td>华为盘古</td><td>{@code PANGU}</td><td>{@code PanguRerankModel}</td><td>/pangu/search/v1/rerank</td></tr>
 *   <tr><td>火山 VikingDB</td><td>{@code DOUBAO}</td><td>{@code DoubaoVikingRerankModel}</td><td>AK/SK HMAC 鉴权</td></tr>
 *   <tr>
 *     <td>Image (文生图)</td>
 *     <td>阿里百炼</td><td>{@code BAILIAN}</td>
 *     <td>{@link com.richie.component.ai.provider.bailian.BailianImageAdapter BailianImageAdapter}</td>
 *     <td>wanx 系列</td>
 *   </tr>
 *   <tr>
 *     <td rowspan="3">Image Embedding<br/>(CLIP-equivalent)</td>
 *     <td>阿里百炼</td><td>{@code BAILIAN}</td>
 *     <td>{@link com.richie.component.ai.provider.bailian.BailianImageEmbeddingAdapter BailianImageEmbeddingAdapter}</td>
 *     <td>multimodal-embedding-v1,1024-dim,<b>真 CLIP</b>(文本/图像共享空间)</td>
 *   </tr>
 *   <tr><td>HuggingFace TEI</td><td>{@code TEI}</td><td>{@code TeiImageEmbeddingAdapter}</td><td>OpenAI 兼容 /v1/embeddings;维度由部署模型决定(<b>当前仅文本分支</b>);可选 Bearer 反代鉴权</td></tr>
 *   <tr><td>Ollama</td><td>{@code OLLAMA}</td><td>{@code OllamaImageEmbeddingAdapter}</td><td>/api/embed 文本端点;<b>非真 CLIP</b> — Ollama 原生无图像 embedding 协议,本类仅承担挂名 image-embedding 入口的文本向量化</td></tr>
 *   <tr>
 *     <td rowspan="4">TTS</td>
 *     <td>腾讯混元</td><td>{@code HUNYUAN}</td><td>{@code HunyuanTextToSpeechModel}</td><td>product 1073, TC3 签名</td></tr>
 *   <tr><td>智谱</td><td>{@code ZHIPU}</td><td>{@code ZhipuTextToSpeechModel}</td><td>glm-tts</td></tr>
 *   <tr><td>火山 openspeech</td><td>{@code DOUBAO}</td><td>{@code DoubaoTextToSpeechModel}</td><td>X-Api-Key 鉴权</td></tr>
 *   <tr><td>华为盘古</td><td>{@code PANGU}</td><td>{@code PanguTextToSpeechModel}</td><td>/pangu 多模态页</td></tr>
 *   <tr>
 *     <td rowspan="4">STT</td>
 *     <td>腾讯混元</td><td>{@code HUNYUAN}</td><td>{@code HunyuanTranscriptionModel}</td><td>product 1093, TC3 签名</td></tr>
 *   <tr><td>智谱</td><td>{@code ZHIPU}</td><td>{@code ZhipuTranscriptionModel}</td><td>glm-asr-2512</td></tr>
 *   <tr><td>火山 openspeech</td><td>{@code DOUBAO}</td><td>{@code DoubaoTranscriptionModel}</td><td>X-Api-Key 鉴权</td></tr>
 *   <tr><td>华为盘古</td><td>{@code PANGU}</td><td>{@code PanguTranscriptionModel}</td><td>/pangu 多模态页</td></tr>
 * </table>
 *
 * <h2>Image Embedding Vendor 选型指南</h2>
 * <ul>
 *   <li><b>BAILIAN</b>(生产推荐):DashScope multimodal-embedding-v1,1024-dim 硬编码,
 *       真 CLIP 语义,文本+图像共享空间,直接支持 {@link com.richie.component.ai.api.image.ImageEmbeddingModel#embedImage(String) embedImage}。
 *       业务侧无需关心维度对齐 — 已与 R-N-DESIGN §G.5 vector 维度约定一致。</li>
 *   <li><b>TEI</b>(自托管推荐):HuggingFace Text Embeddings Inference,
 *       支持部署 CLIP 类多模态模型(注意:加载什么模型决定支持什么能力)。
 *       OpenAI 兼容 /v1/embeddings 路径,反代鉴权可选。
 *       <b>当前仅文本分支</b>(image 端点 /embed_image 协议与 OpenAI 不兼容,待后续 sprint);
 *       维度运行时推断,dimensions() 编译期返回 0。</li>
 *   <li><b>OLLAMA</b>(本地开发):Ollama 原生 /api/embed,<b>非真 CLIP</b>。
 *       默认模型 nomic-embed-text 768-dim,仅承担文本向量化。
 *       命名为 "image embedding" 仅因为挂在 ImageEmbeddingProvider 枚举下,
 *       跨模态检索场景请改用 TEI/Bailian。</li>
 * </ul>
 *
 * <h2>API Key 池化 (R-N.5)</h2>
 * <ul>
 *   <li>单 key → 走原有 {@code create*Model(cfg, httpClient)} 路径,无池</li>
 *   <li>多 key → 走 {@code create*ModelPooled(businessName, cfg, httpClient, poolManager, ...)} 路径,
 *       每个 key 预建一个 provider 实例,用 {@link PooledExecutor} 装饰</li>
 *   <li>限流(429/403/rate limit/quota)→ 自动换下一个 key 重试,2 轮(R-N.5 决策)</li>
 * </ul>
 *
 * <h2>Phase C 关联</h2>
 * VECTOR_SERVICE_V2_DESIGN §11.3 C1-C3 阶段本工厂扩展:
 * <ul>
 *   <li>C1: 新增 {@link com.richie.component.ai.api.image.ImageEmbeddingModel} 接口</li>
 *   <li>C2: 新增 OLLAMA 分支(OllamaImageEmbeddingAdapter)</li>
 *   <li>C3: 新增 TEI 分支(TeiImageEmbeddingAdapter)</li>
 *   <li>C4: 与 vector-core ModalityAwareEmbeddingService 配合模态路由</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public final class MultimodalModelFactory {

    /** 池关闭/不可用时的默认重试轮数 — 与 KeyPoolProperties.retry-rounds 对齐。 */
    private static final int DEFAULT_RETRY_ROUNDS = 2;

    private MultimodalModelFactory() {
    }

    // ====================== Rerank ======================

    public static RerankModel createRerankModel(RerankModelConfig cfg, HttpClient httpClient) {
        RerankProvider vendor = cfg.getProvider();
        if (vendor == null) {
            throw vendorUnknown("rerank", null);
        }
        return switch (vendor) {
            case BAILIAN -> createBailianRerankModel(cfg, httpClient);
            case ZHIPU -> createZhipuRerankModel(cfg, httpClient);
            case PANGU -> createPanguRerankModel(cfg, httpClient);
            case DOUBAO -> createDoubaoVikingRerankModel(cfg, httpClient);
        };
    }

    /**
     * 池化 Rerank — 多 key 时为每个 key 预建一个 RerankModel,用 {@link PooledExecutor} 装饰。
     */
    public static RerankModel createRerankModelPooled(String businessName,
                                                     RerankModelConfig cfg,
                                                     HttpClient httpClient,
                                                     ApiKeyPoolManager poolManager) {
        Set<String> keys = ApiKeyUtils.resolveKeys(cfg);
        if (keys.size() <= 1) {
            return createRerankModel(cfg, httpClient);
        }
        ApiKeyPool pool = poolManager.getPool(businessName, keys);
        List<RerankModel> perKey = new ArrayList<>(keys.size());
        for (String k : keys) {
            perKey.add(createRerankModel(MultimodalConfigCloner.cloneRerankWithKey(cfg, k), httpClient));
        }
        log.info("Rerank[{}] 已启用 KeyPool: totalKeys={}", businessName, keys.size());
        return new PooledRerankModel(businessName, perKey, pool, new DefaultApiKeyValidator(), DEFAULT_RETRY_ROUNDS);
    }

    private static BailianRerankModel createBailianRerankModel(RerankModelConfig cfg, HttpClient httpClient) {
        return new BailianRerankModel(httpClient, cfg.getApiKey(), cfg.getBaseUrl());
    }

    private static ZhipuRerankModel createZhipuRerankModel(RerankModelConfig cfg, HttpClient httpClient) {
        return new ZhipuRerankModel(httpClient, cfg);
    }

    private static PanguRerankModel createPanguRerankModel(RerankModelConfig cfg, HttpClient httpClient) {
        return new PanguRerankModel(httpClient, cfg);
    }

    private static DoubaoVikingRerankModel createDoubaoVikingRerankModel(
            RerankModelConfig cfg, HttpClient httpClient) {
        return new DoubaoVikingRerankModel(cfg);
    }

    // ====================== Image ======================

    public static ImageModel createImageModel(ImageModelConfig cfg, HttpClient httpClient) {
        ImageProvider vendor = cfg.getProvider();
        if (vendor == null) {
            throw vendorUnknown("image", null);
        }
        return switch (vendor) {
            case BAILIAN -> new BailianImageAdapter(httpClient, cfg.getApiKey(), cfg.getBaseUrl(), cfg.getModel());
        };
    }

    public static ImageModel createImageModelPooled(String businessName,
                                                    ImageModelConfig cfg,
                                                    HttpClient httpClient,
                                                    ApiKeyPoolManager poolManager) {
        Set<String> keys = ApiKeyUtils.resolveKeys(cfg);
        if (keys.size() <= 1) {
            return createImageModel(cfg, httpClient);
        }
        ApiKeyPool pool = poolManager.getPool(businessName, keys);
        List<ImageModel> perKey = new ArrayList<>(keys.size());
        for (String k : keys) {
            perKey.add(createImageModel(MultimodalConfigCloner.cloneImageWithKey(cfg, k), httpClient));
        }
        log.info("Image[{}] 已启用 KeyPool: totalKeys={}", businessName, keys.size());
        return new PooledImageModel(businessName, perKey, pool, new DefaultApiKeyValidator(), DEFAULT_RETRY_ROUNDS);
    }

    // ====================== Image Embedding (CLIP-equivalent) ======================

    public static ImageEmbeddingModel createImageEmbeddingModel(ImageEmbeddingModelConfig cfg, HttpClient httpClient) {
        ImageEmbeddingProvider vendor = cfg.getProvider();
        if (vendor == null) {
            throw vendorUnknown("image-embedding", null);
        }
        return switch (vendor) {
            case BAILIAN -> new BailianImageEmbeddingAdapter(httpClient, cfg.getApiKey(), cfg.getBaseUrl(), cfg.getModel());
            case TEI -> new TeiImageEmbeddingAdapter(httpClient, cfg.getBaseUrl(), cfg.getModel(), cfg.getApiKey());
            case OLLAMA -> createOllamaImageEmbeddingModel(cfg, httpClient, null);
        };
    }

    /**
     * 池化图像嵌入模型。多 key 路径仍返回 Spring AI {@link EmbeddingModel} 池包装器，
     * 因为池包装器暂未实现逐调用图像输入语义；单 key 路径返回类型化的 {@link ImageEmbeddingModel}。
     * <p>
     * Ollama 默认不需要鉴权。若显式配置 {@code api-keys}，每个 key 被视为反向代理使用的
     * 不透明 Bearer token，并在对应的 Ollama HTTP 客户端上注入 {@code Authorization} 头；
     * 不会把 key 放进 Ollama 原生请求体。
     */
    public static ImageEmbeddingModel createImageEmbeddingModelPooled(String businessName,
                                                                       ImageEmbeddingModelConfig cfg,
                                                                       HttpClient httpClient,
                                                                       ApiKeyPoolManager poolManager) {
        if (cfg.getProvider() == ImageEmbeddingProvider.OLLAMA
                && cfg.getApiKeys() != null && !cfg.getApiKeys().isEmpty()) {
            Set<String> ollamaKeys = cfg.getApiKeys();
            if (ollamaKeys.size() <= 1) {
                return createOllamaImageEmbeddingModel(
                        cfg, httpClient, ollamaKeys.iterator().next());
            }
            ApiKeyPool pool = poolManager.getPool(businessName, ollamaKeys);
            List<ImageEmbeddingModel> perKey = new ArrayList<>(ollamaKeys.size());
            for (String key : ollamaKeys) {
                perKey.add(createOllamaImageEmbeddingModel(cfg, httpClient, key));
            }
            log.info("ImageEmbedding[{}] Ollama 已启用 KeyPool: totalKeys={}",
                    businessName, ollamaKeys.size());
            return new PooledEmbeddingModel(
                    businessName, perKey, pool, new DefaultApiKeyValidator(), DEFAULT_RETRY_ROUNDS);
        }

        Set<String> keys = ApiKeyUtils.resolveKeys(cfg);
        if (keys.size() <= 1) {
            return createImageEmbeddingModel(cfg, httpClient);
        }
        ApiKeyPool pool = poolManager.getPool(businessName, keys);
        List<ImageEmbeddingModel> perKey = new ArrayList<>(keys.size());
        for (String k : keys) {
            perKey.add(createImageEmbeddingModel(
                    MultimodalConfigCloner.cloneImageEmbeddingWithKey(cfg, k), httpClient));
        }
        log.info("ImageEmbedding[{}] 已启用 KeyPool: totalKeys={}", businessName, keys.size());
        return new PooledEmbeddingModel(
                businessName, perKey, pool, new DefaultApiKeyValidator(), DEFAULT_RETRY_ROUNDS);
    }

    /**
     * 创建仍保持 Spring AI 文本契约的 Ollama 模型，并按需装饰其 HTTP 客户端。
     */
    private static ImageEmbeddingModel createOllamaImageEmbeddingModel(
            ImageEmbeddingModelConfig cfg, HttpClient httpClient, String apiKey) {
        HttpClient authorizedClient = OllamaImageEmbeddingAdapter
                .withBearerAuthorization(httpClient, apiKey);
        return new OllamaImageEmbeddingModel(authorizedClient, cfg.getBaseUrl(), cfg.getModel());
    }

    // ====================== TTS ======================

    public static TextToSpeechModel createTextToSpeechModel(TtsModelConfig cfg, HttpClient httpClient) {
        TtsProvider vendor = cfg.getProvider();
        if (vendor == null) {
            throw vendorUnknown("tts", null);
        }
        return switch (vendor) {
            case HUNYUAN -> new HunyuanTextToSpeechModel(cfg, httpClient);
            case ZHIPU -> new ZhipuTextToSpeechModel(cfg);
            case DOUBAO -> new DoubaoTextToSpeechModel(cfg, httpClient);
            case PANGU -> new PanguTextToSpeechModel(cfg, httpClient);
        };
    }

    public static TextToSpeechModel createTextToSpeechModelPooled(String businessName,
                                                                  TtsModelConfig cfg,
                                                                  HttpClient httpClient,
                                                                  ApiKeyPoolManager poolManager) {
        Set<String> keys = ApiKeyUtils.resolveKeys(cfg);
        if (keys.size() <= 1) {
            return createTextToSpeechModel(cfg, httpClient);
        }
        ApiKeyPool pool = poolManager.getPool(businessName, keys);
        List<TextToSpeechModel> perKey = new ArrayList<>(keys.size());
        for (String k : keys) {
            perKey.add(createTextToSpeechModel(
                    (TtsModelConfig) MultimodalConfigCloner.cloneAudioWithKey(cfg, k), httpClient));
        }
        log.info("TTS[{}] 已启用 KeyPool: totalKeys={}", businessName, keys.size());
        return new PooledTextToSpeechModel(businessName, perKey, pool, new DefaultApiKeyValidator(), DEFAULT_RETRY_ROUNDS);
    }

    // ====================== STT ======================

    public static TranscriptionModel createTranscriptionModel(SttModelConfig cfg, HttpClient httpClient) {
        SttProvider vendor = cfg.getProvider();
        if (vendor == null) {
            throw vendorUnknown("stt", null);
        }
        return switch (vendor) {
            case HUNYUAN -> new HunyuanTranscriptionModel(cfg, httpClient);
            case ZHIPU -> new ZhipuTranscriptionModel(cfg);
            case DOUBAO -> new DoubaoTranscriptionModel(cfg, httpClient);
            case PANGU -> new PanguTranscriptionModel(cfg, httpClient);
        };
    }

    public static TranscriptionModel createTranscriptionModelPooled(String businessName,
                                                                    SttModelConfig cfg,
                                                                    HttpClient httpClient,
                                                                    ApiKeyPoolManager poolManager) {
        Set<String> keys = ApiKeyUtils.resolveKeys(cfg);
        if (keys.size() <= 1) {
            return createTranscriptionModel(cfg, httpClient);
        }
        ApiKeyPool pool = poolManager.getPool(businessName, keys);
        List<TranscriptionModel> perKey = new ArrayList<>(keys.size());
        for (String k : keys) {
            perKey.add(createTranscriptionModel(
                    (SttModelConfig) MultimodalConfigCloner.cloneAudioWithKey(cfg, k), httpClient));
        }
        log.info("STT[{}] 已启用 KeyPool: totalKeys={}", businessName, keys.size());
        return new PooledTranscriptionModel(businessName, perKey, pool, new DefaultApiKeyValidator(), DEFAULT_RETRY_ROUNDS);
    }

    // ====================== helpers ======================

    private static IllegalArgumentException vendorUnknown(String capability, Object vendor) {
        return new IllegalArgumentException(
                "未识别的 R-N 多模态 vendor [" + capability + "]: '" + vendor
                        + "'（" + capability + " 已实现: " + implementedVendors(capability) + "）");
    }

    private static String implementedVendors(String capability) {
        return switch (capability) {
            case "rerank" -> "bailian / zhipu / pangu / doubao";
            case "image" -> "bailian";
            case "image-embedding" -> "bailian / tei / ollama";
            case "tts", "stt" -> "hunyuan / zhipu / doubao / pangu";
            default -> "";
        };
    }

    // ====================== Pooled 装饰器 (实现 Spring AI Model 接口) ======================

    /**
     * 池化 RerankModel — 通过 RerankModel.call(query, docs) 委托给 PooledExecutor。
     */
    public static class PooledRerankModel implements RerankModel {
        private final PooledExecutor<RerankModel> executor;

        public PooledRerankModel(String businessName, List<RerankModel> perKey,
                                  ApiKeyPool pool, ApiKeyValidator validator, int retryRounds) {
            this.executor = new PooledExecutor<>(businessName, perKey, pool, validator, retryRounds);
        }

        @Override
        public RerankResponse rerank(RerankRequest request) {
            return executor.execute("rerank", m -> m.rerank(request));
        }

        @Override
        public CompletableFuture<RerankResponse> rerankAsync(RerankRequest request) {
            return CompletableFuture.completedFuture(rerank(request));
        }
    }

    public static class PooledImageModel implements ImageModel {
        private final PooledExecutor<ImageModel> executor;

        public PooledImageModel(String businessName, List<ImageModel> perKey,
                                ApiKeyPool pool, ApiKeyValidator validator, int retryRounds) {
            this.executor = new PooledExecutor<>(businessName, perKey, pool, validator, retryRounds);
        }

        @Override
        public ImageResponse call(ImagePrompt request) {
            return executor.execute("image", m -> m.call(request));
        }
    }

    /**
     * C1 类型化接口的桥接子类。Ollama 适配器本身刻意只依赖 Spring AI {@link EmbeddingModel}；
     * 工厂在 C1 接口存在时用此子类补上图像模型类型，而图像方法仍保持默认的不支持语义。
     */
    private static final class OllamaImageEmbeddingModel
            extends OllamaImageEmbeddingAdapter implements ImageEmbeddingModel {

        private OllamaImageEmbeddingModel(HttpClient httpClient, String baseUrl, String model) {
            super(httpClient, baseUrl, model);
        }
    }

    /**
     * 池化 EmbeddingModel — 通过 PooledExecutor 把所有 EmbeddingModel.call/embed 委托到当前借出的 key。
     * Spring AI EmbeddingModel 同时是 {@code Model<EmbeddingRequest, EmbeddingResponse>},而 {@code call(ModelRequest)}
     * 默认实现来自父接口的默认实现,把 ModelRequest 强制转换为 EmbeddingRequest 后再次走 call(EmbeddingRequest) 入口 —
     * 因此只需覆写 {@code call(EmbeddingRequest)} 和其他语义方法。
     */
    public static class PooledEmbeddingModel implements ImageEmbeddingModel {
        private final PooledExecutor<ImageEmbeddingModel> executor;

        public PooledEmbeddingModel(String businessName, List<ImageEmbeddingModel> perKey,
                                    ApiKeyPool pool, ApiKeyValidator validator, int retryRounds) {
            this.executor = new PooledExecutor<>(businessName, perKey, pool, validator, retryRounds);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return executor.execute("image-embedding", m -> m.call(request));
        }

        @Override
        public float[] embedImage(String imageUrlOrBase64) {
            return executor.execute("image-embedding", m -> m.embedImage(imageUrlOrBase64));
        }
        @Override
        public float[] embed(String text) {
            return executor.execute("image-embedding", m -> m.embed(text));
        }

        @Override
        public float[] embed(Document document) {
            return executor.execute("image-embedding", m -> m.embed(document));
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return executor.execute("image-embedding", m -> m.embed(texts));
        }

        @Override
        public List<float[]> embed(List<Document> documents, EmbeddingOptions options, BatchingStrategy batchingStrategy) {
            return executor.execute("image-embedding", m -> m.embed(documents, options, batchingStrategy));
        }

        @Override
        public EmbeddingResponse embedForResponse(List<String> texts) {
            return executor.execute("image-embedding", m -> m.embedForResponse(texts));
        }

        @Override
        public int dimensions() {
            return executor.execute("image-embedding", m -> m.dimensions());
        }
    }

    public static class PooledTextToSpeechModel implements TextToSpeechModel {
        private final PooledExecutor<TextToSpeechModel> executor;

        public PooledTextToSpeechModel(String businessName, List<TextToSpeechModel> perKey,
                                        ApiKeyPool pool, ApiKeyValidator validator, int retryRounds) {
            this.executor = new PooledExecutor<>(businessName, perKey, pool, validator, retryRounds);
        }

        @Override
        public TextToSpeechResponse call(TextToSpeechPrompt request) {
            return executor.execute("tts", m -> m.call(request));
        }

        @Override
        public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt request) {
            // 流式模式:借 1 个 key 用到底,过程中切换复杂(订阅方已订阅),遇到限流直接抛
            ApiKey key = executor.borrow();
            try {
                TextToSpeechModel m = executor.perKeyModels().get(
                        key.getCreateIndex() >= 0 ? key.getCreateIndex() : 0);
                return m.stream(request)
                        .doOnError(e -> {
                            if (executor.validator().isKeyInvalidating(e)) {
                                executor.pool().invalidate(key);
                            } else {
                                executor.pool().returnObject(key);
                            }
                        })
                        .doOnComplete(() -> executor.pool().returnObject(key))
                        .doOnCancel(() -> executor.pool().returnObject(key));
            } catch (RuntimeException e) {
                executor.pool().returnObject(key);
                throw e;
            }
        }
    }

    public static class PooledTranscriptionModel implements TranscriptionModel {
        private final PooledExecutor<TranscriptionModel> executor;

        public PooledTranscriptionModel(String businessName, List<TranscriptionModel> perKey,
                                        ApiKeyPool pool, ApiKeyValidator validator, int retryRounds) {
            this.executor = new PooledExecutor<>(businessName, perKey, pool, validator, retryRounds);
        }

        @Override
        public AudioTranscriptionResponse call(AudioTranscriptionPrompt request) {
            return executor.execute("stt", m -> m.call(request));
        }
    }
}