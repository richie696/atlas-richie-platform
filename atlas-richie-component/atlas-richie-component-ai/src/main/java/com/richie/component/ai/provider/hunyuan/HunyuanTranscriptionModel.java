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
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.Resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 腾讯混元（Hunyuan）语音识别（STT / Transcription）模型适配器，实现 Spring AI 标准 {@link TranscriptionModel}。
 * <p>
 * 调用腾讯云 ASR 一句话识别（SentenceRecognition）接口
 * {@code POST https://asr.tencentcloudapi.com}，
 * 请求体形态：
 * <pre>{@code
 * { "EngineModelType": "16k_zh", "ChannelNum": 1,
 *   "ResTextFormat": 0, "SourceType": 1,
 *   "Data": "<base64>", "DataType": 1 }
 * }</pre>
 * 响应体形态：
 * <pre>{@code
 * { "Response": { "Result": "<text>", "RequestId": "..." } }
 * }</pre>
 * 鉴权使用 TC3-HMAC-SHA256 签名，由 {@link Tc3Signer} 计算。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class HunyuanTranscriptionModel implements TranscriptionModel {

    /** TC3 服务名（ASR 接口固定为 {@code asr}）。 */
    public static final String TC3_SERVICE = "asr";

    /** TC3 接口 action 名（一句话识别）。 */
    public static final String TC3_ACTION = "SentenceRecognition";

    /** TC3 接口版本号。 */
    public static final String TC3_VERSION = "2019-08-23";

    /**
     * TC3 签名 canonical header 中的 Content-Type（{@link Tc3Signer} 把它写死在签名中），
     * 因此实际请求头必须保持完全一致 —— 否则服务端会拒绝该请求。
     */
    public static final String CONTENT_TYPE = "application/json; charset=utf-8";

    /** 默认引擎模型：16k 普通话。 */
    public static final String DEFAULT_ENGINE_MODEL_TYPE = "16k_zh";

    /** 单声道。 */
    public static final int DEFAULT_CHANNEL_NUM = 1;

    /** 基础文本结果（不返回词级时间戳）。 */
    public static final int DEFAULT_RES_TEXT_FORMAT = 0;

    /** 音频数据来源：1 = base64 编码音频。 */
    public static final int DEFAULT_SOURCE_TYPE = 1;

    /** 音频数据类型：1 = 原始 PCM / WAV 等裸流，由腾讯云 ASR 后端按 ResTextFormat 解析。 */
    public static final int DEFAULT_DATA_TYPE = 1;

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;
    private final Tc3Signer tc3Signer;

    public HunyuanTranscriptionModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this(cfg, httpClient, new Tc3Signer());
    }

    /**
     * 包内构造器，允许测试或 Spring 容器注入共享的 {@link Tc3Signer} 单例。
     */
    HunyuanTranscriptionModel(AbstractAudioModelConfig cfg, HttpClient httpClient, Tc3Signer tc3Signer) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.tc3Signer = Objects.requireNonNull(tc3Signer, "tc3Signer must not be null");
    }

    /**
     * 同步入口：内部走 {@link HttpClient#future} 通道，便于上层切换为异步时直接复用同一调用栈。
     */
    @Override
    public AudioTranscriptionResponse call(AudioTranscriptionPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        try {
            return callAsync(prompt).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Hunyuan STT interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Hunyuan STT failed: " + e.getMessage(), e);
        }
    }

    /**
     * 异步入口：以 {@link CompletableFuture} 形式返回，便于上游做并发编排。
     * 非 Spring AI 接口方法，仅做便捷暴露。
     */
    public CompletableFuture<AudioTranscriptionResponse> callAsync(AudioTranscriptionPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Resource resource = prompt.getInstructions();
        if (resource == null || !resource.exists()) {
            throw new IllegalArgumentException("AudioTranscriptionPrompt resource must exist");
        }
        byte[] audioBytes = readAllBytes(resource);

        Map<String, Object> body = buildRequestBody(audioBytes);
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
                .future(SttRawResponse.class)
                .thenApply(HunyuanTranscriptionModel::toAudioTranscriptionResponse);
    }

    /**
     * 把 {@link AbstractAudioModelConfig#getEndpoint()} 规范化为 {@code http(s)://host} 形式。
     * 与签名无关 —— {@link Tc3Signer#buildAuthorization} 内部独立解析 host。
     */
    private static String normalizeUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("AbstractAudioModelConfig.endpoint must not be blank for Hunyuan STT");
        }
        String e = endpoint.trim();
        if (e.startsWith("http://") || e.startsWith("https://")) {
            return e;
        }
        return "https://" + e;
    }

    /**
     * 把音频字节与配置装配为混元 STT 请求体。
     * <p>
     * 字段名遵循腾讯云 ASR SentenceRecognition 约定：
     * <ul>
     *   <li>{@code EngineModelType}：来自 {@link AbstractAudioModelConfig#getModel()}；空时回落到 {@code 16k_zh}</li>
     *   <li>{@code ChannelNum}：固定 1（单声道）</li>
     *   <li>{@code ResTextFormat}：固定 0（基础文本）</li>
     *   <li>{@code SourceType}：固定 1（base64 数据）</li>
     *   <li>{@code Data}：音频字节的 Base64 编码</li>
     *   <li>{@code DataType}：固定 1（裸 PCM / WAV 等）</li>
     * </ul>
     */
    private Map<String, Object> buildRequestBody(byte[] audioBytes) {
        Map<String, Object> body = new HashMap<>();
        body.put("EngineModelType", resolveEngineModelType(cfg.getModel()));
        body.put("ChannelNum", DEFAULT_CHANNEL_NUM);
        body.put("ResTextFormat", DEFAULT_RES_TEXT_FORMAT);
        body.put("SourceType", DEFAULT_SOURCE_TYPE);
        body.put("Data", Base64.getEncoder().encodeToString(audioBytes));
        body.put("DataType", DEFAULT_DATA_TYPE);
        return body;
    }

    /**
     * 解析 {@link AbstractAudioModelConfig#getModel()} 字符串为 ASR 引擎模型。
     * 支持 "16k_zh" 直接给模型名，或 "engine:16k_zh" 风格；其他情形回落到默认。
     */
    private static String resolveEngineModelType(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_ENGINE_MODEL_TYPE;
        }
        String trimmed = model.startsWith("engine:") ? model.substring("engine:".length()) : model;
        String s = trimmed.trim();
        return s.isEmpty() ? DEFAULT_ENGINE_MODEL_TYPE : s;
    }

    /**
     * 把 Spring {@link Resource} 的全部内容读入内存字节数组。
     * ASR 输入通常较小（< 5MB），一次性读入便于后续 Base64 与签名。
     */
    private static byte[] readAllBytes(Resource resource) {
        try (InputStream in = resource.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Hunyuan STT 读取音频资源失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把混元 STT 响应装配为 Spring AI {@link AudioTranscriptionResponse}。
     * 缺失 Result 字段时回落到空字符串 —— 上层可通过 {@code getResult().getOutput().isEmpty()} 自行判断。
     */
    static AudioTranscriptionResponse toAudioTranscriptionResponse(SttRawResponse raw) {
        if (raw == null || raw.response == null || raw.response.result == null) {
            log.warn("Hunyuan STT returned empty body");
            return new AudioTranscriptionResponse(new AudioTranscription(""));
        }
        return new AudioTranscriptionResponse(new AudioTranscription(raw.response.result));
    }

    // ====== Hunyuan response DTOs ======

    /** 顶层响应：{@code { "Response": { "Result": "...", "RequestId": "..." } }}。 */
    static class SttRawResponse {
        @JsonProperty("Response")
        public SttRawInner response;
    }

    /** {@code Response} 子对象。 */
    static class SttRawInner {
        @JsonProperty("Result")
        public String result;

        @JsonProperty("RequestId")
        public String requestId;
    }
}