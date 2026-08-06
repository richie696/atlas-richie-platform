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
package cn.richie696.component.vector.model;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 向量内容多模态封装（sealed interface）。
 *
 * <p>作为 {@link VectorRecord} 的 {@code content} 字段，承载"要被嵌入"的真实数据。它的设计动机
 * 是用类型系统把不同模态的不可变约束（文本非空、图片字节非空、MIME 前缀）放进子类构造函数，
 * 让错误尽可能在工厂层就暴露，而不是跑到 provider 调用栈。</p>
 *
 * <p>sealed 设计只暴露两个 permitted 子类（{@link TextContent}、{@link ImageContent}），
 * 未来新增模态（例如 AUDIO/VIDEO）需要修改 permits 列表——这是一个有意的"协议变更点"，
 * 让任何对 sealed 类型的扩展都被显式审视，而不会随意外溢。</p>
 *
 * <p>调用关系：{@link VectorRecord} 持有本类型实例；{@link cn.richie696.component.vector.embeddings.ModalityAwareEmbeddingService}
 * 按 {@link #modality()} 路由到对应的 {@code EmbeddingModel}；文本模型必选，图片模型可选，
 * 缺失时 {@link cn.richie696.component.vector.exceptions.UnsupportedModalityException} 被抛出。</p>
 *
 * <p><b>向量空间约束</b>：仅在文本模型与图像模型属于兼容向量空间（例如 CLIP/SigLIP 类对齐模型）
 * 时，才应把两种内容写入同一索引；否则会出现"跨模态检索无意义"或"维度不匹配"的隐性 bug。
 * 该约束由上层业务而非本接口保证。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public sealed

interface VectorContent
        permits VectorContent.TextContent, VectorContent.ImageContent {

    /**
     * 该内容的模态。
     *
     * <p>{@link TextContent} 始终返回 {@link Modality#TEXT}，{@link ImageContent} 始终返回
     * {@link Modality#IMAGE}；消费者可以直接基于该返回值路由到对应 EmbeddingModel。</p>
     *
     * @return 与内容类型一一对应的 {@link Modality}
     */
    Modality modality();

    /**
     * 文本内容。
     *
     * <p>RAG 流程中的主模态：99% 的检索、文档切分入库都走它。紧凑构造器会校验
     * {@code text} 非空，并把空 MIME 默认填为 {@code text/plain}（{@code text/markdown}、
     * {@code text/html} 等可由调用方显式传入）。</p>
     *
     * @param text     原始文本（必填，紧凑构造器会校验非空）。
     * @param mimeType MIME 类型，例如 {@code text/plain} / {@code text/markdown}，为空时默认
     *                 填 {@code text/plain}。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record TextContent(String text, String mimeType) implements

    VectorContent {

        /**
         * 紧凑构造器：自动校验 + 默认 mimeType。
         *
         * <p>text 非空校验是协议层硬要求，避免上游把空白字符串当成有效文本嵌入。空
         * mimeType 默认填 {@code text/plain} 是为兼容大部分 EmbeddingModel 期望。</p>
         *
         * @param text     原始文本
         * @param mimeType MIME 类型
         * @throws IllegalArgumentException 当 {@code text} 为空时
         */
        public TextContent {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("TextContent.text 不能为空");
            }
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "text/plain";
            }
        }

        /**
         * 文本内容对应的模态。
         *
         * @return 恒为 {@link Modality#TEXT}
         */
        @Override
        public Modality modality () {
            return Modality.TEXT;
        }
    }

    /**
     * 图片内容。
     *
     * <p>字节 + MIME 双字段强制校验：{@code data} 非空、{@code mimeType} 必须以
     * {@code image/} 开头。{@link #ofPath} 工厂方法提供"从路径直接构造"便捷入口，
     * 同时保留紧凑构造器的所有不变量校验。</p>
     *
     * @param data     原始字节数组（必填，紧凑构造器校验非空）。
     * @param mimeType MIME 类型，必须以 {@code image/} 开头（紧凑构造器校验），例如
     *                 {@code image/png} / {@code image/jpeg}。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record ImageContent(byte[] data, String mimeType) implements

    VectorContent {

        /**
         * 紧凑构造器：校验字节非空 + MIME 类型合法。
         *
         * <p>{@link #data} 不能为空 / {@link #mimeType} 必须以 {@code image/} 开头是协议层
         * 硬要求，避免上游把空字节数组当成有效图片嵌入，或把非图片 MIME 错递给图像模型。</p>
         *
         * @param data     原始字节数组
         * @param mimeType MIME 类型
         * @throws IllegalArgumentException 当字节为空或 MIME 不合法时
         */
        public ImageContent {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("ImageContent.data 不能为空");
            }
            if (mimeType == null || !mimeType.startsWith("image/")) {
                throw new IllegalArgumentException("ImageContent.mimeType 必须是 image/* 类型，实际: " + mimeType);
            }
        }

        /**
         * 图片内容对应的模态。
         *
         * @return 恒为 {@link Modality#IMAGE}
         */
        @Override
        public Modality modality () {
            return Modality.IMAGE;
        }

        /**
         * 便利工厂：从 {@link Path} 读取字节并包装为 {@link ImageContent}。
         *
         * <p>读取失败（权限、IO 异常）会被包装为 {@link IllegalArgumentException} 以保持接口
         * 抛出的异常类型与紧凑构造器一致。</p>
         *
         * @param path     图片文件路径
         * @param mimeType MIME 类型
         * @return ImageContent 实例
         * @throws IllegalArgumentException 当读取图片文件失败时
         */
        public static ImageContent ofPath (Path path, String mimeType){
            try {
                return new ImageContent(Files.readAllBytes(path), mimeType);
            } catch (Exception e) {
                throw new IllegalArgumentException("读取图片文件失败: " + path, e);
            }
        }
    }
}