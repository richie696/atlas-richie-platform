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
 * 腾讯云 TC3-HMAC-SHA256 签名工具（纯 JDK 实现，无第三方依赖）。
 * <p>
 * 把 {@code atlas-richie-component-ocr-tencent/.../TencentOcrProvider.buildAuthorization(...)}
 * 的算法移植成可复用 Bean，供 TTS / STT 厂商适配器（混元）以构造参数形式注入调用。
 * <p>
 * 官方文档：<a href="https://cloud.tencent.com/document/api/1729/101843">语音合成 TC3 签名</a>。
 * <p>
 * 该工具无状态 —— 同一个实例可被任意线程安全复用；构造函数为 {@code public}，便于
 * Spring 通过 {@code @Bean} 暴露单例；业务侧也可按需 {@code new Tc3Signer()} 直接持有。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-20
 */
public class Tc3Signer {

    /** 腾讯云 TC3 签名算法名称。 */
    private static final String TC3_ALGORITHM = "TC3-HMAC-SHA256";
    /** 腾讯云 TC3 签名固定终止 scope。 */
    private static final String TC3_REQUEST = "tc3_request";

    public Tc3Signer() {
        // 无状态 Bean，public ctor 允许直接 new / Spring 容器注入
    }

    /**
     * 计算腾讯云 TC3-HMAC-SHA256 {@code Authorization} 头部值。
     * <p>
     * 算法步骤：
     * <ol>
     *   <li>{@code payloadHash = sha256Hex(bodyJson)}</li>
     *   <li>构造 {@code CanonicalRequest}：
     *       {@code POST\n/\n\n(content-type;host;x-tc-action)\n\n<signed_headers>\n<payloadHash>}</li>
     *   <li>构造 {@code StringToSign}：
     *       {@code TC3-HMAC-SHA256\n<timestamp>\n<credentialScope>\n<sha256Hex(canonicalRequest)>}</li>
     *   <li>链式 HMAC-SHA256 派生 {@code SigningKey}（TC3+secretKey → date → service → tc3_request）</li>
     *   <li>{@code Signature = hex(HmacSHA256(SigningKey, StringToSign))}</li>
     *   <li>返回 {@code TC3-HMAC-SHA256 Credential=<secretId>/<scope>, SignedHeaders=..., Signature=<hex>}</li>
     * </ol>
     *
     * @param secretId   腾讯云 SecretId
     * @param secretKey  腾讯云 SecretKey
     * @param service    服务名（如 {@code tts} / {@code asr} / {@code ocr}）
     * @param region     区域（如 {@code ap-guangzhou}）
     * @param action     Action 名（如 {@code TextToVoice} / {@code SentenceRecognition}）
     * @param endpoint   完整端点 URL（如 {@code https://tts.tencentcloudapi.com}），用于解析 host 头
     * @param bodyJson   请求体 JSON 串（用于计算 {@code payloadHash}）
     * @param timestamp  客户端秒级 Unix 时间戳；同时用于 {@code X-TC-Timestamp} 头
     * @return 完整的 {@code Authorization} 头部值
     */
    public String buildAuthorization(String secretId, String secretKey, String service, String region,
                                     String action, String endpoint, String bodyJson, long timestamp) {
        try {
            String date = dateStr(timestamp);

            // 1. Hash payload.
            String payloadHash = sha256Hex(bodyJson == null ? "" : bodyJson);

            // 2. CanonicalRequest (x-tc-action header value 必须小写).
            String actionLower = action == null ? "" : action.toLowerCase(java.util.Locale.ROOT);
            String host = extractHost(endpoint);
            String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                    + "host:" + host + "\n"
                    + "x-tc-action:" + actionLower + "\n";
            String signedHeaders = "content-type;host;x-tc-action";
            String canonicalRequest = "POST\n/\n\n"
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + payloadHash;
            String canonicalRequestHash = sha256Hex(canonicalRequest);

            // 3. StringToSign.
            String credentialScope = date + "/" + service + "/" + TC3_REQUEST;
            String stringToSign = TC3_ALGORITHM + "\n"
                    + timestamp + "\n"
                    + credentialScope + "\n"
                    + canonicalRequestHash;

            // 4. SigningKey（链式 HMAC-SHA256）.
            Mac mac = Mac.getInstance("HmacSHA256");

            // kDate = HmacSHA256("TC3" + SecretKey, Date)
            SecretKeySpec keySpec = new SecretKeySpec(
                    ("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] kDate = mac.doFinal(date.getBytes(StandardCharsets.UTF_8));

            // kService = HmacSHA256(kDate, Service)
            keySpec = new SecretKeySpec(kDate, "HmacSHA256");
            mac.init(keySpec);
            byte[] kService = mac.doFinal(service.getBytes(StandardCharsets.UTF_8));

            // kSigning = HmacSHA256(kService, "tc3_request")
            keySpec = new SecretKeySpec(kService, "HmacSHA256");
            mac.init(keySpec);
            byte[] kSigning = mac.doFinal(TC3_REQUEST.getBytes(StandardCharsets.UTF_8));

            // Signature = HmacSHA256(kSigning, StringToSign)
            keySpec = new SecretKeySpec(kSigning, "HmacSHA256");
            mac.init(keySpec);
            byte[] signature = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sigHex = HexFormat.of().formatHex(signature);

            // 5. Authorization header.
            return TC3_ALGORITHM + " Credential=" + secretId + "/"
                    + credentialScope + ", SignedHeaders=" + signedHeaders
                    + ", Signature=" + sigHex;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build TC3 authorization", e);
        }
    }

    /**
     * 把秒级 Unix 时间戳格式化为 UTC {@code yyyy-MM-dd} 字符串（用于 TC3 credential scope 与
     * {@code X-TC-Date} 头）。
     *
     * @param timestamp 秒级 Unix 时间戳
     * @return UTC 日期串（如 {@code 2026-07-20}）
     */
    public String dateStr(long timestamp) {
        return Instant.ofEpochSecond(timestamp)
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    // ---- 内部工具 ----

    private static String sha256Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
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
