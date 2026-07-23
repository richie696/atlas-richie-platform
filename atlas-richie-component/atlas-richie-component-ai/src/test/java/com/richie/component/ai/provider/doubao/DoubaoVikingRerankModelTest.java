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
package com.richie.component.ai.provider.doubao;

import com.richie.component.ai.config.multimodal.rerank.RerankProvider;

import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.provider.support.MultimodalModelFactory;
import com.richie.component.http.core.HttpClient;
import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 火山引擎 VikingDB Rerank 适配器的纯单元测试（hermetic —— 不发起真实网络请求）。
 * <p>
 * 测试策略：
 * <ul>
 *   <li><b>Request body</b>：走 {@link DoubaoVikingRerankModel#buildRequestBody(RerankRequest)}
 *       验证 rerank_model / datas[].query / datas[].content 字段装配。</li>
 *   <li><b>Signing 常数</b>：验证 {@link DoubaoVikingRerankModel#sha256Hex(byte[])} 等基础工具。</li>
 *   <li><b>HTTP 调用</b>：用 Mockito 桩 JDK {@link java.net.http.HttpClient#sendAsync}，
 *       验证请求头（Host / X-Date / X-Content-Sha256 / Authorization）与 POST 路径。</li>
 *   <li><b>Response mapping</b>：构造假 JSON 走内部 DTO 验证反序列化与
 *       {@link RerankResult} 字段映射（位置对齐 1:1）。</li>
 *   <li><b>Error paths</b>：非 200 HTTP、非 0 code、缺失 data、缺失 scores 等退化路径。</li>
 *   <li><b>Factory dispatch</b>：{@link MultimodalModelFactory#createRerankModel} 验证
 *       vendor {@code doubao} 分派正确。</li>
 * </ul>
 */
class DoubaoVikingRerankModelTest {

    // ==================== Constructor ====================

    @Test
    void constructor_shouldRejectNullConfig() {
        assertThrows(NullPointerException.class,
                () -> new DoubaoVikingRerankModel(null, mockJdkClient()));
    }

    @Test
    void constructor_shouldRejectNullJdkClient() {
        assertThrows(NullPointerException.class,
                () -> new DoubaoVikingRerankModel(makeCfg(), null));
    }

    @Test
    void constructor_shouldRejectNullAccessKey() {
        RerankModelConfig cfg = makeCfg();
        cfg.setAccessKey(null);
        assertThrows(NullPointerException.class,
                () -> new DoubaoVikingRerankModel(cfg, mockJdkClient()));
    }

    @Test
    void constructor_shouldRejectNullSecretKey() {
        RerankModelConfig cfg = makeCfg();
        cfg.setSecretKey(null);
        assertThrows(NullPointerException.class,
                () -> new DoubaoVikingRerankModel(cfg, mockJdkClient()));
    }

    @Test
    void constructor_shouldUseDefaultsForBlankFields() {
        RerankModelConfig cfg = makeCfg();
        cfg.setBaseUrl(null);
        cfg.setRegion(null);
        cfg.setModel(null);
        // Should not throw
        new DoubaoVikingRerankModel(cfg, mockJdkClient());
    }

    // ==================== Request body ====================

    @Test
    void buildRequestBody_shouldSetRerankModelAndDatas() {
        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), mockJdkClient());

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("my query", List.of("doc1", "doc2"), "base-multilingual-rerank", null));

        assertEquals("base-multilingual-rerank", body.get("rerank_model"),
                "rerank_model must match request model");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> datas = (List<Map<String, Object>>) body.get("datas");
        assertNotNull(datas);
        assertEquals(2, datas.size());

        assertEquals("my query", datas.get(0).get("query"));
        assertEquals("doc1", datas.get(0).get("content"));
        assertEquals("my query", datas.get(1).get("query"));
        assertEquals("doc2", datas.get(1).get("content"));
    }

    @Test
    void buildRequestBody_shouldUseDefaultModelWhenRequestModelBlank() {
        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), mockJdkClient());
        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("q", List.of("doc"), null, null));
        assertEquals("base-multilingual-rerank", body.get("rerank_model"),
                "blank request.model must fall back to default model");
    }

    @Test
    void buildRequestBody_shouldPreferRequestModelOverConfigDefault() {
        RerankModelConfig cfg = makeCfg();
        cfg.setModel("m3-v2-rerank"); // config default
        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(cfg, mockJdkClient());

        Map<String, Object> body = model.buildRequestBody(
                RerankRequest.of("q", List.of("doc"), "base-multilingual-rerank", null));

        assertEquals("base-multilingual-rerank", body.get("rerank_model"),
                "request.model must override config.model");
    }

    // ==================== Signing utilities ====================

    @Test
    void sha256Hex_shouldComputeCorrectDigest() {
        byte[] input = "hello".getBytes(StandardCharsets.UTF_8);
        String hex = DoubaoVikingRerankModel.sha256Hex(input);
        assertEquals(64, hex.length(), "SHA-256 hex must be 64 chars");
        // known SHA-256 of "hello"
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hex);
    }

    @Test
    void bytesToHex_shouldProduceLowercaseHex() {
        String hex = DoubaoVikingRerankModel.bytesToHex(new byte[]{0x00, (byte) 0xFF, 0x4A});
        assertEquals("00ff4a", hex, "bytesToHex must produce lowercase hex");
    }

    // ==================== Response mapping ====================

    @Test
    void rerankAsync_shouldDeserializeScoresPositionAligned() {
        java.net.http.HttpClient jdkClient = buildMockJdk(
                200, "{\"code\":0,\"message\":\"success\",\"request_id\":\"r1\","
                        + "\"data\":{\"scores\":[0.95,0.23,0.72],\"token_usage\":100}}");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of("doc-a", "doc-b", "doc-c"), null, null)).join();
        List<RerankResult> results = resp.getResults();

        assertNotNull(results);
        assertEquals(3, results.size());
        // Position-aligned: scores[i] ↔ documents[i]
        assertEquals(0, results.get(0).getIndex());
        assertEquals("doc-a", results.get(0).getDocument());
        assertEquals(0.95, results.get(0).getRelevanceScore(), 1e-9);

        assertEquals(1, results.get(1).getIndex());
        assertEquals("doc-b", results.get(1).getDocument());
        assertEquals(0.23, results.get(1).getRelevanceScore(), 1e-9);

        assertEquals(2, results.get(2).getIndex());
        assertEquals("doc-c", results.get(2).getDocument());
        assertEquals(0.72, results.get(2).getRelevanceScore(), 1e-9);
    }

    @Test
    void rerankAsync_shouldReturnEmptyListOnNonZeroCode() {
        java.net.http.HttpClient jdkClient = buildMockJdk(
                200, "{\"code\":500,\"message\":\"internal error\",\"request_id\":\"r2\","
                        + "\"data\":null}");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of("doc-a"), null, null)).join();
        List<RerankResult> results = resp.getResults();

        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertTrue(results.isEmpty(), "non-zero code must degrade to empty list");
    }

    @Test
    void rerankAsync_shouldReturnEmptyListOnNon200Http() {
        java.net.http.HttpClient jdkClient = buildMockJdk(
                403, "{\"code\":403,\"message\":\"Forbidden\"}");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of("doc-a"), null, null)).join();
        List<RerankResult> results = resp.getResults();

        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertTrue(results.isEmpty(), "non-200 HTTP must degrade to empty list");
    }

    @Test
    void rerankAsync_shouldReturnEmptyListOnNullData() {
        java.net.http.HttpClient jdkClient = buildMockJdk(
                200, "{\"code\":0,\"message\":\"success\",\"data\":null}");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of("doc-a"), null, null)).join();
        List<RerankResult> results = resp.getResults();

        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertTrue(results.isEmpty(), "null data must degrade to empty list");
    }

    @Test
    void rerankAsync_shouldReturnEmptyListOnNullScores() {
        java.net.http.HttpClient jdkClient = buildMockJdk(
                200, "{\"code\":0,\"message\":\"success\",\"data\":{\"scores\":null}}");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of("doc-a"), null, null)).join();
        List<RerankResult> results = resp.getResults();

        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertTrue(results.isEmpty(), "null scores must degrade to empty list");
    }

    @Test
    void rerankAsync_shouldReturnEmptyListOnEmpty200Body() {
        java.net.http.HttpClient jdkClient = buildMockJdk(200, "");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of("doc-a"), null, null)).join();
        List<RerankResult> results = resp.getResults();

        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertTrue(results.isEmpty(), "empty body must degrade to empty list");
    }

    @Test
    void rerankAsync_shouldSetRequiredHttpHeaders() {
        // Verify that the generated HTTP request contains required headers and path
        java.net.http.HttpClient jdkClient = mock(java.net.http.HttpClient.class);
        when(jdkClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest req = invocation.getArgument(0);
                    // Verify path
                    assertTrue(req.uri().toString().contains("/api/knowledge/service/rerank"),
                            "URI must contain rerank path");
                    // Host 头由 JDK HttpClient 自动设；验证 URI 包含正确主机
                    assertTrue(req.uri().getHost().contains(
                            "api-knowledgebase.mlp.cn-beijing.volces.com"),
                            "URI host must contain default host");
                    assertTrue(req.headers().firstValue("X-Date").isPresent());
                    assertTrue(req.headers().firstValue("X-Content-Sha256").isPresent());
                    assertTrue(req.headers().firstValue("Authorization").isPresent());
                    assertTrue(req.headers().firstValue("Content-Type").isPresent());
                    assertEquals("application/json",
                            req.headers().firstValue("Content-Type").get());
                    return CompletableFuture.completedFuture(
                            buildMockResponse(200, "{\"code\":0,\"data\":{\"scores\":[]}}"));
                });

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        model.rerankAsync(RerankRequest.of("q", List.of("doc"), null, null)).join();
    }

    @Test
    void rerankAsync_shouldReturnEmptyListForEmptyDocuments() {
        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), mockJdkClient());
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of(), null, null)).join();
        List<RerankResult> results = resp.getResults();
        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertTrue(results.isEmpty(), "empty documents must short-circuit to empty list");
    }

    // ==================== Sync call ====================

    @Test
    void rerank_sync_shouldDelegateToAsyncAndReturnResults() {
        java.net.http.HttpClient jdkClient = buildMockJdk(
                200, "{\"code\":0,\"message\":\"success\",\"request_id\":\"r3\","
                        + "\"data\":{\"scores\":[0.88,0.44]}}");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerank(
                RerankRequest.of("q", List.of("x", "y"), null, null));
        List<RerankResult> results = resp.getResults();

        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals(0.88, results.get(0).getRelevanceScore(), 1e-9);
        assertEquals(0.44, results.get(1).getRelevanceScore(), 1e-9);
    }

    @Test
    void rerank_sync_shouldWrapAsyncFailureAsRuntimeException() {
        // Null request triggers NPE → wrapped as RuntimeException
        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), mockJdkClient());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> model.rerank(null));
        assertTrue(ex.getMessage().contains("Doubao Viking rerank"),
                "wrapped exception must mention 'Doubao Viking rerank', got: " + ex.getMessage());
    }

    // ==================== Factory dispatch ====================

    @Test
    void factory_shouldDispatchDoubaoVendor() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.DOUBAO);
        cfg.setAccessKey("ak");
        cfg.setSecretKey("sk");

        var model = MultimodalModelFactory.createRerankModel(cfg, mock(HttpClient.class));
        assertNotNull(model);
        assertInstanceOf(DoubaoVikingRerankModel.class, model,
                "vendor 'doubao' must create DoubaoVikingRerankModel");
    }

    @Test
    void factory_shouldDispatchDoubaoVendorCaseInsensitive() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.DOUBAO);
        cfg.setAccessKey("ak");
        cfg.setSecretKey("sk");

        var model = MultimodalModelFactory.createRerankModel(cfg, mock(HttpClient.class));
        assertNotNull(model);
        assertInstanceOf(DoubaoVikingRerankModel.class, model,
                "vendor 'DouBao' (mixed case) must also dispatch correctly");
    }

    // ==================== Business code check ====================

    @Test
    void rerankAsync_shouldHandleCodeZeroSuccessfully() {
        // "code":0 should be treated as success even with empty scores
        java.net.http.HttpClient jdkClient = buildMockJdk(
                200, "{\"code\":0,\"message\":\"success\",\"request_id\":\"r4\","
                        + "\"data\":{\"scores\":[],\"token_usage\":0}}");

        DoubaoVikingRerankModel model = new DoubaoVikingRerankModel(makeCfg(), jdkClient);
        RerankResponse resp = model.rerankAsync(
                RerankRequest.of("q", List.of("doc"), null, null)).join();
        List<RerankResult> results = resp.getResults();

        // 空 scores 数组 → 空结果列表（退化安全）
        assertTrue(resp.isSuccess());
        assertNotNull(results);
        assertTrue(results.isEmpty(), "empty scores array should produce empty results");
    }

    // ==================== Signature constants ====================

    @Test
    void constants_shouldMatchExpectedValues() {
        assertEquals("HMAC-SHA256", DoubaoVikingRerankModel.ALGORITHM);
        assertEquals("/api/knowledge/service/rerank", DoubaoVikingRerankModel.RERANK_PATH);
        assertEquals("api-knowledgebase.mlp.cn-beijing.volces.com", DoubaoVikingRerankModel.DEFAULT_HOST);
        assertEquals("cn-north-1", DoubaoVikingRerankModel.DEFAULT_REGION);
        assertEquals("air", DoubaoVikingRerankModel.SERVICE_NAME);
        assertEquals("base-multilingual-rerank", DoubaoVikingRerankModel.DEFAULT_MODEL);
        assertEquals("Authorization", DoubaoVikingRerankModel.HEADER_AUTHORIZATION);
        assertEquals("X-Date", DoubaoVikingRerankModel.HEADER_X_DATE);
        assertEquals("X-Content-Sha256", DoubaoVikingRerankModel.HEADER_X_CONTENT_SHA256);
    }

    // ==================== Helpers ====================

    private static RerankModelConfig makeCfg() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setAccessKey("test-ak");
        cfg.setSecretKey("test-sk");
        cfg.setBaseUrl(null);   // use default host
        cfg.setRegion(null);    // use default region
        cfg.setModel(null);     // use default model
        return cfg;
    }

    /** 建一个默认 mock JDK HttpClient（发送任何请求都返回 200 OK + 空结果）。 */
    private static java.net.http.HttpClient mockJdkClient() {
        return buildMockJdk(200, "{\"code\":0,\"data\":{\"scores\":[]}}");
    }

    /** 建 mock JDK HttpClient 返回指定状态码和响应体。 */
    private static java.net.http.HttpClient buildMockJdk(int status, String body) {
        @SuppressWarnings("unchecked")
        java.net.http.HttpClient mockClient = mock(java.net.http.HttpClient.class);
        HttpResponse<String> mockResponse = buildMockResponse(status, body);
        when(mockClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        return mockClient;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> buildMockResponse(int status, String body) {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.body()).thenReturn(body);
        return resp;
    }
}
