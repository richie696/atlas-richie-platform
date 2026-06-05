package com.richie.gateway.service.impl;

import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.gateway.service.OAuth2ScopeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OAuth2.0 Scope 权限服务实现（网关侧）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-XX
 */
@Slf4j
@Service
public class OAuth2ScopeServiceImpl implements OAuth2ScopeService {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public List<String> getRequiredScopes(String path, String method) {
        if (StringUtils.isBlank(path)) {
            return Collections.emptyList();
        }

        String httpMethod = StringUtils.upperCase(method);

        // 1. 从 Redis 中获取所有已注册的接口编码（apiCode）
        // Key: gateway:api:index，Value: Set<apiCode>
        Set<String> apiCodes = GlobalCache.collection().get(GatewayRedisKey.GATEWAY_API_INDEX.getKey(), String.class);
        if (apiCodes == null || apiCodes.isEmpty()) {
            // 未配置任何接口，认为不需要做 scope 校验（向后兼容）
            if (log.isDebugEnabled()) {
                log.debug("未在 Redis 中找到任何网关接口配置，跳过 scope 校验: path={}, method={}", path, httpMethod);
            }
            return Collections.emptyList();
        }

        String bestApiCode = null;
        String bestPathPattern = null;
        int bestPatternScore = -1;

        // 2. 遍历所有接口配置，使用 AntPathMatcher 进行路径匹配，选择最具体的匹配
        for (String apiCode : apiCodes) {
            String apiConfigKey = GatewayRedisKey.GATEWAY_API_CONFIG.getKey(apiCode);
            Map<String, String> apiConfig = GlobalCache.field().getAll(apiConfigKey, String.class);
            if (apiConfig == null || apiConfig.isEmpty()) {
                continue;
            }

            // 2.1 检查是否启用
            String enabled = apiConfig.getOrDefault("enabled", "true");
            if (!Boolean.parseBoolean(enabled)) {
                continue;
            }

            // 2.2 获取路径模式和方法
            String pathPattern = apiConfig.get("pathPattern");
            if (StringUtils.isBlank(pathPattern)) {
                continue;
            }
            String apiMethod = StringUtils.defaultIfBlank(apiConfig.get("httpMethod"), "ALL").toUpperCase(Locale.ROOT);

            // 2.3 方法不匹配则跳过（ALL 表示所有方法）
            if (!"ALL".equals(apiMethod) && !apiMethod.equals(httpMethod)) {
                continue;
            }

            // 2.4 使用 AntPathMatcher 进行路径匹配
            if (!pathMatcher.match(pathPattern, path)) {
                continue;
            }

            // 2.5 根据路径模式长度选择“最具体”的匹配（长度越长越具体）
            int score = pathPattern.length();
            if (score > bestPatternScore) {
                bestPatternScore = score;
                bestApiCode = apiCode;
                bestPathPattern = pathPattern;
            }
        }

        if (bestApiCode == null) {
            // 没有找到匹配的接口配置，认为不需要做 scope 校验（向后兼容）
            if (log.isDebugEnabled()) {
                log.debug("未找到匹配的网关接口配置，跳过 scope 校验: path={}, method={}", path, httpMethod);
            }
            return Collections.emptyList();
        }

        if (log.isDebugEnabled()) {
            log.debug("网关接口匹配成功，用于 scope 校验: apiCode={}, pathPattern={}, requestPath={}, method={}",
                    bestApiCode, bestPathPattern, path, httpMethod);
        }

        // 3. 检查是否需要做 scope 校验（requireScope 字段）
        String apiConfigKey = GatewayRedisKey.GATEWAY_API_CONFIG.getKey(bestApiCode);
        Map<String, String> bestApiConfig = GlobalCache.field().getAll(apiConfigKey, String.class);
        if (bestApiConfig == null || bestApiConfig.isEmpty()) {
            return Collections.emptyList();
        }
        String requireScope = bestApiConfig.getOrDefault("requireScope", "true");
        if (!Boolean.parseBoolean(requireScope)) {
            if (log.isDebugEnabled()) {
                log.debug("接口配置为不需要 scope 校验: apiCode={}, pathPattern={}", bestApiCode, bestPathPattern);
            }
            return Collections.emptyList();
        }

        // 4. 从 Redis 中获取该接口所需的 scope 列表
        String scopesKey = GatewayRedisKey.GATEWAY_API_SCOPES.getKey(bestApiCode);
        Set<String> scopeSet = GlobalCache.collection().get(scopesKey, String.class);
        if (scopeSet == null || scopeSet.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("接口未配置任何 scope，视为不需要 scope 校验: apiCode={}, pathPattern={}", bestApiCode, bestPathPattern);
            }
            return Collections.emptyList();
        }

        return new ArrayList<>(scopeSet);
    }

    @Override
    public boolean verifyScope(Set<String> tokenScopes, List<String> requiredScopes) {
        // 如果接口不需要 scope 验证，直接通过
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true;
        }

        // 如果 token 中没有 scope，拒绝访问
        if (tokenScopes == null || tokenScopes.isEmpty()) {
            log.debug("Token 中未包含 scope，但接口需要 scope 验证");
            return false;
        }

        // 验证 token 中的 scope 是否包含至少一个所需的 scope（OR 逻辑）
        // 即：tokenScopes ∩ requiredScopes ≠ ∅
        boolean hasRequiredScope = requiredScopes.stream()
                .anyMatch(tokenScopes::contains);

        if (!hasRequiredScope) {
            log.debug("Token scope 验证失败: tokenScopes={}, requiredScopes={}",
                    tokenScopes, requiredScopes);
        }

        return hasRequiredScope;
    }

    @Override
    public Set<String> extractScopesFromToken(String accessToken) {
        if (StringUtils.isBlank(accessToken) || !accessToken.contains(".")) {
            return Collections.emptySet();
        }

        try {
            // 从 JWT 中提取 scope claim
            String scopeStr = JwtUtils.getArgument(accessToken, OAuth2Constants.JWT_CLAIM_SCOPE);
            if (StringUtils.isBlank(scopeStr)) {
                return Collections.emptySet();
            }

            // scope 在 JWT 中是空格分隔的字符串，如 "read write admin"
            return Arrays.stream(scopeStr.split("\\s+"))
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.debug("从 Token 中提取 scope 失败", e);
            return Collections.emptySet();
        }
    }
}
