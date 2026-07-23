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
package com.richie.component.ai.provider.pangu;

import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 盘古 TTS/STT 适配器的无网络单元测试。
 *
 * @author richie696
 * @since 2026-07-20
 */
class PanguSpeechModelTest {

    @Test
    void tts_givenFakeJson_shouldParseBase64Audio() {
        byte[] expectedAudio = "fake-wav-data".getBytes(StandardCharsets.UTF_8);
        String encodedAudio = Base64.getEncoder().encodeToString(expectedAudio);
        String json = "{\"result\":{\"data\":\"%s\",\"isEnd\":true}}"
                .formatted(encodedAudio);
        RequestFixture fixture = futureJsonFixture(json);
        AbstractAudioModelConfig cfg = voiceConfig();
        PanguTextToSpeechModel model =
                new PanguTextToSpeechModel(cfg, fixture.httpClient());

        TextToSpeechResponse response =
                model.callAsync(new TextToSpeechPrompt("你好")).join();

        assertArrayEquals(expectedAudio, response.getResult().getOutput());
        verify(fixture.httpClient()).post(eq(PanguTextToSpeechModel.DEFAULT_TTS_URL), any());
        verify(fixture.request()).header("X-Apig-AppCode", "test-app-code");
        verify(fixture.request()).header("Content-Type", "application/json");
    }

    @Test
    void stt_givenFakeJson_shouldParseTranscript() {
        RequestFixture fixture = futureJsonFixture("{\"result\":{\"text\":\"你好\"}}");
        AbstractAudioModelConfig cfg = voiceConfig();
        PanguTranscriptionModel model =
                new PanguTranscriptionModel(cfg, fixture.httpClient());
        byte[] audio = "fake-wav-data".getBytes(StandardCharsets.UTF_8);

        AudioTranscriptionResponse response = model.callAsync(
                new AudioTranscriptionPrompt(new ByteArrayResource(audio))).join();

        assertEquals("你好", response.getResult().getOutput());
        verify(fixture.httpClient()).post(eq(PanguTranscriptionModel.DEFAULT_STT_URL), any());
        verify(fixture.request()).header("X-Apig-AppCode", "test-app-code");
        verify(fixture.request()).header("Content-Type", "application/json");
    }

    /**
     * 创建带基础鉴权和语音模型名的配置。
     *
     * @return 测试语音模型配置
     */
    private static AbstractAudioModelConfig voiceConfig() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setAppCode("test-app-code");
        cfg.setModel("pangu-test-voice");
        return cfg;
    }

    /**
     * 创建把假 JSON 反序列化为调用方私有 {@code @JsonProperty} DTO 的 HTTP 桩。
     *
     * @param json 假响应 JSON
     * @return HTTP 客户端与请求桩
     */
    private static RequestFixture futureJsonFixture(String json) {
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest request = mock(HttpRequest.class);
        when(httpClient.post(anyString(), any())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.future(ArgumentMatchers.<Class<Object>>any())).thenAnswer(invocation -> {
            Class<Object> responseType = invocation.getArgument(0);
            Object response = JsonUtils.getInstance().deserialize(json, responseType);
            return CompletableFuture.completedFuture(response);
        });
        return new RequestFixture(httpClient, request);
    }

    /** HTTP 桩组合。 */
    private record RequestFixture(HttpClient httpClient, HttpRequest request) {
    }
}
