package com.richie.gateway.filter.common.infrastructure;

import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import com.richie.gateway.handler.KeyPairManager;
import com.richie.gateway.service.EccCryptoService;
import com.richie.gateway.utils.EccCryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.richie.contract.constant.GlobalConstants.*;


/**
 * ECC加密解密过滤器
 * 处理客户端与网关之间的请求参数加密解密
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-22
 */
@Slf4j
@Component
public class EccCryptoFilter extends AbstractBaseFilter {

    private final EccCryptoService eccCryptoService;
    private final KeyPairManager keyPairManager;

    /**
     * 构造函数
     *
     * @param config 网关配置
     * @param i18n   国际化解析器
     */
    public EccCryptoFilter(GatewayConfig config, I18nResolver i18n,
                           EccCryptoService eccCryptoService, KeyPairManager keyPairManager) {
        super(config, i18n);
        this.eccCryptoService = eccCryptoService;
        this.keyPairManager = keyPairManager;
    }

    /**
     * 过滤器队列序号
     * 需要在认证过滤器之前执行
     *
     * @return 返回当前过滤器的队列序号
     */
    public int getOrder() {
        return FilterOrder.ECC_CRYPTO_FILTER.getOrder();
    }

    /**
     * 执行过滤器逻辑
     *
     * @param exchange 交换机对象
     * @param chain    过滤器链
     * @return 过滤结果
     */
    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 检查是否需要加密处理
        if (!eccCryptoService.shouldEncrypt(path)) {
            return chain.filter(exchange);
        }

        // 1. 检查KeyPair是否过期
        if (keyPairManager.isExpired()) {
            keyPairManager.refreshKeyPair();
            return sendKeyPairChangedResponse(exchange);
        }

        // 2. 检查客户端带的keyId是否和服务端一致
        String clientKeyId = request.getHeaders().getFirst("X-Gateway-KeyId");
        String currentKeyId = keyPairManager.getKeyId();
        if (!currentKeyId.equals(clientKeyId)) {
            return sendKeyPairChangedResponse(exchange);
        }

        // 处理客户端公钥交换
        String clientPublicKey = request.getHeaders().getFirst(X_CLIENT_PUBLIC_KEY);
        String clientId = request.getHeaders().getFirst(X_CLIENT_ID);

        if (StringUtils.hasText(clientPublicKey) && StringUtils.hasText(clientId)) {
            // 缓存客户端公钥
            eccCryptoService.cacheClientPublicKey(clientId, clientPublicKey);

            // 如果是密钥交换请求，直接返回网关公钥
            if (path.endsWith("/api/crypto/exchange")) {
                return handleKeyExchange(exchange);
            }
        }

        // 处理加密的请求数据
        return handleEncryptedRequest(exchange, chain);
    }

    /**
     * 处理密钥交换请求
     *
     * @param exchange 交换机对象
     * @return 响应结果
     */
    private Mono<Void> handleKeyExchange(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String gatewayPublicKey = EccCryptoUtils.publicKeyToBase64(keyPairManager.getKeyPair().getPublic());
        String keyId = keyPairManager.getKeyId();
        String responseBody = String.format(
            "{\"keyId\":\"%s\",\"gatewayPublicKey\":\"%s\"}",
            keyId, gatewayPublicKey
        );

        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 处理加密的请求数据
     *
     * @param exchange 交换机对象
     * @param chain    过滤器链
     * @return 过滤结果
     */
    private Mono<Void> handleEncryptedRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientId = request.getHeaders().getFirst(X_CLIENT_ID);
        String encryptedData = request.getHeaders().getFirst(X_ENCRYPTED_DATA);

        // 如果没有客户端ID，生成一个临时ID
        if (!StringUtils.hasText(clientId)) {
            clientId = UUID.randomUUID().toString();
        }

        String finalClientId = clientId;
        // 如果有加密数据，进行解密
        if (StringUtils.hasText(encryptedData)) {
            return DataBufferUtils.join(request.getBody())
                    .flatMap(dataBuffer -> {
                        try {
                            // 解密请求数据
                            String decryptedData = eccCryptoService.decryptRequestData(
                                    encryptedData, finalClientId, keyPairManager.getKeyPair().getPrivate());

                            if (decryptedData == null) {
                                return handleError(exchange, "解密失败", HttpStatus.BAD_REQUEST);
                            }

                            // 创建新的请求体
                            DataBuffer newBuffer = dataBuffer.factory()
                                    .wrap(decryptedData.getBytes(StandardCharsets.UTF_8));

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

                            // 包裹响应，进行加密
                            return chain.filter(decorateExchangeForResponse(newExchange, finalClientId));

                        } catch (Exception e) {
                            log.error("处理加密请求失败", e);
                            return handleError(exchange, "处理加密请求失败", HttpStatus.INTERNAL_SERVER_ERROR);
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    });
        } else {
            // 没有加密数据，正常处理请求，包裹响应
            return chain.filter(decorateExchangeForResponse(exchange, finalClientId));
        }
    }

    /**
     * 包裹响应，拦截writeWith进行加密
     */
    private ServerWebExchange decorateExchangeForResponse(ServerWebExchange exchange, String clientId) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        DataBufferFactory bufferFactory = originalResponse.bufferFactory();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Nonnull
            @Override
            public Mono<Void> writeWith(@Nonnull Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux<? extends DataBuffer> fluxBody) {
                    return fluxBody.collectList().flatMap(dataBuffers -> {
                        try {
                            int totalLength = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
                            byte[] content = new byte[totalLength];
                            int offset = 0;
                            for (DataBuffer db : dataBuffers) {
                                int len = db.readableByteCount();
                                db.read(content, offset, len);
                                offset += len;
                            }
                            dataBuffers.forEach(DataBufferUtils::release);
                            String responseBody = new String(content, StandardCharsets.UTF_8);
                            String encrypted = eccCryptoService.encryptResponseData(responseBody, clientId, keyPairManager.getKeyPair().getPrivate());
                            if (encrypted != null) {
                                originalResponse.getHeaders().set(X_RESPONSE_ENCRYPTED, "true");
                                DataBuffer buffer = bufferFactory.wrap(encrypted.getBytes(StandardCharsets.UTF_8));
                                return super.writeWith(Mono.just(buffer));
                            } else {
                                // 加密失败，返回原始响应
                                DataBuffer buffer = bufferFactory.wrap(content);
                                return super.writeWith(Mono.just(buffer));
                            }
                        } catch (Exception e) {
                            log.error("处理加密响应失败", e);
                            // 出错时返回原始响应
                            return super.writeWith(body);
                        }
                    });
                }
                return super.writeWith(body);
            }
        };
        return exchange.mutate().response(decoratedResponse).build();
    }

    /**
     * 处理错误响应
     *
     * @param exchange 交换机对象
     * @param message  错误消息
     * @param status   状态码
     * @return 错误响应
     */
    private Mono<Void> handleError(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String errorBody = String.format("{\"error\":\"%s\",\"message\":\"%s\"}",
                status.getReasonPhrase(), message);

        DataBuffer buffer = response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 是否启用验证
     *
     * @param exchange 交换机对象
     * @return 是否启用
     */
    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getEccCrypto().isEnabled();
    }

    private Mono<Void> sendKeyPairChangedResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.LOCKED); // 423 Locked，或自定义状态码
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String gatewayPublicKey = EccCryptoUtils.publicKeyToBase64(keyPairManager.getKeyPair().getPublic());
        String keyId = keyPairManager.getKeyId();
        String responseBody = String.format(
            "{\"keyId\":\"%s\",\"gatewayPublicKey\":\"%s\",\"needReHandshake\":true}",
            keyId, gatewayPublicKey
        );
        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
