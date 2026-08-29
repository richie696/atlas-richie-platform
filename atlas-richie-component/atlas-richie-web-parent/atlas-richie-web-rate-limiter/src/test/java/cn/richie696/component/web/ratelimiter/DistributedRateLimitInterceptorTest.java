package cn.richie696.component.web.ratelimiter;

import cn.richie696.component.cache.ops.LimiterOps;
import cn.richie696.component.web.core.config.ratelimit.RateLimitProperties;
import cn.richie696.component.web.core.metrics.WebMetrics;
import cn.richie696.component.web.core.spi.KeyResolver;
import cn.richie696.component.web.core.spi.support.DefaultWebInterceptorChain;
import cn.richie696.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedRateLimitInterceptorTest {

    @Test
    void allowsWithTrustedGatewayIdentityAndUsesSharedRedisKey() throws Exception {
        AtomicReference<String> capturedKey = new AtomicReference<>();
        LimiterOps limiter = (key, max, window) -> {
            capturedKey.set(key);
            assertThat(max).isEqualTo(2);
            assertThat(window).isEqualTo(1);
            return true;
        };
        AtomicBoolean proceeded = new AtomicBoolean();
        DistributedRateLimitInterceptor interceptor = interceptor(limiter, ctx -> ctx.header("X-Client-Id"));

        var context = context("user-42", true);
        interceptor.intercept(context, new DefaultWebInterceptorChain(List.of((ignored, chain) -> proceeded.set(true))));

        assertThat(proceeded).isTrue();
        assertThat(context.isShortCircuited()).isFalse();
        assertThat(capturedKey.get()).startsWith("richie:web:rate-limit:user-42:");
    }

    @Test
    void rejectsWhenGatewayIdentityIsMissingBeforeCallingRedis() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        DistributedRateLimitInterceptor interceptor = interceptor((key, max, window) -> {
            invoked.set(true);
            return true;
        }, ctx -> ctx.header("X-Client-Id"));

        var context = context("user-42", false);
        interceptor.intercept(context, new DefaultWebInterceptorChain(List.of()));

        assertThat(context.isShortCircuited()).isTrue();
        assertThat(context.responseStatus()).isEqualTo(401);
        assertThat(invoked).isFalse();
    }

    @Test
    void returnsConfiguredRateLimitResponseWhenRedisDenies() throws Exception {
        DistributedRateLimitInterceptor interceptor = interceptor((key, max, window) -> false,
                ctx -> ctx.header("X-Client-Id"));
        var context = context("user-42", true);

        interceptor.intercept(context, new DefaultWebInterceptorChain(List.of()));

        assertThat(context.isShortCircuited()).isTrue();
        assertThat(context.responseStatus()).isEqualTo(429);
        assertThat(context.shortCircuitBody()).contains("RATE_LIMITED", "user-42");
    }

    private static DistributedRateLimitInterceptor interceptor(LimiterOps limiter, KeyResolver resolver) {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.RouteConfig route = new RateLimitProperties.RouteConfig();
        route.setPattern("/api/orders/**");
        route.setPermitsPerSecond(2);
        properties.setRoutes(List.of(route));
        return new DistributedRateLimitInterceptor(limiter, properties, resolver, WebMetrics.noop());
    }

    private static MutableWebRequestContext context(String clientId, boolean gatewayIdentity) {
        var builder = MutableWebRequestContext.builder().method("GET").path("/api/orders/1")
                .header("X-Client-Id", clientId);
        if (gatewayIdentity) {
            builder.header("X-Forwarded-From-Gateway", "test:local:pod-1");
        }
        return builder.build();
    }
}
