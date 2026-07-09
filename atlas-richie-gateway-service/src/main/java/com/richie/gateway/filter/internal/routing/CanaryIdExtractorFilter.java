/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.filter.internal.routing;

import com.richie.contract.gateway.config.DeployConfig;
import com.richie.gateway.config.GatewayConfig;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import tools.jackson.databind.JsonNode;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 灰度ID提取过滤器
 * <p>
 * 自动从请求中提取灰度ID（支持自定义字段配置），并设置到 X-Canary-Id 请求头，用于灰度发布路由。
 * <p>
 * 功能特性：
 * - 支持自定义字段配置（通过 CanaryFieldConfig），可提取任意类型的ID
 * - 内置默认规则：提取门店ID（storeId/shopCode），适用于门店维度灰度场景
 * - 支持从多个来源提取：请求头、JWT Token、路径参数、查询参数、请求体
 * <p>
 * 提取优先级（支持自定义字段配置）：
 * 1. 请求头 X-Canary-Id（已存在则直接使用，不重复提取）
 * 2. 请求头（自定义字段或内置规则：x-rd-request-shopcode）
 * 3. JWT Token（自定义字段列表或内置规则：storeId、shopCode）
 * 4. 请求体 JSON（自定义字段列表或内置规则：storeId、shopId、shopCode）
 * 5. 路径参数（自定义模式或内置规则：/api/store/{id}/xxx）
 * 6. 查询参数（自定义字段或内置规则：?storeId=xxx）
 *
 * @author richie696
 * @version 2.0
 * @since 2025-12-09
 */
@Slf4j
@Component
public class CanaryIdExtractorFilter extends AbstractBaseFilter {

    /**
     * 路径参数中提取灰度ID的正则表达式（内置规则，默认匹配门店ID）
     * 匹配类似 /api/store/123/xxx 或 /api/xxx/123 的路径
     */
    private static final Pattern DEFAULT_STORE_ID_PATH_PATTERN = Pattern.compile("/(?:store|shop|门店)/(\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * 内置字段名称（默认规则，适用于门店维度灰度场景）
     */
    private static final String DEFAULT_HEADER_FIELD = GlobalConstants.X_RD_REQUEST_SHOP_CODE;
    private static final List<String> DEFAULT_TOKEN_FIELDS = Arrays.asList("storeId", "shopCode");
    private static final String DEFAULT_QUERY_FIELD = "storeId";
    private static final List<String> DEFAULT_BODY_FIELDS = Arrays.asList("storeId", "shopId", "shopCode");

    public CanaryIdExtractorFilter(GatewayConfig config, I18nResolver i18n) {
        super(config, i18n);
    }

    public int getOrder() {
        // 在接口权限过滤器之后，灰度负载均衡过滤器之前执行
        // 确保在认证和权限验证完成后提取灰度ID
        return FilterOrder.CANARY_ID_EXTRACTOR_FILTER.getOrder();
    }

    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 只在启用灰度发布时执行
        if (!config.getDeploy().isEnable() || !isIdCategory()) {
            return chain.filter(exchange);
        }

        HttpHeaders headers = exchange.getRequest().getHeaders();

        // 如果已存在 X-Canary-Id，直接使用，不重复提取
        String existingCanaryId = headers.getFirst(GlobalConstants.X_CANARY_ID);
        if (StringUtils.isNotBlank(existingCanaryId)) {
            if (log.isTraceEnabled()) {
                log.trace("X-Canary-Id already exists: {}", existingCanaryId);
            }
            return chain.filter(exchange);
        }

        // 按优先级提取灰度ID（支持从请求体提取）
        return extractCanaryIdAsync(exchange, chain);
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        // 只在启用灰度发布且为 ID 模式时执行
        return config.getDeploy().isEnable() && isIdCategory();
    }

    /**
     * 检查是否启用灰度发布
     * <p>
     * 灰度发布统一使用 ID 模式，无需检查类型
     *
     * @return true 如果启用灰度发布
     */
    private boolean isIdCategory() {
        return config.getDeploy().isEnable();
    }

    /**
     * 异步从请求中提取灰度ID（支持从请求体提取）
     * <p>
     * 提取优先级：
     * 1. 请求头 X-Canary-Id（已存在则直接使用）
     * 2. 请求头（自定义字段或内置规则）
     * 3. JWT Token（自定义字段或内置规则）
     * 4. 请求体 JSON（自定义字段或内置规则）
     * 5. 路径参数（自定义模式或内置规则）
     * 6. 查询参数（自定义字段或内置规则）
     *
     * @param exchange 请求交换对象
     * @param chain 过滤器链
     * @return Mono<Void>
     */
    private Mono<Void> extractCanaryIdAsync(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();

        // 优先级 1: 请求头 X-Canary-Id（已存在则直接使用，已在 doFilter 中处理）

        // 优先级 2: 从请求头提取（自定义字段或内置规则）
        String canaryId = extractFromHeader(headers);
        if (StringUtils.isNotBlank(canaryId)) {
            return setCanaryIdAndContinue(exchange, chain, canaryId, "header");
        }

        // 优先级 3: 从 JWT Token 提取（自定义字段或内置规则）
        canaryId = extractFromToken(headers);
        if (StringUtils.isNotBlank(canaryId)) {
            return setCanaryIdAndContinue(exchange, chain, canaryId, "JWT token");
        }

        // 优先级 4: 从路径参数提取（自定义模式或内置规则）
        canaryId = extractFromPath(exchange.getRequest().getURI().getPath());
        if (StringUtils.isNotBlank(canaryId)) {
            return setCanaryIdAndContinue(exchange, chain, canaryId, "path");
        }

        // 优先级 5: 从查询参数提取（自定义字段或内置规则）
        canaryId = extractFromQuery(exchange.getRequest().getURI().getQuery());
        if (StringUtils.isNotBlank(canaryId)) {
            return setCanaryIdAndContinue(exchange, chain, canaryId, "query");
        }

        // 优先级 6: 从请求体提取（需要读取请求体，异步处理）
        return extractFromBodyAsync(exchange, chain);
    }

    /**
     * 从请求头提取灰度ID
     */
    private String extractFromHeader(HttpHeaders headers) {
        DeployConfig.CanaryFieldConfig fieldConfig = config.getDeploy().getFieldConfig();

        // 优先使用自定义字段（如果配置了）
        if (fieldConfig != null && StringUtils.isNotBlank(fieldConfig.getHeaderFieldName())) {
            String value = headers.getFirst(fieldConfig.getHeaderFieldName());
            if (StringUtils.isNotBlank(value)) {
                if (log.isTraceEnabled()) {
                    log.trace("Extracted canary ID from custom header field [{}]: {}", fieldConfig.getHeaderFieldName(), value);
                }
                return value;
            }
        }

        // 使用内置规则
        String value = headers.getFirst(DEFAULT_HEADER_FIELD);
        if (StringUtils.isNotBlank(value)) {
            if (log.isTraceEnabled()) {
                log.trace("Extracted canary ID from default header field [{}]: {}", DEFAULT_HEADER_FIELD, value);
            }
            return value;
        }

        return null;
    }

    /**
     * 从 JWT Token 提取灰度ID
     */
    private String extractFromToken(HttpHeaders headers) {
        String token = headers.getFirst(JwtUtils.X_ACCESS_TOKEN);
        if (StringUtils.isBlank(token) || "null".equalsIgnoreCase(token) || "undefined".equalsIgnoreCase(token)) {
            return null;
        }

        DeployConfig.CanaryFieldConfig fieldConfig = config.getDeploy().getFieldConfig();

        // 优先使用自定义字段列表（如果配置了）
        if (fieldConfig != null && fieldConfig.getTokenFieldNames() != null && !fieldConfig.getTokenFieldNames().isEmpty()) {
            for (String fieldName : fieldConfig.getTokenFieldNames()) {
                try {
                    String value = JwtUtils.getArgument(token, fieldName);
                    if (StringUtils.isNotBlank(value)) {
                        if (log.isTraceEnabled()) {
                            log.trace("Extracted canary ID from JWT token custom field [{}]: {}", fieldName, value);
                        }
                        return value;
                    }
                } catch (Exception e) {
                    // 继续尝试下一个字段
                }
            }
        }

        // 使用内置规则
        for (String fieldName : DEFAULT_TOKEN_FIELDS) {
            try {
                String value = JwtUtils.getArgument(token, fieldName);
                if (StringUtils.isNotBlank(value)) {
                    if (log.isTraceEnabled()) {
                        log.trace("Extracted canary ID from JWT token default field [{}]: {}", fieldName, value);
                    }
                    return value;
                }
            } catch (Exception e) {
                // 继续尝试下一个字段
            }
        }

        return null;
    }

    /**
     * 从路径参数提取灰度ID
     */
    private String extractFromPath(String path) {
        DeployConfig.CanaryFieldConfig fieldConfig = config.getDeploy().getFieldConfig();
        Pattern pattern;

        // 优先使用自定义模式（如果配置了）
        if (fieldConfig != null && StringUtils.isNotBlank(fieldConfig.getPathFieldPattern())) {
            pattern = Pattern.compile("/(?:%s)/(\\d+)".formatted(fieldConfig.getPathFieldPattern()), Pattern.CASE_INSENSITIVE);
        } else {
            // 使用内置规则
            pattern = DEFAULT_STORE_ID_PATH_PATTERN;
        }

        Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            String value = matcher.group(1);
            if (StringUtils.isNotBlank(value)) {
                if (log.isTraceEnabled()) {
                    log.trace("Extracted canary ID from path: {}", value);
                }
                return value;
            }
        }

        return null;
    }

    /**
     * 从查询参数提取灰度ID
     */
    private String extractFromQuery(String query) {
        if (StringUtils.isBlank(query)) {
            return null;
        }

        DeployConfig.CanaryFieldConfig fieldConfig = config.getDeploy().getFieldConfig();
        String fieldName = (fieldConfig != null && StringUtils.isNotBlank(fieldConfig.getQueryFieldName()))
                ? fieldConfig.getQueryFieldName()
                : DEFAULT_QUERY_FIELD;

        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && fieldName.equalsIgnoreCase(keyValue[0])) {
                String value = keyValue[1];
                if (StringUtils.isNotBlank(value)) {
                    if (log.isTraceEnabled()) {
                        log.trace("Extracted canary ID from query parameter [{}]: {}", fieldName, value);
                    }
                    return value;
                }
            }
        }

        return null;
    }

    /**
     * 从请求体（JSON）异步提取灰度ID
     */
    private Mono<Void> extractFromBodyAsync(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpHeaders headers = request.getHeaders();

        // 只处理 JSON 请求体
        MediaType contentType = headers.getContentType();
        if (contentType == null || !contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            // 不是 JSON 请求，继续执行后续过滤器
            return chain.filter(exchange);
        }

        // 读取请求体
        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    try {
                        String requestBody = dataBuffer.toString(StandardCharsets.UTF_8);
                        if (StringUtils.isBlank(requestBody)) {
                            return chain.filter(exchange);
                        }

                        // 从 JSON 请求体中提取
                        String canaryId = extractFromBody(requestBody);

                        // 创建新的请求体（保持原样）
                        DataBuffer newBuffer = dataBuffer.factory()
                                .wrap(requestBody.getBytes(StandardCharsets.UTF_8));

                        // 创建新的请求
                        ServerHttpRequest newRequest = new ServerHttpRequestDecorator(request) {
                            @Nonnull
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(newBuffer);
                            }
                        };

                        // 创建新的交换机
                        ServerWebExchange newExchange = exchange.mutate()
                                .request(newRequest)
                                .build();

                        if (StringUtils.isNotBlank(canaryId)) {
                            // 找到灰度ID，设置到请求头
                            return setCanaryIdAndContinue(newExchange, chain, canaryId, "request body");
                        } else {
                            // 未找到灰度ID，继续执行后续过滤器
                            return chain.filter(newExchange);
                        }

                    } catch (Exception e) {
                        log.warn("Failed to extract canary ID from request body: {}", e.getMessage());
                        // 请求体解析失败，继续执行后续过滤器
                        return chain.filter(exchange);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .onErrorResume(throwable -> {
                    // 请求体读取失败，继续执行后续过滤器
                    log.debug("Request body read failed, continuing: {}", throwable.getMessage());
                    return chain.filter(exchange);
                });
    }

    /**
     * 从请求体（JSON）提取灰度ID
     */
    private String extractFromBody(String requestBody) {
        try {
            JsonNode jsonNode = JsonUtils.getInstance().deserialize(requestBody, JsonNode.class);
            if (jsonNode == null || !jsonNode.isObject()) {
                return null;
            }

            DeployConfig.CanaryFieldConfig fieldConfig = config.getDeploy().getFieldConfig();

            // 优先使用自定义字段列表（如果配置了）
            if (fieldConfig != null && fieldConfig.getBodyFieldNames() != null && !fieldConfig.getBodyFieldNames().isEmpty()) {
                for (String fieldName : fieldConfig.getBodyFieldNames()) {
                    JsonNode valueNode = jsonNode.get(fieldName);
                    if (valueNode != null && !valueNode.isNull()) {
                        String value = valueNode.asString();
                        if (StringUtils.isNotBlank(value)) {
                            if (log.isTraceEnabled()) {
                                log.trace("Extracted canary ID from request body custom field [{}]: {}", fieldName, value);
                            }
                            return value;
                        }
                    }
                }
            }

            // 使用内置规则
            for (String fieldName : DEFAULT_BODY_FIELDS) {
                JsonNode valueNode = jsonNode.get(fieldName);
                if (valueNode != null && !valueNode.isNull()) {
                    String value = valueNode.asString();
                    if (StringUtils.isNotBlank(value)) {
                        if (log.isTraceEnabled()) {
                            log.trace("Extracted canary ID from request body default field [{}]: {}", fieldName, value);
                        }
                        return value;
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Failed to parse request body as JSON: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 设置 X-Canary-Id 并继续执行过滤器链
     */
    private Mono<Void> setCanaryIdAndContinue(ServerWebExchange exchange, GatewayFilterChain chain,
                                             String canaryId, String source) {
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(builder -> builder.header(GlobalConstants.X_CANARY_ID, canaryId))
                .build();

        if (log.isDebugEnabled()) {
            log.debug("Extracted canary ID: {} from {} for path: {}",
                    canaryId, source, exchange.getRequest().getURI().getPath());
        }

        return chain.filter(modifiedExchange);
    }
}
