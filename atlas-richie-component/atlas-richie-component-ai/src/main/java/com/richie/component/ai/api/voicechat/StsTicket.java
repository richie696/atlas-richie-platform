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
package com.richie.component.ai.api.voicechat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 统一的 STS 短时凭证票面（vendor 5 种认证域收敛）。
 * <p>
 * 业务侧永远不接触 vendor 原始凭证；所有 vendor 差异由 {@code StsSigner} impl 消化，
 * 业务侧按能力类型调 {@link #asBearerHeaders()} / {@link #asTc3Headers} /
 * {@link #asAppCodeHeaders()} / {@link #asSignedHeaders} / {@link #asHeaderMap()}。
 * <p>
 * 设计原则 J:WS + STS 统一接口 — 任何 vendor 差异收敛到本类内部方法,
 * 业务代码不写 {@code if (vendor == "hunyuan-tts")}。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-21
 */
public final class StsTicket {

    /** 能力标识常量 — 业务侧按需引用。 */
    public static final String CAPABILITY_VOICE_CHAT = "voice-chat";
    public static final String CAPABILITY_TTS_STREAM = "tts-stream";
    public static final String CAPABILITY_STT_STREAM = "stt-stream";

    /** vendor 标识常量 — 仅用于业务侧做日志/埋点，禁止用于 if/else 分支。 */
    public static final String VENDOR_DASHSCOPE = "dashscope";
    public static final String VENDOR_ZHIPU = "zhipu";
    public static final String VENDOR_DOUBAO_OPENSPEECH = "doubao-openspeech";
    public static final String VENDOR_DOUBAO_VIKINGDB = "doubao-vikingdb";
    public static final String VENDOR_HUNYUAN_TOKENHUB = "hunyuan-tokenhub";
    public static final String VENDOR_HUNYUAN_TTS = "hunyuan-tts";
    public static final String VENDOR_HUNYUAN_STT = "hunyuan-stt";
    public static final String VENDOR_PANGU = "pangu";
    public static final String VENDOR_HUAWEI_SIS = "huawei-sis";
    public static final String VENDOR_MINIMAX = "minimax";

    private final String vendor;
    private final String model;
    private final String capability;
    private final String endpoint;
    private final long issuedAt;
    private final long expiresAt;
    private final Map<String, String> credentials;
    private final Map<String, String> bearerHeaders;
    private final Map<String, String> appCodeHeaders;
    private final Map<String, String> headerMap;
    private final Tc3Material tc3Material;
    private final AkSkMaterial akSkMaterial;

    private StsTicket(Builder b) {
        this.vendor = Objects.requireNonNull(b.vendor, "vendor");
        this.model = Objects.requireNonNull(b.model, "model");
        this.capability = Objects.requireNonNull(b.capability, "capability");
        this.endpoint = Objects.requireNonNull(b.endpoint, "endpoint");
        this.issuedAt = b.issuedAt;
        this.expiresAt = b.expiresAt;
        this.credentials = b.credentials == null ? Map.of() : Map.copyOf(b.credentials);
        this.bearerHeaders = b.bearerHeaders == null ? Map.of() : Map.copyOf(b.bearerHeaders);
        this.appCodeHeaders = b.appCodeHeaders == null ? Map.of() : Map.copyOf(b.appCodeHeaders);
        this.headerMap = b.headerMap == null ? Map.of() : Map.copyOf(b.headerMap);
        this.tc3Material = b.tc3Material;
        this.akSkMaterial = b.akSkMaterial;
    }

    /** vendor 标识(如 "zhipu" / "hunyuan-tts")。仅用于日志/埋点,禁止业务 if/else。 */
    public String vendor() {
        return vendor;
    }

    /** 选中的 model 名。 */
    public String model() {
        return model;
    }

    /** 能力标识 — 决定业务侧调哪个 {@code asXxx()} 方法。 */
    public String capability() {
        return capability;
    }

    /** 完整 WebSocket / HTTP 端点 URL(前端直连目标)。 */
    public String endpoint() {
        return endpoint;
    }

    /** 签发时刻(epoch ms)。 */
    public long issuedAt() {
        return issuedAt;
    }

    /** 过期时刻(epoch ms)。 */
    public long expiresAt() {
        return expiresAt;
    }

    /** 票面有效时长。 */
    public Duration ttl() {
        return Duration.ofMillis(expiresAt - issuedAt);
    }

    /** 是否已过期。 */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /**
     * 统一方法 1:Bearer 域 — DashScope / Zhipu / Hunyuan-TokenHub / MiniMax。
     *
     * @return 不可变头映射,包含 {@code Authorization: Bearer <token>}
     * @throws UnsupportedOperationException 当前票面 vendor 不是 Bearer 域
     */
    public Map<String, String> asBearerHeaders() {
        requireBearer();
        return bearerHeaders;
    }

    /**
     * 统一方法 2:TC3 域 — Hunyuan TTS(1073)/ STT(1093)。延迟签名:每次按当前时间戳重算。
     *
     * @param action  Action 名(如 {@code TextToVoice} / {@code SentenceRecognition})
     * @param payload 请求体 JSON 串(用于计算 {@code payloadHash})
     * @return 不可变头映射,含 TC3 {@code Authorization} / {@code X-TC-Action} / {@code X-TC-Timestamp} / {@code X-TC-Version} / {@code Host} / {@code Content-Type}
     * @throws UnsupportedOperationException 当前票面 vendor 不是 TC3 域
     */
    public Map<String, String> asTc3Headers(String action, String payload) {
        requireTc3();
        long timestamp = Instant.now().getEpochSecond();
        return computeTc3Headers(tc3Material, action, payload, timestamp);
    }

    /**
     * 统一方法 3:AppCode 域 — Pangu / Huawei-SIS(华为 API 网关 AppCode 模式)。
     *
     * @return 不可变头映射,包含 {@code X-Apig-AppCode: <appCode>}
     * @throws UnsupportedOperationException 当前票面 vendor 不是 AppCode 域
     */
    public Map<String, String> asAppCodeHeaders() {
        requireAppCode();
        return appCodeHeaders;
    }

    /**
     * 统一方法 4:AK-SK-HMAC 域 — Doubao VikingDB。延迟签名:每次按当前请求重算。
     *
     * @param method HTTP 方法(如 {@code POST})
     * @param uri    请求 URI(含 query)
     * @param body   请求体字节(空为 new byte[0])
     * @return 不可变头映射,含火山 OpenAPI 签名 {@code Authorization}
     * @throws UnsupportedOperationException 当前票面 vendor 不是 AK-SK-HMAC 域
     */
    public Map<String, String> asSignedHeaders(String method, String uri, byte[] body) {
        requireAkSk();
        if (body == null) {
            body = new byte[0];
        }
        return computeAkSkHeaders(akSkMaterial, method, uri, body);
    }

    /**
     * 统一方法 5:X-Api-Key 域 — Doubao openspeech(语音 TTS / STT 流式)。
     *
     * @return 不可变头映射,含 {@code X-Api-Key} / {@code X-Api-Resource-Id} / 可选 {@code X-Api-App-Id} + {@code X-Api-Access-Key}
     * @throws UnsupportedOperationException 当前票面 vendor 不是 X-Api-Key 域
     */
    public Map<String, String> asHeaderMap() {
        requireXApiKey();
        return headerMap;
    }

    /**
     * 通用取值:任一域统一返回"最适合当前 capability"的全部头映射。
     * <p>
     * 业务侧可直接 {@code Object headers = ticket.headersForRequest(...)} 拿现成头,
     * 无需判断 vendor。capability 决定调用哪个 asXxx:
     * <ul>
     *   <li>{@code CAPABILITY_VOICE_CHAT} / {@code CAPABILITY_STT_STREAM} → 按 vendor 自动选 {@code asBearerHeaders} / {@code asXApiKeyHeaders}</li>
     *   <li>{@code CAPABILITY_TTS_STREAM} → TC3 / AppCode / Bearer / X-Api-Key 自动适配</li>
     * </ul>
     *
     * @param actionOrMethod TC3 域用 action 名;AK-SK 域用 HTTP method;其他域忽略
     * @param body           TC3 / AK-SK 域用请求体;其他域忽略
     * @return 不可变头映射
     */
    public Map<String, String> headersForRequest(String actionOrMethod, byte[] body) {
        return switch (capability) {
            case CAPABILITY_VOICE_CHAT, CAPABILITY_STT_STREAM -> {
                // 双工/流式:优先 Bearer / X-Api-Key(WS 握手静态头)
                if (!bearerHeaders.isEmpty()) {
                    yield bearerHeaders;
                }
                if (!headerMap.isEmpty()) {
                    yield headerMap;
                }
                throw new UnsupportedOperationException(
                        "Ticket vendor " + vendor + " capability " + capability + " 无可用鉴权头");
            }
            case CAPABILITY_TTS_STREAM -> {
                if (tc3Material != null) {
                    yield asTc3Headers(actionOrMethod, body == null ? "" : new String(body, StandardCharsets.UTF_8));
                }
                if (akSkMaterial != null) {
                    yield asSignedHeaders(actionOrMethod, "/", body == null ? new byte[0] : body);
                }
                if (!appCodeHeaders.isEmpty()) {
                    yield appCodeHeaders;
                }
                if (!bearerHeaders.isEmpty()) {
                    yield bearerHeaders;
                }
                if (!headerMap.isEmpty()) {
                    yield headerMap;
                }
                throw new UnsupportedOperationException(
                        "Ticket vendor " + vendor + " capability " + capability + " 无可用鉴权头");
            }
            default -> throw new UnsupportedOperationException(
                    "Unknown capability " + capability + ", 请直接调 asBearer/asTc3Headers/asAppCode/asSignedHeaders/asHeaderMap");
        };
    }

    // ============ 域校验(失败抛 UnsupportedOperationException,不暴露 vendor 字符串泄露) ============

    private void requireBearer() {
        if (bearerHeaders.isEmpty()) {
            throw new UnsupportedOperationException("当前票面不支持 Bearer 域(vendor=" + vendor + ")");
        }
    }

    private void requireTc3() {
        if (tc3Material == null) {
            throw new UnsupportedOperationException("当前票面不支持 TC3 域(vendor=" + vendor + ")");
        }
    }

    private void requireAppCode() {
        if (appCodeHeaders.isEmpty()) {
            throw new UnsupportedOperationException("当前票面不支持 AppCode 域(vendor=" + vendor + ")");
        }
    }

    private void requireAkSk() {
        if (akSkMaterial == null) {
            throw new UnsupportedOperationException("当前票面不支持 AK-SK-HMAC 域(vendor=" + vendor + ")");
        }
    }

    private void requireXApiKey() {
        if (headerMap.isEmpty()) {
            throw new UnsupportedOperationException("当前票面不支持 X-Api-Key 域(vendor=" + vendor + ")");
        }
    }

    // ============ TC3 / AK-SK HMAC 计算逻辑(纯 JDK,无第三方依赖) ============

    private static Map<String, String> computeTc3Headers(Tc3Material m, String action, String bodyJson, long timestamp) {
        try {
            String date = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String payloadHash = sha256Hex(bodyJson == null ? "" : bodyJson);
            String host = extractHost(m.endpoint());
            String actionLower = action == null ? "" : action.toLowerCase(java.util.Locale.ROOT);
            String canonicalHeaders = "content-type:application/json; charset=utf-8\n"
                    + "host:" + host + "\n"
                    + "x-tc-action:" + actionLower + "\n";
            String signedHeaders = "content-type;host;x-tc-action";
            String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
            String canonicalRequestHash = sha256Hex(canonicalRequest);

            String credentialScope = date + "/" + m.service() + "/tc3_request";
            String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + canonicalRequestHash;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(("TC3" + m.secretKey()).getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] kDate = mac.doFinal(date.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(kDate, "HmacSHA256"));
            byte[] kService = mac.doFinal(m.service().getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(kService, "HmacSHA256"));
            byte[] kSigning = mac.doFinal("tc3_request".getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(kSigning, "HmacSHA256"));
            String sigHex = HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

            String authorization = "TC3-HMAC-SHA256 Credential=" + m.secretId() + "/" + credentialScope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + sigHex;

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", authorization);
            headers.put("Content-Type", "application/json; charset=utf-8");
            headers.put("Host", host);
            headers.put("X-TC-Action", action);
            headers.put("X-TC-Timestamp", String.valueOf(timestamp));
            headers.put("X-TC-Version", "2019-08-23");
            return Collections.unmodifiableMap(headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute TC3 headers", e);
        }
    }

    private static Map<String, String> computeAkSkHeaders(AkSkMaterial m, String method, String uri, byte[] body) {
        try {
            // 火山 OpenAPI 签名规范 — 算法等价 AWS SigV4,简化为单一 region "cn-beijing"
            long timestamp = Instant.now().getEpochSecond();
            String date = Instant.ofEpochSecond(timestamp)
                    .atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            String bodyHash = sha256Hex(new String(body, StandardCharsets.UTF_8));

            // CanonicalRequest
            String canonicalHeaders = "content-type:application/json\n"
                    + "host:" + extractHost(uri) + "\n"
                    + "x-date:" + date + "\n";
            String signedHeaders = "content-type;host;x-date";
            String canonicalRequest = method + "\n"
                    + extractPath(uri) + "\n"
                    + extractQuery(uri) + "\n"
                    + canonicalHeaders + "\n"
                    + signedHeaders + "\n"
                    + bodyHash;

            // StringToSign
            String credentialScope = date + "/" + m.region() + "/" + m.service() + "/request";
            String stringToSign = "HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);

            // SigningKey (chain: ak-secret → date → region → service → request)
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(m.sk().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] kDate = mac.doFinal(date.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(kDate, "HmacSHA256"));
            byte[] kRegion = mac.doFinal(m.region().getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(kRegion, "HmacSHA256"));
            byte[] kService = mac.doFinal(m.service().getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(kService, "HmacSHA256"));
            byte[] kSigning = mac.doFinal("request".getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(kSigning, "HmacSHA256"));
            String sigHex = HexFormat.of().formatHex(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

            String authorization = "HMAC-SHA256 Credential=" + m.ak() + "/" + credentialScope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + sigHex;

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Authorization", authorization);
            headers.put("Content-Type", "application/json");
            headers.put("Host", extractHost(uri));
            headers.put("X-Date", date);
            return Collections.unmodifiableMap(headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute AK-SK HMAC headers", e);
        }
    }

    private static String sha256Hex(String data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String extractHost(String url) {
        if (url == null) {
            return "";
        }
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) {
            return url;
        }
        String rest = url.substring(schemeIdx + 3);
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static String extractPath(String uri) {
        if (uri == null) {
            return "/";
        }
        int q = uri.indexOf('?');
        String pathPart = q < 0 ? uri : uri.substring(0, q);
        int schemeIdx = pathPart.indexOf("://");
        if (schemeIdx >= 0) {
            String rest = pathPart.substring(schemeIdx + 3);
            int slash = rest.indexOf('/');
            return slash < 0 ? "/" : rest.substring(slash);
        }
        return pathPart.isEmpty() ? "/" : pathPart;
    }

    private static String extractQuery(String uri) {
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        return q < 0 ? "" : uri.substring(q + 1);
    }

    // ============ 内部材料 record(纯数据,无逻辑) ============

    /**
     * TC3 域延迟签名材料。仅 StsSigner impl 填充,业务侧不可见。
     */
    public record Tc3Material(String secretId, String secretKey, String service, String region, String endpoint) {
        public Tc3Material {
            Objects.requireNonNull(secretId, "secretId");
            Objects.requireNonNull(secretKey, "secretKey");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(endpoint, "endpoint");
        }
    }

    /**
     * AK-SK HMAC 域延迟签名材料(火山 VikingDB)。仅 StsSigner impl 填充。
     */
    public record AkSkMaterial(String ak, String sk, String service, String region) {
        public AkSkMaterial {
            Objects.requireNonNull(ak, "ak");
            Objects.requireNonNull(sk, "sk");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(region, "region");
        }
    }

    /**
     * 创建 Builder。仅 {@code StsSigner} impl 应当调用,业务侧用 {@code ticket.asXxxHeaders()} 消费票面。
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder — 仅 StsSigner impl 包可见(package-private constructor)。 */
    public static final class Builder {
        private String vendor;
        private String model;
        private String capability;
        private String endpoint;
        private long issuedAt;
        private long expiresAt;
        private Map<String, String> credentials;
        private Map<String, String> bearerHeaders;
        private Map<String, String> appCodeHeaders;
        private Map<String, String> headerMap;
        private Tc3Material tc3Material;
        private AkSkMaterial akSkMaterial;

        public Builder vendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder capability(String capability) {
            this.capability = capability;
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder ttl(Duration ttl) {
            long now = System.currentTimeMillis();
            this.issuedAt = now;
            this.expiresAt = now + ttl.toMillis();
            return this;
        }

        public Builder ttlSeconds(long seconds) {
            return ttl(Duration.ofSeconds(seconds));
        }

        public Builder issuedAt(long issuedAtMs) {
            this.issuedAt = issuedAtMs;
            return this;
        }

        public Builder expiresAt(long expiresAtMs) {
            this.expiresAt = expiresAtMs;
            return this;
        }

        public Builder credentials(Map<String, String> credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder bearerHeaders(Map<String, String> headers) {
            this.bearerHeaders = headers;
            return this;
        }

        public Builder appCodeHeaders(Map<String, String> headers) {
            this.appCodeHeaders = headers;
            return this;
        }

        public Builder headerMap(Map<String, String> headers) {
            this.headerMap = headers;
            return this;
        }

        public Builder tc3Material(Tc3Material material) {
            this.tc3Material = material;
            return this;
        }

        public Builder akSkMaterial(AkSkMaterial material) {
            this.akSkMaterial = material;
            return this;
        }

        public StsTicket build() {
            return new StsTicket(this);
        }
    }
}