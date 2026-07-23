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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.ai.provider.sign.Tc3Signer;
import com.richie.component.http.core.HttpClient;
import com.richie.context.utils.data.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechMessage;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 腾讯混元（Hunyuan）语音合成（TTS）模型适配器，实现 Spring AI 标准 {@link TextToSpeechModel}。
 * <p>
 * 调用腾讯云混元 TTS 接口 {@code POST https://tts.tencentcloudapi.com}，
 * 请求体形态：
 * <pre>{@code
 * { "AppId": "...", "Text": "...", "VoiceType": 0, "Codec": "mp3" }
 * }</pre>
 * 响应体形态：
 * <pre>{@code
 * { "Response": { "Audio": "<base64>", "RequestId": "..." } }
 * }</pre>
 * 鉴权使用 TC3-HMAC-SHA256 签名，由 {@link Tc3Signer} 计算并以
 * {@code Authorization / X-TC-Action / X-TC-Version / X-TC-Timestamp / X-TC-Region} 头传递。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class HunyuanTextToSpeechModel implements TextToSpeechModel {

    /** TC3 服务名（TTS 接口固定为 {@code tts}）。 */
    public static final String TC3_SERVICE = "tts";

    /** TC3 接口 action 名。 */
    public static final String TC3_ACTION = "TextToVoice";

    /** TC3 接口版本号。 */
    public static final String TC3_VERSION = "2019-08-23";

    /**
     * TC3 签名 canonical header 中的 Content-Type（{@link Tc3Signer} 把它写死在签名中），
     * 因此实际请求头必须保持完全一致 —— 否则服务端会拒绝该请求。
     */
    public static final String CONTENT_TYPE = "application/json; charset=utf-8";

    /** 音频编码格式：mp3。 */
    public static final String DEFAULT_CODEC = "mp3";

    /** 当 {@code AbstractAudioModelConfig#getModel()} 解析为非法整数时使用的默认 VoiceType（云小宁）。 */
    public static final int DEFAULT_VOICE_TYPE = 0;

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;
    private final Tc3Signer tc3Signer;

    public HunyuanTextToSpeechModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this(cfg, httpClient, new Tc3Signer());
    }

    /**
     * 包内构造器，允许测试或 Spring 容器注入共享的 {@link Tc3Signer} 单例。
     */
    HunyuanTextToSpeechModel(AbstractAudioModelConfig cfg, HttpClient httpClient, Tc3Signer tc3Signer) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.tc3Signer = Objects.requireNonNull(tc3Signer, "tc3Signer must not be null");
    }

    /**
     * 同步入口：内部走 {@link HttpClient#future} 通道，便于上层切换为异步时直接复用同一调用栈。
     */
    @Override
    public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        try {
            return callAsync(prompt).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Hunyuan TTS interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Hunyuan TTS failed: " + e.getMessage(), e);
        }
    }

    /**
     * 流式入口：腾讯云混元 TTS 默认一次性返回整段音频，本实现把同步结果包成单元素 {@link Flux}。
     * <p>
     * 该方法必须存在 —— Spring AI 的 {@link TextToSpeechModel} 通过父接口
     * {@link org.springframework.ai.audio.tts.StreamingTextToSpeechModel} 强制要求。
     */
    @Override
    public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
        return Mono.fromFuture(callAsync(prompt)).flux();
    }

    /**
     * 异步入口：以 {@link CompletableFuture} 形式返回，便于上游做并发编排。
     * 非 Spring AI 接口方法，仅做便捷暴露。
     */
    public CompletableFuture<TextToSpeechResponse> callAsync(TextToSpeechPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        TextToSpeechMessage message = prompt.getInstructions();
        if (message == null) {
            throw new IllegalArgumentException("TextToSpeechPrompt instructions must not be null");
        }
        String text = message.getText();
        if (text == null) {
            throw new IllegalArgumentException("TextToSpeechPrompt text must not be null");
        }

        // 1) 构造 Map 请求体（便于维护字段顺序与命名）
        Map<String, Object> body = buildRequestBody(text);
        // 2) TC3 签名对 body hash 敏感：必须把同一份 JSON 字节流送出去，因此先序列化为字符串
        //    再以 String 形式交给 http-core，避免 http-core 二次序列化产生差异。
        String bodyJson = JsonUtils.getInstance().serialize(body);

        long timestamp = System.currentTimeMillis() / 1000L;
        String authorization = tc3Signer.buildAuthorization(
                cfg.getSecretId(),
                cfg.getSecretKey(),
                TC3_SERVICE,
                cfg.getRegion(),
                TC3_ACTION,
                cfg.getEndpoint(),
                bodyJson,
                timestamp);

        return httpClient.post(normalizeUrl(cfg.getEndpoint()), bodyJson)
                .header("Authorization", authorization)
                .header("Content-Type", CONTENT_TYPE)
                .header("X-TC-Action", TC3_ACTION)
                .header("X-TC-Version", TC3_VERSION)
                .header("X-TC-Timestamp", Long.toString(timestamp))
                .header("X-TC-Region", cfg.getRegion() == null ? "" : cfg.getRegion())
                .future(TtsRawResponse.class)
                .thenApply(HunyuanTextToSpeechModel::toSpeechResponse);
    }

    /**
     * 把 {@link AbstractAudioModelConfig#getEndpoint()} 规范化为 {@code http(s)://host} 形式，
     * 便于 {@link HttpClient#post(String, Object)} 直接发起请求。
     * <p>
     * 配置字段示例：
     * <ul>
     *   <li>{@code tts.tencentcloudapi.com} → {@code https://tts.tencentcloudapi.com}</li>
     *   <li>{@code https://tts.tencentcloudapi.com} → 原样返回</li>
     * </ul>
     * 该规范不影响签名 —— {@link Tc3Signer#buildAuthorization} 内部独立解析 host。
     */
    private static String normalizeUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("AbstractAudioModelConfig.endpoint must not be blank for Hunyuan TTS");
        }
        String e = endpoint.trim();
        if (e.startsWith("http://") || e.startsWith("https://")) {
            return e;
        }
        return "https://" + e;
    }

    /**
     * 把文本与配置装配为混元 TTS 请求体。
     * <p>
     * 字段名遵循腾讯云 API 3.0 约定（PascalCase）：
     * <ul>
     *   <li>{@code AppId}：来自 {@link AbstractAudioModelConfig#getAppId()}（可为 null —— 公共账号鉴权不依赖 AppId）</li>
     *   <li>{@code Text}：待合成文本</li>
     *   <li>{@code VoiceType}：从 {@link AbstractAudioModelConfig#getModel()} 解析为整数；
     *       解析失败则回落到 {@link #DEFAULT_VOICE_TYPE}</li>
     *   <li>{@code Codec}：固定 {@value #DEFAULT_CODEC}</li>
     * </ul>
     */
    private Map<String, Object> buildRequestBody(String text) {
        Map<String, Object> body = new HashMap<>();
        if (cfg.getAppId() != null && !cfg.getAppId().isBlank()) {
            body.put("AppId", cfg.getAppId());
        }
        body.put("Text", text);
        body.put("VoiceType", resolveVoiceType(cfg.getModel()));
        body.put("Codec", DEFAULT_CODEC);
        return body;
    }

    /**
     * 解析 {@link AbstractAudioModelConfig#getModel()} 字符串为 VoiceType 整数。
     * 支持 "101001" 直接给数字，或 "voice:101001" 风格；其他情形回落到默认。
     */
    private static int resolveVoiceType(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_VOICE_TYPE;
        }
        String trimmed = model.startsWith("voice:") ? model.substring("voice:".length()) : model;
        try {
            return Integer.parseInt(trimmed.trim());
        } catch (NumberFormatException e) {
            log.warn("Hunyuan TTS VoiceType 解析失败（model={}），回落默认 {}", model, DEFAULT_VOICE_TYPE);
            return DEFAULT_VOICE_TYPE;
        }
    }

    /**
     * 把混元响应装配为 Spring AI {@link TextToSpeechResponse}。
     * 响应缺失或 Audio 字段为空白时回落到空字节数组 —— 上层可通过 {@code getResult().getOutput().length == 0} 自行判断。
     */
    static TextToSpeechResponse toSpeechResponse(TtsRawResponse raw) {
        if (raw == null || raw.response == null || raw.response.audio == null) {
            log.warn("Hunyuan TTS returned empty body");
            return new TextToSpeechResponse(Collections.singletonList(new Speech(new byte[0])));
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw.response.audio);
        } catch (IllegalArgumentException e) {
            log.warn("Hunyuan TTS Audio 字段不是合法 Base64：{}", e.getMessage());
            bytes = new byte[0];
        }
        return new TextToSpeechResponse(Collections.singletonList(new Speech(bytes)));
    }

    // ====== Hunyuan response DTOs ======

    /** 顶层响应：{@code { "Response": { "Audio": "...", "RequestId": "..." } }}。 */
    static class TtsRawResponse {
        @JsonProperty("Response")
        public TtsRawInner response;
    }

    /** {@code Response} 子对象。 */
    static class TtsRawInner {
        @JsonProperty("Audio")
        public String audio;

        @JsonProperty("RequestId")
        public String requestId;
    }
}