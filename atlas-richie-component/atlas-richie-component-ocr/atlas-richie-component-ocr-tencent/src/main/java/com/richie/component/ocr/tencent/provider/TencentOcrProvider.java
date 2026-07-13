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
package com.richie.component.ocr.tencent.provider;

import com.richie.component.http.core.HttpClient;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.ocr.model.OcrBlock;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrLine;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.model.Point;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.tencent.protocol.TencentOcrEnvelope;
import com.richie.component.ocr.tencent.protocol.TencentOcrPayload;
import com.richie.component.ocr.tencent.protocol.TencentRequest;
import com.richie.component.ocr.tencent.protocol.TencentResponse;
import com.richie.component.ocr.provider.AbstractOcrProvider;
import com.richie.component.ocr.tencent.config.TencentOcrProperties;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.security.MessageDigest;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 腾讯云 OCR Provider 实现，支持高精度版和标准版双模式。
 *
 * <p>通过配置 {@code action} 切换识别模式（默认高精度版）:
 * <ul>
 *   <li>{@code GeneralAccurateOCR} — 通用文字识别高精度版（推荐，99%）</li>
 *   <li>{@code GeneralBasicOCR} — 通用印刷体识别标准版（96%）</li>
 * </ul>
 *
 * <p>官方文档:
 * <ul>
 *   <li>高精度版:
 *   <a href="https://cloud.tencent.com/document/api/866/34937" target="_blank">
 *   GeneralAccurateOCR</a></li>
 *   <li>标准版:
 *   <a href="https://cloud.tencent.com/document/api/866/33526" target="_blank">
 *   GeneralBasicOCR</a></li>
 * </ul>
 *
 * <p>API 协议:
 * <ul>
 *   <li>HTTP 端点: POST {@code https://ocr.tencentcloudapi.com}</li>
 *   <li>鉴权: TC3-HMAC-SHA256 (SecretId + SecretKey)</li>
 *   <li>请求体: JSON {@code {"ImageBase64": "..."}}</li>
 *   <li>Version: 2018-11-19</li>
 * </ul>
 *
 * <p>配置方式: 由 {@code platform.component.ocr.tencent.*} 绑定到
 * {@code TencentOcrProperties}，自动配置通过构造函数直接注入 typed POJO。
 *
 * <p><b>热更新友好 (L2)</b>: 所有 vendor 配置属性（endpoint / timeoutMs / region /
 * secretId / secretKey / action）都不在构造期固化，改为在每次调用时按需通过
 * {@code liveXxx()} 方法读取 {@link TencentOcrProperties}。此种模式下：
 * <ul>
 *   <li>Provider 不会"闭锁"配置 —— 业务侧通过 Spring Cloud {@code @RefreshScope} /
 *       Nacos Config Listener 替换 {@link TencentOcrProperties} Bean 实例后，下次调用即生效</li>
 *   <li>无运行时分支、无 reflection、无 thread-local</li>
 *   <li>校验分两段：构造期 fast-fail（仅校验 {@code credentials.secret-id} /
 *       {@code credentials.secret-key}）+ 每次调用 lazy re-check（捕获配置漂移）</li>
 * </ul>
 *
 * <p>v1.1: 移除构造期固化的 6 个 vendor 配置字段，改为持有 {@link TencentOcrProperties}
 * 引用 + 每次调用 lazy 读取（liveXxx 模式），对齐 {@code AliyunOcrProvider} 模式。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
public class TencentOcrProvider extends AbstractOcrProvider<TencentRequest, TencentResponse> implements Serializable {

    /** Java 序列化版本号，保持 Provider 序列化兼容。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认腾讯云 OCR HTTP JSON 服务端点。 */
    private static final String DEFAULT_ENDPOINT = "https://ocr.tencentcloudapi.com";
    /** 默认请求超时时间，单位毫秒。 */
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    /** 默认腾讯云服务地域。 */
    private static final String DEFAULT_REGION = "ap-guangzhou";
    /** 默认腾讯云 OCR API Action。 */
    private static final String DEFAULT_ACTION = "GeneralAccurateOCR";
    /** 腾讯云 TC3 签名算法名称。 */
    private static final String TC3_ALGORITHM = "TC3-HMAC-SHA256";
    /** 腾讯云 OCR 服务名称。 */
    private static final String TC3_SERVICE = "ocr";
    /** 腾讯云 TC3 签名请求类型。 */
    private static final String TC3_REQUEST = "tc3_request";
    /** 调用腾讯云 OCR HTTP 接口的共享客户端（不是 vendor 配置，不走 props）。 */
    private final HttpClient httpClient;
    /** 腾讯云 OCR vendor 配置 Properties —— 每次调用 lazy 读取。 */
    private final TencentOcrProperties props;

    /**
     * 构造基于共享 HTTP 客户端与 typed 配置的腾讯云 OCR Provider。
     *
     * <p>仅保存 {@code props} 引用并 fast-fail 校验凭据
     * （{@code credentials.secret-id} / {@code credentials.secret-key}）；其他配置
     * 在每次调用时通过 {@code liveXxx()} 实时读取。
     *
     * @param props typed 配置（非 {@code null}）
     * @param httpClient 调用腾讯云 OCR HTTP JSON 端点的共享 HTTP 客户端，不能为 {@code null}
     * @throws OcrException.ConfigMissing {@code credentials.secret-id} 或 {@code credentials.secret-key} 缺失时抛出
     */
    public TencentOcrProvider(TencentOcrProperties props, HttpClient httpClient) {
        super();
        this.httpClient = httpClient;
        this.props = props;

        if (StringUtils.isBlank(liveSecretId())) {
            throw new OcrException.ConfigMissing("tencent", "credentials.secret-id");
        }
        if (StringUtils.isBlank(liveSecretKey())) {
            throw new OcrException.ConfigMissing("tencent", "credentials.secret-key");
        }

        log().info("Tencent OCR provider[{}] loaded: endpoint={}, region={}, timeoutMs={}",
                "tencent", liveEndpoint(), liveRegion(), liveTimeoutMs());
    }

    // --- live configuration accessors: 每次调用时实时读 props ---
    // 业务侧可以通过 Spring @RefreshScope / Nacos Config Listener 等替换 props Bean 引用实现热更新。

    private String liveEndpoint() {
        return props.getEndpoint() != null ? props.getEndpoint() : DEFAULT_ENDPOINT;
    }

    private long liveTimeoutMs() {
        return props.getTimeoutMs() > 0 ? props.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
    }

    private String liveRegion() {
        String r = props.getRegion() != null ? props.getRegion() : DEFAULT_REGION;
        if (r.isBlank()) {
            throw new OcrException.ConfigMissing("tencent", "region");
        }
        return r;
    }

    private String liveAction() {
        String a = props.getAction() != null ? props.getAction() : DEFAULT_ACTION;
        if (a.isBlank()) {
            throw new OcrException.ConfigMissing("tencent", "action");
        }
        return a;
    }

    private String liveSecretId() {
        TencentOcrProperties.Credentials c = props.getCredentials();
        return c != null ? c.getSecretId() : null;
    }

    private String liveSecretKey() {
        TencentOcrProperties.Credentials c = props.getCredentials();
        return c != null ? c.getSecretKey() : null;
    }

    // --- AbstractOcrProvider 模板实现 ---

    @Override
    protected TencentRequest toProviderRequest(OcrImage image, OcrOptions options) {
        if (image instanceof OcrImage.Bytes bytes) {
            String base64 = Base64.getEncoder().encodeToString(bytes.data());
            return new TencentRequest(base64, options);
        }
        if (image instanceof OcrImage.Url url) {
            throw new OcrException.ProviderUnavailable("tencent", null,
                    new UnsupportedOperationException("Tencent OCR requires OcrImage.Bytes; got OcrImage.Url"));
        }
        if (image instanceof OcrImage.Stream stream) {
            try (InputStream in = stream.input()) {
                byte[] data = in.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(data);
                return new TencentRequest(base64, options);
            } catch (Exception e) {
                throw new OcrException.ProviderUnavailable("tencent", null, e);
            }
        }
        throw new IllegalArgumentException(
                "Unsupported OcrImage variant: " + image.getClass().getName());
    }

    @Override
    protected TencentResponse callProvider(TencentRequest request) {
        try {
            TencentOcrPayload payload = TencentOcrPayload.of(request.imageBase64());
            // payloadStr 仅用于 TC3-HMAC-SHA256 签名（payloadHash = sha256Hex(body)）；
            // HTTP body 由平台 http 层基于 typed payload 自行序列化
            String payloadStr = JsonUtils.getInstance().serialize(payload);
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            long ts = Long.parseLong(timestamp);
            String date = Instant.ofEpochSecond(ts)
                    .atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            String endpoint = liveEndpoint();
            String region = liveRegion();
            String action = liveAction();
            String auth = buildAuthorization(liveSecretId(), liveSecretKey(), region, action,
                    endpoint, payloadStr, timestamp, date);

            long start = System.currentTimeMillis();
            HttpResponse resp = httpClient.post(endpoint, payload)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Host", extractHost(endpoint))
                    .header("X-TC-Action", action)
                    .header("X-TC-Version", "2018-11-19")
                    .header("X-TC-Region", region)
                    .header("X-TC-Timestamp", timestamp)
                    .header("Authorization", auth)
                    .timeout(Duration.ofMillis(liveTimeoutMs()))
                    .execute();
            long latencyMs = System.currentTimeMillis() - start;

            if (!resp.isSuccessful()) {
                throw new OcrException.ProviderUnavailable(
                        "tencent", resp.statusCode(),
                        new RuntimeException(resp.statusCode()
                                + " " + safeBody(resp.bodyAsString())));
            }

            TencentOcrEnvelope envelope = resp.bodyAs(TencentOcrEnvelope.class);
            TencentOcrEnvelope.ErrorBody error = envelope != null && envelope.response() != null
                    ? envelope.response().error() : null;
            if (error != null && (error.code() != null || error.message() != null)) {
                throw new OcrException.Unrecognized("tencent",
                        "tencent error " + error.code() + ": " + error.message());
            }

            return new TencentResponse(envelope, latencyMs);
        } catch (OcrException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrException.ProviderUnavailable("tencent", null, e);
        }
    }

    @Override
    protected OcrResult fromProviderResponse(TencentResponse response) {
        TencentOcrEnvelope envelope = response.body();
        TencentOcrEnvelope.Response resp = envelope != null ? envelope.response() : null;
        List<TencentOcrEnvelope.TextDetection> detections = resp != null ? resp.textDetections() : null;

        StringBuilder fullText = new StringBuilder();
        float totalConf = 0f;
        int count = 0;
        List<OcrBlock> blocks = new ArrayList<>();

        if (detections != null) {
            for (TencentOcrEnvelope.TextDetection item : detections) {
                String lineText = item.detectedText() != null ? item.detectedText() : "";
                fullText.append(lineText);

                float conf = item.confidence() != null ? item.confidence().floatValue() : 0f;
                totalConf += conf;
                count++;

                List<Point> box = parsePolygon(item.polygon());
                List<OcrLine> lines = List.of(new OcrLine(lineText, box, conf / 100f));
                blocks.add(new OcrBlock(lineText, box, conf / 100f, lines));
            }
        }

        float avgConfidence = count > 0 ? (totalConf / count) / 100f : 0f;

        Map<String, Object> metadata = new HashMap<>();
        if (envelope != null && envelope.requestId() != null) {
            metadata.put("requestId", envelope.requestId());
        }
        metadata.put("provider", "tencent");

        return new OcrResult(fullText.toString().strip(), blocks,
                Math.min(avgConfidence, 1.0f), metadata, response.latencyMs());
    }

    // --- TC3-HMAC-SHA256 签名 ---

    private String buildAuthorization(String secretId, String secretKey, String region,
                                       String action, String endpoint,
                                       String bodyJson, String timestamp, String date) {
        try {
            // 1. Hash payload
            String payloadHash = sha256Hex(bodyJson);

            // 2. CanonicalRequest (x-tc-action 必须小写)
            String actionLower = action.toLowerCase(Locale.ROOT);
            String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                    + "host:" + extractHost(endpoint) + "\n"
                    + "x-tc-action:" + actionLower + "\n";
            String signedHeaders = "content-type;host;x-tc-action";
            String canonicalRequest = "POST\n/\n\n"
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + payloadHash;
            String canonicalRequestHash = sha256Hex(canonicalRequest);

            // 3. StringToSign
            String credentialScope = date + "/" + TC3_SERVICE + "/" + TC3_REQUEST;
            String stringToSign = TC3_ALGORITHM + "\n"
                    + timestamp + "\n"
                    + credentialScope + "\n"
                    + canonicalRequestHash;

            // 4. SigningKey
            Mac hmac = Mac.getInstance("HmacSHA256");

            // kDate = HmacSHA256("TC3" + SecretKey, Date)
            SecretKeySpec keySpec = new SecretKeySpec(
                    ("TC3" + secretKey).getBytes(UTF_8), "HmacSHA256");
            hmac.init(keySpec);
            byte[] kDate = hmac.doFinal(date.getBytes(UTF_8));

            // kService = HmacSHA256(kDate, Service)
            keySpec = new SecretKeySpec(kDate, "HmacSHA256");
            hmac.init(keySpec);
            byte[] kService = hmac.doFinal(TC3_SERVICE.getBytes(UTF_8));

            // kSigning = HmacSHA256(kService, "tc3_request")
            keySpec = new SecretKeySpec(kService, "HmacSHA256");
            hmac.init(keySpec);
            byte[] kSigning = hmac.doFinal(TC3_REQUEST.getBytes(UTF_8));

            // Signature = HmacSHA256(kSigning, StringToSign)
            keySpec = new SecretKeySpec(kSigning, "HmacSHA256");
            hmac.init(keySpec);
            byte[] signature = hmac.doFinal(stringToSign.getBytes(UTF_8));
            String sigHex = HexFormat.of().formatHex(signature);

            // 5. Authorization
            return TC3_ALGORITHM + " Credential=" + secretId + "/"
                    + credentialScope + ", SignedHeaders=" + signedHeaders
                    + ", Signature=" + sigHex;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build TC3 authorization", e);
        }
    }

    // --- 辅助方法 ---

    private static String safeBody(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private static String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return "ocr.tencentcloudapi.com";
        }
    }

    private static String sha256Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static List<Point> parsePolygon(List<TencentOcrEnvelope.PolygonPoint> polygon) {
        if (polygon == null) {
            return List.of();
        }
        List<Point> points = new ArrayList<>(polygon.size());
        for (TencentOcrEnvelope.PolygonPoint p : polygon) {
            if (p != null && p.x() != null && p.y() != null) {
                points.add(new Point(p.x(), p.y()));
            }
        }
        return List.copyOf(points);
    }
}
