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
package com.richie.component.ai.provider.bailian;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.richie.component.http.core.HttpClient;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Spring AI {@link ImageModel} 在阿里百炼（DashScope）平台上的适配器。
 * <p>
 * 把 Spring AI 标准 {@link ImagePrompt} 转换为 DashScope 文生图 REST 调用：
 * <pre>{@code
 * POST https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis
 * {
 *   "model": "wanx-v1",
 *   "input": { "prompt": "..." },
 *   "parameters": { ... }
 * }
 * }</pre>
 * 响应 {@code { "output": { "results": [ { "url": "..." } ] } }} 映射回 Spring AI 的 {@link ImageResponse} / {@link Image}。
 * <p>
 * 实现 {@link ImageModel} 接口：业务侧只依赖 Spring AI 的抽象，可由任意厂商实现替换。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class BailianImageAdapter implements ImageModel {

    /** DashScope 文生图 REST 端点。 */
    public static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";

    /** 当请求未指定模型时使用的默认模型。 */
    public static final String DEFAULT_MODEL = "wanx-v1";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;

    public BailianImageAdapter(HttpClient httpClient, String apiKey, String baseUrl, String defaultModel) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl;
        this.defaultModel = (defaultModel == null || defaultModel.isBlank()) ? DEFAULT_MODEL : defaultModel;
    }

    @Nonnull
    @Override
    public ImageResponse call(@Nonnull ImagePrompt request) {
        Objects.requireNonNull(request, "request must not be null");
        ImageRawResponse raw = httpClient.post(baseUrl, buildRequestBody(request))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable") // 文生图默认异步；同步语义以 polling 关闭，这里标记为同步 enable=false 需另外处理
                .execute(ImageRawResponse.class);
        return toImageResponse(raw);
    }

    /**
     * 同步包装：本实现同步返回，但内部走 {@link HttpClient#future} 通道便于上层切换为异步。
     * 当前接口（{@link ImageModel#call(ImagePrompt)}）未暴露 {@code callAsync}，故以同步方式实现。
     */
    public CompletableFuture<ImageResponse> callAsync(ImagePrompt request) {
        Objects.requireNonNull(request, "request must not be null");
        return httpClient.post(baseUrl, buildRequestBody(request))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .future(ImageRawResponse.class)
                .thenApply(this::toImageResponse);
    }

    /**
     * 把 Spring AI {@link ImagePrompt} 装配为 DashScope 请求体。
     * <p>
     * 行为约定：
     * <ul>
     *   <li>{@code input.prompt}：拼接所有 {@link ImageMessage#getText()}（按消息顺序）</li>
     *   <li>{@code model}：优先取 {@link org.springframework.ai.image.ImageOptions#getModel()}，
     *       否则用构造时给定的默认模型</li>
     *   <li>{@code parameters}：把 {@link org.springframework.ai.image.ImageOptions} 中非 null 字段透传，
     *       字段名采用 DashScope 约定的 snake_case</li>
     * </ul>
     */
    private Map<String, Object> buildRequestBody(ImagePrompt request) {
        Map<String, Object> input = new HashMap<>();
        input.put("prompt", joinMessages(request.getInstructions()));

        Map<String, Object> parameters = new HashMap<>();
        String model = defaultModel;
        org.springframework.ai.image.ImageOptions options = request.getOptions();
        if (options != null) {
            if (options.getModel() != null && !options.getModel().isBlank()) {
                model = options.getModel();
            }
            // DashScope parameters 字段采用 snake_case；逐项透传非 null 值。
            if (options.getN() != null) {
                parameters.put("n", options.getN());
            }
            if (options.getWidth() != null) {
                parameters.put("width", options.getWidth());
            }
            if (options.getHeight() != null) {
                parameters.put("height", options.getHeight());
            }
            if (options.getStyle() != null && !options.getStyle().isBlank()) {
                parameters.put("style", options.getStyle());
            }
            if (options.getResponseFormat() != null && !options.getResponseFormat().isBlank()) {
                parameters.put("response_format", options.getResponseFormat());
            }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", input);
        if (!parameters.isEmpty()) {
            body.put("parameters", parameters);
        }
        return body;
    }

    private String joinMessages(List<ImageMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ImageMessage m : messages) {
            if (m == null || m.getText().isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(m.getText());
        }
        return sb.toString();
    }

    /**
     * 把 DashScope 响应装配为 Spring AI {@link ImageResponse}。
     * 缺失 output / results 时返回空结果列表 —— 上层可通过 {@code getResults().isEmpty()} 自行判断。
     */
    private ImageResponse toImageResponse(ImageRawResponse raw) {
        if (raw == null || raw.output == null || raw.output.results == null) {
            log.warn("Bailian image returned empty body");
            return new ImageResponse(Collections.emptyList());
        }
        List<ImageGeneration> generations = new ArrayList<>(raw.output.results.size());
        for (ImageRawItem item : raw.output.results) {
            if (item == null) {
                continue;
            }
            // 仅 url 字段回填；b64_json 由 DashScope 的 response_format=base64 触发，当前实现未启用。
            generations.add(new ImageGeneration(new Image(item.url, null)));
        }
        return new ImageResponse(generations);
    }

    // ====== DashScope response DTOs ======

    /** 顶层响应：{@code { "output": { "results": [...] } }}。 */
    static class ImageRawResponse {
        public ImageRawOutput output;
    }

    /** {@code output} 子对象。 */
    static class ImageRawOutput {
        public List<ImageRawItem> results;
    }

    /** 单张图片结果：{@code { "url": "..." }}。 */
    static class ImageRawItem {
        @JsonProperty("url")
        public String url;

        @JsonProperty("b64_json")
        public String b64Json;
    }
}