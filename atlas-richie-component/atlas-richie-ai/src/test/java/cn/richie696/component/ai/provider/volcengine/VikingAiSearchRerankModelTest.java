/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.ai.provider.volcengine;

import cn.richie696.component.ai.api.RerankRequest;
import cn.richie696.component.ai.api.RerankResult;
import cn.richie696.component.ai.config.multimodal.rerank.RerankModelConfig;
import cn.richie696.component.http.core.HttpClient;
import cn.richie696.component.http.core.HttpRequest;
import cn.richie696.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 新版 Viking AI Search 精排协议的纯单元测试。 */
class VikingAiSearchRerankModelTest {

    @Test
    void constructor_shouldRejectMissingApiKey() {
        RerankModelConfig cfg = config();
        cfg.setApiKey(null);
        assertThrows(IllegalArgumentException.class,
                () -> new VikingAiSearchRerankModel(mock(HttpClient.class), cfg));
    }

    @Test
    void constructor_shouldRequireApplicationAndScene() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("ark-key");
        assertThrows(IllegalArgumentException.class,
                () -> new VikingAiSearchRerankModel(mock(HttpClient.class), cfg));
    }

    @Test
    void buildRequestBody_shouldUseOfficialSnakeCaseCandidateShape() {
        RerankModelConfig cfg = config();
        cfg.setUserId("user-default");
        VikingAiSearchRerankModel model = new VikingAiSearchRerankModel(mock(HttpClient.class), cfg);

        Map<String, Object> body = model.buildRequestBody(
                new RerankRequest("query-is-compatible-only", List.of("A", "B"), null, 2,
                        List.of("doc-a", "doc-b"), "user-request"));

        assertEquals(2, body.get("page_size"));
        assertEquals(Map.of("_user_id", "user-request"), body.get("user"));
        assertEquals(Map.of("items", List.of(Map.of("_id", "doc-a"), Map.of("_id", "doc-b")),
                "weight", 1.0), body.get("items"));
        assertFalse(body.containsKey("query"), "Viking AI Search does not use generic text-rerank query field");
    }

    @Test
    void rerankAsync_shouldPostToRegionEndpointWithBearerApiKey() {
        RequestFixture fixture = futureJsonFixture("{\"result\":{\"rec_results\":[]}}");
        VikingAiSearchRerankModel model = new VikingAiSearchRerankModel(fixture.httpClient(), config());

        model.rerankAsync(request()).join();

        verify(fixture.httpClient()).post(
                eq("https://aisearch.cn-beijing.volces.com/api/v1/application/app-1/scene-1/rerank"), any());
        verify(fixture.request()).header("Authorization", "Bearer ark-key");
        verify(fixture.request()).header("Content-Type", "application/json");
    }

    @Test
    void rerankAsync_shouldMapOfficialResultIdsBackToInputDocuments() {
        String json = "{\"result\":{\"rec_results\":["
                + "{\"_id\":\"doc-b\",\"score\":0.91},"
                + "{\"_id\":\"doc-a\",\"score\":0.55}]}}";
        RequestFixture fixture = futureJsonFixture(json);
        VikingAiSearchRerankModel model = new VikingAiSearchRerankModel(fixture.httpClient(), config());

        var response = model.rerankAsync(request()).join();

        assertTrue(response.isSuccess());
        List<RerankResult> results = response.getResults();
        assertEquals(2, results.size());
        assertEquals(1, results.get(0).getIndex());
        assertEquals("B", results.get(0).getDocument());
        assertEquals(0.91, results.get(0).getRelevanceScore(), 1e-9);
        assertEquals(0, results.get(1).getIndex());
        assertEquals("A", results.get(1).getDocument());
    }

    @Test
    void rerankAsync_shouldRejectMissingOrMismatchedDocumentIds() {
        VikingAiSearchRerankModel model = new VikingAiSearchRerankModel(mock(HttpClient.class), config());

        assertThrows(IllegalArgumentException.class,
                () -> model.rerankAsync(RerankRequest.of("q", List.of("A"), null, null)));
        assertThrows(IllegalArgumentException.class,
                () -> model.rerankAsync(new RerankRequest("q", List.of("A", "B"), null, null,
                        List.of("only-one"), null)));
    }

    @Test
    void rerankAsync_shouldHonorConfiguredEndpoint() {
        RerankModelConfig cfg = config();
        cfg.setEndpoint("https://custom.example/rerank");
        RequestFixture fixture = futureJsonFixture("{\"result\":{\"rec_results\":[]}}");
        VikingAiSearchRerankModel model = new VikingAiSearchRerankModel(fixture.httpClient(), cfg);

        model.rerankAsync(request()).join();

        verify(fixture.httpClient()).post(eq("https://custom.example/rerank"), any());
    }

    private static RerankModelConfig config() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setApiKey("ark-key");
        cfg.setApplicationId("app-1");
        cfg.setSceneId("scene-1");
        return cfg;
    }

    private static RerankRequest request() {
        return RerankRequest.ofWithDocumentIds("q", List.of("A", "B"),
                List.of("doc-a", "doc-b"), null, 2);
    }

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

    private record RequestFixture(HttpClient httpClient, HttpRequest request) {
    }
}
