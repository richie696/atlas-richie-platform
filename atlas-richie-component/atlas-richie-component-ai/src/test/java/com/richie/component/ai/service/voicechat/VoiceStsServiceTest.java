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
import com.richie.component.ai.service.VoiceStsService;
import com.richie.component.ai.service.impl.VoiceStsServiceImpl;
import com.richie.component.ai.support.sign.BearerStsSigner;
import com.richie.component.ai.support.sign.Tc3StsSigner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VoiceStsService 单测 — 验证 4 项核心不变量:
 * <ol>
 *   <li>按 ctx.vendor 正确分派到对应 StsSigner</li>
 *   <li>未知 vendor 时 fail-fast 抛 IllegalStateException</li>
 *   <li>TTL 缓存续签 (同一 vendor 多次签发,使用同一 signer 实例,不会重复创建)</li>
 *   <li>业务侧通过 service 调用,完全不感知具体 signer impl 类型</li>
 * </ol>
 */
class VoiceStsServiceTest {

    /**
     * 场景 1 — 按 ctx.vendor 正确分派到对应 signer。
     *
     * <p>vendor=zhipu + capability=voice-chat → Bearer 域 signer
     * vendor=hunyuan-tts + capability=tts-stream → TC3 域 signer
     */
    @Test
    void dispatch_by_vendor_should_route_to_correct_signer() {
        BearerStsSigner zhipuSigner = new BearerStsSigner(
                StsTicket.VENDOR_ZHIPU, "test-zhipu-key",
                "wss://open.bigmodel.cn/api/paas/v4/realtime");
        Tc3StsSigner hunyuanSigner = new Tc3StsSigner(
                StsTicket.VENDOR_HUNYUAN_TTS, "AKID", "SECRET",
                "ap-guangzhou", "tts", "https://tts.tencentcloudapi.com");

        VoiceStsService service = new VoiceStsServiceImpl(List.of(zhipuSigner, hunyuanSigner));

        StsTicket zhipuTicket = service.sign(StsTicket.VENDOR_ZHIPU,
                StsTicket.CAPABILITY_VOICE_CHAT, "glm-4-voice");
        assertEquals(StsTicket.VENDOR_ZHIPU, zhipuTicket.vendor());
        assertEquals("Bearer test-zhipu-key", zhipuTicket.asBearerHeaders().get("Authorization"));

        StsTicket hunyuanTicket = service.sign(StsTicket.VENDOR_HUNYUAN_TTS,
                StsTicket.CAPABILITY_TTS_STREAM, "HunyuanTTS");
        assertEquals(StsTicket.VENDOR_HUNYUAN_TTS, hunyuanTicket.vendor());
        assertTrue(hunyuanTicket.asTc3Headers("TextToVoice", "{}")
                .get("Authorization").startsWith("TC3-HMAC-SHA256"));
    }

    /**
     * 场景 2 — 未知 vendor fail-fast。
     */
    @Test
    void unknown_vendor_should_fail_fast_with_IllegalStateException() {
        BearerStsSigner zhipuSigner = new BearerStsSigner(
                StsTicket.VENDOR_ZHIPU, "test-zhipu-key",
                "wss://open.bigmodel.cn/api/paas/v4/realtime");
        VoiceStsService service = new VoiceStsServiceImpl(List.of(zhipuSigner));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.sign("unknown-vendor-xyz",
                        StsTicket.CAPABILITY_VOICE_CHAT, "some-model"));
        // 异常消息应包含:未找到 + 已注册 vendor 列表 (便于运维调试)
        assertTrue(ex.getMessage().contains("未找到"));
        assertTrue(ex.getMessage().contains(StsTicket.VENDOR_ZHIPU),
                "异常消息应列出已注册 vendor 便于排查");
    }

    /**
     * 场景 3 — TTL 缓存续签验证:同一 vendor 多次签发,返回不同 expiresAt(随当前时间变化),
     * 证明每次都重新走 sign() (而不是缓存 ticket)。
     *
     * <p>缓存的是 signer 实例引用(避免每次遍历 list),而不是 ticket 本身 —
     * 因为 ticket 有 TTL,过期必须重新签发。
     */
    @Test
    void multiple_signs_should_re_sign_and_not_cache_ticket() throws InterruptedException {
        BearerStsSigner zhipuSigner = new BearerStsSigner(
                StsTicket.VENDOR_ZHIPU, "test-zhipu-key",
                "wss://open.bigmodel.cn/api/paas/v4/realtime");
        VoiceStsService service = new VoiceStsServiceImpl(List.of(zhipuSigner));

        StsTicket t1 = service.sign(StsTicket.VENDOR_ZHIPU,
                StsTicket.CAPABILITY_VOICE_CHAT, "glm-4-voice");

        // 等 50ms 让 issuedAt 变化 (issuedAt = System.currentTimeMillis())
        Thread.sleep(50);

        StsTicket t2 = service.sign(StsTicket.VENDOR_ZHIPU,
                StsTicket.CAPABILITY_VOICE_CHAT, "glm-4-voice");

        // ticket 重新签发,issuedAt 不同 (但都属于同一个 vendor 的同一 signer 路由结果)
        assertTrue(t2.issuedAt() > t1.issuedAt(),
                "二次签发的 issuedAt 必须晚于首次签发,证明走的是真 sign() 而非缓存 ticket");
        assertEquals(zhipuSigner.vendor(), t2.vendor());
    }

    /**
     * 场景 4 — 业务侧不感知 vendor。
     *
     * <p>验证业务侧拿到的是 StsTicket 抽象 (只有 vendor() 字符串用于日志),而具体 signer 类型不暴露。
     * 本测试通过 List<String> listRegisteredVendors() 间接验证 — 业务侧只能看到厂商标识字符串,
     * 拿不到任何 impl 类的 Class 对象。
     */
    @Test
    void business_side_should_not_see_signer_impl_classes() {
        BearerStsSigner zhipuSigner = new BearerStsSigner(
                StsTicket.VENDOR_ZHIPU, "k1", "wss://a");
        Tc3StsSigner hunyuanSigner = new Tc3StsSigner(
                StsTicket.VENDOR_HUNYUAN_TTS, "ak", "sk",
                "ap-guangzhou", "tts", "https://tts.tencentcloudapi.com");
        VoiceStsService service = new VoiceStsServiceImpl(List.of(zhipuSigner, hunyuanSigner));

        // 业务侧只能看到 vendor 字符串列表,无法反射拿 impl 类型
        List<String> vendors = service.listRegisteredVendors();
        assertEquals(2, vendors.size());
        assertTrue(vendors.contains(StsTicket.VENDOR_ZHIPU));
        assertTrue(vendors.contains(StsTicket.VENDOR_HUNYUAN_TTS));

        // 业务侧拿 ticket 也是抽象接口,只调 asXxx() 拿 Map
        StsTicket ticket = service.sign(StsTicket.VENDOR_ZHIPU,
                StsTicket.CAPABILITY_VOICE_CHAT, "glm-4-voice");
        Map<String, String> headers = ticket.asBearerHeaders();
        assertNotNull(headers.get("Authorization"));
    }
}