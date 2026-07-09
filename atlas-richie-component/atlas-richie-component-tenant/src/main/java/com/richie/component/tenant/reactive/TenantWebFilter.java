/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.reactive;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.model.TenantPrincipal;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;



import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reactive 环境租户身份过滤器（WebFlux 版 {@code TenantIdentityFilter}）。
 *
 * <p>在 WebFlux 请求入口处完成：
 * <ol>
 *   <li>从 JWT（{@code X-ACCESS-TOKEN}）解析租户主体</li>
 *   <li>降级：从 {@code X-Tenant-ID} header 解析（Feign 内部调用场景）</li>
 *   <li>校验租户存在性、状态（过期/迁移中）</li>
 *   <li>将租户上下文写入 Reactor {@code Context}，纯 Reactive 链路通过
 *       {@link ReactorTenantContext#get()} 读取</li>
 *   <li>阻塞链路（如 MyBatis 拦截器）需使用
 *       {@link ReactorTenantContext#bridgeToBlocking(Runnable)} 手动桥接</li>
 * </ol>
 *
 * <p>仅当 {@code MultiTenancyProperties#isEnabled()} 为 {@code true} 且
 * 应用为 Reactive Web 环境时生效。</p>
 *
 * @author richie696
 * @since 1.0.0
 * @see TenantContextKeys
 * @see ReactorTenantContext
 */
@Order(TenantWebFilter.ORDER)
public class TenantWebFilter implements WebFilter {

    /**
     * 与 Servlet 版保持一致的优先级：{@link Ordered#HIGHEST_PRECEDENCE} + 500。
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 500;

    private static final Logger log = LoggerFactory.getLogger(TenantWebFilter.class);

    private final MultiTenancyProperties properties;
    private final TenantInfoProvider tenantInfoProvider;
    private final List<String> whitelistPaths;
    private final List<String> superAdminPaths;

    public TenantWebFilter(MultiTenancyProperties properties,
                           TenantInfoProvider tenantInfoProvider,
                           List<String> whitelistPaths,
                           List<String> superAdminPaths) {
        this.properties = properties;
        this.tenantInfoProvider = tenantInfoProvider;
        this.whitelistPaths = whitelistPaths != null ? whitelistPaths : List.of();
        this.superAdminPaths = superAdminPaths != null ? superAdminPaths : List.of();
    }

    @Override
    @Nonnull
    public Mono<Void> filter(@Nonnull ServerWebExchange exchange,
                             @Nonnull WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String requestPath = exchange.getRequest().getURI().getPath();

        // 白名单检查
        if (isWhitelisted(requestPath)) {
            return chain.filter(exchange);
        }

        return resolveTenant(exchange)
            .flatMap(principal ->
                chain.filter(exchange)
                    .contextWrite(ctx -> ctx.put(TenantContextKeys.TENANT_KEY, principal))
                    .then(Mono.just(true))
            )
            .switchIfEmpty(Mono.defer(() -> {
                if (properties.isEnforceAuthTenant() && !isSuperAdminPath(requestPath)) {
                    return writeError(exchange, TenantErrorCode.TENANT_AUTH_MISSING_TOKEN, requestPath)
                        .then(Mono.just(true));
                }
                return chain.filter(exchange).then(Mono.just(true));
            }))
            .onErrorResume(this::isTenantException, ex ->
                writeError(exchange, ((TenantValidationException) ex).getErrorCode(),
                    ((TenantValidationException) ex).getArgs())
                    .then(Mono.just(true)))
            .then();
    }

    /**
     * 解析租户主体：JWT → Header 降级 → 校验。
     */
    private Mono<TenantPrincipal> resolveTenant(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            TenantPrincipal principal = resolveFromJwt(exchange);
            if (principal != null) {
                return principal;
            }
            principal = resolveFromHeader(exchange);
            return principal;
        }).flatMap(principal -> {

            Long tenantId = principal.getTenantId();
            if (tenantId == null || tenantId <= 0) {
                return Mono.error(new TenantValidationException(
                    TenantErrorCode.TENANT_AUTH_INVALID_FORMAT, tenantId));
            }

            TenantInfo tenantInfo = tenantInfoProvider.getTenantInfo(tenantId);
            if (tenantInfo == null) {
                return Mono.error(new TenantValidationException(
                    TenantErrorCode.TENANT_IDENTITY_NOT_FOUND, tenantId));
            }
            if (tenantInfo.getStatus() == TenantStatus.EXPIRED) {
                return Mono.error(new TenantValidationException(
                    TenantErrorCode.TENANT_AUTH_EXPIRED, tenantId));
            }
            if (tenantInfo.getStatus() == TenantStatus.MIGRATING) {
                return Mono.error(new TenantValidationException(
                    TenantErrorCode.TENANT_MIGRATING, tenantId));
            }

            return Mono.just(principal);
        });
    }

    private TenantPrincipal resolveFromJwt(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders()
            .getFirst(GlobalConstants.X_ACCESS_TOKEN);
        if (token == null || token.isEmpty()) {
            token = exchange.getRequest().getHeaders()
                .getFirst(JwtUtils.X_ACCESS_TOKEN);
        }
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            TenantPrincipal principal = JwtUtils.getTenantPrincipal(token);
            if (principal != null && principal.getTenantId() != null) {
                return principal;
            }
        } catch (Exception e) {
            log.debug("Failed to parse tenant from JWT: {}", e.getMessage());
        }
        return null;
    }

    private TenantPrincipal resolveFromHeader(ServerWebExchange exchange) {
        String tenantIdStr = exchange.getRequest().getHeaders()
            .getFirst(properties.getTenantIdHeader());
        if (tenantIdStr == null || tenantIdStr.isEmpty()) {
            tenantIdStr = exchange.getRequest().getHeaders()
                .getFirst(GlobalConstants.X_TENANT_ID);
        }
        if (tenantIdStr == null || tenantIdStr.isEmpty()) {
            return null;
        }
        try {
            Long tenantId = Long.valueOf(tenantIdStr);
            return new TenantPrincipal().setTenantId(tenantId);
        } catch (NumberFormatException e) {
            log.warn("Invalid tenant ID header value: {} = '{}'",
                properties.getTenantIdHeader(), tenantIdStr);
            return null;
        }
    }

    private boolean isWhitelisted(String requestUri) {
        for (String path : whitelistPaths) {
            if (requestUri.startsWith(path) || matchSimplePattern(requestUri, path)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSuperAdminPath(String requestUri) {
        for (String path : superAdminPaths) {
            if (requestUri.startsWith(path) || matchSimplePattern(requestUri, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchSimplePattern(String uri, String pattern) {
        if (pattern.endsWith("/**")) {
            return uri.startsWith(pattern.substring(0, pattern.length() - 3));
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return uri.startsWith(prefix) && !uri.substring(prefix.length()).contains("/");
        }
        return uri.equals(pattern);
    }

    private static Mono<Void> writeError(ServerWebExchange exchange,
                                          TenantErrorCode errorCode,
                                          Object... args) {
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(errorCode.getHttpStatus()));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] jsonBytes = serializeJson(errorCode.format(args));
        return exchange.getResponse()
            .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(jsonBytes)));
    }

    private static byte[] serializeJson(String msg) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "TENANT_ERROR");
            body.put("msg", msg);
            body.put("timestamp", System.currentTimeMillis());
            body.put("data", null);
            return mapper.writeValueAsBytes(body);
        } catch (Exception e) {
            log.error("Failed to serialize error response", e);
            return ("{\"msg\":\"" + msg + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private boolean isTenantException(Throwable ex) {
        return ex instanceof TenantValidationException;
    }

    /**
     * 租户校验异常（框架内部使用，终止 filter chain 并返回错误响应）。
     */
    static class TenantValidationException extends RuntimeException {
        private final TenantErrorCode errorCode;
        private final Object[] args;

        TenantValidationException(TenantErrorCode errorCode, Object... args) {
            super(errorCode.format(args));
            this.errorCode = errorCode;
            this.args = args;
        }

        TenantErrorCode getErrorCode() {
            return errorCode;
        }

        Object[] getArgs() {
            return args;
        }
    }
}
