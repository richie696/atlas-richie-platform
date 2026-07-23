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
package com.richie.component.ai.api.image;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 图像嵌入模型接口。
 * <p>
 * Spring AI 标准 {@link EmbeddingModel} 仅显式暴露文本嵌入语义，本接口为需要图像分支语义的调用方
 * 提供类型化入口。CLIP 风格的实现也可以通过继承的 {@link #embed(String)} 方法投影文本，使文本与
 * 图像落入同一向量空间。
 *
 * @author richie696
 * @since 2026-07-22
 */
public interface ImageEmbeddingModel extends EmbeddingModel {

    /**
     * 将图像像素数据投影到与文本相同的向量空间（CLIP-equivalent）。
     *
     * @param imageUrlOrBase64 可访问的图像 URL，或携带媒体类型前缀的 Base64 data URL
     * @return 图像在文本对齐向量空间中的向量表示
     * @throws UnsupportedOperationException 当具体实现尚未提供图像分支时
     */
    default float[] embedImage(String imageUrlOrBase64) {
        throw new UnsupportedOperationException("Image embedding is not supported by this model");
    }
}
