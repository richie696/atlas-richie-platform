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
package com.richie.gateway.utils;

import com.richie.gateway.config.GatewayConfig;
import com.richie.context.utils.spring.JwtUtils;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MFA Token 工具类
 * <p>
 * 用于生成和解析 MFA 临时 Token（用于 MFA 验证流程中的临时凭证）
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaTokenUtils {

    private final GatewayConfig gatewayConfig;

    /**
     * MFA Token 有效期（5分钟）
     */
    private static final long MFA_TOKEN_EXPIRES_IN_MINUTES = 5;

    /**
     * 生成 MFA Token
     * <p>
     * MFA Token 是一个短期有效的 JWT Token，用于在 MFA 验证流程中临时存储用户信息
     *
     * @param userId   用户ID
     * @param tenantId 租户ID（可为null）
     * @param username 用户名
     * @return MFA Token
     */
    public String generateMfaToken(String userId, String tenantId, String username) {
        String tokenSecret = gatewayConfig.getToken().getSecret();
        if (StringUtils.isBlank(tokenSecret)) {
            throw new RuntimeException("Token 密钥未配置");
        }

        // 计算过期时间（5分钟后）
        long expiredTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(MFA_TOKEN_EXPIRES_IN_MINUTES);

        // 构建 JWT Claims
        Map<String, String> params = new HashMap<>();
        params.put("userId", userId);
        params.put("username", username);
        params.put("type", "MFA_TOKEN");
        if (StringUtils.isNotBlank(tenantId)) {
            params.put("tenantId", tenantId);
        }

        // 使用 JwtUtils 生成 Token（使用 username 作为 JWT 的 username 字段）
        return generateJwtTokenWithParams(username, params, tokenSecret, expiredTime);
    }

    /**
     * 解析 MFA Token，获取用户ID
     *
     * @param mfaToken MFA Token
     * @return 用户ID
     */
    public String getUserIdFromMfaToken(String mfaToken) {
        if (StringUtils.isBlank(mfaToken)) {
            return null;
        }
        return JwtUtils.getArgument(mfaToken, "userId");
    }

    /**
     * 解析 MFA Token，获取租户ID
     *
     * @param mfaToken MFA Token
     * @return 租户ID（可为null）
     */
    public String getTenantIdFromMfaToken(String mfaToken) {
        if (StringUtils.isBlank(mfaToken)) {
            return null;
        }
        return JwtUtils.getArgument(mfaToken, "tenantId");
    }

    /**
     * 解析 MFA Token，获取用户名
     *
     * @param mfaToken MFA Token
     * @return 用户名
     */
    public String getUsernameFromMfaToken(String mfaToken) {
        if (StringUtils.isBlank(mfaToken)) {
            return null;
        }
        return JwtUtils.getUsername(mfaToken);
    }

    /**
     * 验证 MFA Token 是否有效
     *
     * @param mfaToken MFA Token
     * @return true-有效，false-无效
     */
    public boolean isValidMfaToken(String mfaToken) {
        if (StringUtils.isBlank(mfaToken)) {
            return false;
        }

        // 验证 Token 签名
        String tokenSecret = gatewayConfig.getToken().getSecret();
        if (!JwtUtils.verify(mfaToken, tokenSecret)) {
            return false;
        }

        // 验证 Token 是否过期
        java.util.Date expiredTime = JwtUtils.getExpiredTime(mfaToken);
        if (expiredTime == null || expiredTime.getTime() < System.currentTimeMillis()) {
            return false;
        }

        // 验证 Token 类型
        String type = JwtUtils.getArgument(mfaToken, "type");
        return "MFA_TOKEN".equals(type);
    }

    /**
     * 使用 JWT 库生成 Token（与 JwtUtils 保持一致的风格）
     *
     * @param username   用户名（作为 JWT 的 username 字段）
     * @param params     自定义参数
     * @param secret     密钥
     * @param expiredTime 过期时间（时间戳，毫秒）
     * @return JWT Token
     */
    private String generateJwtTokenWithParams(String username, Map<String, String> params, String secret, long expiredTime) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTCreator.Builder builder = JWT.create()
                .withSubject(username)
                .withExpiresAt(new Date(expiredTime))
                .withIssuedAt(new Date());

        // 添加自定义参数
        if (params != null) {
            params.forEach(builder::withClaim);
        }

        return builder.sign(algorithm);
    }
}
