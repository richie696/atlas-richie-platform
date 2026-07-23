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
package com.richie.component.ai.service.impl;

import com.richie.component.ai.config.multimodal.stt.SttModelConfig;

import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;

import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

import com.richie.component.ai.config.multimodal.image.ImageEmbeddingModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageModelConfig;

import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.image.ImageEmbeddingModel;
import com.richie.component.ai.config.AiModelProperties;



import com.richie.component.ai.provider.support.MultimodalModelFactory;
import com.richie.component.ai.service.AiMultimodalService;
import com.richie.component.http.core.HttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * R-N 多模态服务（重排 / 文生图 / 多模态向量(CLIP) / 语音合成 / 语音识别）。
 * <p>
 * 镜像 Chat 端 {@code AiChatServiceImpl} 的 Map + refresh 模式：以 {@code Map<String, T>}
 * （{@code T ∈ {RerankModel, ImageModel, ImageEmbeddingModel, TextToSpeechModel, TranscriptionModel}}）作为运行时状态，
 * 启动期 + 每次 {@code refresh()} 调用从 {@link AiModelProperties} 重新构建实例。
 * <p>
 * Chat ↔ 多模态 二维寻址模型对齐：
 * <ul>
 *   <li>Map key = 业务名（{@code platform.component.ai.rerank.<key>}）</li>
 *   <li>实现名 vendor（{@code RerankModelConfig.vendor} / {@code VoiceModelConfig.vendor}）
 *       由 {@link MultimodalModelFactory} 分派 impl</li>
 * </ul>
 * <p>
 * 与 Chat 端 {@code AiChatServiceImpl.initializeModels} 同构：
 * <ul>
 *   <li>新增 key → 加入 Map</li>
 *   <li>同 name 已存在 → cover-replace（{@code put} 即覆盖）</li>
 *   <li>{@code properties} 上已删除的 key → 不从内部 Map 删除（避免运行期抖动踩到热数据，
 *       与 Chat 端"configure-file 模型不会被 removeModel 删除"行为对齐）</li>
 * </ul>
 *
 * <h2>配置变更自动刷新</h2>
 * <p>当业务方引入 {@code spring-cloud-context}（典型通过 {@code spring-cloud-starter-config} /
 * Nacos Config / Apollo 客户端）时，{@code AiMultimodalCloudBridge} 会自动激活 —— 监听
 * {@code EnvironmentChangeEvent}，任一变更 key 以 {@code platform.component.ai.} 开头即触发
 * {@link #refresh()} 重建。镜像 {@code HotReloadCloudBridge} 的设计：不引入 spring-cloud 编译期依赖，
 * 通过 {@code SmartApplicationListener} + 类名匹配反射。
 * <p>未引入 spring-cloud-context 时，{@code refresh()} 仍可通过 {@link #refresh()} 手动调用、
 * 启动期 {@code AiModelAutoConfiguration} 自动调用一次、未来业务方自定义 ApplicationEvent 触发。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMultimodalServiceImpl implements AiMultimodalService {

    private final AiModelProperties aiModelProperties;
    private final ObjectProvider<HttpClient> httpClientProvider;

    /** Rerank 模型运行时缓存，key = 业务名。{@link LinkedHashMap} 保持插入顺序。 */
    private final Map<String, RerankModel> rerankModels = new LinkedHashMap<>();

    /** 文生图模型运行时缓存。 */
    private final Map<String, ImageModel> imageModels = new LinkedHashMap<>();

    /** 多模态向量(CLIP-equivalent)模型运行时缓存。 */
    private final Map<String, ImageEmbeddingModel> imageEmbeddings = new LinkedHashMap<>();

    /** TTS 模型运行时缓存。 */
    private final Map<String, TextToSpeechModel> ttsModels = new LinkedHashMap<>();

    /** STT / Transcription 模型运行时缓存。 */
    private final Map<String, TranscriptionModel> sttModels = new LinkedHashMap<>();

    /**
     * 重新从 {@link AiModelProperties} 加载所有多模态模型。
     * <p>
     * 行为：
     * <ol>
     *   <li>遍历 {@code properties.rerank / image / image-embedding / tts / stt} Map，按 vendor 分派到
     *       {@link MultimodalModelFactory} 的工厂方法构建实例</li>
     *   <li>内部 Map 进行 {@code put} —— 同 name 已存在则覆盖（即"新增/覆盖"语义）</li>
     *   <li>未识别的 vendor / HttpClient 缺失 → 当前 entry 跳过，日志 WARN，不阻断其他条目</li>
     *   <li>已删除的 key 不会被主动清理 —— 与 Chat 端"removeModel 显式调用才删"行为对齐</li>
     * </ol>
     * <p>
     * 线程安全：通过 {@code synchronized} 保证并发 reload 一致性，与 Chat 端
     * {@code AiChatServiceImpl.initializeModels} 风格一致。
     */
    @Override
    public synchronized void refresh() {
        // 5 个维度的完整刷新顺序与日志粒度一一对应，便于故障时按 capability 维度排障。
        int rerankCount = refreshRerank();
        int imageCount = refreshImage();
        int imageEmbeddingCount = refreshImageEmbedding();
        int ttsCount = refreshTts();
        int sttCount = refreshStt();

        if (rerankCount == 0 && imageCount == 0 && imageEmbeddingCount == 0
                && ttsCount == 0 && sttCount == 0) {
            log.info("R-N 多模态刷新完成: 未配置任何多模态模型（platform.component.ai.rerank/image/image-embedding/tts/stt）");
            return;
        }
        log.info("R-N 多模态动态初始化完成,新增/覆盖 {} 个 Rerank / {} 个 Image / {} 个 ImageEmbedding / {} 个 TTS / {} 个 STT 模型",
                rerankCount, imageCount, imageEmbeddingCount, ttsCount, sttCount);
    }

    /**
     * Rerank 维度刷新。
     *
     * @return 本轮新增/覆盖的 Rerank 模型数
     */
    private int refreshRerank() {
        Map<String, RerankModelConfig> configs = aiModelProperties.getRerank();
        if (configs == null || configs.isEmpty()) {
            return 0;
        }
        HttpClient httpClient = httpClientProvider.getIfAvailable();
        if (httpClient == null) {
            log.warn("R-N 多模态刷新跳过: 未找到 HttpClient Bean（请引入 atlas-richie-component-http-{okhttp|httpclient5|jdk|restclient} 之一）");
            return 0;
        }
        int added = 0;
        for (Map.Entry<String, RerankModelConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            RerankModelConfig cfg = entry.getValue();
            try {
                RerankModel model = MultimodalModelFactory.createRerankModel(cfg, httpClient);
                rerankModels.put(name, model);
                added++;
            } catch (Exception ex) {
                log.warn("R-N 多模态 Rerank 刷新跳过: key={}, vendor={}, error={}",
                        name, cfg.getProvider(), ex.getMessage());
            }
        }
        return added;
    }

    /**
     * Image 维度刷新。
     */
    private int refreshImage() {
        Map<String, ImageModelConfig> configs = aiModelProperties.getImage();
        if (configs == null || configs.isEmpty()) {
            return 0;
        }
        HttpClient httpClient = httpClientProvider.getIfAvailable();
        if (httpClient == null) {
            // refreshRerank 已打过 warn，此处不再重复
            return 0;
        }
        int added = 0;
        for (Map.Entry<String, ImageModelConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            ImageModelConfig cfg = entry.getValue();
            try {
                ImageModel model = MultimodalModelFactory.createImageModel(cfg, httpClient);
                imageModels.put(name, model);
                added++;
            } catch (Exception ex) {
                log.warn("R-N 多模态 Image 刷新跳过: key={}, vendor={}, error={}",
                        name, cfg.getProvider(), ex.getMessage());
            }
        }
        return added;
    }

    /**
     * Image Embedding (CLIP-equivalent) 维度刷新。
     */
    private int refreshImageEmbedding() {
        Map<String, ImageEmbeddingModelConfig> configs = aiModelProperties.getImageEmbedding();
        if (configs == null || configs.isEmpty()) {
            return 0;
        }
        HttpClient httpClient = httpClientProvider.getIfAvailable();
        if (httpClient == null) {
            // refreshRerank 已打过 warn，此处不再重复
            return 0;
        }
        int added = 0;
        for (Map.Entry<String, ImageEmbeddingModelConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            ImageEmbeddingModelConfig cfg = entry.getValue();
            try {
                ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModel(cfg, httpClient);
                imageEmbeddings.put(name, model);
                added++;
            } catch (Exception ex) {
                log.warn("R-N 多模态 ImageEmbedding 刷新跳过: key={}, vendor={}, error={}",
                        name, cfg.getProvider(), ex.getMessage());
            }
        }
        return added;
    }

    /**
     * TTS 维度刷新。
     */
    private int refreshTts() {
        Map<String, TtsModelConfig> configs = aiModelProperties.getTts();
        if (configs == null || configs.isEmpty()) {
            return 0;
        }
        HttpClient httpClient = httpClientProvider.getIfAvailable();
        if (httpClient == null) {
            return 0;
        }
        int added = 0;
        for (Map.Entry<String, TtsModelConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            TtsModelConfig cfg = entry.getValue();
            try {
                TextToSpeechModel model = MultimodalModelFactory.createTextToSpeechModel(cfg, httpClient);
                ttsModels.put(name, model);
                added++;
            } catch (Exception ex) {
                log.warn("R-N 多模态 TTS 刷新跳过: key={}, vendor={}, error={}",
                        name, cfg.getProvider(), ex.getMessage());
            }
        }
        return added;
    }

    /**
     * STT 维度刷新。
     */
    private int refreshStt() {
        Map<String, SttModelConfig> configs = aiModelProperties.getStt();
        if (configs == null || configs.isEmpty()) {
            return 0;
        }
        HttpClient httpClient = httpClientProvider.getIfAvailable();
        if (httpClient == null) {
            return 0;
        }
        int added = 0;
        for (Map.Entry<String, SttModelConfig> entry : configs.entrySet()) {
            String name = entry.getKey();
            SttModelConfig cfg = entry.getValue();
            try {
                TranscriptionModel model = MultimodalModelFactory.createTranscriptionModel(cfg, httpClient);
                sttModels.put(name, model);
                added++;
            } catch (Exception ex) {
                log.warn("R-N 多模态 STT 刷新跳过: key={}, vendor={}, error={}",
                        name, cfg.getProvider(), ex.getMessage());
            }
        }
        return added;
    }

    // ====================== 取用 API（按 Chat 模式: 返回实例或 null） ======================

    /**
     * 按业务名取用 Rerank 模型实例；未注册返回 {@code null}。
     */
    @Override
    public RerankModel getRerankModel(String name) {
        return rerankModels.get(name);
    }

    /**
     * 按业务名取用文生图模型实例；未注册返回 {@code null}。
     */
    @Override
    public ImageModel getImageModel(String name) {
        return imageModels.get(name);
    }

    /**
     * 按业务名取用多模态向量(CLIP-equivalent)模型实例；未注册返回 {@code null}。
     */
    @Override
    public ImageEmbeddingModel getImageEmbeddingModel(String name) {
        return imageEmbeddings.get(name);
    }

    /**
     * 按业务名取用 TTS 模型实例；未注册返回 {@code null}。
     */
    @Override
    public TextToSpeechModel getTextToSpeechModel(String name) {
        return ttsModels.get(name);
    }

    /**
     * 按业务名取用 STT / Transcription 模型实例；未注册返回 {@code null}。
     */
    @Override
    public TranscriptionModel getTranscriptionModel(String name) {
        return sttModels.get(name);
    }

    /**
     * 当前 Rerank 已注册业务名集合（只读快照）。
     */
    @Override
    public Set<String> getRerankModelNames() {
        return Collections.unmodifiableSet(rerankModels.keySet());
    }

    /**
     * 当前 Image 已注册业务名集合（只读快照）。
     */
    @Override
    public Set<String> getImageModelNames() {
        return Collections.unmodifiableSet(imageModels.keySet());
    }

    /**
     * 当前 Image Embedding 已注册业务名集合（只读快照）。
     */
    @Override
    public Set<String> getImageEmbeddingModelNames() {
        return Collections.unmodifiableSet(imageEmbeddings.keySet());
    }

    /**
     * 当前 TTS 已注册业务名集合（只读快照）。
     */
    @Override
    public Set<String> getTextToSpeechModelNames() {
        return Collections.unmodifiableSet(ttsModels.keySet());
    }

    /**
     * 当前 STT 已注册业务名集合（只读快照）。
     */
    @Override
    public Set<String> getTranscriptionModelNames() {
        return Collections.unmodifiableSet(sttModels.keySet());
    }
}