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
package com.richie.component.ai.provider.tei;

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
 * Spring AI {@link org.springframework.ai.embedding.EmbeddingModel} 在 HuggingFace
 * Text Embeddings Inference (TEI) 上的通用适配器。
 *
 * <p>TEI 是 HF 官方推荐的自托管嵌入推理运行时,典型部署形态为 Docker 容器,服务单
 * 一 sentence-transformers / CLIP 模型。本适配器走 TEI 暴露的
 * <b>OpenAI 兼容协议</b>({@code /v1/embeddings}),原因是它在各 TEI 版本/部署方式
 * (CPU / GPU / candle / tgi 后端)中最稳定 — TEI 原生路径 {@code /embed} 在不同模型下
 * 的请求体形态差异较大,而 OpenAI 协议在文档与实现上保持一致。
 *
 * <h2>CLIP-equivalent 关键性质</h2>
 * <ul>
 *   <li><b>统一向量空间</b>:TEI 加载 CLIP 类多模态模型时,文本与图像共享同一向量空间,
 *       业务侧可借此做"以文搜图 / 以图搜图"等跨模态语义检索。</li>
 *   <li><b>维度取决于部署模型</b>:与 {@code BailianImageEmbeddingAdapter} 不同,TEI
 *       的向量维度由运行时部署的模型决定,本适配器在编译期无法确定 — 因此
 *       {@link #dimensions()} 直接返回 {@code 0},避免 Spring AI 默认实现做一次计费
 *       probe 调用。</li>
 *   <li><b>本适配器当前只覆盖"文本分支"</b>:OpenAI 协议的标准 {@code input} 字段为
 *       字符串/字符串数组。图像分支(CLIP 真正的双向语义)需走 TEI 的
 *       {@code /embed_image} 端点 — 该路径在协议层与 OpenAI 不兼容,留待 Phase C
 *       统一 {@code ImageEmbeddingModel} 抽象时再行扩展;在此之前业务侧如有图像需求
 *       建议继续走 {@link com.richie.component.ai.provider.bailian.BailianImageEmbeddingAdapter#embedImage(String)}。</li>
 * </ul>
 *
 * <h2>协议细节(OpenAI 兼容)</h2>
 * 请求端点:
 * <pre>{@code
 * POST {baseUrl}/v1/embeddings
 * Content-Type: application/json
 * Authorization: Bearer <apiKey>  (可选 — 仅当 apiKey 非空时添加)
 *
 * {
 *   "input": ["text1", "text2", ...],
 *   "model": "BAAI/bge-large-en-v1.5"   // 可选 — TEI 单容器只服务一个模型,通常省略
 * }
 * }</pre>
 * 响应体:
 * <pre>{@code
 * {
 *   "data": [
 *     {"embedding": [0.0123, -0.0456, ...], "index": 0},
 *     {"embedding": [0.0789, -0.0234, ...], "index": 1}
 *   ],
 *   "model": "BAAI/bge-large-en-v1.5",
 *   "usage": {"prompt_tokens": 2, "total_tokens": 2}
 * }
 * }</pre>
 *
 * <h2>Spring AI 契约</h2>
 * <ul>
 *   <li>{@link #call(EmbeddingRequest)} 是抽象入口 — 每次返回 n 个 {@link Embedding},
 *       与请求的 {@code inputs} 个数一一对应;按响应中的 {@code index} 字段重新排序,
 *       缺失或越界的槽位用空向量占位。</li>
 *   <li>{@link #embed(String)} / {@link #embed(List)} 由 Spring AI 默认实现委托到
 *       {@link #call(EmbeddingRequest)},自动生效。</li>
 *   <li>{@link #embed(Document)} 走"文本分支" — 调用 {@link Document#getText()};
 *       图像 / 媒体型文档在 Spring AI 抽象下只能退化为文本路径,真正的图像分支由
 *       Phase C 统一接口接管。</li>
 * </ul>
 *
 * <h2>鉴权</h2>
 * TEI 默认 <b>不需要</b>鉴权 — 但很多生产部署会在前面挂 Nginx / Envoy 反代,
 * 通过 {@code Authorization: Bearer <key>} 做内网访问控制。本适配器在 {@code apiKey}
 * 非空时自动注入 Bearer 头,空时跳过,与典型部署保持一致。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class TeiImageEmbeddingAdapter implements ImageEmbeddingModel {

    /**
     * TEI Docker 默认端口 {@code 8080}(暴露 {@code /v1/embeddings})。
     * 反代部署时把 baseUrl 指向 Nginx / Envoy 即可,无需修改端口。
     */
    public static final String DEFAULT_BASE_URL = "http://localhost:8080";

    /**
     * OpenAI 兼容 embeddings 路径 — 与 {@code /embed} (TEI 原生) 二选一,
     * 这里固定走 OpenAI 兼容以获得跨部署的稳定性。
     */
    private static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private final HttpClient httpClient;
    private final String baseUrl;
    /** TEI 单容器服务单模型,默认 {@code null}(请求体不携带 {@code model} 字段)。 */
    private final String defaultModel;
    /** 反代鉴权用 Bearer;{@code null} 或空白 → 不注入 Authorization 头。 */
    private final String apiKey;

    /**
     * 构造适配器。
     *
     * @param httpClient 共享 HTTP 客户端(由 atlas-richie-component-http-* 提供)
     * @param baseUrl    TEI 服务根地址(不含 {@code /v1/embeddings});允许为 null/blank 自动落到
     *                   {@link #DEFAULT_BASE_URL}
     * @param model      模型名;允许为 null/blank(TEI 单容器固定模型,通常省略)
     * @param apiKey     反代 Bearer 鉴权 key;允许为 null/blank(无鉴权时不携带 Authorization 头)
     */
    public TeiImageEmbeddingAdapter(HttpClient httpClient, String baseUrl, String model, String apiKey) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        String resolved = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        this.baseUrl = stripTrailingSlash(resolved);
        this.defaultModel = (model == null || model.isBlank()) ? null : model;
        this.apiKey = (apiKey == null || apiKey.isBlank()) ? null : apiKey;
    }

    // ====================== Spring AI 抽象入口 ======================

    /**
     * Spring AI 标准调用入口 — 把 {@link EmbeddingRequest#getInstructions()} 统一以
     * "数组"形态提交(OpenAI 兼容 TEI 同时接受单字符串和数组,数组是兼容性最好的选择),
     * 把响应中的 {@code data[*].index} 重新映射回请求槽位 — TEI 在某些模型下不保证顺序,
     * 必须以 index 排序。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> inputs = request.getInstructions();
        Objects.requireNonNull(inputs, "EmbeddingRequest.inputs must not be null");
        if (inputs.isEmpty()) {
            log.warn("TEI image-embedding called with empty inputs, returning empty response");
            return new EmbeddingResponse(Collections.emptyList());
        }

        String url = baseUrl + EMBEDDINGS_PATH;
        Map<String, Object> body = new HashMap<>();
        // 始终以数组形式提交 — 单元素请求也走数组,降低"单/批"差异带来的兼容性问题
        body.put("input", inputs);
        if (defaultModel != null) {
            body.put("model", defaultModel);
        }

        com.richie.component.http.core.HttpRequest httpReq = httpClient.post(url, body)
                .header("Content-Type", "application/json");
        if (apiKey != null) {
            // 仅在 apiKey 非空时携带 Authorization 头 — TEI 本身不要求鉴权,
            // 但生产反代通常需要 Bearer;为空时静默跳过,避免污染请求。
            httpReq.header("Authorization", "Bearer " + apiKey);
        }
        TeiEmbeddingRawResponse raw = httpReq.execute(TeiEmbeddingRawResponse.class);
        return toEmbeddingResponse(raw, inputs.size());
    }

    /**
     * 文档向量 — 沿用 {@link Document#getText()}(文本分支)。
     * <p>
     * Spring AI 标准 {@link Document} 不携带像素信息,因此本适配器与
     * {@link com.richie.component.ai.provider.bailian.BailianImageEmbeddingAdapter}
     * 行为一致:只走文本路径。真正的图像分支需要 Phase C 统一
     * {@code ImageEmbeddingModel} 抽象后另行暴露。
     */
    @Override
    public float[] embed(Document document) {
        Objects.requireNonNull(document, "document must not be null");
        String text = document.getText();
        if (text == null) {
            // 文档只携带 Media(图像),无可向量化的文本 — 返回空向量而不是抛错,
            // 让上层 vector store 仍然能走到写入流程;空向量会被上层识别为
            // "未成功向量化"条目,可由业务侧过滤。
            log.warn("TEI image-embedding: Document has no text (likely image-only), returning empty vector");
            return new float[0];
        }
        return embed(text);
    }

    /**
     * 维度 — 返回 {@code 0}(编译期未知)。
     * <p>
     * Spring AI 默认实现会调一次 {@code embed("Test String")} 去读返回向量的长度 —
     * 等价于每次业务侧获取 dimension 信息都会浪费一次 TEI 推理调用。TEI 的向量维度
     * 完全由部署模型决定(常见 384 / 768 / 1024 / 1536),这里保守返回 0,让业务侧
     * 自行决定是否需要运行时 probe。如果需要,可在调用方主动执行一次
     * {@code adapter.call(new EmbeddingRequest(List.of("probe"), null))},从响应向量长度
     * 推断维度。
     */
    @Override
    public int dimensions() {
        return 0;
    }

    // ====================== 内部工具 ======================

    /**
     * 去掉 baseUrl 末尾的 "/",避免拼接 {@code /v1/embeddings} 时出现双斜杠。
     */
    private static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return (end == url.length()) ? url : url.substring(0, end);
    }

    /**
     * 把 TEI 响应装配为 Spring AI {@link EmbeddingResponse}。
     * <p>
     * 关键保护(镜像 Bailian 风格):
     * <ul>
     *   <li>{@code data == null} 或 {@code data} 为空 → 返回空向量占位,数量与请求输入一致,
     *       让上层按"结果数量 = 请求数量"判定而不必做 null 检查。</li>
     *   <li>按响应中 {@code index} 字段重新排序(TEI 不保证顺序);缺失 index 时退化为
     *       按响应顺序填入。</li>
     *   <li>请求槽位缺失时补空向量占位(不抛 IndexOutOfBounds),保证 EmbeddingResponse 大小
     *       永远等于 {@code expectedInputs}。</li>
     * </ul>
     */
    private EmbeddingResponse toEmbeddingResponse(TeiEmbeddingRawResponse raw, int expectedInputs) {
        if (raw == null || raw.data == null || raw.data.isEmpty()) {
            log.warn("TEI image-embedding returned empty body: {}", JsonUtils.getInstance().serialize(raw));
            return new EmbeddingResponse(buildEmptyPlaceholders(expectedInputs));
        }

        // 决定"按 index 排序"还是"按位置排序":
        // - 所有非 null 项都带 index → 按 index
        // - 任意一项缺 index → 全部按位置(混合模式无法对齐)
        boolean allHaveIndex = true;
        int maxIndex = expectedInputs - 1;
        for (TeiEmbeddingDataItem item : raw.data) {
            if (item == null) {
                continue;
            }
            if (item.index == null) {
                allHaveIndex = false;
            } else if (item.index > maxIndex) {
                maxIndex = item.index;
            }
        }
        int slotSize = Math.max(expectedInputs, maxIndex + 1);
        float[][] slots = new float[slotSize][];
        int fallbackPos = 0;
        for (TeiEmbeddingDataItem item : raw.data) {
            if (item == null) {
                continue;
            }
            int idx = allHaveIndex && item.index != null ? item.index : fallbackPos;
            if (idx >= 0 && idx < slotSize) {
                slots[idx] = toFloatArray(item.embedding);
            }
            fallbackPos++;
        }

        List<Embedding> out = new ArrayList<>(expectedInputs);
        for (int i = 0; i < expectedInputs; i++) {
            float[] vec = (i < slotSize) ? slots[i] : null;
            // 缺失槽位补空向量 — 上层可通过"向量内容为空"判定"无意义条目"
            out.add(new Embedding(vec != null ? vec : new float[0], i));
        }
        return new EmbeddingResponse(out);
    }

    /**
     * 构造 n 个空向量占位 — 用于响应为空或 data 字段缺失时的兜底。
     */
    private static List<Embedding> buildEmptyPlaceholders(int n) {
        List<Embedding> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Embedding(new float[0], i));
        }
        return out;
    }

    /**
     * 把 TEI 返回的数字数组(可能含 String 或 Number)转 float[]。
     * 健壮性:空/全 null → 返回空数组;坏值 → 该槽位 0f。
     * <p>
     * 注意:这里无法像 Bailian 那样预分配 {@code DIMENSIONS} 长度的零向量 — TEI
     * 维度未知,只能返回与 vendor 实际返回长度一致的数组;上层如需固定维度,应自行
     * 填充。
     */
    private static float[] toFloatArray(List<Number> values) {
        if (values == null || values.isEmpty()) {
            return new float[0];
        }
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Number n = values.get(i);
            out[i] = (n == null) ? 0f : n.floatValue();
        }
        return out;
    }

    // ====== TEI / OpenAI-compatible response DTOs ======

    /**
     * 顶层响应:{@code { "data": [...], "model": "...", "usage": {...} }}。
     * 使用 public 字段以便 Jackson 在没有 setter 的情况下也能反序列化;
     * {@link JsonProperty} 显式声明是为了表达意图(OpenAI 协议字段名固定)。
     */
    static class TeiEmbeddingRawResponse {
        @JsonProperty("data")
        public List<TeiEmbeddingDataItem> data;

        @JsonProperty("model")
        public String model;

        @JsonProperty("usage")
        public TeiUsage usage;
    }

    /**
     * 单条 embedding 结果:{@code { "embedding": [...], "index": N }}。
     * {@code index} 在 OpenAI 协议下用于把响应条目对齐到请求中的输入位置 —
     * TEI 大多数部署会回带,极个别旧版本可能省略,本适配器对省略情况有兜底逻辑。
     */
    static class TeiEmbeddingDataItem {
        @JsonProperty("embedding")
        public List<Number> embedding;

        @JsonProperty("index")
        public Integer index;
    }

    /**
     * 用量统计:{@code { "prompt_tokens": N, "total_tokens": N }}。
     * 当前适配器未使用 — 保留字段供后续埋点 / 限流逻辑引用。
     */
    static class TeiUsage {
        @JsonProperty("prompt_tokens")
        public Integer promptTokens;

        @JsonProperty("total_tokens")
        public Integer totalTokens;
    }
}