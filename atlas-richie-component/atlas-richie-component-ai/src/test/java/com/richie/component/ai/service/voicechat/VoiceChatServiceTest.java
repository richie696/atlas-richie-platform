/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 */
package com.richie.component.ai.service.voicechat;

import com.richie.component.ai.api.voicechat.StsTicket;
import com.richie.component.ai.api.voicechat.VoiceChatConfig;
import com.richie.component.ai.api.voicechat.VoiceChatEvent;
import com.richie.component.ai.api.voicechat.VoiceChatModel;
import com.richie.component.ai.api.voicechat.VoiceConversation;
import com.richie.component.ai.service.VoiceChatService;
import com.richie.component.ai.service.impl.VoiceChatServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VoiceChatService 单测 — 验证 2 项核心不变量:
 * <ol>
 *   <li>业务名解析 (open(businessName, config) — businessName 当作 vendor 透传给 model)</li>
 *   <li>未知 businessName/vendor fail-fast</li>
 * </ol>
 *
 * <p>本测试通过最小 {@link VoiceChatModel} stub 验证路由,不依赖任何 vendor 真实实现。
 */
class VoiceChatServiceTest {

    /**
     * 场景 1 — 业务名解析:open(businessName, config) 将 businessName 解析为 vendor,
     * 再按 vendor 匹配已注册的 VoiceChatModel,最终调用 model.open(config)。
     *
     * <p>RN.4-alpha 简化:businessName == vendor (RN.4-beta 替换为 BusinessNameResolver SPI)。
     */
    @Test
    void open_by_businessName_should_resolve_to_matching_model() {
        StubVoiceChatModel zhipuModel = new StubVoiceChatModel(StsTicket.VENDOR_ZHIPU, "glm-4-voice");
        StubVoiceChatModel dashscopeModel = new StubVoiceChatModel(StsTicket.VENDOR_DASHSCOPE, "qwen-omni-turbo-realtime");
        VoiceChatService service = new VoiceChatServiceImpl(List.of(zhipuModel, dashscopeModel));

        VoiceChatConfig config = VoiceChatConfig.builder()
                .model("glm-4-voice")
                .voice("tongtong")
                .language("zh-CN")
                .build();

        // businessName=zhipu → vendor=zhipu → zhipuModel.open(config)
        try (VoiceConversation conv = service.open("zhipu", config)) {
            assertNotNull(conv);
            assertSame(zhipuModel.lastReturnedConv, conv, "conv 必须由 zhipuModel.open 返回");
            assertEquals("zhipu", zhipuModel.lastConfig.vendor(), "model 收到的 config.vendor 应被 businessName 注入");
            assertEquals("glm-4-voice", zhipuModel.lastConfig.model());
        }
    }

    /**
     * 场景 2 — 未知 vendor fail-fast:open(businessName, config) 当 businessName 不匹配任何
     * 已注册 VoiceChatModel 时抛 IllegalStateException。
     */
    @Test
    void open_with_unknown_businessName_should_fail_fast() {
        StubVoiceChatModel zhipuModel = new StubVoiceChatModel(StsTicket.VENDOR_ZHIPU, "glm-4-voice");
        VoiceChatService service = new VoiceChatServiceImpl(List.of(zhipuModel));

        VoiceChatConfig config = VoiceChatConfig.builder()
                .model("glm-4-voice")
                .build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.open("unknown-business-name", config));
        assertTrue(ex.getMessage().contains("未找到"),
                "异常消息应说明未找到匹配模型,实际: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(StsTicket.VENDOR_ZHIPU),
                "异常消息应列出已注册 vendor 便于排查");
    }

    /**
     * 场景 3 — open(config) 当 config.vendor() 为空时 fail-fast (业务侧应走 open(businessName, config))。
     */
    @Test
    void open_without_vendor_should_fail_fast() {
        StubVoiceChatModel zhipuModel = new StubVoiceChatModel(StsTicket.VENDOR_ZHIPU, "glm-4-voice");
        VoiceChatService service = new VoiceChatServiceImpl(List.of(zhipuModel));

        VoiceChatConfig configWithoutVendor = VoiceChatConfig.builder()
                .model("glm-4-voice")
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.open(configWithoutVendor));
        assertTrue(ex.getMessage().contains("config.vendor() 不能为空"));
    }

    /**
     * 场景 4 — open(config) 当 config.vendor() 与 model 完全匹配时优先于 supports()。
     *
     * <p>RN.4-alpha 阶段不存在多模型同 vendor 的场景,本测试为防御性测试。
     */
    @Test
    void open_should_prefer_exact_vendor_match() {
        StubVoiceChatModel zhipuModel1 = new StubVoiceChatModel(StsTicket.VENDOR_ZHIPU, "glm-4-voice");
        StubVoiceChatModel zhipuModel2 = new StubVoiceChatModel(StsTicket.VENDOR_ZHIPU, "glm-4-voice-v2");
        VoiceChatService service = new VoiceChatServiceImpl(List.of(zhipuModel1, zhipuModel2));

        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor(StsTicket.VENDOR_ZHIPU)
                .model("glm-4-voice")
                .build();

        try (VoiceConversation conv = service.open(config)) {
            // 精确匹配优先 — 两个 model vendor 都 == "zhipu",取第一个
            assertSame(zhipuModel1.lastReturnedConv, conv);
        }
    }

    // ============ Stub 辅助类 (不依赖 vendor 真实实现) ============

    /**
     * 最小 VoiceChatModel stub — 仅用于测试路由逻辑,不模拟真实 WS。
     *
     * <p>业务侧拿到 conv 后调 close() 应幂等关闭。
     */
    private static class StubVoiceChatModel implements VoiceChatModel {
        private final String vendor;
        private final String[] supportedModels;
        private VoiceChatConfig lastConfig;
        private VoiceConversation lastReturnedConv;

        StubVoiceChatModel(String vendor, String... supportedModels) {
            this.vendor = vendor;
            this.supportedModels = supportedModels;
        }

        @Override
        public String vendor() {
            return vendor;
        }

        @Override
        public String[] supportedModels() {
            return supportedModels;
        }

        @Override
        public VoiceConversation open(VoiceChatConfig config) {
            this.lastConfig = config;
            this.lastReturnedConv = new StubVoiceConversation();
            return lastReturnedConv;
        }
    }

    /**
     * 最小 VoiceConversation stub — close() 幂等,isActive() 切换状态。
     */
    private static class StubVoiceConversation implements VoiceConversation {
        private volatile boolean active = true;

        @Override
        public Flow.Publisher<VoiceChatEvent> events() {
            return subscriber -> {
                subscriber.onComplete();
            };
        }

        @Override
        public void sendAudio(VoiceChatEvent.AudioFrame frame) {
            if (!active) {
                throw new IllegalStateException("会话已关闭");
            }
        }

        @Override
        public void sendText(String text) {
            if (!active) {
                throw new IllegalStateException("会话已关闭");
            }
        }

        @Override
        public void interrupt() {
            // no-op
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            active = false;
        }
    }
}