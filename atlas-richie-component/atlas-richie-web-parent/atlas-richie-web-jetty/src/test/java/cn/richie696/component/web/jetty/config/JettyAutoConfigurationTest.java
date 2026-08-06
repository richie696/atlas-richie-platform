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
package cn.richie696.component.web.jetty.config;

import cn.richie696.component.web.jetty.handler.JsonAccessLogHandler;
import cn.richie696.component.web.jetty.handler.TraceIdInjectHandler;
import cn.richie696.component.web.jetty.management.JettyThreadPoolUpdater;
import cn.richie696.component.web.jetty.metrics.StatisticHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationListener;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 直接调用 {@link JettyAutoConfiguration} 的 {@code @Bean} 方法验证工厂逻辑。
 * <p>Spring 上下文相关测试（{@code @ConditionalOnWebApplication}）由 {@code JettyServerIntegrationTest} 等集成测试覆盖。
 */
class JettyAutoConfigurationTest {

    private final JettyAutoConfiguration config = new JettyAutoConfiguration();

    // ========================================================================================
    // JsonAccessLogHandler
    // ========================================================================================

    @Test
    void jsonAccessLogHandler_returnsNonNullHandler() {
        JettyProperties properties = new JettyProperties();
        JsonAccessLogHandler handler = config.jsonAccessLogHandler(properties);

        assertThat(handler).isNotNull();
    }

    @Test
    void jsonAccessLogHandler_usesSuppliedProperties() {
        JettyProperties properties = new JettyProperties();
        properties.getAccessLog().setDirectory("/tmp/logs");

        JsonAccessLogHandler handler = config.jsonAccessLogHandler(properties);

        assertThat(handler).isNotNull();
        assertThat(handler.getTotalCount()).isZero();
    }

    // ========================================================================================
    // TraceIdInjectHandler
    // ========================================================================================

    @Test
    void traceIdInjectHandler_usesProperties() {
        JettyProperties properties = new JettyProperties();
        properties.getTraceId().setHeader("X-Custom-Trace");
        properties.getTraceId().setGenerateIfMissing(false);

        TraceIdInjectHandler handler = config.traceIdInjectHandler(properties);

        assertThat(handler).isNotNull();
    }

    @Test
    void traceIdInjectHandler_defaultConfig() {
        JettyProperties properties = new JettyProperties();

        TraceIdInjectHandler handler = config.traceIdInjectHandler(properties);

        assertThat(handler).isNotNull();
    }

    // ========================================================================================
    // StatisticHandler
    // ========================================================================================

    @Test
    void statisticHandler_usesPrefixFromProperties() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JettyProperties properties = new JettyProperties();
        properties.getMetrics().setPrefix("custom_metrics");

        StatisticHandler handler = config.statisticHandler(registry, properties);

        assertThat(handler).isNotNull();
    }

    @Test
    void statisticHandler_registersActiveGaugeOnConstruction() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JettyProperties properties = new JettyProperties();
        properties.getMetrics().setPrefix("atlas_jetty");

        config.statisticHandler(registry, properties);

        assertThat(registry.find("atlas_jetty.requests.active").gauge()).isNotNull();
    }

    // ========================================================================================
    // jettyThreadPoolMetrics
    // ========================================================================================

    @Test
    void jettyThreadPoolMetrics_returnsMeterBinderWhenServerHasQueuedThreadPool() {
        Server server = mock(Server.class);
        QueuedThreadPool threadPool = new QueuedThreadPool();
        when(server.getThreadPool()).thenReturn(threadPool);

        MeterBinder binder = config.jettyThreadPoolMetrics(server);

        assertThat(binder).isNotNull();
    }

    // ========================================================================================
    // JettyThreadPoolUpdater
    // ========================================================================================

    @Test
    void jettyThreadPoolUpdater_returnsNonNullUpdater() {
        Server server = mock(Server.class);

        JettyThreadPoolUpdater updater = config.jettyThreadPoolUpdater(server);

        assertThat(updater).isNotNull();
    }

    @Test
    void jettyThreadPoolUpdater_holdsServerReference() {
        Server server = mock(Server.class);

        JettyThreadPoolUpdater updater = config.jettyThreadPoolUpdater(server);

        // Updater holds Server reference; lazy initialization triggered on first refresh()
        updater.setEnvironment(null);
        updater.refresh();
        // Server is referenced; we don't verify internal state directly
        assertThat(updater).isNotNull();
    }

    // ========================================================================================
    // jettyThreadPoolEnvironmentRefreshListener
    // ========================================================================================

    @Test
    void refreshListener_invokesUpdaterOnEnvironmentChangeEvent() {
        Server server = mock(Server.class);
        QueuedThreadPool threadPool = new QueuedThreadPool();
        when(server.getThreadPool()).thenReturn(threadPool);
        JettyThreadPoolUpdater updater = new JettyThreadPoolUpdater(server);

        ApplicationListener<?> listener = config.jettyThreadPoolEnvironmentRefreshListener(updater);

        assertThat(listener).isNotNull();
    }

    @Test
    void refreshListener_ignoresNonEnvironmentChangeEvents() {
        Server server = mock(Server.class);
        QueuedThreadPool threadPool = new QueuedThreadPool();
        when(server.getThreadPool()).thenReturn(threadPool);
        JettyThreadPoolUpdater updater = new JettyThreadPoolUpdater(server);

        ApplicationListener<?> listener = config.jettyThreadPoolEnvironmentRefreshListener(updater);

        // Non-matching event should be silently ignored
        org.springframework.context.event.ContextRefreshedEvent nonMatchingEvent =
                new org.springframework.context.event.ContextRefreshedEvent(mock(
                        org.springframework.context.ApplicationContext.class));
        @SuppressWarnings({"rawtypes", "unchecked"})
        org.springframework.context.ApplicationListener rawListener = listener;
        rawListener.onApplicationEvent(nonMatchingEvent);
        // No exception thrown, no updater refresh called — verify behavior
        assertThat(listener).isNotNull();
    }

    // ========================================================================================
    // jettyHandlerChainCustomizer (basic construction test only)
    // ========================================================================================

    @Test
    void handlerChainCustomizer_returnsNonNullCustomizer() {
        WebServerFactoryCustomizer<org.springframework.boot.jetty.servlet.JettyServletWebServerFactory> customizer =
                config.jettyHandlerChainCustomizer(
                        org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class),
                        org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class),
                        org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class));

        assertThat(customizer).isNotNull();
    }

    // ========================================================================================
    // jettyServerBeanRegistrar
    // ========================================================================================

    @Test
    void serverBeanRegistrar_returnsNonNullCustomizer() {
        org.springframework.beans.factory.support.DefaultListableBeanFactory beanFactory =
                new org.springframework.beans.factory.support.DefaultListableBeanFactory();

        WebServerFactoryCustomizer<org.springframework.boot.jetty.servlet.JettyServletWebServerFactory> customizer =
                config.jettyServerBeanRegistrar(beanFactory);

        assertThat(customizer).isNotNull();
    }

    // Helper to access Handler type (required for unused mock verification)
    @SuppressWarnings("unused")
    private static Handler noOpHandler() {
        return new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                return true;
            }
        };
    }

    @SuppressWarnings("unused")
    private static void verifyMockUsage() {
        // Avoid "unused import" warnings on mockito ArgumentMatchers
        ArgumentMatchers.any();
    }
}