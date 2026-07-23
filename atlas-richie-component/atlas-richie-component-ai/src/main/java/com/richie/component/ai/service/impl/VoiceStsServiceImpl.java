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
package com.richie.component.ai.service.impl;

import com.richie.component.ai.api.voicechat.StsTicket;
import com.richie.component.ai.service.VoiceStsService;
import com.richie.component.ai.support.sign.StsSigner;
import com.richie.component.ai.support.sign.VendorStsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link VoiceStsService} 默认实现。
 *
 * <h2>路由策略</h2>
 * <ol>
 *   <li>遍历已注入的 {@link StsSigner} 列表,找到第一个 {@link StsSigner#supports(VendorStsContext)} 返回 true 的实现</li>
 *   <li>若多个 signer 同时 supports (例如同一个 vendor 配置了多个凭证域),取第一个匹配</li>
 *   <li>未找到任何 signer 时抛 {@link IllegalStateException} (业务侧 fail-fast)</li>
 * </ol>
 *
 * <h2>配置切换 vendor</h2>
 * 业务代码 + 本服务代码不动,只需在 {@code application.yml} 切换
 * {@code platform.component.ai.voice-chat.<businessName>.vendor} 字段。
 *
 * @author richie696
 * @since 2026-07-21
 */
@Service
public class VoiceStsServiceImpl implements VoiceStsService {

    private static final Logger log = LoggerFactory.getLogger(VoiceStsServiceImpl.class);

    private final List<StsSigner> signers;
    private final ConcurrentMap<String, StsSigner> signerCache = new ConcurrentHashMap<>();

    public VoiceStsServiceImpl(List<StsSigner> signers) {
        this.signers = signers == null ? List.of() : List.copyOf(signers);
        log.info("VoiceStsService 初始化完成,已注册 {} 个 StsSigner 实现: {}",
                this.signers.size(), this.signers.stream().map(StsSigner::vendor).toList());
    }

    @Override
    public StsTicket sign(VendorStsContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为空");
        StsSigner signer = resolve(ctx);
        try {
            StsTicket ticket = signer.sign(ctx);
            log.debug("VoiceStsService 签发 STS 票面成功: vendor={}, capability={}, model={}, expiresAt={}",
                    ticket.vendor(), ticket.capability(), ticket.model(), ticket.expiresAt());
            return ticket;
        } catch (RuntimeException e) {
            log.error("VoiceStsService 签发 STS 票面失败: vendor={}, capability={}, model={}",
                    ctx.getVendor(), ctx.getCapability(), ctx.getModel(), e);
            throw e;
        }
    }

    @Override
    public StsTicket sign(String vendor, String capability, String model) {
        return sign(vendor, capability, model, 0, null);
    }

    @Override
    public StsTicket sign(String vendor, String capability, String model,
                          int ttlSeconds, Map<String, Object> attributes) {
        Objects.requireNonNull(vendor, "vendor 不能为空");
        Objects.requireNonNull(capability, "capability 不能为空");
        Objects.requireNonNull(model, "model 不能为空");
        VendorStsContext probeCtx = VendorStsContext.builder()
                .vendor(vendor)
                .authDomain(probeAuthDomain(vendor))
                .capability(capability)
                .model(model)
                .issuedAt(Instant.now())
                .build();
        StsSigner signer = resolve(probeCtx);
        int effectiveTtl = ttlSeconds > 0 ? ttlSeconds : signer.defaultTtlSeconds();
        VendorStsContext fullCtx = VendorStsContext.builder()
                .vendor(vendor)
                .authDomain(probeCtx.getAuthDomain())
                .capability(capability)
                .model(model)
                .ttlSeconds(effectiveTtl)
                .issuedAt(Instant.now())
                .attributes(attributes)
                .build();
        return sign(fullCtx);
    }

    @Override
    public List<String> listRegisteredVendors() {
        List<String> vendors = new ArrayList<>(signers.size());
        for (StsSigner s : signers) {
            vendors.add(s.vendor());
        }
        return Collections.unmodifiableList(vendors);
    }

    @Override
    public int signerCount() {
        return signers.size();
    }

    private StsSigner resolve(VendorStsContext ctx) {
        String cacheKey = ctx.getVendor() + "|" + ctx.getAuthDomain();
        StsSigner cached = signerCache.get(cacheKey);
        if (cached != null && cached.supports(ctx)) {
            return cached;
        }
        for (StsSigner s : signers) {
            if (s.supports(ctx)) {
                signerCache.put(cacheKey, s);
                return s;
            }
        }
        throw new IllegalStateException(String.format(
                "VoiceStsService 未找到能处理 ctx 的 StsSigner: vendor=%s, authDomain=%s, capability=%s, model=%s。"
                        + "请检查 application.yml 是否配置了对应 vendor 的 long-term credential。已注册 vendor: %s",
                ctx.getVendor(), ctx.getAuthDomain(), ctx.getCapability(), ctx.getModel(),
                listRegisteredVendors()));
    }

    private String probeAuthDomain(String vendor) {
        return switch (vendor) {
            case StsTicket.VENDOR_HUNYUAN_TTS, StsTicket.VENDOR_HUNYUAN_STT -> "tc3";
            case StsTicket.VENDOR_DOUBAO_VIKINGDB -> "aksk-hmac";
            case StsTicket.VENDOR_DOUBAO_OPENSPEECH -> "x-api-key";
            case StsTicket.VENDOR_PANGU, StsTicket.VENDOR_HUAWEI_SIS -> "appcode";
            case StsTicket.VENDOR_DASHSCOPE,
                 StsTicket.VENDOR_ZHIPU,
                 StsTicket.VENDOR_HUNYUAN_TOKENHUB,
                 StsTicket.VENDOR_MINIMAX -> "bearer";
            default -> "bearer";
        };
    }
}