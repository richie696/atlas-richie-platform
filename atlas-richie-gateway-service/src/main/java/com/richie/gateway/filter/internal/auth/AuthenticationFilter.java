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
package com.richie.gateway.filter.internal.auth;

import com.richie.contract.constant.GlobalConstants;
import com.richie.gateway.config.GatewayConfig;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.util.HardwareFingerprintUtils;
import com.richie.gateway.utils.NetworkUtils;
import com.auth0.jwt.interfaces.Claim;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 授信接口过滤器
 *
 * @author richie696
 * @version 1.0
 * @since 2023-07-19 11:30:46
 */
@Slf4j
@Component
public class AuthenticationFilter extends AbstractBaseFilter {

    /**
     * 构造函数
     *
     * @param config 网关配置
     */
    public AuthenticationFilter(GatewayConfig config, I18nResolver i18n) {
        super(config, i18n);
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    public int getOrder() {
        return FilterOrder.AUTHENTICATION_FILTER.getOrder();
    }

    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpResponse response = exchange.getResponse();

        // 获取header中的Token信息
        String token = exchange.getRequest().getHeaders().getFirst(JwtUtils.X_ACCESS_TOKEN);
        if (StringUtils.isBlank(token) || "null".equalsIgnoreCase(token)) {
            log.info("JWT令牌为空，token：{}", token);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_1"));
        }
        Date expiredTime = JwtUtils.getExpiredTime(token);
        // 验证令牌是否过期
        if (expiredTime == null || expiredTime.getTime() < System.currentTimeMillis()) {
            log.info("JWT令牌已过期，token：{}", token);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_3"));
        }
        // 验证令牌是否在黑名单中
        if (GlobalCache.key().hasKey(config.getToken().getBlacklistPath() + token)) {
            log.info("JWT令牌已被加入黑名单，token：{}", token);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
        }
        // 认证JWT令牌有效性
        if (!JwtUtils.verify(token, config.getToken().getSecret())) {
            log.info("JWT令牌认证未通过，token：{}", token);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
        }

        // 验证Token与设备绑定（如果Token中包含deviceId，则必须与请求的设备ID匹配）
        String tokenDeviceId = JwtUtils.getArgument(token, "deviceId");
        if (StringUtils.isNotBlank(tokenDeviceId)) {
            String requestDeviceId = extractDeviceId(exchange);
            if (StringUtils.isBlank(requestDeviceId) || !tokenDeviceId.equals(requestDeviceId)) {
                log.warn("Token设备ID不匹配，token中的deviceId: {}, 请求中的deviceId: {}, token: {}",
                    tokenDeviceId, requestDeviceId, token);
                return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
            }
        }

        // 验证硬件指纹（增强安全：即使deviceId被盗，硬件指纹不匹配也会拒绝）
        HardwareFingerprintUtils.HardwareFingerprint tokenFingerprint = extractHardwareFingerprintFromToken(token);
        if (tokenFingerprint != null) {
            // 从请求头获取签名后的硬件指纹
            String signedFingerprint = extractSignedHardwareFingerprintFromRequest(exchange);
            if (StringUtils.isBlank(signedFingerprint)) {
                log.warn("Token中包含硬件指纹，但请求中未提供硬件指纹");
                return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
            }

            // 1. 分离签名和JSON部分
            HardwareFingerprintUtils.SignedFingerprintParts parts = HardwareFingerprintUtils.separateSignedFingerprint(signedFingerprint);
            if (parts == null) {
                log.warn("分离签名后的硬件指纹失败，格式错误");
                return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
            }

            // 2-4. 解析并验证签名和时间戳（综合方法）
            String hmacSecret = config.getHardwareFingerprint().getHmacSecret();
            long timestampValidDuration = config.getHardwareFingerprint().getTimestampValidDuration();
            HardwareFingerprintUtils.HardwareFingerprint requestFingerprint = HardwareFingerprintUtils
                    .parseAndVerifySignedFingerprint(parts.getJsonPart(), parts.getSignature(), hmacSecret, timestampValidDuration);
            if (requestFingerprint == null) {
                log.warn("硬件指纹签名验证或时间戳验证失败，拒绝请求");
                return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
            }

            // 5. 验证硬件指纹特征匹配（排除timestamp和nonce字段）
            if (!HardwareFingerprintUtils.verifyFingerprint(requestFingerprint, tokenFingerprint)) {
                log.warn("硬件指纹不匹配，拒绝请求。Token中的指纹: {}, 请求中的指纹: {}",
                    HardwareFingerprintUtils.serializeFingerprint(tokenFingerprint),
                    HardwareFingerprintUtils.serializeFingerprint(requestFingerprint));
                return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_2"));
            }
        }

        // 开启单点登录且重复登录校验
        if (config.getSso().isEnable() && repeatLogins(token)) {
            log.info("JWT令牌已被踢出，token：{}", token);
            return NetworkUtils.returnError(response, HttpStatus.UNAUTHORIZED, i18n.get("MSG_GATEWAY_TIP_8"));
        }
        long endTime = TimeUnit.MINUTES.toMillis(config.getToken().getExpirationRenewalTime()) + System.currentTimeMillis();
        // 判断是否需要续期
        if (expiredTime.getTime() < endTime) {
            // 续期
            long time = System.currentTimeMillis() + config.getToken().getExpireTimeMillis();
            token = JwtUtils.renewToken(token, config.getToken().getSecret(), time);
            String userKey = JwtUtils.getUserKey(token);
            Date expiredDate = JwtUtils.getExpiredTime(token);
            Optional.ofNullable(userKey).ifPresent(_ -> GlobalCache.key().expireAt(userKey, expiredDate));
            // redis需要记录这个续期的token
            if (config.getSso().isEnable()) {
                String lastOnlineTokenPath = getLastOnlineTokenPath(token);
                GlobalCache.collection().add(lastOnlineTokenPath, token);
            }
        }
        exchange.getResponse().getHeaders().set(JwtUtils.X_ACCESS_TOKEN, token);
        return chain.filter(exchange);
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getToken().isEnable() && config.getToken().getIgnoreUriList().stream().noneMatch(exchange.getRequest().getURI().getPath()::matches)
                && config.getToken().getLoginUriList().stream().noneMatch(exchange.getRequest().getURI().getPath()::matches);
    }

    /**
     * 判断是否重复登录
     *
     * @param token 本次访问令牌
     * @return 返回是否重复登录
     */
    private boolean repeatLogins(String token) {
        String lastOnlineTokenPath = getLastOnlineTokenPath(token);
        Set<String> lastOnlineTokens = GlobalCache.collection().get(lastOnlineTokenPath, String.class);
        return null != lastOnlineTokens && !lastOnlineTokens.contains(token);
    }

    /**
     * 获取最后一次在线token缓存路径
     *
     * @param token 本次访问令牌
     * @return 返回token缓存陆军
     */
    private String getLastOnlineTokenPath(String token) {
        String tenantId = JwtUtils.getArgument(token, "tenantId");
        String username = JwtUtils.getUsername(token);
        boolean isMobileToken = Boolean.parseBoolean(JwtUtils.getArgument(token, GlobalConstants.IS_MOBILE_TOKEN));
        String key = "";
        if (null != tenantId) {
            key = tenantId + "-";
        }
        key += username;
        if (isMobileToken) {
            key += "-" + GlobalConstants.IS_MOBILE_TOKEN;
        }
        return config.getSso().getOnlineTokenPath() + key;
    }

    /**
     * 从请求中提取设备ID
     * <p>
     * 设备ID可以从请求头或请求参数中获取
     *
     * @param exchange 请求交换器
     * @return 设备ID（可为null）
     */
    private String extractDeviceId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // 优先从请求头获取
        String deviceId = request.getHeaders().getFirst("X-Device-Id");
        if (StringUtils.isNotBlank(deviceId)) {
            return deviceId;
        }

        // 从请求参数获取
        deviceId = request.getQueryParams().getFirst("deviceId");
        if (StringUtils.isNotBlank(deviceId)) {
            return deviceId;
        }

        return null;
    }

    /**
     * 从 Token 中提取硬件指纹
     *
     * @param token JWT Token
     * @return 硬件指纹对象，如果不存在返回 null
     */
    private HardwareFingerprintUtils.HardwareFingerprint extractHardwareFingerprintFromToken(String token) {
        try {
            Map<String, Claim> claims = JwtUtils.getClaims(token);
            return HardwareFingerprintUtils.extractFingerprintFromClaims(claims);
        } catch (Exception e) {
            log.warn("从Token提取硬件指纹失败", e);
            return null;
        }
    }

    /**
     * 从请求中提取签名后的硬件指纹
     * <p>
     * 硬件指纹可以从请求头 "X-Hardware-Fingerprint" 中获取（格式：JSON.签名）。
     *
     * @param exchange 请求交换器
     * @return 签名后的硬件指纹字符串，如果不存在返回 null
     */
    private String extractSignedHardwareFingerprintFromRequest(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // 从请求头获取
        String signedFingerprint = request.getHeaders().getFirst("X-Hardware-Fingerprint");
        if (StringUtils.isBlank(signedFingerprint)) {
            // 从请求参数获取（备用方案）
            signedFingerprint = request.getQueryParams().getFirst("hardwareFingerprint");
        }

        return StringUtils.isBlank(signedFingerprint) ? null : signedFingerprint;
    }

    /**
     * 从请求中提取硬件指纹（已废弃，保留用于兼容）
     * <p>
     * 硬件指纹可以从请求头 "X-Hardware-Fingerprint" 中获取（JSON格式）。
     *
     * @param exchange 请求交换器
     * @return 硬件指纹对象，如果不存在返回 null
     * @deprecated 请使用 {@link #extractSignedHardwareFingerprintFromRequest(ServerWebExchange)} 获取签名后的指纹
     */
    @Deprecated
    private HardwareFingerprintUtils.HardwareFingerprint extractHardwareFingerprintFromRequest(ServerWebExchange exchange) {
        String signedFingerprint = extractSignedHardwareFingerprintFromRequest(exchange);
        if (StringUtils.isBlank(signedFingerprint)) {
            return null;
        }
        return HardwareFingerprintUtils.parseSignedFingerprint(signedFingerprint);
    }

}
