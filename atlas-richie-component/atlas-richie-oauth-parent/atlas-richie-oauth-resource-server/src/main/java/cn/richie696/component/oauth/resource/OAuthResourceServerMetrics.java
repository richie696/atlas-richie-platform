package cn.richie696.component.oauth.resource;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Resource Server 鉴权路径的 Micrometer 指标封装，只暴露低基数语义事件。
 *
 * <p>处于 {@link ResourceServerAuthenticator} 与 Prometheus / Grafana 等监控系统之间：
 * 上游 Authenticator 在鉴权成功、JWT 失败触发 introspection fallback、整体失败三个
 * 关键节点调用本组件打点，下游通过已注册的 {@code MeterRegistry} 输出
 * {@code oauth.resource.authentication{result=success|failure}} 与
 * {@code oauth.resource.introspection.fallback} 三条 Counter。它是有副作用最轻的 SPI，
 * Resource Server Authenticator 通过 null 检查保证"未注入指标时不抛 NPE"。
 *
 * <p>解决"Resource Server 自带的鉴权指标把 token、subject、tenant 等高基数字段作为
 * tag，Prometheus 出现时间序列爆炸"的运维事故风险，把指标面收敛到 result / fallback
 * 两个固定标签，既保留审计价值又避免监控后端被击穿。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
