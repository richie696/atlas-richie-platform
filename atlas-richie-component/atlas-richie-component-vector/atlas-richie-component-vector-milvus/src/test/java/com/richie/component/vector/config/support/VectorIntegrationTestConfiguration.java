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
package com.richie.component.vector.config.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Milvus IT 测试上下文 — Spring 装配 + EmbeddingModel 抽象。
 * <p>
 * <b>嵌入模型注入策略</b>（依据环境变量，无任何硬编码 key/url）：
 * <ul>
 *   <li>当 {@code DASHSCOPE_API_KEY} + {@code DASHSCOPE_BASE_URL} + {@code DASHSCOPE_EMBEDDING_MODEL}
 *       + {@code DASHSCOPE_EMBEDDING_DIM} 四个环境变量全部设置时 → 走真实 DashScope OpenAI-兼容端点
 *       的远程嵌入 Bean（{@link EnvAwareDashScopeEmbeddingModel}）。</li>
 *   <li>否则 → 4 维本地 stub，本地无网也可运行。</li>
 * </ul>
 * 即 key/URL/模型名/维度全部在 runner/IDE/CI 注入，源码零硬编码、零默认值。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({
        com.richie.component.vector.config.MilvusVectorAutoConfiguration.class,
})
@Slf4j
public class VectorIntegrationTestConfiguration {

    /** 环境变量：DashScope 兼容模式 baseUrl（含 scheme+host+path 前缀，不带尾斜杠）。 */
    private static final String ENV_BASE_URL = "DASHSCOPE_BASE_URL";

    /** 环境变量：DashScope API Key（Bearer Token）。 */
    private static final String ENV_API_KEY = "DASHSCOPE_API_KEY";

    /** 环境变量：嵌入模型名（例如 {@code text-embedding-v3}）。 */
    private static final String ENV_MODEL = "DASHSCOPE_EMBEDDING_MODEL";

    /** 环境变量：嵌入向量维度（与 {@link #ENV_MODEL} 强绑定，例如 {@code 1536}）。 */
    private static final String ENV_DIM = "DASHSCOPE_EMBEDDING_DIM";

    /**
     * 文本嵌入 Bean — 当且仅当四个环境变量全配置时使用真实 DashScope 远端；否则回退本地 stub。
     * <p>
     * 维度也由环境变量驱动，确保 collection 创建时的 dim 与 embed() 返回的 dim 严格一致；
     * 测试侧（如 {@code MilvusVectorRecordOpsIT}）通过相同的 env var 创建 collection。
     */
    @Bean("aiEmbeddingModel")
    @Primary
    public EmbeddingModel aiEmbeddingModel() {
        String baseUrl = trimToNull(System.getenv(ENV_BASE_URL));
        String apiKey = trimToNull(System.getenv(ENV_API_KEY));
        String model = trimToNull(System.getenv(ENV_MODEL));
        String dimStr = trimToNull(System.getenv(ENV_DIM));

        if (baseUrl != null && apiKey != null && model != null && dimStr != null) {
            int dim;
            try {
                dim = Integer.parseInt(dimStr);
            } catch (NumberFormatException nfe) {
                log.warn("[IT] {}={} 非整数，回退 stub。", ENV_DIM, dimStr);
                return new MilvusStubEmbeddingModel();
            }
            log.info("[IT] 启用真实 DashScope 嵌入 — model={}, dim={}, baseUrl={} (key 已脱敏省略)",
                    model, dim, baseUrl);
            return new EnvAwareDashScopeEmbeddingModel(baseUrl, apiKey, model, dim);
        }
        log.info("[IT] 缺少远端嵌入所需环境变量（{} / {} / {} / {}），回退本地 stub（dim=4）。",
                ENV_BASE_URL, ENV_API_KEY, ENV_MODEL, ENV_DIM);
        return new MilvusStubEmbeddingModel();
    }

    /**
     * RerankModel 占位 — atlas-richie-component-ai 的 {@code RerankServiceImpl} 是 {@code @Service},
     * 组件扫描自动注册,IT 上下文里需要提供一个非 null Bean 才能满足构造注入。
     */
    @Bean
    public RerankModel rerankModel() {
        return new RerankModel() {
            @Override
            public RerankResponse rerank(RerankRequest request) {
                return RerankResponse.succeed(Collections.emptyList(), Clock.systemDefaultZone());
            }

            @Override
            public CompletableFuture<RerankResponse> rerankAsync(RerankRequest request) {
                return CompletableFuture.completedFuture(
                        RerankResponse.succeed(Collections.emptyList(), Clock.systemDefaultZone()));
            }
        };
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 本地 4 维嵌入 stub — 用于无网/无 env 的快速冒烟开发。
     */
    private static final class MilvusStubEmbeddingModel implements EmbeddingModel {

        @Override
        public org.springframework.ai.embedding.EmbeddingResponse call(
                org.springframework.ai.embedding.EmbeddingRequest request) {
            List<org.springframework.ai.embedding.Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new org.springframework.ai.embedding.Embedding(
                        new float[]{0.1f, 0.2f, 0.3f, 0.4f}, i));
            }
            return new org.springframework.ai.embedding.EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }

        @Override
        public float[] embed(String text) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return texts.stream().map(t -> new float[]{0.0f, 0.0f, 0.0f, 0.0f}).toList();
        }

        @Override
        public int dimensions() {
            return 4;
        }
    }

    /**
     * DashScope OpenAI-兼容模式嵌入 Bean — 全部配置（baseUrl / apiKey / model / dim）
     * 由调用方在构造期通过 {@link System#getenv(String)} 注入，本类不做任何默认值假设，
     * 也无 {@code static final String URL = ...} 类硬编码。
     * <p>
     * 协议：
     * <ul>
     *   <li>{@code POST {baseUrl}/embeddings}</li>
     *   <li>Authorization: {@code Bearer <apiKey>}</li>
     *   <li>Request: {@code {"model":"<model>","input":"<text or list>","dimensions":<dim>}}</li>
     *   <li>Response: {@code {"data":[{"embedding":[...]}]}}</li>
     * </ul>
     * 调用失败抛 {@link RuntimeException}（不吞错 — 测试应当红）。
     */
    private static final class EnvAwareDashScopeEmbeddingModel implements EmbeddingModel {

        private static final ObjectMapper MAPPER = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        private final HttpClient httpClient;
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final int dimensions;

        EnvAwareDashScopeEmbeddingModel(String baseUrl, String apiKey, String model, int dimensions) {
            this.baseUrl = baseUrl.endsWith("/")
                    ? baseUrl.substring(0, baseUrl.length() - 1)
                    : baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.dimensions = dimensions;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }

        @Override
        public org.springframework.ai.embedding.EmbeddingResponse call(
                org.springframework.ai.embedding.EmbeddingRequest request) {
            List<String> inputs = request.getInstructions();
            if (inputs == null || inputs.isEmpty()) {
                return new org.springframework.ai.embedding.EmbeddingResponse(List.of());
            }
            try {
                Map<String, Object> body = Map.of(
                        "model", model,
                        "input", inputs,
                        "dimensions", dimensions
                );
                String json = MAPPER.writeValueAsString(body);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/embeddings"))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new RuntimeException(
                            "DashScope embed 调用失败 status=" + response.statusCode()
                                    + " body=" + response.body());
                }
                DashScopeEmbedResponse parsed = MAPPER.readValue(response.body(), DashScopeEmbedResponse.class);
                List<org.springframework.ai.embedding.Embedding> result = new ArrayList<>(parsed.data.size());
                AtomicLong idx = new AtomicLong();
                for (DashScopeEmbedItem item : parsed.data) {
                    result.add(new org.springframework.ai.embedding.Embedding(
                            toFloatArray(item.embedding), (int) idx.getAndIncrement()));
                }
                return new org.springframework.ai.embedding.EmbeddingResponse(result);
            } catch (Exception e) {
                throw new RuntimeException("DashScope embed 调用异常: " + e.getMessage(), e);
            }
        }

        @Override
        public float[] embed(Document document) {
            if (document == null) {
                throw new IllegalArgumentException("document 不能为空");
            }
            String text = document.getText();
            if (text == null) {
                return new float[dimensions];
            }
            return embed(text);
        }

        @Override
        public float[] embed(String text) {
            return embed(List.of(text)).get(0);
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            if (texts == null || texts.isEmpty()) {
                return List.of();
            }
            List<String> inputs = texts.stream()
                    .map(s -> s == null ? "" : s)
                    .toList();
            org.springframework.ai.embedding.EmbeddingRequest req =
                    new org.springframework.ai.embedding.EmbeddingRequest(inputs, null);
            List<org.springframework.ai.embedding.Embedding> r = call(req).getResults();
            List<float[]> out = new ArrayList<>(r.size());
            for (org.springframework.ai.embedding.Embedding e : r) {
                out.add(e.getOutput());
            }
            return out;
        }

        @Override
        public int dimensions() {
            return dimensions;
        }

        private static float[] toFloatArray(List<Number> values) {
            if (values == null || values.isEmpty()) {
                return new float[0];
            }
            float[] out = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                Number n = values.get(i);
                out[i] = (n == null) ? 0f : n.floatValue();
            }
            return out;
        }

        /** DashScope / OpenAI-兼容模式 embeddings 响应的最小 DTO。 */
        @JsonIgnoreProperties(ignoreUnknown = true)
        static final class DashScopeEmbedResponse {

            @JsonProperty("data")
            public List<DashScopeEmbedItem> data;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static final class DashScopeEmbedItem {

            @JsonProperty("embedding")
            public List<Number> embedding;

            @JsonProperty("index")
            public Integer index;
        }
    }
}
