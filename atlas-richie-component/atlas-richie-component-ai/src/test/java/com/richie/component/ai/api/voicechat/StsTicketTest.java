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
package com.richie.component.ai.api.voicechat;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一性测试 — 验证 5 种 asXxx() 方法不向业务调用方泄露 vendor 字符串 (除错误消息中用于调试的 vendor 外)。
 *
 * <p>原则 J 守护:业务侧拿到的头映射只含 RFC 标准 / vendor 标准头,不含 vendor 字符串。
 */
class StsTicketTest {

    /**
     * 场景 1 — Bearer 域:返回标准 Authorization 头,业务侧拿到的 Map key 仅含 "Authorization",
     * 不暴露 zhipu/dashscope 等 vendor 字符串到业务分支。
     */
    @Test
    void asBearerHeaders_should_return_only_standard_auth_headers() {
        StsTicket ticket = StsTicket.builder()
                .vendor(StsTicket.VENDOR_ZHIPU)
                .model("glm-4-voice")
                .capability(StsTicket.CAPABILITY_VOICE_CHAT)
                .endpoint("wss://open.bigmodel.cn/api/paas/v4/realtime")
                .ttlSeconds(3600)
                .bearerHeaders(Map.of("Authorization", "Bearer test-key"))
                .build();

        Map<String, String> headers = ticket.asBearerHeaders();

        assertEquals(1, headers.size(), "业务侧只看到 1 个标准 Authorization 头");
        assertEquals("Bearer test-key", headers.get("Authorization"));
        // vendor 字符串不出现在 key 里
        assertTrue(headers.keySet().stream().noneMatch(k -> k.contains(StsTicket.VENDOR_ZHIPU)));
        // vendor() 仅在 ticket 元数据上,业务侧按需调 vendor() 做日志,不参与 if/else
        assertEquals(StsTicket.VENDOR_ZHIPU, ticket.vendor());
    }

    /**
     * 场景 2 — TC3 域:返回 5 个标准 TC3 头,Authorization 格式符合 TC3-HMAC-SHA256 规范,
     * 业务侧不感知 Hunyuan 等具体 vendor 名。
     */
    @Test
    void asTc3Headers_should_return_only_standard_tc3_headers() {
        StsTicket.Tc3Material material = new StsTicket.Tc3Material(
                "AKIDTest", "secretTest", "tts", "ap-guangzhou",
                "https://tts.tencentcloudapi.com");
        StsTicket ticket = StsTicket.builder()
                .vendor(StsTicket.VENDOR_HUNYUAN_TTS)
                .model("HunyuanTTS")
                .capability(StsTicket.CAPABILITY_TTS_STREAM)
                .endpoint("https://tts.tencentcloudapi.com")
                .ttlSeconds(3600)
                .tc3Material(material)
                .build();

        Map<String, String> headers = ticket.asTc3Headers("TextToVoice", "{\"Text\":\"hi\"}");

        // 标准 TC3 头集合
        assertTrue(headers.containsKey("Authorization"), "含 Authorization");
        assertTrue(headers.containsKey("Content-Type"), "含 Content-Type");
        assertTrue(headers.containsKey("Host"), "含 Host");
        assertTrue(headers.containsKey("X-TC-Action"), "含 X-TC-Action");
        assertTrue(headers.containsKey("X-TC-Timestamp"), "含 X-TC-Timestamp");
        assertTrue(headers.containsKey("X-TC-Version"), "含 X-TC-Version");
        // Authorization 格式校验
        String auth = headers.get("Authorization");
        assertNotNull(auth);
        assertTrue(auth.startsWith("TC3-HMAC-SHA256 Credential=AKIDTest/"),
                "Authorization 必须以 TC3-HMAC-SHA256 Credential=AKIDTest 开头,实际: " + auth);
        assertTrue(auth.contains("SignedHeaders=content-type;host;x-tc-action"));
        assertTrue(auth.contains("Signature="));
    }

    /**
     * 场景 3 — X-Api-Key 域:返回 4 个 X-Api-* 标准头,业务侧拿到的 key 全是 X-Api-* 前缀。
     */
    @Test
    void asHeaderMap_should_return_only_x_api_key_headers() {
        StsTicket ticket = StsTicket.builder()
                .vendor(StsTicket.VENDOR_DOUBAO_OPENSPEECH)
                .model("seed-tts-2.0")
                .capability(StsTicket.CAPABILITY_TTS_STREAM)
                .endpoint("wss://openspeech.bytedance.com/api/v3/tts/bidirection")
                .ttlSeconds(3600)
                .headerMap(Map.of(
                        "X-Api-Key", "test-key",
                        "X-Api-Resource-Id", "volc.test.voice",
                        "X-Api-App-Id", "test-app",
                        "X-Api-Access-Key", "test-ak"))
                .build();

        Map<String, String> headers = ticket.asHeaderMap();

        assertEquals(4, headers.size());
        assertEquals("test-key", headers.get("X-Api-Key"));
        assertEquals("volc.test.voice", headers.get("X-Api-Resource-Id"));
        // 业务侧拿到的 key 全是标准 X-Api-*,不含 doubao/openspeech 等 vendor 字符串
        assertTrue(headers.keySet().stream().allMatch(k -> k.startsWith("X-Api-")),
                "所有头必须以 X-Api- 开头,实际: " + headers.keySet());
    }

    /**
     * 场景 4 — 域校验:跨域调用抛 UnsupportedOperationException,且异常消息不暴露业务判断所需的 vendor 字符串。
     *
     * <p>原则 J 要求异常消息中的 vendor 仅用于日志/调试,不应被业务代码 catch 后做分支判断。
     * 本测试只验证异常正确抛出,业务侧应使用 {@link #headersForRequest(String, byte[])} 而非手动 try/catch 各域。
     */
    @Test
    void cross_domain_access_should_throw_UnsupportedOperationException() {
        StsTicket bearerTicket = StsTicket.builder()
                .vendor(StsTicket.VENDOR_ZHIPU)
                .model("glm-4-voice")
                .capability(StsTicket.CAPABILITY_VOICE_CHAT)
                .endpoint("wss://example.com")
                .ttlSeconds(3600)
                .bearerHeaders(Map.of("Authorization", "Bearer k"))
                .build();

        // Bearer 域 ticket 调 TC3 应抛
        UnsupportedOperationException tc3Ex = assertThrows(UnsupportedOperationException.class,
                () -> bearerTicket.asTc3Headers("act", "{}"));
        assertTrue(tc3Ex.getMessage().contains("TC3 域"), "异常消息应说明缺失的域");

        // Bearer 域 ticket 调 AppCode 应抛
        assertThrows(UnsupportedOperationException.class, bearerTicket::asAppCodeHeaders);

        // Bearer 域 ticket 调 AK-SK 应抛
        assertThrows(UnsupportedOperationException.class,
                () -> bearerTicket.asSignedHeaders("POST", "/", "{}".getBytes(StandardCharsets.UTF_8)));

        // Bearer 域 ticket 调 X-Api-Key 应抛
        assertThrows(UnsupportedOperationException.class, bearerTicket::asHeaderMap);
    }

    /**
     * 场景 5 — 通用方法 headersForRequest: 业务侧无需判断 vendor,按 capability 自动选最适域。
     *
     * <p>capability=VOICE_CHAT/STT_STREAM 时优先 Bearer / X-Api-Key 静态头;
     * capability=TTS_STREAM 时按 TC3/AK-SK/AppCode/Bearer/X-Api-Key 优先级选择。
     */
    @Test
    void headersForRequest_should_auto_select_auth_domain_without_vendor_branch() {
        // VOICE_CHAT + Bearer — 静态 Bearer 头直接返回
        StsTicket chatTicket = StsTicket.builder()
                .vendor(StsTicket.VENDOR_ZHIPU)
                .model("glm-4-voice")
                .capability(StsTicket.CAPABILITY_VOICE_CHAT)
                .endpoint("wss://example.com")
                .ttlSeconds(3600)
                .bearerHeaders(Map.of("Authorization", "Bearer k1"))
                .build();
        Map<String, String> chatHeaders = chatTicket.headersForRequest("ignored", new byte[0]);
        assertEquals("Bearer k1", chatHeaders.get("Authorization"));

        // TTS_STREAM + TC3 — 自动按 action 重算 TC3 头
        StsTicket ttsTicket = StsTicket.builder()
                .vendor(StsTicket.VENDOR_HUNYUAN_TTS)
                .model("HunyuanTTS")
                .capability(StsTicket.CAPABILITY_TTS_STREAM)
                .endpoint("https://tts.tencentcloudapi.com")
                .ttlSeconds(3600)
                .tc3Material(new StsTicket.Tc3Material(
                        "AKID", "SECRET", "tts", "ap-guangzhou",
                        "https://tts.tencentcloudapi.com"))
                .build();
        Map<String, String> ttsHeaders = ttsTicket.headersForRequest("TextToVoice", "{}".getBytes(StandardCharsets.UTF_8));
        assertTrue(ttsHeaders.get("Authorization").startsWith("TC3-HMAC-SHA256"));
        assertEquals("TextToVoice", ttsHeaders.get("X-TC-Action"));
    }
}