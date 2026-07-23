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
 * X-Api-Key 域 STS 签发器 — Doubao openspeech 语音(TTS 双向 / STT 流式)。
 *
 * <p>本类遵循 R-N 设计 §14.3.3 原则 J:静态头凭证(api-key / app-id / resource-id 都不变),
 * 一次性在 {@link StsTicket#asHeaderMap()} 返回 4 个 {@code X-Api-*} 头。
 *
 * <h2>ctx.attributes 约定</h2>
 * <ul>
 *   <li>{@code apiKey} (可选)— 覆盖构造器凭证</li>
 *   <li>{@code appId} (可选)— 覆盖构造器 appId</li>
 *   <li>{@code resourceId} (可选)— 覆盖构造器 resourceId</li>
 *   <li>{@code endpoint} (可选)— 覆盖构造器端点</li>
 * </ul>
 *
 * @author richie696
 * @see StsSigner
 * @since 1.0.0
 */
public final class XApiKeyStsSigner implements StsSigner {

    /**
     * 鉴权域常量。
     */
    public static final String AUTH_DOMAIN = "x-api-key";

    private final String vendor;
    private final String apiKey;
    private final String appId;
    private final String resourceId;
    private final String defaultEndpoint;

    public XApiKeyStsSigner(String vendor, String apiKey, String appId,
                            String resourceId, String defaultEndpoint) {
        this.vendor = Objects.requireNonNull(vendor, "vendor");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.appId = appId;            // 可选
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
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
                    "XApiKeyStsSigner[" + vendor + "] 不支持 ctx: " + ctx);
        }

        String effectiveKey = firstNonEmpty(ctx.attribute("apiKey"), apiKey);
        String effectiveAppId = firstNonEmpty(ctx.attribute("appId"), appId);
        String effectiveResourceId = firstNonEmpty(ctx.attribute("resourceId"), resourceId);
        String endpoint = firstNonEmpty(ctx.attribute("endpoint"), defaultEndpoint);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Api-Key", effectiveKey);
        headers.put("X-Api-Resource-Id", effectiveResourceId);
        if (effectiveAppId != null && !effectiveAppId.isEmpty()) {
            headers.put("X-Api-App-Id", effectiveAppId);
            headers.put("X-Api-Access-Key", effectiveKey);
        }

        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("apiKey", effectiveKey);
        credentials.put("appId", effectiveAppId);
        credentials.put("resourceId", effectiveResourceId);

        long nowMs = System.currentTimeMillis();
        long ttlMs = ctx.getTtlSeconds() * 1000L;

        return StsTicket.builder()
                .vendor(vendor)
                .model(ctx.getModel())
                .capability(ctx.getCapability())
                .endpoint(endpoint)
                .issuedAt(nowMs)
                .expiresAt(nowMs + ttlMs)
                .credentials(credentials)
                .headerMap(headers)
                .build();
    }

    private static String firstNonEmpty(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }
}