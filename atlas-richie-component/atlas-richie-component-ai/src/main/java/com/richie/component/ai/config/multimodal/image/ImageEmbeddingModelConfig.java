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
package com.richie.component.ai.config.multimodal.image;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 多模态向量模型(CLIP-equivalent)配置 — 映射 {@code platform.component.ai.image-embedding.<key>}。
 *
 * <p>与 {@link ImageModelConfig}(文生图)并列存在：前者面向"出图"语义、生成像素；
 * 本配置面向"出向量"语义、为跨模态检索提供统一的 1024 维表示空间。
 * 两者配置键不同({@code image} vs {@code image-embedding})，避免误用。
 *
 * <p>当前仅 {@link ImageEmbeddingProvider#BAILIAN} 落地。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class ImageEmbeddingModelConfig {

    /** 厂商(枚举)— 见 {@link ImageEmbeddingProvider}。 */
    private ImageEmbeddingProvider provider;

    /** 业务可读标识(默认与 Map key 相同)。 */
    private String name;

    /** DashScope / 其他厂商的 API Key。 */
    private String apiKey;

    /** API Key 池 — Token Plan 多 key 轮询 / 限流后冷却。YAML: {@code api-keys: [sk-1, sk-2]}。 */
    private Set<String> apiKeys = new LinkedHashSet<>();

    /** 厂商端点 URL(为空时适配器回落到默认 URL)。 */
    private String baseUrl;

    /**
     * 多模态嵌入模型名，默认 {@code multimodal-embedding-v1}，产出 1024 维向量。
     * 与 {@link #baseUrl} 联动 — 一般仅自建代理/灰度时需要覆盖。
     */
    private String model;
}
