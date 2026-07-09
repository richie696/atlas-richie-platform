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
package com.richie.gateway.filter.internal.auth;

import com.richie.contract.model.LoginUserPrincipal;
import com.richie.contract.model.ApiResult;
import com.richie.contract.constant.GlobalConstants;
import com.richie.gateway.config.GatewayConfig;
import com.richie.context.utils.data.Collections;
import com.richie.context.utils.data.JsonUtils;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.component.mfa.validation.dto.MfaValidationResult;
import com.richie.component.mfa.validation.service.MfaValidationService;
import com.richie.gateway.dto.MfaRequiredResponse;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.service.SignatureService;
import com.richie.gateway.util.HardwareFingerprintUtils;
import com.richie.gateway.utils.MfaTokenUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR;

/**
 * 网关安全过滤器
 *
 *
 * @author richie696
 * @version 1.0
 * @since 2021/06/29
 */
@Slf4j
@Component
public class IssueTokensFilter extends AbstractBaseFilter {

    private final SignatureService signatureService;

    @Autowired(required = false)
    private MfaValidationService mfaValidationService;

    @Autowired(required = false)
    private MfaTokenUtils mfaTokenUtils;

    /**
     * 构造函数
     *
     * @param config 网关配置
     * @param i18n 国际化解析器
     * @param signatureService 签名服务
     */
    public IssueTokensFilter(GatewayConfig config, I18nResolver i18n, SignatureService signatureService) {
        super(config, i18n);
        this.signatureService = signatureService;
    }

    /**
     * 过滤器队列序号
     *
     * @return 返回当前过滤器的队列序号
     */
    public int getOrder() {
        return FilterOrder.ISSUE_TOKENS_FILTER.getOrder();
    }

    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        var path = request.getURI().getPath();
        if (config.getToken().getLoginUriList().stream().anyMatch(path::matches)) {
            return chain.filter(exchange.mutate().response(postHandler(exchange)).build());
        }
        return chain.filter(exchange);
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getToken().getLoginUriList().stream().anyMatch(exchange.getRequest().getURI().getPath()::matches);
    }

    private ServerHttpResponseDecorator postHandler(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        var path = exchange.getRequest().getURI().getPath();
        return new ServerHttpResponseDecorator(response) {
            @Nonnull
            @Override
            public Mono<Void> writeWith(@Nonnull Publisher<? extends DataBuffer> body) {
                // 检查响应状态码是否为HTTP 200 OK，并且响应体是一个Flux流
                if (Objects.equals(getStatusCode(), HttpStatus.OK) && body instanceof Flux) {
                    // 获取原始响应内容类型
                    String originalResponseContentType = exchange.getAttribute(ORIGINAL_RESPONSE_CONTENT_TYPE_ATTR);
                    // 检查原始响应内容类型是否为application/json
                    if (StringUtils.isNotBlank(originalResponseContentType) && originalResponseContentType.contains("application/json")) {
                        // 将响应体转换为Flux流
                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                        // 重写writeWith方法，处理响应体
                        return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                            // 读取字节流
                            DataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();
                            // 将多个DataBuffer缓冲区对象利用数据缓冲区工厂合并读取到一个数据缓冲区对象中
                            var fullDataBuffer = dataBufferFactory.join(dataBuffers);
                            // 根据完整的数据流长度创建临时缓冲区
                            byte[] temporaryBuffers = new byte[fullDataBuffer.readableByteCount()];
                            // 读取数据到临时缓冲区
                            fullDataBuffer.read(temporaryBuffers);
                            // 释放缓冲区
                            DataBufferUtils.release(fullDataBuffer);
                            // 解析字节数据为字符串
                            String responseData = new String(temporaryBuffers, StandardCharsets.UTF_8);
                            // 获取原始数据报文
                            ApiResult<LoginUserPrincipal> result = JsonUtils.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSSX").deserialize(responseData, new TypeReference<>() {
                            });
                            // 如果当前报文是有效的登录请求，则进行令牌签发操作
                            if (result != null && result.isSuccess() && result.getData() != null) {
                                LoginUserPrincipal userVO = result.getData();

                                // 可信设备与“是否需要 MFA”由业务登录接口（如 sample-mfa）在返回前判断；
                                // 若响应体 data 中已含 accessToken，表示业务侧已放行（可信设备或未绑定/已验 MFA），不再做 MFA 检查
                                boolean backendReturnedToken = hasNonBlankAccessTokenInData(responseData);

                                if (!backendReturnedToken && isMfaEnabledForCurrentLoginUri(path) && mfaValidationService != null && mfaTokenUtils != null) {
                                    // 业务侧未返回 token，且当前登录页启用 MFA：根据 data.mfaBound 决定是否返回 MFA_REQUIRED
                                    if (isMfaBoundInData(responseData)) {
                                        String userId = getUserId(userVO);
                                        String tenantId = userVO.getSignParams() != null ? userVO.getSignParams().get("tenantId") : null;

                                        MfaValidationResult mfaResult = mfaValidationService.checkMfaStatus(userId, tenantId, extractDeviceId(exchange));
                                        if (mfaResult.isMfaRequired()) {
                                            log.info("用户需要MFA验证，userId: {}, tenantId: {}, loginUri: {}", userId, tenantId, path);

                                            String mfaToken = mfaTokenUtils.generateMfaToken(userId, tenantId, userVO.getUsername());
                                            storeUserInfoToCache(userId, tenantId, userVO);

                                            MfaRequiredResponse mfaRequiredResponse = MfaRequiredResponse.builder()
                                                .code("MFA_REQUIRED")
                                                .msg("需要进行多因子认证")
                                                .data(MfaRequiredResponse.MfaRequiredData.builder()
                                                    .mfaToken(mfaToken)
                                                    .mfaMethods(java.util.Collections.singletonList("TOTP"))
                                                    .trustedDeviceSupported(mfaResult.isTrustedDeviceSupported())
                                                    .trustedDeviceCount(mfaResult.getTrustedDeviceCount())
                                                    .maxTrustedDevices(mfaResult.getMaxTrustedDevices())
                                                    .defaultTrustDays(mfaResult.getDefaultTrustDays())
                                                    .build())
                                                .build();

                                            String jsonResponse = JsonUtils.getInstance().serialize(mfaRequiredResponse);
                                            Objects.requireNonNull(jsonResponse, "The serialized string cannot be null.");
                                            return response.bufferFactory().wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
                                        }
                                    }
                                }

                                // 业务侧已返回 token 或无需 MFA：直接签发访问 Token
                                // 如果提供了设备ID，将其绑定到Token中（用于后续API请求的设备验证）
                                String deviceId = extractDeviceId(exchange);
                                if (StringUtils.isNotBlank(deviceId)) {
                                    userVO.addParam("deviceId", deviceId);
                                }

                                // 如果提供了硬件指纹，将其绑定到Token中（增强安全：即使deviceId被盗，硬件指纹不匹配也会拒绝）
                                HardwareFingerprintUtils.HardwareFingerprint hardwareFingerprint = extractHardwareFingerprint(exchange);
                                if (hardwareFingerprint != null) {
                                    String fingerprintJson = HardwareFingerprintUtils.serializeFingerprint(hardwareFingerprint);
                                    if (StringUtils.isNotBlank(fingerprintJson)) {
                                        userVO.addParam("hardwareFingerprint", fingerprintJson);
                                    }
                                }

                                String signature = signatureService.createSignature(result);
                                // 写入请求头
                                response.getHeaders().set(JwtUtils.X_ACCESS_TOKEN, signature);
                                // 如果当前启用SSO登录，则刷新该用户的最后在线时间
                                if (config.getSso().isEnable()) {
                                    setLastOnlineToken(result.getData(), signature);
                                }
                            }
                            // 返回原始报文数据流
                            return response.bufferFactory().wrap(temporaryBuffers);
                        }));
                    }
                }
                return super.writeWith(body);
            }

            @Nonnull
            @Override
            public Mono<Void> writeAndFlushWith(@Nonnull Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(p -> p));
            }
        };
    }

    /**
     * 刷新当前用户的最后访问时间
     *
     * @param userVO    用户信息
     * @param signature 当前访问的令牌
     */
    private void setLastOnlineToken(LoginUserPrincipal userVO, String signature) {
        //获取redis中的token
        String tenantId = userVO.getSignParams() != null ? userVO.getSignParams().get("tenantId") : null;
        String key = "";
        if (null != tenantId) {
            key = "%s-".formatted(tenantId);
        }
        key += userVO.getUsername();
        boolean isMobileToken = Boolean.parseBoolean(JwtUtils.getArgument(signature, GlobalConstants.IS_MOBILE_TOKEN));
        if (isMobileToken) {
            key += "-%s".formatted(GlobalConstants.IS_MOBILE_TOKEN);
        }
        String lastOnlineTokenPath = config.getSso().getOnlineTokenPath() + key;
        //存入缓存
        GlobalCache.key().removeCache(lastOnlineTokenPath);
        GlobalCache.collection().set(lastOnlineTokenPath, Collections.setOf(signature), TimeUnit.HOURS.toMillis(1));
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
        // 优先从请求头获取
        String deviceId = exchange.getRequest().getHeaders().getFirst("X-Device-Id");
        if (StringUtils.isNotBlank(deviceId)) {
            return deviceId;
        }

        // 从请求参数获取
        deviceId = exchange.getRequest().getQueryParams().getFirst("deviceId");
        if (StringUtils.isNotBlank(deviceId)) {
            return deviceId;
        }

        return null;
    }

    /**
     * 从请求中提取硬件指纹
     * <p>
     * 硬件指纹可以从请求头 "X-Hardware-Fingerprint" 中获取（格式：JSON.签名）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>分离签名和JSON部分</li>
     *   <li>使用HMAC密钥验证签名是否正确</li>
     *   <li>如果签名正确，解析硬件指纹对象</li>
     *   <li>如果签名不正确，返回 null（拒绝签发Token）</li>
     * </ol>
     *
     * @param exchange 请求交换器
     * @return 硬件指纹对象，如果不存在、格式错误或签名验证失败返回 null
     */
    private HardwareFingerprintUtils.HardwareFingerprint extractHardwareFingerprint(ServerWebExchange exchange) {
        // 从请求头获取签名后的硬件指纹
        String signedFingerprint = exchange.getRequest().getHeaders().getFirst("X-Hardware-Fingerprint");
        if (StringUtils.isBlank(signedFingerprint)) {
            // 从请求参数获取（备用方案）
            signedFingerprint = exchange.getRequest().getQueryParams().getFirst("hardwareFingerprint");
        }

        if (StringUtils.isBlank(signedFingerprint)) {
            return null;
        }

        // 1. 分离签名和JSON部分
        HardwareFingerprintUtils.SignedFingerprintParts parts = HardwareFingerprintUtils.separateSignedFingerprint(signedFingerprint);
        if (parts == null) {
            log.warn("分离签名后的硬件指纹失败，格式错误。签发Token时忽略硬件指纹");
            return null;
        }

        // 2. 验证HMAC签名
        String hmacSecret = config.getHardwareFingerprint().getHmacSecret();
        if (!HardwareFingerprintUtils.verifySignature(parts.getJsonPart(), parts.getSignature(), hmacSecret)) {
            log.warn("硬件指纹签名验证失败，拒绝签发Token");
            return null;
        }

        // 3. 解析硬件指纹对象（签名验证通过后）
        HardwareFingerprintUtils.HardwareFingerprint fingerprint = HardwareFingerprintUtils.parseFingerprint(parts.getJsonPart());
        if (fingerprint == null) {
            log.warn("解析硬件指纹失败，签发Token时忽略硬件指纹");
            return null;
        }

        // 注意：签发Token时不需要验证时间戳，因为这是首次登录，时间戳应该是当前的
        // 时间戳验证在后续API请求时进行（AuthenticationFilter中）

        return fingerprint;
    }

    /**
     * 从 GeneralUserVO 获取用户ID
     * <p>
     * 注意：这里假设可以从 userVO 获取 userId，如果实际字段不同需要调整
     * 可能的方案：
     * 1. 如果 GeneralUserVO 有 userId 字段，直接使用
     * 2. 如果 username 就是 userId，使用 username
     * 3. 如果 signParams 中包含 userId，从 signParams 获取
     *
     * @param userVO 用户信息
     * @return 用户ID
     */
    private String getUserId(LoginUserPrincipal userVO) {
        // 方案1：尝试从 signParams 中获取 userId
        if (userVO.getSignParams() != null && userVO.getSignParams().containsKey("userId")) {
            return userVO.getSignParams().get("userId");
        }

        // 方案2：如果 username 就是 userId，使用 username
        // 注意：这里假设 username 就是 userId，如果实际不是，需要调整
        return userVO.getUsername();
    }

    /**
     * 存储用户信息到缓存
     * <p>
     * 用于后续 MFA 验证成功后签发 Token 时获取用户信息
     *
     * @param userId   用户ID
     * @param tenantId 租户ID（可为null）
     * @param userVO   用户信息
     */
    private void storeUserInfoToCache(String userId, String tenantId, LoginUserPrincipal userVO) {
        String cacheKey = StringUtils.isNotBlank(tenantId)
            ? "login:user:%s:%s".formatted(tenantId, userId)
            : "login:user:%s".formatted(userId);

        // 存储用户信息到缓存，有效期 10 分钟（足够完成 MFA 验证流程）
        String userInfoJson = JsonUtils.getInstance().serialize(userVO);
        GlobalCache.value().set(cacheKey, userInfoJson, TimeUnit.MINUTES.toMillis(10));
        log.debug("用户信息已存储到缓存，cacheKey: {}", cacheKey);
    }

    /**
     * 判断登录响应体 data 中是否包含非空 accessToken
     * <p>
     * 业务登录接口（如 sample-mfa）在可信设备或未绑定/已验 MFA 时会返回 accessToken，
     * 网关据此决定是否跳过 MFA 检查、直接签发 Token。
     *
     * @param responseData 登录接口原始响应 JSON 字符串
     * @return true 表示 data.accessToken 存在且非空
     */
    private boolean hasNonBlankAccessTokenInData(String responseData) {
        if (StringUtils.isBlank(responseData)) {
            return false;
        }
        try {
            JsonNode root = JsonUtils.getInstance().convertJsonNode(responseData);
            JsonNode data = root != null ? root.get("data") : null;
            if (data == null || !data.has("accessToken")) {
                return false;
            }
            String token = data.get("accessToken").asString(null);
            return StringUtils.isNotBlank(token);
        } catch (Exception e) {
            log.debug("解析登录响应 data.accessToken 失败，按需 MFA 处理", e);
            return false;
        }
    }

    /**
     * 判断登录响应体 data 中 mfaBound 是否为 true
     *
     * @param responseData 登录接口原始响应 JSON 字符串
     * @return true 表示 data.mfaBound 为 true
     */
    private boolean isMfaBoundInData(String responseData) {
        if (StringUtils.isBlank(responseData)) {
            return false;
        }
        try {
            JsonNode root = JsonUtils.getInstance().convertJsonNode(responseData);
            JsonNode data = root != null ? root.get("data") : null;
            return data != null && data.has("mfaBound") && data.get("mfaBound").asBoolean(false);
        } catch (Exception e) {
            log.debug("解析登录响应 data.mfaBound 失败", e);
            return false;
        }
    }

    /**
     * 判断当前登录页是否启用MFA
     * <p>
     * 只有当登录页路径在 {@code mfaEnabledLoginUriList} 配置列表中时，才启用MFA检查。
     * <p>
     * 判断逻辑：
     * <ol>
     *   <li>如果 {@code mfaEnabledLoginUriList} 为空，返回 {@code false}（不启用MFA）</li>
     *   <li>如果 {@code mfaEnabledLoginUriList} 不为空，检查当前路径是否匹配列表中的任一模式</li>
     *   <li>支持精确匹配和正则匹配</li>
     * </ol>
     *
     * @param loginUri 当前登录页路径
     * @return 是否启用MFA
     * <ul>
     *   <li>{@code true}：当前登录页启用了MFA，需要进行MFA检查</li>
     *   <li>{@code false}：当前登录页未启用MFA，跳过MFA检查</li>
     * </ul>
     */
    private boolean isMfaEnabledForCurrentLoginUri(String loginUri) {
        List<String> mfaEnabledList = config.getToken().getMfaEnabledLoginUriList();

        // 如果未配置MFA启用列表，则不启用MFA
        if (mfaEnabledList == null || mfaEnabledList.isEmpty()) {
            return false;
        }

        // 检查当前路径是否匹配MFA启用列表中的任一模式
        return mfaEnabledList.stream().anyMatch(pattern -> {
            // 尝试正则匹配
            if (loginUri.matches(pattern)) {
                return true;
            }
            // 尝试精确匹配
            return loginUri.equals(pattern);
        });
    }

}
