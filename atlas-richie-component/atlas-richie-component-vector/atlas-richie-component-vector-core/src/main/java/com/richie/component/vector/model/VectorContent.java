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

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 向量内容多模态封装（sealed interface）。
 * <p>
 * 用 sealed 表达"内容要么是文本，要么是图片"的封闭域，编译期挡住错误用法。
 * 每种实现都必须返回自己的 {@link Modality}。
 *
 * @author richie696
 * @since 2.0.0
 */
public sealed interface VectorContent
        permits VectorContent.TextContent, VectorContent.ImageContent {

    /**
     * @return 该内容的模态
     */
    Modality modality();

    /**
     * 文本内容。
     *
     * @param text     原始文本（必填）
     * @param mimeType MIME 类型，例如 {@code text/plain} / {@code text/markdown}
     */
    record TextContent(String text, String mimeType) implements VectorContent {

        /**
         * 紧凑构造器：自动校验 + 默认 mimeType
         */
        public TextContent {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("TextContent.text 不能为空");
            }
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "text/plain";
            }
        }

        @Override
        public Modality modality() {
            return Modality.TEXT;
        }
    }

    /**
     * 图片内容。
     *
     * @param data     原始字节数组（必填）
     * @param mimeType MIME 类型，必须以 {@code image/} 开头，例如 {@code image/png} / {@code image/jpeg}
     */
    record ImageContent(byte[] data, String mimeType) implements VectorContent {

        /**
         * 紧凑构造器：校验字节非空 + MIME 类型合法
         */
        public ImageContent {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("ImageContent.data 不能为空");
            }
            if (mimeType == null || !mimeType.startsWith("image/")) {
                throw new IllegalArgumentException("ImageContent.mimeType 必须是 image/* 类型，实际: " + mimeType);
            }
        }

        @Override
        public Modality modality() {
            return Modality.IMAGE;
        }

        /**
         * 便利工厂：从 Path 读取字节并包装为 ImageContent。
         *
         * @param path     图片文件路径
         * @param mimeType MIME 类型
         * @return ImageContent 实例
         */
        public static ImageContent ofPath(Path path, String mimeType) {
            try {
                return new ImageContent(Files.readAllBytes(path), mimeType);
            } catch (Exception e) {
                throw new IllegalArgumentException("读取图片文件失败: " + path, e);
            }
        }
    }
}