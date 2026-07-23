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

import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智谱 Rerank 适配器的纯单元测试（hermetic —— 不发起真实网络请求）。
 * <p>
 * 测试策略：
 * <ul>
 *   <li><b>Request body</b>：走 {@link ZhipuRerankModel#buildRequestBody(RerankRequest)} 验证
 *       query / documents / top_n / return_documents / model 字段装配；缺省值回落路径。</li>
 *   <li><b>Auth header</b>：用 Mockito 桩 {@link HttpClient} + {@link HttpRequest}，
 *       触发 {@code callAsync()} 并断言 Bearer 鉴权头 + POST 端点。</li>
 *   <li><b>Response mapping</b>：构造假 JSON 走内部 DTO 验证反序列化与 {@link com.richie.component.ai.api.RerankResult}
 *       字段映射。</li>
 * </ul>
 */
class ZhipuRerankModelTest {

    // ==================== Constructor ====================

    @Test
    void constructor_shouldRejectNullHttpClient() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("k");
        assertThrows(NullPointerException.class, () -> new ZhipuRerankModel(null, cfg));
    }

    @Test
    void constructor_shouldRejectNullConfig() {
        assertThrows(NullPointerException.class,
                () -> new ZhipuRerankModel(mock(HttpClient.class), null));
    }

    @Test
    void constructor_shouldRejectNullApiKey() {
        RerankModelConfig cfg = new RerankModelConfig();
        // cfg.apiKey intentionally left null
        assertThrows(NullPointerException.class,
                () -> new ZhipuRerankModel(mock(HttpClient.class), cfg));
    }

    // ==================== Request body ====================

    @Test
    void buildRequestBody_shouldUseDefaultModelWhenRequestModelBlank() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("k");
        ZhipuRerankModel model = new ZhipuRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("doc1"), null, null));

        assertEquals("rerank", body.get("model"),
                "blank request.model must fall back to default 'rerank'");
    }

    @Test
    void buildRequestBody_shouldPreferRequestModelOverConfigDefault() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("k");
        cfg.setModel("zhipu-rerank-config-default"); // config default
        ZhipuRerankModel model = new ZhipuRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("doc1"), "zhipu-rerank-request-override", null));

        assertEquals("zhipu-rerank-request-override", body.get("model"),
                "request.model must override config.model");
    }

    @Test
    void buildRequestBody_shouldIncludeReturnDocumentsTrue() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("k");
        ZhipuRerankModel model = new ZhipuRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("doc1"), null, null));

        assertEquals(Boolean.TRUE, body.get("return_documents"),
                "return_documents must default to true so callers can read RerankResult.document");
    }

    @Test
    void buildRequestBody_shouldOmitTopNWhenNull() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("k");
        ZhipuRerankModel model = new ZhipuRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("doc1"), null, null));

        assertFalse(body.containsKey("top_n"),
                "top_n must be omitted when RerankRequest.topN is null");
    }

    @Test
    void buildRequestBody_shouldIncludeTopNWhenPositive() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("k");
        ZhipuRerankModel model = new ZhipuRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("query", List.of("d1", "d2", "d3", "d4", "d5"), null, 3));

        assertEquals(3, body.get("top_n"), "top_n must be set when RerankRequest.topN > 0");
    }

    // ==================== Auth header + endpoint ====================

    @Test
    void rerankAsync_shouldPostToDefaultUrlAndSetBearerAuth() {
        RequestFixture fixture = futureJsonFixture("{\"results\":[]}");
        ZhipuRerankModel model = new ZhipuRerankModel(fixture.httpClient(), makeZhipuCfg());

        var ignored = model.rerankAsync(
                RerankRequest.of("q", List.of("d1"), null, 5)).join();

        verify(fixture.httpClient()).post(eq(ZhipuRerankModel.DEFAULT_BASE_URL), any());
        verify(fixture.request()).header("Authorization", "Bearer zhipu-test-key");
        verify(fixture.request()).header("Content-Type", "application/json");
    }

    @Test
    void rerankAsync_shouldPostToCustomBaseUrlWhenConfigured() {
        RequestFixture fixture = futureJsonFixture("{\"results\":[]}");
        RerankModelConfig cfg = makeZhipuCfg();
        cfg.setBaseUrl("https://custom.example.com/api/paas/v4/rerank");
        ZhipuRerankModel model = new ZhipuRerankModel(fixture.httpClient(), cfg);

        var ignored = model.rerankAsync(
                RerankRequest.of("q", List.of("d1"), null, null)).join();

        verify(fixture.httpClient()).post(eq("https://custom.example.com/api/paas/v4/rerank"), any());
    }

    // ==================== Response mapping ====================

    @Test
    void rerankAsync_shouldDeserializeResultsArray() {
        String json = "{\"results\":[{\"index\":2,\"relevance_score\":0.91,\"document\":\"doc-c\"},"
                + "{\"index\":0,\"relevance_score\":0.55,\"document\":\"doc-a\"}]}";
        RequestFixture fixture = futureJsonFixture(json);
        ZhipuRerankModel model = new ZhipuRerankModel(fixture.httpClient(), makeZhipuCfg());

        var resp = model.rerankAsync(
                RerankRequest.of("q", List.of("a", "b", "c"), null, null)).join();

        assertNotNull(resp);
        assertTrue(resp.isSuccess());
        List<RerankResult> results = resp.getResults();
        assertEquals(2, results.size());
        assertEquals(2, results.get(0).getIndex(), "preserves upstream index order");
        assertEquals(0.91, results.get(0).getRelevanceScore(), 1e-9);
        assertEquals("doc-c", results.get(0).getDocument());
        assertEquals(0, results.get(1).getIndex());
    }

    @Test
    void rerankAsync_shouldReturnEmptyListWhenBodyMissingResults() {
        RequestFixture fixture = futureJsonFixture("{}");
        ZhipuRerankModel model = new ZhipuRerankModel(fixture.httpClient(), makeZhipuCfg());

        var resp = model.rerankAsync(
                RerankRequest.of("q", List.of("d1"), null, null)).join();

        assertNotNull(resp);
        assertTrue(resp.getResults().isEmpty(), "missing results must degrade to empty list");
    }

    @Test
    void rerank_sync_shouldWrapAsyncFailureAsRuntimeException() {
        RerankModelConfig cfg = makeZhipuCfg();
        ZhipuRerankModel model = new ZhipuRerankModel(mock(HttpClient.class), cfg);

        // null request is caught by Objects.requireNonNull → NPE wrapped as RuntimeException
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> model.rerank(null));
        assertTrue(ex.getMessage().contains("Zhipu rerank"),
                "wrapped exception must mention 'Zhipu rerank', got: " + ex.getMessage());
    }

    // -------- helpers --------

    private static RerankModelConfig makeZhipuCfg() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("zhipu-test-key");
        cfg.setBaseUrl(null);
        cfg.setModel("rerank");
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
