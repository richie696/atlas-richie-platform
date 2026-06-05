package com.richie.component.redis.streammq.config.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 根据 RedisStreamTracingProperties 初始化 OpenTelemetry，并按配置启用导出器与采样。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15 16:44:51
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(OpenTelemetry.class)
@EnableConfigurationProperties(RedisStreamTracingProperties.class)
@ConditionalOnProperty(prefix = "platform.cache.redis.stream.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamTracingAutoConfiguration {

    /** 链路追踪配置属性 */
    private final RedisStreamTracingProperties props;

    /**
     * 创建 OpenTelemetry 对象
     *
     * @return OpenTelemetry
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry openTelemetry() {
        // 检查是否已经存在 Java Agent 配置的 GlobalOpenTelemetry
        OpenTelemetry globalOtel = GlobalOpenTelemetry.get();
        if (globalOtel != null && !globalOtel.getPropagators().getTextMapPropagator().getClass().getSimpleName().contains("Noop")) {
            log.info("检测到 Java Agent 配置的 GlobalOpenTelemetry，跳过应用内配置");
            return globalOtel;
        }

        log.info("未检测到 Java Agent 配置，使用应用内 OpenTelemetry 配置");

        // 资源信息（服务名/版本）
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), props.getServiceName(),
                        AttributeKey.stringKey("service.version"), props.getServiceVersion()
                )));

        // 采样器
        Sampler sampler = Sampler.traceIdRatioBased(Math.max(0.0, Math.min(1.0, props.getSampling().getProbability())));

        // 导出器集合
        List<SpanExporter> exporters = new ArrayList<>();

        // OTLP 导出器（支持 http 协议，若以后需要可扩展 grpc）
        if (props.getOtlp().isEnabled()) {
            // 检查是否配置了有效的端点
            if (props.getOtlp().getEndpoint() == null || props.getOtlp().getEndpoint().trim().isEmpty()) {
                log.warn("OTLP 导出器已启用但未配置端点，跳过 OTLP 导出器创建");
            } else {
                String protocol = String.valueOf(props.getOtlp().getProtocol()).toLowerCase();
                SpanExporter otlpExporter;
                if ("grpc".equals(protocol)) {
                    otlpExporter = OtlpGrpcSpanExporter.builder()
                            .setEndpoint(props.getOtlp().getEndpoint())
                            .setTimeout(Duration.ofSeconds(props.getOtlp().getTimeoutSeconds()))
                            .build();
                } else {
                    otlpExporter = OtlpHttpSpanExporter.builder()
                            .setEndpoint(props.getOtlp().getEndpoint())
                            .setTimeout(Duration.ofSeconds(props.getOtlp().getTimeoutSeconds()))
                            .build();
                }
                exporters.add(otlpExporter);
                log.debug("启用 OTLP 导出器: endpoint={}, protocol={}", props.getOtlp().getEndpoint(), protocol);
            }
        }

        // Zipkin 导出器
        if (props.getZipkin().isEnabled()) {
            SpanExporter zipkinExporter = ZipkinSpanExporter.builder()
                    .setEndpoint(props.getZipkin().getEndpoint())
                    .build();
            exporters.add(zipkinExporter);
            log.debug("启用 Zipkin 导出器: endpoint={}", props.getZipkin().getEndpoint());
        }

        // Logging 导出器（控制台日志）
        if (props.getLogging().isEnabled()) {
            SpanExporter loggingExporter = LoggingSpanExporter.create();
            exporters.add(loggingExporter);
            log.debug("启用 Logging 导出器: level={}", props.getLogging().getLevel());
        }

        // 构建 TracerProvider，并挂载导出器
        SdkTracerProviderBuilder providerBuilder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(sampler);

        // 使用批处理导出器（生产建议），若仅启用 Logging 则简单处理器也可
        for (SpanExporter exporter : exporters) {
            if (exporter instanceof LoggingSpanExporter) {
                providerBuilder.addSpanProcessor(SimpleSpanProcessor.create(exporter));
            } else {
                providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter).build());
            }
        }

        SdkTracerProvider tracerProvider = providerBuilder.build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        log.info("OpenTelemetry 初始化完成：service.name={}, service.version={}, exporters={}",
                props.getServiceName(), props.getServiceVersion(), exporters.size());

        return sdk;
    }
}


