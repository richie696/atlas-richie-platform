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

import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;
import com.richie.component.ai.provider.sign.AppCodeSigner;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 盘古 Rerank 适配器的纯单元测试（hermetic —— 不发起真实网络请求）。
 * <p>
 * 测试策略：
 * <ul>
 *   <li><b>Request body</b>：走 {@link PanguRerankModel#buildRequestBody(RerankRequest)} 验证
 *       query / documents / top_k / model 字段装配；盘古使用 {@code top_k} 命名（与 DashScope
 *       的 {@code top_n} / 智谱的 {@code top_n} 不同）。</li>
 *   <li><b>Auth header</b>：用 Mockito 桩 {@link HttpClient} + {@link HttpRequest}，
 *       触发 {@code callAsync()} 并断言 AppCode 鉴权头 {@code X-Apig-AppCode} + POST 端点。</li>
 *   <li><b>Response mapping</b>：构造假 JSON 走内部 DTO 验证反序列化；
 *       盘古响应不携带原文，{@code document} 字段应保持 {@code null}。</li>
 * </ul>
 */
class PanguRerankModelTest {

    // ==================== Constructor ====================

    @Test
    void constructor_shouldRejectNullHttpClient() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setAppCode("k");
        assertThrows(NullPointerException.class, () -> new PanguRerankModel(null, cfg));
    }

    @Test
    void constructor_shouldRejectNullConfig() {
        assertThrows(NullPointerException.class,
                () -> new PanguRerankModel(mock(HttpClient.class), null));
    }

    @Test
    void constructor_shouldRejectNullAppCode() {
        RerankModelConfig cfg = new RerankModelConfig();
        // cfg.appCode intentionally left null
        assertThrows(NullPointerException.class,
                () -> new PanguRerankModel(mock(HttpClient.class), cfg));
    }

    // ==================== Request body ====================

    @Test
    void buildRequestBody_shouldUseDefaultModelWhenRequestModelBlank() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setAppCode("k");
        PanguRerankModel model = new PanguRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("doc1"), null, null));

        assertEquals("pangu-rerank", body.get("model"),
                "blank request.model must fall back to default 'pangu-rerank'");
    }

    @Test
    void buildRequestBody_shouldPreferRequestModelOverConfigDefault() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setAppCode("k");
        cfg.setModel("pangu-rerank-config-default"); // config default
        PanguRerankModel model = new PanguRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("doc1"), "pangu-rerank-request-override", null));

        assertEquals("pangu-rerank-request-override", body.get("model"),
                "request.model must override config.model");
    }

    @Test
    void buildRequestBody_shouldUseTopKKeyWhenTopNPositive() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setAppCode("k");
        PanguRerankModel model = new PanguRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("d1", "d2", "d3"), null, 2));

        assertEquals(2, body.get("top_k"),
                "Pangu Rerank uses 'top_k' key, must be set when RerankRequest.topN > 0");
        assertFalse(body.containsKey("top_n"),
                "Pangu Rerank must NOT emit 'top_n' (that's DashScope/Zhipu naming)");
    }

    @Test
    void buildRequestBody_shouldOmitTopKWhenTopNNull() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setAppCode("k");
        PanguRerankModel model = new PanguRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("doc1"), null, null));

        assertFalse(body.containsKey("top_k"),
                "top_k must be omitted when RerankRequest.topN is null");
    }

    // ==================== Auth header + endpoint ====================

    @Test
    void rerankAsync_shouldPostToDefaultUrlAndSetAppCodeAuth() {
        RequestFixture fixture = futureJsonFixture("{\"results\":[]}");
        PanguRerankModel model = new PanguRerankModel(fixture.httpClient(), makePanguCfg());

        var ignored = model.rerankAsync(
                RerankRequest.of("q", List.of("d1"), null, 5)).join();

        verify(fixture.httpClient()).post(eq(PanguRerankModel.DEFAULT_BASE_URL), any());
        verify(fixture.request()).header(AppCodeSigner.HEADER_APP_CODE, "pangu-test-app-code");
        verify(fixture.request()).header("Content-Type", "application/json");
    }

    @Test
    void rerankAsync_shouldPostToCustomBaseUrlWhenConfigured() {
        RequestFixture fixture = futureJsonFixture("{\"results\":[]}");
        RerankModelConfig cfg = makePanguCfg();
        cfg.setBaseUrl("https://custom-pangu.example.com/api/v1/rerank");
        PanguRerankModel model = new PanguRerankModel(fixture.httpClient(), cfg);

        var ignored = model.rerankAsync(
                RerankRequest.of("q", List.of("d1"), null, null)).join();

        verify(fixture.httpClient()).post(eq("https://custom-pangu.example.com/api/v1/rerank"), any());
    }

    // ==================== Response mapping ====================

    @Test
    void rerankAsync_shouldDeserializeResultsAndLeaveDocumentNull() {
        // 盘古 Rerank 响应只携带 index + relevance_score，不回带原文档
        String json = "{\"results\":[{\"index\":2,\"relevance_score\":0.91},"
                + "{\"index\":0,\"relevance_score\":0.55}]}";
        RequestFixture fixture = futureJsonFixture(json);
        PanguRerankModel model = new PanguRerankModel(fixture.httpClient(), makePanguCfg());

        var resp = model.rerankAsync(
                RerankRequest.of("q", List.of("a", "b", "c"), null, null)).join();

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        List<RerankResult> results = resp.getResults();
        assertEquals(2, results.size());
        assertEquals(2, results.get(0).getIndex(), "preserves upstream index order");
        assertEquals(0.91, results.get(0).getRelevanceScore(), 1e-9);
        assertNull(results.get(0).getDocument(),
                "Pangu doesn't echo original documents → document must be null");
        assertEquals(0, results.get(1).getIndex());
        assertEquals(0.55, results.get(1).getRelevanceScore(), 1e-9);
    }

    @Test
    void rerankAsync_shouldReturnEmptyListWhenBodyMissingResults() {
        RequestFixture fixture = futureJsonFixture("{}");
        PanguRerankModel model = new PanguRerankModel(fixture.httpClient(), makePanguCfg());

        var resp = model.rerankAsync(
                RerankRequest.of("q", List.of("d1"), null, null)).join();

        assertNotNull(resp);
        assertTrue(resp.getResults().isEmpty(), "missing results must degrade to empty list");
    }

    @Test
    void rerank_sync_shouldWrapAsyncFailureAsRuntimeException() {
        RerankModelConfig cfg = makePanguCfg();
        PanguRerankModel model = new PanguRerankModel(mock(HttpClient.class), cfg);

        // null request is caught by Objects.requireNonNull → NPE wrapped as RuntimeException
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> model.rerank(null));
        assertTrue(ex.getMessage().contains("Pangu rerank"),
                "wrapped exception must mention 'Pangu rerank', got: " + ex.getMessage());
    }

    // -------- helpers --------

    private static RerankModelConfig makePanguCfg() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setAppCode("pangu-test-app-code");
        cfg.setBaseUrl(null);
        cfg.setModel("pangu-rerank");
        return cfg;
    }

    /** HTTP 桩组合，把假 JSON 反序列化为调用方私有 DTO 并包成 completed future。 */
    private static RequestFixture futureJsonFixture(String json) {
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest request = mock(HttpRequest.class);
        when(httpClient.post(anyString(), any())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.future(ArgumentMatchers.<Class<Object>>any())).thenAnswer(invocation -> {
            Class<Object> responseType = invocation.getArgument(0);
            Object response = JsonUtils.getInstance().deserialize(json, responseType);
            return CompletableFuture.completedFuture(response);
        });
        return new RequestFixture(httpClient, request);
    }

    /** HTTP 桩组合。 */
    private record RequestFixture(HttpClient httpClient, HttpRequest request) {
    }
}
