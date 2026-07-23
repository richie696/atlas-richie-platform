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

import com.richie.component.ai.api.voicechat.VoiceChatConfig;
import com.richie.component.ai.api.voicechat.VoiceChatModel;
import com.richie.component.ai.api.voicechat.VoiceConversation;
import com.richie.component.ai.service.impl.VoiceChatServiceImpl;

import java.util.List;

/**
 * 业务门面 — 唯一打开实时语音会话的入口接口。
 *
 * <p>本接口遵循 R-N 设计 §14.3.5 原则 J — WS + STS 统一接口原则:
 * 业务代码 (BFF / Controller) 仅调用本接口拿 {@link VoiceConversation},
 * 不直接引用任何 {@link VoiceChatModel} impl 类、不感知具体 vendor。
 *
 * <h2>使用模式</h2>
 * <pre>{@code
 * public class BffVoiceController {
 *     private final VoiceChatService voiceChatService;
 *
 *     public void handleVoiceCall(String businessName) {
 *         VoiceChatConfig config = VoiceChatConfig.builder()
 *             .model("glm-4-voice")
 *             .voice("tongtong")
 *             .language("zh-CN")
 *             .build();
 *
 *         try (VoiceConversation conv = voiceChatService.open(businessName, config)) {
 *             conv.events().subscribe(this::handleEvent);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author richie696
 * @see VoiceChatServiceImpl
 * @since 2026-07-21
 */
public interface VoiceChatService {

    /**
     * 打开实时语音会话(按 {@code config.vendor()} 路由)。
     *
     * @param config 会话配置(vendor 必填)
     * @return 双工会话
     * @throws IllegalStateException 找不到任何能处理该 config 的模型
     */
    VoiceConversation open(VoiceChatConfig config);

    /**
     * 打开实时语音会话(按 businessName → 配置映射自动注入 vendor)。
     *
     * <p>RN.4-alpha 阶段简化:businessName 直接作为 vendor 标识透传。
     * RN.4-beta 阶段替换为 {@code BusinessNameResolver} SPI。
     *
     * @param businessName 业务名(例如 {@code customer-service})
     * @param config       会话配置(vendor 字段将被覆盖)
     * @return 双工会话
     */
    VoiceConversation open(String businessName, VoiceChatConfig config);

    /**
     * 列出所有已注册模型的厂商标识(供调试 / 健康检查使用)。
     */
    List<String> listRegisteredVendors();

    /**
     * 当前已注册的 VoiceChatModel 总数。
     */
    int modelCount();
}