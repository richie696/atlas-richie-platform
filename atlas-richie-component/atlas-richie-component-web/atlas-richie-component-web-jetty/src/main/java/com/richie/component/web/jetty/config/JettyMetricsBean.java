package com.richie.component.web.jetty.config;

/**
 * Jetty Micrometer 指标占位（README.md §5.2 D 阶段）。
 *
 * <p>实际注册逻辑由 {@code spring-boot-starter-actuator} + Micrometer Jetty 绑定器完成
 * （{@code JettyServerThreadPoolMetricsBinder} / {@code JettyConnectionMetricsBinder}）。
 * 本类只携带自定义前缀，绑定器按此前缀注册指标。
 *
 * @author richie696
 * @since 2026-07
 */
public class JettyMetricsBean {

    private final JettyProperties.Metrics metrics;

    public JettyMetricsBean(JettyProperties.Metrics metrics) {
        this.metrics = metrics;
    }

    public JettyProperties.Metrics getMetrics() {
        return metrics;
    }
}