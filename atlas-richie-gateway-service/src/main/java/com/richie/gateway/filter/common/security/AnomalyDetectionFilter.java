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
package com.richie.gateway.filter.common.security;

import com.richie.contract.constant.GlobalConstants;
import com.richie.gateway.config.AnomalyDetectionConfig;
import com.richie.gateway.config.GatewayConfig;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.gateway.service.AuditService;
import com.richie.gateway.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 通用异常行为检测过滤器
 * <p>
 * 职责：
 * 1. 暴力破解检测（基于 userId/clientId 和 IP 的失败次数）
 * 2. 基于 userId/clientId 的限流
 * 3. 异常 IP 访问检测（新 IP、IP 频繁变化）
 * <p>
 * 说明：
 * - 此过滤器适用于所有公网接口，不限于 OAuth2.0
 * - 通过提取用户标识（userId/clientId）进行检测
 * - 用户标识提取策略可配置（从请求头、JWT token、路径参数等）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-18
 */
@Slf4j
@Component
public class AnomalyDetectionFilter extends AbstractBaseFilter {

    private final AnomalyDetectionConfig detectionConfig;
    private final AuditService auditService;


    /**
     * 构造函数
     *
     * @param config          网关配置
     * @param i18n            国际化解析器
     * @param detectionConfig 异常检测配置
     */
    public AnomalyDetectionFilter(GatewayConfig config, I18nResolver i18n,
                                  AnomalyDetectionConfig detectionConfig, AuditService auditService) {
        super(config, i18n);
        this.detectionConfig = detectionConfig;
        this.auditService = auditService;
    }

    @Override
    public int getOrder() {
        // 在安全过滤器之后执行
        return FilterOrder.ANOMALY_DETECTION_FILTER.getOrder();
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 如果异常检测未启用，直接放行
        if (!detectionConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String ip = NetworkUtils.getIP(request);

        // 提取用户标识（userId 或 clientId）
        String userId = extractUserId(request);

        // 1. 检测 userId 是否被封禁
        if (StringUtils.isNotBlank(userId) && isUserIdBanned(userId)) {
            log.warn("userId 已被封禁: userId={}, ip={}", userId, ip);
            return returnRateLimitError(response);
        }

        // 2. 检测异常 IP 访问
        if (StringUtils.isNotBlank(userId)) {
            detectAbnormalIp(userId, ip);
        }

        // 3. 检测基于 userId 的限流
        if (StringUtils.isNotBlank(userId)) {
            if (!checkRateLimit(userId, ip)) {
                return returnRateLimitError(response);
            }
        }

        return chain.filter(exchange);
    }

    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getSecurity().isEnable() && detectionConfig.isEnabled();
    }

    /**
     * 检测异常 IP 访问
     */
    private void detectAbnormalIp(String userId, String ip) {
        AnomalyDetectionConfig.AbnormalIpConfig config = detectionConfig.getAbnormalIp();
        String key = GatewayRedisKey.ANOMALY_USER_IPS.getKey(userId);
        long ttl = TimeUnit.SECONDS.toMillis(config.getTimeWindowSeconds());

        // 检查 IP 是否已存在
        boolean ipExists = GlobalCache.collection().exists(key, ip);

        // 如果 IP 不存在，则添加
        if (!ipExists) {
            // 首次创建时设置 TTL，否则直接添加并更新 TTL
            if (!GlobalCache.key().hasKey(key)) {
                GlobalCache.collection().set(key, new HashSet<>(Set.of(ip)), ttl);
            } else {
                GlobalCache.collection().add(key, ip);
                // 更新 TTL（确保 key 不会过期）
                GlobalCache.key().setExpiredTime(key, ttl);
            }
        }

        // 获取 Set 大小（用于检测 IP 变化次数）
        Long setSize = GlobalCache.collection().size(key);
        if (setSize != null && setSize > config.getMaxIpChangesPer10Min()) {
            log.warn("检测到异常 IP 访问: userId={}, ip={}, totalIps={}", userId, ip, setSize);
            // 记录可疑活动审计日志
            auditService.auditSuspiciousActivity(
                    userId, ip, "ABNORMAL_IP_ACCESS",
                    String.format("10 分钟内从 %d 个不同 IP 访问，超过阈值 %d", setSize, config.getMaxIpChangesPer10Min())
            );
        }
    }

    /**
     * 检测基于 userId 的限流
     */
    private boolean checkRateLimit(String userId, String ip) {
        AnomalyDetectionConfig.RateLimitConfig config = detectionConfig.getRateLimit();
        if (!config.isEnabled()) {
            return true;
        }

        // 获取限流阈值（使用默认值，业务可扩展）
        int rateLimit = config.getDefaultLimit();

        // 统计请求次数
        String key = GatewayRedisKey.ANOMALY_RATELIMIT.getKey(userId);
        long ttl = TimeUnit.SECONDS.toMillis(config.getTimeWindowSeconds());
        long count = GlobalCache.value().increment(key, 1L, ttl);

        // 检测是否超过阈值
        if (count > rateLimit) {
            log.warn("用户请求频率超限: userId={}, count={}, limit={}", userId, count, rateLimit);
            // 记录可疑活动审计日志
            auditService.auditSuspiciousActivity(
                    userId, ip, "RATE_LIMIT_EXCEEDED",
                    String.format("请求频率超限: %d 次/%d 秒，超过阈值 %d", count, config.getTimeWindowSeconds(), rateLimit)
            );
            return false;
        }

        return true;
    }

    /**
     * 从请求中提取用户标识（userId 或 clientId）
     * <p>
     * 提取策略（按优先级）：
     * 1. 从 OAuth2.0 Authorization: Bearer token 中提取 clientId（外部接口）
     * 2. 从系统内 X-Access-Token 中提取 userId（内部接口）
     * 3. 从路径参数中提取（如果配置了路径参数提取规则）
     */
    private String extractUserId(ServerHttpRequest request) {
        // 1. 从 OAuth2.0 Authorization: Bearer token 中提取 clientId（外部接口）
        String authorization = request.getHeaders().getFirst(OAuth2Constants.HEADER_AUTHORIZATION);
        if (StringUtils.isNotBlank(authorization) && authorization.startsWith(OAuth2Constants.BEARER_PREFIX)) {
            try {
                // 提取 Bearer token
                String token = authorization.substring(OAuth2Constants.BEARER_PREFIX.length()).trim();
                if (StringUtils.isNotBlank(token) && token.contains(".")) {
                    // 从 JWT token 中提取 clientId
                    String extractedClientId = JwtUtils.getArgument(token, OAuth2Constants.JWT_CLAIM_CLIENT_ID);
                    if (StringUtils.isNotBlank(extractedClientId)) {
                        return extractedClientId;
                    }
                }
            } catch (Exception e) {
                log.debug("从 OAuth2.0 Authorization token 提取 clientId 失败", e);
            }
        }

        // 2. 从系统内 X-Access-Token 中提取 userId（内部接口）
        String token = request.getHeaders().getFirst(GlobalConstants.X_ACCESS_TOKEN);
        if (StringUtils.isNotBlank(token)) {
            try {
                // 尝试从 JWT 中提取 userId
                String jwtUserId = JwtUtils.getUsername(token);
                if (StringUtils.isNotBlank(jwtUserId)) {
                    return jwtUserId;
                }
            } catch (Exception e) {
                log.debug("从 JWT token 提取 userId 失败", e);
            }
        }

        return null;
    }

    /**
     * 记录认证失败（用于暴力破解检测）
     * <p>
     * 此方法由业务过滤器调用，用于记录认证失败事件
     *
     * @param userId 用户标识（userId 或 clientId）
     * @param ip     IP 地址
     * @param success 是否成功
     */
    public void recordAuthResult(String userId, String ip, boolean success) {
        if (!detectionConfig.isEnabled()) {
            return;
        }

        detectBruteForce(userId, ip, success);
    }

    /**
     * 检测暴力破解攻击
     */
    private void detectBruteForce(String userId, String ip, boolean success) {
        if (success) {
            // 成功则清除失败计数
            clearFailureCount(userId, ip);
            return;
        }

        // 失败则增加计数
        AnomalyDetectionConfig.BruteForceConfig config = detectionConfig.getBruteForce();
        long ttl = TimeUnit.SECONDS.toMillis(config.getTimeWindowSeconds());

        // 统计 userId 失败次数
        String userFailureKey = GatewayRedisKey.ANOMALY_FAILURES_USER.getKey(userId);
        long userFailures = GlobalCache.value().increment(userFailureKey, 1L, ttl);

        // 统计 IP 失败次数
        String ipFailureKey = GatewayRedisKey.ANOMALY_FAILURES_IP.getKey(ip);
        long ipFailures = GlobalCache.value().increment(ipFailureKey, 1L, ttl);

        // 检测是否超过阈值
        if (userFailures > config.getMaxFailuresPerUser()) {
            log.warn("检测到暴力破解攻击（userId）: userId={}, failures={}", userId, userFailures);
            // 记录可疑活动审计日志
            auditService.auditSuspiciousActivity(
                    userId, ip, "BRUTE_FORCE_ATTACK",
                    String.format("用户认证失败次数超限: %d 次/%d 秒，超过阈值 %d，已临时封禁 %d 秒",
                            userFailures, config.getTimeWindowSeconds(), config.getMaxFailuresPerUser(), config.getBanDurationSeconds())
            );
            // 临时封禁 userId
            banUserId(userId, config.getBanDurationSeconds());
        }

        if (ipFailures > config.getMaxFailuresPerIp()) {
            log.warn("检测到暴力破解攻击（IP）: ip={}, failures={}", ip, ipFailures);
            // 记录可疑活动审计日志
            auditService.auditSuspiciousActivity(
                    null, ip, "BRUTE_FORCE_ATTACK",
                    String.format("IP 认证失败次数超限: %d 次/%d 秒，超过阈值 %d",
                            ipFailures, config.getTimeWindowSeconds(), config.getMaxFailuresPerIp())
            );
        }
    }

    /**
     * 清除失败计数
     */
    private void clearFailureCount(String userId, String ip) {
        if (StringUtils.isNotBlank(userId)) {
            String userFailureKey = GatewayRedisKey.ANOMALY_FAILURES_USER.getKey(userId);
            GlobalCache.key().removeCache(userFailureKey);
        }
        if (StringUtils.isNotBlank(ip)) {
            String ipFailureKey = GatewayRedisKey.ANOMALY_FAILURES_IP.getKey(ip);
            GlobalCache.key().removeCache(ipFailureKey);
        }
    }

    /**
     * 临时封禁 userId
     */
    private void banUserId(String userId, int banDurationSeconds) {
        String banKey = GatewayRedisKey.ANOMALY_BAN_USER.getKey(userId);
        long ttl = TimeUnit.SECONDS.toMillis(banDurationSeconds);
        GlobalCache.value().set(banKey, "1", ttl);
        log.info("临时封禁 userId: userId={}, duration={}秒", userId, banDurationSeconds);
    }

    /**
     * 检测 userId 是否被封禁
     */
    public boolean isUserIdBanned(String userId) {
        if (StringUtils.isBlank(userId)) {
            return false;
        }
        String banKey = GatewayRedisKey.ANOMALY_BAN_USER.getKey(userId);
        return GlobalCache.key().hasKey(banKey);
    }

    /**
     * 返回限流错误响应
     */
    private Mono<Void> returnRateLimitError(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS); // 429
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        String errorJson = "{\"error\":\"rate_limit_exceeded\",\"error_description\":\"请求频率超限，请稍后再试\"}";
        byte[] bytes = errorJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
