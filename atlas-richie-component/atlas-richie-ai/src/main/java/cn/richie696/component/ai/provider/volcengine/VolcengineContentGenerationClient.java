/*
 * Copyright (c) 2026 Richie
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.provider.volcengine;

import cn.richie696.component.http.core.HttpClient;
import cn.richie696.context.utils.data.JsonUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 火山方舟内容/视频/3D 生成任务协议客户端。
 *
 * <p>该能力不是同步 ImageModel：创建任务使用
 * {@code POST /contents/generations/tasks}，随后以任务 ID 查询状态。因此单独提供
 * client，避免把异步任务协议错误地伪装成图片或 OpenAI Chat 适配器。</p>
 */
public final class VolcengineContentGenerationClient {

    public static final String DEFAULT_ENDPOINT =
            "https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;

    public VolcengineContentGenerationClient(HttpClient httpClient, String apiKey, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.endpoint = resolveEndpoint(baseUrl);
    }

    static String resolveEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return DEFAULT_ENDPOINT;
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        return normalized.endsWith("/contents/generations/tasks")
                ? normalized : normalized + "/contents/generations/tasks";
    }

    /** 创建内容生成任务；content 项由厂商协议定义为 text 或 image_url。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createTask(String model, List<Map<String, Object>> content,
                                           Map<String, Object> parameters) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (parameters != null) body.putAll(parameters);
        body.put("model", Objects.requireNonNull(model, "model must not be null"));
        body.put("content", content == null ? List.of() : content);
        return executePost(endpoint, body);
    }

    /** 查询异步任务状态。 */
    public Map<String, Object> getTask(String taskId) {
        String id = Objects.requireNonNull(taskId, "taskId must not be null").trim();
        if (id.isBlank()) throw new IllegalArgumentException("taskId must not be blank");
        var response = httpClient.get(endpoint + "/" + id)
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(15))
                .execute();
        return parseResponse(response.statusCode(), response.bodyAsString());
    }

    private Map<String, Object> executePost(String url, Map<String, Object> body) {
        var response = httpClient.post(url, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .asJson()
                .timeout(Duration.ofSeconds(30))
                .execute();
        return parseResponse(response.statusCode(), response.bodyAsString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(int status, String raw) {
        Map<String, Object> parsed = JsonUtils.getInstance().deserialize(raw, Map.class);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Volcengine Ark content request failed: HTTP " + status
                    + "; body=" + (raw == null ? "" : raw.substring(0, Math.min(raw.length(), 500))));
        }
        return parsed == null ? Map.of() : parsed;
    }
}
