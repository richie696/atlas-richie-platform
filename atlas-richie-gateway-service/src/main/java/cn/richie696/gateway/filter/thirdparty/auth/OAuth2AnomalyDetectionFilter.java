/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.gateway.filter.thirdparty.auth;

import cn.richie696.component.i18n.resolver.I18nResolver;
import cn.richie696.component.oauth.gateway.OAuthGatewayAdapter;
import cn.richie696.contract.gateway.model.OAuth2Constants;
import cn.richie696.gateway.config.GatewayConfig;
import cn.richie696.gateway.config.OAuth2AnomalyDetectionConfig;
import cn.richie696.gateway.constants.GatewayRedisKey;
import cn.richie696.gateway.filter.AbstractBaseFilter;
import cn.richie696.gateway.filter.FilterOrder;
import cn.richie696.gateway.filter.common.security.AnomalyDetectionFilter;
import cn.richie696.gateway.service.AuditService;
import cn.richie696.gateway.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import cn.richie696.component.cache.GlobalCache;

/**
 * Resource Server 侧的 OAuth 异常检测。
 *
 * <p>令牌签发、刷新、客户端限流和客户端配置读取已经移交 Authorization Server；
 * Gateway 这里只保留资源访问阶段的 token 重放观测，并继续复用通用异常检测。</p>
 */
@Slf4j
@Component
@ConditionalOnBean(OAuthGatewayAdapter.class)
public class OAuth2AnomalyDetectionFilter extends AbstractBaseFilter {

    private final AuditService auditService;
    private final OAuth2AnomalyDetectionConfig detectionConfig;
    private final AnomalyDetectionFilter commonAnomalyDetectionFilter;

    public OAuth2AnomalyDetectionFilter(GatewayConfig config,
                                        I18nResolver i18n,
                                        AuditService auditService,
                                        OAuth2AnomalyDetectionConfig detectionConfig,
                                        AnomalyDetectionFilter commonAnomalyDetectionFilter) {
        super(config, i18n);
        this.auditService = auditService;
        this.detectionConfig = detectionConfig;
        this.commonAnomalyDetectionFilter = commonAnomalyDetectionFilter;
    }

    @Override
    public int getOrder() {
        return FilterOrder.OAUTH2_ANOMALY_DETECTION_FILTER.getOrder();
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(OAuth2Constants.OAUTH2_BASE)) {
            return chain.filter(exchange);
        }

        String clientId = exchange.getRequest().getHeaders().getFirst(OAuthGatewayAdapter.CLIENT_ID_HEADER);
        if (StringUtils.isBlank(clientId)) {
            return chain.filter(exchange);
        }

        String token = extractBearer(exchange);
        String ip = NetworkUtils.getIP(exchange.getRequest());
        if (StringUtils.isNotBlank(token) && detectionConfig.getTokenReplay() != null) {
            detectTokenReplay(token, ip, clientId);
        }
        return chain.filter(exchange);
    }

    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getOauth2() != null && config.getOauth2().isEnabled() && detectionConfig.isEnabled();
    }

    private void detectTokenReplay(String token, String ip, String clientId) {
        OAuth2AnomalyDetectionConfig.TokenReplayConfig replay = detectionConfig.getTokenReplay();
        String tokenFingerprint = sha256(token);
        String key = GatewayRedisKey.OAUTH2_ANOMALY_TOKEN_IPS.getKey(tokenFingerprint);
        long ttl = TimeUnit.SECONDS.toMillis(replay.getTimeWindowSeconds());

        boolean ipExists = GlobalCache.collection().exists(key, ip);
        if (!ipExists) {
            if (!GlobalCache.key().hasKey(key)) {
                GlobalCache.collection().set(key, new HashSet<>(java.util.Set.of(ip)), ttl);
            } else {
                GlobalCache.collection().add(key, ip);
                GlobalCache.key().setExpiredTime(key, ttl);
            }
        }

        Long setSize = GlobalCache.collection().size(key);
        if (setSize != null && setSize > replay.getMaxIpsPerToken()) {
            log.warn("检测到 Token 重放攻击: fingerprint={}, ips={}, clientId={}", tokenFingerprint, setSize, clientId);
            auditService.auditSuspiciousActivity(clientId, ip, "TOKEN_REPLAY",
                    String.format("同一 token 从 %d 个不同 IP 使用", setSize));
        }
    }

    private String extractBearer(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(OAuth2Constants.HEADER_AUTHORIZATION);
        if (StringUtils.isBlank(authorization) || !authorization.startsWith(OAuth2Constants.BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(OAuth2Constants.BEARER_PREFIX.length()).trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算 Token 指纹", e);
        }
    }
}
