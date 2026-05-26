package com.richie.context.utils.spring;

import com.richie.contract.model.LoginUserPrincipal;
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
     * 该方法从token中解析出商户ID和用户名再加上密钥进行校验
     *
     * @param token  密钥
     * @param secret 令牌的密钥
     * @return 返回校验结果（true：验证通过，false：验证不通过）
     */
    public static boolean verify(@Nonnull String token, @Nonnull String secret) {
        var username = getUsername(token);
        var tenantCode = getTenantCode(token);
        if (tenantCode == null) {
            tenantCode = "";
        }
        if (username == null) {
            return false;
        }
        return verify(token, tenantCode, username, secret);
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
        return verify(token, "", username, secret);
    }

    /**
     * 验证JWT令牌是否有效的方法
     *
     * @param token      密钥
     * @param tenantCode 租户ID
     * @param username   用户名
     * @param secret     令牌的密钥
     * @return 返回校验结果（true：验证通过，false：验证不通过）
     */
    public static boolean verify(@Nonnull String token, @Nonnull String tenantCode, @Nonnull String username, @Nonnull String secret) {
        return verify(token, tenantCode, username, Map.of(), secret);
    }

    /**
     * 验证JWT令牌是否有效的方法
     *
     * @param token      密钥
     * @param tenantCode 租户ID
     * @param username   用户名
     * @param params     自定义扩展参数
     * @param secret     令牌的密钥
     * @return 返回校验结果（true：验证通过，false：验证不通过）
     */
    public static boolean verify(@Nonnull String token, @Nonnull String tenantCode, @Nonnull String username, Map<String, String> params,
                                 @Nonnull String secret) {
        try {
            // 根据密码生成JWT效验器
            var algorithm = Algorithm.HMAC256(secret);
            var verifier = JWT.require(algorithm)
                    .withClaim("username", username);
            if (StringUtils.isNotBlank(tenantCode)) {
                verifier.withClaim("tenantCode", tenantCode);
            }
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
     * 获得token中租户ID的方法
     * <p style="color: red">（注：如果令牌无效则返回null）
     *
     * @param token JWT令牌
     * @return 返回租户ID（如果获取不到则返回空）
     */
    @Nullable
    public static String getTenantCode(@Nonnull String token) {
        try {
            var jwt = JWT.decode(token);
            return jwt.getClaim("tenantCode").asString();
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
     * 获得token中租户账号到期时间的方法
     * <p style="color: red">（注：如果令牌无效或当前未启用租户功能则返回null）
     *
     * @param token JWT令牌
     * @return 返回租户ID（如果获取不到则返回空）
     */
    @Nullable
    public static OffsetDateTime getTenantExpiredTime(@Nonnull String token) {
        try {
            var jwt = JWT.decode(token);
            var time = jwt.getClaim("tenantExpiredTime").asDate();
            return time.toInstant().atOffset(ZoneOffset.UTC);
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
        return generateJwtToken(null, null, username, Map.of(), secret, expiredTime);
    }

    /**
     * 生成JWT令牌的方法
     *
     * @param tenantCode 租户ID
     * @param tenantExpiredTime 租户账号到期时间
     * @param username   用户名
     * @param secret     令牌的密钥
     * @param expiredTime 令牌过期时间
     * @return 返回创建的新令牌
     */
    public static String generateJwtToken(@Nonnull String tenantCode, @Nonnull OffsetDateTime tenantExpiredTime, @Nonnull String username, @Nonnull String secret, long expiredTime) {
        return generateJwtToken(tenantCode, tenantExpiredTime, username, Map.of(), secret, expiredTime);
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
        return generateJwtToken(userVO.getTenantCode(), userVO.getTenantExpiredTime(), userVO.getUsername(), userVO.getSignParams(), secret, expiredTime);
    }

    /**
     * 生成JWT令牌的方法
     *
     * @param tenantCode 租户ID
     * @param username   用户名
     * @param params     自定义扩展参数
     * @param secret     令牌的密钥
     * @return 返回创建的新令牌
     */
    private static String generateJwtToken(String tenantCode, OffsetDateTime tenantExpiredTime, String username, Map<String, String> params, String secret, long expiredTime) {
        var algorithm = Algorithm.HMAC256(secret);
        var builder = JWT.create()
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(expiredTime))
                .withJWTId(UUID.randomUUID().toString())
                .withIssuer("Richie Inc.")
                .withSubject("Interactive token")
                .withAudience(username);
        if (StringUtils.isNotBlank(tenantCode) && tenantExpiredTime != null) {
            builder
                    .withClaim("tenantCode", tenantCode)
                    .withClaim("tenantExpiredTime", Date.from(tenantExpiredTime.toInstant()));
        }
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

    /**
     * 根据request中的token获取用户账号
     *
     * @param request 请求对象
     * @return 返回用户名
     * @throws RuntimeException 当获取不到用户名时抛出该异常
     */
    public static String getTenantCodeByToken(HttpServletRequest request) throws RuntimeException {
        var accessToken = request.getHeader(X_ACCESS_TOKEN);
        var tenantCode = getTenantCode(accessToken);
        if (ObjectUtils.isEmpty(tenantCode)) {
            throw new RuntimeException("未获取到租户ID");
        }
        return tenantCode;
    }

}
