/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.ai.provider.volcengine;

import cn.richie696.component.ai.api.RerankModel;
import cn.richie696.component.ai.api.RerankRequest;
import cn.richie696.component.ai.api.RerankResponse;
import cn.richie696.component.ai.api.RerankResult;
import cn.richie696.component.ai.config.multimodal.rerank.RerankModelConfig;
import cn.richie696.component.http.core.HttpClient;
import cn.richie696.context.utils.data.JsonUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 火山引擎新版 Viking AI Search 精排适配器。
 *
 * <p>该接口不是旧 VikingDB 文本重排协议，也不是 OpenAI/Cohere 兼容协议：
 * 请求通过 application + scene 路径定位策略，候选项使用 {@code items.items[]._id}，
 * 响应在 {@code result.rec_results} 中返回 {@code _id} 和 {@code score}。
 * 因此调用方必须提供与 documents 一一对应的 {@link RerankRequest#getDocumentIds()}。</p>
 */
@Slf4j
public class VikingAiSearchRerankModel implements RerankModel {

    public static final String DEFAULT_BASE_URL = "https://aisearch.cn-beijing.volces.com";
    public static final String DEFAULT_REGION = "cn-beijing";
    public static final String DEFAULT_API_VERSION = "v1";
    public static final int MAX_CANDIDATES = 400;

    private final HttpClient httpClient;
    private final String apiKey;
    private final String endpoint;
    private final Map<String, Object> requestParameters;
    private final String defaultUserId;

    public VikingAiSearchRerankModel(HttpClient httpClient, RerankModelConfig cfg) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        Objects.requireNonNull(cfg, "cfg must not be null");
        this.apiKey = requireText(cfg.getApiKey(), "apiKey");
        String applicationId = firstNonBlank(cfg.getApplicationId(), value(cfg.getRequestParameters(), "application_id"));
        String sceneId = firstNonBlank(cfg.getSceneId(), value(cfg.getRequestParameters(), "scene_id"));
        if (applicationId == null || sceneId == null) {
            throw new IllegalArgumentException("Viking AI Search Rerank 必须配置 applicationId 和 sceneId");
        }
        String base = firstNonBlank(cfg.getEndpoint(), cfg.getBaseUrl());
        this.endpoint = resolveEndpoint(base, cfg.getRegion(), applicationId, sceneId);
        this.requestParameters = cfg.getRequestParameters() == null
                ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(cfg.getRequestParameters()));
        this.defaultUserId = cfg.getUserId();
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        long start = System.currentTimeMillis();
        try {
            return rerankAsync(request).get().withDuration(System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Viking AI Search rerank interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Viking AI Search rerank failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<RerankResponse> rerankAsync(RerankRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> documents = Objects.requireNonNull(request.getDocuments(), "documents must not be null");
        List<String> ids = request.getDocumentIds();
        if (ids == null || ids.size() != documents.size() || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("Viking AI Search Rerank 要求 documentIds 与 documents 数量一致且不能为空");
        }
        if (ids.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Viking AI Search Rerank 单次候选项不能超过 " + MAX_CANDIDATES + " 条");
        }
        Map<String, Object> body = buildRequestBody(request);
        long start = System.currentTimeMillis();
        return httpClient.post(endpoint, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .future(VikingRerankRawResponse.class)
                .thenApply(raw -> RerankResponse.succeed(toResults(raw, ids, documents), Clock.systemUTC())
                        .withDuration(System.currentTimeMillis() - start));
    }

    Map<String, Object> buildRequestBody(RerankRequest request) {
        List<String> ids = Objects.requireNonNull(request.getDocumentIds(), "documentIds must not be null");
        Map<String, Object> body = new LinkedHashMap<>(requestParameters);
        String userId = firstNonBlank(request.getUserId(), defaultUserId);
        if (userId != null) {
            body.put("user", Map.of("_user_id", userId));
        }
        if (request.getTopN() != null && request.getTopN() > 0) {
            body.put("page_size", request.getTopN());
        }
        List<Map<String, String>> items = ids.stream()
                .map(id -> Map.of("_id", id))
                .toList();
        body.put("items", Map.of("items", items, "weight", 1.0));
        return body;
    }

    private List<RerankResult> toResults(VikingRerankRawResponse raw, List<String> ids, List<String> documents) {
        if (raw == null || raw.result == null || raw.result.recResults == null) {
            log.warn("Viking AI Search rerank returned empty body: {}", JsonUtils.getInstance().serialize(raw));
            return Collections.emptyList();
        }
        List<RerankResult> results = new ArrayList<>(raw.result.recResults.size());
        for (VikingRawResult item : raw.result.recResults) {
            if (item == null || item.id == null) continue;
            int index = ids.indexOf(item.id);
            if (index >= 0) {
                results.add(new RerankResult(index, documents.get(index), item.score));
            }
        }
        return results;
    }

    private static String resolveEndpoint(String configured, String region, String applicationId, String sceneId) {
        String base = firstNonBlank(configured, regionBaseUrl(region));
        if (base.endsWith("/rerank")) return base;
        return trimTrailingSlash(base) + "/api/" + DEFAULT_API_VERSION + "/application/"
                + encode(applicationId) + "/" + encode(sceneId) + "/rerank";
    }

    private static String regionBaseUrl(String region) {
        String effective = firstNonBlank(region, DEFAULT_REGION);
        return "https://aisearch." + effective + ".volces.com";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }

    private static String value(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second != null && !second.isBlank() ? second : null);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    static class VikingAiSearchResultContainer {
        @JsonProperty("rec_results")
        public List<VikingRawResult> recResults;
    }

    static class VikingRerankRawResponse {
        @JsonProperty("result")
        public VikingAiSearchResultContainer result;
    }

    static class VikingRawResult {
        @JsonProperty("_id")
        public String id;
        @JsonProperty("score")
        public double score;
    }
}
