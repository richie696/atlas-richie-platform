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
package com.richie.gateway.filter.thirdparty.auth;

import com.richie.component.cache.GlobalCache;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.component.oauth.core.ScopeResolver;
import com.richie.component.oauth.core.TokenEndpoint;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.service.AuditService;
import com.richie.gateway.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第三方系统 OAuth2.0 认证过滤器
 * <p>
 * 功能：
 * 1. 验证 access_token（从 Authorization: Bearer {token} 中提取）
 * 2. 验证 IP 白名单（从客户端配置中读取，按 clientId 管理）
 * 3. 将 clientId 设置到请求头，供后端服务使用
 * <p>
 * 安全说明：
 * - IP 白名单按客户端（clientId）管理，存储在 Redis 缓存中
 * - 如果访问 IP 不在客户端白名单中，说明令牌可能被盗用，拒绝访问
 *
 * @author richie696
 * @version 2.0
 * @since 2023-07-19 11:30:46
 */
@Slf4j
@Component
@ConditionalOnBean(TokenEndpoint.class)
public class InterfaceAuthFilter extends AbstractBaseFilter {

    private final TokenEndpoint tokenEndpoint;
    private final AuditService auditService;
    private final ScopeResolver scopeResolver;

    /**
     * 构造函数
     *
     * @param config         网关配置
     * @param i18n           国际化解析器
     * @param tokenEndpoint   Token 端点
     * @param auditService   审计服务
     * @param scopeResolver   Scope 权限解析器
     */
    public InterfaceAuthFilter(GatewayConfig config, I18nResolver i18n, TokenEndpoint tokenEndpoint, AuditService auditService, ScopeResolver scopeResolver) {
        super(config, i18n);
        this.tokenEndpoint = tokenEndpoint;
        this.auditService = auditService;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    @Override
    public int getOrder() {
        return FilterOrder.INTERFACE_AUTH_FILTER.getOrder();
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getURI().getPath();

        // 跳过 OAuth2.0 Token 接口（由 Controller 处理）
        if (path.startsWith(OAuth2Constants.OAUTH2_BASE)) {
            return chain.filter(exchange);
        }

        String clientIp = NetworkUtils.getIP(request);
        String userAgent = NetworkUtils.getUserAgent(request);
        String method = request.getMethod().name();

        // 1. 提取 access_token
        String accessToken = extractAccessToken(request);
        if (StringUtils.isBlank(accessToken)) {
            log.warn("缺少 access_token，请求路径: {}", path);
            // 记录访问被拒绝审计日志
            recordAccessDenied(null, path, method, clientIp, userAgent, "token_missing", OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌缺失");
            return returnOAuth2Error(response, OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌缺失");
        }

        // 2. 验证 access_token
        String clientId = extractClientIdFromToken(accessToken);
        if (clientId == null) {
            recordAccessDenied(null, path, method, clientIp, userAgent, "token_invalid", OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌缺失");
            return returnOAuth2Error(response, OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌无效");
        }
        List<String> ipWhitelist = tokenEndpoint.getIpWhitelist(accessToken);
        if (ipWhitelist == null) {
            log.warn("access_token 验证失败，请求路径: {}", path);
            // 记录访问被拒绝审计日志
            String tokenId = extractJwtId(accessToken);
            recordAccessDenied(clientId, path, method, clientIp, userAgent, "token_invalid", OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌无效或已过期");
            return returnOAuth2Error(response, OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌无效或已过期");
        }

        // 2.1 验证 access_token 与 IP 的绑定关系（令牌归属校验）
        if (!verifyAccessTokenIpBinding(accessToken, clientIp, clientId)) {
            log.warn("access_token 绑定 IP 不匹配: clientId={}, clientIp={}, path={}", clientId, clientIp, path);
            recordAccessDenied(clientId, path, method, clientIp, userAgent,
                    "token_ip_mismatch", OAuth2Constants.ERROR_IP_NOT_ALLOWED, "令牌绑定 IP 不匹配");
            return returnOAuth2Error(response, OAuth2Constants.ERROR_IP_NOT_ALLOWED, "令牌绑定 IP 不匹配");
        }

        // 3. 验证 IP 白名单（从客户端配置中读取，防止令牌被盗用）
        if (!isIpAllowed(clientIp, clientId, ipWhitelist)) {
            log.warn("IP 不在客户端白名单中，可能令牌被盗用: clientIp={}, clientId={}, 请求路径: {}", clientIp, clientId, path);
            // 记录访问被拒绝审计日志（标记为可疑行为）
            String tokenId = extractJwtId(accessToken);
            recordAccessDenied(clientId, path, method, clientIp, userAgent, "ip_not_allowed", OAuth2Constants.ERROR_IP_NOT_ALLOWED, "IP 地址不在客户端白名单中，令牌可能被盗用");
            return returnOAuth2Error(response, OAuth2Constants.ERROR_IP_NOT_ALLOWED, "IP 地址不在客户端白名单中");
        }

        // 4. 验证 Scope 权限（根据接口路径和方法，验证 token 中的 scope 是否包含所需的 scope）
        if (!verifyScopePermission(accessToken, path, method, clientId, clientIp, userAgent)) {
            log.warn("Scope 权限验证失败: clientId={}, path={}, method={}", clientId, path, method);
            // 记录访问被拒绝审计日志
            recordAccessDenied(clientId, path, method, clientIp, userAgent, "insufficient_scope", OAuth2Constants.ERROR_INVALID_SCOPE, "权限不足，Token 中的 scope 不包含访问该接口所需的 scope");
            return returnOAuth2Error(response, OAuth2Constants.ERROR_INVALID_SCOPE, "权限不足");
        }

        // 5. 设置请求头（供后端服务使用）
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(OAuth2Constants.HEADER_X_THIRD_PARTY_CLIENT_ID, clientId)
                .header(OAuth2Constants.GRANT_TYPE_ACCESS_TOKEN, accessToken)
                .header(GlobalConstants.X_RD_REQUEST_FLAG, Base64.getEncoder().encodeToString(clientIp.getBytes()))
                .build();

        log.debug("第三方系统认证通过: clientId={}, clientIp={}, path={}",
                clientId, clientIp, path);

        // 6. 记录访问通过审计日志（可选：高频场景可采样）
        if (shouldAuditAccess(path)) {
            String tokenId = extractJwtId(accessToken);
            auditService.auditAccessGranted(
                    clientId,
                    path,
                    method,
                    clientIp,
                    userAgent
            );
        }

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        // 只对非 OAuth2.0 Token 接口启用验证
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(OAuth2Constants.OAUTH2_BASE)) {
            return false;
        }
        return config.getOauth2().isEnabled();
    }

    /**
     * 从请求头中提取 access_token
     */
    private String extractAccessToken(ServerHttpRequest request) {
        // 从 Authorization: Bearer {token} 中提取
        String authorization = request.getHeaders().getFirst(OAuth2Constants.HEADER_AUTHORIZATION);
        if (StringUtils.isNotBlank(authorization) && authorization.startsWith(OAuth2Constants.BEARER_PREFIX)) {
            return authorization.substring(OAuth2Constants.BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    /**
     * 验证 IP 是否在客户端白名单中
     * <p>
     * 安全说明：
     * - IP 白名单按客户端（clientId）管理，存储在 Redis 缓存中
     * - 从客户端配置（clientConfig）中读取 IP 白名单
     * - 如果访问 IP 不在白名单中，说明令牌可能被盗用，拒绝访问
     * - 如果客户端未配置 IP 白名单，默认拒绝（安全考虑）
     *
     * @param clientIp         客户端访问 IP
     * @param clientId         客户端ID
     * @param clientIpWhitelist 客户端 IP 白名单（从 Redis 读取）
     * @return true 如果 IP 在白名单中，false 否则
     */
    private boolean isIpAllowed(String clientIp, String clientId, List<String> clientIpWhitelist) {
        if (clientIpWhitelist == null || clientIpWhitelist.isEmpty()) {
            log.warn("客户端未配置 IP 白名单，拒绝访问: clientId={}", clientId);
            return false;
        }

        // 检查访问 IP 是否在客户端白名单中
        boolean allowed = isIpInList(clientIp, clientIpWhitelist);
        if (!allowed) {
            log.warn("访问 IP 不在客户端白名单中，可能令牌被盗用: clientId={}, clientIp={}, whitelist={}",
                    clientId, clientIp, clientIpWhitelist);
        }
        return allowed;
    }

    /**
     * 检查 IP 是否在列表中（支持 CIDR 格式）
     */
    private boolean isIpInList(String ip, java.util.Collection<String> ipList) {
        if (ip == null || ipList == null || ipList.isEmpty()) {
            return false;
        }

        for (String allowedIp : ipList) {
            if (allowedIp.equals(ip)) {
                return true;
            }
            // 支持 CIDR 格式（如 192.168.1.0/24）
            if (allowedIp.contains("/")) {
                try {
                    SubnetUtils.SubnetInfo subnetInfo = new SubnetUtils(allowedIp).getInfo();
                    if (subnetInfo.isInRange(ip)) {
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("解析 CIDR 格式失败: {}", allowedIp, e);
                }
            }
        }
        return false;
    }

    /**
     * 校验 access_token 与 IP 的绑定关系
     * <p>
     * 绑定数据在签发/刷新时写入 Redis：
     * Key: access-token-ip:{accessToken}
     * Fields:
     * - clientId
     * - ip
     * - createdAt
     */
    private boolean verifyAccessTokenIpBinding(String accessToken, String clientIp, String clientId) {
        String key = OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey(accessToken);
        Map<String, String> bindData = GlobalCache.field().getAll(key, String.class);
        if (bindData == null || bindData.isEmpty()) {
            // 未绑定视为不校验，兼容历史令牌或特殊场景
            return true;
        }
        String boundIp = bindData.get("ip");
        if (boundIp == null || boundIp.isEmpty()) {
            return true;
        }
        if (boundIp.equals(clientIp)) {
            return true;
        }
        log.warn("Access token 归属校验失败: clientId={}, boundIp={}, requestIp={}", clientId, boundIp, clientIp);
        return false;
    }

    /**
     * 返回 OAuth2.0 标准错误响应
     */
    private Mono<Void> returnOAuth2Error(ServerHttpResponse response, String error, String errorDescription) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        String errorJson = String.format(
                "{\"error\":\"%s\",\"error_description\":\"%s\",\"error_uri\":\"%s%s\"}",
                error, errorDescription, OAuth2Constants.ERROR_DOCS_BASE_URI, error);

        byte[] bytes = errorJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    /**
     * 记录访问被拒绝审计日志
     */
    private void recordAccessDenied(String clientId, String path, String method, String ip, String userAgent,
                                    String reason, String errorCode, String errorMsg) {
        try {
            auditService.auditAccessDenied(
                    clientId != null ? clientId : "unknown",
                    path,
                    method,
                    ip,
                    userAgent,
                    reason,
                    errorCode,
                    errorMsg
            );
        } catch (Exception e) {
            log.warn("记录审计日志失败", e);
        }
    }

    /**
     * 判断是否应该记录访问审计日志（高频场景可采样）
     */
    private boolean shouldAuditAccess(String path) {
        // 关键接口 100% 记录，其他接口可采样（当前实现为全部记录）
        // 如需采样，可改为：return isCriticalPath(path) || ThreadLocalRandom.current().nextInt(100) < 10;
        return true;
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
     * 从 Token 中提取 clientId（用于审计）
     */
    private String extractClientIdFromToken(String token) {
        if (StringUtils.isBlank(token) || !token.contains(".")) {
            return null;
        }
        try {
            return JwtUtils.getArgument(token, OAuth2Constants.JWT_CLAIM_CLIENT_ID);
        } catch (Exception e) {
            log.debug("提取 clientId 失败", e);
            return null;
        }
    }

    /**
     * 验证 Scope 权限
     * <p>
     * 验证流程：
     * 1. 根据请求路径和 HTTP 方法查找接口所需的 scope 列表
     * 2. 如果接口不需要 scope 验证，直接通过
     * 3. 从 token 中提取 scope 列表
     * 4. 验证 token 中的 scope 是否包含至少一个所需的 scope
     *
     * @param accessToken Access Token（JWT 格式）
     * @param path        请求路径
     * @param method      HTTP 方法
     * @param clientId    客户端ID（用于日志）
     * @param clientIp    客户端IP（用于日志）
     * @param userAgent   用户代理（用于日志）
     * @return true 如果验证通过，false 否则
     */
    private boolean verifyScopePermission(String accessToken, String path, String method,
                                          String clientId, String clientIp, String userAgent) {
        // 1. 查找接口所需的 scope 列表
        List<String> requiredScopes = scopeResolver.getRequiredScopes(path, method);

        // 2. 如果接口不需要 scope 验证，直接通过
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            log.debug("接口不需要 scope 验证: path={}, method={}", path, method);
            return true;
        }

        // 3. 从 token 中提取 scope 列表
        Set<String> tokenScopes = scopeResolver.extractScopesFromToken(accessToken);
        if (tokenScopes == null || tokenScopes.isEmpty()) {
            log.warn("Token 中未包含 scope，但接口需要 scope 验证: path={}, method={}, requiredScopes={}",
                    path, method, requiredScopes);
            return false;
        }

        // 4. 验证 token 中的 scope 是否包含至少一个所需的 scope
        boolean hasRequiredScope = scopeResolver.verifyScope(tokenScopes, requiredScopes);
        if (!hasRequiredScope) {
            log.warn("Scope 权限验证失败: clientId={}, path={}, method={}, tokenScopes={}, requiredScopes={}",
                    clientId, path, method, tokenScopes, requiredScopes);
        }

        return hasRequiredScope;
    }
}
