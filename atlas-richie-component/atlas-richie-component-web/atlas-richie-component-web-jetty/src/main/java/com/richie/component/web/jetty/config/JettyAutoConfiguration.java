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
package com.richie.component.web.jetty.config;
import com.richie.component.web.jetty.handler.JsonAccessLogHandler;
import com.richie.component.web.jetty.handler.TraceIdInjectHandler;
import com.richie.component.web.jetty.management.JettyThreadPoolUpdater;
import com.richie.component.web.jetty.metrics.StatisticHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jetty.JettyServerThreadPoolMetrics;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Embedded Jetty 12 专属自动配置（完整 Phase 1-5 集成）。
 *
 * <p>仅在 classpath 存在 {@link Server} 且为 SERVLET Web 应用时激活。
 * 覆盖 Phase 1-5 全部能力：</p>
 * <ol>
 *   <li>Phase 1: QueuedThreadPool + Connector 调优（{@link JettyProductionCustomizer}）</li>
 *   <li>Phase 2: HTTP/2 一键启用（Jetty 12 原生）</li>
 *   <li>Phase 3: Handler 链（access log、trace ID 注入）</li>
 *   <li>Phase 4: 指标采集（由 Spring Boot Actuator + Micrometer 桥接）</li>
 *   <li>Phase 5: 优雅停服（由 Spring Boot 默认处理）</li>
 * </ol>
 *
 * <p>Phase 2-4 各项通过 {@code @ConditionalOnProperty} 细粒度启用，业务方可按需关闭。
 * Phase 3 的 Handler 通过 {@code @ConditionalOnProperty} 控制是否插入 Handler 链。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.eclipse.jetty.server.Server")
@EnableConfigurationProperties(JettyProperties.class)
@Slf4j
public class JettyAutoConfiguration {

    /**
     * Phase 3: JSON Access Log Handler（默认禁用，业务方通过 {@code platform.component.web.jetty.access-log.enabled=true} 启用）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.web.jetty.access-log", name = "enabled", havingValue = "true")
    public JsonAccessLogHandler jsonAccessLogHandler(JettyProperties properties) {
        return new JsonAccessLogHandler(line -> {
            // 默认输出到 stdout；生产环境可扩展为写入文件
            System.out.println(line);
        });
    }

    /**
     * Phase 3: Trace ID 注入 Handler（默认启用，Phase 1-4 全程开启以串联 request_id）。
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.web.jetty.trace-id", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceIdInjectHandler traceIdInjectHandler(JettyProperties properties) {
        return new TraceIdInjectHandler(
                properties.getTraceId().getHeader(),
                properties.getTraceId().isGenerateIfMissing());
    }

    /**
     * Phase 4: 请求指标采集 Handler。
     *
     * <p>仅在容器中存在 {@link MeterRegistry} Bean 且指标功能启用时创建。
     * 指标前缀通过 {@code platform.component.web.jetty.metrics.prefix} 配置。</p>
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "platform.component.web.jetty.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StatisticHandler statisticHandler(MeterRegistry meterRegistry, JettyProperties properties) {
        return new StatisticHandler(meterRegistry, properties.getMetrics().getPrefix());
    }

    /**
     * QueuedThreadPool 指标暴露给 Actuator。
     *
     * <p>将 Jetty 线程池的各项指标注册到 Micrometer，可通过
     * {@code GET /actuator/metrics/jetty.threads.current} 等端点查看。
     * 仅在存在 {@link MeterRegistry} Bean 时激活。</p>
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public MeterBinder jettyThreadPoolMetrics(Server server) {
        return registry -> {
            ThreadPool pool = server.getThreadPool();
            if (pool instanceof QueuedThreadPool) {
                new JettyServerThreadPoolMetrics((QueuedThreadPool) pool, Tags.empty()).bindTo(registry);
            }
        };
    }

    /**
     * QueuedThreadPool 运行时更新器。
     *
     * <p>零配置中心依赖。消费方在 Nacos/Apollo/Spring Cloud Config 监听器中
     * 调用 {@link JettyThreadPoolUpdater#refresh()} 即可动态更新线程池参数。</p>
     *
     * <p>使用 {@link Lazy} 注入 {@link Server} 以避免 BeanPostProcessor 阶段的循环依赖。
     * updater 只在首次被实际调用时才解析 Server 引用，此时所有后处理器已完成。</p>
     */
    @Bean
    public JettyThreadPoolUpdater jettyThreadPoolUpdater(@Lazy Server server) {
        return new JettyThreadPoolUpdater(server);
    }

    /**
     * 可选 Spring Cloud Config 刷新事件监听器。
     *
     * <p>不 import {@code EnvironmentChangeEvent}，避免编译期依赖 spring-cloud-context。
     * 运行时类型判断：若 classpath 存在 Spring Cloud，{@code POST /actuator/refresh}
     * 或配置中心推送会自动触发线程池更新；否则此监听器静默不做事。</p>
     */
    @Bean
    public ApplicationListener<?> jettyThreadPoolEnvironmentRefreshListener(JettyThreadPoolUpdater updater) {
        return event -> {
            if (event.getClass().getName().equals(
                    "org.springframework.cloud.context.environment.EnvironmentChangeEvent")) {
                updater.refresh();
            }
        };
    }

    /**
     * Jetty 12 Handler 链装配器（在 {@link Server#start()} 之前完成包装）。
     * <p>通过 {@link WebServerFactoryCustomizer} + {@code addServerCustomizers} 把
     * handler 链包装 lambda 注入到 {@code JettyServletWebServerFactory}——
     * 该 lambda 在 {@code factory.getWebServer()} 内部、{@link Server#start()}
     * <strong>之前</strong>执行，可安全调用 {@link Server#setHandler(Handler)}。
     * <p>采用 {@link ObjectProvider} 软引用 handler bean：handler 各自受
     * {@code @ConditionalOnXxx} 控制，未启用时 {@code getIfAvailable()} 返回 null，组装 lambda
     * 自动跳过对应层级。
     * <p>组装顺序（由内到外）：
     * <ol>
     *   <li>原始 Handler（Spring Boot 创建的默认 handler 链）</li>
     *   <li>{@link JsonAccessLogHandler}（记录访问日志）</li>
     *   <li>{@link StatisticHandler}（记录请求指标）</li>
     *   <li>{@link TraceIdInjectHandler}（最外层，注入 trace ID）</li>
     * </ol>
     */
    @Bean
    public WebServerFactoryCustomizer<JettyServletWebServerFactory> jettyHandlerChainCustomizer(
            ObjectProvider<JsonAccessLogHandler> accessLogProvider,
            ObjectProvider<StatisticHandler> statisticProvider,
            ObjectProvider<TraceIdInjectHandler> traceIdProvider) {
        return factory -> factory.addServerCustomizers(server -> {
            Handler current = server.getHandler();
            if (current == null) {
                return;
            }
            JsonAccessLogHandler a = accessLogProvider.getIfAvailable();
            if (a != null) {
                a.setHandler(current);
                current = a;
            }
            StatisticHandler s = statisticProvider.getIfAvailable();
            if (s != null) {
                s.setHandler(current);
                current = s;
            }
            TraceIdInjectHandler t = traceIdProvider.getIfAvailable();
            if (t != null) {
                t.setHandler(current);
                current = t;
            }
            server.setHandler(current);
            log.info("JettyAutoConfiguration: Handler chain assembled (accessLog={} statistic={} traceId={})",
                    a != null, s != null, t != null);
        });
    }

    /**
     * 把嵌入式 Jetty 的 {@link Server} 实例注册为 Spring Bean。
     * <p>Spring Boot 默认<strong>不</strong>把 {@code JettyServletWebServerFactory.getWebServer()}
     * 内部创建的 {@link Server} 注册成 bean，但本类的
     * {@link #jettyThreadPoolMetrics(Server)} / {@link #jettyThreadPoolUpdater} 等 bean
     * 都需按类型注入 {@link Server}。
     * <p><strong>时序保证</strong>：customizer bean 在 {@code finishBeanFactoryInitialization}
     * 阶段被创建；其 {@code customize()} 在 {@code onRefresh} 阶段被 BPP 调用，向 factory
     * 追加 {@code addServerCustomizers} lambda；该 lambda 在
     * {@code factory.getWebServer()} 内执行（BeanFactory 已就绪），把 {@link Server}
     * 注册为 bean——随后 {@code finishBeanFactoryInitialization} 实例化依赖 {@link Server}
     * 的 bean 时类型匹配成功。{@code @Lazy} 仅为安全网，不能替代本 customizer。
     */
    @Bean
    public WebServerFactoryCustomizer<JettyServletWebServerFactory> jettyServerBeanRegistrar(
            BeanFactory beanFactory) {
        ConfigurableListableBeanFactory bf = (ConfigurableListableBeanFactory) beanFactory;
        return factory -> factory.addServerCustomizers(server -> {
            bf.registerSingleton("jettyServer", server);
            log.info("JettyAutoConfiguration: registered Server bean (jettyServer) into BeanFactory");
        });
    }
}
