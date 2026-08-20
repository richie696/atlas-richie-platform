package cn.richie696.gateway.filter.common.infrastructure;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.security.SecureRandom;

/**
 * 全链路请求 ID 过滤器（{@link Ordered#HIGHEST_PRECEDENCE}）。
 * <p>
 * 在网关入口处保证每一次响应都携带稳定的 {@code X-Request-Id}，贯穿五个传播点：
 * </p>
 * <ol>
 *   <li>ServerWebExchange attributes（{@code requestId} key）</li>
 *   <li>Reactor Context（{@code requestId} key）</li>
 *   <li>SLF4J MDC（{@code requestId} key）</li>
 *   <li>下游请求头（{@code X-Request-Id}）</li>
 *   <li>客户端响应头（{@code X-Request-Id}，无论成功 / 失败）</li>
 * </ol>
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li>请求入口若未携带 {@code X-Request-Id}，则生成 32 位小写十六进制随机 ID（16 字节）。</li>
 *   <li>同时实现 {@link GlobalFilter} 与 {@link WebFilter}：前者覆盖有路由的请求，后者覆盖
 *       未匹配路由（404）的请求——SCG 的 {@code RoutePredicateHandlerMapping} 在未匹配时直接
 *       返回 404 不调用 GlobalFilter 链，必须依靠 WebFilter 在更早的 HTTP 处理器层注入。</li>
 *   <li>使用 {@link Ordered#HIGHEST_PRECEDENCE} 早于 I18N_FILTER，确保后续过滤器 / 服务
 *       可以从 attributes / Reactor Context / MDC 中读取到请求 ID。</li>
 *   <li>MDC 通过 {@code doFirst} / {@code doFinally} 包夹，确保在订阅边界设置并在终止时清理，
 *       避免线程复用导致 MDC 串扰。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-04
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, WebFilter, Ordered {

    /**
     * HTTP 头名称（请求 / 响应 / 下游一致）。
     */
    public static final String HEADER_NAME = "X-Request-Id";

    /**
     * ServerWebExchange attributes 键。
     */
    public static final String ATTRIBUTE_KEY = "requestId";

    /**
     * Reactor Context 键。
     */
    public static final String CONTEXT_KEY = "requestId";

    /**
     * SLF4J MDC 键。
     */
    public static final String MDC_KEY = "requestId";

    /**
     * 随机 ID 字节长度（16 字节 → 32 位小写十六进制字符串）。
     */
    private static final int RANDOM_ID_BYTE_LENGTH = 16;

    /**
     * 随机 ID 字符长度（每字节 2 个十六进制字符）。
     */
    private static final int RANDOM_ID_HEX_LENGTH = RANDOM_ID_BYTE_LENGTH * 2;

    /**
     * 随机 ID 生成器（线程安全）。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 十六进制字符查找表，避免 {@code String.format} 的开销。
     */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return doFilter(exchange, chain::filter);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return doFilter(exchange, chain::filter);
    }

    /**
     * 统一的请求 ID 注入逻辑（同时供 GlobalFilter 与 WebFilter 调用）。
     * <p>
     * 幂等：如果 attributes 已有 requestId 则直接复用（避免重复生成），保证同一请求两条链路
     * （WebFilter + GlobalFilter）只产生一个 ID。
     * </p>
     *
     * @param exchange  ServerWebExchange
     * @param filterFn  链路 filter 调用（来自 GlobalFilter 或 WebFilter）
     * @return 过滤链返回值
     */
    private Mono<Void> doFilter(ServerWebExchange exchange, java.util.function.Function<ServerWebExchange, Mono<Void>> filterFn) {
        Object existing = exchange.getAttributes().get(ATTRIBUTE_KEY);
        final String requestId;
        if (existing instanceof String s && !s.isEmpty()) {
            requestId = s;
        } else {
            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            String headerRequestId = requestHeaders.getFirst(HEADER_NAME);
            requestId = isBlank(headerRequestId) ? generateRequestId() : headerRequestId;
        }

        // 1. 写入 ServerWebExchange attributes（幂等）
        exchange.getAttributes().put(ATTRIBUTE_KEY, requestId);

        // 2. 装饰下游请求头（写入 X-Request-Id）
        ServerHttpRequest decoratedRequest = exchange.getRequest().mutate()
                .header(HEADER_NAME, requestId)
                .build();
        ServerWebExchange decoratedExchange = exchange.mutate()
                .request(decoratedRequest)
                .build();

        // 3. 写入响应头（无论成功 / 失败都包含）
        decoratedExchange.getResponse().getHeaders().set(HEADER_NAME, requestId);

        return filterFn.apply(decoratedExchange)
                .contextWrite(Context.of(CONTEXT_KEY, requestId))
                .doFirst(() -> MDC.put(MDC_KEY, requestId))
                .doFinally(signal -> MDC.remove(MDC_KEY));
    }

    /**
     * 生成 32 位小写十六进制随机 ID。
     *
     * @return 32 位随机 ID
     */
    private static String generateRequestId() {
        byte[] bytes = new byte[RANDOM_ID_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        char[] hex = new char[RANDOM_ID_HEX_LENGTH];
        for (int i = 0; i < RANDOM_ID_BYTE_LENGTH; i++) {
            int value = bytes[i] & 0xff;
            hex[i * 2] = HEX_CHARS[value >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[value & 0x0f];
        }
        return new String(hex);
    }

    /**
     * 判空工具（避免空字符串穿透到下游）。
     *
     * @param value 字符串
     * @return true 表示 null 或空白
     */
    private static boolean isBlank(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
