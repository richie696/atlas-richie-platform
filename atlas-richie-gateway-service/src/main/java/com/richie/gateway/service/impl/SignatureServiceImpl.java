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
package com.richie.gateway.service.impl;

import com.richie.gateway.service.SignatureService;
import com.richie.gateway.utils.MfaTokenUtils;
import com.richie.contract.model.LoginUserPrincipal;
import com.richie.contract.model.ApiResult;
import com.richie.gateway.config.GatewayConfig;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 部门基类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 17:05:13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureServiceImpl implements SignatureService {

    /**
     * 网关配置
     */
    protected final GatewayConfig config;

    /**
     * MFA Token 工具
     */
    private final MfaTokenUtils mfaTokenUtils;

    @Override
    public String createSignature(@Nonnull ApiResult<LoginUserPrincipal> result) {
        LoginUserPrincipal data = result.getData();
        String secret = config.getToken().getSecret();
        long expiredTime = System.currentTimeMillis() + config.getToken().getExpireTimeMillis();
        String token = JwtUtils.generateJwtToken(data, secret, expiredTime);
        data.getSignParams().clear();
        return token;
    }

    @Override
    public ApiResult<Void> invalidToken(String token) {
        if (!JwtUtils.verify(token, config.getToken().getSecret())) {
            return ApiResult.success();
        }
        Date expiredTime = JwtUtils.getExpiredTime(token);
        long expired = expiredTime.getTime() - System.currentTimeMillis();
        GlobalCache.value().set(config.getToken().getBlacklistPath() + token, JwtUtils.getUsername(token), expired);
        return ApiResult.success();
    }

    @Override
    public ApiResult<Void> logout(String accessToken, String mfaToken) {
        String userId = null;
        String tenantId = null;

        // 1. 普通 token 加入黑名单，并解析用户缓存 key
        if (StringUtils.isNotBlank(accessToken) && JwtUtils.verify(accessToken, config.getToken().getSecret())) {
            Date expiredTime = JwtUtils.getExpiredTime(accessToken);
            if (expiredTime != null) {
                long ttl = expiredTime.getTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    String blacklistKey = config.getToken().getBlacklistPath() + accessToken;
                    GlobalCache.value().set(blacklistKey, "1", ttl);
                    log.info("登出：普通 token 已加入黑名单，剩余有效期(ms)={}", ttl);
                }
            }
            userId = StringUtils.firstNonBlank(JwtUtils.getArgument(accessToken, "userId"), JwtUtils.getUsername(accessToken));
            tenantId = JwtUtils.getArgument(accessToken, "tenantId");
        }

        // 2. MFA 令牌加入黑名单，并补充用户缓存 key（若尚未从普通 token 解析到）
        if (StringUtils.isNotBlank(mfaToken) && mfaTokenUtils.isValidMfaToken(mfaToken)) {
            Date mfaExpiredTime = JwtUtils.getExpiredTime(mfaToken);
            if (mfaExpiredTime != null) {
                long ttl = mfaExpiredTime.getTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    String blacklistKey = config.getToken().getBlacklistPath() + mfaToken;
                    GlobalCache.value().set(blacklistKey, "1", ttl);
                    log.info("登出：MFA 令牌已加入黑名单，剩余有效期(ms)={}", ttl);
                }
            }
            if (StringUtils.isBlank(userId)) {
                userId = StringUtils.firstNonBlank(mfaTokenUtils.getUserIdFromMfaToken(mfaToken), mfaTokenUtils.getUsernameFromMfaToken(mfaToken));
            }
            if (StringUtils.isBlank(tenantId)) {
                tenantId = mfaTokenUtils.getTenantIdFromMfaToken(mfaToken);
            }
        }

        // 3. 移除用户缓存（与 IssueTokensFilter 中 storeUserInfoToCache 的 key 一致）
        if (StringUtils.isNotBlank(userId)) {
            String userCacheKey = StringUtils.isNotBlank(tenantId)
                ? "login:user:%s:%s".formatted(tenantId, userId)
                : "login:user:%s".formatted(userId);
            GlobalCache.key().removeCache(userCacheKey);
            log.debug("登出：已移除用户缓存，cacheKey={}", userCacheKey);
        }

        return ApiResult.success();
    }

    @Override
    public ApiResult<Void> notifyTenantExpired(String tenantCode) {
        return ApiResult.success();
    }
}
