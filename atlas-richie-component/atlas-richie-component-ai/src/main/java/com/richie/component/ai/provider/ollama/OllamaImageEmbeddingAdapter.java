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
package com.richie.component.ai.provider.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import com.richie.context.utils.data.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Spring AI {@link org.springframework.ai.embedding.EmbeddingModel} 适配 Ollama 本地推理运行时。
 *
 * <p>Ollama 通常以单机服务运行，默认监听 {@code http://localhost:11434}，不要求 API Key。
 * 本适配器只调用 Ollama 原生的 {@code POST /api/embed} 文本 embedding 端点：单条输入使用
 * 字符串 {@code input}，批量输入使用字符串数组 {@code input}。默认模型为
 * {@code nomic-embed-text}，其默认输出维度为 768；调用方可以通过构造器覆盖模型名。
 *
 * <p>Ollama 的原生 embeddings API 并不提供可依赖的 CLIP 图像 embedding 协议。本类因此
 * <b>不实现图像分支，也不伪造 multimodal 请求体</b>；需要真正的文本/图像共同向量空间时，
 * 请使用 Phase C C3 的 HF TEI 适配器。这里的 "image embedding" 命名只表示它被挂在图向量
 * 能力的 Spring AI 工厂入口下，Ollama 实际承担的是检索上下文中的文本向量化。
 *
 * <h2>协议示例</h2>
 * <pre>{@code
 * POST http://localhost:11434/api/embed
 * Content-Type: application/json
 *
 * {"model":"nomic-embed-text","input":["first text","second text"]}
 *
 * {"model":"nomic-embed-text","embeddings":[[0.1,0.2,...],[0.3,0.4,...]],
 *  "total_duration":123456,"load_duration":1234}
 * }</pre>
 *
 * <p>适配器假设 Ollama 与应用部署在同一台机器或由调用方显式配置的内网地址上；它不负责
 * 拉取模型、服务发现、重试或跨节点负载均衡。
 *
 * @author richie696
 * @since 2026-07-22
 */
@Slf4j
public class OllamaImageEmbeddingAdapter implements org.springframework.ai.embedding.EmbeddingModel {

    /** Ollama 默认本地服务地址。 */
    public static final String DEFAULT_BASE_URL = "http://localhost:11434";

    /** Ollama 推荐的文本 embedding 模型，默认输出 768 维向量。 */
    public static final String DEFAULT_MODEL = "nomic-embed-text";

    /** {@link #DEFAULT_MODEL} 的默认向量维度；不通过远程 probe 获取。 */
    public static final int DEFAULT_DIMENSIONS = 768;

    private static final String EMBEDDINGS_PATH = "/api/embed";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String defaultModel;

    /**
     * 创建 Ollama embedding 适配器。
     *
     * @param httpClient 共享 HTTP 客户端，由 atlas-richie-component-http-* 提供
     * @param baseUrl    Ollama 服务根地址；为空或空白时使用 {@link #DEFAULT_BASE_URL}
     * @param model      Ollama 模型名；为空或空白时使用 {@link #DEFAULT_MODEL}
     * @throws NullPointerException 当 {@code httpClient} 为空时
     */
    public OllamaImageEmbeddingAdapter(HttpClient httpClient, String baseUrl, String model) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        String resolvedBaseUrl = (baseUrl == null || baseUrl.isBlank())
                ? DEFAULT_BASE_URL : baseUrl;
        this.baseUrl = stripTrailingSlash(resolvedBaseUrl);
        this.defaultModel = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
    }

    /**
     * 为 Ollama 请求创建可选的 Bearer 鉴权客户端装饰器。
     *
     * <p>Ollama 本身默认无鉴权，但部署在 Nginx/Envoy 等反向代理后时，代理可能要求
     * {@code Authorization: Bearer <key>}。使用动态代理只改写请求头，不改变适配器构造器
     * 的无 API Key 契约；空白 key 直接返回原客户端。
     *
     * @param delegate 原始共享 HTTP 客户端
     * @param apiKey   反向代理使用的不透明 Bearer key
     * @return 会为请求添加 Bearer 头的 HTTP 客户端，或空白 key 对应的原客户端
     * @throws NullPointerException 当 {@code delegate} 为空时
     */
    public static HttpClient withBearerAuthorization(HttpClient delegate, String apiKey) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        if (apiKey == null || apiKey.isBlank()) {
            return delegate;
        }
        return (HttpClient) Proxy.newProxyInstance(
                HttpClient.class.getClassLoader(),
                new Class<?>[]{HttpClient.class},
                (proxy, method, args) -> invokeWithBearer(delegate, apiKey, method, args));
    }

    /**
     * 动态代理的单次方法转发；同时覆盖请求工厂方法和直接执行方法。
     */
    private static Object invokeWithBearer(HttpClient delegate,
                                           String apiKey,
                                           java.lang.reflect.Method method,
                                           Object[] args) throws Throwable {
        Object[] forwardedArgs = args == null ? new Object[0] : args.clone();
        for (Object argument : forwardedArgs) {
            if (argument instanceof HttpRequest request) {
                request.header("Authorization", "Bearer " + apiKey);
            }
        }
        try {
            Object result = method.invoke(delegate, forwardedArgs);
            if (result instanceof HttpRequest request) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            return result;
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    /**
     * 调用 Ollama {@code /api/embed} 文本端点。
     *
     * <p>单条输入按 Ollama 协议发送字符串，批量输入发送字符串列表；响应列表按返回顺序
     * 绑定到 {@link Embedding} 的索引。每个向量会归一化到 {@link #DEFAULT_DIMENSIONS} 维，
     * 对短向量的尾部补 {@code 0f}，避免下游向量库收到不一致的长度。
     *
     * @param request Spring AI embedding 请求
     * @return 与请求输入数量一致的 embedding 响应；空输入返回空结果
     * @throws NullPointerException 当请求或输入列表为空时
     * @throws RuntimeException 当 Ollama HTTP 调用失败时，原始异常作为 cause 保留
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> inputs = request.getInstructions();
        Objects.requireNonNull(inputs, "EmbeddingRequest.inputs must not be null");
        if (inputs.isEmpty()) {
            log.info("Ollama image-embedding called with empty inputs, returning empty response");
            return new EmbeddingResponse(Collections.emptyList());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", defaultModel);
        body.put("input", inputs.size() == 1 ? inputs.get(0) : inputs);

        OllamaEmbeddingRawResponse raw = executeEmbeddingRequest(body);
        return toEmbeddingResponse(raw, inputs.size());
    }

    /**
     * 使用文档文本执行 embedding；Spring AI {@link Document} 不携带可直接提交给 Ollama 的像素数据。
     *
     * <p>文档文本为空或只包含空白时返回 768 维零向量并记录 WARN，而不是发起无意义的远程请求。
     * 图像文档请改用 Phase C C3 的 TEI 图像能力。
     *
     * @param document 待向量化文档
     * @return 文档文本的 embedding，或空文本对应的零向量
     * @throws NullPointerException 当文档为空时
     * @throws RuntimeException 当文本 embedding 请求失败时
     */
    @Override
    public float[] embed(Document document) {
        Objects.requireNonNull(document, "document must not be null");
        String text = document.getText();
        if (text == null || text.isBlank()) {
            log.warn("Ollama image-embedding: Document has no usable text, returning zero vector");
            return new float[DEFAULT_DIMENSIONS];
        }
        return embed(text);
    }

    /**
     * 返回默认模型的静态维度，不发起远程 probe。
     *
     * @return {@link #DEFAULT_DIMENSIONS}，即 768
     */
    @Override
    public int dimensions() {
        return DEFAULT_DIMENSIONS;
    }

    /**
     * 执行一次 Ollama 请求并把传输/序列化异常包装为统一 RuntimeException。
     *
     * @param body Ollama 请求体
     * @return Ollama 原始响应
     * @throws RuntimeException HTTP 客户端或响应反序列化失败时抛出，原始异常作为 cause
     */
    private OllamaEmbeddingRawResponse executeEmbeddingRequest(Map<String, Object> body) {
        try {
            return httpClient.post(baseUrl + EMBEDDINGS_PATH, body)
                    .header("Content-Type", "application/json")
                    .execute(OllamaEmbeddingRawResponse.class);
        } catch (Exception exception) {
            throw new RuntimeException("Ollama image embedding request failed", exception);
        }
    }

    /**
     * 将 Ollama 返回结果按输入位置映射为 Spring AI 响应。
     *
     * @param raw Ollama 原始响应
     * @param expectedInputs 请求输入数量
     * @return 位置稳定且维度固定的 embedding 响应
     */
    private EmbeddingResponse toEmbeddingResponse(OllamaEmbeddingRawResponse raw, int expectedInputs) {
        if (raw == null || raw.embeddings == null) {
            log.warn("Ollama image-embedding returned empty body: {}",
                    JsonUtils.getInstance().serialize(raw));
            return new EmbeddingResponse(zeroEmbeddings(expectedInputs));
        }

        List<Embedding> results = new ArrayList<>(expectedInputs);
        for (int index = 0; index < expectedInputs; index++) {
            List<Number> values = index < raw.embeddings.size() ? raw.embeddings.get(index) : null;
            results.add(new Embedding(toFixedDimensionVector(values), index));
        }
        return new EmbeddingResponse(results);
    }

    /**
     * 将响应向量复制到固定长度数组；短向量未覆盖的槽位保持 {@code 0f}。
     *
     * @param values Ollama 返回的数字列表
     * @return 768 维浮点向量
     */
    private static float[] toFixedDimensionVector(List<Number> values) {
        float[] vector = new float[DEFAULT_DIMENSIONS];
        if (values == null) {
            return vector;
        }
        int copyLength = Math.min(values.size(), DEFAULT_DIMENSIONS);
        for (int index = 0; index < copyLength; index++) {
            Number value = values.get(index);
            vector[index] = value == null ? 0f : value.floatValue();
        }
        return vector;
    }

    /**
     * 构造 n 个固定维度零向量占位。
     *
     * @param count 占位数量
     * @return 零向量 embedding 列表
     */
    private static List<Embedding> zeroEmbeddings(int count) {
        List<Embedding> results = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            results.add(new Embedding(new float[DEFAULT_DIMENSIONS], index));
        }
        return results;
    }

    /**
     * 去掉服务根地址末尾的斜杠，避免端点拼接出双斜杠。
     *
     * @param url 服务根地址
     * @return 不带末尾斜杠的地址
     */
    private static String stripTrailingSlash(String url) {
        int end = url.length();
        while (end > 0 && url.charAt(end - 1) == '/') {
            end--;
        }
        return end == url.length() ? url : url.substring(0, end);
    }

    /** Ollama {@code /api/embed} 顶层响应。 */
    static final class OllamaEmbeddingRawResponse {
        @JsonProperty("model")
        public String model;

        @JsonProperty("embeddings")
        public List<List<Number>> embeddings;

        @JsonProperty("total_duration")
        public Long totalDuration;

        @JsonProperty("load_duration")
        public Long loadDuration;
    }
}
