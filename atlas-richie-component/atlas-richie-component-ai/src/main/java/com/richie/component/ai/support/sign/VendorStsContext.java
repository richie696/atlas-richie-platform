package com.richie.component.ai.support.sign;

import com.richie.component.ai.api.voicechat.StsTicket;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * STS 签发上下文 (不可变 record-like 容器, 支持 builder)。
 *
 * <p>封装一次 STS 签发请求所需的全部信息:
 * <ul>
 *   <li>目标 vendor (必填,用于路由到正确 signer)</li>
 *   <li>凭证域 (必填,例如 {@code "bearer"} / {@code "tc3"} / {@code "appcode"} / {@code "aksk-hmac"} / {@code "x-api-key"})</li>
 *   <li>能力 (必填,例如 {@link StsTicket#CAPABILITY_VOICE_CHAT})</li>
 *   <li>模型名 (必填,例如 {@code "qwen-omni-realtime"} / {@code "glm-4-voice"})</li>
 *   <li>ttl (可选,默认由 signer 提供)</li>
 *   <li>issuedAt (可选,用于测试可重放,默认当前时间)</li>
 *   <li>attributes (可选, vendor-private 透传字段)</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * VendorStsContext ctx = VendorStsContext.builder()
 *     .vendor(StsTicket.VENDOR_ZHIPU)
 *     .authDomain("bearer")
 *     .capability(StsTicket.CAPABILITY_VOICE_CHAT)
 *     .model("glm-4-voice")
 *     .ttlSeconds(1800)
 *     .attribute("region", "cn-bj2")
 *     .build();
 *
 * StsSigner signer = signerRegistry.find(ctx);
 * StsTicket ticket = signer.sign(ctx);
 * }</pre>
 *
 * @author Sisyphus
 * @since 1.0.0
 */
public final class VendorStsContext {

    private final String vendor;
    private final String authDomain;
    private final String capability;
    private final String model;
    private final int ttlSeconds;
    private final Instant issuedAt;
    private final Map<String, Object> attributes;

    private VendorStsContext(Builder b) {
        this.vendor = Objects.requireNonNull(b.vendor, "vendor 不能为空");
        this.authDomain = Objects.requireNonNull(b.authDomain, "authDomain 不能为空");
        this.capability = Objects.requireNonNull(b.capability, "capability 不能为空");
        this.model = Objects.requireNonNull(b.model, "model 不能为空");
        if (b.ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds 必须 > 0, current=" + b.ttlSeconds);
        }
        this.ttlSeconds = b.ttlSeconds;
        this.issuedAt = b.issuedAt != null ? b.issuedAt : Instant.now();
        this.attributes = b.attributes != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(b.attributes))
                : Collections.emptyMap();
    }

    /**
     * 厂商标识,见 {@code StsTicket#VENDOR_*} 系列常量。
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * 凭证域,见 {@link StsSigner#supportedAuthDomains()}。
     */
    public String getAuthDomain() {
        return authDomain;
    }

    /**
     * 能力 (VOICE_CHAT / TTS_STREAM / STT_STREAM),见 {@code StsTicket#CAPABILITY_*} 常量。
     */
    public String getCapability() {
        return capability;
    }

    /**
     * 模型名 (例如 "qwen-omni-realtime" / "glm-4-voice")。
     */
    public String getModel() {
        return model;
    }

    /**
     * 凭证有效期 (秒)。
     */
    public int getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * 凭证签发时间 (用于测试可重放)。
     */
    public Instant getIssuedAt() {
        return issuedAt;
    }

    /**
     * vendor-private 透传字段 (例如 region / endpoint / appCode 等)。
     *
     * <p>实现可从此 Map 读取 vendor-specific 配置,无需扩展上下文字段。
     *
     * @return 不可变 attributes Map
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 读取 vendor-private 字段。
     *
     * @param key 字段名
     * @param <T> 期望类型
     * @return 字段值,不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T attribute(String key) {
        return (T) attributes.get(key);
    }

    @Override
    public String toString() {
        return "VendorStsContext{"
                + "vendor='" + vendor + '\''
                + ", authDomain='" + authDomain + '\''
                + ", capability='" + capability + '\''
                + ", model='" + model + '\''
                + ", ttlSeconds=" + ttlSeconds
                + ", issuedAt=" + issuedAt
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder。
     */
    public static final class Builder {
        private String vendor;
        private String authDomain;
        private String capability;
        private String model;
        private int ttlSeconds = 3600;
        private Instant issuedAt;
        private Map<String, Object> attributes;

        public Builder vendor(String vendor) {
            this.vendor = vendor;
            return this;
        }

        public Builder authDomain(String authDomain) {
            this.authDomain = authDomain;
            return this;
        }

        public Builder capability(String capability) {
            this.capability = capability;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder ttlSeconds(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder attribute(String key, Object value) {
            if (this.attributes == null) {
                this.attributes = new LinkedHashMap<>();
            }
            this.attributes.put(key, value);
            return this;
        }

        public VendorStsContext build() {
            return new VendorStsContext(this);
        }
    }
}