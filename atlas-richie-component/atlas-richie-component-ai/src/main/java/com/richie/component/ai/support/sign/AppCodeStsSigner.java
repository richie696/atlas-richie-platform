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
 * AppCode 域 STS 签发器 — 阿里云 APIG / 华为云 SIS 网关 AppCode 模式鉴权
 * (盘古大模型市场版 / 华为云 SIS 语音)。
 *
 * <p>本类遵循 R-N 设计 §14.3.3 原则 J:静态头凭证(AppCode 不会过期),
 * 一次性在 {@link StsTicket#asAppCodeHeaders()} 时返回 {@code X-Apig-AppCode} 头。
 *
 * <h2>ctx.attributes 约定</h2>
 * <ul>
 *   <li>{@code appCode} (可选)— 覆盖构造器凭证</li>
 *   <li>{@code endpoint} (可选)— 覆盖构造器端点</li>
 * </ul>
 *
 * @author richie696
 * @see StsSigner
 * @since 1.0.0
 */
public final class AppCodeStsSigner implements StsSigner {

    /**
     * 鉴权域常量。
     */
    public static final String AUTH_DOMAIN = "appcode";

    private final String vendor;
    private final String appCode;
    private final String defaultEndpoint;

    public AppCodeStsSigner(String vendor, String appCode, String defaultEndpoint) {
        this.vendor = Objects.requireNonNull(vendor, "vendor");
        this.appCode = Objects.requireNonNull(appCode, "appCode");
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
                    "AppCodeStsSigner[" + vendor + "] 不支持 ctx: " + ctx);
        }

        String effectiveCode = ctx.attribute("appCode");
        if (effectiveCode == null || effectiveCode.isEmpty()) {
            effectiveCode = appCode;
        }

        String endpoint = ctx.attribute("endpoint");
        if (endpoint == null || endpoint.isEmpty()) {
            endpoint = defaultEndpoint;
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Apig-AppCode", effectiveCode);

        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("appCode", effectiveCode);

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
                .appCodeHeaders(headers)
                .build();
    }
}