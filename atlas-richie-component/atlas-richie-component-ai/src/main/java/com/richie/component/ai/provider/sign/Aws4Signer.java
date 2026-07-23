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
package com.richie.component.ai.provider.sign;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * AWS4-HMAC-SHA256 签名工具（纯 JDK 实现，无第三方依赖）。
 * <p>
 * 移植自 {@code atlas-richie-component-ocr-volcano/.../VolcanoOcrProvider.buildAuthorization(...)}。
 * <p>
 * 主要复用场景：火山引擎视觉 / VikingDB 等 AWS4 鉴权风格的 vendor；语音合成（豆包 openspeech）走
 * X-Api-Key 鉴权，本工具仅为后续可能的鉴权兼容型 vendor 预留。
 * <p>
 * 该工具无状态 —— 同一个实例可被任意线程安全复用；构造函数为 {@code public}，便于
 * Spring 直接 {@code @Bean} 暴露单例或业务侧按需 {@code new Aws4Signer()} 持有。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-20
 */
public class Aws4Signer {

    /** AWS4 签名算法名称。 */
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";
    /** AWS4 签名链路终止 token。 */
    private static final String AWS4_REQUEST = "aws4_request";
    /** 本工具对 payload 哈希使用的请求头名称（火山视觉风格）。 */
    private static final String X_CONTENT_SHA256 = "x-content-sha256";

    public Aws4Signer() {
        // 无状态 Bean，public ctor 允许直接 new / Spring 容器注入
    }

    /**
     * 计算 AWS4-HMAC-SHA256 {@code Authorization} 头部值。
     * <p>
     * 本实现的固定约定：
     * <ul>
     *   <li>HTTP 方法：{@code POST}</li>
     *   <li>canonical URI：{@code /}</li>
     *   <li>canonical query string：空（语音 / 重排类 JSON 接口无 query）</li>
     *   <li>canonical headers：
     *       {@code content-type:application/json} + {@code host:<host>} + {@code x-content-sha256:<payloadHash>}</li>
     *   <li>signed headers：{@code content-type;host;x-content-sha256}</li>
     * </ul>
     * <p>
     * 算法步骤：
     * <ol>
     *   <li>{@code canonicalRequest = "POST\n/\n\n<canonical_headers>\n<signed_headers>\n<payloadHash>"}</li>
     *   <li>{@code credentialScope = <date>/<region>/<service>/aws4_request}</li>
     *   <li>{@code stringToSign = "AWS4-HMAC-SHA256\n<datetime>\n<scope>\n<sha256Hex(canonicalRequest)>"}</li>
     *   <li>链式 HMAC-SHA256 派生 {@code SigningKey}（{@code AWS4+secretKey} → date → region → service → aws4_request）</li>
     *   <li>{@code Signature = hex(HmacSHA256(SigningKey, stringToSign))}</li>
     *   <li>返回 {@code AWS4-HMAC-SHA256 Credential=<accessKey>/<scope>, SignedHeaders=..., Signature=<hex>}</li>
     * </ol>
     *
     * @param accessKey   AccessKey ID（火山引擎 IAM AccessKey）
     * @param secretKey   Secret Access Key
     * @param service     服务名（如 {@code cv} 视觉 / {@code ml_platform} 机器学习平台）
     * @param region      区域（如 {@code cn-north-1}）
     * @param endpoint    完整端点 URL（用于解析 host 头）
     * @param payloadHash 请求体 SHA-256 十六进制哈希（火山引擎约定通过 {@code X-Content-Sha256} 头传递）
     * @param datetime    UTC 时间戳串，{@code yyyyMMdd'T'HHmmss'Z'} 格式（火山引擎 {@code X-Amz-Date}）
     * @return 完整的 {@code Authorization} 头部值
     */
    public String buildAuthorization(String accessKey, String secretKey, String service, String region,
                                     String endpoint, String payloadHash, String datetime) {
        try {
            String date = datetime.substring(0, 8);
            String host = extractHost(endpoint);

            String canonicalHeaders = "content-type:application/json\n"
                    + "host:" + host + "\n"
                    + X_CONTENT_SHA256 + ":" + payloadHash + "\n";
            String signedHeaders = "content-type;host;" + X_CONTENT_SHA256;

            // 语音 / 重排 JSON 接口默认无 query string；callers 如有需要可自行扩展。
            String canonicalRequest = "POST\n/\n\n"
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + payloadHash;

            String credentialScope = date + "/" + region + "/" + service + "/" + AWS4_REQUEST;
            String stringToSign = ALGORITHM + "\n" + datetime + "\n"
                    + credentialScope + "\n"
                    + sha256Hex(canonicalRequest);

            byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8),
                    date.getBytes(StandardCharsets.UTF_8));
            byte[] kRegion = hmacSha256(kDate, region.getBytes(StandardCharsets.UTF_8));
            byte[] kService = hmacSha256(kRegion, service.getBytes(StandardCharsets.UTF_8));
            byte[] kSigning = hmacSha256(kService, AWS4_REQUEST.getBytes(StandardCharsets.UTF_8));
            byte[] signature = hmacSha256(kSigning, stringToSign.getBytes(StandardCharsets.UTF_8));
            String sigHex = HexFormat.of().formatHex(signature);

            return ALGORITHM + " Credential=" + accessKey + "/" + credentialScope
                    + ", SignedHeaders=" + signedHeaders
                    + ", Signature=" + sigHex;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build AWS4 authorization", e);
        }
    }

    /**
     * 计算 body SHA-256 十六进制哈希（用于 {@code X-Content-Sha256} 头 + AWS4 签名 payloadHash）。
     *
     * @param body 请求体字符串（{@code null} 视为空串）
     * @return 小写十六进制 SHA-256
     */
    public String sha256Hex(String body) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 生成 AWS4 / 火山引擎风格的 UTC datetime 字符串：{@code yyyyMMdd'T'HHmmss'Z'}。
     *
     * @return 当前 UTC datetime
     */
    public String nowDatetime() {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
    }

    // ---- 内部工具 ----

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
        if (url == null) {
            return "";
        }
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return url.replaceAll("https?://", "").replaceAll("/.*", "");
        }
    }
}
