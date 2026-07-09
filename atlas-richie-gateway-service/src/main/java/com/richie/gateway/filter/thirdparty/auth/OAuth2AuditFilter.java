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
package com.richie.gateway.filter.thirdparty.auth;

import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.core.model.OAuth2ErrorResponse;
import com.richie.component.oauth.core.model.TokenResponse;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.contract.gateway.model.OAuth2AuditEvent;
import com.richie.contract.gateway.model.OAuth2AuditEventType;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.service.AuditService;
import com.richie.gateway.utils.NetworkUtils;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static com.richie.contract.gateway.model.OAuth2Constants.*;

/**
 * OAuth2.0 审计过滤器
 * <p>
 * 职责：
 * 1. 拦截 OAuth2.0 接口（/api/oauth2/*）的请求和响应
 * 2. 统一记录审计日志（Token 颁发、刷新、撤销、查询等）
 * 3. 检测异常行为并记录
 * <p>
 * 说明：
 * - 此过滤器专门处理 OAuth2.0 接口的审计，不处理业务逻辑
 * - 业务逻辑由 Controller 处理，审计逻辑由 Filter 统一处理
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-18
 */
@Slf4j
@Component
@ConditionalOnBean(ClientRegistry.class)
public class OAuth2AuditFilter extends AbstractBaseFilter {

    private final AuditService auditService;
    private final ClientRegistry clientRegistry;

    /**
     * 构造函数
     *
     * @param config        网关配置
     * @param i18n          国际化解析器
     * @param auditService  审计服务
     * @param clientRegistry 客户端注册中心
     */
    public OAuth2AuditFilter(GatewayConfig config, I18nResolver i18n,
                             AuditService auditService,
                             ClientRegistry clientRegistry) {
        super(config, i18n);
        this.auditService = auditService;
        this.clientRegistry = clientRegistry;
    }

    @Override
    public int getOrder() {
        // 在 InterfaceAuthFilter 之后执行，确保能获取到响应体
        return FilterOrder.OAUTH2_AUDIT_FILTER.getOrder();
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 只处理 OAuth2.0 接口
        if (!path.startsWith(OAUTH2_BASE)) {
            return chain.filter(exchange);
        }

        // 提取请求信息
        String ip = NetworkUtils.getIP(request);
        String userAgent = NetworkUtils.getUserAgent(request);

        // 如果是 Token 接口，需要从请求体中提取 grant_type
        if (path.equals("%s/token".formatted(OAUTH2_BASE))) {
            return handleTokenEndpointWithGrantType(exchange, chain, request, ip, userAgent);
        }

        // 其他接口直接处理
        return handleOtherEndpoints(exchange, chain, request, path, ip, userAgent);
    }

    /**
     * 处理 Token 接口（需要提取 grant_type）
     */
    private Mono<Void> handleTokenEndpointWithGrantType(ServerWebExchange exchange, GatewayFilterChain chain,
                                                         ServerHttpRequest request, String ip, String userAgent) {
        // 先从查询参数中提取 grant_type
        final String[] grantType = {request.getQueryParams().getFirst("grant_type")};

        // 如果查询参数中没有 grant_type，且是 form-urlencoded 请求，尝试从请求体中提取
        if (StringUtils.isBlank(grantType[0]) && isFormUrlEncoded(request)) {
            // 需要读取请求体来提取 grant_type
            return DataBufferUtils.join(request.getBody())
                    .flatMap(dataBuffer -> {
                        try {
                            // 读取请求体
                            String requestBody = dataBuffer.toString(StandardCharsets.UTF_8);

                            // 从请求体中提取 grant_type 和 refresh_token（如果是刷新请求）
                            String extractedGrantType = extractGrantTypeFromFormBody(requestBody);
                            if (StringUtils.isNotBlank(extractedGrantType)) {
                                grantType[0] = extractedGrantType;
                            }

                            // 如果是刷新请求，提取旧的 refresh_token
                            String oldRefreshToken = null;
                            if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType[0])) {
                                oldRefreshToken = extractRefreshTokenFromFormBody(requestBody);
                            }

                            // 将 grant_type 和旧的 refresh_token 存储到 exchange 属性中，供响应拦截器使用
                            exchange.getAttributes().put("oauth2.grant_type", grantType[0]);
                            if (StringUtils.isNotBlank(oldRefreshToken)) {
                                exchange.getAttributes().put("oauth2.old_refresh_token", oldRefreshToken);
                            }

                            // 重新包装请求体（因为请求体只能读取一次）
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

                            // 继续处理
                            return handleOtherEndpoints(newExchange, chain, newRequest, request.getURI().getPath(), ip, userAgent);
                        } catch (Exception e) {
                            log.warn("从请求体提取 grant_type 失败", e);
                            // 请求体解析失败，继续处理（grantType 可能为 null）
                            DataBufferUtils.release(dataBuffer);
                            exchange.getAttributes().put("oauth2.grant_type", grantType[0]);
                            return handleOtherEndpoints(exchange, chain, request, request.getURI().getPath(), ip, userAgent);
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    })
                    .onErrorResume(throwable -> {
                        log.debug("读取请求体失败，继续处理: {}", throwable.getMessage());
                        // 请求体读取失败，继续处理（grantType 可能为 null）
                        exchange.getAttributes().put("oauth2.grant_type", grantType[0]);
                        return handleOtherEndpoints(exchange, chain, request, request.getURI().getPath(), ip, userAgent);
                    });
        }

        // 如果查询参数中有 grant_type，尝试从查询参数中提取 refresh_token
        final String[] oldRefreshToken = {null};
        if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType[0])) {
            oldRefreshToken[0] = request.getQueryParams().getFirst(GRANT_TYPE_REFRESH_TOKEN);
        }

        // 如果查询参数中没有 refresh_token，且是 form-urlencoded 请求，尝试从请求体中提取
        if (StringUtils.isBlank(oldRefreshToken[0]) && isFormUrlEncoded(request)) {
            // 需要读取请求体来提取 refresh_token
            return DataBufferUtils.join(request.getBody())
                    .flatMap(dataBuffer -> {
                        try {
                            // 读取请求体
                            String requestBody = dataBuffer.toString(StandardCharsets.UTF_8);

                            // 从请求体中提取 grant_type（如果之前没有）
                            if (StringUtils.isBlank(grantType[0])) {
                                String extractedGrantType = extractGrantTypeFromFormBody(requestBody);
                                if (StringUtils.isNotBlank(extractedGrantType)) {
                                    grantType[0] = extractedGrantType;
                                }
                            }

                            // 如果是刷新请求，提取旧的 refresh_token
                            if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType[0])) {
                                String extractedRefreshToken = extractRefreshTokenFromFormBody(requestBody);
                                if (StringUtils.isNotBlank(extractedRefreshToken)) {
                                    oldRefreshToken[0] = extractedRefreshToken;
                                }
                            }

                            // 将 grant_type 和旧的 refresh_token 存储到 exchange 属性中
                            exchange.getAttributes().put("oauth2.grant_type", grantType[0]);
                            if (StringUtils.isNotBlank(oldRefreshToken[0])) {
                                exchange.getAttributes().put("oauth2.old_refresh_token", oldRefreshToken[0]);
                            }

                            // 重新包装请求体（因为请求体只能读取一次）
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

                            // 继续处理
                            return handleOtherEndpoints(newExchange, chain, newRequest, request.getURI().getPath(), ip, userAgent);
                        } catch (Exception e) {
                            log.warn("从请求体提取 refresh_token 失败", e);
                            // 请求体解析失败，继续处理
                            DataBufferUtils.release(dataBuffer);
                            exchange.getAttributes().put("oauth2.grant_type", grantType[0]);
                            if (StringUtils.isNotBlank(oldRefreshToken[0])) {
                                exchange.getAttributes().put("oauth2.old_refresh_token", oldRefreshToken[0]);
                            }
                            return handleOtherEndpoints(exchange, chain, request, request.getURI().getPath(), ip, userAgent);
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    })
                    .onErrorResume(throwable -> {
                        log.debug("读取请求体失败，继续处理: {}", throwable.getMessage(), throwable);
                        // 请求体读取失败，继续处理
                        exchange.getAttributes().put("oauth2.grant_type", grantType[0]);
                        if (StringUtils.isNotBlank(oldRefreshToken[0])) {
                            exchange.getAttributes().put("oauth2.old_refresh_token", oldRefreshToken[0]);
                        }
                        return handleOtherEndpoints(exchange, chain, request, request.getURI().getPath(), ip, userAgent);
                    });
        }

        // 如果查询参数中有 grant_type 和 refresh_token，或不是 form-urlencoded 请求，直接处理
        exchange.getAttributes().put("oauth2.grant_type", grantType[0]);
        if (StringUtils.isNotBlank(oldRefreshToken[0])) {
            exchange.getAttributes().put("oauth2.old_refresh_token", oldRefreshToken[0]);
        }
        return handleOtherEndpoints(exchange, chain, request, request.getURI().getPath(), ip, userAgent);
    }

    /**
     * 处理其他接口（装饰响应，拦截响应体）
     */
    private Mono<Void> handleOtherEndpoints(ServerWebExchange exchange, GatewayFilterChain chain,
                                            ServerHttpRequest request, String path, String ip, String userAgent) {
        // 装饰响应，拦截响应体
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Nonnull
            @Override
            public Mono<Void> writeWith(@Nonnull Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                    return fluxBody.collectList().flatMap(dataBuffers -> {
                        try {
                            // 读取响应体
                            String responseBody = readResponseBody(dataBuffers);
                            HttpStatusCode statusCode = getStatusCode();

                            // 根据路径和状态码记录审计日志
                            switch (path) {
                                case OAUTH2_BASE + "/token" -> {
                                    // 从 exchange 属性中获取 grant_type 和旧的 refresh_token
                                    String grantType = (String) exchange.getAttributes().get("oauth2.grant_type");
                                    String oldRefreshToken = (String) exchange.getAttributes().get("oauth2.old_refresh_token");
                                    auditTokenEndpoint(ip, userAgent, statusCode, responseBody, grantType, oldRefreshToken);
                                }
                                case OAUTH2_BASE + "/revoke" -> {
                                    // 从请求中提取 token（从查询参数或请求属性中获取）
                                    String token = extractRequestParam(request, "token");
                                    String tokenTypeHint = extractRequestParam(request, "token_type_hint");
                                    auditRevokeEndpoint(token, tokenTypeHint, ip, userAgent, statusCode);
                                }
                                case OAUTH2_BASE + "/introspect" -> {
                                }
                                // 查询接口通常不需要详细审计
                            }

                            // 重新包装响应体
                            DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
                            DataBuffer buffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
                            return super.writeWith(Mono.just(buffer));
                        } catch (Exception e) {
                            log.error("处理 OAuth2.0 审计失败: path={}", path, e);
                            // 出错时返回原始响应
                            return super.writeWith(body);
                        }
                    });
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.startsWith(OAUTH2_BASE) && config.getOauth2().isEnabled();
    }

    /**
     * 审计 Token 接口（/token）
     * <p>
     * 从响应体中提取所有信息，并根据 grant_type 区分首次颁发和刷新
     * <p>
     * 对于刷新请求，记录令牌全生命周期：
     * - 旧的 refresh_token（从请求中提取）
     * - 新的 access_token 和 refresh_token（从响应中提取）
     * - 通过 metadata 记录关联关系
     *
     * @param ip             客户端 IP
     * @param userAgent      用户代理
     * @param statusCode     响应状态码
     * @param responseBody   响应体
     * @param grantType      授权类型（client_credentials 或 refresh_token）
     * @param oldRefreshToken 旧的 refresh_token（仅刷新请求时有效）
     */
    private void auditTokenEndpoint(String ip, String userAgent,
                                    HttpStatusCode statusCode, String responseBody, String grantType,
                                    String oldRefreshToken) {
        try {
            if (statusCode == HttpStatus.OK) {
                // 成功响应：解析响应体，记录成功审计
                TokenResponse response = JsonUtils.getInstance().deserialize(responseBody, TokenResponse.class);
                if (response != null && StringUtils.isNotBlank(response.getAccessToken())) {
                    String tokenId = extractJwtId(response.getAccessToken());
                    String clientId = extractClientIdFromToken(response.getAccessToken());

                    if (StringUtils.isBlank(clientId)) {
                        log.warn("无法从响应中提取 clientId，跳过审计日志");
                        return;
                    }

                    // 获取客户端配置
                    ClientConfig clientConfig = clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME);
                    String clientName = clientConfig != null ? clientConfig.getClientName() : "unknown";

                    // 根据 grant_type 区分首次颁发和刷新
                    if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType)) {
                        // 刷新 token：记录为 TOKEN_REFRESHED，并记录令牌全生命周期
                        // 使用底层接口，通过 metadata 记录旧的 refresh_token 和新的 refresh_token
                        Map<String, Object> metadata = new HashMap<>();
                        if (StringUtils.isNotBlank(oldRefreshToken)) {
                            metadata.put("oldRefreshToken", oldRefreshToken);
                        }
                        if (StringUtils.isNotBlank(response.getRefreshToken())) {
                            metadata.put("newRefreshToken", response.getRefreshToken());
                        }
                        // 记录新的 access_token ID
                        metadata.put("newAccessTokenId", tokenId);

                        OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                                .eventType(OAuth2AuditEventType.TOKEN_REFRESHED)
                                .clientId(clientId)
                                .ip(ip)
                                .userAgent(userAgent)
                                .tokenId(tokenId)  // 新的 access_token ID
                                .grantType(grantType)
                                .metadata(metadata)
                                .result("SUCCESS")
                                .build();
                        auditService.auditAuthEvent(event);
                    } else {
                        // 首次颁发 token：记录为 TOKEN_ISSUED（默认或 client_credentials）
                        auditService.auditTokenIssued(
                                clientId,
                                clientName,
                                ip,
                                userAgent,
                                tokenId,
                                response.getExpiresIn()
                        );
                    }
                }
            } else {
                // 失败响应：解析错误信息，记录失败审计
                OAuth2ErrorResponse errorResponse = JsonUtils.getInstance().deserialize(responseBody, OAuth2ErrorResponse.class);
                String errorCode = errorResponse != null ? errorResponse.getError() : "UNKNOWN";
                String errorMsg = errorResponse != null ? errorResponse.getErrorDescription() : "未知错误";

                // 根据 grant_type 区分失败类型
                if (GRANT_TYPE_REFRESH_TOKEN.equals(grantType)) {
                    // 刷新失败：记录为 TOKEN_REFRESHED_FAILED
                    // 失败时无法从响应中提取 clientId，使用 "unknown"
                    auditService.auditTokenRefreshFailed("unknown", ip, userAgent, errorCode, errorMsg);
                } else {
                    // 颁发失败：记录为 TOKEN_ISSUE_FAILED
                    // 失败时无法从响应中提取 clientId，使用 "unknown"
                    auditService.auditTokenIssueFailed("unknown", ip, userAgent, errorCode, errorMsg);
                }
            }
        } catch (Exception e) {
            log.warn("解析 Token 接口响应失败，无法记录审计日志", e);
        }
    }

    /**
     * 审计撤销接口（/revoke）
     * <p>
     * 使用 tokenTypeHint 来区分 access_token 和 refresh_token：
     * - access_token：JWT 格式，可以提取 clientId 和 tokenId
     * - refresh_token：随机字符串，无法提取 clientId 和 tokenId，但仍需记录审计日志
     */
    private void auditRevokeEndpoint(String token, String tokenTypeHint,
                                     String ip, String userAgent, HttpStatusCode statusCode) {
        try {
            if (statusCode == HttpStatus.OK && StringUtils.isNotBlank(token)) {
                // 判断 token 类型
                boolean isRefreshToken = GRANT_TYPE_REFRESH_TOKEN.equals(tokenTypeHint);
                if (!isRefreshToken) {
                    // 尝试从 token 格式判断（JWT 格式为 access_token，随机字符串为 refresh_token）
                    isRefreshToken = !token.contains(".");
                }

                String clientId;
                String tokenId;

                if (isRefreshToken) {
                    // refresh_token 是随机字符串，无法从 token 中提取 clientId 和 tokenId
                    // 但需要记录审计日志，使用 "unknown" 作为 clientId
                    clientId = "unknown";
                    tokenId = null;  // refresh_token 没有 tokenId
                } else {
                    // access_token 是 JWT 格式，可以提取 clientId 和 tokenId
                    clientId = extractClientIdFromToken(token);
                    tokenId = extractJwtId(token);
                }

                // 记录审计日志（即使 clientId 为 null，也记录，使用 "unknown"）
                if (StringUtils.isBlank(clientId)) {
                    clientId = "unknown";
                }

                // 使用底层接口，通过 metadata 记录 token 类型
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("tokenType", isRefreshToken ? GRANT_TYPE_REFRESH_TOKEN : GRANT_TYPE_ACCESS_TOKEN);
                if (isRefreshToken) {
                    // refresh_token 没有 tokenId，记录 token 本身（部分，用于追踪）
                    metadata.put("refreshTokenPrefix", token.length() > 8 ? "%s...".formatted(token.substring(0, 8)) : token);
                }

                OAuth2AuditEvent event = OAuth2AuditEvent.builder()
                        .eventType(OAuth2AuditEventType.TOKEN_REVOKED)
                        .clientId(clientId)
                        .tokenId(tokenId)
                        .tokenType(isRefreshToken ? GRANT_TYPE_REFRESH_TOKEN : GRANT_TYPE_ACCESS_TOKEN)
                        .ip(ip)
                        .userAgent(userAgent)
                        .reason("用户主动撤销")
                        .metadata(metadata)
                        .result("SUCCESS")
                        .build();
                auditService.auditAuthEvent(event);
            }
        } catch (Exception e) {
            log.warn("记录撤销审计日志失败", e);
        }
    }

    /**
     * 判断请求是否为 form-urlencoded 格式
     *
     * @param request 请求对象
     * @return 是否为 form-urlencoded
     */
    private boolean isFormUrlEncoded(ServerHttpRequest request) {
        MediaType contentType = request.getHeaders().getContentType();
        return contentType != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType);
    }

    /**
     * 从 form-urlencoded 请求体中提取 grant_type
     * <p>
     * 请求体格式示例：grant_type=client_credentials&client_id=xxx&client_secret=yyy
     *
     * @param requestBody 请求体内容
     * @return grant_type，如果未找到则返回 null
     */
    private String extractGrantTypeFromFormBody(String requestBody) {
        if (StringUtils.isBlank(requestBody)) {
            return null;
        }

        try {
            // 解析 form-urlencoded 格式
            String[] pairs = requestBody.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && "grant_type".equals(keyValue[0])) {
                    // URL 解码（处理特殊字符）
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("解析 form-urlencoded 请求体失败", e);
        }

        return null;
    }

    /**
     * 从 form-urlencoded 请求体中提取 refresh_token
     * <p>
     * 请求体格式示例：grant_type=refresh_token&refresh_token=xxx
     *
     * @param requestBody 请求体内容
     * @return refresh_token，如果未找到则返回 null
     */
    private String extractRefreshTokenFromFormBody(String requestBody) {
        if (StringUtils.isBlank(requestBody)) {
            return null;
        }

        try {
            // 解析 form-urlencoded 格式
            String[] pairs = requestBody.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && GRANT_TYPE_REFRESH_TOKEN.equals(keyValue[0])) {
                    // URL 解码（处理特殊字符）
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("解析 form-urlencoded 请求体失败", e);
        }

        return null;
    }

    /**
     * 从请求中提取参数
     * <p>
     * 注意：对于 form-urlencoded 请求，参数在请求体中，但请求体只能读取一次。
     * 这里只从查询参数中提取，如果参数在请求体中，则从响应中的 token 提取 clientId。
     */
    private String extractRequestParam(ServerHttpRequest request, String paramName) {
        // 从查询参数中提取（OAuth2.0 标准接口通常使用 form-urlencoded，但查询参数也可能存在）
        return request.getQueryParams().getFirst(paramName);
    }

    /**
     * 读取响应体
     */
    private String readResponseBody(java.util.List<? extends DataBuffer> dataBuffers) {
        int totalLength = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
        byte[] content = new byte[totalLength];
        int offset = 0;
        for (DataBuffer db : dataBuffers) {
            int len = db.readableByteCount();
            db.read(content, offset, len);
            offset += len;
        }
        dataBuffers.forEach(DataBufferUtils::release);
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * 从 JWT Token 中提取 JWT ID (jti)
     */
    private String extractJwtId(String accessToken) {
        if (StringUtils.isBlank(accessToken) || !accessToken.contains(".")) {
            return null;
        }
        try {
            return JwtUtils.getArgument(accessToken, "jti");
        } catch (Exception e) {
            log.debug("提取 JWT ID 失败", e);
            return null;
        }
    }

    /**
     * 从 Token 中提取 clientId
     */
    private String extractClientIdFromToken(String token) {
        if (StringUtils.isBlank(token) || !token.contains(".")) {
            return null;
        }
        try {
            return JwtUtils.getArgument(token, JWT_CLAIM_CLIENT_ID);
        } catch (Exception e) {
            log.debug("提取 clientId 失败", e);
            return null;
        }
    }
}
