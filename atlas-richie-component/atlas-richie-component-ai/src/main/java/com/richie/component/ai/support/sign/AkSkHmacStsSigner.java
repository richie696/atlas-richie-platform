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
 * AK-SK-HMAC 域 STS 签发器 — 火山引擎 OpenAPI 签名(Doubao VikingDB Rerank)。
 *
 * <p>本类遵循 R-N 设计 §14.3.3 原则 J:延迟签名(AkSkMaterial 留在 ticket 内),
 * 业务侧调 {@link StsTicket#asSignedHeaders(String, String, byte[])} 时按当前请求重算,
 * 与 AWS SigV4 等价。
 *
 * <h2>ctx.attributes 约定</h2>
 * <ul>
 *   <li>{@code ak} (可选)— 覆盖构造器凭证</li>
 *   <li>{@code sk} (可选)— 覆盖构造器凭证</li>
 *   <li>{@code region} (可选)— 覆盖构造器 region</li>
 *   <li>{@code service} (可选)— 覆盖构造器 service</li>
 *   <li>{@code endpoint} (可选)— 覆盖构造器端点</li>
 * </ul>
 *
 * @author richie696
 * @see StsSigner
 * @see StsTicket.AkSkMaterial
 * @since 1.0.0
 */
public final class AkSkHmacStsSigner implements StsSigner {

    /**
     * 鉴权域常量。
     */
    public static final String AUTH_DOMAIN = "aksk-hmac";

    private final String vendor;
    private final String ak;
    private final String sk;
    private final String region;
    private final String service;
    private final String defaultEndpoint;

    public AkSkHmacStsSigner(String vendor, String ak, String sk,
                             String region, String service, String defaultEndpoint) {
        this.vendor = Objects.requireNonNull(vendor, "vendor");
        this.ak = Objects.requireNonNull(ak, "ak");
        this.sk = Objects.requireNonNull(sk, "sk");
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
                    "AkSkHmacStsSigner[" + vendor + "] 不支持 ctx: " + ctx);
        }

        String effectiveAk = firstNonEmpty(ctx.attribute("ak"), ak);
        String effectiveSk = firstNonEmpty(ctx.attribute("sk"), sk);
        String effectiveRegion = firstNonEmpty(ctx.attribute("region"), region);
        String effectiveService = firstNonEmpty(ctx.attribute("service"), service);
        String effectiveEndpoint = firstNonEmpty(ctx.attribute("endpoint"), defaultEndpoint);

        StsTicket.AkSkMaterial material = new StsTicket.AkSkMaterial(
                effectiveAk, effectiveSk, effectiveService, effectiveRegion);

        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("ak", effectiveAk);
        credentials.put("region", effectiveRegion);
        credentials.put("service", effectiveService);

        long nowMs = System.currentTimeMillis();
        long ttlMs = ctx.getTtlSeconds() * 1000L;

        return StsTicket.builder()
                .vendor(vendor)
                .model(ctx.getModel())
                .capability(ctx.getCapability())
                .endpoint(effectiveEndpoint)
                .issuedAt(nowMs)
                .expiresAt(nowMs + ttlMs)
                .credentials(credentials)
                .akSkMaterial(material)
                .build();
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }
}