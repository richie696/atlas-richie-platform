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
package com.richie.component.ocr.volcano.provider;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.ocr.model.OcrBlock;
import com.richie.component.ocr.model.OcrImage;
import com.richie.component.ocr.model.OcrLine;
import com.richie.component.ocr.model.OcrOptions;
import com.richie.component.ocr.model.OcrResult;
import com.richie.component.ocr.model.Point;
import com.richie.component.ocr.volcano.config.VolcanoOcrProperties;
import com.richie.component.ocr.volcano.protocol.VolcanoOcrEnvelope;
import com.richie.component.ocr.volcano.protocol.VolcanoOcrPayload;
import com.richie.component.ocr.volcano.protocol.VolcanoRequest;
import com.richie.component.ocr.volcano.protocol.VolcanoResponse;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.ocr.exception.OcrException;
import com.richie.component.ocr.provider.AbstractOcrProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 火山引擎 OCR Provider 实现。
 *
 * <p>官方文档:
 * <a href="https://docs.volcengine.com/docs/86081/1660261" target="_blank">
 * 火山引擎 — 通用文字识别能力介绍</a>
 *
 * <p>API 协议参考: 火山引擎视觉智能 OCR v2020-08-26
 * <ul>
 *   <li>HTTP 端点: POST {@code {endpoint}/?Action=RecognizeImage&Version=2020-08-26}</li>
 *   <li>鉴权: AWS4-HMAC-SHA256 (AccessKey + SecretKey)</li>
 *   <li>请求体: JSON, 包含 {@code image_base64} 等字段</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-11
 */
public class VolcanoOcrProvider extends AbstractOcrProvider<VolcanoRequest, VolcanoResponse> {

    /** 默认火山引擎视觉智能 HTTP JSON 服务端点。 */
    private static final String DEFAULT_ENDPOINT = "https://visual.volcengineapi.com";
    /** 默认请求超时时间，单位毫秒。 */
    private static final long DEFAULT_TIMEOUT_MS = 30_000L;
    /** 默认火山引擎服务地域。 */
    private static final String DEFAULT_REGION = "cn-north-1";
    /** 火山引擎视觉智能签名服务名。 */
    private static final String SERVICE = "cv";
    /** 火山引擎请求签名算法名称。 */
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    /** 调用火山引擎 OCR HTTP 接口的共享客户端。 */
    private final HttpClient httpClient;

    /** 当前 Provider 使用的火山引擎服务端点。 */
    private final String endpoint;
    /** 当前 Provider 的请求超时时间，单位毫秒。 */
    private final long timeoutMs;
    /** 当前 Provider 使用的火山引擎服务地域。 */
    private final String region;
    /** 火山引擎 API 访问密钥 Id。 */
    private final String accessKey;
    /** 火山引擎 API 访问密钥 Secret。 */
    private final String secretKey;

    /**
     * 通过 typed {@link VolcanoOcrProperties} 配置直接构造火山引擎 OCR Provider。
     *
     * <p>vendor 配置由构造器注入 typed VolcanoOcrProperties POJO：
     * 父类构造在此处调用。
     *
     * @param props 配置属性，必填的 {@code credentials.access-key} 与 {@code credentials.secret-key} 缺失时会拒绝构造
     * @param httpClient 调用火山引擎视觉智能 HTTP JSON 端点的共享 HTTP 客户端，不能为 {@code null}
     * @throws OcrException.ConfigMissing 缺少 {@code credentials.access-key} 或
     *                                    {@code credentials.secret-key} 时抛出
     */
    public VolcanoOcrProvider(VolcanoOcrProperties props, HttpClient httpClient) {
        super();
        this.httpClient = httpClient;
        this.endpoint = blankTo(props.getEndpoint(), DEFAULT_ENDPOINT);
        this.timeoutMs = props.getTimeoutMs() != 0L ? props.getTimeoutMs() : DEFAULT_TIMEOUT_MS;
        this.region = blankTo(props.getRegion(), DEFAULT_REGION);

        VolcanoOcrProperties.Credentials creds = props.getCredentials();
        this.accessKey = creds == null ? null : creds.getAccessKey();
        this.secretKey = creds == null ? null : creds.getSecretKey();

        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new OcrException.ConfigMissing("volcano",
                    "credentials.access-key / credentials.secret-key");
        }
        log().info("Volcano OCR provider[{}] loaded: endpoint={}, region={}, timeoutMs={}",
                "volcano", endpoint, region, timeoutMs);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    // --- AbstractOcrProvider 模板实现 ---

    @Override
    protected VolcanoRequest toProviderRequest(OcrImage image, OcrOptions options) {
        if (image instanceof OcrImage.Bytes bytes) {
            String base64 = Base64.getEncoder().encodeToString(bytes.data());
            return new VolcanoRequest(base64, options);
        }
        if (image instanceof OcrImage.Url url) {
            try (InputStream in = URI.create(url.url()).toURL().openStream()) {
                byte[] data = in.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(data);
                return new VolcanoRequest(base64, options);
            } catch (Exception e) {
                throw new OcrException.ProviderUnavailable("volcano", null, e);
            }
        }
        if (image instanceof OcrImage.Stream stream) {
            try (InputStream in = stream.input()) {
                byte[] data = in.readAllBytes();
                String base64 = Base64.getEncoder().encodeToString(data);
                return new VolcanoRequest(base64, options);
            } catch (Exception e) {
                throw new OcrException.ProviderUnavailable("volcano", null, e);
            }
        }
        throw new IllegalArgumentException("Unsupported OcrImage variant: " + image.getClass().getName());
    }

    @Override
    protected VolcanoResponse callProvider(VolcanoRequest request) {
        try {
            VolcanoOcrPayload payload = VolcanoOcrPayload.ofBase64(request.imageBase64());
            // payloadStr 仅用于 AWS4-HMAC-SHA256 签名（payloadHash = sha256Hex(body)）；
            // HTTP body 由平台 http 层基于 typed payload 自行序列化
            String payloadStr = JsonUtils.getInstance().serialize(payload);
            String payloadHash = sha256Hex(payloadStr);

            Instant now = Instant.now();
            String datetime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC)
                    .format(now);
            String date = datetime.substring(0, 8);

            String auth = buildAuthorization(payloadHash, datetime, date);

            String fullUrl = endpoint + "/?Action=RecognizeImage&Version=2020-08-26";
            long start = System.currentTimeMillis();
            HttpResponse resp = httpClient.post(fullUrl, payload)
                    .header("Content-Type", "application/json")
                    .header("Host", extractHost(endpoint))
                    .header("X-Content-Sha256", payloadHash)
                    .header("X-Amz-Date", datetime)
                    .header("Authorization", auth)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .execute();
            long latencyMs = System.currentTimeMillis() - start;

            if (!resp.isSuccessful()) {
                throw new OcrException.ProviderUnavailable(
                        "volcano", resp.statusCode(),
                        new RuntimeException(resp.statusCode() + " " + safeBody(resp.bodyAsString())));
            }

            VolcanoOcrEnvelope envelope = resp.bodyAs(VolcanoOcrEnvelope.class);
            VolcanoOcrEnvelope.ErrorBody error = envelope != null && envelope.responseMetadata() != null
                    ? envelope.responseMetadata().error() : null;
            if (error != null && (error.code() != null || error.message() != null)) {
                throw new OcrException.Unrecognized("volcano",
                        "volcano error " + error.code() + ": " + error.message());
            }

            return new VolcanoResponse(envelope, latencyMs);
        } catch (OcrException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrException.ProviderUnavailable("volcano", null, e);
        }
    }

    @Override
    protected OcrResult fromProviderResponse(VolcanoResponse response) {
        VolcanoOcrEnvelope envelope = response.body();
        List<OcrBlock> blocks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        float totalConf = 0f;
        int count = 0;

        VolcanoOcrEnvelope.Result result = envelope != null ? envelope.result() : null;
        List<VolcanoOcrEnvelope.TextBlock> texts = result != null ? result.texts() : null;
        if (texts != null) {
            for (VolcanoOcrEnvelope.TextBlock item : texts) {
                String lineText = item.text() != null ? item.text() : "";
                sb.append(lineText);

                float conf = item.confidence() != null ? item.confidence().floatValue() : 0f;
                totalConf += conf;
                count++;

                List<Point> box = parseRect(item.rect());
                List<OcrLine> lines = new ArrayList<>();
                List<VolcanoOcrEnvelope.LineText> lineTexts = item.lineTexts();
                if (lineTexts != null) {
                    for (VolcanoOcrEnvelope.LineText lt : lineTexts) {
                        String ltText = lt.text() != null ? lt.text() : "";
                        float ltConf = lt.confidence() != null ? lt.confidence().floatValue() : 0f;
                        lines.add(new OcrLine(ltText, parseRect(lt.rect()), ltConf / 100f));
                    }
                }
                blocks.add(new OcrBlock(lineText, box, conf / 100f, lines));
            }
        }

        float confidence = count > 0 ? Math.min((totalConf / count) / 100f, 1.0f) : 0f;

        Map<String, Object> metadata = new HashMap<>();
        String requestId = result != null ? result.requestId() : null;
        if (requestId != null) {
            metadata.put("requestId", requestId);
        }
        metadata.put("provider", "volcano");

        return new OcrResult(sb.toString().strip(), blocks, confidence, metadata, response.latencyMs());
    }

    // --- 签名实现 ---

    /**
     * 构建 AWS4-HMAC-SHA256 签名 Authorization 头。
     */
    private String buildAuthorization(String payloadHash, String datetime, String date) {
        String canonicalQueryString = "Action=RecognizeImage&Version=2020-08-26";
        String host = extractHost(endpoint);

        String canonicalHeaders = "content-type:application/json\n"
                + "host:" + host + "\n"
                + "x-content-sha256:" + payloadHash + "\n";
        String signedHeaders = "content-type;host;x-content-sha256";

        String canonicalRequest = "POST\n/\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;

        String credentialScope = date + "/" + region + "/" + SERVICE + "/aws4_request";
        String stringToSign = ALGORITHM + "\n" + datetime + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8),
                date.getBytes(StandardCharsets.UTF_8));
        byte[] kRegion = hmacSha256(kDate, region.getBytes(StandardCharsets.UTF_8));
        byte[] kService = hmacSha256(kRegion, SERVICE.getBytes(StandardCharsets.UTF_8));
        byte[] kSigning = hmacSha256(kService, "aws4_request".getBytes(StandardCharsets.UTF_8));
        byte[] signature = hmacSha256(kSigning, stringToSign.getBytes(StandardCharsets.UTF_8));
        String sigHex = HexFormat.of().formatHex(signature);

        return ALGORITHM + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + sigHex;
    }

    // --- 工具方法 ---

    private static String safeBody(String body) {
        if (body == null) return "<empty>";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HmacSHA256 not available", e);
        }
    }

    private static String extractHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return url.replaceAll("https?://", "").replaceAll("/.*", "");
        }
    }

    private static List<Point> parseRect(VolcanoOcrEnvelope.Rect rect) {
        if (rect == null
                || rect.x() == null || rect.y() == null
                || rect.width() == null || rect.height() == null) {
            return List.of();
        }
        int x = rect.x();
        int y = rect.y();
        int w = rect.width();
        int h = rect.height();
        return List.of(
                new Point(x, y),
                new Point(x + w, y),
                new Point(x + w, y + h),
                new Point(x, y + h)
        );
    }
}
