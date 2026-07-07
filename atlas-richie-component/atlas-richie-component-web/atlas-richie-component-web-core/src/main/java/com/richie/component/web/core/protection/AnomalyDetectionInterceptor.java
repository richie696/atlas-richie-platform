package com.richie.component.web.core.protection;

import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Map;

/**
 * 平台防护层 B 组：异常客户端检测（README.md §4.8.2 / §5.2 A-5）。
 * <p>
 * 检测两类异常：
 * <ol>
 *   <li><strong>Bot UA</strong>：User-Agent header 命中 glob 黑名单</li>
 *   <li><strong>IP 黑名单</strong>：客户端 IP 命中单 IP / CIDR 列表</li>
 * </ol>
 * 命中 → markShortCircuit(denyStatus, body) + 不调 proceed；可由后续拦截器（如 §4.5 HookBus）
 * 通过 ctx.attribute("filter.decision") 拿到决策事件。
 *
 * <h2>Gateway 互斥</h2>
 * <p>与 gateway 同部署时（B 组规则）：检测到 {@code X-Forwarded-From-Gateway} header 后
 * 由 §4.8.4 A 组设 {@code gateway-bypass}=true，B 组拦截器见此标志直接放行。
 *
 * <h2>Order</h2>
 * <p>{@link #ORDER} = 200：在 A 组(100) 之后、KeyResolver(250) / RateLimit(300) 之前。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class AnomalyDetectionInterceptor implements WebInterceptor, Ordered {

    /** 决策事件 attribute key，供 §4.5 HookBus 监听。 */
    public static final String DECISION_ATTRIBUTE = "platform.web.anomaly-decision";

    /** 拦截器在链中的位置。 */
    public static final int ORDER = 200;

    private final List<BotUserAgentMatcher> botMatchers;
    private final List<IpBlacklistEntry> ipEntries;
    private final int denyStatus;
    private final String denyCode;
    private final String denyMsg;

    public AnomalyDetectionInterceptor(List<String> botUserAgentPatterns,
                                       List<String> ipBlacklist,
                                       int denyStatus,
                                       String denyCode,
                                       String denyMsg) {
        this.botMatchers = botUserAgentPatterns.stream()
                .map(BotUserAgentMatcher::new)
                .toList();
        this.ipEntries = ipBlacklist.stream()
                .map(IpBlacklistEntry::new)
                .toList();
        this.denyStatus = denyStatus;
        this.denyCode = denyCode;
        this.denyMsg = denyMsg;
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        if (isGatewayBypassed(ctx)) {
            chain.proceed(ctx);
            return;
        }

        String userAgent = ctx.header("User-Agent");
        for (BotUserAgentMatcher m : botMatchers) {
            if (m.matches(userAgent)) {
                String body = renderBody("bot_user_agent");
                ctx.setAttribute(DECISION_ATTRIBUTE,
                        Map.of("type", "bot", "pattern", m.pattern(), "ua", String.valueOf(userAgent)));
                ctx.markShortCircuit(denyStatus, body);
                log.info("Anomaly deny (bot UA): pattern={} ua={} path={}",
                        m.pattern(), userAgent, ctx.path());
                return;
            }
        }

        String xff = ctx.header("X-Forwarded-For");
        if (xff == null) {
            xff = ctx.header("X-Real-IP");
        }
        if (xff != null && !xff.isBlank()) {
            for (IpBlacklistEntry e : ipEntries) {
                if (e.matches(xff)) {
                    String body = renderBody("ip_blacklisted");
                    ctx.setAttribute(DECISION_ATTRIBUTE,
                            Map.of("type", "ip", "rule", e.source(), "ip", xff));
                    ctx.markShortCircuit(denyStatus, body);
                    log.info("Anomaly deny (IP): rule={} ip={} path={}", e.source(), xff, ctx.path());
                    return;
                }
            }
        }

        chain.proceed(ctx);
    }

    private String renderBody(String reason) {
        String msg = denyMsg.replace("{reason}", reason);
        return ApiResult.error(denyCode, msg).toJson();
    }

    private static boolean isGatewayBypassed(WebRequestContext ctx) {
        Boolean flag = ctx.attribute(PlatformProtectionInterceptor.GATEWAY_BYPASS_ATTRIBUTE);
        if (Boolean.TRUE.equals(flag)) {
            return true;
        }
        String header = ctx.header(PlatformProtectionInterceptor.GATEWAY_HEADER);
        return header != null && !header.isBlank();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}