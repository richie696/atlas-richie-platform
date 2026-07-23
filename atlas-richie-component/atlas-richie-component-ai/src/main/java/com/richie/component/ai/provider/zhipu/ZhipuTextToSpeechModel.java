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

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpResponse;
import com.richie.context.utils.spring.SpringBeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.audio.tts.Speech;
import org.springframework.ai.audio.tts.TextToSpeechMessage;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechOptions;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 智谱 AI（BigModel）语音合成（TTS）模型适配器，实现 Spring AI 标准 {@link TextToSpeechModel}。
 * <p>
 * 调用智谱 BigModel 开放接口
 * {@code POST https://open.bigmodel.cn/api/paas/v4/audio/speech}，Bearer 鉴权，
 * 请求体形态：
 * <pre>{@code
 * { "model": "glm-tts",
 *   "input": "<text>",
 *   "voice": "tongtong",
 *   "response_format": "wav" }
 * }</pre>
 * 响应为原始音频字节流（{@code Content-Type: audio/wav}），不属于 JSON，
 * 因此本实现走 {@link HttpResponse#body()} 通道而非 Jackson 反序列化。
 * <p>
 * {@link TextToSpeechModel} 通过父接口 {@link org.springframework.ai.audio.tts.StreamingTextToSpeechModel}
 * 强制要求 {@link #stream(TextToSpeechPrompt)} 方法；本实现把同步结果包成单元素 {@link Flux}，
 * 与 {@code HunyuanTextToSpeechModel} 等同类适配器保持一致。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class ZhipuTextToSpeechModel implements TextToSpeechModel {

    /** 智谱 TTS REST 端点。 */
    public static final String DEFAULT_TTS_URL =
            "https://open.bigmodel.cn/api/paas/v4/audio/speech";

    /** 当未指定模型时使用的默认模型（{@code glm-tts}）。 */
    public static final String DEFAULT_MODEL = "glm-tts";

    /** 当未指定音色时使用的默认音色（智谱 GLM-TTS 7 音色之一）。 */
    public static final String DEFAULT_VOICE = "tongtong";

    /** 音频输出格式：wav（与 STT 回路对齐，便于回路播放）。 */
    public static final String RESPONSE_FORMAT = "wav";

    private final AbstractAudioModelConfig cfg;
    private final HttpClient httpClient;

    /**
     * 容器入口构造器（{@code com.richie.component.ai.config.AiModelAutoConfiguration#aiZhipuTtsModel}
     * 使用此签名）。HttpClient 通过 {@link SpringBeanUtils} 在 Spring 容器中懒加载解析，
     * 避免对该 Bean 形成强制运行时依赖 —— 未引入 http provider 的上下文也能编译通过；
     * 运行期若需要 TTS 但 HttpClient 缺失，会在首次调用时 fail-fast。
     */
    public ZhipuTextToSpeechModel(AbstractAudioModelConfig cfg) {
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
    ZhipuTextToSpeechModel(AbstractAudioModelConfig cfg, HttpClient httpClient) {
        this.cfg = Objects.requireNonNull(cfg, "cfg must not be null");
        this.httpClient = httpClient; // may be null — resolve lazily
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
            throw new RuntimeException("Zhipu TTS interrupted", ie);
        } catch (Exception e) {
            throw new RuntimeException("Zhipu TTS failed: " + e.getMessage(), e);
        }
    }

    /**
     * 流式入口：智谱 TTS 当前为一次性返回整段音频，本实现把同步结果包成单元素流。
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
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TextToSpeechPrompt text must not be null or blank");
        }

        Map<String, Object> body = buildRequestBody(prompt, text);
        HttpClient client = resolveHttpClient();

        // raw audio bytes can't go through HttpClient.future(Class) (Jackson would fail to parse);
        // wrap the sync execute() in supplyAsync to keep the callAsync contract.
        return CompletableFuture.supplyAsync(() -> {
            HttpResponse resp = client.post(resolveEndpoint(), body)
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .execute();
            return toSpeechResponse(resp);
        });
    }

    /**
     * 把文本与配置装配为智谱 TTS 请求体。
     * <p>
     * 字段命名遵循智谱 BigModel 官方约定：
     * <ul>
     *   <li>{@code model}：优先取 {@link TextToSpeechOptions#getModel()}，
     *       否则用 {@link AbstractAudioModelConfig#getModel()}，最后回落到 {@link #DEFAULT_MODEL}</li>
     *   <li>{@code input}：待合成文本（智谱把 input 直接做字符串，不嵌套对象）</li>
     *   <li>{@code voice}：优先取 {@link TextToSpeechOptions#getVoice()}，
     *       否则回落到 {@link #DEFAULT_VOICE} —— 智谱 GLM-TTS 固定 7 音色之一</li>
     *   <li>{@code response_format}：固定 {@value #RESPONSE_FORMAT}（与 STT 回路对齐）</li>
     * </ul>
     */
    Map<String, Object> buildRequestBody(TextToSpeechPrompt prompt, String text) {
        String model = DEFAULT_MODEL;
        String voice = DEFAULT_VOICE;
        TextToSpeechOptions options = prompt.getOptions();
        if (options != null) {
            if (options.getModel() != null && !options.getModel().isBlank()) {
                model = options.getModel();
            }
            if (options.getVoice() != null && !options.getVoice().isBlank()) {
                voice = options.getVoice();
            }
        }
        if (cfg.getModel() != null && !cfg.getModel().isBlank() && DEFAULT_MODEL.equals(model)) {
            // 配置提供了默认模型且请求未显式覆盖 → 用配置
            model = cfg.getModel();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", text);
        body.put("voice", voice);
        body.put("response_format", RESPONSE_FORMAT);
        return body;
    }

    /**
     * 解析 TTS 端点 URL：{@link AbstractAudioModelConfig#getBaseUrl()} 非空时使用配置值，否则回落到
     * {@link #DEFAULT_TTS_URL}。
     */
    String resolveEndpoint() {
        String url = cfg.getBaseUrl();
        return (url == null || url.isBlank()) ? DEFAULT_TTS_URL : url;
    }

    /**
     * 把智谱响应的原始字节流装配为 Spring AI {@link TextToSpeechResponse}。
     * <p>
     * 智谱 TTS 响应为 {@code Content-Type: audio/wav} 的二进制流，不是 JSON；
     * 这里直接接收 {@link HttpResponse} 并取 {@code body()}，不走 Jackson。
     * 响应体为空或 HTTP 失败时回落到空字节数组 —— 上层可通过
     * {@code getResult().getOutput().length == 0} 自行判断失败。
     * <p>
     * 该方法设计为 {@code static}，便于单元测试在不实例化完整模型的前提下验证响应映射。
     */
    static TextToSpeechResponse toSpeechResponse(HttpResponse resp) {
        if (resp == null || !resp.isSuccessful()) {
            int status = resp == null ? -1 : resp.statusCode();
            log.warn("Zhipu TTS returned non-2xx response: status={} body={}",
                    status, resp == null ? "<null>" : safeBody(resp));
            return new TextToSpeechResponse(Collections.singletonList(new Speech(new byte[0])));
        }
        byte[] bytes = resp.body();
        if (bytes == null || bytes.length == 0) {
            log.warn("Zhipu TTS returned empty body: status={}", resp.statusCode());
            return new TextToSpeechResponse(Collections.singletonList(new Speech(new byte[0])));
        }
        return new TextToSpeechResponse(Collections.singletonList(new Speech(bytes)));
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

    private static String safeBody(HttpResponse resp) {
        try {
            byte[] body = resp.body();
            if (body == null) {
                return "<empty>";
            }
            // 二进制响应可能不可 UTF-8 解码 —— 截取可读部分
            int max = Math.min(body.length, 256);
            String preview = new String(body, 0, max, java.nio.charset.StandardCharsets.ISO_8859_1);
            return preview.length() > 200 ? preview.substring(0, 200) + "..." : preview;
        } catch (Exception e) {
            return "<unreadable>";
        }
    }
}