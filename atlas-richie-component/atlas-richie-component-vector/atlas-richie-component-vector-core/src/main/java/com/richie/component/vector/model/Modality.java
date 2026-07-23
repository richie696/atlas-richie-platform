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
package com.richie.component.vector.model;

/**
 * 向量内容模态。
 * <p>
 * 用于标识 {@link VectorContent} 的内容类型，决定走哪个 EmbeddingModel。
 *
 * @author richie696
 * @since 2.0.0
 */
public enum Modality {

    /**
     * 文本模态 — 使用文本 EmbeddingModel（OpenAI text-embedding、BGE 等）
     */
    TEXT,

    /**
     * 图片模态 — 使用多模态 EmbeddingModel（CLIP / SigLIP / BLIP-2）
     */
    IMAGE
}