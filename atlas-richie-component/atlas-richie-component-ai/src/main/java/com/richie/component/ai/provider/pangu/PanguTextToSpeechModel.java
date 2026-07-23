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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.ai.provider.sign.AppCodeSigner;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechMessage;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 华为盘古语音合成适配器。
 * <p>
 * 默认端点基于当前冲刺约定，尚未通过华为盘古公开文档验证；生产环境应优先通过
 * {@code AbstractAudioModelConfig#getBaseUrl()} 配置实际市场实例端点。
 *
 * @author richie696
 * @since 2026-07-20
 */
public class PanguTextToSpeechModel implements TextToSpeechModel {

    /** 当前冲刺约定的盘古 TTS 默认端点。 */
    public static final String DEFAULT_TTS_URL =
            "https://pangu.cn-north-4.myhuaweicloud.com/v1.0/voice/tts";

    private static final String AUDIO_FORMAT = "wav";
    private static final String SAMPLE_RATE = "16000";

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;

    /**
     * 创建盘古语音合成适配器。
     *
     * @param cfg        语音模型配置
     * @param httpClient HTTP 客户端
     * @throws NullPointerException 当配置或 HTTP 客户端为空时
     */
    public PanguTextToSpeechModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * 同步合成文本。
     *
     * @param prompt Spring AI 语音合成提示
     * @return 语音合成响应
     * @throws RuntimeException 当请求被中断或盘古调用失败时
     */
    @Override
    public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
        try {
            return callAsync(prompt).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Pangu TTS interrupted", exception);
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Pangu TTS failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    /**
     * 以单元素流适配盘古一次性音频响应。
     *
     * @param prompt Spring AI 语音合成提示
     * @return 单元素语音合成响应流
     */
    @Override
    public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
        return Mono.fromFuture(callAsync(prompt)).flux();
    }

    /**
     * 异步合成文本。
     *
     * @param prompt Spring AI 语音合成提示
     * @return 异步语音合成响应
     * @throws NullPointerException 当提示为空时
     * @throws IllegalArgumentException 当文本或语音模型名为空时
     */
    public CompletableFuture<TextToSpeechResponse> callAsync(TextToSpeechPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        TextToSpeechMessage message = prompt.getInstructions();
        if (message == null || message.getText() == null || message.getText().isBlank()) {
            throw new IllegalArgumentException("TextToSpeechPrompt text must not be blank");
        }

        HttpRequest request = httpClient.post(resolveEndpoint(), buildRequestBody(message.getText()));
        AppCodeSigner.appCodeHeaders(cfg.getAppCode()).forEach(request::header);
        return request
                .header("Content-Type", "application/json")
                .future(PanguTtsRawResponse.class)
                .thenApply(PanguTextToSpeechModel::toTextToSpeechResponse);
    }

    /**
     * 解析配置中的端点。
     *
     * @return 配置端点或盘古默认端点
     */
    private String resolveEndpoint() {
        String baseUrl = cfg.getBaseUrl();
        return baseUrl == null || baseUrl.isBlank() ? DEFAULT_TTS_URL : baseUrl;
    }

    /**
     * 构造盘古 TTS 请求体。
     *
     * @param text 待合成文本
     * @return 盘古 TTS 请求体
     * @throws IllegalArgumentException 当语音模型名为空时
     */
    private Map<String, Object> buildRequestBody(String text) {
        String voiceName = cfg.getModel();
        if (voiceName == null || voiceName.isBlank()) {
            throw new IllegalArgumentException(
                    "AbstractAudioModelConfig.model (voiceName) is required for Pangu TTS");
        }
        Map<String, Object> config = Map.of(
                "audioFormat", AUDIO_FORMAT,
                "sampleRate", SAMPLE_RATE,
                "voiceName", voiceName,
                "volume", "\"\"",
                "speed", "\"\"");
        return Map.of("config", config, "text", text);
    }

    /**
     * 将盘古响应映射为 Spring AI 响应。
     *
     * @param raw 盘古原始响应
     * @return Spring AI 语音合成响应
     * @throws IllegalStateException 当响应不包含音频数据时
     * @throws IllegalArgumentException 当音频数据不是合法 Base64 时
     */
    private static TextToSpeechResponse toTextToSpeechResponse(PanguTtsRawResponse raw) {
        if (raw == null || raw.result == null
                || raw.result.data == null || raw.result.data.isBlank()) {
            throw new IllegalStateException("Pangu TTS response result.data must not be blank");
        }
        byte[] audio = Base64.getDecoder().decode(raw.result.data);
        return new TextToSpeechResponse(List.of(new Speech(audio)));
    }

    /** 盘古 TTS 顶层响应。 */
    private static final class PanguTtsRawResponse {
        @JsonProperty("result")
        public PanguTtsRawResult result;
    }

    /** 盘古 TTS 结果。 */
    private static final class PanguTtsRawResult {
        @JsonProperty("data")
        public String data;

        @JsonProperty("isEnd")
        public Boolean isEnd;
    }
}
