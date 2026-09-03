/*
 * Copyright (c) 2026 Richie
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.provider.volcengine;

import cn.richie696.component.http.core.HttpClient;
import cn.richie696.component.ai.api.image.ImageEmbeddingModel;
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
 * 火山方舟文本 Embedding 适配器。
 *
 * <p>与图文 Embedding 的 {@code /embeddings/multimodal} 不同，文本接口使用
 * {@code /embeddings}，且 input 是字符串数组。不能复用 OpenAI 适配器猜测该协议。</p>
 */
public final class VolcengineTextEmbeddingAdapter implements ImageEmbeddingModel {

    public static final String DEFAULT_BASE_URL =
            "https://ark.cn-beijing.volces.com/api/v3/embeddings";
    public static final int DEFAULT_DIMENSIONS = 1024;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final Map<String, Object> requestParameters;

    public VolcengineTextEmbeddingAdapter(HttpClient httpClient, String apiKey, String baseUrl, String model,
                                          Map<String, Object> requestParameters) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.endpoint = resolveEndpoint(baseUrl);
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.requestParameters = requestParameters == null ? Map.of() : Map.copyOf(requestParameters);
    }

    static String resolveEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return DEFAULT_BASE_URL;
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        return normalized.endsWith("/embeddings") ? normalized : normalized + "/embeddings";
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> inputs = request.getInstructions();
        if (inputs == null || inputs.isEmpty()) return new EmbeddingResponse(Collections.emptyList());
        Map<String, Object> body = new LinkedHashMap<>(requestParameters);
        body.put("model", model);
        body.put("input", inputs);
        String raw = httpClient.post(endpoint, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .execute().bodyAsString();
        List<float[]> vectors = parseVectors(raw);
        List<Embedding> results = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            results.add(new Embedding(i < vectors.size() ? vectors.get(i) : new float[DEFAULT_DIMENSIONS], i));
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
        return response.getResults().isEmpty() ? new float[DEFAULT_DIMENSIONS]
                : response.getResults().get(0).getOutput();
    }

    @Override
    public int dimensions() {
        return DEFAULT_DIMENSIONS;
    }

    @Override
    public float[] embedImage(String imageUrlOrBase64) {
        throw new UnsupportedOperationException("火山方舟文本 Embedding 不支持图片输入");
    }

    @SuppressWarnings("unchecked")
    private List<float[]> parseVectors(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        Map<String, Object> root = JsonUtils.getInstance().deserialize(raw, Map.class);
        if (root == null || !(root.get("data") instanceof List<?> data)) return List.of();
        List<float[]> vectors = new ArrayList<>();
        for (Object item : data) {
            if (!(item instanceof Map<?, ?> map) || !(map.get("embedding") instanceof List<?> values)) continue;
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) instanceof Number number) vector[i] = number.floatValue();
            }
            vectors.add(vector);
        }
        return vectors;
    }
}
