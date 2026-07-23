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
package com.richie.component.ai.support.keypool;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageEmbeddingModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageModelConfig;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

/**
 * 多模态 config 浅拷贝工具 — 把 config 的 apiKey 覆盖成指定值,其他字段共享引用。
 * <p>
 * 用于池化路径:为每个 key 预建一个 provider 实例,复用同一份 baseUrl/region/options 等。
 *
 * @author richie696
 */
public final class MultimodalConfigCloner {

    private MultimodalConfigCloner() {}

    public static RerankModelConfig cloneRerankWithKey(RerankModelConfig src, String apiKey) {
        RerankModelConfig copy = new RerankModelConfig();
        copy.setProvider(src.getProvider());
        copy.setName(src.getName());
        copy.setApiKey(apiKey);
        copy.setAppCode(src.getAppCode());
        copy.setBaseUrl(src.getBaseUrl());
        copy.setModel(src.getModel());
        copy.setAccessKey(src.getAccessKey());
        copy.setSecretKey(src.getSecretKey());
        copy.setRegion(src.getRegion());
        copy.setApiKeys(src.getApiKeys());  // 保留 key 池(向后兼容/调试)
        return copy;
    }

    public static ImageModelConfig cloneImageWithKey(ImageModelConfig src, String apiKey) {
        ImageModelConfig copy = new ImageModelConfig();
        copy.setProvider(src.getProvider());
        copy.setName(src.getName());
        copy.setApiKey(apiKey);
        copy.setBaseUrl(src.getBaseUrl());
        copy.setModel(src.getModel());
        copy.setApiKeys(src.getApiKeys());
        return copy;
    }

    public static ImageEmbeddingModelConfig cloneImageEmbeddingWithKey(ImageEmbeddingModelConfig src, String apiKey) {
        ImageEmbeddingModelConfig copy = new ImageEmbeddingModelConfig();
        copy.setProvider(src.getProvider());
        copy.setName(src.getName());
        copy.setApiKey(apiKey);
        copy.setBaseUrl(src.getBaseUrl());
        copy.setModel(src.getModel());
        copy.setApiKeys(src.getApiKeys());
        return copy;
    }

    public static AbstractAudioModelConfig cloneAudioWithKey(AbstractAudioModelConfig src, String apiKey) {
        AbstractAudioModelConfig copy;
        if (src.getClass() == com.richie.component.ai.config.multimodal.tts.TtsModelConfig.class) {
            com.richie.component.ai.config.multimodal.tts.TtsModelConfig t =
                    new com.richie.component.ai.config.multimodal.tts.TtsModelConfig();
            t.setProvider(((com.richie.component.ai.config.multimodal.tts.TtsModelConfig) src).getProvider());
            copy = t;
        } else if (src.getClass() == com.richie.component.ai.config.multimodal.stt.SttModelConfig.class) {
            com.richie.component.ai.config.multimodal.stt.SttModelConfig s =
                    new com.richie.component.ai.config.multimodal.stt.SttModelConfig();
            s.setProvider(((com.richie.component.ai.config.multimodal.stt.SttModelConfig) src).getProvider());
            copy = s;
        } else if (src.getClass() == com.richie.component.ai.config.multimodal.voicechat.VoiceChatModelConfig.class) {
            com.richie.component.ai.config.multimodal.voicechat.VoiceChatModelConfig v =
                    new com.richie.component.ai.config.multimodal.voicechat.VoiceChatModelConfig();
            v.setVendor(((com.richie.component.ai.config.multimodal.voicechat.VoiceChatModelConfig) src).getVendor());
            copy = v;
        } else {
            throw new IllegalArgumentException("未知 AbstractAudioModelConfig 子类型: " + src.getClass());
        }
        copy.setName(src.getName());
        copy.setApiKey(apiKey);
        copy.setSecretId(src.getSecretId());
        copy.setSecretKey(src.getSecretKey());
        copy.setAppCode(src.getAppCode());
        copy.setBaseUrl(src.getBaseUrl());
        copy.setModel(src.getModel());
        copy.setRegion(src.getRegion());
        copy.setEndpoint(src.getEndpoint());
        copy.setResourceId(src.getResourceId());
        copy.setAppId(src.getAppId());
        copy.setApiKeys(src.getApiKeys());
        return copy;
    }
}