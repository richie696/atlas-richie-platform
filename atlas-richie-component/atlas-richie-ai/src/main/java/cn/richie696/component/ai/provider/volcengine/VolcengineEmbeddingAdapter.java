/*
 * Copyright (c) 2026 Richie
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.provider.volcengine;

import cn.richie696.component.ai.api.image.ImageEmbeddingModel;
import cn.richie696.component.http.core.HttpClient;
import cn.richie696.component.http.core.HttpResponse;
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
 * 火山方舟图文向量化适配器。
 *
 * <p>Ark 的多模态 Embedding 不是 OpenAI {@code /embeddings} 结构：端点为
 * {@code /embeddings/multimodal}，输入是 {@code [{type,text|image_url}]}，
 * 返回 {@code data[*].embedding}（向量数组可能再包一层）。</p>
 */
public final class VolcengineEmbeddingAdapter implements ImageEmbeddingModel {

    public static final String DEFAULT_BASE_URL =
            "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal";
    public static final String DEFAULT_MODEL = "doubao-embedding-vision-241215";
    public static final int DIMENSIONS = 1024;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final Map<String, Object> requestParameters;

    public VolcengineEmbeddingAdapter(HttpClient httpClient, String apiKey, String baseUrl, String model) {
        this(httpClient, apiKey, baseUrl, model, Map.of());
    }

    public VolcengineEmbeddingAdapter(HttpClient httpClient,
                                      String apiKey,
                                      String baseUrl,
                                      String model,
                                      Map<String, Object> requestParameters) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.endpoint = resolveEndpoint(baseUrl);
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.requestParameters = requestParameters == null ? Map.of() : Map.copyOf(requestParameters);
    }

    static String resolveEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return DEFAULT_BASE_URL;
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/embeddings/multimodal")) return normalized;
        return normalized + "/embeddings/multimodal";
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> inputs = request.getInstructions();
        if (inputs == null || inputs.isEmpty()) return new EmbeddingResponse(Collections.emptyList());
        List<Map<String, Object>> input = new ArrayList<>(inputs.size());
        for (String value : inputs) {
            input.add(Map.of("type", "text", "text", value == null ? "" : value));
        }
        Map<String, Object> body = new LinkedHashMap<>(requestParameters);
        body.put("model", model);
        body.put("input", input);
        HttpResponse response = httpClient.post(endpoint, body)
                .header("Authorization", "Bearer " + apiKey)
                // HttpClient requires its JSON codec to serialize a Map body.
                // A Content-Type header alone can produce a non-JSON Ark request.
                .asJson()
                .header("Content-Type", "application/json")
                .execute();
        String raw = requireSuccessfulResponse(response);
        return toResponse(raw, inputs.size());
    }

    @Override
    public float[] embed(Document document) {
        Objects.requireNonNull(document, "document must not be null");
        return embed(document.getText() == null ? "" : document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embedImage(String imageUrlOrBase64) {
        Objects.requireNonNull(imageUrlOrBase64, "imageUrlOrBase64 must not be null");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "image_url");
        content.put("image_url", Map.of("url", imageUrlOrBase64));
        Map<String, Object> body = new LinkedHashMap<>(requestParameters);
        body.put("model", model);
        body.put("input", List.of(content));
        HttpResponse response = httpClient.post(endpoint, body)
                .header("Authorization", "Bearer " + apiKey)
                .asJson()
                .header("Content-Type", "application/json")
                .execute();
        String raw = requireSuccessfulResponse(response);
        List<float[]> vectors = parseVectors(raw);
        if (vectors.size() != 1) {
            throw new IllegalStateException("Volcengine embedding response must contain exactly one vector, actual=" + vectors.size());
        }
        return vectors.get(0);
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = call(new EmbeddingRequest(List.of(text), null));
        return response.getResults().isEmpty() ? new float[DIMENSIONS] : response.getResults().get(0).getOutput();
    }

    private EmbeddingResponse toResponse(String raw, int expected) {
        List<float[]> vectors = parseVectors(raw);
        if (vectors.size() != expected) {
            throw new IllegalStateException("Volcengine embedding response count mismatch: expected=" + expected + ", actual=" + vectors.size());
        }
        List<Embedding> results = new ArrayList<>(expected);
        for (int i = 0; i < expected; i++) {
            results.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(results);
    }

    @SuppressWarnings("unchecked")
    private String requireSuccessfulResponse(HttpResponse response) {
        String raw = response.bodyAsString();
        if (response.isSuccessful()) return raw;
        String detail = "";
        try {
            Map<String, Object> payload = JsonUtils.getInstance().deserialize(raw, Map.class);
            detail = providerError(payload);
        } catch (RuntimeException ignored) {
            // Never include raw provider bodies in exceptions: they can contain request diagnostics.
        }
        throw new IllegalStateException("Volcengine embedding request failed: http=" + response.statusCode() + detail);
    }

    private static String providerError(Map<String, Object> payload) {
        Object error = payload == null ? null : payload.get("error");
        Map<?, ?> errorMap = error instanceof Map<?, ?> map ? map : payload;
        if (errorMap == null) return "";
        Object code = errorMap.get("code");
        Object message = errorMap.get("message");
        if (code == null && message == null) return "";
        String normalized = message == null ? "" : String.valueOf(message).replaceAll("[\\r\\n]", " ");
        if (normalized.length() > 240) normalized = normalized.substring(0, 240);
        return (code == null ? "" : " code=" + code) + (normalized.isBlank() ? "" : " message=" + normalized);
    }

    @SuppressWarnings("unchecked")
    private List<float[]> parseVectors(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Volcengine embedding response is empty");
        }
        Map<String, Object> root = JsonUtils.getInstance().deserialize(raw, Map.class);
        if (root == null) throw new IllegalStateException("Volcengine embedding response is empty");
        Object data = root.get("data");
        List<float[]> vectors = new ArrayList<>();
        if (data instanceof Map<?, ?> item) {
            appendEmbedding(vectors, item);
        } else if (data instanceof List<?> items) {
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new IllegalStateException("Volcengine embedding response contains an invalid data item");
                }
                appendEmbedding(vectors, map);
            }
        } else {
            throw new IllegalStateException("Volcengine embedding response is missing data" + providerError(root));
        }
        return vectors;
    }

    private void appendEmbedding(List<float[]> vectors, Map<?, ?> item) {
        Object embedding = item.get("embedding");
        if (!(embedding instanceof List<?> values)) {
            throw new IllegalStateException("Volcengine embedding response is missing embedding values");
        }
        if (!values.isEmpty() && values.get(0) instanceof List<?>) {
            for (Object nested : values) vectors.add(toVector((List<?>) nested));
        } else {
            vectors.add(toVector(values));
        }
    }

    private float[] toVector(List<?> values) {
        if (values.isEmpty()) {
            throw new IllegalStateException("Volcengine embedding response contains an empty vector");
        }
        float[] vector = new float[values.size()];
        double squaredNorm = 0.0;
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (!(value instanceof Number number) || !Float.isFinite(number.floatValue())) {
                throw new IllegalStateException("Volcengine embedding response contains a non-finite vector value");
            }
            vector[i] = number.floatValue();
            squaredNorm += vector[i] * vector[i];
        }
        if (!(squaredNorm > 0.0) || !Double.isFinite(squaredNorm)) {
            throw new IllegalStateException("Volcengine embedding response contains a zero vector");
        }
        return vector;
    }
}
