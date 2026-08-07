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
package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.core.spi.ScopePolicyRepository;
import cn.richie696.component.oauth.core.support.GlobalCacheScopePolicyRepository;
import cn.richie696.context.utils.spring.JwtUtils;
import cn.richie696.component.oauth.contract.OAuth2Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 网关接口到 Scope 需求的解析与校验器。
 * <p>
 * 职责分为两段:(1) 通过 Ant 路径匹配 + HTTP 方法,结合 {@link ScopePolicyRepository} 解析出指定
 * 接口需要的 Scope 集合;(2) 从 Access Token 的 {@code scope} claim 中提取 Scope 集合,与接口
 * 要求做包含关系校验。
 * </p>
 * <p>
 * 处于网关层与 Resource Server 共用的能力位置:位于网关拦截器/Resource Server 鉴权流程内部,
 * 不直接依赖 Redis,通过 {@link ScopePolicyRepository} 抽象获取策略数据;下游是
 * {@link ClientRegistry} 与 Token 端点,只关心接口与 Scope 的映射。
 * </p>
 * <p>
 * 解决的问题:统一回答"业务接口需要哪些 Scope、当前 Token 是否拥有这些 Scope",避免每个接口硬编码
 * Scope 字符串;同时把接口-策略配置从代码中剥离到 Redis 策略库,运维可热更新。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class ScopeResolver {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ScopePolicyRepository policyRepository;

    public ScopeResolver() {
        this(new GlobalCacheScopePolicyRepository());
    }

    public ScopeResolver(ScopePolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public List<String> getRequiredScopes(String path, String method) {
        if (StringUtils.isBlank(path)) {
            return Collections.emptyList();
        }

        String httpMethod = StringUtils.upperCase(method);

        Set<String> apiCodes = policyRepository.apiCodes();
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
            Map<String, String> apiConfig = policyRepository.apiConfig(apiCode);
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

        Map<String, String> bestApiConfig = policyRepository.apiConfig(bestApiCode);
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

        Set<String> scopeSet = policyRepository.requiredScopes(bestApiCode);
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
