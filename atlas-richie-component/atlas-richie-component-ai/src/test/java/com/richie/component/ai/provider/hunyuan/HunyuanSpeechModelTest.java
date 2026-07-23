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
package com.richie.component.ai.provider.hunyuan;

import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.ai.provider.sign.Tc3Signer;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 腾讯混元 TTS / STT 适配器的纯单元测试。
 * <p>
 * 测试目标（hermetic —— 不发起真实网络请求）：
 * <ul>
 *   <li>解析层：构造假的 JSON 响应字符串，验证响应 DTO 的反序列化与 base64 ↔ byte[] 映射</li>
 *   <li>签名层：调用 {@link Tc3Signer#buildAuthorization} 验证返回的 Authorization 头符合 TC3 规范</li>
 * </ul>
 * 不验证 HttpClient 调用 —— 该层依赖 Spring Bean 装配，由 AiModelAutoConfiguration 与集成测试覆盖。
 */
class HunyuanSpeechModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Tc3Signer TC3_SIGNER = new Tc3Signer();

    // ==================== TTS 响应解析 ====================

    @Test
    void ttsResponseDto_shouldDeserializeBase64AudioIntoBytes() throws Exception {
        // 准备：构造一段待 base64 的字节，编码后嵌入假 JSON
        byte[] original = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x10, 0x20, 0x30};
        String audioB64 = Base64.getEncoder().encodeToString(original);
        String json = "{\"Response\":{\"Audio\":\"" + audioB64 + "\",\"RequestId\":\"req-123\"}}";

        // 反序列化为内部 DTO（同样使用项目内 JsonUtils 验证 Jackson 解析链路一致）
        HunyuanTextToSpeechModel.TtsRawResponse raw =
                JsonUtils.getInstance().deserialize(json, HunyuanTextToSpeechModel.TtsRawResponse.class);

        assertNotNull(raw, "raw response must not be null");
        assertNotNull(raw.response, "raw.response must not be null");
        assertEquals("req-123", raw.response.requestId);
        assertEquals(audioB64, raw.response.audio);

        // 调用适配器的转换方法：base64 字符串 → byte[]
        org.springframework.ai.audio.tts.TextToSpeechResponse resp =
                HunyuanTextToSpeechModel.toSpeechResponse(raw);

        assertNotNull(resp);
        byte[] out = resp.getResult().getOutput();
        assertNotNull(out, "audio bytes must not be null");
        assertEquals(original.length, out.length, "decoded length must match original");
        for (int i = 0; i < original.length; i++) {
            assertEquals(original[i], out[i], "byte[" + i + "] must match");
        }
    }

    @Test
    void ttsResponseDto_shouldHandleMissingAudioAsEmptyBytes() {
        // 缺失 Audio 字段时回落到空字节数组（不抛错）
        org.springframework.ai.audio.tts.TextToSpeechResponse resp =
                HunyuanTextToSpeechModel.toSpeechResponse(null);
        assertNotNull(resp);
        assertNotNull(resp.getResult().getOutput());
        assertEquals(0, resp.getResult().getOutput().length);

        HunyuanTextToSpeechModel.TtsRawResponse empty = new HunyuanTextToSpeechModel.TtsRawResponse();
        org.springframework.ai.audio.tts.TextToSpeechResponse resp2 =
                HunyuanTextToSpeechModel.toSpeechResponse(empty);
        assertNotNull(resp2);
        assertEquals(0, resp2.getResult().getOutput().length);
    }

    @Test
    void ttsResponseDto_shouldHandleInvalidBase64AsEmptyBytes() {
        HunyuanTextToSpeechModel.TtsRawResponse raw = new HunyuanTextToSpeechModel.TtsRawResponse();
        HunyuanTextToSpeechModel.TtsRawInner inner = new HunyuanTextToSpeechModel.TtsRawInner();
        inner.audio = "!!!not-base64@@@";
        raw.response = inner;

        org.springframework.ai.audio.tts.TextToSpeechResponse resp =
                HunyuanTextToSpeechModel.toSpeechResponse(raw);
        assertNotNull(resp);
        assertNotNull(resp.getResult().getOutput());
        assertEquals(0, resp.getResult().getOutput().length,
                "invalid base64 must degrade to empty bytes");
    }

    // ==================== STT 响应解析 ====================

    @Test
    void sttResponseDto_shouldDeserializeResultIntoText() throws Exception {
        String text = "你好，世界。";
        // 用 Jackson 转义确保中文 / 标点不破坏 JSON 结构
        String json = MAPPER.writeValueAsString(java.util.Map.of(
                "Response", java.util.Map.of(
                        "Result", text,
                        "RequestId", "req-stt-1")));

        HunyuanTranscriptionModel.SttRawResponse raw =
                JsonUtils.getInstance().deserialize(json, HunyuanTranscriptionModel.SttRawResponse.class);

        assertNotNull(raw);
        assertNotNull(raw.response);
        assertEquals("req-stt-1", raw.response.requestId);
        assertEquals(text, raw.response.result);

        org.springframework.ai.audio.transcription.AudioTranscriptionResponse resp =
                HunyuanTranscriptionModel.toAudioTranscriptionResponse(raw);
        assertNotNull(resp);
        assertEquals(text, resp.getResult().getOutput());
    }

    @Test
    void sttResponseDto_shouldHandleMissingResultAsEmptyText() {
        org.springframework.ai.audio.transcription.AudioTranscriptionResponse resp =
                HunyuanTranscriptionModel.toAudioTranscriptionResponse(null);
        assertNotNull(resp);
        assertEquals("", resp.getResult().getOutput());

        HunyuanTranscriptionModel.SttRawResponse empty = new HunyuanTranscriptionModel.SttRawResponse();
        org.springframework.ai.audio.transcription.AudioTranscriptionResponse resp2 =
                HunyuanTranscriptionModel.toAudioTranscriptionResponse(empty);
        assertEquals("", resp2.getResult().getOutput());
    }

    // ==================== TC3 头构造 ====================

    @Test
    void tc3Signer_shouldBuildTtsAuthorizationHeader() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        // TODO: 填写实际腾讯云 SecretId（在 https://console.cloud.tencent.com/cam/capi 获取）
        cfg.setSecretId("");
        // TODO: 填写实际腾讯云 SecretKey
        cfg.setSecretKey("");
        cfg.setRegion("ap-guangzhou");
        cfg.setEndpoint("tts.tencentcloudapi.com");
        cfg.setModel("101001");

        String bodyJson = "{\"AppId\":\"1300000001\",\"Text\":\"你好\",\"VoiceType\":101001,\"Codec\":\"mp3\"}";
        long ts = 1700000000L;

        String auth = TC3_SIGNER.buildAuthorization(
                cfg.getSecretId(),
                cfg.getSecretKey(),
                HunyuanTextToSpeechModel.TC3_SERVICE,
                cfg.getRegion(),
                HunyuanTextToSpeechModel.TC3_ACTION,
                cfg.getEndpoint(),
                bodyJson,
                ts);

        assertNotNull(auth, "authorization header must not be null");
        assertFalse(auth.isBlank(), "authorization header must not be blank");
        // TC3 规范：必须以 "TC3-HMAC-SHA256 Credential=" 开头
        assertTrue(auth.startsWith("TC3-HMAC-SHA256 Credential="),
                "authorization header must start with 'TC3-HMAC-SHA256 Credential=', got: " + auth);
        // 必须包含 SecretId
        assertTrue(auth.contains(cfg.getSecretId()),
                "authorization header must contain secretId, got: " + auth);
        // 必须包含 SignedHeaders（TC3 签名头列表）
        assertTrue(auth.contains("SignedHeaders="),
                "authorization header must contain 'SignedHeaders=', got: " + auth);
        // 必须包含 Signature
        assertTrue(auth.contains("Signature="),
                "authorization header must contain 'Signature=', got: " + auth);
    }

    @Test
    void tc3Signer_shouldBuildSttAuthorizationHeader() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        // TODO: 填写实际腾讯云 SecretId
        cfg.setSecretId("");
        // TODO: 填写实际腾讯云 SecretKey
        cfg.setSecretKey("");
        cfg.setRegion("ap-guangzhou");
        cfg.setEndpoint("asr.tencentcloudapi.com");
        cfg.setModel("16k_zh");

        String bodyJson = "{\"EngineModelType\":\"16k_zh\",\"ChannelNum\":1,\"ResTextFormat\":0,"
                + "\"SourceType\":1,\"Data\":\"AAAA\",\"DataType\":1}";
        long ts = 1700000000L;

        String auth = TC3_SIGNER.buildAuthorization(
                cfg.getSecretId(),
                cfg.getSecretKey(),
                HunyuanTranscriptionModel.TC3_SERVICE,
                cfg.getRegion(),
                HunyuanTranscriptionModel.TC3_ACTION,
                cfg.getEndpoint(),
                bodyJson,
                ts);

        assertNotNull(auth);
        assertTrue(auth.startsWith("TC3-HMAC-SHA256 Credential="),
                "STT authorization header must start with 'TC3-HMAC-SHA256 Credential=', got: " + auth);
        assertTrue(auth.contains(cfg.getSecretId()));
        assertTrue(auth.contains("Signature="));
    }

    @Test
    void tc3Signer_shouldProduceDeterministicSignatureForSameInput() {
        // 同一组入参 → 同一签名字符串（可重现性是 TC3 调试时的硬要求）
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        // TODO: 填写实际腾讯云 SecretId
        cfg.setSecretId("");
        // TODO: 填写实际腾讯云 SecretKey
        cfg.setSecretKey("");
        cfg.setRegion("ap-guangzhou");
        cfg.setEndpoint("tts.tencentcloudapi.com");

        String bodyJson = "{\"Text\":\"hi\"}";
        long ts = 1700000000L;

        String a1 = TC3_SIGNER.buildAuthorization(cfg.getSecretId(), cfg.getSecretKey(),
                HunyuanTextToSpeechModel.TC3_SERVICE, cfg.getRegion(),
                HunyuanTextToSpeechModel.TC3_ACTION, cfg.getEndpoint(), bodyJson, ts);
        String a2 = TC3_SIGNER.buildAuthorization(cfg.getSecretId(), cfg.getSecretKey(),
                HunyuanTextToSpeechModel.TC3_SERVICE, cfg.getRegion(),
                HunyuanTextToSpeechModel.TC3_ACTION, cfg.getEndpoint(), bodyJson, ts);
        assertEquals(a1, a2, "same inputs must yield identical authorization header");
    }

    @Test
    void tc3Signer_shouldChangeSignatureWhenTimestampDiffers() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        // TODO: 填写实际腾讯云 SecretId
        cfg.setSecretId("");
        // TODO: 填写实际腾讯云 SecretKey
        cfg.setSecretKey("");
        cfg.setRegion("ap-guangzhou");
        cfg.setEndpoint("tts.tencentcloudapi.com");

        String bodyJson = "{\"Text\":\"hi\"}";
        String a1 = TC3_SIGNER.buildAuthorization(cfg.getSecretId(), cfg.getSecretKey(),
                HunyuanTextToSpeechModel.TC3_SERVICE, cfg.getRegion(),
                HunyuanTextToSpeechModel.TC3_ACTION, cfg.getEndpoint(), bodyJson, 1700000000L);
        String a2 = TC3_SIGNER.buildAuthorization(cfg.getSecretId(), cfg.getSecretKey(),
                HunyuanTextToSpeechModel.TC3_SERVICE, cfg.getRegion(),
                HunyuanTextToSpeechModel.TC3_ACTION, cfg.getEndpoint(), bodyJson, 1700000001L);
        // 不同 timestamp → 不同 signature（这是 TC3 防重放的硬要求）
        assertFalse(a1.equals(a2),
                "different timestamps must yield different authorization headers");
    }
}