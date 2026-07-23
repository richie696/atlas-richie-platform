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
package com.richie.component.ai.provider.bailian;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.api.image.ImageEmbeddingModel;
import com.richie.component.http.core.HttpClient;
import com.richie.context.utils.data.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spring AI {@link org.springframework.ai.embedding.EmbeddingModel} 在阿里百炼（DashScope）平台上的适配器。
 *
 * <p>这是 R-N 中的"图向量"能力（CLIP-equivalent）— 通过 DashScope
 * {@code multimodal-embedding-v1} 把文本与图像投影到同一 1024 维向量空间，业务侧可借此
 * 实现"以文搜图 / 以图搜图 / 文-图混合检索"等跨模态语义检索场景。
 *
 * <h2>CLIP-equivalent 关键性质</h2>
 * <ul>
 *   <li><b>统一向量空间</b>：文本与图像共享一个 {@link #DIMENSIONS} 维表示，cosine/inner-product
 *       相似度可直接做跨模态排序。</li>
 *   <li><b>双模态输入</b>：同一请求体内的 {@code input.contents[]} 元素可以混排 {@code {"text":"..."}}
 *       与 {@code {"image":"data:image/jpeg;base64,..."}} — 每个元素独立返回一条向量。</li>
 *   <li><b>维度静态已知</b>：{@code multimodal-embedding-v1} 由官方文档承诺输出 1024 维。
 *       {@link #dimensions()} 直接返回 1024（避免 default 实现走远程 probe — R-N 决策）。</li>
 * </ul>
 *
 * <h2>协议细节</h2>
 * 请求端点：
 * <pre>{@code
 * POST https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding
 * Authorization: Bearer sk-xxx
 * Content-Type:  application/json
 *
 * {
 *   "model": "multimodal-embedding-v1",
 *   "input": {
 *     "contents": [
 *       {"text": "a cute cat"},
 *       {"image": "data:image/jpeg;base64,..."}
 *     ]
 *   }
 * }
 * }</pre>
 * 响应体：
 * <pre>{@code
 * {
 *   "output": {
 *     "embeddings": [
 *       {"embedding": [0.0123, 0.0456, ...], "text_index": 0},
 *       {"embedding": [0.0789, 0.0234, ...], "image_index": 0}
 *     ]
 *   }
 * }
 * }</pre>
 *
 * <h2>Spring AI 契约</h2>
 * <ul>
 *   <li>{@link #call(EmbeddingRequest)} 是抽象入口，由 Spring AI 调用 — 每次返回 n 个 {@link Embedding}，
 *       与请求的 {@code inputs} 个数一一对应。</li>
 *   <li>{@link #embed(String)} 由默认实现 {@code embed(List.of(text)).get(0)} 委托，会触发一次远程调用；
 *       业务侧若想避免 N+1 调用可改用 {@code embed(List<String>)} 批量形式。</li>
 *   <li>{@link #embed(Document)} 采用"文本分支" — 调用 {@link Document#getText()}，将文档降级为
 *       文本后做 embedding；这是 CLIP 的退化路径，能保证 Spring AI 标准管线（vector store 等）仍然可用，
 *       但会丢失图像语义。若业务需要图像语义，请走 {@link #embedImage(String)}。</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class BailianImageEmbeddingAdapter implements ImageEmbeddingModel {

    /**
     * DashScope {@code multimodal-embedding-v1} 多模态嵌入 REST 端点。
     * 路径遵循 DashScope 的"按 capability 路由"命名，区别于 {@code /api/v1/services/embeddings/text-embedding/text-embedding}
     * （纯文本嵌入）和 {@code /api/v1/services/rerank/...}（重排序）。
     */
    public static final String DEFAULT_BASE_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";

    /** DashScope 官方当前唯一对外的多模态嵌入模型名 — 1024 维。 */
    public static final String DEFAULT_MODEL = "multimodal-embedding-v1";

    /**
     * Embedding 维度 — 与 {@link #DEFAULT_MODEL} 强绑定。
     * 由阿里百炼官方文档固定为 1024 维，因此 {@link #dimensions()} 直接返回此常量（不发起远程 probe）。
     * 这是 R-N 决策：在模型名变化导致维度变化时需要主动同步修改本常量。
     */
    public static final int DIMENSIONS = 1024;

    /** Spring AI {@code ImageModel.embed(Document)} 默认的"文本分支"标识 — 区别于业务自定义图像分支。 */
    private static final String INPUT_TYPE_TEXT = "text";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;

    /**
     * 构造适配器。
     *
     * @param httpClient 共享 HTTP 客户端(由 atlas-richie-component-http-* 提供)
     * @param apiKey     DashScope API Key (Bearer)
     * @param baseUrl    REST 端点,允许为 null/blank 自动落到 {@link #DEFAULT_BASE_URL}
     * @param model      模型名,允许为 null/blank 自动落到 {@link #DEFAULT_MODEL}
     */
    public BailianImageEmbeddingAdapter(HttpClient httpClient, String apiKey, String baseUrl, String model) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        this.defaultModel = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    }

    // ====================== Spring AI 抽象入口 ======================

    /**
     * Spring AI 标准调用入口 — 把 {@link EmbeddingRequest#getInstructions()} 一一映射为
     * DashScope 请求中的 {@code input.contents[*].text}。本方法的实现只覆盖"文本分支"，因为
     * Spring AI 标准 {@code EmbeddingRequest} 不携带图像 — 纯文本向量化走此路径即可。
     * <p>
     * 若业务侧需要"图像分支"（CLIP 真正的双向语义），请改用本类扩展方法 {@link #embedImage(String)}
     * 或将其作为单独的 {@code ApiImageEmbedder} 接口暴露给业务侧。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> inputs = request.getInstructions();
        Objects.requireNonNull(inputs, "EmbeddingRequest.inputs must not be null");
        if (inputs.isEmpty()) {
            log.warn("Bailian image-embedding called with empty inputs, returning empty response");
            return new EmbeddingResponse(Collections.emptyList());
        }

        List<Map<String, Object>> contents = new ArrayList<>(inputs.size());
        for (String t : inputs) {
            // Spring AI 的 EmbeddingRequest 不携带"图像"分支，因此这里只走文本路径；
            // 图像通过 embedImage(String) 走专有 payload。
            contents.add(textContent(t));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("model", defaultModel);
        Map<String, Object> input = new HashMap<>();
        input.put("contents", contents);
        body.put("input", input);

        EmbeddingRawResponse raw = httpClient.post(baseUrl, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .execute(EmbeddingRawResponse.class);
        return toEmbeddingResponse(raw, inputs.size());
    }

    /**
     * 文档向量 — 沿用 {@link Document#getText()}（CLIP 文本分支）。
     * <p>
     * 这是 Spring AI 标准管线（如 vector store 文档摄取）与 CLIP-equivalent 的"妥协点"：
     * Spring AI 的 {@link Document} 模型不携带像素信息，所以此处只能退化到文本分支。
     * 真正的图像分支请调用 {@link #embedImage(String)}。
     */
    @Override
    public float[] embed(Document document) {
        Objects.requireNonNull(document, "document must not be null");
        String text = document.getText();
        if (text == null) {
            // 文档只携带 Media（图像），没有可向量化的文本 — 返回零向量而不是抛错，
            // 让上层 vector store 仍然能走到写入流程。
            log.warn("Bailian image-embedding: Document has no text (likely image-only), returning zero vector");
            return new float[DIMENSIONS];
        }
        return embed(text);
    }

    /**
     * 维度 — 返回常量 {@link #DIMENSIONS} = 1024，<b>不发起远程 probe</b>。
     * <p>
     * Spring AI 默认实现的 {@code dimensions()} 会调一次 {@code embed("Test String")} 去读
     * 返回向量的长度 — 等价于每次业务侧使用 dimension 信息都会浪费一次计费调用。
     * 因为 {@code multimodal-embedding-v1} 维度由厂商文档硬编码，我们直接返回 1024。
     * 若未来 vendor 在同一端点引入新模型（如 {@code multimodal-embedding-v2}，维度不同），
     * 需要在此分支上加判断或拓宽为实例字段。
     */
    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    // ====================== Bailian-specific 扩展（非 Spring AI 标准） ======================

    /**
     * 图像分支 — Bailian 扩展能力，不在 Spring AI 标准 {@link org.springframework.ai.embedding.EmbeddingModel}
     * 接口中。业务侧若需要"以图向量"或"以文搜图"，应单独暴露本方法（或包装一个 {@code ImageEmbedder}
     * 接口）调用，避免与 Spring AI 文本文档摄取混淆。
     *
     * @param imageUrlOrBase64 远程可访问的图片 URL，或 {@code data:image/<sub>;base64,<data>} 前缀的内联 data URL
     * @return 1024 维的 {@code float[]} 向量
     */
    @Override
    public float[] embedImage(String imageUrlOrBase64) {
        Objects.requireNonNull(imageUrlOrBase64, "imageUrlOrBase64 must not be null");
        Map<String, Object> body = new HashMap<>();
        body.put("model", defaultModel);
        Map<String, Object> input = new HashMap<>();
        input.put("contents", List.of(Map.of("image", imageUrlOrBase64)));
        body.put("input", input);

        EmbeddingRawResponse raw = httpClient.post(baseUrl, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .execute(EmbeddingRawResponse.class);
        return toEmbeddingResponse(raw, 1).getResults().get(0).getOutput();
    }

    // ====================== 内部工具 ======================

    /**
     * 构造一个文本 content 条目。
     */
    static Map<String, Object> textContent(String text) {
        Map<String, Object> m = new HashMap<>(1);
        m.put(INPUT_TYPE_TEXT, text);
        return m;
    }

    /**
     * 把 DashScope 响应装配为 Spring AI {@link EmbeddingResponse}。
     * <p>
     * 关键保护：
     * <ul>
     *   <li>{@code output == null} 或 {@code output.embeddings == null} → 返回空 results，
     *       让上层按"业务方拿到空列表"判断 — 不抛异常，与同包其他 adapter 风格一致。</li>
     *   <li>向量可能以 String/Number 两种形态回传（DashScope 在不同 region 行为略不同），
     *       这里用 {@code Number} 接住后转 float，端到端容忍。</li>
     *   <li>results 数量小于 expectedInputs 时补零向量占位 — 极端边界（裁剪输出）兜底，
     *       避免上层 index out of bounds。</li>
     * </ul>
     */
    private EmbeddingResponse toEmbeddingResponse(EmbeddingRawResponse raw, int expectedInputs) {
        List<Embedding> out = new ArrayList<>(expectedInputs);
        if (raw == null || raw.output == null || raw.output.embeddings == null) {
            log.warn("Bailian image-embedding returned empty body: {}", JsonUtils.getInstance().serialize(raw));
            for (int i = 0; i < expectedInputs; i++) {
                out.add(new Embedding(new float[DIMENSIONS], i));
            }
            return new EmbeddingResponse(out);
        }
        List<EmbeddingRawItem> items = raw.output.embeddings;
        for (int i = 0; i < expectedInputs; i++) {
            if (i < items.size() && items.get(i) != null) {
                float[] vec = toFloatArray(items.get(i).embedding);
                out.add(new Embedding(vec, i));
            } else {
                // 缺失项补零向量 — 上层可通过"向量内容全 0"判定"无意义条目"
                out.add(new Embedding(new float[DIMENSIONS], i));
            }
        }
        return new EmbeddingResponse(out);
    }

    /**
     * 把 DashScope 返回的数字数组（可能含 String 或 Number）转 float[]。
     * 健壮性：空/全 null → 全部 0f；坏值 → 该槽位 0f（不让单点序列化错误炸掉整次调用）。
     */
    private static float[] toFloatArray(List<Number> values) {
        if (values == null || values.isEmpty()) {
            return new float[DIMENSIONS];
        }
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Number n = values.get(i);
            out[i] = (n == null) ? 0f : n.floatValue();
        }
        return out;
    }

    // ====== DashScope response DTOs ======

    /** 顶层响应：{@code { "output": { "embeddings": [...] } }}。 */
    static class EmbeddingRawResponse {
        public EmbeddingRawOutput output;
    }

    /** {@code output} 子对象。 */
    static class EmbeddingRawOutput {
        public List<EmbeddingRawItem> embeddings;
    }

    /**
     * 单条 embedding 结果：{@code { "embedding": [0.1, 0.2, ...], "text_index": 0, "image_index": 0 }}。
     * 使用 {@link JsonProperty} 显式声明是因为 DashScope 返回 snake_case 而 Java 字段为 camelCase。
     * {@code text_index} / {@code image_index} 在不同 input 形态下只返回其中一个 —— 反序列化时
     * 哪个非空就以哪个标识索引。
     */
    static class EmbeddingRawItem {
        @JsonProperty("embedding")
        public List<Number> embedding;

        @JsonProperty("text_index")
        public Integer textIndex;

        @JsonProperty("image_index")
        public Integer imageIndex;
    }
}
