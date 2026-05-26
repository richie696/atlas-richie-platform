package com.richie.gateway.filter.internal.business;

import com.richie.contract.model.ApiResult;
import com.richie.gateway.config.GatewayConfig;
import com.richie.component.i18n.resolver.I18nResolver;
import com.richie.gateway.service.DuplicateSubmitService;
import com.richie.gateway.filter.AbstractBaseFilter;
import com.richie.gateway.filter.FilterOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 防重复提交过滤器
 * 防止用户在短时间内重复提交相同请求
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
@Component
public class DuplicateSubmitFilter extends AbstractBaseFilter {

    private final DuplicateSubmitService duplicateSubmitService;

    public DuplicateSubmitFilter(GatewayConfig config, I18nResolver i18n, DuplicateSubmitService duplicateSubmitService) {
        super(config, i18n);
        this.duplicateSubmitService = duplicateSubmitService;
    }

    public int getOrder() {
        return FilterOrder.DUPLICATE_SUBMIT_FILTER.getOrder();
    }

    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 检查是否需要防重复提交
        if (!duplicateSubmitService.shouldCheckDuplicateSubmit(path)) {
            return chain.filter(exchange);
        }

        // 只对POST、PUT、DELETE等写操作进行防重复提交检查
        String method = request.getMethod().name();
        if (!isWriteOperation(method)) {
            return chain.filter(exchange);
        }

        // 获取请求体内容
        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    try {
                        String requestBody = dataBuffer.toString(StandardCharsets.UTF_8);

                        // 检查是否为重复提交
                        if (duplicateSubmitService.isDuplicateSubmit(request, requestBody)) {
                            log.warn("检测到重复提交请求: {} {}", method, path);
                            return handleDuplicateSubmit(exchange);
                        }

                        // 记录请求提交
                        duplicateSubmitService.recordSubmit(request, requestBody);

                        // 创建新的请求体
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

                        // 继续处理请求
                        return chain.filter(newExchange);

                    } catch (Exception e) {
                        log.error("处理防重复提交请求失败", e);
                        return handleError(exchange, "处理请求失败", HttpStatus.INTERNAL_SERVER_ERROR);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .onErrorResume(throwable -> {
                    // 处理请求体读取失败的情况，可能是空请求体
                    log.debug("请求体读取失败，可能是空请求体: {}", throwable.getMessage());

                    // 检查是否为重复提交（无请求体）
                    if (duplicateSubmitService.isDuplicateSubmit(request, null)) {
                        log.warn("检测到重复提交请求（无请求体）: {} {}", method, path);
                        return handleDuplicateSubmit(exchange);
                    }

                    // 记录请求提交
                    duplicateSubmitService.recordSubmit(request, null);

                    // 继续处理请求
                    return chain.filter(exchange);
                });
    }

    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getDuplicateSubmit().isEnabled();
    }

    /**
     * 判断是否为写操作
     *
     * @param method HTTP方法
     * @return 是否为写操作
     */
    private boolean isWriteOperation(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
    }

    /**
     * 处理重复提交
     *
     * @param exchange 交换机对象
     * @return 错误响应
     */
    private Mono<Void> handleDuplicateSubmit(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS); // 429 Too Many Requests
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var duplicateSubmitConfig = config.getDuplicateSubmit();
        var errorResult = ApiResult.error(duplicateSubmitConfig.getErrorCode(), duplicateSubmitConfig.getErrorMessage());
        DataBuffer buffer = response.bufferFactory().wrap(errorResult.toBytes());
        return response.writeWith(Mono.just(buffer));
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
        var errorResult = ApiResult.error(status.getReasonPhrase(), message);
        DataBuffer buffer = response.bufferFactory().wrap(errorResult.toBytes());
        return response.writeWith(Mono.just(buffer));
    }
}
