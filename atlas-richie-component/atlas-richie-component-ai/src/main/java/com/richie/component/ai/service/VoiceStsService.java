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
package com.richie.component.ai.service;

import com.richie.component.ai.api.voicechat.StsTicket;
import com.richie.component.ai.service.impl.VoiceStsServiceImpl;
import com.richie.component.ai.support.sign.VendorStsContext;

import java.util.List;
import java.util.Map;

/**
 * 业务门面 — 唯一签发 STS 凭证的入口接口。
 *
 * <p>本接口遵循 R-N 设计 §14.3.5 原则 J — WS + STS 统一接口原则:
 * 业务代码 (BFF / Controller / 前端 SDK) 仅调用本接口拿 {@link StsTicket},
 * 不直接引用任何 {@code StsSigner} impl 类、不感知具体 vendor / 凭证域。
 *
 * <h2>使用模式</h2>
 * <pre>{@code
 * public class BffVoiceController {
 *     private final VoiceStsService voiceStsService;
 *
 *     public Map<String, String> issueTicket(String businessName) {
 *         String vendor = resolveVendor(businessName);
 *         StsTicket ticket = voiceStsService.sign(vendor, CAPABILITY_VOICE_CHAT, "glm-4-voice");
 *         return ticket.asBearerHeaders();
 *     }
 * }
 * }</pre>
 *
 * @author richie696
 * @see VoiceStsServiceImpl
 * @since 2026-07-21
 */
public interface VoiceStsService {

    /**
     * 签发 STS 凭证票面(完整上下文版)。
     *
     * @param ctx 上下文 (vendor / capability / model / ttl / attributes)
     * @return 不可变 STS 票面
     * @throws IllegalStateException 找不到任何能处理 {@code ctx} 的 signer
     */
    StsTicket sign(VendorStsContext ctx);

    /**
     * 便捷签发方法 — 按 vendor + capability + model 构建默认上下文,委托给 {@link #sign(VendorStsContext)}。
     */
    StsTicket sign(String vendor, String capability, String model);

    /**
     * 便捷签发方法 — 支持自定义 TTL 与 attributes。
     *
     * @param ttlSeconds 凭证 TTL (秒),传 0 或负数则用 signer 默认 TTL
     * @param attributes vendor-private 透传字段 (可空)
     */
    StsTicket sign(String vendor, String capability, String model,
                   int ttlSeconds, Map<String, Object> attributes);

    /**
     * 列出所有已注册 signer 的厂商标识(供调试 / 健康检查使用)。
     */
    List<String> listRegisteredVendors();

    /**
     * 当前已注册的 signer 总数。
     */
    int signerCount();
}