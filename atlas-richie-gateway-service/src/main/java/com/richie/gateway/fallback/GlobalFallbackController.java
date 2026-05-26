package com.richie.gateway.fallback;

import com.richie.contract.model.ApiResult;
import com.richie.gateway.config.FallbackConfig;
import com.richie.gateway.config.GatewayConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局降级响应控制器
 * <p>
 * 支持按 URL 路径配置不同的降级响应消息，配置存放在 Nacos 配置中心，可随时修改自定义内容
 *
 * @author richie696
 * @version 2.0
 * @since 2025-06-27 17:55:18
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class GlobalFallbackController {

    private final GatewayConfig gatewayConfig;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 默认降级响应接口
     * <p>
     * 根据请求路径匹配配置中的降级响应消息，如果未匹配到则使用默认消息
     *
     * @param exchange 请求交换对象
     * @return 降级响应
     */
    @RequestMapping("/fallback/default")
    public Mono<ApiResult<Void>> defaultFallback(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().toString();
        String message = getFallbackMessage(path);
        log.debug("降级响应: path={}, message={}", path, message);
        return Mono.just(ApiResult.error(message));
    }

    /**
     * 根据路径获取降级响应消息
     * <p>
     * 匹配规则：
     * 1. 如果降级配置未启用，返回默认消息
     * 2. 按配置顺序匹配路径模式，第一个匹配成功的路径将使用对应的消息
     * 3. 如果所有路径都不匹配，返回默认消息
     *
     * @param path 请求路径
     * @return 降级响应消息
     */
    private String getFallbackMessage(String path) {
        FallbackConfig fallbackConfig = gatewayConfig.getFallback();

        // 如果降级配置未启用，返回默认消息
        if (!fallbackConfig.isEnabled()) {
            return fallbackConfig.getDefaultMessage();
        }

        // 按配置顺序匹配路径模式
        if (fallbackConfig.getPathMessages() != null && !fallbackConfig.getPathMessages().isEmpty()) {
            for (FallbackConfig.PathMessage pathMessage : fallbackConfig.getPathMessages()) {
                if (pathMessage.getPath() != null && pathMessage.getMessage() != null) {
                    // 使用 Ant 路径匹配
                    if (pathMatcher.match(pathMessage.getPath(), path)) {
                        log.debug("路径匹配成功: pattern={}, path={}, message={}",
                                pathMessage.getPath(), path, pathMessage.getMessage());
                        return pathMessage.getMessage();
                    }
                }
            }
        }

        // 如果所有路径都不匹配，返回默认消息
        return fallbackConfig.getDefaultMessage();
    }
}
