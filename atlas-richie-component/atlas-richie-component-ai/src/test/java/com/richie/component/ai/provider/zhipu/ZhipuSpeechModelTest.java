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
package com.richie.component.ai.provider.zhipu;

import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.http.core.HttpResponse;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.Resource;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 智谱 GLM-TTS / GLM-ASR 适配器的纯单元测试（hermetic —— 不发起真实网络请求）。
 * <p>
 * 测试策略：
 * <ul>
 *   <li><b>TTS</b>：构造假的 {@link HttpResponse}（任意字节数组 → 模拟 audio/wav 响应），
 *       走 {@link ZhipuTextToSpeechModel#toSpeechResponse(HttpResponse)} 验证响应映射逻辑；
 *       同时验证请求体构造覆盖 model / voice / format 字段。</li>
 *   <li><b>STT</b>：构造假 JSON {@code {"text":"..."}}，用
 *       {@link ZhipuTranscriptionModel.SttRawResponse} 验证反序列化与字段映射。</li>
 * </ul>
 * 不验证 HttpClient 调用 —— 该层依赖 Spring Bean 装配，由集成测试覆盖。
 */
class ZhipuSpeechModelTest {

    // ==================== TTS ====================

    @Test
    void tts_givenFakeAudioBytes_shouldWrapIntoResponse() {
        // 准备：任意二进制字节（模拟 audio/wav 内容）
        byte[] fakeWav = new byte[]{(byte) 0x52, (byte) 0x49, (byte) 0x46, (byte) 0x46, // RIFF
                (byte) 0x24, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x57, (byte) 0x41, (byte) 0x56, (byte) 0x45}; // WAVE
        HttpResponse resp = HttpResponse.of(200, Collections.emptyMap(), fakeWav);

        // 执行
        TextToSpeechResponse out = ZhipuTextToSpeechModel.toSpeechResponse(resp);

        // 断言
        assertNotNull(out, "TTS response must not be null");
        assertNotNull(out.getResult(), "TTS result must not be null");
        byte[] got = out.getResult().getOutput();
        assertNotNull(got, "audio bytes must not be null");
        assertArrayEquals(fakeWav, got, "output bytes must equal input bytes verbatim");
    }

    @Test
    void tts_givenEmptyBody_shouldReturnEmptyBytes() {
        HttpResponse resp = HttpResponse.of(200, Collections.emptyMap(), new byte[0]);

        TextToSpeechResponse out = ZhipuTextToSpeechModel.toSpeechResponse(resp);

        assertNotNull(out);
        assertNotNull(out.getResult());
        assertNotNull(out.getResult().getOutput());
        assertEquals(0, out.getResult().getOutput().length,
                "empty body must degrade to zero-length bytes");
    }

    @Test
    void tts_givenNonSuccessfulStatus_shouldReturnEmptyBytes() {
        HttpResponse resp = HttpResponse.of(401, Collections.emptyMap(),
                "{\"error\":\"unauthorized\"}".getBytes());

        TextToSpeechResponse out = ZhipuTextToSpeechModel.toSpeechResponse(resp);

        assertNotNull(out);
        assertEquals(0, out.getResult().getOutput().length,
                "non-2xx must degrade to zero-length bytes");
    }

    @Test
    void tts_givenNullResponse_shouldReturnEmptyBytes() {
        TextToSpeechResponse out = ZhipuTextToSpeechModel.toSpeechResponse(null);

        assertNotNull(out);
        assertEquals(0, out.getResult().getOutput().length,
                "null response must degrade to zero-length bytes");
    }

    @Test
    void tts_buildRequestBody_shouldUseDefaultsWhenNoOptionsOrConfig() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("test-key");
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);
        TextToSpeechPrompt prompt = new TextToSpeechPrompt("你好，世界。");

        Map<String, Object> body = model.buildRequestBody(prompt, "你好，世界。");

        assertEquals("glm-tts", body.get("model"), "default model must be glm-tts");
        assertEquals("tongtong", body.get("voice"), "default voice must be tongtong");
        assertEquals("wav", body.get("response_format"), "default format must be wav");
        assertEquals("你好，世界。", body.get("input"), "input must be the raw text");
    }

    @Test
    void tts_buildRequestBody_shouldPreferPromptOptionsOverConfig() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("test-key");
        cfg.setModel("glm-tts"); // config default
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);

        TextToSpeechOptions opts = TextToSpeechOptions.builder()
                .model("glm-tts-pro")
                .voice("xiaoyan")
                .build();
        TextToSpeechPrompt prompt = new TextToSpeechPrompt("hi", opts);

        Map<String, Object> body = model.buildRequestBody(prompt, "hi");

        assertEquals("glm-tts-pro", body.get("model"),
                "prompt model option must override config default");
        assertEquals("xiaoyan", body.get("voice"),
                "prompt voice option must override default voice");
    }

    @Test
    void tts_buildRequestBody_shouldUseConfigModelWhenPromptOmits() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("test-key");
        cfg.setModel("custom-zhipu-tts");
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);

        TextToSpeechPrompt prompt = new TextToSpeechPrompt("hi");

        Map<String, Object> body = model.buildRequestBody(prompt, "hi");

        assertEquals("custom-zhipu-tts", body.get("model"),
                "config model must be used when prompt omits model");
    }

    @Test
    void tts_resolveEndpoint_shouldFallbackToDefaultWhenBaseUrlBlank() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);

        assertEquals(ZhipuTextToSpeechModel.DEFAULT_TTS_URL, model.resolveEndpoint(),
                "blank baseUrl must fall back to DEFAULT_TTS_URL");

        cfg.setBaseUrl("https://custom.example.com/audio");
        assertEquals("https://custom.example.com/audio", model.resolveEndpoint(),
                "non-blank baseUrl must override default");
    }

    @Test
    void tts_callAsync_shouldRejectBlankText() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);
        TextToSpeechPrompt prompt = new TextToSpeechPrompt("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> model.callAsync(prompt));
        assertTrue(ex.getMessage().contains("text"),
                "error message must mention 'text', got: " + ex.getMessage());
    }

    @Test
    void tts_callAsync_shouldRejectNullPrompt() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);

        assertThrows(NullPointerException.class, () -> model.callAsync(null));
    }

    @Test
    void tts_call_shouldWrapAsyncFailureAsRuntimeException() {
        // 给一个 null cfg 会立刻在构造期抛 NPE，但 call() 期望能转换异常类型 —— 验证包装路径
        // 这里用最简路径：构造合法 model，但 prompt 文本为空 → callAsync 抛 IllegalArgumentException
        // → call() 把它包装成 RuntimeException("Zhipu TTS failed: ...")
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);
        TextToSpeechPrompt prompt = new TextToSpeechPrompt("");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> model.call(prompt));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("Zhipu TTS"),
                "wrapped exception message must contain vendor name, got: " + ex.getMessage());
    }

    // ==================== STT ====================

    @Test
    void stt_givenFakeJsonResponse_shouldDeserializeTextField() {
        // 准备：模拟智谱 STT 标准响应
        String json = "{\"text\":\"你好，世界。\"}";

        ZhipuTranscriptionModel.SttRawResponse raw =
                JsonUtils.getInstance().deserialize(json, ZhipuTranscriptionModel.SttRawResponse.class);

        assertNotNull(raw, "raw response must not be null");
        assertEquals("你好，世界。", raw.text,
                "SttRawResponse.text must be mapped from JSON 'text' field via @JsonProperty");
    }

    @Test
    void stt_givenFakeJsonResponse_shouldMapIntoAudioTranscriptionResponse() {
        String json = "{\"text\":\"hello world\"}";

        ZhipuTranscriptionModel.SttRawResponse raw =
                JsonUtils.getInstance().deserialize(json, ZhipuTranscriptionModel.SttRawResponse.class);
        AudioTranscriptionResponse resp =
                ZhipuTranscriptionModel.toAudioTranscriptionResponse(raw);

        assertNotNull(resp, "transcription response must not be null");
        assertNotNull(resp.getResult(), "transcription result must not be null");
        assertEquals("hello world", resp.getResult().getOutput(),
                "AudioTranscription.getOutput() must equal parsed text");
    }

    @Test
    void stt_givenEmptyTextField_shouldReturnEmptyString() {
        String json = "{\"text\":\"\"}";

        ZhipuTranscriptionModel.SttRawResponse raw =
                JsonUtils.getInstance().deserialize(json, ZhipuTranscriptionModel.SttRawResponse.class);
        AudioTranscriptionResponse resp =
                ZhipuTranscriptionModel.toAudioTranscriptionResponse(raw);

        assertNotNull(resp);
        assertEquals("", resp.getResult().getOutput());
    }

    @Test
    void stt_givenNullRawResponse_shouldReturnEmptyString() {
        AudioTranscriptionResponse resp =
                ZhipuTranscriptionModel.toAudioTranscriptionResponse(null);

        assertNotNull(resp, "null raw must degrade to non-null response");
        assertEquals("", resp.getResult().getOutput(),
                "null raw must degrade to empty transcript");
    }

    @Test
    void stt_givenRawResponseWithNullText_shouldReturnEmptyString() {
        ZhipuTranscriptionModel.SttRawResponse raw = new ZhipuTranscriptionModel.SttRawResponse();
        // text remains null

        AudioTranscriptionResponse resp = ZhipuTranscriptionModel.toAudioTranscriptionResponse(raw);

        assertNotNull(resp);
        assertEquals("", resp.getResult().getOutput(),
                "raw with null text must degrade to empty transcript");
    }

    @Test
    void stt_buildRequestBody_shouldBase64EncodeAudio() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("test-key");
        cfg.setModel("glm-asr-2512");
        ZhipuTranscriptionModel model = new ZhipuTranscriptionModel(cfg);

        byte[] audio = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        Map<String, Object> body = model.buildRequestBody(audio);

        assertEquals("glm-asr-2512", body.get("model"), "model must match config");
        String expectedB64 = Base64.getEncoder().encodeToString(audio);
        assertEquals(expectedB64, body.get("file"),
                "file must be base64-encoded audio (JSON base64 fallback for multipart limitation)");
    }

    @Test
    void stt_buildRequestBody_shouldFallbackToDefaultModelWhenConfigBlank() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("test-key");
        // cfg.model is null
        ZhipuTranscriptionModel model = new ZhipuTranscriptionModel(cfg);

        Map<String, Object> body = model.buildRequestBody(new byte[]{0x01});

        assertEquals(ZhipuTranscriptionModel.DEFAULT_MODEL, body.get("model"),
                "blank config.model must fall back to DEFAULT_MODEL (glm-asr-2512)");
    }

    @Test
    void stt_resolveEndpoint_shouldFallbackToDefaultWhenBaseUrlBlank() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        ZhipuTranscriptionModel model = new ZhipuTranscriptionModel(cfg);

        assertEquals(ZhipuTranscriptionModel.DEFAULT_STT_URL, model.resolveEndpoint(),
                "blank baseUrl must fall back to DEFAULT_STT_URL");

        cfg.setBaseUrl("https://custom.example.com/asr");
        assertEquals("https://custom.example.com/asr", model.resolveEndpoint());
    }

    @Test
    void stt_callAsync_shouldRejectNullPrompt() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        ZhipuTranscriptionModel model = new ZhipuTranscriptionModel(cfg);

        assertThrows(NullPointerException.class, () -> model.callAsync(null));
    }

    @Test
    void stt_call_shouldWrapAsyncFailureAsRuntimeException() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        ZhipuTranscriptionModel model = new ZhipuTranscriptionModel(cfg);

        // call(null) 会在 call() 自身的 Objects.requireNonNull 处抛 NPE，跳过 try-catch；
        // 这里用合法 prompt + null Resource 让 callAsync 抛 IllegalArgumentException，
        // 走 .get() 包装路径 → RuntimeException("Zhipu STT failed: ...")。
        AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt((Resource) null);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> model.call(prompt));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("Zhipu STT"),
                "wrapped exception must mention 'Zhipu STT', got: " + ex.getMessage());
    }

    // ==================== 构造器校验 ====================

    @Test
    void constructor_shouldRejectNullConfig() {
        assertThrows(NullPointerException.class, () -> new ZhipuTextToSpeechModel(null));
        assertThrows(NullPointerException.class, () -> new ZhipuTranscriptionModel(null));
    }

    @Test
    void httpClientLazyResolution_shouldFailFastWhenBeanMissing() {
        // 构造时未注入 HttpClient，且当前测试无 Spring 上下文 → SpringBeanUtils.getBean 返回 null
        // → callAsync 在内部抛 IllegalStateException
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setApiKey("k");
        cfg.setModel("glm-tts");
        ZhipuTextToSpeechModel model = new ZhipuTextToSpeechModel(cfg);
        TextToSpeechPrompt prompt = new TextToSpeechPrompt("hello");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> model.callAsync(prompt));
        assertTrue(ex.getMessage().contains("HttpClient"),
                "error must mention missing HttpClient, got: " + ex.getMessage());
    }

    // ==================== 公共 DTO 字段验证 ====================

    @Test
    void sttRawResponse_defaultFieldValues_shouldBeNull() {
        // 验证 DTO 字段在 Jackson 反序列化前的默认状态（防止有人误改成带默认值的字段）
        ZhipuTranscriptionModel.SttRawResponse raw = new ZhipuTranscriptionModel.SttRawResponse();
        assertNull(raw.text, "SttRawResponse.text must default to null before deserialization");
    }
}