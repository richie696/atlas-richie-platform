package com.richie.gateway.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.richie.contract.exception.BusinessException;
import com.richie.context.utils.data.JsonUtils;
import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.config.IOAuthFilterConfig;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.redis.manage.CacheLock;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.gateway.service.OAuth2AuthService;
import com.richie.gateway.service.OAuth2ClientService;
import com.richie.gateway.utils.NetworkUtils;
import com.richie.gateway.vo.OAuth2ErrorResponseVO;
import com.richie.gateway.vo.OAuth2IntrospectionResponseVO;
import com.richie.gateway.vo.OAuth2SimpleResponseVO;
import com.richie.gateway.vo.OAuth2TokenResponseVO;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2.0 认证服务实现
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2AuthServiceImpl implements OAuth2AuthService {

    private final OAuth2ClientService clientService;
    private final GatewayConfig gatewayConfig;

    @Override
    public OAuth2TokenResponseVO generateToken(String clientId, String clientSecret, String ip) {
        // 1. 验证客户端
        if (!clientService.verifyClientSecret(clientId, clientSecret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端认证失败");
        }

        // 2. 获取客户端配置
        ThirdPartyClientConfigVO config = loadClientConfig(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已被禁用");
        }

        IOAuthFilterConfig oauthConfig = gatewayConfig.getInterfaceAuth();

        // 3. 如启用每日签发次数限制，先检查当日调用次数
        if (oauthConfig != null && oauthConfig.isEnableDailyIssueLimit()) {
            enforceDailyIssueLimit(clientId, config, oauthConfig);
        }

        // 4. 如果启用了立即作废功能，先作废该客户端之前的令牌
        if (oauthConfig != null && oauthConfig.isRevokePreviousTokensOnIssue()) {
            revokePreviousTokensForClient(clientId);
        }

        // 5. 使用客户端注册的全部 scope（由系统授权，调用方不能自定义）
        List<String> finalScopes = config.getScopes() != null ? config.getScopes() : Collections.emptyList();
        if (finalScopes.isEmpty()) {
            log.warn("客户端未配置任何权限范围: clientId={}", clientId);
        }

        // 6. 生成 access_token（JWT 格式，使用客户端注册的全部 scope）
        String accessToken = generateAccessToken(clientId, config, finalScopes);

        // 7. 生成 refresh_token
        String refreshToken = generateRefreshToken();

        // 8. 存储 refresh_token 到 Redis（绑定 IP）
        storeRefreshToken(refreshToken, clientId, ip, config);

        // 9. 计算 access_token 过期时间（秒）
        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L
                : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;

        // 10. 绑定 access_token 与 IP（TTL 与 access_token 一致）
        long accessTokenTtlMillis = expiresIn * 1000L;
        bindAccessTokenIp(accessToken, clientId, ip, accessTokenTtlMillis);

        // 11. 构建响应（scope 已包含在 JWT 中，此处不再返回）
        return OAuth2TokenResponseVO.builder()
                .accessToken(accessToken)
                .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                .expiresIn(expiresIn)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public OAuth2TokenResponseVO refreshToken(String refreshToken, String ip) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌不能为空");
        }

        String lockKey = GatewayRedisKey.OAUTH2_REFRESH_TOKEN_LOCK.getKey(refreshToken);

        // 1. 获取分布式锁（5秒超时，防止并发刷新）
        try (CacheLock lock = GlobalCache.lock().optimisticWithRenewal(lockKey, 5L)) {
            if (!lock.isSuccess()) {
                throw new BusinessException(OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED,
                        "刷新令牌正在处理中，请稍后重试");
            }

            // 2. 从 Redis 读取 refresh_token
            String refreshTokenKey = GatewayRedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken);
            Map<String, String> tokenData = GlobalCache.field().getAll(refreshTokenKey, String.class);

            if (tokenData == null || tokenData.isEmpty()) {
                throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌无效或已使用");
            }

            // 3. 校验刷新令牌绑定的 IP（如果存在）
            String boundIp = tokenData.get("ip");
            if (StringUtils.isNotBlank(boundIp) && !boundIp.equals(ip)) {
                log.warn("刷新令牌绑定 IP 不匹配: boundIp={}, currentIp={}", boundIp, ip);
                throw new BusinessException(OAuth2Constants.ERROR_IP_NOT_ALLOWED, "刷新令牌绑定 IP 不匹配");
            }

            // 4. 删除旧的 refresh_token（包含 IP 绑定信息）
            GlobalCache.key().removeCache(refreshTokenKey);

            // 5. 验证客户端状态
            String clientId = tokenData.get(OAuth2Constants.JWT_CLAIM_CLIENT_ID);
            ThirdPartyClientConfigVO config = loadClientConfig(clientId);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已被禁用");
            }

            // 6. 生成新的 token（使用客户端注册的全部 scope，刷新时不再限制 scope）
            List<String> refreshScopes = config.getScopes() != null ? config.getScopes() : Collections.emptyList();
            String newAccessToken = generateAccessToken(clientId, config, refreshScopes);
            String newRefreshToken = generateRefreshToken();

            // 7. 存储新的 refresh_token（绑定当前 IP）
            // 注意：刷新 token 时，旧的 refresh_token 已经在步骤 4 中删除，索引也会自动更新
            storeRefreshToken(newRefreshToken, clientId, ip, config);

            // 8. 计算新的 access_token 过期时间（秒）
            long expiresIn = config.getTokenValidDuration() != null
                    ? config.getTokenValidDuration() * 3600L
                    : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;

            // 9. 绑定新的 access_token 与 IP
            long accessTokenTtlMillis = expiresIn * 1000L;
            bindAccessTokenIp(newAccessToken, clientId, ip, accessTokenTtlMillis);

            // 10. 构建响应（scope 已包含在 JWT 中，此处不再返回）
            return OAuth2TokenResponseVO.builder()
                    .accessToken(newAccessToken)
                    .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                    .expiresIn(expiresIn)
                    .refreshToken(newRefreshToken)
                    .build();
        }
    }

    @Override
    public ThirdPartyClientConfigVO verifyAccessToken(String accessToken) {
        String clientId = validateAccessToken(accessToken);
        if (StringUtils.isBlank(clientId)) {
            return null;
        }

        // 5. 验证客户端状态（只读取此链路必需字段）
        ThirdPartyClientConfigVO config = loadClientConfigForTokenVerification(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            log.debug("客户端不存在或已禁用: clientId={}", clientId);
            return null;
        }

        return config;
    }

    @Override
    public List<String> getIpWhitelist(String accessToken) {
        String clientId = validateAccessToken(accessToken);
        if (StringUtils.isBlank(clientId)) {
            return null;
        }

        List<String> ipWhitelist = getEnabledClientIpWhitelist(clientId);
        if (ipWhitelist == null) {
            log.debug("客户端不存在或已禁用: clientId={}", clientId);
            return null;
        }
        return ipWhitelist;
    }

    private String validateAccessToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return null;
        }

        String tokenSecret = gatewayConfig.getInterfaceAuth().getTokenSecret();
        if (StringUtils.isBlank(tokenSecret)) {
            log.warn("第三方系统 Token 密钥未配置");
            return null;
        }
        if (!JwtUtils.verify(accessToken, tokenSecret)) {
            log.debug("Access token 签名验证失败");
            return null;
        }

        String blacklistKey = GatewayRedisKey.OAUTH2_ACCESS_TOKEN_BLACKLIST.getKey(accessToken);
        if (GlobalCache.key().hasKey(blacklistKey)) {
            log.debug("Access token 已被拉入黑名单，拒绝访问");
            return null;
        }

        Date expiredTime = JwtUtils.getExpiredTime(accessToken);
        if (expiredTime == null || expiredTime.getTime() < System.currentTimeMillis()) {
            log.debug("Access token 已过期");
            return null;
        }

        String clientId = JwtUtils.getArgument(accessToken, OAuth2Constants.JWT_CLAIM_CLIENT_ID);
        if (StringUtils.isBlank(clientId)) {
            log.debug("Access token 中未找到 clientId");
            return null;
        }
        return clientId;
    }

    @Override
    public void revokeToken(String token, String tokenTypeHint) {
        if (StringUtils.isBlank(token)) {
            return;
        }

        // 判断 token 类型
        boolean isRefreshToken = OAuth2Constants.GRANT_TYPE_REFRESH_TOKEN.equals(tokenTypeHint);
        if (!isRefreshToken) {
            // 尝试从 token 格式判断（JWT 格式为 access_token，随机字符串为 refresh_token）
            isRefreshToken = !token.contains(".");
        }

        if (isRefreshToken) {
            // 删除 refresh_token（包含 IP 绑定信息）
            String refreshTokenKey = GatewayRedisKey.OAUTH2_REFRESH_TOKEN.getKey(token);
            GlobalCache.key().removeCache(refreshTokenKey);
            log.info("撤销 refresh_token: {}", token);
        } else {
            // access_token 是 JWT，无法直接物理删除，改为加入黑名单，黑名单过期时间与 token 本身一致
            String tokenSecret = gatewayConfig.getInterfaceAuth().getTokenSecret();
            if (StringUtils.isBlank(tokenSecret)) {
                log.warn("撤销 access_token 失败，Token 密钥未配置");
                return;
            }

            // 只有签名有效的 token 才需要加入黑名单
            if (!JwtUtils.verify(token, tokenSecret)) {
                log.info("撤销 access_token: token 无效或签名错误，直接忽略");
                return;
            }

            Date expiredTime = JwtUtils.getExpiredTime(token);
            if (expiredTime == null) {
                log.info("撤销 access_token: 无过期时间，直接忽略");
                return;
            }

            long now = System.currentTimeMillis();
            long ttlMillis = expiredTime.getTime() - now;
            if (ttlMillis <= 0) {
                log.info("撤销 access_token: 已过期，无需加入黑名单");
                return;
            }

            String blacklistKey = GatewayRedisKey.OAUTH2_ACCESS_TOKEN_BLACKLIST.getKey(token);
            GlobalCache.value().set(blacklistKey, "1", ttlMillis);

            // 同时删除 access_token 与 IP 的绑定关系
            String bindKey = GatewayRedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey(token);
            GlobalCache.key().removeCache(bindKey);

            log.info("撤销 access_token: 已加入黑名单并移除 IP 绑定，剩余有效期(ms)={}", ttlMillis);
        }
    }

    /**
     * 生成 JWT access_token
     *
     * @param clientId     客户端ID
     * @param config       客户端配置
     * @param finalScopes   最终确定的 scope 列表（已通过验证）
     */
    private String generateAccessToken(String clientId, ThirdPartyClientConfigVO config, List<String> finalScopes) {
        String tokenSecret = gatewayConfig.getInterfaceAuth().getTokenSecret();
        if (StringUtils.isBlank(tokenSecret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CONFIG, "Token 密钥未配置");
        }

        // 计算过期时间
        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L * 1000L
                : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN * 1000L;
        long expiredTime = System.currentTimeMillis() + expiresIn;

        // 使用 JwtUtils 生成 token，使用 clientId 作为 username
        // 并通过 getArgument 方法可以获取 clientId
        // 注意：JwtUtils.generateJwtToken 需要 username，我们使用 clientId 作为 username
        // 同时通过自定义参数添加 clientId 和 type 字段
        Map<String, String> params = new HashMap<>();
        params.put(OAuth2Constants.JWT_CLAIM_CLIENT_ID, clientId);
        params.put(OAuth2Constants.JWT_CLAIM_TYPE, OAuth2Constants.JWT_CLAIM_TYPE_THIRD_PARTY);
        // 使用最终确定的 scope（而不是客户端注册的全部 scope）
        if (finalScopes != null && !finalScopes.isEmpty()) {
            params.put(OAuth2Constants.JWT_CLAIM_SCOPE, String.join(" ", finalScopes));
        }

        // 使用 JwtUtils 的私有方法需要反射，为了简化，我们直接使用 JWT 库生成 token
        // 保持与 JwtUtils 相同的风格
        return generateJwtTokenWithParams(clientId, params, tokenSecret, expiredTime);
    }

    /**
     * 使用 JWT 库生成 token（与 JwtUtils 保持一致的风格）
     * <p>
     * 注意：生成的 token 必须包含 "username" 字段（使用 clientId），
     * 以便 JwtUtils.verify 方法能够正确验证
     */
    private String generateJwtTokenWithParams(String username, Map<String, String> params, String secret, long expiredTime) {
        Algorithm algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(secret);
        JWTCreator.Builder builder = JWT.create()
                .withClaim(OAuth2Constants.JWT_CLAIM_USERNAME, username)  // JwtUtils.verify 需要此字段
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(expiredTime))
                .withJWTId(UUID.randomUUID().toString())
                .withIssuer("Richie Inc.")
                .withSubject(OAuth2Constants.JWT_SUBJECT_THIRD_PARTY_ACCESS_TOKEN)
                .withAudience(username);

        // 添加自定义参数
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.withClaim(entry.getKey(), entry.getValue());
            }
        }

        return builder.sign(algorithm);
    }

    /**
     * 生成 refresh_token（随机字符串）
     */
    private String generateRefreshToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[48]; // 48字节 = 64字符（Base64）
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 存储 refresh_token 到 Redis
     */
    private void storeRefreshToken(String refreshToken, String clientId, String ip, ThirdPartyClientConfigVO config) {
        String refreshTokenKey = GatewayRedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken);

        // 构建存储数据
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put(OAuth2Constants.JWT_CLAIM_CLIENT_ID, clientId);
        tokenData.put("createdAt", String.valueOf(System.currentTimeMillis()));
        if (StringUtils.isNotBlank(ip)) {
            tokenData.put("ip", ip);
        }

        // 计算过期时间
        long expiresIn = config.getRefreshTokenValidDuration() != null
                ? config.getRefreshTokenValidDuration() * 3600L * 1000L
                : OAuth2Constants.DEFAULT_REFRESH_TOKEN_EXPIRES_IN * 1000L;

        // 存储到 Redis Hash
        GlobalCache.struct().set(refreshTokenKey, tokenData, expiresIn);

        // 维护客户端 Refresh Token 索引（用于立即作废功能）
        String clientRefreshTokenIndexKey = GatewayRedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId);
        GlobalCache.value().set(clientRefreshTokenIndexKey, refreshToken, expiresIn);
    }

    /**
     * 作废客户端之前的令牌（用于立即作废功能）
     * <p>
     * 此方法会：
     * 1. 查找该客户端之前的 refresh_token 索引
     * 2. 删除旧的 refresh_token 记录
     * 3. 删除客户端索引本身
     *
     * @param clientId 客户端ID
     */
    private void revokePreviousTokensForClient(String clientId) {
        String clientRefreshTokenIndexKey = GatewayRedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId);
        String previousRefreshToken = GlobalCache.value().get(clientRefreshTokenIndexKey, String.class);

        if (StringUtils.isBlank(previousRefreshToken)) {
            // 没有之前的 refresh_token，无需作废
            return;
        }

        log.info("立即作废功能：作废客户端之前的 refresh_token, clientId={}, previousRefreshToken={}", clientId, previousRefreshToken);

        // 1. 删除旧的 refresh_token 记录
        String refreshTokenKey = GatewayRedisKey.OAUTH2_REFRESH_TOKEN.getKey(previousRefreshToken);
        GlobalCache.key().removeCache(refreshTokenKey);

        // 2. 删除客户端索引本身（防止重复作废）
        GlobalCache.key().removeCache(clientRefreshTokenIndexKey);
    }

    /**
     * 按日限制签发令牌接口的调用次数
     *
     * @param clientId    客户端ID
     * @param config      客户端配置
     * @param oauthConfig 网关 OAuth2 配置
     */
    private void enforceDailyIssueLimit(String clientId, ThirdPartyClientConfigVO config, IOAuthFilterConfig oauthConfig) {
        // 计算每日最大签发次数：与 access_token 有效期成反比
        int tokenHours = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration()
                : (oauthConfig.getDefaultTokenValidDuration() != null ? oauthConfig.getDefaultTokenValidDuration() : 1);
        if (tokenHours <= 0) {
            tokenHours = 1;
        }
        // 简单规则：每天最多 24 / tokenHours 次，至少 1 次，然后在此基础上额外允许 +2 次
        int base = 24 / tokenHours;
        if (base <= 0) {
            base = 1;
        }
        int maxIssuesPerDay = base + 2;

        // 构造按天计数的 Key：oauth2:daily:issue-count:{clientId}:{yyyyMMdd}
        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        String counterKey = GatewayRedisKey.OAUTH2_DAILY_TOKEN_ISSUE_COUNT.getKey(clientId + ":" + today);

        // 计数 +1，TTL 设置为 24 小时
        long currentCount = GlobalCache.value().increment(counterKey, 1L, TimeUnit.DAYS.toMillis(1));

        if (currentCount > maxIssuesPerDay) {
            log.warn("客户端当日签发次数已达上限, clientId={}, count={}, limit={}", clientId, currentCount, maxIssuesPerDay);
            throw new BusinessException(OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED,
                    "当日签发令牌次数已达上限，请稍后再试");
        }
    }

    @Override
    public Mono<ResponseEntity<?>> requestToken(ServerWebExchange exchange) {
        String ip = NetworkUtils.getIP(exchange.getRequest());
        return parseFormBody(exchange)
                .flatMap(formData -> {
                    String grantType = formData.getFirst("grant_type");
                    String refreshToken = formData.getFirst("refresh_token");

                    if (StringUtils.isBlank(grantType)) {
                        return Mono.just(ResponseEntity
                                .status(getHttpStatus(OAuth2Constants.ERROR_INVALID_REQUEST))
                                .body(createErrorResponse(OAuth2Constants.ERROR_INVALID_REQUEST, "grant_type 参数缺失")));
                    }

                    // 从 HTTP Basic Auth 读取 client_id 和 client_secret
                    // 注意：refresh_token grant type 不需要客户端凭据，因为 refresh_token 本身已包含客户端信息
                    String authHeader = exchange.getRequest().getHeaders().getFirst(OAuth2Constants.HEADER_AUTHORIZATION);
                    String clientId = null;
                    String clientSecret = null;

                    // 只有 client_credentials grant type 需要 HTTP Basic Auth
                    if (OAuth2Constants.GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)) {
                        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith("Basic ")) {
                            return Mono.just(ResponseEntity
                                    .status(getHttpStatus(OAuth2Constants.ERROR_INVALID_REQUEST))
                                    .body(createErrorResponse(OAuth2Constants.ERROR_INVALID_REQUEST,
                                            "必须通过 HTTP Basic Auth 提供 client_id 和 client_secret")));
                        }

                        try {
                            String base64Credentials = authHeader.substring(6);
                            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
                            String[] parts = credentials.split(":", 2);
                            if (parts.length != 2 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1])) {
                                return Mono.just(ResponseEntity
                                        .status(getHttpStatus(OAuth2Constants.ERROR_INVALID_REQUEST))
                                        .body(createErrorResponse(OAuth2Constants.ERROR_INVALID_REQUEST,
                                                "HTTP Basic Auth 格式错误，应为 base64(client_id:client_secret)")));
                            }
                            clientId = parts[0];
                            clientSecret = parts[1];
                            log.debug("从 HTTP Basic Auth 读取客户端凭据: clientId={}", clientId);
                        } catch (IllegalArgumentException e) {
                            log.warn("解析 HTTP Basic Auth 失败: {}", e.getMessage());
                            return Mono.just(ResponseEntity
                                    .status(getHttpStatus(OAuth2Constants.ERROR_INVALID_REQUEST))
                                    .body(createErrorResponse(OAuth2Constants.ERROR_INVALID_REQUEST,
                                            "HTTP Basic Auth 解析失败: " + e.getMessage())));
                        }
                    }

                    // 根据 grant_type 处理请求
                    try {
                        OAuth2TokenResponseVO response;
                        switch (grantType) {
                            case OAuth2Constants.GRANT_TYPE_CLIENT_CREDENTIALS:
                                if (StringUtils.isBlank(clientId)) {
                                    throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "client_id 缺失");
                                }
                                if (StringUtils.isBlank(clientSecret)) {
                                    throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "client_secret 缺失");
                                }
                                log.info("第三方系统请求 access_token: clientId={}, ip={}", clientId, ip);
                                response = generateToken(clientId, clientSecret, ip);
                                break;
                            case OAuth2Constants.GRANT_TYPE_REFRESH_TOKEN:
                                if (StringUtils.isBlank(refreshToken)) {
                                    throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "refresh_token 参数缺失");
                                }
                                log.info("第三方系统刷新 access_token, ip={}", ip);
                                response = refreshToken(refreshToken, ip);
                                break;
                            default:
                                throw new BusinessException(OAuth2Constants.ERROR_UNSUPPORTED_GRANT_TYPE,
                                        "不支持的 grant_type: %s".formatted(grantType));
                        }
                        return Mono.just(ResponseEntity.ok(response));
                    } catch (BusinessException e) {
                        log.warn("OAuth2.0 Token 请求失败: grant_type={}, error={}", grantType, e.getMessage());
                        return Mono.just(ResponseEntity.status(getHttpStatus(e.getCode()))
                                .body(createErrorResponse(e.getCode(), e.getMessage())));
                    }
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("OAuth2.0 Token 请求异常", e);
                    return Mono.just(ResponseEntity.status(getHttpStatus(OAuth2Constants.ERROR_SERVER_ERROR))
                            .body(createErrorResponse(OAuth2Constants.ERROR_SERVER_ERROR, "服务器内部错误")));
                });
    }

    @Override
    public Mono<ResponseEntity<?>> introspectToken(ServerWebExchange exchange) {
        return parseFormBody(exchange)
                .flatMap(formData -> {
                    String token = formData.getFirst("token");
                    String tokenTypeHint = formData.getFirst("token_type_hint");

                    if (StringUtils.isBlank(token)) {
                        return Mono.just(ResponseEntity
                                .status(getHttpStatus(OAuth2Constants.ERROR_INVALID_REQUEST))
                                .body(createErrorResponse(OAuth2Constants.ERROR_INVALID_REQUEST, "token 参数缺失")));
                    }

                    try {
                        OAuth2IntrospectionResponseVO resp = OAuth2IntrospectionResponseVO.builder()
                                .active(false)
                                .build();

                        // 当前主要针对 access_token 做校验；refresh_token 可按需扩展
                        if (StringUtils.isBlank(tokenTypeHint)
                                || OAuth2Constants.GRANT_TYPE_ACCESS_TOKEN.equals(tokenTypeHint)) {
                            ThirdPartyClientConfigVO clientConfig = verifyAccessToken(token);
                            if (clientConfig != null) {
                                resp.setActive(true);
                                resp.setClientId(clientConfig.getClientId());
                                resp.setTokenType(OAuth2Constants.TOKEN_TYPE_BEARER);
                                if (clientConfig.getScopes() != null && !clientConfig.getScopes().isEmpty()) {
                                    resp.setScope(String.join(" ", clientConfig.getScopes()));
                                }
                            }
                        }

                        return Mono.just(ResponseEntity.ok(resp));
                    } catch (BusinessException e) {
                        log.warn("验证 token 失败: error={}", e.getMessage());
                        return Mono.just(ResponseEntity.status(getHttpStatus(e.getCode()))
                                .body(createErrorResponse(e.getCode(), e.getMessage())));
                    }
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("验证 token 异常", e);
                    return Mono.just(ResponseEntity.status(getHttpStatus(OAuth2Constants.ERROR_SERVER_ERROR))
                            .body(createErrorResponse(OAuth2Constants.ERROR_SERVER_ERROR, "服务器内部错误")));
                });
    }

    @Override
    public Mono<ResponseEntity<?>> revokeTokenRequest(ServerWebExchange exchange) {
        return parseFormBody(exchange)
                .flatMap(formData -> {
                    String token = formData.getFirst("token");
                    String tokenTypeHint = formData.getFirst("token_type_hint");

                    if (StringUtils.isBlank(token)) {
                        return Mono.just(ResponseEntity
                                .status(getHttpStatus(OAuth2Constants.ERROR_INVALID_REQUEST))
                                .body(createErrorResponse(OAuth2Constants.ERROR_INVALID_REQUEST, "token 参数缺失")));
                    }

                    try {
                        revokeToken(token, tokenTypeHint);
                        log.info("撤销 token 成功: tokenTypeHint={}", tokenTypeHint);
                        return Mono.just(ResponseEntity.ok(new OAuth2SimpleResponseVO("200", "success")));
                    } catch (BusinessException e) {
                        log.warn("撤销 token 失败: error={}", e.getMessage());
                        return Mono.just(ResponseEntity.status(getHttpStatus(e.getCode()))
                                .body(createErrorResponse(e.getCode(), e.getMessage())));
                    }
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("撤销 token 失败", e);
                    return Mono.just(ResponseEntity.status(getHttpStatus(OAuth2Constants.ERROR_SERVER_ERROR))
                            .body(createErrorResponse(OAuth2Constants.ERROR_SERVER_ERROR, "服务器内部错误")));
                });
    }

    /**
     * 解析 form-urlencoded 请求体
     * <p>
     * 使用 ServerWebExchange.getFormData() 方法，这是 Spring WebFlux 推荐的方式
     */
    private Mono<MultiValueMap<String, String>> parseFormBody(ServerWebExchange exchange) {
        return exchange.getFormData()
                .onErrorResume(e -> {
                    log.warn("解析 form body 失败: {}", e.getMessage());
                    return Mono.just(new LinkedMultiValueMap<>());
                });
    }

    /**
     * 创建 OAuth2.0 标准错误响应
     */
    private OAuth2ErrorResponseVO createErrorResponse(String error, String errorDescription) {
        return OAuth2ErrorResponseVO.builder()
                .error(error)
                .errorDescription(errorDescription)
                .errorUri(OAuth2Constants.ERROR_DOCS_BASE_URI + error)
                .build();
    }

    /**
     * 根据错误码获取 HTTP 状态码
     */
    private int getHttpStatus(String errorCode) {
        return switch (errorCode) {
            case OAuth2Constants.ERROR_INVALID_REQUEST,
                 OAuth2Constants.ERROR_UNSUPPORTED_GRANT_TYPE,
                 OAuth2Constants.ERROR_INVALID_SCOPE -> 400;
            case OAuth2Constants.ERROR_INVALID_CLIENT,
                 OAuth2Constants.ERROR_INVALID_TOKEN,
                 OAuth2Constants.ERROR_INVALID_GRANT -> 401;
            case OAuth2Constants.ERROR_UNAUTHORIZED_CLIENT,
                 OAuth2Constants.ERROR_IP_NOT_ALLOWED,
                 OAuth2Constants.ERROR_CLIENT_DISABLED -> 403;
            case OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED -> 429;
            default -> 500;
        };
    }

    /**
     * 绑定 access_token 与 IP（用于令牌归属校验）
     */
    private void bindAccessTokenIp(String accessToken, String clientId, String ip, long ttlMillis) {
        if (StringUtils.isBlank(accessToken) || StringUtils.isBlank(ip)) {
            return;
        }
        String key = GatewayRedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey(accessToken);
        Map<String, String> data = new HashMap<>();
        data.put(OAuth2Constants.JWT_CLAIM_CLIENT_ID, clientId);
        data.put("ip", ip);
        data.put("createdAt", String.valueOf(System.currentTimeMillis()));
        GlobalCache.struct().set(key, data, ttlMillis);
    }

    @SuppressWarnings("unchecked")
    private ThirdPartyClientConfigVO loadClientConfigForTokenVerification(String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return null;
        }
        Map<ThirdPartyClientConfigVO.Field, Object> fieldMap = clientService.getClientConfig(
                clientId,
                ThirdPartyClientConfigVO.Field.ENABLED,
                ThirdPartyClientConfigVO.Field.SCOPES
        );
        if (fieldMap == null || fieldMap.isEmpty()) {
            return null;
        }
        Boolean enabled = (Boolean) fieldMap.get(ThirdPartyClientConfigVO.Field.ENABLED);
        List<String> scopes = (List<String>) fieldMap.getOrDefault(ThirdPartyClientConfigVO.Field.SCOPES, Collections.emptyList());
        return ThirdPartyClientConfigVO.builder()
                .clientId(clientId)
                .enabled(enabled)
                .scopes(scopes)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<String> getEnabledClientIpWhitelist(String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return null;
        }
        Map<ThirdPartyClientConfigVO.Field, Object> fieldMap = clientService.getClientConfig(
                clientId,
                ThirdPartyClientConfigVO.Field.ENABLED,
                ThirdPartyClientConfigVO.Field.IP_WHITELIST
        );
        if (fieldMap == null || fieldMap.isEmpty()) {
            return null;
        }
        Boolean enabled = (Boolean) fieldMap.get(ThirdPartyClientConfigVO.Field.ENABLED);
        if (!Boolean.TRUE.equals(enabled)) {
            return null;
        }
        return (List<String>) fieldMap.getOrDefault(ThirdPartyClientConfigVO.Field.IP_WHITELIST, Collections.emptyList());
    }

    private ThirdPartyClientConfigVO loadClientConfig(String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return null;
        }

        Boolean enabled = clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.ENABLED);
        if (enabled == null) {
            return null;
        }

        String scopesRaw = clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.SCOPES);
        List<String> scopes = parseScopes(scopesRaw);

        return ThirdPartyClientConfigVO.builder()
                .clientId(clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.CLIENT_ID))
                .clientSecret(clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.CLIENT_SECRET))
                .clientName(clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.CLIENT_NAME))
                .enabled(enabled)
                .scopes(scopes)
                .tokenValidDuration(clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.TOKEN_VALID_DURATION))
                .refreshTokenValidDuration(clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.REFRESH_TOKEN_VALID_DURATION))
                .rateLimit(clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.RATE_LIMIT))
                .build();
    }

    private List<String> parseScopes(String scopesRaw) {
        if (StringUtils.isBlank(scopesRaw)) {
            return Collections.emptyList();
        }
        try {
            return JsonUtils.getInstance().deserialize(scopesRaw, new tools.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Arrays.stream(scopesRaw.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .toList();
        }
    }
}
