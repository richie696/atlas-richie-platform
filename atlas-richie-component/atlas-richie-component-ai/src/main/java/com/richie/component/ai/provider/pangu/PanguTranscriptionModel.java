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
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 华为盘古短语音识别适配器。
 * <p>
 * 默认端点基于当前冲刺约定，尚未通过华为盘古公开文档验证；生产环境应优先通过
 * {@code AbstractAudioModelConfig#getBaseUrl()} 配置实际市场实例端点。
 *
 * @author richie696
 * @since 2026-07-20
 */
public class PanguTranscriptionModel implements TranscriptionModel {

    /** 当前冲刺约定的盘古 STT 默认端点。 */
    public static final String DEFAULT_STT_URL =
            "https://pangu.cn-north-4.myhuaweicloud.com/v1.0/voice/asr/sentence";

    private static final String AUDIO_FORMAT = "wav";
    private static final String AUDIO_ENCODING = "BASE64";

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;

    /**
     * 创建盘古短语音识别适配器。
     *
     * @param cfg        语音模型配置
     * @param httpClient HTTP 客户端
     * @throws NullPointerException 当配置或 HTTP 客户端为空时
     */
    public PanguTranscriptionModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * 同步识别短语音。
     *
     * @param prompt Spring AI 语音转写提示
     * @return 语音转写响应
     * @throws RuntimeException 当请求被中断或盘古调用失败时
     */
    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
        try {
            return callAsync(prompt).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Pangu STT interrupted", exception);
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Pangu STT failed: %s".formatted(exception.getMessage()), exception);
        }
    }

    /**
     * 异步识别短语音。
     *
     * @param prompt Spring AI 语音转写提示
     * @return 异步语音转写响应
     * @throws NullPointerException 当提示为空时
     * @throws IllegalArgumentException 当音频资源不可读时
     */
    public CompletableFuture<AudioTranscriptionResponse> callAsync(
            AudioTranscriptionPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Resource audio = prompt.getInstructions();
        if (audio == null || !audio.isReadable()) {
            throw new IllegalArgumentException(
                    "AudioTranscriptionPrompt audio resource must be readable");
        }

        String audioBase64 = Base64.getEncoder().encodeToString(readAudio(audio));
        HttpRequest request = httpClient.post(resolveEndpoint(), buildRequestBody(audioBase64));
        AppCodeSigner.appCodeHeaders(cfg.getAppCode()).forEach(request::header);
        return request
                .header("Content-Type", "application/json")
                .future(PanguSttRawResponse.class)
                .thenApply(PanguTranscriptionModel::toAudioTranscriptionResponse);
    }

    /**
     * 解析配置中的端点。
     *
     * @return 配置端点或盘古默认端点
     */
    private String resolveEndpoint() {
        String baseUrl = cfg.getBaseUrl();
        return baseUrl == null || baseUrl.isBlank() ? DEFAULT_STT_URL : baseUrl;
    }

    /**
     * 读取完整音频资源。
     *
     * @param audio 音频资源
     * @return 音频字节
     * @throws RuntimeException 当资源读取失败时
     */
    private static byte[] readAudio(Resource audio) {
        try {
            return audio.getContentAsByteArray();
        } catch (IOException exception) {
            throw new RuntimeException(
                    "Pangu STT failed to read audio resource: %s"
                            .formatted(audio.getDescription()),
                    exception);
        }
    }

    /**
     * 构造盘古 STT 请求体。
     *
     * @param audioBase64 Base64 编码的音频
     * @return 盘古 STT 请求体
     */
    private static Map<String, Object> buildRequestBody(String audioBase64) {
        Map<String, Object> config = Map.of(
                "audioFormat", AUDIO_FORMAT,
                "property", "\"\"",
                "addPunc", "no");
        Map<String, Object> data = Map.of(
                "audio", audioBase64,
                "encoding", AUDIO_ENCODING);
        return Map.of("config", config, "data", data);
    }

    /**
     * 将盘古响应映射为 Spring AI 响应。
     *
     * @param raw 盘古原始响应
     * @return Spring AI 语音转写响应
     * @throws IllegalStateException 当响应不包含转写文本时
     */
    private static AudioTranscriptionResponse toAudioTranscriptionResponse(
            PanguSttRawResponse raw) {
        if (raw == null || raw.result == null || raw.result.text == null) {
            throw new IllegalStateException("Pangu STT response result.text must not be null");
        }
        return new AudioTranscriptionResponse(new AudioTranscription(raw.result.text));
    }

    /** 盘古 STT 顶层响应。 */
    private static final class PanguSttRawResponse {
        @JsonProperty("result")
        public PanguSttRawResult result;
    }

    /** 盘古 STT 结果。 */
    private static final class PanguSttRawResult {
        @JsonProperty("text")
        public String text;
    }
}
