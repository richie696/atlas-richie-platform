package com.richie.gateway.filter.thirdparty.auth;

import com.richie.gateway.config.GatewayConfig;
import com.richie.gateway.config.OAuth2AnomalyDetectionConfig;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.component.cache.GlobalCache;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.filter.common.security.AnomalyDetectionFilter;
import com.richie.gateway.service.AuditService;
import com.richie.gateway.service.OAuth2ClientService;
import com.richie.gateway.utils.NetworkUtils;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2.0 专属异常行为检测过滤器
 * <p>
 * 职责：
 * 1. Token 重放检测（同一 token 多 IP 使用）- OAuth2.0 专属
 * 2. 异常刷新检测（刷新频率异常）- OAuth2.0 专属
 * 3. 基于客户端配置的 rateLimit 限流（OAuth2.0 特定实现）
 * <p>
 * 说明：
 * - 通用异常检测功能（暴力破解、异常 IP、通用限流）已迁移到 {@link AnomalyDetectionFilter}
 * - 此过滤器仅处理 OAuth2.0 专属的检测逻辑
 * - 通用检测功能通过调用 {@link AnomalyDetectionFilter} 实现
 * <p>
 * 执行顺序：在 InterfaceAuthFilter 之后，OAuth2AuditFilter 之前
 *
 * @author richie696
 * @version 2.0
 * @since 2025-12-18
 */
@Slf4j
@Component
public class OAuth2AnomalyDetectionFilter extends AbstractBaseFilter {

    private final OAuth2ClientService clientService;
    private final AuditService auditService;
    private final OAuth2AnomalyDetectionConfig detectionConfig;
    private final AnomalyDetectionFilter commonAnomalyDetectionFilter;

    /**
     * 构造函数
     *
     * @param config                       网关配置
     * @param i18n                         国际化解析器
     * @param clientService                客户端服务
     * @param auditService                 审计服务
     * @param detectionConfig              OAuth2.0 专属异常检测配置
     * @param commonAnomalyDetectionFilter 通用异常检测过滤器
     */
    public OAuth2AnomalyDetectionFilter(GatewayConfig config, I18nResolver i18n,
                                        OAuth2ClientService clientService,
                                        AuditService auditService,
                                        OAuth2AnomalyDetectionConfig detectionConfig,
                                        AnomalyDetectionFilter commonAnomalyDetectionFilter) {
        super(config, i18n);
        this.clientService = clientService;
        this.auditService = auditService;
        this.detectionConfig = detectionConfig;
        this.commonAnomalyDetectionFilter = commonAnomalyDetectionFilter;
    }

    @Override
    public int getOrder() {
        // 在 InterfaceAuthFilter 之后执行
        return FilterOrder.OAUTH2_ANOMALY_DETECTION_FILTER.getOrder();
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getURI().getPath();

        // 只处理 OAuth2.0 相关接口
        if (!path.startsWith(OAuth2Constants.OAUTH2_BASE)) {
            return chain.filter(exchange);
        }

        // 如果异常检测未启用，直接放行
        if (!detectionConfig.isEnabled()) {
            return chain.filter(exchange);
        }

        String ip = NetworkUtils.getIP(request);
        String userAgent = NetworkUtils.getUserAgent(request);

        // 1. Token 接口：检测暴力破解和异常刷新
        if (path.equals(OAuth2Constants.OAUTH2_TOKEN_PATH)) {
            return handleTokenEndpoint(exchange, chain, request, response, ip, userAgent);
        }

        // 2. 撤销接口：无需特殊检测（由审计过滤器处理）
        if (path.equals(OAuth2Constants.OAUTH2_REVOKE_PATH)) {
            return chain.filter(exchange);
        }

        // 2. 资源访问：检测 Token 重放、异常 IP、限流
        return handleResourceAccess(exchange, chain, request, response, ip, userAgent);
    }

    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path.startsWith(OAuth2Constants.OAUTH2_BASE)
                && config.getInterfaceAuth().isEnable()
                && detectionConfig.isEnabled();
    }

    /**
     * 处理 Token 接口（/api/oauth2/token）
     */
    private Mono<Void> handleTokenEndpoint(ServerWebExchange exchange, GatewayFilterChain chain,
                                           ServerHttpRequest request, ServerHttpResponse response,
                                           String ip, String userAgent) {
        // 先从查询参数中提取 clientId
        final String[] clientId = {request.getQueryParams().getFirst("client_id")};

        // 如果查询参数中没有 client_id，且是 form-urlencoded 请求，尝试从请求体中提取
        if (StringUtils.isBlank(clientId[0]) && isFormUrlEncoded(request)) {
            // 需要读取请求体来提取 client_id
            return DataBufferUtils.join(request.getBody())
                    .flatMap(dataBuffer -> {
                        try {
                            // 读取请求体
                            String requestBody = dataBuffer.toString(StandardCharsets.UTF_8);

                            // 从请求体中提取 client_id
                            String extractedClientId = extractClientIdFromFormBody(requestBody);
                            if (StringUtils.isNotBlank(extractedClientId)) {
                                clientId[0] = extractedClientId;
                            }

                            // 重新包装请求体（因为请求体只能读取一次）
                            DataBuffer newBuffer = dataBuffer.factory()
                                    .wrap(requestBody.getBytes(StandardCharsets.UTF_8));

                            // 创建新的请求
                            ServerHttpRequest newRequest = new ServerHttpRequestDecorator(request) {
                                @Nonnull
                                @Override
                                public Flux<DataBuffer> getBody() {
                                    return Flux.just(newBuffer);
                                }
                            };

                            // 创建新的交换机
                            ServerWebExchange newExchange = exchange.mutate()
                                    .request(newRequest)
                                    .build();

                            // 继续处理（使用提取的 clientId）
                            return processTokenEndpointWithClientId(newExchange, chain, newRequest, response, clientId[0], ip, userAgent);
                        } catch (Exception e) {
                            log.warn("从请求体提取 client_id 失败", e);
                            // 请求体解析失败，继续处理（clientId 可能为 null）
                            DataBufferUtils.release(dataBuffer);
                            return processTokenEndpointWithClientId(exchange, chain, request, response, clientId[0], ip, userAgent);
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    })
                    .onErrorResume(throwable -> {
                        log.debug("读取请求体失败，继续处理: {}", throwable.getMessage());
                        // 请求体读取失败，继续处理（clientId 可能为 null）
                        return processTokenEndpointWithClientId(exchange, chain, request, response, clientId[0], ip, userAgent);
                    });
        }

        // 如果查询参数中有 client_id 或不是 form-urlencoded 请求，直接处理
        return processTokenEndpointWithClientId(exchange, chain, request, response, clientId[0], ip, userAgent);
    }

    /**
     * 处理 Token 接口（使用已提取的 clientId）
     */
    private Mono<Void> processTokenEndpointWithClientId(ServerWebExchange exchange, GatewayFilterChain chain,
                                                       ServerHttpRequest request, ServerHttpResponse response,
                                                       String clientId, String ip, String userAgent) {
        // 检测 clientId 是否被封禁（使用通用异常检测过滤器）
        if (StringUtils.isNotBlank(clientId) && commonAnomalyDetectionFilter.isUserIdBanned(clientId)) {
            log.warn("clientId 已被封禁: clientId={}, ip={}", clientId, ip);
            return returnOAuth2Error(response, OAuth2Constants.ERROR_INVALID_CLIENT, "客户端已被临时封禁，请稍后再试");
        }

        // 装饰响应，拦截响应体以统计失败次数
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(response) {
            @Nonnull
            @Override
            public Mono<Void> writeWith(@Nonnull org.reactivestreams.Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                    return fluxBody.collectList().flatMap(dataBuffers -> {
                        try {
                            // 读取响应体（用于判断成功/失败）
                            String responseBody = readResponseBody(dataBuffers);
                            HttpStatusCode statusCode = getStatusCode();

                            // 根据响应状态码判断成功/失败
                            boolean success = statusCode == HttpStatus.OK;
                            if (StringUtils.isNotBlank(clientId)) {
                                // 使用通用异常检测过滤器记录认证结果（用于暴力破解检测）
                                commonAnomalyDetectionFilter.recordAuthResult(clientId, ip, success);

                                // 如果是成功响应，检测异常刷新（OAuth2.0 专属）
                                if (success) {
                                    detectAbnormalRefresh(clientId, ip, responseBody);
                                }
                            }

                            // 重新包装响应体
                            DataBufferFactory bufferFactory = new DefaultDataBufferFactory();
                            DataBuffer buffer = bufferFactory.wrap(responseBody.getBytes(StandardCharsets.UTF_8));
                            return super.writeWith(Mono.just(buffer));
                        } catch (Exception e) {
                            log.error("处理 Token 接口响应失败", e);
                            return super.writeWith(body);
                        }
                    });
                }
                return super.writeWith(body);
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 读取响应体
     */
    private String readResponseBody(java.util.List<? extends DataBuffer> dataBuffers) {
        int totalLength = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
        byte[] content = new byte[totalLength];
        int offset = 0;
        for (DataBuffer db : dataBuffers) {
            int len = db.readableByteCount();
            db.read(content, offset, len);
            offset += len;
        }
        dataBuffers.forEach(DataBufferUtils::release);
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * 处理资源访问
     */
    private Mono<Void> handleResourceAccess(ServerWebExchange exchange, GatewayFilterChain chain,
                                            ServerHttpRequest request, ServerHttpResponse response,
                                            String ip, String userAgent) {
        // 1. 提取 access_token
        String accessToken = extractAccessToken(request);
        if (StringUtils.isBlank(accessToken)) {
            return chain.filter(exchange);
        }

        // 2. 提取 clientId
        String clientId = extractClientIdFromToken(accessToken);
        if (StringUtils.isBlank(clientId)) {
            return chain.filter(exchange);
        }

        // 3. 检测 Token 重放（OAuth2.0 专属）
        detectTokenReplay(accessToken, ip, clientId);

        // 4. 检测异常 IP 访问（使用通用异常检测过滤器）
        // 注意：异常 IP 检测在通用过滤器中自动执行（通过 extractUserId 提取 clientId）
        // 这里不需要额外调用，通用过滤器已经处理了

        // 5. 检测基于 clientId 的限流（OAuth2.0 特定实现：基于客户端配置的 rateLimit）
        if (!checkOAuth2RateLimit(clientId, ip)) {
            log.warn("客户端请求频率超限: clientId={}, ip={}", clientId, ip);
            return returnOAuth2Error(response, OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED, "请求频率超限，请稍后再试");
        }

        return chain.filter(exchange);
    }


    /**
     * 检测 Token 重放攻击
     */
    private void detectTokenReplay(String token, String ip, String clientId) {
        OAuth2AnomalyDetectionConfig.TokenReplayConfig config = detectionConfig.getTokenReplay();
        String key = GatewayRedisKey.OAUTH2_ANOMALY_TOKEN_IPS.getKey(token);
        long ttl = TimeUnit.SECONDS.toMillis(config.getTimeWindowSeconds());

        // 检查 IP 是否已存在
        boolean ipExists = GlobalCache.collection().exists(key, ip);

        // 如果 IP 不存在，则添加
        if (!ipExists) {
            // 由于 addSetItem 没有 TTL 参数，需要先检查 key 是否存在，如果不存在则先创建并设置 TTL
            if (!GlobalCache.key().hasKey(key)) {
                // 首次创建时设置 TTL
                GlobalCache.collection().set(key, new HashSet<>(java.util.Set.of(ip)), ttl);
            } else {
                GlobalCache.collection().add(key, ip);
                // 更新 TTL（确保 key 不会过期）
                GlobalCache.key().setExpiredTime(key, ttl);
            }
        }

        // 获取 Set 大小（用于检测）
        Long setSize = GlobalCache.collection().size(key);
        if (setSize != null && setSize > config.getMaxIpsPerToken()) {
            log.warn("检测到 Token 重放攻击: token={}, ips={}, clientId={}",
                    token.length() > 20 ? token.substring(0, 20) + "..." : token, setSize, clientId);
            // 记录可疑活动审计日志
            auditService.auditSuspiciousActivity(
                    clientId, ip, "TOKEN_REPLAY",
                    String.format("同一 token 从 %d 个不同 IP 使用", setSize)
            );
        }
    }

    /**
     * 检测异常刷新
     */
    private void detectAbnormalRefresh(String clientId, String ip, String responseBody) {
        try {
            // 尝试从响应体中判断是否为刷新操作
            // 注意：由于无法从响应中准确区分 grant_type，这里简化处理
            // 如果响应中有 refresh_token，可能是 client_credentials 或 refresh_token
            // 实际实现中，可以通过请求属性传递 grant_type，或通过其他方式判断

            // 简化实现：统计刷新请求频率（所有成功的 token 请求都统计）
            // 如果需要更精确的判断，可以在响应 VO 中添加 grant_type 字段
            OAuth2AnomalyDetectionConfig.AbnormalRefreshConfig config = detectionConfig.getAbnormalRefresh();
            String key = GatewayRedisKey.OAUTH2_ANOMALY_REFRESH_COUNT.getKey(clientId);
            long ttl = TimeUnit.SECONDS.toMillis(config.getTimeWindowSeconds());

            long refreshCount = GlobalCache.value().increment(key, 1L, ttl);

            // 检测是否超过阈值
            if (refreshCount > config.getMaxRefreshesPerMinute()) {
                log.warn("检测到异常刷新: clientId={}, count={}, ip={}", clientId, refreshCount, ip);
                // 记录可疑活动审计日志
                auditService.auditSuspiciousActivity(
                        clientId, ip, "ABNORMAL_REFRESH",
                        String.format("1 分钟内刷新 %d 次", refreshCount)
                );
            }
        } catch (Exception e) {
            log.warn("检测异常刷新失败", e);
        }
    }

    /**
     * 检测基于 clientId 的限流（OAuth2.0 特定实现：基于客户端配置的 rateLimit）
     */
    private boolean checkOAuth2RateLimit(String clientId, String ip) {
        // 注意：通用限流已在 AnomalyDetectionFilter 中处理
        // 这里只处理 OAuth2.0 特定的限流逻辑（基于客户端配置的 rateLimit）

        Integer rateLimitValue = clientService.getClientConfig(clientId, ThirdPartyClientConfigVO.Field.RATE_LIMIT);
        if (rateLimitValue == null) {
            return true; // 客户端不存在，不限制
        }

        // 如果客户端配置中没有 rateLimit，使用通用限流（已在 AnomalyDetectionFilter 中处理）
        // 使用客户端配置的 rateLimit（OAuth2.0 特定）
        int rateLimit = rateLimitValue;
        String key = GatewayRedisKey.OAUTH2_ANOMALY_RATELIMIT.getKey(clientId);
        long ttl = TimeUnit.HOURS.toMillis(1); // 1 小时窗口

        long count = GlobalCache.value().increment(key, 1L, ttl);

        // 检测是否超过阈值
        if (count > rateLimit) {
            log.warn("客户端请求频率超限（OAuth2.0 特定）: clientId={}, count={}, limit={}", clientId, count, rateLimit);
            // 记录可疑活动审计日志
            auditService.auditSuspiciousActivity(
                    clientId, ip, "RATE_LIMIT_EXCEEDED",
                    String.format("请求频率超限（OAuth2.0 特定）: count=%d, limit=%d", count, rateLimit)
            );
            return false;
        }

        return true;
    }

    /**
     * 检测基于 clientId 的限流（已废弃，使用 checkOAuth2RateLimit）
     *
     * @deprecated 使用 {@link #checkOAuth2RateLimit(String, String)} 替代
     */
    @Deprecated
    private boolean checkRateLimit(String clientId, String ip) {
        // 此方法已废弃，保留仅为兼容
        return checkOAuth2RateLimit(clientId, ip);
    }

    /**
     * 从请求中提取 clientId
     * <p>
     * 注意：此方法仅从查询参数中提取，form-urlencoded 请求体中的 client_id 已在 handleTokenEndpoint 中处理
     *
     * @param request 请求对象
     * @return clientId，如果未找到则返回 null
     */
    private String extractClientIdFromRequest(ServerHttpRequest request) {
        // 从查询参数中提取
        return request.getQueryParams().getFirst("client_id");
    }

    /**
     * 判断请求是否为 form-urlencoded 格式
     *
     * @param request 请求对象
     * @return 是否为 form-urlencoded
     */
    private boolean isFormUrlEncoded(ServerHttpRequest request) {
        MediaType contentType = request.getHeaders().getContentType();
        return contentType != null && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType);
    }

    /**
     * 从 form-urlencoded 请求体中提取 client_id
     * <p>
     * 请求体格式示例：grant_type=client_credentials&client_id=xxx&client_secret=yyy&scope=read write
     *
     * @param requestBody 请求体内容
     * @return client_id，如果未找到则返回 null
     */
    private String extractClientIdFromFormBody(String requestBody) {
        if (StringUtils.isBlank(requestBody)) {
            return null;
        }

        try {
            // 解析 form-urlencoded 格式
            String[] pairs = requestBody.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2 && "client_id".equals(keyValue[0])) {
                    // URL 解码（处理特殊字符）
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            log.debug("解析 form-urlencoded 请求体失败", e);
        }

        return null;
    }

    /**
     * 从请求头中提取 access_token
     */
    private String extractAccessToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(OAuth2Constants.HEADER_AUTHORIZATION);
        if (StringUtils.isNotBlank(authorization) && authorization.startsWith(OAuth2Constants.BEARER_PREFIX)) {
            return authorization.substring(OAuth2Constants.BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    /**
     * 从 Token 中提取 clientId
     */
    private String extractClientIdFromToken(String token) {
        if (StringUtils.isBlank(token) || !token.contains(".")) {
            return null;
        }
        try {
            return JwtUtils.getArgument(token, OAuth2Constants.JWT_CLAIM_CLIENT_ID);
        } catch (Exception e) {
            log.debug("提取 clientId 失败", e);
            return null;
        }
    }

    /**
     * 返回 OAuth2.0 标准错误响应
     */
    private Mono<Void> returnOAuth2Error(ServerHttpResponse response, String error, String errorDescription) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS); // 429
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        String errorJson = String.format(
                "{\"error\":\"%s\",\"error_description\":\"%s\",\"error_uri\":\"%s%s\"}",
                error, errorDescription, OAuth2Constants.ERROR_DOCS_BASE_URI, error);

        byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
