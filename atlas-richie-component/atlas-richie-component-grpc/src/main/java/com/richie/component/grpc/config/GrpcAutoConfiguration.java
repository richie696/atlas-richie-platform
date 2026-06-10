package com.richie.component.grpc.config;

import com.richie.component.grpc.interceptor.GrpcClientHeaderInterceptor;
import com.richie.component.grpc.interceptor.GrpcClientLoggingInterceptor;
import com.richie.component.grpc.interceptor.GrpcClientMetricsInterceptor;
import com.richie.component.grpc.interceptor.GrpcClientSentinelInterceptor;
import com.richie.component.grpc.interceptor.GrpcClientTracingInterceptor;
import com.richie.component.grpc.interceptor.GrpcServerAuthInterceptor;
import com.richie.component.grpc.interceptor.GrpcServerExceptionInterceptor;
import com.richie.component.grpc.interceptor.GrpcServerHeaderInterceptor;
import com.richie.component.grpc.interceptor.GrpcServerLoggingInterceptor;
import com.richie.component.grpc.interceptor.GrpcServerMetricsInterceptor;
import com.richie.component.grpc.interceptor.GrpcServerSentinelInterceptor;
import com.richie.component.grpc.interceptor.GrpcServerTracingInterceptor;
import com.richie.component.grpc.lifecycle.GrpcServerGracefulShutdown;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * gRPC 组件自动配置
 *
 * <p>注册全部 gRPC 拦截器（头信息透传 / 鉴权 / 指标 / 日志 / 异常映射）
 * 以及优雅停服生命周期 Bean。</p>
 *
 * <p>本组件仅提供拦截器 Bean 定义，不管理 gRPC Server / Channel 的启动。
 * 使用者需在应用代码中将拦截器注册到对应的 {@code ServerBuilder} 或 {@code ManagedChannelBuilder} 上：</p>
 * <pre>{@code
 * var server = NettyServerBuilder.forPort(port)
 *     .intercept(grpcServerHeaderInterceptor)
 *     .intercept(grpcServerLoggingInterceptor)
 *     .intercept(grpcServerAuthInterceptor)
 *     .intercept(grpcServerMetricsInterceptor)
 *     .intercept(grpcServerExceptionInterceptor)
 *     .addService(myService)
 *     .build();
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(GrpcProperties.class)
public class GrpcAutoConfiguration {

    // ==================== 头信息透传 ====================

    /**
     * gRPC 服务端请求头拦截器，从 Metadata 提取白名单头信息注入 {@code HeaderContextHolder}
     */
    @Bean
    @ConditionalOnProperty(name = "richie.grpc.server.header-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcServerHeaderInterceptor grpcServerHeaderInterceptor(GrpcProperties properties) {
        return new GrpcServerHeaderInterceptor(properties.getHeaderPropagation().getHeaders());
    }

    /**
     * gRPC 客户端请求头拦截器，从 {@code HeaderContextHolder} 读取白名单头信息写入 Metadata
     */
    @Bean
    @ConditionalOnProperty(name = "richie.grpc.client.header-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcClientHeaderInterceptor grpcClientHeaderInterceptor(GrpcProperties properties) {
        return new GrpcClientHeaderInterceptor(properties.getHeaderPropagation().getHeaders());
    }

    // ==================== 指标采集 ====================

    /**
     * gRPC 服务端指标拦截器，基于 Micrometer 记录 requests / duration / errors 三指标
     *
     * <p>仅在容器中存在 {@link MeterRegistry} Bean 且 {@code richie.grpc.server.metrics-enabled=true} 时激活</p>
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(name = "richie.grpc.server.metrics-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcServerMetricsInterceptor grpcServerMetricsInterceptor(MeterRegistry meterRegistry) {
        return new GrpcServerMetricsInterceptor(meterRegistry);
    }

    /**
     * gRPC 客户端指标拦截器，基于 Micrometer 记录客户端请求 / 延迟 / 错误
     *
     * <p>仅在容器中存在 {@link MeterRegistry} Bean 且 {@code richie.grpc.client.metrics-enabled=true} 时激活</p>
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(name = "richie.grpc.client.metrics-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcClientMetricsInterceptor grpcClientMetricsInterceptor(MeterRegistry meterRegistry) {
        return new GrpcClientMetricsInterceptor(meterRegistry);
    }

    // ==================== 日志 ====================

    /**
     * gRPC 服务端日志拦截器，记录每次调用的方法名、状态码和耗时
     */
    @Bean
    @ConditionalOnProperty(name = "richie.grpc.server.logging-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcServerLoggingInterceptor grpcServerLoggingInterceptor() {
        return new GrpcServerLoggingInterceptor();
    }

    // ==================== 异常映射 ====================

    /**
     * gRPC 服务端异常拦截器，将 {@link RuntimeException} 映射为 gRPC {@code Status}
     */
    @Bean
    @ConditionalOnProperty(name = "richie.grpc.server.exception-mapping-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcServerExceptionInterceptor grpcServerExceptionInterceptor() {
        return new GrpcServerExceptionInterceptor();
    }

    // ==================== JWT 鉴权 ====================

    /**
     * gRPC 服务端 JWT 鉴权拦截器，验证请求 Metadata 中的 access token
     *
     * <p>仅在 {@code richie.grpc.server.auth-enabled=true} 时激活</p>
     */
    @Bean
    @ConditionalOnProperty(name = "richie.grpc.server.auth-enabled", havingValue = "true")
    public GrpcServerAuthInterceptor grpcServerAuthInterceptor(GrpcProperties properties) {
        return new GrpcServerAuthInterceptor(properties.getServer().getAuthSecret());
    }

    // ==================== Sentinel 限流/熔断/降级 ====================

    /**
     * gRPC 服务端 Sentinel 拦截器，限流 → {@code RESOURCE_EXHAUSTED}，熔断 → {@code UNAVAILABLE}
     *
     * <p>仅在 classpath 存在 {@code sentinel-core} 且 {@code richie.grpc.server.sentinel-enabled=true} 时激活</p>
     */
    @Bean
    @ConditionalOnClass(name = "com.alibaba.csp.sentinel.SphU")
    @ConditionalOnProperty(name = "richie.grpc.server.sentinel-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcServerSentinelInterceptor grpcServerSentinelInterceptor() {
        return new GrpcServerSentinelInterceptor();
    }

    // ==================== 链路追踪 ====================

    /**
     * gRPC 服务端链路追踪拦截器，创建 OpenTelemetry SERVER span 并提取 W3C traceparent
     *
     * <p>仅在 classpath 存在 {@code opentelemetry-api} 且 {@code richie.grpc.server.tracing-enabled=true} 时激活</p>
     */
    @Bean
    @ConditionalOnClass(name = "io.opentelemetry.api.GlobalOpenTelemetry")
    @ConditionalOnProperty(name = "richie.grpc.server.tracing-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcServerTracingInterceptor grpcServerTracingInterceptor() {
        return new GrpcServerTracingInterceptor();
    }

    /**
     * gRPC 客户端链路追踪拦截器，创建 OpenTelemetry CLIENT span 并注入 W3C traceparent
     *
     * <p>仅在 classpath 存在 {@code opentelemetry-api} 且 {@code richie.grpc.client.tracing-enabled=true} 时激活</p>
     */
    @Bean
    @ConditionalOnClass(name = "io.opentelemetry.api.GlobalOpenTelemetry")
    @ConditionalOnProperty(name = "richie.grpc.client.tracing-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcClientTracingInterceptor grpcClientTracingInterceptor() {
        return new GrpcClientTracingInterceptor();
    }

    // ==================== 客户端 Sentinel ====================

    /**
     * gRPC 客户端 Sentinel 拦截器，出站方向限流 / 熔断保护
     *
     * <p>仅在 classpath 存在 {@code sentinel-core} 且 {@code richie.grpc.client.sentinel-enabled=true} 时激活</p>
     */
    @Bean
    @ConditionalOnClass(name = "com.alibaba.csp.sentinel.SphU")
    @ConditionalOnProperty(name = "richie.grpc.client.sentinel-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcClientSentinelInterceptor grpcClientSentinelInterceptor() {
        return new GrpcClientSentinelInterceptor();
    }

    // ==================== 客户端日志 ====================

    /**
     * gRPC 客户端日志拦截器，记录出站调用的方法名、状态码和耗时
     */
    @Bean
    @ConditionalOnProperty(name = "richie.grpc.client.logging-enabled", havingValue = "true", matchIfMissing = true)
    public GrpcClientLoggingInterceptor grpcClientLoggingInterceptor() {
        return new GrpcClientLoggingInterceptor();
    }

    // ==================== 优雅停服 ====================

    /**
     * gRPC 服务端优雅停服，监听 Spring {@code ContextClosedEvent} 平滑关闭所有注册的 Server
     */
    @Bean
    public GrpcServerGracefulShutdown grpcServerGracefulShutdown(GrpcProperties properties) {
        return new GrpcServerGracefulShutdown(properties.getServer().getGracefulShutdownTimeout());
    }
}
