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
import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.http.core.HttpClient;
import com.richie.context.utils.data.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 阿里百炼（DashScope）重排序模型适配器。
 * <p>
 * 调用 DashScope 文本重排 REST 接口
 * {@code https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}，
 * 请求体形态（类 Cohere）：
 * <pre>{@code
 * { "model": "gte-rerank",
 *   "input": { "query": "...", "documents": [ ... ] },
 *   "parameters": { "top_n": 5 } }
 * }</pre>
 * 响应体形态：
 * <pre>{@code
 * { "output": { "results": [ { "index": 0, "relevance_score": 0.97, "document": "..." }, ... ] } }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class BailianRerankModel implements RerankModel {

    /** DashScope 文本重排 REST 端点。 */
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    /** 当 {@link RerankRequest#model} 为空时使用的默认模型。 */
    public static final String DEFAULT_MODEL = "gte-rerank";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;

    public BailianRerankModel(HttpClient httpClient, String apiKey, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        long start = System.currentTimeMillis();
        try {
            RerankResponse resp = rerankAsync(request).get();
            return resp.withDuration(System.currentTimeMillis() - start);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Bailian rerank interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Bailian rerank failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<RerankResponse> rerankAsync(RerankRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getQuery(), "query must not be null");
        Objects.requireNonNull(request.getDocuments(), "documents must not be null");

        // Map → JSON 序列化由 http-core 内部的 HttpRequestSupport.serializeBody 完成；
        // 默认 Content-Type 已为 application/json，无需再调用 .asJson()。
        Map<String, Object> body = buildRequestBody(request);

        long start = System.currentTimeMillis();

        return httpClient.post(baseUrl, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .future(RerankRawResponse.class)
                .thenApply(raw -> RerankResponse.succeed(toResults(raw), Clock.systemUTC())
                        .withDuration(System.currentTimeMillis() - start));
    }

    /**
     * 把 {@link RerankRequest} 装配为 DashScope 的请求体（嵌套 Map）。
     * 用 Map 而非手写 JSON 字符串，是为了让结构变更集中且依赖 Jackson 的稳定转义。
     */
    private Map<String, Object> buildRequestBody(RerankRequest request) {
        String model = (request.getModel() == null || request.getModel().isBlank())
                ? DEFAULT_MODEL
                : request.getModel();

        Map<String, Object> input = new HashMap<>();
        input.put("query", request.getQuery());
        input.put("documents", request.getDocuments());

        Map<String, Object> parameters = new HashMap<>();
        if (request.getTopN() != null && request.getTopN() > 0) {
            parameters.put("top_n", request.getTopN());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", input);
        if (!parameters.isEmpty()) {
            body.put("parameters", parameters);
        }
        return body;
    }

    /**
     * 把 DashScope 响应装配为按相关性降序的 {@link RerankResult} 列表。
     * 缺失 output / results 时回退为空列表（而不是抛错）——上层可以基于空结果自行决策。
     */
    private List<RerankResult> toResults(RerankRawResponse raw) {
        if (raw == null || raw.output == null || raw.output.results == null) {
            log.warn("Bailian rerank returned empty body: {}", JsonUtils.getInstance().serialize(raw));
            return Collections.emptyList();
        }
        List<RerankResult> out = new ArrayList<>(raw.output.results.size());
        for (RerankRawItem item : raw.output.results) {
            if (item == null) {
                continue;
            }
            out.add(new RerankResult(item.index, item.document, item.relevanceScore));
        }
        return out;
    }

    // ====== DashScope response DTOs ======

    /** 顶层响应：{@code { "output": { "results": [...] } }}。 */
    static class RerankRawResponse {
        public RerankRawOutput output;
    }

    /** {@code output} 子对象。 */
    static class RerankRawOutput {
        public List<RerankRawItem> results;
    }

    /**
     * 单条结果：{@code { "index": N, "relevance_score": F, "document": "..." }}。
     * 使用 public 字段以便 Jackson 在没有 setter 的情况下也能反序列化；{@link JsonProperty}
     * 显式声明是因为 DashScope 返回 snake_case 而 Java 字段为 camelCase。
     */
    static class RerankRawItem {
        @JsonProperty("index")
        public int index;

        @JsonProperty("relevance_score")
        public double relevanceScore;

        @JsonProperty("document")
        public String document;
    }
}