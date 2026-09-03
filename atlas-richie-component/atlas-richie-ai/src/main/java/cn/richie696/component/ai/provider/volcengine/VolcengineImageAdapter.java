/* Copyright (c) 2026 Richie. Licensed under the Apache License, Version 2.0. */
package cn.richie696.component.ai.provider.volcengine;

import cn.richie696.component.http.core.HttpClient;
import cn.richie696.context.utils.data.JsonUtils;
import jakarta.annotation.Nonnull;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 火山方舟 Seedream 图片生成适配器。 */
public final class VolcengineImageAdapter implements ImageModel {
    public static final String DEFAULT_BASE_URL =
            "https://ark.cn-beijing.volces.com/api/v3/images/generations";
    public static final String DEFAULT_MODEL = "doubao-seedream-4-0-250828";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final String defaultModel;
    private final Map<String, Object> requestParameters;

    public VolcengineImageAdapter(HttpClient httpClient, String apiKey, String baseUrl, String model) {
        this(httpClient, apiKey, baseUrl, model, Map.of());
    }

    public VolcengineImageAdapter(HttpClient httpClient,
                                  String apiKey,
                                  String baseUrl,
                                  String model,
                                  Map<String, Object> requestParameters) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.endpoint = resolveEndpoint(baseUrl);
        this.defaultModel = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        this.requestParameters = requestParameters == null ? Map.of() : Map.copyOf(requestParameters);
    }

    static String resolveEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return DEFAULT_BASE_URL;
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        if (normalized.endsWith("/images/generations")) return normalized;
        return normalized + "/images/generations";
    }

    @Nonnull
    @Override
    public ImageResponse call(@Nonnull ImagePrompt request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, Object> body = new LinkedHashMap<>(requestParameters);
        body.put("model", resolveModel(request.getOptions()));
        body.put("prompt", joinPrompt(request));
        ImageOptions options = request.getOptions();
        if (options != null && options.getN() != null) body.put("sequential_image_generation", options.getN() > 1 ? "auto" : "disabled");
        String raw = httpClient.post(endpoint, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .execute()
                .bodyAsString();
        return toResponse(raw);
    }

    private String resolveModel(ImageOptions options) {
        return options != null && options.getModel() != null && !options.getModel().isBlank()
                ? options.getModel() : defaultModel;
    }

    private String joinPrompt(ImagePrompt request) {
        return request.getInstructions().stream().filter(Objects::nonNull).map(m -> m.getText()).filter(Objects::nonNull).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    @SuppressWarnings("unchecked")
    private ImageResponse toResponse(String raw) {
        Map<String, Object> root = raw == null ? null : JsonUtils.getInstance().deserialize(raw, Map.class);
        List<ImageGeneration> results = new ArrayList<>();
        if (root != null && root.get("data") instanceof List<?> data) {
            for (Object item : data) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Object url = map.get("url");
                if (url instanceof String value && !value.isBlank()) results.add(new ImageGeneration(new Image(value, null)));
            }
        }
        return new ImageResponse(results);
    }
}
