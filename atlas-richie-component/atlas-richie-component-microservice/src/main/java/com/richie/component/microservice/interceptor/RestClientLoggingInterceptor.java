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
package com.richie.component.microservice.interceptor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * RestClient 请求日志拦截器
 * <p>
 * 用于打印 RestClient 的详细请求信息，包括：
 * - 完整的请求 URL
 * - 请求方法
 * - 请求头
 * - 请求体（如果有）
 * - 响应状态码
 * - 响应头
 * - 响应体（如果有）
 * <p>
 * 用于调试 RestClient 调用问题
 * <p>
 * 注意：每次请求都会打印日志，用于调试 RestClient 调用问题
 *
 * @author richie696
 * @since 2025-12-15
 */
@Slf4j
@Component
public class RestClientLoggingInterceptor implements ClientHttpRequestInterceptor {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public RestClientLoggingInterceptor() {
    }

    /**
     * LRU 缓存，用于追踪已打印的请求，避免重复打印
     * Key: 请求的唯一标识（方法 + URL + 时间戳秒级）
     * Value: 是否已打印（固定为 true）
     * <p>
     * 使用 Caffeine 实现高性能线程安全的 LRU 缓存
     * 缓存大小：1000 个条目，超出后自动淘汰最久未使用的条目
     */
    private static final Cache<String, Boolean> printedRequests = Caffeine.newBuilder()
            .maximumSize(1000)
            .build();

    /**
     * 拦截 RestClient 请求：打印请求/响应信息（去重），并包装响应体以支持多次读取。
     *
     * @param request     HTTP 请求
     * @param body        请求体
     * @param execution   执行链
     * @return 响应（可能为包装类以支持多次读取 body）
     * @throws IOException 执行或读取响应时发生 IO 异常
     */
    @Override
    @Nonnull
    public ClientHttpResponse intercept(@Nonnull HttpRequest request, @Nonnull byte[] body, @Nonnull ClientHttpRequestExecution execution) throws IOException {
        // 生成请求 ID（用于关联请求和响应日志）
        // 使用毫秒级时间戳和请求对象的 hashCode，确保唯一性
        String requestId = "%s_%s_%d_%d".formatted(
                request.getMethod(),
                request.getURI().toString(),
                System.identityHashCode(request),
                System.currentTimeMillis()
        );

        // 生成去重标识（用于避免重复打印）
        // 使用秒级时间戳，同一秒内的相同请求视为同一请求
        String dedupeKey = "%s_%s_%d".formatted(
                request.getMethod(),
                request.getURI().toString(),
                System.currentTimeMillis() / 1000
        );

        // 只在第一次打印（避免拦截器链中的重复调用导致重复打印）
        // 使用 Caffeine 的 asMap().putIfAbsent 方法，原子性地实现 putIfAbsent 语义
        // 如果 key 不存在则放入并返回 null，如果已存在则返回现有值
        Boolean existing = printedRequests.asMap().putIfAbsent(dedupeKey, true);
        boolean shouldPrint = existing == null;  // null 表示成功放入（之前不存在）

        if (shouldPrint) {
            logRequest(request, body, requestId);
        }

        try {
            // 执行请求
            ClientHttpResponse response = execution.execute(request, body);

            // 打印响应信息（会读取响应体，所以需要包装响应以便可以多次读取）
            if (shouldPrint) {
                return logResponseAndWrap(response, requestId);
            } else {
                // 如果已经打印过，直接返回响应（不包装，避免重复读取响应体）
                return response;
            }
        } catch (Exception e) {
            if (shouldPrint) {
                log.error("RestClient 请求执行失败: requestId={}, url={}", requestId, request.getURI(), e);
            }
            throw e;
        }
    }

    /**
     * 打印请求信息（方法、URL、头、体）。
     *
     * @param request   HTTP 请求
     * @param body      请求体
     * @param requestId 请求 ID（用于关联日志）
     */
    private void logRequest(HttpRequest request, byte[] body, String requestId) {
        try {
            log.info("========== RestClient 请求信息 [RequestId: {}] ==========", requestId);
            log.info("请求方法: {}", request.getMethod());
            log.info("完整URL: {}", request.getURI());

            // 打印请求头（每个 header 单独一行，更清晰）
            log.info("请求头:");
            request.getHeaders().forEach((key, values) -> {
                String valueStr = String.join(", ", values);
                // 对于敏感信息（如 token），只显示前20个字符
                if (key.toLowerCase().contains("token") || key.toLowerCase().contains("authorization")) {
                    if (valueStr.length() > 20) {
                        valueStr = "%s... [已截断]".formatted(valueStr.substring(0, 20));
                    }
                }
                log.info("  {}: {}", key, valueStr);
            });

            if (body != null && body.length > 0) {
                String bodyStr = new String(body, StandardCharsets.UTF_8);
                // 限制请求体长度，避免日志过长
                if (bodyStr.length() > 2000) {
                    bodyStr = "%s... [已截断，总长度: %d 字符]".formatted(bodyStr.substring(0, 2000), bodyStr.length());
                }
                log.info("请求体: {}", bodyStr);
            } else {
                log.info("请求体: [无]");
            }
            log.info("==========================================");
        } catch (Exception e) {
            log.warn("打印请求信息失败: requestId={}", requestId, e);
        }
    }

    /**
     * 打印响应信息并包装响应，以便可以多次读取响应体。
     *
     * @param response  原始响应
     * @param requestId 请求 ID
     * @return 包装后的响应（body 可多次读取）
     */
    private ClientHttpResponse logResponseAndWrap(ClientHttpResponse response, String requestId) {
        try {
            log.info("========== RestClient 响应信息 [RequestId: {}] ==========", requestId);
            try {
                log.info("状态码: {}", response.getStatusCode());
            } catch (Exception e) {
                log.warn("获取响应状态码失败: requestId={}", requestId, e);
            }

            // 打印响应头（每个 header 单独一行，更清晰）
            log.info("响应头:");
            response.getHeaders().forEach((key, values) -> {
                log.info("  {}: {}", key, String.join(", ", values));
            });

            // 读取响应体并缓存，以便可以多次读取
            byte[] bodyBytes = null;
            try {
                bodyBytes = StreamUtils.copyToByteArray(response.getBody());
                if (bodyBytes.length > 0) {
                    String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
                    // 限制响应体长度，避免日志过长
                    if (bodyStr.length() > 2000) {
                        bodyStr = bodyStr.substring(0, 2000) + "... [已截断，总长度: " + bodyStr.length() + " 字符]";
                    }
                    log.info("响应体: {}", bodyStr);
                } else {
                    log.info("响应体: [空]");
                }
            } catch (IOException e) {
                log.warn("读取响应体失败: requestId={}", requestId, e);
            }
            log.info("==========================================");

            // 返回包装后的响应，使用缓存的响应体
            return new CachedBodyClientHttpResponse(response, bodyBytes);
        } catch (Exception e) {
            log.warn("打印响应信息失败: requestId={}", requestId, e);
            return response;
        }
    }

    /**
         * 缓存响应体的 ClientHttpResponse 包装器
         */
        private record CachedBodyClientHttpResponse(ClientHttpResponse response,
                                                    byte[] cachedBody) implements ClientHttpResponse {

        @Override
            @Nonnull
            public HttpStatusCode getStatusCode() throws IOException {
                return response.getStatusCode();
            }

            @Override
            @Nonnull
            public String getStatusText() throws IOException {
                return response.getStatusText();
            }

            @Override
            public void close() {
                response.close();
            }

            @Override
            @Nonnull
            public InputStream getBody() throws IOException {
                return cachedBody != null ? new ByteArrayInputStream(cachedBody) : new ByteArrayInputStream(new byte[0]);
            }

            @Override
            @Nonnull
            public HttpHeaders getHeaders() {
                return response.getHeaders();
            }
        }
}
