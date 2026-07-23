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
package com.richie.component.vector.exceptions;

import com.richie.component.vector.model.Modality;

/**
 * 当前向量服务不支持所请求的模态时抛出。
 * <p>
 * 典型场景：
 * <ul>
 *   <li>调用方传入 {@code IMAGE} 模态内容，但未配置图像嵌入模型（CLIP / SigLIP）</li>
 *   <li>当前 provider 实现不支持多模态（如 Elasticsearch 文本+向量混合模式）</li>
 * </ul>
 *
 * @author richie696
 * @since 2.0.0
 */
public class UnsupportedModalityException extends UnsupportedOperationException {

    private final Modality modality;

    public UnsupportedModalityException(Modality modality, String message) {
        super(message);
        this.modality = modality;
    }

    public UnsupportedModalityException(String message) {
        super(message);
        this.modality = Modality.IMAGE;
    }

    public UnsupportedModalityException(Modality modality, String message, Throwable cause) {
        super(message, cause);
        this.modality = modality;
    }

    public Modality getModality() {
        return modality;
    }
}