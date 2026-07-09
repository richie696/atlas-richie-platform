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
package com.richie.context.utils.spring;

import com.richie.contract.model.LoginUserPrincipal;
import com.richie.contract.model.TenantFeature;
import com.richie.contract.model.TenantPrincipal;
import com.richie.contract.constant.GlobalConstants;
import com.auth0.jwt.JWT;
import com.auth0.jwt.RegisteredClaims;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JWT令牌工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-10 12:33:17
 */
@SuppressWarnings("unused")
public final class JwtUtils {

    /**
     * JWT令牌的请求头名称
     */
    public static final String X_ACCESS_TOKEN = GlobalConstants.X_ACCESS_TOKEN;

    /**
     * Token有效期为1小时（Token在redis中缓存时间为两倍）
     */
    public static final long EXPIRE_TIME = TimeUnit.HOURS.toMillis(1);

    private JwtUtils() {
    }

    /**
     * 验证JWT令牌是否有效的方法
     *
     * @param token  密钥
     * @param secret 令牌的密钥
     * @return 返回校验结果（true：验证通过，false：验证不通过）
     */
    public static boolean verify(@Nonnull String token, @Nonnull String secret) {
        var username = getUsername(token);
        if (username == null) {
            return false;
        }
        return verify(token, username, secret);
    }

    /**
     * 验证JWT令牌是否有效的方法
     *
     * @param token    密钥
     * @param username 用户名
     * @param secret   令牌的密钥
     * @return 返回校验结果（true：验证通过，false：验证不通过）
     */
    public static boolean verify(@Nonnull String token, @Nonnull String username, @Nonnull String secret) {
        return verify(token, username, Map.of(), secret);
    }

    /**
     * 验证JWT令牌是否有效的方法
     *
     * @param token    密钥
     * @param username 用户名
     * @param params   自定义扩展参数
     * @param secret   令牌的密钥
     * @return 返回校验结果（true：验证通过，false：验证不通过）
     */
    public static boolean verify(@Nonnull String token, @Nonnull String username, Map<String, String> params,
                                 @Nonnull String secret) {
        try {
            // 根据密码生成JWT效验器
            var algorithm = Algorithm.HMAC256(secret);
            var verifier = JWT.require(algorithm)
                    .withClaim("username", username);
            if (Objects.nonNull(params)) {
                for (var entry : params.entrySet()) {
                    verifier.withClaim(entry.getKey(), entry.getValue());
                }
            }
            verifier.build().verify(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * 获得token中其他参数的方法
     * <p style="color: red">（注：如果令牌无效则返回null）
     *
     * @param token JWT令牌
     * @param key   Token中参数的KEY
     * @return 返回用户名（如果获取不到则返回空）
     */
    public static String getArgument(@Nonnull String token, @Nonnull String key) {
        try {
            var jwt = JWT.decode(token);
            return jwt.getClaim(key).asString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获得token中其他参数的方法
     * <p style="color: red">（注：如果令牌无效则返回null）
     *
     * @param token JWT令牌
     * @return 返回用户名（如果获取不到则返回空）
     */
    public static Date getExpiredTime(@Nonnull String token) {
        try {
            var jwt = JWT.decode(token);
            return jwt.getClaim(RegisteredClaims.EXPIRES_AT).asDate();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获得token中用户名的方法
     * <p style="color: red">（注：如果令牌无效则返回null）
     *
     * @param token JWT令牌
     * @return 返回用户名（如果获取不到则返回空）
     */
    @Nullable
    public static String getUsername(@Nonnull String token) {
        try {
            var jwt = JWT.decode(token);
            return jwt.getClaim("username").asString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取用户信息缓存Key的方法
     *
     * @param token JWT令牌
     * @return 返回用户信息缓存Key（如果获取不到则返回空）
     */
    @Nullable
    public static String getUserKey(@Nonnull String token) {
        try {
            var jwt = JWT.decode(token);
            var uk = jwt.getClaim(GlobalConstants.JWT_USER_KEY).asString();
            return StringUtils.isBlank(uk) ? null : new String(Base64.getDecoder().decode(uk));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成JWT令牌的方法
     *
     * @param username 用户名
     * @param secret   令牌的密钥
     * @param expiredTime 令牌过期时间
     * @return 返回创建的新令牌
     */
    public static String generateJwtToken(@Nonnull String username, @Nonnull String secret, long expiredTime) {
        return generateJwtToken(username, Map.of(), secret, expiredTime);
    }

    /**
     * 生成JWT令牌的方法
     *
     * @param userVO 用户信息
     * @param secret 令牌的密钥
     * @param expiredTime 令牌过期时间
     * @return 返回创建的新令牌
     */
    public static String generateJwtToken(@Nonnull LoginUserPrincipal userVO, @Nonnull String secret, long expiredTime) {
        Map<String, String> params = new HashMap<>(userVO.getSignParams());
        params.put("tenantEnabled", String.valueOf(userVO.isTenantEnabled() || TenantFeature.isEnabled()));
        return generateJwtToken(userVO.getUsername(), params, secret, expiredTime);
    }

    /**
     * 生成携带租户信息的JWT令牌的方法
     * <p>自动将 {@link TenantPrincipal} 中的 tenantId、tenantName、expiredTime
     * 填入JWT claims，使用者无需手动拼装参数Map。</p>
     *
     * @param username    用户名（JWT subject）
     * @param tenant      租户信息
     * @param secret      令牌的密钥
     * @param expiredTime 令牌过期时间
     * @return 返回创建的新令牌
     */
    public static String generateJwtToken(@Nonnull String username, @Nonnull TenantPrincipal tenant,
                                          @Nonnull String secret, long expiredTime) {
        Map<String, String> params = new HashMap<>();
        if (tenant.getTenantId() != null) {
            params.put("tenantId", String.valueOf(tenant.getTenantId()));
        }
        if (tenant.getTenantName() != null) {
            params.put("tenantName", tenant.getTenantName());
        }
        if (tenant.getExpiredTime() != null) {
            params.put("tenantExpiredTime", String.valueOf(tenant.getExpiredTime().toEpochSecond()));
        }
        return generateJwtToken(username, params, secret, expiredTime);
    }

    /**
     * 生成JWT令牌的方法
     */
    private static String generateJwtToken(String username, Map<String, String> params, String secret, long expiredTime) {
        var algorithm = Algorithm.HMAC256(secret);
        var builder = JWT.create()
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(expiredTime))
                .withJWTId(UUID.randomUUID().toString())
                .withIssuer("Richie Inc.")
                .withSubject("Interactive token")
                .withAudience(username);
        for (var entry : params.entrySet()) {
            builder.withClaim(entry.getKey(), entry.getValue());
        }
        return builder.sign(algorithm);
    }

    /**
     * 续签令牌的方法
     *
     * @param token       令牌
     * @param secret      密钥
     * @param expiredTime 过期时间
     * @return 返回续签后的令牌
     */
    public static String renewToken(@Nonnull String token, @Nonnull String secret, long expiredTime) {
        var jwt = JWT.decode(token);
        var claims = jwt.getClaims();
        Map<String, Object> params = new HashMap<>();
        for (var entry : claims.entrySet()) {
            params.put(entry.getKey(), entry.getValue().as(Object.class));
        }
        var algorithm = Algorithm.HMAC256(secret);
        var builder = JWT.create()
                .withPayload(params)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(expiredTime));
        return builder.sign(algorithm);
    }

    /**
     * 获取令牌中的参数的方法
     *
     * @param token 令牌
     * @return 返回令牌中的参数
     */
    public static Map<String, Claim> getClaims(@Nonnull String token) {
        var jwt = JWT.decode(token);
        return jwt.getClaims();
    }

    /**
     * 从JWT令牌中提取租户信息的方法
     * <p>从token中解析 tenantId、tenantName、tenantExpiredTime 三个claim，
     * 组装成 {@link TenantPrincipal} 对象返回。</p>
     * <p style="color: red">（注：如果令牌无效或不包含租户信息则返回null）</p>
     *
     * @param token JWT令牌
     * @return 返回租户主体信息（如果获取不到则返回空）
     */
    @Nullable
    public static TenantPrincipal getTenantPrincipal(@Nonnull String token) {
        try {
            var jwt = JWT.decode(token);
            String tid = jwt.getClaim("tenantId").asString();
            if (tid == null) {
                return null;
            }
            TenantPrincipal tenant = new TenantPrincipal()
                    .setTenantId(Long.parseLong(tid));
            String tname = jwt.getClaim("tenantName").asString();
            if (tname != null) {
                tenant.setTenantName(tname);
            }
            String expired = jwt.getClaim("tenantExpiredTime").asString();
            if (expired != null) {
                long epochSecond = Long.parseLong(expired);
                tenant.setExpiredTime(OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
            }
            return tenant;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据request中的token获取用户账号
     *
     * @param request 请求对象
     * @return 返回用户名
     * @throws RuntimeException 当获取不到用户名时抛出该异常
     */
    public static String getUsernameByToken(HttpServletRequest request) throws RuntimeException {
        var accessToken = request.getHeader(X_ACCESS_TOKEN);
        var username = getUsername(accessToken);
        if (ObjectUtils.isEmpty(username)) {
            throw new RuntimeException("未获取到用户名");
        }
        return username;
    }

}
