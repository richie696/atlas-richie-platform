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
package com.richie.component.ocr.baidu.provider;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.ocr.baidu.config.BaiduOcrProperties;
import com.richie.component.ocr.baidu.protocol.BaiduOcrEnvelope;
import com.richie.component.ocr.baidu.protocol.BaiduOcrPayload;
import com.richie.component.ocr.baidu.protocol.BaiduRequest;
import com.richie.component.ocr.baidu.protocol.BaiduResponse;
import com.richie.component.ocr.baidu.protocol.BaiduTokenResponse;
import com.richie.component.ocr.model.Languages;
import com.richie.component.ocr.model.OcrBlock;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrLine;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.provider.AbstractOcrProvider;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 百度智能云 OCR Provider 实现。
 *
 * <p>协议参考: 百度 OCR REST API v2
 * <p>官方文档:
 * <a href="https://ai.baidu.com/ai-doc/OCR/rkibizxtw" target="_blank">
 * 百度 AI 开放平台 — 通用文字识别 (general_basic)</a>
 * <ul>
 *   <li>通用文字识别: POST {endpoint}/rest/2.0/ocr/v1/general_basic</li>
 *   <li>access_token: OAuth2 流程, 缓存 30 天, 提前 5 分钟刷新</li>
 *   <li>请求体: {@code application/x-www-form-urlencoded}, 字段 {@code image}（base64，无 data: 前缀）
 *       或 {@code url}（公网 URL），加 {@code language_type} 等可选参数</li>
 *   <li>响应: JSON, 顶层 {@code words_result: [{words: "..."}]} + {@code words_result_num}; 错误时
 *       {@code error_code} + {@code error_msg}</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-12
 */
public class BaiduOcrProvider extends AbstractOcrProvider<BaiduRequest, BaiduResponse> {

    /** 默认百度智能云 OCR API 根端点。 */
    private static final String DEFAULT_ENDPOINT = "https://aip.baidubce.com";
    /** 默认请求超时时间，单位毫秒。 */
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    /** access_token 提前刷新余量（30 天有效期，提前 5 分钟刷）。 */
    private static final long TOKEN_REFRESH_MARGIN_MS = 5L * 60L * 1000L;

    /** 调用百度 OCR 与 OAuth2 接口的共享客户端（不是 vendor 配置，不走 props）。 */
    private final HttpClient httpClient;
    /** 百度 OCR vendor 配置 Properties —— 每次调用 lazy 读取。 */
    private final BaiduOcrProperties props;

    /** 当前缓存的百度 OAuth2 access_token（运行时缓存，不是 vendor 配置）。 */
    private volatile String accessToken;
    /** 当前 access_token 的过期时间（运行时缓存）。 */
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    /**
     * 构造基于 typed Properties 与共享 HTTP 客户端的百度智能云 OCR Provider。
     *
     * <p>仅保存 {@code props} 引用并 fast-fail 校验必填项
     * （{@code credentials.api-key} / {@code credentials.secret-key}）；其他配置（endpoint /
     * timeout-ms）在每次调用时通过 {@code liveXxx()} 实时读取，便于业务侧通过 Spring Cloud
     * {@code @RefreshScope} / Nacos Config Listener 实现热更新。
     *
     * @param props 百度智能云 OCR 配置（endpoint / timeout-ms / api-key / secret-key），不能为 {@code null}
     * @param httpClient 调用百度 OCR 识别端点和 OAuth2 Token 端点的共享 HTTP 客户端，不能为 {@code null}
     * @throws OcrException.ConfigMissing 缺少 {@code credentials.api-key} 或
     *                                    {@code credentials.secret-key} 时抛出
     */
    public BaiduOcrProvider(BaiduOcrProperties props, HttpClient httpClient) {
        super();
        this.httpClient = httpClient;
        this.props = props;

        if (liveApiKey() == null || liveApiKey().isBlank()) {
            throw new OcrException.ConfigMissing("baidu", "credentials.api-key");
        }
        if (liveSecretKey() == null || liveSecretKey().isBlank()) {
            throw new OcrException.ConfigMissing("baidu", "credentials.secret-key");
        }
    }

    // --- live configuration accessors: 每次调用时实时读 props ---
    // 业务侧可以通过 Spring @RefreshScope / Nacos Config Listener 等替换 props Bean 引用实现热更新。

    private String liveEndpoint() {
        String ep = props.getEndpoint();
        return (ep != null && !ep.isBlank()) ? ep : DEFAULT_ENDPOINT;
    }

    private long liveTimeoutMs() {
        return props.getTimeoutMs() > 0 ? props.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
    }

    private String liveApiKey() {
        return props.getApiKey();
    }

    private String liveSecretKey() {
        return props.getSecretKey();
    }

    // ---- AbstractOcrProvider 模板实现 ----

    @Override
    protected BaiduRequest toProviderRequest(OcrImage image, OcrOptions options) {
        if (image instanceof OcrImage.Bytes bytes) {
            String base64 = Base64.getEncoder().encodeToString(bytes.data());
            return new BaiduRequest(null, base64, resolveLanguageType(options.languages()), options);
        }
        if (image instanceof OcrImage.Url url) {
            return new BaiduRequest(url.url(), null, resolveLanguageType(options.languages()), options);
        }
        if (image instanceof OcrImage.Stream stream) {
            try (InputStream in = stream.input()) {
                byte[] data = in.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(data);
                return new BaiduRequest(null, base64, resolveLanguageType(options.languages()), options);
            } catch (Exception e) {
                throw new OcrException.ProviderUnavailable("baidu", null, e);
            }
        }
        throw new IllegalArgumentException("Unsupported OcrImage variant: " + image.getClass().getName());
    }

    @Override
    protected BaiduResponse callProvider(BaiduRequest request) {
        try {
            String token = obtainAccessToken();

            long start = System.currentTimeMillis();

            BaiduOcrPayload payload = request.imageUrl() != null
                    ? BaiduOcrPayload.ofUrl(token, request.imageUrl(), request.languageType(), request.options())
                    : BaiduOcrPayload.ofBase64(token, request.imageBase64(), request.languageType(), request.options());

            HttpResponse httpResp = httpClient.post(liveEndpoint() + "/rest/2.0/ocr/v1/general_basic", payload.toFormBody())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofMillis(liveTimeoutMs()))
                    .execute();

            long latencyMs = System.currentTimeMillis() - start;

            if (!httpResp.isSuccessful()) {
                throw new OcrException.ProviderUnavailable(
                        "baidu", httpResp.statusCode(),
                        new RuntimeException(httpResp.statusCode() + " " + safeBody(httpResp.bodyAsString())));
            }

            BaiduOcrEnvelope envelope = httpResp.bodyAs(BaiduOcrEnvelope.class);
            Integer errCode = envelope != null ? envelope.errorCode() : null;
            if (errCode != null && errCode != 0) {
                String errMsg = envelope.errorMsg() != null ? envelope.errorMsg() : "<no error_msg>";
                throw new OcrException.Unrecognized("baidu",
                        "baidu error_code=" + errCode + " error_msg=" + errMsg);
            }

            return new BaiduResponse(envelope, latencyMs);
        } catch (OcrException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrException.ProviderUnavailable("baidu", null, e);
        }
    }

    @Override
    protected OcrResult fromProviderResponse(BaiduResponse response) {
        BaiduOcrEnvelope envelope = response.body();

        // 百度通用文字识别响应: words_result: [{words: "..."}], words_result_num: N
        List<OcrBlock> blocks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int wordCount = 0;

        List<BaiduOcrEnvelope.WordItem> words = envelope != null ? envelope.wordsResult() : null;
        if (words != null) {
            for (BaiduOcrEnvelope.WordItem item : words) {
                String word = item.words() != null ? item.words() : "";
                if (!text.isEmpty()) text.append('\n');
                text.append(word);
                blocks.add(new OcrBlock(word, List.of(), 1.0f,
                        List.of(new OcrLine(word, List.of(), 1.0f))));
                wordCount++;
            }
        }

        float overallConfidence = 0f;

        Map<String, Object> metadata = new HashMap<>();
        if (envelope != null) {
            if (envelope.wordsResultNum() != null) {
                metadata.put("words_result_num", envelope.wordsResultNum());
            }
            if (envelope.logId() != null) {
                try {
                    metadata.put("log_id", Long.parseLong(envelope.logId()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        metadata.put("provider", "baidu");

        return new OcrResult(text.toString(), blocks, overallConfidence, metadata, response.latencyMs());
    }

    // ---- access_token 缓存 ----

    /**
     * 拉取并缓存 access_token。百度返回 30 天有效期, 提前 5 分钟刷新以避免边界 race。
     */
    private String obtainAccessToken() {
        String cached = accessToken;
        Instant now = Instant.now();
        if (cached != null && tokenExpiresAt.isAfter(now.plusMillis(TOKEN_REFRESH_MARGIN_MS))) {
            return cached;
        }

        synchronized (this) {
            if (accessToken != null && tokenExpiresAt.isAfter(now.plusMillis(TOKEN_REFRESH_MARGIN_MS))) {
                return accessToken;
            }
            try {
                String tokenUrl = liveEndpoint()
                        + "/oauth/2.0/token?grant_type=client_credentials"
                        + "&client_id=" + URLEncoder.encode(liveApiKey(), StandardCharsets.UTF_8)
                        + "&client_secret=" + URLEncoder.encode(liveSecretKey(), StandardCharsets.UTF_8);

                HttpResponse resp = httpClient.get(tokenUrl)
                        .timeout(Duration.ofSeconds(10))
                        .execute();
                if (!resp.isSuccessful()) {
                    throw new OcrException.ProviderUnavailable("baidu", resp.statusCode(),
                            new RuntimeException("oauth failed: " + resp.statusCode() + " " + safeBody(resp.bodyAsString())));
                }
                BaiduTokenResponse tokenResp = resp.bodyAs(BaiduTokenResponse.class);
                String token = tokenResp != null ? tokenResp.accessToken() : null;
                long expiresIn = tokenResp != null && tokenResp.expiresIn() != null
                        ? tokenResp.expiresIn() : 2592000L;
                if (token == null) {
                    String err = tokenResp != null && tokenResp.error() != null
                            ? tokenResp.error() : "<no error>";
                    throw new OcrException.ProviderUnavailable("baidu", null,
                            new RuntimeException("oauth response missing access_token: " + err));
                }
                this.accessToken = token;
                this.tokenExpiresAt = now.plusMillis(expiresIn * 1000L);
                log().debug("Baidu access_token refreshed, expires at {}", tokenExpiresAt);
                return token;
            } catch (OcrException e) {
                throw e;
            } catch (Exception e) {
                throw new OcrException.ProviderUnavailable("baidu", null, e);
            }
        }
    }

    private static String resolveLanguageType(java.util.Set<Languages> languages) {
        if (languages == null || languages.isEmpty()) return "CHN_ENG";
        Languages lang = languages.iterator().next();
        return switch (lang) {
            case ENGLISH, DIGITS_ONLY, LATIN -> "ENG";
            case CHINESE_TRADITIONAL -> "CHT_ENG";
            case JAPANESE -> "JAP";
            case KOREAN -> "KOR";
            case ARABIC -> "ARA";
            case RUSSIAN -> "RUS";
            default -> "CHN_ENG";
        };
    }

    private static String safeBody(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }
}
