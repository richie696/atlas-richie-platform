package cn.richie696.component.oauth.resource;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Resource Server 安全路径指标；不把 token、subject、tenant 等高基数字段作为 tag。 */
public final class OAuthResourceServerMetrics {

    private final Counter authenticationSuccess;
    private final Counter authenticationFailure;
    private final Counter introspectionFallback;

    public OAuthResourceServerMetrics(MeterRegistry registry) {
        this.authenticationSuccess = Counter.builder("oauth.resource.authentication")
                .tag("result", "success").register(registry);
        this.authenticationFailure = Counter.builder("oauth.resource.authentication")
                .tag("result", "failure").register(registry);
        this.introspectionFallback = Counter.builder("oauth.resource.introspection.fallback")
                .register(registry);
    }

    public void authenticationSucceeded() {
        authenticationSuccess.increment();
    }

    public void authenticationFailed() {
        authenticationFailure.increment();
    }

    public void introspectionFallbackUsed() {
        introspectionFallback.increment();
    }
}
