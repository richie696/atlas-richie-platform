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
package com.richie.component.ai.provider.pangu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;
import com.richie.component.ai.provider.sign.AppCodeSigner;
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
 * 华为盘古大模型重排序模型适配器。
 * <p>
 * 调用华为云盘古 API 网关转发后的独立 Rerank REST 接口，
 * 默认端点 {@code POST https://pangu.open.huawei.com/api/v1/rerank}，
 * AppCode 鉴权（HTTP 头 {@code X-Apig-AppCode}），与本组件语音端的
 * {@code AppCodeSigner} 复用同一个鉴权头部工具，保证盘古市场版多能力
 * 凭据共享。
 * <p>
 * 请求体形态：
 * <pre>{@code
 * { "model": "pangu-rerank",
 *   "query": "...",
 *   "documents": [ ... ],
 *   "top_k": 5 }
 * }</pre>
 * 响应体形态（Cohere 兼容）：
 * <pre>{@code
 * { "results": [ { "index": 0, "relevance_score": 0.97 }, ... ] }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class PanguRerankModel implements RerankModel {

    /** 华为盘古 Rerank REST 默认端点（{@code pangu.open.huawei.com/api/v1/rerank}）。 */
    public static final String DEFAULT_BASE_URL = "https://pangu.open.huawei.com/api/v1/rerank";

    /** 当 {@link RerankRequest#model} 为空时使用的默认模型（{@code pangu-rerank}）。 */
    public static final String DEFAULT_MODEL = "pangu-rerank";

    private final HttpClient httpClient;
    private final String appCode;
    private final String baseUrl;
    private final String defaultModel;

    /**
     * 构造器：从 {@link RerankModelConfig} 提取鉴权 / 端点 / 默认模型字段。
     *
     * @param httpClient HTTP 客户端
     * @param cfg        重排序模型配置（{@code RerankModelConfig#getAppCode()} 提供盘古 AppCode，
     *                   {@code RerankModelConfig#getBaseUrl()} 提供可选端点覆盖，
     *                   {@code RerankModelConfig#getModel()} 提供可选默认模型）
     * @throws NullPointerException 当 {@code httpClient} 或 {@code cfg} 为 null 时
     */
    public PanguRerankModel(HttpClient httpClient, RerankModelConfig cfg) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        Objects.requireNonNull(cfg, "cfg must not be null");
        this.appCode = Objects.requireNonNull(cfg.getAppCode(), "appCode must not be null");
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
            throw new RuntimeException("Pangu rerank interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Pangu rerank failed: " + e.getMessage(), e);
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
                .header(AppCodeSigner.HEADER_APP_CODE, appCode)
                .header("Content-Type", "application/json")
                .future(PanguRerankRawResponse.class)
                .thenApply(raw -> RerankResponse.succeed(toResults(raw), Clock.systemUTC())
                        .withDuration(System.currentTimeMillis() - start));
    }

    /**
     * 把 {@link RerankRequest} 装配为盘古 Rerank 请求体。
     * <p>
     * 字段命名遵循盘古 OpenAPI 约定：
     * <ul>
     *   <li>{@code model}：优先取 {@link RerankRequest#getModel()}，否则用
     *       构造期固定的 {@link #defaultModel}（{@link #DEFAULT_MODEL} 或配置覆盖）</li>
     *   <li>{@code top_k}：仅当 {@link RerankRequest#getTopN()} 非空且 &gt;0 时输出
     *       （盘古 OpenAPI 使用 {@code top_k} 命名，与 DashScope 的 {@code top_n} 不同）</li>
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
        if (request.getTopN() != null && request.getTopN() > 0) {
            body.put("top_k", request.getTopN());
        }
        return body;
    }

    /**
     * 把盘古响应装配为按相关性降序的 {@link RerankResult} 列表。
     * 缺失 {@code results} 时回退为空列表（而不是抛错）——上层可以基于空结果自行决策。
     */
    private List<RerankResult> toResults(PanguRerankRawResponse raw) {
        if (raw == null || raw.results == null) {
            log.warn("Pangu rerank returned empty body: {}", JsonUtils.getInstance().serialize(raw));
            return Collections.emptyList();
        }
        List<RerankResult> out = new ArrayList<>(raw.results.size());
        for (PanguRerankRawItem item : raw.results) {
            if (item == null) {
                continue;
            }
            // 盘古 Rerank 响应不携带原文档内容，document 字段填空，让上层按 index 回查
            out.add(new RerankResult(item.index, null, item.relevanceScore));
        }
        return out;
    }

    // ====== Pangu response DTOs ======

    /** 顶层响应：{@code { "results": [...] }}。 */
    static class PanguRerankRawResponse {
        @JsonProperty("results")
        public List<PanguRerankRawItem> results;
    }

    /**
     * 单条结果：{@code { "index": N, "relevance_score": F }}。
     * 使用 public 字段以便 Jackson 在没有 setter 的情况下也能反序列化；
     * {@link JsonProperty} 显式声明以表达意图（snake_case → camelCase 字段映射）。
     */
    static class PanguRerankRawItem {
        @JsonProperty("index")
        public int index;

        @JsonProperty("relevance_score")
        public double relevanceScore;
    }
}
