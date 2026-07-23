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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechMessage;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 字节跳动豆包（火山引擎 OpenSpeech）语音合成（TTS）模型适配器，实现 Spring AI 标准 {@link TextToSpeechModel}。
 * <p>
 * 调用豆包 OpenSpeech 单向流式 TTS REST 接口
 * {@code POST https://openspeech.bytedance.com/api/v3/tts/unidirectional}，
 * 请求体形态（NDJSON / 单行 JSON 两种调用形态均被服务端接受）：
 * <pre>{@code
 * {
 *   "app":    { "appid": "<appId>", "token": "<apiKey>", "cluster": "<resourceId>" },
 *   "user":   { "uid": "doubao-tts" },
 *   "audio":  { "voice_type": "<model>", "encoding": "mp3" },
 *   "request":{ "reqid": "<uuid>", "text": "<text>", "operation": "query" }
 * }
 * }</pre>
 * 响应体形态（NDJSON 多 chunk 拼接，单次 query 也可能只返回一行）：
 * <pre>{@code
 * {"code":3000,"data":"<base64 chunk 1>","message":"success"}
 * {"code":3000,"data":"<base64 chunk 2>","message":"success"}
 * ...}
 * }</pre>
 * 鉴权使用豆包 OpenSpeech 自定义头：
 * <ul>
 *   <li>{@code X-Api-Key} —— {@code AbstractAudioModelConfig#getApiKey()}</li>
 *   <li>{@code X-Api-Resource-Id} —— {@code AbstractAudioModelConfig#getResourceId()}，
 *       典型值 {@code volc.tts.api.voiceclone} / {@code seed-tts-2.0} / {@code seed-icl-2.0}</li>
 *   <li>{@code X-Api-App-Id} —— {@code AbstractAudioModelConfig#getAppId()}</li>
 * </ul>
 * <p>
 * NDJSON 解析：每个非空行作为独立 JSON 对象解析；{@code code==3000} 的 {@code data} 字段
 * （base64 音频）按出现顺序拼接后统一解码；遇到非 0 / 非 3000 的 code 抛出运行时异常。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class DoubaoTextToSpeechModel implements TextToSpeechModel {

    /** 豆包 OpenSpeech 单向流式 TTS REST 端点。 */
    public static final String DEFAULT_TTS_URL =
            "https://openspeech.bytedance.com/api/v3/tts/unidirectional";

    /** 默认音频编码格式。 */
    public static final String DEFAULT_AUDIO_ENCODING = "mp3";

    /** 业务侧 {@code user.uid} 字段占位（豆包仅用于审计，可固定）。 */
    public static final String DEFAULT_UID = "doubao-tts";

    /** 业务侧 {@code request.operation} 固定为 {@code query}（同步语义）。 */
    public static final String DEFAULT_OPERATION = "query";

    /** 豆包 TTS 成功返回码。 */
    public static final int CODE_SUCCESS = 3000;

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;
    private final String ttsUrl;

    public DoubaoTextToSpeechModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this(cfg, httpClient, DEFAULT_TTS_URL);
    }

    /**
     * 暴露包内构造器，便于测试或区域定制端点注入。
     *
     * @param cfg        语音模型配置（提供 apiKey / appId / resourceId / model）
     * @param httpClient HTTP 客户端
     * @param ttsUrl     TTS 接口地址（为空时回落到 {@link #DEFAULT_TTS_URL}）
     */
    DoubaoTextToSpeechModel(AbstractAudioModelConfig cfg, HttpClient httpClient, String ttsUrl) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.ttsUrl = (ttsUrl == null || ttsUrl.isBlank()) ? DEFAULT_TTS_URL : ttsUrl;
        validateConfig(cfg);
    }

    /**
     * 同步入口：内部走 {@link HttpClient#execute} 通道，便于上层切换为异步时直接复用同一调用栈。
     */
    @Override
    public TextToSpeechResponse call(TextToSpeechPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        try {
            return callAsync(prompt).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Doubao TTS interrupted", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("Doubao TTS failed: " + cause.getMessage(), cause);
        }
    }

    /**
     * 异步入口：以 {@link CompletableFuture} 形式返回，便于上游做并发编排。
     * <p>
     * 非 Spring AI 接口方法，仅做便捷暴露。内部使用
     * {@link CompletableFuture#supplyAsync(java.util.function.Supplier)} 把同步
     * {@link HttpClient#execute} 卸载到公共 ForkJoinPool，避免阻塞业务线程。
     * <p>
     * <strong>为何不直接用 {@link HttpClient#future}：</strong>豆包返回 NDJSON（非单个 JSON
     * 对象），若通过 {@code .future(String.class)} 或 {@code .future(byte[].class)} 让
     * 底层走 Jackson 反序列化，会因 NDJSON 不是合法 JSON 而失败。直接拿 {@link HttpResponse}
     * 字节体手动拆分行是最稳健的做法。
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

        Map<String, Object> body = buildRequestBody(text);
        return CompletableFuture.supplyAsync(() -> {
            HttpResponse resp = httpClient.post(ttsUrl, body)
                    .header("X-Api-Key", cfg.getApiKey())
                    .header("X-Api-Resource-Id", cfg.getResourceId() == null ? "" : cfg.getResourceId())
                    .header("X-Api-App-Id", cfg.getAppId() == null ? "" : cfg.getAppId())
                    .header("Content-Type", "application/json")
                    .execute();
            if (!resp.isSuccessful()) {
                throw new RuntimeException(
                        "Doubao TTS HTTP error: status=" + resp.statusCode()
                                + " body=" + resp.bodyAsString());
            }
            return parseTtsResponse(resp.bodyAsString());
        });
    }

    /**
     * Spring AI 流式接口：豆包 TTS 单次调用即可获得完整音频字节，本实现把同步结果作为单元素 Flux 输出。
     * <p>
     * 注意：{@link TextToSpeechModel} 继承自 {@link org.springframework.ai.audio.tts.StreamingTextToSpeechModel}，
     * 后者的 {@code stream(TextToSpeechPrompt)} 是抽象方法，必须提供实现。这里用最小代价
     * （{@code Flux.just(call(prompt))}）满足接口契约，业务侧仍可通过 {@code call} / {@code callAsync}
     * 走完整编码。
     */
    @Override
    public Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt) {
        return Flux.just(call(prompt));
    }

    /**
     * 把文本与配置装配为豆包 OpenSpeech TTS 请求体。
     * <p>
     * 字段命名遵循豆包官方 API v3 约定：
     * <ul>
     *   <li>{@code app.appid} —— {@code AbstractAudioModelConfig#getAppId()}</li>
     *   <li>{@code app.token} —— {@code AbstractAudioModelConfig#getApiKey()}</li>
     *   <li>{@code app.cluster} —— {@code AbstractAudioModelConfig#getResourceId()}（豆包 cluster 与 resourceId 一致）</li>
     *   <li>{@code user.uid} —— 固定 {@value #DEFAULT_UID}</li>
     *   <li>{@code audio.voice_type} —— {@link AbstractAudioModelConfig#getModel()}</li>
     *   <li>{@code audio.encoding} —— 固定 {@value #DEFAULT_AUDIO_ENCODING}</li>
     *   <li>{@code request.reqid} —— 每请求唯一 UUID，便于日志链路关联</li>
     *   <li>{@code request.text} —— 待合成文本</li>
     *   <li>{@code request.operation} —— 固定 {@value #DEFAULT_OPERATION}</li>
     * </ul>
     */
    private Map<String, Object> buildRequestBody(String text) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("appid", cfg.getAppId());
        app.put("token", cfg.getApiKey());
        app.put("cluster", cfg.getResourceId());

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("uid", DEFAULT_UID);

        Map<String, Object> audio = new LinkedHashMap<>();
        audio.put("voice_type", cfg.getModel());
        audio.put("encoding", DEFAULT_AUDIO_ENCODING);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reqid", UUID.randomUUID().toString());
        request.put("text", text);
        request.put("operation", DEFAULT_OPERATION);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", app);
        body.put("user", user);
        body.put("audio", audio);
        body.put("request", request);
        return body;
    }

    /**
     * 解析豆包 TTS NDJSON 响应为 Spring AI {@link TextToSpeechResponse}。
     * <p>
     * 算法：
     * <ol>
     *   <li>按行拆分（{@code \\R} 兼容 \n / \r\n）</li>
     *   <li>跳过空行；其余每行调用 Jackson 解析为 {@link TtsRawChunk}</li>
     *   <li>对于 {@code code==3000} 的 chunk，拼接 {@code data} 字段（base64 字符串）</li>
     *   <li>遇到其它非零 code，抛 {@link RuntimeException}（携带 message 字段）</li>
     *   <li>所有 chunk 解析完成后，统一 Base64 解码得到完整音频字节</li>
     * </ol>
     * 缺失 {@code data} 时回落到空字节 —— 上层可通过
     * {@code getResult().getOutput().length == 0} 自行判断。
     *
     * @param ndjson 响应原始字符串（{@code null} 或空白按空字节返回）
     * @return 包含拼接后音频的 {@link TextToSpeechResponse}
     */
    static TextToSpeechResponse parseTtsResponse(String ndjson) {
        if (ndjson == null || ndjson.isBlank()) {
            log.warn("Doubao TTS returned empty body");
            return new TextToSpeechResponse(Collections.singletonList(new Speech(new byte[0])));
        }

        StringBuilder combined = new StringBuilder();
        for (String line : ndjson.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            TtsRawChunk chunk = TtsRawChunk.fromJson(trimmed);
            if (chunk == null) {
                // 单行解析失败不中断整体流程：可能是服务端心跳行（如 ": keep-alive"），
                // 记录 warn 后继续。
                log.warn("Doubao TTS skip non-JSON line: {}", trimmed);
                continue;
            }

            int code = chunk.code == null ? 0 : chunk.code;
            if (code == CODE_SUCCESS && chunk.data != null) {
                combined.append(chunk.data);
            } else if (code != 0 && code != CODE_SUCCESS) {
                throw new RuntimeException(
                        "Doubao TTS error: code=" + code + " message=" + chunk.message);
            }
        }

        if (combined.length() == 0) {
            log.warn("Doubao TTS response contains no audio data");
            return new TextToSpeechResponse(Collections.singletonList(new Speech(new byte[0])));
        }
        byte[] audio;
        try {
            audio = Base64.getDecoder().decode(combined.toString());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Doubao TTS data is not valid Base64: " + e.getMessage(), e);
        }
        return new TextToSpeechResponse(Collections.singletonList(new Speech(audio)));
    }

    /**
     * 豆包 TTS NDJSON 单行 chunk 结构：{@code {"code":..,"data":"..","message":".."}}。
     * <p>
     * 字段采用 public 形式以便 Jackson 在没有 setter 的情况下也能反序列化；
     * {@link JsonProperty} 显式声明以兼容多种 JSON 字段命名风格。
     */
    static class TtsRawChunk {
        @JsonProperty("code")
        public Integer code;

        @JsonProperty("data")
        public String data;

        @JsonProperty("message")
        public String message;

        /**
         * 反序列化单行 NDJSON 为 {@link TtsRawChunk}。
         * <p>
         * 反序列化失败返回 {@code null}（而非抛错），由调用方决定是否跳过该行。
         */
        static TtsRawChunk fromJson(String line) {
            try {
                return com.richie.context.utils.data.JsonUtils.getInstance()
                        .deserialize(line, TtsRawChunk.class);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * 启动期快速校验：豆包 TTS 至少需要 apiKey / appId / resourceId / model 四项才能成功鉴权。
     */
    private static void validateConfig(AbstractAudioModelConfig cfg) {
        requireNonBlank(cfg.getApiKey(), "AbstractAudioModelConfig.apiKey");
        requireNonBlank(cfg.getAppId(), "AbstractAudioModelConfig.appId");
        requireNonBlank(cfg.getResourceId(), "AbstractAudioModelConfig.resourceId");
        requireNonBlank(cfg.getModel(), "AbstractAudioModelConfig.model (voice_type)");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required for Doubao TTS");
        }
    }
}