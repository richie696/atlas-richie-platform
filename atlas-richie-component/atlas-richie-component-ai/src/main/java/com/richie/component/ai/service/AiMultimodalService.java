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
package com.richie.component.ai.service;

import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.image.ImageEmbeddingModel;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.image.ImageModel;

import java.util.Set;

/**
 * R-N 多模态服务接口(重排 / 文生图 / 语音合成 / 语音识别)。
 *
 * <p>镜像 {@link AiChatService} 的 Map + refresh 模式:以 {@code Map<String, T>} 作为运行时状态,
 * 启动期 + 每次 {@link #refresh()} 调用从 {@link com.richie.component.ai.config.AiModelProperties}
 * 重新构建实例。
 *
 * <h2>Chat ↔ 多模态 二维寻址模型对齐</h2>
 * <ul>
 *   <li>Map key = 业务名(对应 {@code platform.component.ai.rerank.<key>} 等)</li>
 *   <li>实现名 vendor 由 {@code RerankModelConfig.vendor} / {@code VoiceModelConfig.vendor} 等字段决定,
 *       由 {@code MultimodalModelFactory} 分派 impl</li>
 * </ul>
 *
 * @author richie696
 * @see com.richie.component.ai.service.impl.AiMultimodalServiceImpl
 * @since 1.0.0
 */
public interface AiMultimodalService {

    /**
     * 重新从 {@link com.richie.component.ai.config.AiModelProperties} 加载所有多模态模型。
     *
     * <p>适用场景:
     * <ul>
     *   <li>配置中心推送(Nacos / Apollo / Spring Cloud Config)变更后由 {@code AiMultimodalCloudBridge} 触发</li>
     *   <li>业务方运行时手动调用(例如从数据库读最新配置后强制刷新)</li>
     *   <li>启动期由 {@code AiModelAutoConfiguration} 调用一次</li>
     * </ul>
     */
    void refresh();

    RerankModel getRerankModel(String name);

    ImageModel getImageModel(String name);

    ImageEmbeddingModel getImageEmbeddingModel(String name);

    TextToSpeechModel getTextToSpeechModel(String name);

    TranscriptionModel getTranscriptionModel(String name);

    Set<String> getRerankModelNames();

    Set<String> getImageModelNames();

    Set<String> getImageEmbeddingModelNames();

    Set<String> getTextToSpeechModelNames();

    Set<String> getTranscriptionModelNames();
}