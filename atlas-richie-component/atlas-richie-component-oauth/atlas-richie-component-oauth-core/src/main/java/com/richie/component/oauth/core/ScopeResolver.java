package com.richie.component.oauth.core;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.context.utils.spring.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scope 路径解析器
 * <p>
 * 根据请求路径和 HTTP 方法，使用 Ant 路径匹配查找接口所需的 Scope。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
@Component
public class ScopeResolver {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public List<String> getRequiredScopes(String path, String method) {
        if (StringUtils.isBlank(path)) {
            return Collections.emptyList();
        }

        String httpMethod = StringUtils.upperCase(method);

        Set<String> apiCodes = GlobalCache.collection().get(OAuth2RedisKey.GATEWAY_API_INDEX.getKey(), String.class);
        if (apiCodes == null || apiCodes.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("未在 Redis 中找到任何网关接口配置，跳过 scope 校验: path={}, method={}", path, httpMethod);
            }
            return Collections.emptyList();
        }

        String bestApiCode = null;
        String bestPathPattern = null;
        int bestPatternScore = -1;

        for (String apiCode : apiCodes) {
            String apiConfigKey = OAuth2RedisKey.GATEWAY_API_CONFIG.getKey(apiCode);
            Map<String, String> apiConfig = GlobalCache.field().getAll(apiConfigKey, String.class);
            if (apiConfig == null || apiConfig.isEmpty()) {
                continue;
            }

            String enabled = apiConfig.getOrDefault("enabled", "true");
            if (!Boolean.parseBoolean(enabled)) {
                continue;
            }

            String pathPattern = apiConfig.get("pathPattern");
            if (StringUtils.isBlank(pathPattern)) {
                continue;
            }
            String apiMethod = StringUtils.defaultIfBlank(apiConfig.get("httpMethod"), "ALL").toUpperCase(Locale.ROOT);

            if (!"ALL".equals(apiMethod) && !apiMethod.equals(httpMethod)) {
                continue;
            }

            if (!pathMatcher.match(pathPattern, path)) {
                continue;
            }

            int score = pathPattern.length();
            if (score > bestPatternScore) {
                bestPatternScore = score;
                bestApiCode = apiCode;
                bestPathPattern = pathPattern;
            }
        }

        if (bestApiCode == null) {
            if (log.isDebugEnabled()) {
                log.debug("未找到匹配的网关接口配置，跳过 scope 校验: path={}, method={}", path, httpMethod);
            }
            return Collections.emptyList();
        }

        if (log.isDebugEnabled()) {
            log.debug("网关接口匹配成功，用于 scope 校验: apiCode={}, pathPattern={}, requestPath={}, method={}",
                    bestApiCode, bestPathPattern, path, httpMethod);
        }

        String apiConfigKey = OAuth2RedisKey.GATEWAY_API_CONFIG.getKey(bestApiCode);
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

        String scopesKey = OAuth2RedisKey.GATEWAY_API_SCOPES.getKey(bestApiCode);
        Set<String> scopeSet = GlobalCache.collection().get(scopesKey, String.class);
        if (scopeSet == null || scopeSet.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("接口未配置任何 scope，视为不需要 scope 校验: apiCode={}, pathPattern={}", bestApiCode, bestPathPattern);
            }
            return Collections.emptyList();
        }

        return new ArrayList<>(scopeSet);
    }

    public boolean verifyScope(Set<String> tokenScopes, List<String> requiredScopes) {
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true;
        }

        if (tokenScopes == null || tokenScopes.isEmpty()) {
            log.debug("Token 中未包含 scope，但接口需要 scope 验证");
            return false;
        }

        boolean hasRequiredScope = requiredScopes.stream()
                .anyMatch(tokenScopes::contains);

        if (!hasRequiredScope) {
            log.debug("Token scope 验证失败: tokenScopes={}, requiredScopes={}", tokenScopes, requiredScopes);
        }

        return hasRequiredScope;
    }

    public Set<String> extractScopesFromToken(String accessToken) {
        if (StringUtils.isBlank(accessToken) || !accessToken.contains(".")) {
            return Collections.emptySet();
        }

        try {
            String scopeStr = JwtUtils.getArgument(accessToken, OAuth2Constants.JWT_CLAIM_SCOPE);
            if (StringUtils.isBlank(scopeStr)) {
                return Collections.emptySet();
            }

            return Arrays.stream(scopeStr.split("\\s+"))
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.debug("从 Token 中提取 scope 失败", e);
            return Collections.emptySet();
        }
    }
}
