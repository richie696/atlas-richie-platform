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
package com.richie.component.ai.provider.doubao;

import com.richie.component.ai.provider.support.JsonSafe;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import com.richie.context.utils.data.JsonUtils;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 字节跳动豆包（火山引擎 OpenSpeech）语音识别（STT / 一句话识别 flash 模式）模型适配器，
 * 实现 Spring AI 标准 {@link TranscriptionModel}。
 * <p>
 * 调用豆包 OpenSpeech 一句话识别 flash 接口
 * {@code POST https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash}，
 * 请求体形态（NDJSON / 单行 JSON 两种调用形态均被服务端接受）：
 * <pre>{@code
 * {
 *   "app":    { "appid": "<appId>", "token": "<apiKey>", "cluster": "<resourceId>" },
 *   "user":   { "uid": "doubao-stt" },
 *   "audio":  { "format": "wav", "data": "<base64 audio>" },
 *   "request":{ "reqid": "<uuid>" }
 * }
 * }</pre>
 * 响应体形态（单行 NDJSON，识别成功时携带 {@code result} 文本）：
 * <pre>{@code
 * {"code":1000,"result":"你好世界","message":"success"}
 * }</pre>
 * 鉴权使用豆包 OpenSpeech 自定义头：
 * <ul>
 *   <li>{@code X-Api-Key} —— {@code AbstractAudioModelConfig#getApiKey()}</li>
 *   <li>{@code X-Api-Resource-Id} —— 固定为 {@link #STT_RESOURCE_ID}（一句话识别 flash）</li>
 *   <li>{@code X-Api-App-Id} —— {@code AbstractAudioModelConfig#getAppId()}</li>
 * </ul>
 * <p>
 * NDJSON 解析：按行拆分，跳过空行；取首个携带 {@code result} 字段的行作为识别结果；
 * 遇到非 0 / 非 1000 的 code 抛出运行时异常。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class DoubaoTranscriptionModel implements TranscriptionModel {

    /** 豆包 OpenSpeech 一句话识别 flash REST 端点。 */
    public static final String DEFAULT_STT_URL =
            "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash";

    /** 豆包 OpenSpeech STT resourceId（flash 一句话识别专用）。 */
    public static final String STT_RESOURCE_ID = "volc.bigasr.auc_turbo";

    /** 默认音频格式（wav / pcm / mp3 等；当前适配 flash 接口固定 wav）。 */
    public static final String DEFAULT_AUDIO_FORMAT = "wav";

    /** 业务侧 {@code user.uid} 字段占位（豆包仅用于审计）。 */
    public static final String DEFAULT_UID = "doubao-stt";

    /** 豆包 STT 成功返回码。 */
    public static final int CODE_SUCCESS = 1000;

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;
    private final String sttUrl;

    public DoubaoTranscriptionModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this(cfg, httpClient, DEFAULT_STT_URL);
    }

    /**
     * 暴露包内构造器，便于测试或区域定制端点注入。
     *
     * @param cfg        语音模型配置（提供 apiKey / appId；model 不被 STT 使用）
     * @param httpClient HTTP 客户端
     * @param sttUrl     STT 接口地址（为空时回落到 {@link #DEFAULT_STT_URL}）
     */
    DoubaoTranscriptionModel(AbstractAudioModelConfig cfg, HttpClient httpClient, String sttUrl) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.sttUrl = (sttUrl == null || sttUrl.isBlank()) ? DEFAULT_STT_URL : sttUrl;
        validateConfig(cfg);
    }

    /**
     * 同步入口：内部走 {@link HttpClient#execute} 通道，便于上层切换为异步时直接复用同一调用栈。
     */
    @Nonnull
    @Override
    public AudioTranscriptionResponse call(@Nonnull AudioTranscriptionPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        try {
            return callAsync(prompt).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Doubao STT interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Doubao STT failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * 异步入口：以 {@link CompletableFuture} 形式返回，便于上游做并发编排。
     * <p>
     * 非 Spring AI 接口方法，仅做便捷暴露。内部使用
     * {@link CompletableFuture#supplyAsync(java.util.function.Supplier)} 把同步
     * {@link HttpClient#execute} 卸载到公共 ForkJoinPool。
     * <p>
     * <strong>为何不直接用 {@link HttpClient#future}：</strong>豆包返回 NDJSON（非单个 JSON
     * 对象），由 Jackson 反序列化为 POJO 会失败；本实现拿到原始响应体后手动拆分。
     */
    public CompletableFuture<AudioTranscriptionResponse> callAsync(AudioTranscriptionPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        Resource audio = prompt.getInstructions();
        if (!audio.isReadable()) {
            throw new IllegalArgumentException(
                    "AudioTranscriptionPrompt audioResource must be readable");
        }

        // 1) 读取音频字节并 base64 编码
        byte[] audioBytes = readAllBytes(audio);
        String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);

        // 2) 构造请求体（NDJSON 单行 / 多行调用皆可；本适配器走单行）
        Map<String, Object> body = buildRequestBody(audioBase64);

        // 3) 调用豆包 flash 接口；resourceId 对 STT 固定为 volc.bigasr.auc_turbo
        return CompletableFuture.supplyAsync(() -> {
            HttpResponse resp = httpClient.post(sttUrl, body)
                    .header("X-Api-Key", cfg.getApiKey())
                    .header("X-Api-Resource-Id", STT_RESOURCE_ID)
                    .header("X-Api-App-Id", cfg.getAppId() == null ? "" : cfg.getAppId())
                    .header("Content-Type", "application/json")
                    .execute();
            if (!resp.isSuccessful()) {
                throw new RuntimeException(
                        "Doubao STT HTTP error: status=" + resp.statusCode()
                                + " body=" + resp.bodyAsString());
            }
            return parseSttResponse(resp.bodyAsString());
        });
    }

    /**
     * 安全地读取 {@link Resource} 全部字节。
     * <p>
     * 优先使用 {@link Resource#getContentAsByteArray()}（Spring 默认实现从
     * {@link Resource#getInputStream()} 流式读取直到 EOF）。若读取失败则转为
     * {@link RuntimeException}，不静默吞 IO 异常。
     */
    private static byte[] readAllBytes(Resource audio) {
        try {
            return audio.getContentAsByteArray();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Doubao STT failed to read audio resource: " + audio.getDescription(), e);
        }
    }

    /**
     * 把 base64 后的音频与配置装配为豆包 OpenSpeech STT 请求体。
     * <p>
     * 字段命名遵循豆包官方 API v3 约定：
     * <ul>
     *   <li>{@code app.appid} —— {@code AbstractAudioModelConfig#getAppId()}</li>
     *   <li>{@code app.token} —— {@code AbstractAudioModelConfig#getApiKey()}</li>
     *   <li>{@code app.cluster} —— {@link AbstractAudioModelConfig#getResourceId()}</li>
     *   <li>{@code user.uid} —— 固定 {@value #DEFAULT_UID}</li>
     *   <li>{@code audio.format} —— 固定 {@value #DEFAULT_AUDIO_FORMAT}</li>
     *   <li>{@code audio.data} —— base64 编码后的音频字节</li>
     *   <li>{@code request.reqid} —— 每请求唯一 UUID，便于日志链路关联</li>
     * </ul>
     * 注意：{@link AbstractAudioModelConfig#getModel()} 在 STT flash 接口不被消费（服务端通过
     * resourceId {@value #STT_RESOURCE_ID} 路由识别模型），此处忽略。
     */
    private Map<String, Object> buildRequestBody(String audioBase64) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("appid", cfg.getAppId());
        app.put("token", cfg.getApiKey());
        app.put("cluster", cfg.getResourceId());

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", DEFAULT_UID);

        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("format", DEFAULT_AUDIO_FORMAT);
        audio.put("data", audioBase64);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reqid", UUID.randomUUID().toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", app);
        body.put("user", user);
        body.put("audio", audio);
        body.put("request", request);
        return body;
    }

    /**
     * 解析豆包 STT NDJSON 响应为 Spring AI {@link AudioTranscriptionResponse}。
     * <p>
     * 算法：
     * <ol>
     *   <li>按行拆分（{@code \\R} 兼容 \n / \r\n）</li>
     *   <li>跳过空行；其余每行调用 Jackson 解析为 {@link SttRawChunk}</li>
     *   <li>取首个携带 {@code result} 字段的行（{@code code==1000}）作为识别文本</li>
     *   <li>遇到其它非零 code，抛 {@link RuntimeException}（携带 message 字段）</li>
     * </ol>
     * 缺失 {@code result} 时回落到空字符串 —— 上层可通过
     * {@code getResult().getOutput().isEmpty()} 自行判断。
     *
     * @param ndjson 响应原始字符串（{@code null} 或空白按空文本返回）
     * @return 包含识别文本的 {@link AudioTranscriptionResponse}
     */
    static AudioTranscriptionResponse parseSttResponse(String ndjson) {
        if (ndjson == null || ndjson.isBlank()) {
            log.warn("Doubao STT returned empty body");
            return new AudioTranscriptionResponse(new AudioTranscription(""));
        }

        String transcript = null;
        for (String line : ndjson.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            SttRawChunk chunk = SttRawChunk.fromJson(trimmed);
            if (chunk == null) {
                log.warn("Doubao STT skip non-JSON line: {}", trimmed);
                continue;
            }

            int code = chunk.code == null ? 0 : chunk.code;
            if (code == CODE_SUCCESS && chunk.result != null) {
                transcript = chunk.result;
                // 取到首个成功 chunk 即可终止；NDJSON 出现多个 chunk 时首条即最终结果
                break;
            } else if (code != 0 && code != CODE_SUCCESS) {
                throw new RuntimeException(
                        "Doubao STT error: code=" + code + " message=" + chunk.message);
            }
        }

        if (transcript == null) {
            log.warn("Doubao STT response contains no transcript text");
            transcript = "";
        }
        return new AudioTranscriptionResponse(new AudioTranscription(transcript));
    }

    /**
     * 豆包 STT NDJSON 单行 chunk 结构：{@code {"code":..,"result":"..","message":".."}}。
     * <p>
     * 字段采用 public 形式以便 Jackson 在没有 setter 的情况下也能反序列化；
     * {@link JsonProperty} 显式声明以兼容多种 JSON 字段命名风格。
     */
    static class SttRawChunk {
        @JsonProperty("code")
        public Integer code;

        @JsonProperty("result")
        public String result;

        @JsonProperty("message")
        public String message;

        /**
         * 反序列化单行 NDJSON 为 {@link SttRawChunk}。
         * <p>
         * 反序列化失败返回 {@code null}（而非抛错），由调用方决定是否跳过该行。
         */
        static SttRawChunk fromJson(String line) {
            try {
                return JsonSafe.parseObject(line, SttRawChunk.class);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * 启动期快速校验：豆包 STT 至少需要 apiKey / appId / resourceId 三项才能成功鉴权。
     */
    private static void validateConfig(AbstractAudioModelConfig cfg) {
        requireNonBlank(cfg.getApiKey(), "AbstractAudioModelConfig.apiKey");
        requireNonBlank(cfg.getAppId(), "AbstractAudioModelConfig.appId");
        requireNonBlank(cfg.getResourceId(), "AbstractAudioModelConfig.resourceId");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required for Doubao STT");
        }
    }
}