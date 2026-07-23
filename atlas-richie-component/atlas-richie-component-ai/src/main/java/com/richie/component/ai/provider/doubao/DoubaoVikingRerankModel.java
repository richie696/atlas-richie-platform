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

import com.richie.component.ai.provider.support.JsonSafe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.RerankRequest;
import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;
import com.richie.context.utils.data.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 火山引擎 VikingDB 重排序（Rerank）模型适配器 —— 字节跳动豆包系。
 * <p>
 * 调用 VikingDB 知识库 Rerank 端点
 * {@code POST /api/knowledge/service/rerank}（{@code Host: api-knowledgebase.mlp.cn-beijing.volces.com}），
 * 鉴权使用火山引擎 AK/SK HMAC-SHA256 签名（AWS SigV4 变体）。
 * <p>
 * 请求体形态（与 Cohere 风格不兼容 —— 响应为 {@code float64[]} 位置对齐, 而非引用配对）：
 * <pre>{@code
 * {
 *   "rerank_model": "base-multilingual-rerank",
 *   "datas": [
 *     { "query": "...", "content": "document 1" },
 *     { "query": "...", "content": "document 2" }
 *   ]
 * }
 * }</pre>
 * 响应体形态（位置对齐）：
 * <pre>{@code
 * {
 *   "code": 0,
 *   "message": "success",
 *   "request_id": "...",
 *   "data": {
 *     "scores": [0.95, 0.23, 0.72],
 *     "token_usage": 100
 *   }
 * }
 * }</pre>
 * 本适配器将 {@code data.scores} 按位置回填到 {@link RerankResult}（index = 原序, document = 回填原文）。
 * <p>
 * <b>为什么使用 JDK {@link HttpClient} 而非组件 {@code com.richie.component.http.core.HttpClient}：</b>
 * 此 API 需要 HMAC-SHA256 签名, 签名计算依赖请求体字节的 SHA-256 哈希，而组件 HttpClient 的 {@code .future(Map)}
 * 在内部序列化请求体，外部无法在构建阶段获得序列化后的字节用于签名字段
 * {@code X-Content-Sha256} 与鉴权头 {@code Authorization} 。使用 JDK HttpClient 可让本类
 * 自包含签名逻辑，不破坏组件 HttpClient 的封装。本类与组件 HttpClient 对齐的项目规范
 * （异常语义、日志级别、超时配置）保持一致。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class DoubaoVikingRerankModel implements RerankModel {

    // ===== 端点常量 =====

    /** VikingDB 知识库 Rerank REST 路径。 */
    public static final String RERANK_PATH = "/api/knowledge/service/rerank";

    /** 默认 Rerank 主机。 */
    public static final String DEFAULT_HOST = "api-knowledgebase.mlp.cn-beijing.volces.com";

    /** 默认协议。 */
    public static final String DEFAULT_SCHEME = "https";

    /** 默认服务区域（火山引擎固定 {@code cn-north-1}）。 */
    public static final String DEFAULT_REGION = "cn-north-1";

    /** VikingDB Rerank 服务名（固定 {@code air}）。 */
    public static final String SERVICE_NAME = "air";

    /** 当 {@link RerankRequest#getModel()} 为空时使用的默认模型。 */
    public static final String DEFAULT_MODEL = "base-multilingual-rerank";

    // ===== 签名常量 =====

    /** 算法前缀。 */
    public static final String ALGORITHM = "HMAC-SHA256";

    /** 签名密钥前缀（AWS SigV4 兼容）。 */
    private static final String AWS4_PREFIX = "AWS4";

    /** 请求体签名请求头。 */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** 时间头。 */
    public static final String HEADER_X_DATE = "X-Date";

    /** 请求体 SHA-256 摘要头。 */
    public static final String HEADER_X_CONTENT_SHA256 = "X-Content-Sha256";

    /** X-Date 格式 {@code yyyyMMddTHHmmssZ}。 */
    private static final DateTimeFormatter X_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    // ===== 实例字段 =====

    private final String accessKey;
    private final String secretKey;
    private final String host;
    private final String region;
    private final String defaultModel;
    private final HttpClient jdkClient;

    /**
     * 公开构造器（自动创建 JDK HttpClient）。
     *
     * @param cfg VikingDB Rerank 配置
     */
    public DoubaoVikingRerankModel(RerankModelConfig cfg) {
        this(cfg, HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build());
    }

    /**
     * 包内构造器，允许注入 JDK HttpClient 用于测试。
     *
     * @param cfg       VikingDB Rerank 配置
     * @param jdkClient JDK HttpClient 实例
     */
    DoubaoVikingRerankModel(RerankModelConfig cfg, HttpClient jdkClient) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        Objects.requireNonNull(jdkClient, "jdkClient must not be null");
        this.accessKey = Objects.requireNonNull(cfg.getAccessKey(),
                "accessKey must not be null for DoubaoVikingRerankModel");
        this.secretKey = Objects.requireNonNull(cfg.getSecretKey(),
                "secretKey must not be null for DoubaoVikingRerankModel");
        this.host = (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank())
                ? DEFAULT_HOST : cfg.getBaseUrl();
        this.region = (cfg.getRegion() == null || cfg.getRegion().isBlank())
                ? DEFAULT_REGION : cfg.getRegion();
        this.defaultModel = (cfg.getModel() == null || cfg.getModel().isBlank())
                ? DEFAULT_MODEL : cfg.getModel();
        this.jdkClient = jdkClient;
    }

    // ===== RerankModel 接口实现 =====

    @Override
    public RerankResponse rerank(RerankRequest request) {
        long start = System.currentTimeMillis();
        try {
            RerankResponse resp = rerankAsync(request).get();
            return resp.withDuration(System.currentTimeMillis() - start);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Doubao Viking rerank interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Doubao Viking rerank failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<RerankResponse> rerankAsync(RerankRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getQuery(), "query must not be null");
        Objects.requireNonNull(request.getDocuments(), "documents must not be null");
        if (request.getDocuments().isEmpty()) {
            return CompletableFuture.completedFuture(
                    RerankResponse.succeed(Collections.emptyList(), Clock.systemUTC()));
        }

        // 1. 构建请求体
        Map<String, Object> bodyMap = buildRequestBody(request);
        String bodyJson = JsonUtils.getInstance().serialize(bodyMap);
        byte[] bodyBytes = bodyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // 2. 计算签名所需字段
        String xDate = ZonedDateTime.now(java.time.ZoneOffset.UTC).format(X_DATE_FMT);
        String xContentSha256 = sha256Hex(bodyBytes);

        String shortDate = xDate.substring(0, 8);
        String credentialScope = shortDate + "/" + region + "/" + SERVICE_NAME + "/request";

        // 3. 构建 CanonicalRequest
        String canonicalHeaders =
                "content-type:application/json\n"
                        + "host:" + host + "\n"
                        + "x-content-sha256:" + xContentSha256 + "\n"
                        + "x-date:" + xDate + "\n";
        String signedHeaders = "content-type;host;x-content-sha256;x-date";

        String canonicalString = "POST\n"
                + RERANK_PATH + "\n"
                + "\n"           // 空 query string
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + xContentSha256;

        // 4. 构建 StringToSign
        String canonicalHash = sha256Hex(canonicalString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String stringToSign = ALGORITHM + "\n"
                + xDate + "\n"
                + credentialScope + "\n"
                + canonicalHash;

        // 5. 派生签名密钥
        byte[] kDate = hmacSha256((AWS4_PREFIX + secretKey).getBytes(java.nio.charset.StandardCharsets.UTF_8), shortDate);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, SERVICE_NAME);
        byte[] kSigning = hmacSha256(kService, "request");

        // 6. 计算签名
        String signature = hmacSha256Hex(kSigning, stringToSign);

        // 7. 组装 Authorization 头
        String authHeader = ALGORITHM + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        // 8. 发起 HTTPS 请求（JDK HttpClient）
        String url = DEFAULT_SCHEME + "://" + host + RERANK_PATH;

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                // Host 头由 JDK HttpClient 自动根据 URI 设置，不显式添加
                .header(HEADER_X_DATE, xDate)
                .header(HEADER_X_CONTENT_SHA256, xContentSha256)
                .header(HEADER_AUTHORIZATION, authHeader)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                .build();

        long start = System.currentTimeMillis();

        return jdkClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> RerankResponse.succeed(
                        parseResponse(response, request.getDocuments()), Clock.systemUTC())
                        .withDuration(System.currentTimeMillis() - start));
    }

    // ===== 请求体构建 =====

    /**
     * 把 {@link RerankRequest} 装配为 VikingDB Rerank 请求体。
     * <p>
     * 字段映射：
     * <ul>
     *   <li>{@code rerank_model}：优先取 {@link RerankRequest#getModel()}，否则用构造期默认模型。</li>
     *   <li>{@code datas}：每个文档转为一个 {@code {query, content}} 对；query 为请求的统一查询。</li>
     * </ul>
     */
    Map<String, Object> buildRequestBody(RerankRequest request) {
        String model = (request.getModel() == null || request.getModel().isBlank())
                ? defaultModel
                : request.getModel();

        String query = request.getQuery();
        List<Map<String, Object>> datas = new ArrayList<>(request.getDocuments().size());
        for (String doc : request.getDocuments()) {
            Map<String, Object> item = new HashMap<>();
            item.put("query", query);
            item.put("content", doc);
            datas.add(item);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("rerank_model", model);
        body.put("datas", datas);
        return body;
    }

    // ===== 响应解析 =====

    /**
     * 解析 VikingDB Rerank HTTP 响应为 {@link RerankResult} 列表。
     * <p>
     * 响应格式 {@code { code, message, request_id, data: { scores: [...], token_usage } }}。
     * {@code code == 0} 时取 {@code data.scores} 按位置回填；
     * 非 0 code / 缺失 data / 缺失 scores 均退化为空列表。
     */
    private List<RerankResult> parseResponse(HttpResponse<String> response, List<String> documents) {
        int status = response.statusCode();
        String body = response.body();
        if (status != 200) {
            log.warn("Doubao Viking rerank HTTP {}: {}", status, body);
            return Collections.emptyList();
        }
        if (body == null || body.isBlank()) {
            log.warn("Doubao Viking rerank returned empty body");
            return Collections.emptyList();
        }

        VikingRerankRawResponse raw;
        try {
            raw = JsonSafe.parseObject(body, VikingRerankRawResponse.class);
        } catch (Exception e) {
            log.warn("Doubao Viking rerank deserialization failed: {}", e.getMessage());
            return Collections.emptyList();
        }

        if (raw.code != 0 || raw.data == null || raw.data.scores == null) {
            if (raw.code != 0) {
                log.warn("Doubao Viking rerank business error: code={} message={} request_id={}",
                        raw.code, raw.message, raw.requestId);
            }
            return Collections.emptyList();
        }

        double[] scores = raw.data.scores;
        List<RerankResult> results = new ArrayList<>(scores.length);
        for (int i = 0; i < scores.length && i < documents.size(); i++) {
            results.add(new RerankResult(i, documents.get(i), scores[i]));
        }
        return results;
    }

    // ===== HMAC-SHA256 签名工具 =====

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    private static String hmacSha256Hex(byte[] key, String data) {
        return bytesToHex(hmacSha256(key, data));
    }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return bytesToHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    // ===== 响应 DTO =====

    static class VikingRerankRawResponse {
        @JsonProperty("code")
        public int code;

        @JsonProperty("message")
        public String message;

        @JsonProperty("request_id")
        public String requestId;

        @JsonProperty("data")
        public VikingRerankRawData data;
    }

    static class VikingRerankRawData {
        @JsonProperty("scores")
        public double[] scores;

        @JsonProperty("token_usage")
        public int tokenUsage;
    }
}
