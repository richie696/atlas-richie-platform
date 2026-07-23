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
package com.richie.component.vector.embeddings;

import com.richie.component.vector.exceptions.UnsupportedModalityException;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.service.VectorService;
import jakarta.annotation.Nullable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 多模态嵌入路由服务 — {@link VectorService} 的中央模态分发器。
 * <p>
 * <b>职责</b>：
 * <ul>
 *   <li>根据 {@link Modality} 把 {@link VectorContent} 路由到对应的 {@link EmbeddingModel}
 *       — TEXT 走 {@code textModel}，IMAGE 走 {@code imageModel}；</li>
 *   <li>暴露 {@link #supportsModality(Modality)} 与 {@link #dimensionFor(Modality)}，
 *       让 vector provider 在写入前查询"是否支持"与"维度"。</li>
 *   <li>当 {@code imageModel} 未配置时，IMAGE 内容抛 {@link UnsupportedModalityException} —
 *       与 Phase A "v2 IMAGE 模态暂未启用" 语义一致，但文案更新为配置指引。</li>
 * </ul>
 *
 * <h2>关键约束（同 §6.3 决策点 1）</h2>
 * 文本和图片嵌入模型产出的向量<b>必须在同一个向量空间内</b>，否则混合检索无意义。
 * 当前实现假定调用方注入的 {@code textModel} 与 {@code imageModel} 满足这一前提（如 CLIP/SigLIP）。
 *
 * <h2>Bean 装配</h2>
 * <ul>
 *   <li>{@code textModel}：必填，由 Spring 容器注入 {@code aiEmbeddingModel} Bean
 *       （{@code atlas-richie-component-ai} 自动注册）。</li>
 *   <li>{@code imageModel}：可选，通过 {@code @Qualifier("imageEmbeddingModel")} 注入；
 *       不存在时为 {@code null}，{@link #embed(Modality, VectorContent)} 会抛
 *       {@link UnsupportedModalityException}。</li>
 * </ul>
 *
 * @author richie696
 * @since 2.0.0
 */
@Service
public class ModalityAwareEmbeddingService {

    /** 文本嵌入模型 — 必填。 */
    private EmbeddingModel textModel;

    /** 图像嵌入模型 — 可选（CLIP/SigLIP 未配置时为 {@code null}）。 */
    @Nullable
    private EmbeddingModel imageModel;

    /**
     * 构造器 — 由 Spring 调用。
     * <p>
     * <b>为何不在此调用 {@code textModel.dimensions()}</b>：Spring DI 可能先注入代理包装的对象，
     * 此时 {@code dimensions()} 在某些 Spring AI 实现（如基于 bean factory 的懒初始化模型）下
     * 未必可用或代价昂贵；按需懒解析更稳健。
     *
     * @param textModel  文本嵌入模型，必填
     * @param imageModel 图像嵌入模型，可为 {@code null}
     */
public ModalityAwareEmbeddingService(EmbeddingModel textModel,
                                          @Nullable @Qualifier("imageEmbeddingModel") EmbeddingModel imageModel) {
        if (textModel == null) {
            throw new IllegalArgumentException("textModel 不能为空 — 文本嵌入是基线能力");
        }
        this.textModel = textModel;
        this.imageModel = imageModel;
    }

    public void setTextModel(@Nullable EmbeddingModel textModel) {
        if (textModel == null) {
            throw new IllegalArgumentException("textModel 不能为空 — 文本嵌入是基线能力");
        }
        this.textModel = textModel;
    }

    public void setImageModel(@Nullable EmbeddingModel imageModel) {
        this.imageModel = imageModel;
    }

    /**
     * 按模态路由，把 {@link VectorContent} 投到对应的嵌入模型。
     *
     * @param modality 内容模态
     * @param content  内容载体（{@link VectorContent.TextContent} 或 {@link VectorContent.ImageContent}）
     * @return 嵌入向量（维度由所选模型的 {@link EmbeddingModel#dimensions()} 决定）
     * @throws UnsupportedModalityException 当 {@code modality == IMAGE} 但未配置图像嵌入模型
     * @throws IllegalArgumentException     当 {@code content} 类型与 {@code modality} 不匹配
     */
    public float[] embed(Modality modality, VectorContent content) {
        if (content == null) {
            throw new IllegalArgumentException("content 不能为空");
        }
        return switch (modality) {
            case TEXT -> embedText(content);
            case IMAGE -> embedImage(content);
        };
    }

    /**
     * 查询当前服务是否支持某种模态。
     * <p>
     * 规则：
     * <ul>
     *   <li>TEXT — 始终支持（textModel 必填）。</li>
     *   <li>IMAGE — 仅当 {@code imageModel} 已配置时支持。</li>
     * </ul>
     *
     * @param m 模态枚举
     * @return true=可路由到对应嵌入模型
     */
    public boolean supportsModality(Modality m) {
        if (m == null) {
            return false;
        }
        return switch (m) {
            case TEXT -> true;
            case IMAGE -> imageModel != null;
        };
    }

    /**
     * 查询指定模态的嵌入维度。
     * <p>
     * TEXT 一律返回 {@code textModel.dimensions()}；IMAGE 在模型缺失时返回 {@code 0}，
     * 让调用方可以"零向量 = 未配置"的语义兜底（例如写入前的向量空间校验）。
     *
     * @param m 模态枚举
     * @return 维度数值；未配置时为 0
     */
    public int dimensionFor(Modality m) {
        if (m == null) {
            return 0;
        }
        return switch (m) {
            case TEXT -> textModel.dimensions();
            case IMAGE -> imageModel != null ? imageModel.dimensions() : 0;
        };
    }

    // ==================== 内部 ====================

    private float[] embedText(VectorContent content) {
        if (!(content instanceof VectorContent.TextContent text)) {
            throw new IllegalArgumentException(
                    "TEXT 模态要求 TextContent, 实际: " + content.getClass().getSimpleName());
        }
        return textModel.embed(text.text());
    }

    private float[] embedImage(VectorContent content) {
        if (imageModel == null) {
            throw new UnsupportedModalityException(
                    "IMAGE 模态未配置 — 请配置 vector.image-embedding.model 后启用 CLIP");
        }
        if (!(content instanceof VectorContent.ImageContent image)) {
            throw new IllegalArgumentException(
                    "IMAGE 模态要求 ImageContent, 实际: " + content.getClass().getSimpleName());
        }

        // 标准路径：data URL 字符串走 EmbeddingModel.embed(String) —
        // provider 必须将字符串解释为图像（CLIP 多模态协议普遍如此）。
        return imageModel.embed(toDataUrl(image));
    }

    /**
     * 把 {@link VectorContent.ImageContent}（字节 + mimeType）转换为嵌入模型可消费的字符串。
     * <p>
     * 行为：
     * <ul>
     *   <li>image.data() 视为原始图片字节 — base64 编码后拼成 {@code data:<mime>;base64,<...>}</li>
     *   <li>Spring AI EmbeddingModel.embed(String) 默认仅消费文本，因此这里一律输出 data URL 形式，
     *       与 Bailian / DashScope multimodal-embedding-v1 协议对齐</li>
     * </ul>
     */
    private static String toDataUrl(VectorContent.ImageContent image) {
        byte[] bytes = image.data();
        String mime = image.mimeType();
        String base64 = Base64.getEncoder().encodeToString(bytes == null ? new byte[0] : bytes);
        return "data:" + mime + ";base64," + base64;
    }

    /**
     * 保留工具方法：从纯文本内容构造 data URL（供未来 text-as-image 场景使用）。
     * 当前未对外暴露。
     */
    @SuppressWarnings("unused")
    private static String dataUrlUtf8(String mime, String raw) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}