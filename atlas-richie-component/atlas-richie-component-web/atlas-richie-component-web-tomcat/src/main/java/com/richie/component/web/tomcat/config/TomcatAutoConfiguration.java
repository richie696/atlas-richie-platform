/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.tomcat.config;
import com.richie.component.web.tomcat.executor.TomcatThreadPoolUpdater;
import com.richie.component.web.tomcat.valve.JsonAccessLogValve;
import com.richie.component.web.tomcat.valve.StatisticValve;
import com.richie.component.web.tomcat.valve.TraceIdInjectValve;
import org.apache.catalina.Context;
import org.apache.catalina.Pipeline;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Embedded Tomcat 专属自动配置
 *
 * @author richie696
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.apache.catalina.startup.Tomcat",
        "org.apache.coyote.http2.Http2Protocol"
})
@EnableConfigurationProperties(TomcatProperties.class)
public class TomcatAutoConfiguration {

    /**
     * Phase 3: Trace ID 注入 Valve（默认启用）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.web.tomcat.trace-id", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceIdInjectValve traceIdInjectValve(TomcatProperties properties) {
        return new TraceIdInjectValve(
                properties.getTraceId().getHeader(),
                properties.getTraceId().isGenerateIfMissing());
    }

    /**
     * Phase 3: JSON 格式 Access Log Valve（默认禁用）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.web.tomcat.access-log", name = "enabled", havingValue = "true")
    public JsonAccessLogValve jsonAccessLogValve() {
        return new JsonAccessLogValve(line -> System.out.println(line));
    }

    /**
     * Phase 4: Statistic Valve（基于 Micrometer）。
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "platform.component.web.tomcat.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StatisticValve statisticValve(MeterRegistry meterRegistry, TomcatProperties properties) {
        return new StatisticValve(meterRegistry, properties.getMetrics().getPrefix());
    }

    /**
     * Phase 3+4: 将 TraceId / Statistic / AccessLog Valve 注入到 Tomcat 容器。
     *
     * <p>实现为 {@link WebServerFactoryCustomizer}，Spring Boot 4 自动将其注册到
     * {@code TomcatServletWebServerFactory}。通过 {@code addContextCustomizers} 在
     * {@code StandardContext} 启动前把 valve 加到 {@code StandardHost.getPipeline()}。</p>
     *
     * <p>使用 {@link ObjectProvider} 延迟查找，可选 valve 不存在时调用
     * {@link ObjectProvider#ifAvailable} 跳过。</p>
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatValveInstaller(
            ObjectProvider<TraceIdInjectValve> traceIdProvider,
            ObjectProvider<StatisticValve> statisticProvider,
            ObjectProvider<JsonAccessLogValve> accessLogProvider) {
        return factory -> factory.addContextCustomizers(context ->
                injectValves(context, traceIdProvider, statisticProvider, accessLogProvider));
    }

    private static void injectValves(Context context,
                                       ObjectProvider<TraceIdInjectValve> traceIdProvider,
                                       ObjectProvider<StatisticValve> statisticProvider,
                                       ObjectProvider<JsonAccessLogValve> accessLogProvider) {
        if (!(context instanceof StandardContext sc)) {
            return;
        }
        if (!(sc.getParent() instanceof StandardHost host)) {
            return;
        }
        Pipeline pipeline = host.getPipeline();
        if (pipeline == null) {
            return;
        }
        traceIdProvider.ifAvailable(pipeline::addValve);
        statisticProvider.ifAvailable(pipeline::addValve);
        accessLogProvider.ifAvailable(pipeline::addValve);
    }

    /**
     * Phase 5: Executor 运行时更新器。
     *
     * <p>{@link Lazy} 注入 {@code WebServer} — Spring Boot 启动过程中才会创建，
     * {@code Lazy} 引用保证 updater 只在被实际调用时才解析底层 Catalina Server，
     * 避免 BeanPostProcessor 阶段循环依赖。</p>
     */
    @Bean
    public TomcatThreadPoolUpdater tomcatThreadPoolUpdater(@Lazy WebServer webServer) {
        return new TomcatThreadPoolUpdater(webServer);
    }

    /**
     * 可选 Spring Cloud Config 刷新事件监听器。
     *
     * <p>零配置中心依赖 — 不 import {@code EnvironmentChangeEvent}，运行时通过
     * {@code event.getClass().getName()} 判定事件类型。Spring Cloud 不存在时静默。</p>
     */
    @Bean
    public ApplicationListener<?> tomcatExecutorRefreshListener(TomcatThreadPoolUpdater updater) {
        return event -> {
            if (event.getClass().getName().equals(
                    "org.springframework.cloud.context.environment.EnvironmentChangeEvent")) {
                updater.refresh();
            }
        };
    }
}
