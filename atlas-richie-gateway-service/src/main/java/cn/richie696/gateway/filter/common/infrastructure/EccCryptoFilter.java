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
package cn.richie696.gateway.filter.common.infrastructure;

import cn.richie696.gateway.config.GatewayConfig;
import cn.richie696.component.i18n.resolver.I18nResolver;
import cn.richie696.gateway.filter.AbstractBaseFilter;
import cn.richie696.gateway.filter.FilterOrder;
import cn.richie696.gateway.handler.KeyPairManager;
import cn.richie696.gateway.service.EccCryptoService;
import cn.richie696.gateway.utils.EccCryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.UUID;

import static cn.richie696.contract.constant.GlobalConstants.*;


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
    private final Environment environment;

    /**
     * 构造函数
     *
     * @param config 网关配置
     * @param i18n   国际化解析器
     */
    public EccCryptoFilter(GatewayConfig config, I18nResolver i18n,
                           EccCryptoService eccCryptoService, KeyPairManager keyPairManager,
                           Environment environment) {
        super(config, i18n);
        this.eccCryptoService = eccCryptoService;
        this.keyPairManager = keyPairManager;
        this.environment = environment;
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

        // 密钥交换不是业务加密路由；即使它位于 excludePaths，也必须由网关本身处理。
        if ("/api/crypto/exchange".equals(path)) {
            return handleKeyExchangeRequest(exchange);
        }

        // 检查是否需要加密处理
        String method = request.getMethod() == null ? null : request.getMethod().name();
        if (!eccCryptoService.shouldEncrypt(path, method)) {
            return chain.filter(exchange);
        }

        // 开发环境的明文降级仅用于尚未接入 ECC 的调用方；已明确携带
        // body-v1 标记的请求必须继续经过解密与响应加密链路，不能被降级
        // 分支直接转发，否则浏览器会收到与协议预期不一致的响应。
        if (isPlaintextFallbackAllowed()
                && !"body-v1".equals(request.getHeaders().getFirst(X_ENCRYPTED_DATA))) {
            log.warn("ECC 明文降级已启用：仅用于开发/测试环境，path={}, method={}", path, method);
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

        String clientPublicKey = request.getHeaders().getFirst(X_CLIENT_PUBLIC_KEY);
        String clientId = request.getHeaders().getFirst(X_CLIENT_ID);

        if (StringUtils.hasText(clientPublicKey) && StringUtils.hasText(clientId)) {
            // 缓存客户端公钥
            eccCryptoService.cacheClientPublicKey(clientId, clientPublicKey);
        }

        // 同一请求的入站解密与出站加密必须绑定同一把网关私钥。不能在异步
        // 响应阶段再次从 KeyPairManager 读取，否则密钥轮换边界上可能对同一
        // HTTP 交互派生出不同的共享密钥，导致客户端无法解密 200 响应。
        PrivateKey gatewayPrivateKey = keyPairManager.getKeyPair().getPrivate();
        return handleEncryptedRequest(exchange, chain, gatewayPrivateKey);
    }

    private Mono<Void> handleKeyExchangeRequest(ServerWebExchange exchange) {
        if (!config.getEccCrypto().isEnabled()) {
            return handleError(exchange, "加密通道未启用", HttpStatus.SERVICE_UNAVAILABLE);
        }
        ServerHttpRequest request = exchange.getRequest();
        String clientPublicKey = request.getHeaders().getFirst(X_CLIENT_PUBLIC_KEY);
        String clientId = request.getHeaders().getFirst(X_CLIENT_ID);
        if (!StringUtils.hasText(clientPublicKey) || !StringUtils.hasText(clientId)) {
            return handleError(exchange, "密钥交换参数不完整", HttpStatus.BAD_REQUEST);
        }
        if (keyPairManager.isExpired()) {
            keyPairManager.refreshKeyPair();
        }
        eccCryptoService.cacheClientPublicKey(clientId, clientPublicKey);
        return handleKeyExchange(exchange);
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
    private Mono<Void> handleEncryptedRequest(ServerWebExchange exchange,
                                              GatewayFilterChain chain,
                                              PrivateKey gatewayPrivateKey) {
        ServerHttpRequest request = exchange.getRequest();
        String clientId = request.getHeaders().getFirst(X_CLIENT_ID);
        String encryptedData = request.getHeaders().getFirst(X_ENCRYPTED_DATA);

        // 如果没有客户端ID，生成一个临时ID
        if (!StringUtils.hasText(clientId)) {
            clientId = UUID.randomUUID().toString();
        }

        String finalClientId = clientId;
        if ("body-v1".equals(encryptedData)) {
            return DataBufferUtils.join(request.getBody())
                    .flatMap(dataBuffer -> {
                        try {
                            byte[] encryptedPayload = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(encryptedPayload);
                            // 为本次请求固定共享密钥，响应阶段复用同一实例，避免
                            // Redis 缓存刷新或网关密钥轮换影响同一次往返。
                            SecretKey sharedKey = eccCryptoService.getOrGenerateSharedKey(
                                    finalClientId,
                                    gatewayPrivateKey
                            );
                            if (sharedKey == null) {
                                return handleError(exchange, "无法建立加密会话", HttpStatus.BAD_REQUEST);
                            }
                            String decryptedData = EccCryptoUtils.decrypt(
                                    new String(encryptedPayload, StandardCharsets.UTF_8),
                                    sharedKey
                            );

                            if (decryptedData == null) {
                                return handleError(exchange, "解密失败", HttpStatus.BAD_REQUEST);
                            }

                            byte[] decryptedPayload = decryptedData.getBytes(StandardCharsets.UTF_8);
                            DataBuffer newBuffer = exchange.getResponse().bufferFactory().wrap(decryptedPayload);
                            HttpHeaders headers = new HttpHeaders();
                            headers.putAll(request.getHeaders());
                            headers.remove(HttpHeaders.CONTENT_LENGTH);
                            headers.remove(X_ENCRYPTED_DATA);
                            headers.setContentLength(decryptedPayload.length);
                            headers.setContentType(MediaType.APPLICATION_JSON);

                            // 创建新的请求
                            ServerHttpRequest newRequest = new ServerHttpRequestDecorator(request) {
                                @Nonnull
                                @Override
                                public HttpHeaders getHeaders() {
                                    return headers;
                                }

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
                            return chain.filter(decorateExchangeForResponse(newExchange, sharedKey));

                        } catch (Exception e) {
                            log.error("处理加密请求失败", e);
                            return handleError(exchange, "处理加密请求失败", HttpStatus.INTERNAL_SERVER_ERROR);
                        } finally {
                            DataBufferUtils.release(dataBuffer);
                        }
                    });
        }

        return handleError(exchange, "该接口要求使用加密通道", HttpStatus.BAD_REQUEST);
    }

    /**
     * 包裹响应，拦截writeWith进行加密
     */
    private ServerWebExchange decorateExchangeForResponse(ServerWebExchange exchange, SecretKey sharedKey) {
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
                            String encrypted = EccCryptoUtils.encrypt(responseBody, sharedKey);
                            if (encrypted != null) {
                                byte[] encryptedPayload = encrypted.getBytes(StandardCharsets.UTF_8);
                                // 下游已写入明文 Content-Length。AES-GCM 产生的 Base64 密文
                                // 长度不同；若保留旧值，Netty/浏览器会截断密文，造成认证标签校验失败。
                                originalResponse.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                                originalResponse.getHeaders().setContentLength(encryptedPayload.length);
                                originalResponse.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
                                originalResponse.getHeaders().set(X_RESPONSE_ENCRYPTED, "true");
                                DataBuffer buffer = bufferFactory.wrap(encryptedPayload);
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
        // 握手属于网关本地协议端点，绝不能在关闭 ECC 时被继续路由到下游服务。
        return "/api/crypto/exchange".equals(exchange.getRequest().getPath().value())
                || config.getEccCrypto().isEnabled();
    }

    private boolean isPlaintextFallbackAllowed() {
        return config.getEccCrypto().isAllowPlaintextInDev()
                && environment.acceptsProfiles(Profiles.of("dev", "test"));
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
