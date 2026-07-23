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
 * Bearer 域 STS 签发器 — 通用实现,适用于所有以 {@code Authorization: Bearer <apiKey>}
 * 鉴权的厂商(智谱 / DashScope / Hunyuan-TokenHub / MiniMax)。
 *
 * <p>本类遵循 R-N 设计 §14.3.3 原则 J:每个 vendor 实例化为独立 Spring Bean,
 * 共用此 impl,业务侧只通过 {@link StsSigner} 抽象调用,感知不到 vendor 字符串。
 *
 * <h2>凭证来源</h2>
 * 凭证通过构造器注入(由 {@code AiModelAutoConfiguration} 按 vendor 装配);
 * 也可通过上下文属性 {@code apiKey} 覆盖(用于测试 / 临时换密)。
 *
 * <h2>ctx.attributes 约定</h2>
 * <ul>
 *   <li>{@code endpoint} (可选)— vendor WS 端点 URL;缺省用构造器传入的 {@code defaultEndpoint}</li>
 *   <li>{@code apiKey} (可选)— 覆盖构造器凭证</li>
 *   <li>{@code ttlSeconds} 已通过 {@link VendorStsContext#getTtlSeconds()} 提供</li>
 * </ul>
 *
 * @author richie696
 * @see StsSigner
 * @since 1.0.0
 */
public final class BearerStsSigner implements StsSigner {

    /**
     * 鉴权域常量 — 同时也是 {@link #supportedAuthDomains()} 的返回值。
     */
    public static final String AUTH_DOMAIN = "bearer";

    private final String vendor;
    private final String apiKey;
    private final String defaultEndpoint;

    /**
     * @param vendor         厂商标识,见 {@code StsTicket#VENDOR_*}
     * @param apiKey         长时凭证 API Key
     * @param defaultEndpoint 缺省 WS 端点 URL
     */
    public BearerStsSigner(String vendor, String apiKey, String defaultEndpoint) {
        this.vendor = Objects.requireNonNull(vendor, "vendor");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
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
                    "BearerStsSigner[" + vendor + "] 不支持 ctx: " + ctx);
        }

        String effectiveKey = ctx.attribute("apiKey");
        if (effectiveKey == null || effectiveKey.isEmpty()) {
            effectiveKey = apiKey;
        }

        String endpoint = ctx.attribute("endpoint");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = defaultEndpoint;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + effectiveKey);

        long nowMs = System.currentTimeMillis();
        long ttlMs = ctx.getTtlSeconds() * 1000L;

        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("apiKey", effectiveKey);

        return StsTicket.builder()
                .vendor(vendor)
                .model(ctx.getModel())
                .capability(ctx.getCapability())
                .endpoint(endpoint)
                .ttlSeconds(ctx.getTtlSeconds())
                .issuedAt(nowMs)
                .expiresAt(nowMs + ttlMs)
                .credentials(credentials)
                .bearerHeaders(headers)
                .build();
    }
}