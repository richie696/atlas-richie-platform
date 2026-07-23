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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.http.core.HttpClient;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.spring.SpringBeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 智谱 AI（BigModel）语音识别（STT / Transcription）模型适配器，实现 Spring AI 标准
 * {@link TranscriptionModel}。
 * <p>
 * 调用智谱 BigModel 开放接口
 * {@code POST https://open.bigmodel.cn/api/paas/v4/audio/transcriptions}，Bearer 鉴权，
 * 官方协议为 {@code multipart/form-data}（字段 {@code file} 音频 + {@code model} 模型）。
 * <p>
 * <strong>实现选择（JSON base64 fallback）</strong>：当前平台 {@code atlas-richie-component-http-core}
 * 的 {@link com.richie.component.http.core.HttpRequest#multipart(String, String, InputStream)}
 * 仅支持<b>单文件字段</b>，无法同时投递 {@code file} 音频 + {@code model} 字符串字段；
 * 因此本实现采用 <b>JSON base64 fallback</b> —— 把音频 base64 编码后随 {@code model} 一同放入
 * JSON body：
 * <pre>{@code
 * { "model": "glm-asr-2512",
 *   "file":  "<base64 audio>" }
 * }</pre>
 * 响应仍为标准 JSON {@code {"text": "<transcript>"}}。此方案可能不与智谱官方完全兼容，
 * 真实集成测试时若智谱拒绝此格式，需切换到原 multipart 协议（需要扩展 {@code HttpRequest}
 * 支持多字段 multipart 或绕开 http-core 直接拼装请求）。
 * <p>
 * {@link TranscriptionModel} 不需要实现流式接口（与 TTS 不同），因此本类无 {@code Reactor}
 * 依赖。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class ZhipuTranscriptionModel implements TranscriptionModel {

    /** 智谱 STT REST 端点。 */
    public static final String DEFAULT_STT_URL =
            "https://open.bigmodel.cn/api/paas/v4/audio/transcriptions";

    /** 当未指定模型时使用的默认模型（{@code glm-asr-2512}）。 */
    public static final String DEFAULT_MODEL = "glm-asr-2512";

    /** 单次调用允许的最大音频字节数（10 MB）—— 智谱 ASR 限制，防止 OOM。 */
    public static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;

    /**
     * 容器入口构造器（{@code com.richie.component.ai.config.AiModelAutoConfiguration#aiZhipuSttModel}
     * 使用此签名）。HttpClient 通过 {@link SpringBeanUtils} 懒加载解析，避免对该 Bean 形成强制运行时依赖。
     */
    public ZhipuTranscriptionModel(AbstractAudioModelConfig cfg) {
        this(cfg, null);
    }

    /**
     * 包内构造器：允许测试或特殊场景显式注入 {@link HttpClient}。
     * 传入 {@code null} 时回落到 {@link SpringBeanUtils} 懒加载模式（生产路径）。
     *
     * @param cfg        语音模型配置（{@link AbstractAudioModelConfig#getApiKey()} 提供 Bearer apiKey，
     *                   {@link AbstractAudioModelConfig#getModel()} 提供默认模型）
     * @param httpClient HTTP 客户端；为 {@code null} 时走 Spring 懒加载
     */
    ZhipuTranscriptionModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = httpClient;
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
            throw new RuntimeException("Zhipu STT interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Zhipu STT failed: " + e.getMessage(), e);
        }
    }

    /**
     * 异步入口：以 {@link CompletableFuture} 形式返回，便于上游做并发编排。
     * 非 Spring AI 接口方法，仅做便捷暴露。
     */
    public CompletableFuture<AudioTranscriptionResponse> callAsync(AudioTranscriptionPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Resource audio = prompt.getInstructions();
        if (audio == null) {
            throw new IllegalArgumentException("AudioTranscriptionPrompt instructions (Resource) must not be null");
        }
        byte[] audioBytes = readAudioBytes(audio);
        if (audioBytes.length == 0) {
            throw new IllegalArgumentException("AudioTranscriptionPrompt audio resource is empty");
        }
        if (audioBytes.length > MAX_AUDIO_BYTES) {
            throw new IllegalArgumentException(
                    "Audio exceeds Zhipu STT limit: " + audioBytes.length + " > " + MAX_AUDIO_BYTES);
        }

        Map<String, Object> body = buildRequestBody(audioBytes);
        HttpClient client = resolveHttpClient();

        return client.post(resolveEndpoint(), body)
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .header("Content-Type", "application/json")
                .future(SttRawResponse.class)
                .thenApply(ZhipuTranscriptionModel::toAudioTranscriptionResponse);
    }

    /**
     * 把音频字节与配置装配为智谱 STT 请求体（JSON base64 fallback）。
     * <p>
     * 字段命名遵循智谱 BigModel 约定：
     * <ul>
     *   <li>{@code model}：来自 {@link AbstractAudioModelConfig#getModel()}，为空时回落到
     *       {@link #DEFAULT_MODEL}</li>
     *   <li>{@code file}：音频字节的 base64 编码字符串（智谱 STT 官方 multipart 协议下的
     *       {@code file} 字段本意为二进制文件；此处按 base64 JSON fallback 提交）</li>
     * </ul>
     */
    Map<String, Object> buildRequestBody(byte[] audioBytes) {
        String model = (cfg.getModel() == null || cfg.getModel().isBlank()) ? DEFAULT_MODEL : cfg.getModel();
        String fileBase64 = Base64.getEncoder().encodeToString(audioBytes);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("file", fileBase64);
        return body;
    }

    /**
     * 解析 STT 端点 URL：{@link AbstractAudioModelConfig#getBaseUrl()} 非空时使用配置值，否则回落到
     * {@link #DEFAULT_STT_URL}。
     */
    String resolveEndpoint() {
        String url = cfg.getBaseUrl();
        return (url == null || url.isBlank()) ? DEFAULT_STT_URL : url;
    }

    /**
     * 读取 Spring {@link Resource} 的全部字节。Stream 用 try-with-resources 关闭。
     */
    private static byte[] readAudioBytes(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read audio resource: " + e.getMessage(), e);
        }
    }

    /**
     * 把智谱 STT 响应装配为 Spring AI {@link AudioTranscriptionResponse}。
     * <p>
     * 响应缺失或 {@code text} 字段为空时回落到空字符串 —— 上层可通过
     * {@code getResult().getOutput().isEmpty()} 自行判断失败。
     * <p>
     * 该方法设计为 {@code static}，便于单元测试在不实例化完整模型的前提下验证响应映射。
     */
    static AudioTranscriptionResponse toAudioTranscriptionResponse(SttRawResponse raw) {
        if (raw == null || raw.text == null) {
            log.warn("Zhipu STT returned empty body: {}", JsonUtils.getInstance().serialize(raw));
            return new AudioTranscriptionResponse(new AudioTranscription(""));
        }
        return new AudioTranscriptionResponse(new AudioTranscription(raw.text));
    }

    /**
     * 解析 HttpClient：构造时已注入则直接返回；否则通过 {@link SpringBeanUtils} 懒加载。
     */
    private HttpClient resolveHttpClient() {
        if (httpClient != null) {
            return httpClient;
        }
        HttpClient resolved = SpringBeanUtils.getBean(HttpClient.class);
        if (resolved == null) {
            throw new IllegalStateException(
                    "HttpClient bean not found; please import one of atlas-richie-component-http-{okhttp|httpclient5|jdk|restclient}");
        }
        return resolved;
    }

    // ====== 智谱 STT response DTOs ======

    /**
     * 智谱 STT 顶层响应：{@code { "text": "<transcript>" }}。
     * <p>
     * 使用 public 字段以便 Jackson 在没有 setter 的情况下也能反序列化；
     * {@link JsonProperty} 显式声明是因为虽然字段名与 JSON 一致，但保留注解便于后续扩展。
     */
    static class SttRawResponse {
        @JsonProperty("text")
        public String text;
    }
}