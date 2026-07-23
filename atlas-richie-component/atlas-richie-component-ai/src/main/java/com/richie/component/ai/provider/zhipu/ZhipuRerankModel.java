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
package com.richie.component.ai.provider.zhipu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;
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
 * 智谱 AI（BigModel）重排序模型适配器。
 * <p>
 * 调用智谱 BigModel 开放接口 {@code POST https://open.bigmodel.cn/api/paas/v4/rerank}，
 * Bearer 鉴权，请求体形态：
 * <pre>{@code
 * { "model": "rerank",
 *   "query": "...",
 *   "documents": [ ... ],
 *   "top_n": 5,
 *   "return_documents": true }
 * }</pre>
 * 响应体形态（Cohere 兼容）：
 * <pre>{@code
 * { "results": [ { "index": 0, "relevance_score": 0.97, "document": "..." }, ... ],
 *   "usage": { ... } }
 * }</pre>
 * 与 {@code BailianRerankModel} 共用同一 {@link RerankResult} 抽象，
 * 业务侧只看 vendor 字段即可水平切换。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class ZhipuRerankModel implements RerankModel {

    /** 智谱 Rerank REST 端点（{@code /paas/v4/rerank}）。 */
    public static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4/rerank";

    /** 当 {@link RerankRequest#model} 为空时使用的默认模型（{@code rerank}，固定枚举）。 */
    public static final String DEFAULT_MODEL = "rerank";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;

    /**
     * 构造器：从 {@link RerankModelConfig} 提取鉴权 / 端点 / 默认模型字段。
     *
     * @param httpClient HTTP 客户端
     * @param cfg        重排序模型配置（{@code RerankModelConfig#getApiKey()} 提供 Bearer apiKey，
     *                   {@code RerankModelConfig#getBaseUrl()} 提供可选端点覆盖，
     *                   {@code RerankModelConfig#getModel()} 提供可选默认模型）
     * @throws NullPointerException 当 {@code httpClient} 或 {@code cfg} 为 null 时
     */
    public ZhipuRerankModel(HttpClient httpClient, RerankModelConfig cfg) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        Objects.requireNonNull(cfg, "cfg must not be null");
        this.apiKey = Objects.requireNonNull(cfg.getApiKey(), "apiKey must not be null");
        this.baseUrl = (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank())
                ? DEFAULT_BASE_URL
                : cfg.getBaseUrl();
        this.defaultModel = (cfg.getModel() == null || cfg.getModel().isBlank())
                ? DEFAULT_MODEL
                : cfg.getModel();
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        long start = System.currentTimeMillis();
        try {
            RerankResponse resp = rerankAsync(request).get();
            return resp.withDuration(System.currentTimeMillis() - start);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Zhipu rerank interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Zhipu rerank failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<RerankResponse> rerankAsync(RerankRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getQuery(), "query must not be null");
        Objects.requireNonNull(request.getDocuments(), "documents must not be null");

        Map<String, Object> body = buildRequestBody(request);
        long start = System.currentTimeMillis();

        return httpClient.post(baseUrl, body)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .future(ZhipuRerankRawResponse.class)
                .thenApply(raw -> RerankResponse.succeed(toResults(raw), Clock.systemUTC())
                        .withDuration(System.currentTimeMillis() - start));
    }

    /**
     * 把 {@link RerankRequest} 装配为智谱 Rerank 请求体。
     * <p>
     * 字段命名遵循智谱 BigModel 官方约定：
     * <ul>
     *   <li>{@code model}：优先取 {@link RerankRequest#getModel()}，否则用
     *       构造期固定的 {@link #defaultModel}（{@link #DEFAULT_MODEL} 或配置覆盖）</li>
     *   <li>{@code top_n}：仅当 {@link RerankRequest#getTopN()} 非空且 &gt;0 时输出</li>
     *   <li>{@code return_documents}：固定 {@code true}（让响应回带原文档，
     *       上层可直接读取 {@link RerankResult#getDocument()}）</li>
     * </ul>
     */
    Map<String, Object> buildRequestBody(RerankRequest request) {
        String model = (request.getModel() == null || request.getModel().isBlank())
                ? defaultModel
                : request.getModel();

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("query", request.getQuery());
        body.put("documents", request.getDocuments());
        body.put("return_documents", Boolean.TRUE);
        if (request.getTopN() != null && request.getTopN() > 0) {
            body.put("top_n", request.getTopN());
        }
        return body;
    }

    /**
     * 把智谱响应装配为按相关性降序的 {@link RerankResult} 列表。
     * 缺失 {@code results} 时回退为空列表（而不是抛错）——上层可以基于空结果自行决策。
     */
    private List<RerankResult> toResults(ZhipuRerankRawResponse raw) {
        if (raw == null || raw.results == null) {
            log.warn("Zhipu rerank returned empty body: {}", JsonUtils.getInstance().serialize(raw));
            return Collections.emptyList();
        }
        List<RerankResult> out = new ArrayList<>(raw.results.size());
        for (ZhipuRerankRawItem item : raw.results) {
            if (item == null) {
                continue;
            }
            out.add(new RerankResult(item.index, item.document, item.relevanceScore));
        }
        return out;
    }

    // ====== Zhipu response DTOs ======

    /** 顶层响应：{@code { "results": [...], "usage": { ... } }}。 */
    static class ZhipuRerankRawResponse {
        @JsonProperty("results")
        public List<ZhipuRerankRawItem> results;
    }

    /**
     * 单条结果：{@code { "index": N, "relevance_score": F, "document": "..." }}。
     * 使用 public 字段以便 Jackson 在没有 setter 的情况下也能反序列化；{@link JsonProperty}
     * 显式声明是为了表达意图（智谱当前返回 snake_case → camelCase 字段映射）。
     */
    static class ZhipuRerankRawItem {
        @JsonProperty("index")
        public int index;

        @JsonProperty("relevance_score")
        public double relevanceScore;

        @JsonProperty("document")
        public String document;
    }
}
