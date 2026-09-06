/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.provider.bailian;

import cn.richie696.component.ai.api.image.ImageEmbeddingModel;
import cn.richie696.component.http.core.HttpClient;
import cn.richie696.context.utils.data.JsonUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 阿里百炼（DashScope）文本 Embedding 适配器。
 *
 * <p>走 DashScope 的 OpenAI 兼容入口
 * {@code POST {baseUrl}/embeddings}，请求体和响应体遵循 OpenAI embeddings 协议，
 * 业务可使用 {@code text-embedding-v3}、{@code text-embedding-v2}、{@code text-embedding-async-v2}
 * 等任一 DashScope 文本嵌入模型。{@code text-embedding-v3} 支持
 * 自定义输出维度（{@code dimensions} 取值 64..1024，必须是 8 的倍数），由 {@link #dimensions()} 返回
 * 配置值，让 Spring AI / 第三方 Provider Factory 在不显式声明维度时也能拿到真实大小。</p>
 *
 * <p>与 {@code /api/v1/services/embeddings/text-embedding/text-embedding} 不同，本适配器
 * 走 OpenAI 兼容入口，方便与 OpenAI SDK 客户端、Spring AI 抽象层集成。DashScope 也只在该入口
 * 暴露 {@code text-embedding-v3} 自定义维度能力。</p>
 */
public final class BailianTextEmbeddingAdapter implements ImageEmbeddingModel {

    /** DashScope OpenAI 兼容入口前缀。 */
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** DashScope 官方当前主推的文本嵌入模型。 */
    public static final String DEFAULT_MODEL = "text-embedding-v3";

    /**
     * 兜底维度，仅在调用方未通过 {@link #dimensions()} 配置且未从首条响应回填时使用。
     * 业务必须显式传入预期维度，避免和 Provider 维度漂移。
     */
    public static final int DEFAULT_DIMENSIONS = 1024;

    /**
     * {@code text-embedding-v3} 支持的自定义输出维度合法范围。
     * DashScope 限制为 64..1024 且必须是 8 的倍数。
     */
    public static final int MIN_DIMENSIONS = 64;
    public static final int MAX_DIMENSIONS = 1024;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final int configuredDimensions;
    private volatile int effectiveDimensions;
    private final Map<String, Object> requestParameters;

    public BailianTextEmbeddingAdapter(HttpClient httpClient, String apiKey, String baseUrl, String model,
                                      Integer dimensions, Map<String, Object> requestParameters) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.endpoint = resolveEndpoint(baseUrl);
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.configuredDimensions = resolveConfiguredDimensions(model, dimensions);
        this.effectiveDimensions = this.configuredDimensions;
        this.requestParameters = requestParameters == null ? Map.of() : Map.copyOf(requestParameters);
    }

    static int resolveConfiguredDimensions(String model, Integer dimensions) {
        if (dimensions == null) {
            return DEFAULT_DIMENSIONS;
        }
        if (!isTextEmbeddingV3(model)) {
            // v3 之外的模型不暴露自定义维度，按业务输入透传。
            return dimensions;
        }
        if (dimensions < MIN_DIMENSIONS || dimensions > MAX_DIMENSIONS || dimensions % 8 != 0) {
            throw new IllegalArgumentException(
                    "Bailian text-embedding-v3 dimensions must be between 64 and 1024 and a multiple of 8: "
                            + dimensions);
        }
        return dimensions;
    }

    static String resolveEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL + "/embeddings";
        }
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/embeddings")) {
            return normalized;
        }
        if (normalized.endsWith("/compatible-mode/v1")) {
            return normalized + "/embeddings";
        }
        return normalized + "/embeddings";
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> inputs = request.getInstructions();
        if (inputs == null || inputs.isEmpty()) {
            return new EmbeddingResponse(Collections.emptyList());
        }
        Map<String, Object> body = new LinkedHashMap<>(requestParameters);
        body.put("model", model);
        body.put("input", inputs);
        if (isTextEmbeddingV3(model)) {
            body.put("dimensions", configuredDimensions);
            body.put("encoding_format", "float");
        }
        String raw = httpClient.post(endpoint, body)
                .header("Authorization", "Bearer " + apiKey)
                .asJson()
                .header("Content-Type", "application/json")
                .execute().bodyAsString();
        List<float[]> vectors = parseVectors(raw);
        if (vectors.size() != inputs.size()) {
            throw new IllegalStateException(
                    "Bailian text embedding response count mismatch: expected=" + inputs.size()
                            + ", actual=" + vectors.size());
        }
        // 用首条响应回填实际维度，向下兼容 DashScope 内部维度变化。
        if (!vectors.isEmpty()) {
            effectiveDimensions = vectors.get(0).length;
        }
        List<Embedding> results = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            results.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document == null || document.getText() == null ? "" : document.getText());
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(text == null ? "" : text), null));
        if (response.getResults().isEmpty()) {
            throw new IllegalStateException("Bailian text embedding returned no vectors for the request");
        }
        return response.getResults().get(0).getOutput();
    }

    @Override
    public int dimensions() {
        return effectiveDimensions;
    }

    @Override
    public float[] embedImage(String imageUrlOrBase64) {
        throw new UnsupportedOperationException("Bailian text embedding does not support image input");
    }

    public int configuredDimensions() {
        return configuredDimensions;
    }

    public String model() {
        return model;
    }

    private static boolean isTextEmbeddingV3(String model) {
        return model != null && model.toLowerCase(java.util.Locale.ROOT).startsWith("text-embedding-v3");
    }

    @SuppressWarnings("unchecked")
    private List<float[]> parseVectors(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Bailian text embedding response is empty");
        }
        Map<String, Object> root = JsonUtils.getInstance().deserialize(raw, Map.class);
        if (root == null || !(root.get("data") instanceof List<?> data)) {
            throw new IllegalStateException("Bailian text embedding response is missing data array");
        }
        List<float[]> vectors = new ArrayList<>();
        for (Object item : data) {
            if (!(item instanceof Map<?, ?> map) || !(map.get("embedding") instanceof List<?> values)) {
                throw new IllegalStateException(
                        "Bailian text embedding response contains an invalid data item");
            }
            if (values.isEmpty()) {
                throw new IllegalStateException("Bailian text embedding response contains an empty vector");
            }
            float[] vector = new float[values.size()];
            double squaredNorm = 0.0;
            for (int i = 0; i < values.size(); i++) {
                if (!(values.get(i) instanceof Number number) || !Float.isFinite(number.floatValue())) {
                    throw new IllegalStateException(
                            "Bailian text embedding response contains a non-finite vector value");
                }
                vector[i] = number.floatValue();
                squaredNorm += vector[i] * vector[i];
            }
            if (!(squaredNorm > 0.0) || !Double.isFinite(squaredNorm)) {
                throw new IllegalStateException("Bailian text embedding response contains a zero vector");
            }
            vectors.add(vector);
        }
        return vectors;
    }
}
