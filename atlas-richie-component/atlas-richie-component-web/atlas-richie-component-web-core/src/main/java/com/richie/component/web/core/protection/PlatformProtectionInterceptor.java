package com.richie.component.web.core.protection;

import com.richie.component.web.core.config.protection.PlatformProtectionProperties;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

/**
 * 平台防护层拦截器（README.md §4.8 入口）。
 * <p>
 * 一个拦截器承担 A 组全部职责 + Gateway 互斥检测：
 * <ol>
 *   <li><strong>Gateway 互斥</strong>：读 {@code X-Forwarded-From-Gateway} header；有则
 *       在 ctx 上设 {@link #GATEWAY_BYPASS_ATTRIBUTE}=true 并把 gateway-id 写入
 *       {@link #GATEWAY_ID_ATTRIBUTE}（B 组拦截器读这个标志决定是否跳过）</li>
 *   <li><strong>RequestSize</strong>：body / header 字节超限 → markShortCircuit + 不调 proceed</li>
 *   <li><strong>LongLived</strong>：路径匹配 SSE/WS/stream patterns → ctx 设
 *       {@code long-lived}=true（§4.4 HangDetection 跳过 watchdog）</li>
 * </ol>
 *
 * <h2>Order</h2>
 * <p>{@link #ORDER} = 100：在 RateLimit(300) / CircuitBreaker(400) 之前执行——A 组是
 * 平台级基础设施，必须先于业务拦截器（防止业务拦截器跑了但其实请求 body 超限）。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class PlatformProtectionInterceptor implements WebInterceptor, Ordered {

    /** Gateway 互斥标志：ctx attribute key，B 组拦截器（如 AnomalyDetection）读取以决定是否跳过。 */
    public static final String GATEWAY_BYPASS_ATTRIBUTE = "platform.web.gateway-bypass";

    /** Gateway id 值（从 {@code X-Forwarded-From-Gateway} header 取），供日志 / 审计。 */
    public static final String GATEWAY_ID_ATTRIBUTE = "platform.web.gateway-id";

    /** Gateway header 名（README.md §4.8.4 契约）。 */
    public static final String GATEWAY_HEADER = "X-Forwarded-From-Gateway";

    /** 拦截器在链中的位置；越早越先执行。 */
    public static final int ORDER = 100;

    private final RequestSizeGuard requestSizeGuard;
    private final LongLivedPathBypass longLivedPathBypass;
    private final PlatformProtectionProperties.RequestSize config;

    public PlatformProtectionInterceptor(RequestSizeGuard requestSizeGuard,
                                         LongLivedPathBypass longLivedPathBypass,
                                         PlatformProtectionProperties.RequestSize config) {
        this.requestSizeGuard = requestSizeGuard;
        this.longLivedPathBypass = longLivedPathBypass;
        this.config = config;
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        String gatewayId = ctx.header(GATEWAY_HEADER);
        if (gatewayId != null && !gatewayId.isBlank()) {
            ctx.setAttribute(GATEWAY_BYPASS_ATTRIBUTE, Boolean.TRUE);
            ctx.setAttribute(GATEWAY_ID_ATTRIBUTE, gatewayId);
            log.debug("Gateway request detected: gatewayId={} path={}", gatewayId, ctx.path());
        }

        var denyOpt = requestSizeGuard.check(ctx);
        if (denyOpt.isPresent()) {
            RequestSizeGuard.RequestSizeDecision decision = denyOpt.get();
            String body = renderBody(decision);
            ctx.markShortCircuit(decision.status(), body);
            log.info("RequestSize deny: status={} reason={} limit={} actual={} path={}",
                    decision.status(), decision.reason(), decision.limit(), decision.actual(), ctx.path());
            return;
        }

        if (longLivedPathBypass.matches(ctx.path())) {
            ctx.setAttribute(LongLivedPathBypass.LONG_LIVED_ATTRIBUTE, Boolean.TRUE);
            log.debug("Long-lived path bypass set: path={}", ctx.path());
        }

        chain.proceed(ctx);
    }

    private String renderBody(RequestSizeGuard.RequestSizeDecision d) {
        String msg = config.getDenyMsg()
                .replace("{reason}", d.reason())
                .replace("{limit}", String.valueOf(d.limit()))
                .replace("{actual}", String.valueOf(d.actual()));
        return ApiResult.error(config.getDenyCode(), msg).toJson();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}