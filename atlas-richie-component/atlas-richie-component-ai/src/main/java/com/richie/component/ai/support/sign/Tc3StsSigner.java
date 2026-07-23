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
package com.richie.component.ai.support.sign;

import com.richie.component.ai.api.voicechat.StsTicket;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * TC3 域 STS 签发器 — 腾讯云 TC3-HMAC-SHA256 鉴权(Hunyuan TTS 1073 / Hunyuan STT 1093)。
 *
 * <p>本类遵循 R-N 设计 §14.3.3 原则 J:延迟签名(Tc3Material 留在 ticket 内),
 * 业务侧调 {@link StsTicket#asTc3Headers(String, String)} 时按当前时间戳重算签名,
 * 避免长期 ticket 内嵌短期签名的安全风险。
 *
 * <h2>ctx.attributes 约定</h2>
 * <ul>
 *   <li>{@code secretId} (可选)— 覆盖构造器凭证</li>
 *   <li>{@code secretKey} (可选)— 覆盖构造器凭证</li>
 *   <li>{@code region} (可选)— TC3 区域(如 {@code ap-guangzhou})</li>
 *   <li>{@code service} (可选)— TC3 服务名(如 {@code tts} / {@code asr})</li>
 *   <li>{@code endpoint} (可选)— 覆盖构造器端点</li>
 * </ul>
 *
 * @author richie696
 * @see StsSigner
 * @see StsTicket.Tc3Material
 * @since 1.0.0
 */
public final class Tc3StsSigner implements StsSigner {

    /**
     * 鉴权域常量。
     */
    public static final String AUTH_DOMAIN = "tc3";

    private final String vendor;
    private final String secretId;
    private final String secretKey;
    private final String region;
    private final String service;
    private final String defaultEndpoint;

    /**
     * @param vendor         厂商标识(例如 {@link StsTicket#VENDOR_HUNYUAN_TTS})
     * @param secretId       TC3 SecretId
     * @param secretKey      TC3 SecretKey
     * @param region         TC3 区域(如 {@code ap-guangzhou})
     * @param service        TC3 服务名(如 {@code tts} / {@code asr})
     * @param defaultEndpoint 缺省 endpoint(如 {@code tts.tencentcloudapi.com})
     */
    public Tc3StsSigner(String vendor, String secretId, String secretKey,
                        String region, String service, String defaultEndpoint) {
        this.vendor = Objects.requireNonNull(vendor, "vendor");
        this.secretId = Objects.requireNonNull(secretId, "secretId");
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey");
        this.region = Objects.requireNonNull(region, "region");
        this.service = Objects.requireNonNull(service, "service");
        this.defaultEndpoint = Objects.requireNonNull(defaultEndpoint, "defaultEndpoint");
    }

    @Override
    public String vendor() {
        return vendor;
    }

    @Override
    public String[] supportedAuthDomains() {
        return new String[]{AUTH_DOMAIN};
    }

    @Override
    public StsTicket sign(VendorStsContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if (!supports(ctx)) {
            throw new IllegalArgumentException(
                    "Tc3StsSigner[" + vendor + "] 不支持 ctx: " + ctx);
        }

        String effectiveSecretId = firstNonEmpty(ctx.attribute("secretId"), secretId);
        String effectiveSecretKey = firstNonEmpty(ctx.attribute("secretKey"), secretKey);
        String effectiveRegion = firstNonEmpty(ctx.attribute("region"), region);
        String effectiveService = firstNonEmpty(ctx.attribute("service"), service);
        String effectiveEndpoint = firstNonEmpty(ctx.attribute("endpoint"), defaultEndpoint);

        StsTicket.Tc3Material material = new StsTicket.Tc3Material(
                effectiveSecretId, effectiveSecretKey, effectiveService, effectiveRegion, effectiveEndpoint);

        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("secretId", effectiveSecretId);
        credentials.put("region", effectiveRegion);
        credentials.put("service", effectiveService);

        long nowMs = System.currentTimeMillis();
        long ttlMs = ctx.getTtlSeconds() * 1000L;

        return StsTicket.builder()
                .vendor(vendor)
                .model(ctx.getModel())
                .capability(ctx.getCapability())
                .endpoint("https://" + effectiveEndpoint)
                .issuedAt(nowMs)
                .expiresAt(nowMs + ttlMs)
                .credentials(credentials)
                .tc3Material(material)
                .build();
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }
}