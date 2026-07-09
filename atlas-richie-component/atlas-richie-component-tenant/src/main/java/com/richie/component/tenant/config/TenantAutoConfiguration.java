/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.config;

import com.richie.component.tenant.circuit.DataSourceCircuitBreaker;
import com.richie.component.tenant.circuit.DataSourceHealthProbe;
import com.richie.component.tenant.context.ScopedValueHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.TenantContextHolder;
import com.richie.component.tenant.context.TransactionTenantHolder;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.component.tenant.datasource.DynamicTenantDataSource;
import com.richie.component.tenant.cross.TenantTaskDecorator;
import com.richie.component.tenant.cross.TenantTaskDecoratorBeanPostProcessor;
import com.richie.component.tenant.handler.TenantExceptionHandler;
import com.richie.component.tenant.handler.TenantMetaObjectHandler;
import com.richie.component.tenant.interceptor.ConnectionResetInterceptor;
import com.richie.component.tenant.interceptor.DynamicTableNameInnerInterceptor;
import com.richie.component.tenant.interceptor.TenantLineInnerInterceptor;
import com.richie.component.tenant.interceptor.TenantStrategyInterceptor;
import com.richie.component.tenant.monitor.TenantMeterBinder;
import com.richie.component.tenant.monitor.TenantMetricsCollector;
import com.richie.component.tenant.reactive.TenantWebFilter;
import com.richie.component.tenant.spi.CachingTenantInfoProvider;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.richie.component.tenant.strategy.ColumnStrategy;
import com.richie.component.tenant.strategy.DatabaseStrategy;
import com.richie.component.tenant.strategy.HybridStrategy;
import com.richie.component.tenant.strategy.SchemaStrategy;
import com.richie.component.tenant.strategy.TableStrategy;
import com.richie.component.tenant.strategy.TenancyStrategy;
import com.richie.component.tenant.strategy.TenancyStrategyFactory;
import com.richie.component.tenant.web.TenantIdentityFilter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * 多租户组件自动配置（2.0 重写）。
 *
 * <p>按职责拆分为多个内部配置类：
 * <ul>
 *   <li>{@link CoreConfig} — 配置绑定 + 上下文持有器初始化 + 通信框架诊断（任何环境）</li>
 *   <li>{@link ServletConfig} — Servlet Filter + ExceptionHandler（仅 Servlet Web）</li>
 *   <li>{@link ReactiveConfig} — WebFlux WebFilter + Reactor Context 集成（仅 Reactive Web）</li>
 *   <li>{@link MyBatisConfig} — SQL 拦截器 + 策略工厂（仅 MyBatis classpath）</li>
 * </ul>
 *
 * <h2>微服务通信框架租户上下文透传</h2>
 * <p>多租户上下文（{@code X-Tenant-ID}）在微服务间透传需引入对应的通信组件（二选一）：
 * <ul>
 *   <li><b>HTTP</b> — 引入 {@code atlas-richie-component-microservice}（覆盖 Feign + RestClient）</li>
 *   <li><b>gRPC</b> — 引入 {@code atlas-richie-component-grpc}（{@code x-tenant-id} 已在默认透传白名单中）</li>
 * </ul>
 * <p>启动时自动检测；若两者均未引入，输出 WARN 日志（不影响启动，但跨服务调用时租户上下文会中断）。</p>
 *
 * @author richie696
 * @since 2.0
 */
@AutoConfiguration
@EnableConfigurationProperties(MultiTenancyProperties.class)
public class TenantAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantAutoConfiguration.class);

    // ==================== 核心配置（任何环境） ====================

    @Configuration
    static class CoreConfig {

        private final MultiTenancyProperties properties;

        CoreConfig(MultiTenancyProperties properties) {
            this.properties = properties;
        }

        @PostConstruct
        public void initTenantContext() {
            TenantContextHolder holder;
            if (properties.isForceThreadLocal()) {
                holder = new ThreadLocalHolder();
                log.info("TenantContext initialized with ThreadLocal + micrometer context-propagation (force-thread-local=true)");
            } else {
                holder = new ScopedValueHolder();
                log.info("TenantContext initialized with ScopedValue (preferred for virtual threads)");
            }
            TenantContext.init(holder);

            // 注册事务租户切换检测器
            TenantContext.setTransactionChecker(TransactionTenantHolder::checkSwitch);

            // 微服务通信框架诊断
            diagnoseCommunicationFrameworks(properties);
        }

        @Bean
        @ConditionalOnMissingBean
        public TenantInfoProvider tenantInfoProvider() {
            return new com.richie.component.tenant.spi.NoOpTenantInfoProvider();
        }

        /**
         * TTL 缓存装饰器，挂在真实 {@link TenantInfoProvider} 上拦截重复查询。
         * 默认开启（{@code multi-tenancy.cache.tenant-info.enabled=true}）。
         * 若关闭则每次 SQL 都直接穿透到业务实现的 {@link TenantInfoProvider}。
         */
        @Bean
        @Primary
        @ConditionalOnProperty(prefix = "multi-tenancy.cache.tenant-info", name = "enabled", havingValue = "true", matchIfMissing = true)
        public CachingTenantInfoProvider cachingTenantInfoProvider(TenantInfoProvider tenantInfoProvider,
                                                                    ObjectProvider<TenantMetricsCollector> metricsCollectorProvider) {
            MultiTenancyProperties.TenantInfoCacheConfig cfg = properties.getCache();
            return new CachingTenantInfoProvider(tenantInfoProvider, cfg.getTtlSeconds(), cfg.getMaxSize(),
                metricsCollectorProvider.getIfUnique());
        }

        @Bean
        @ConditionalOnMissingBean
        public DataSourceCircuitBreaker dataSourceCircuitBreaker() {
            return new DataSourceCircuitBreaker(properties);
        }

        @Bean
        @ConditionalOnMissingBean
        public DataSourceHealthProbe dataSourceHealthProbe(
            ObjectProvider<DynamicTenantDataSource> dynamicDataSourceProvider,
            DataSourceCircuitBreaker circuitBreaker) {
            DynamicTenantDataSource dynamicDataSource = dynamicDataSourceProvider.getIfAvailable();
            if (dynamicDataSource == null) {
                log.debug("DynamicTenantDataSource not available, DataSourceHealthProbe disabled");
                return new DataSourceHealthProbe(circuitBreaker);
            }
            return new DataSourceHealthProbe(dynamicDataSource, circuitBreaker);
        }

        @Bean
        @ConditionalOnMissingBean
        public TenantTaskDecorator tenantTaskDecorator() {
            return new TenantTaskDecorator();
        }

        /**
         * 自动给所有 Spring {@code ThreadPoolTaskExecutor} / {@code ThreadPoolTaskScheduler}
         * Bean 注入 {@link TenantTaskDecorator},解决 {@code @Async} / {@code @Scheduled}
         * 任务租户上下文丢失的 silent failure。
         *
         * <p>作为 {@link org.springframework.beans.factory.config.BeanPostProcessor} Bean
         * 注册,Spring 会在所有其他 Bean 初始化完成后调用 {@code postProcessAfterInitialization},
         * 自动注入任务装饰器,业务代码无需手动配置。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        public TenantTaskDecoratorBeanPostProcessor tenantTaskDecoratorBeanPostProcessor(
                TenantTaskDecorator tenantTaskDecorator) {
            return new TenantTaskDecoratorBeanPostProcessor(tenantTaskDecorator);
        }

        @Bean
        @ConditionalOnMissingBean(MetaObjectHandler.class)
        public TenantMetaObjectHandler tenantMetaObjectHandler() {
            return new TenantMetaObjectHandler(properties);
        }

        /**
         * 监控指标收集器（轻量 AtomicLong 计数器 holder，无 Micrometer 依赖）。
         * <p>始终可用，拦截器通过 {@code ObjectProvider} 获取（缺省时跳过采集）。
         * MeteorBinder 由 {@link MetricsConfig} 通过 {@code @ConditionalOnClass(MeterRegistry)}
         * 控制，Actuator 端暴露由 {@code management.metrics.enable.*} 控制。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        public TenantMetricsCollector tenantMetricsCollector() {
            return new TenantMetricsCollector();
        }

        // ==================== 微服务通信框架诊断 ====================

        /**
         * 检测 classpath 上的微服务通信框架，确认租户上下文可透传。
         *
         * <p>两种通信协议互斥，只需引入其一：
         * <ul>
         *   <li><b>HTTP</b> — 引入 {@code atlas-richie-component-microservice}（覆盖 Feign + RestClient）</li>
         *   <li><b>gRPC</b> — 引入 {@code atlas-richie-component-grpc}</li>
         * </ul>
         * <p>若两者均未引入，输出 WARN 日志（不阻断启动）。
         */
        private void diagnoseCommunicationFrameworks(MultiTenancyProperties props) {
            if (!props.isEnabled()) {
                return;
            }

            // 单体应用无需跨服务透传检测
            if (!props.isMicroservice()) {
                log.info("[多租户] 单体应用模式 (microservice=false)，跳过通信框架检测");
                return;
            }

            boolean hasHttp = isClassPresent("feign.RequestInterceptor")
                    || isClassPresent("org.springframework.web.client.RestClient");
            boolean hasGrpc = isClassPresent("io.grpc.ClientInterceptor");

            if (hasHttp || hasGrpc) {
                log.info("[多租户] 微服务通信框架已就绪: {}{}{}",
                    hasHttp ? "HTTP (Feign/RestClient)" : "",
                    hasHttp && hasGrpc ? " + " : "",
                    hasGrpc ? "gRPC" : "");
            } else {
                log.warn("[多租户] 微服务模式已开启但未检测到通信框架！跨服务调用时租户上下文将中断。");
                log.warn("  请根据通信协议引入其中一个组件:");
                log.warn("    • HTTP (Feign/RestClient) → atlas-richie-component-microservice");
                log.warn("    • gRPC                    → atlas-richie-component-grpc");
                log.warn("  若为单体应用，请设置 multi-tenancy.microservice=false 关闭此检测");
            }
        }

        private boolean isClassPresent(String className) {
            try {
                Class.forName(className, false, getClass().getClassLoader());
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    }

    // ==================== Micrometer 指标配置 ====================

    @Configuration
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MetricsConfig {

        private final DataSourceCircuitBreaker circuitBreaker;
        private final TenantMetricsCollector metricsCollector;
        private final ObjectProvider<CachingTenantInfoProvider> cachingProvider;

        MetricsConfig(DataSourceCircuitBreaker circuitBreaker,
                      ObjectProvider<TenantMetricsCollector> metricsCollectorProvider,
                      ObjectProvider<CachingTenantInfoProvider> cachingProvider) {
            this.circuitBreaker = circuitBreaker;
            this.metricsCollector = metricsCollectorProvider.getIfUnique();
            this.cachingProvider = cachingProvider;
        }

        /**
         * 多租户 Micrometer {@link io.micrometer.core.instrument.binder.MeterBinder}。
         * <p>仅在 {@link TenantMetricsCollector} 存在时注册指标；若 monitor 关闭，
         * {@code metricsCollector} 为 null，则 {@link TenantMeterBinder#bindTo} 自动跳过。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        public TenantMeterBinder tenantMeterBinder() {
            return new TenantMeterBinder(circuitBreaker, metricsCollector, cachingProvider);
        }
    }

    // ==================== Servlet Web 配置 ====================

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfig {

        private final MultiTenancyProperties properties;

        ServletConfig(MultiTenancyProperties properties) {
            this.properties = properties;
        }

        @Bean
        public FilterRegistrationBean<TenantIdentityFilter> tenantIdentityFilter(
            TenantInfoProvider tenantInfoProvider,
            ObjectProvider<List<String>> whitelistPathsProvider) {

            List<String> whitelistPaths = whitelistPathsProvider.getIfAvailable();
            TenantIdentityFilter filter = new TenantIdentityFilter(
                properties, tenantInfoProvider, whitelistPaths);

            FilterRegistrationBean<TenantIdentityFilter> registration = new FilterRegistrationBean<>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 500);
            registration.addUrlPatterns("/*");
            registration.setName("tenantIdentityFilter");
            return registration;
        }

        @Bean
        public TenantExceptionHandler tenantExceptionHandler() {
            return new TenantExceptionHandler();
        }
    }

    // ==================== MyBatis 配置 ====================

    @Configuration
    @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
    static class MyBatisConfig {

        private final MultiTenancyProperties properties;

        MyBatisConfig(MultiTenancyProperties properties) {
            this.properties = properties;
        }

        // ---------- 策略 Bean 注册 ----------

        @Bean
        public ColumnStrategy columnStrategy(TenantInfoProvider provider) {
            return new ColumnStrategy(properties, provider);
        }

        @Bean
        public TableStrategy tableStrategy(TenantInfoProvider provider) {
            return new TableStrategy(properties, provider);
        }

        @Bean
        public SchemaStrategy schemaStrategy(TenantInfoProvider provider) {
            return new SchemaStrategy(properties, provider);
        }

        @Bean
        public DatabaseStrategy databaseStrategy(TenantInfoProvider provider) {
            return new DatabaseStrategy(properties, provider);
        }

        @Bean
        public TenancyStrategyFactory tenancyStrategyFactory(List<TenancyStrategy> strategies) {
            return new TenancyStrategyFactory(strategies);
        }

        @Bean
        public HybridStrategy hybridStrategy(TenantInfoProvider provider,
                                             ColumnStrategy columnStrategy,
                                             TableStrategy tableStrategy,
                                             SchemaStrategy schemaStrategy,
                                             DatabaseStrategy databaseStrategy) {
            return new HybridStrategy(properties, provider,
                    columnStrategy, tableStrategy, schemaStrategy, databaseStrategy);
        }

        // ---------- MyBatis 拦截器 ----------

        /**
         * 注册 {@link TenantLineInnerInterceptor} (Column 模式 SQL 改写)。
         * <p>{@code @Order(2)}:在第 3 层执行,先于它的是策略调度 + 表名改写。</p>
         */
        @Bean
        @Order(2)
        public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantInfoProvider provider,
                                                                      ObjectProvider<TenantMetricsCollector> metricsCollectorProvider) {
            return new TenantLineInnerInterceptor(properties, provider, metricsCollectorProvider.getIfUnique());
        }

        /**
         * 注册 {@link DynamicTableNameInnerInterceptor} (表后缀改写)。
         * <p>{@code @Order(3)}:在第 2 层执行,先于它的是策略调度。</p>
         */
        @Bean
        @Order(3)
        public DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor(
                ObjectProvider<TenantMetricsCollector> metricsCollectorProvider) {
            return new DynamicTableNameInnerInterceptor(properties, metricsCollectorProvider.getIfUnique());
        }

        /**
         * 注册 {@link TenantStrategyInterceptor} (策略调度)。
         * <p>{@code @Order(4)}:在最外层执行(最先拦截),完成租户解析 + 熔断检查 +
         * 策略前置(schema切换/数据源路由/表后缀设置)。</p>
         */
        @Bean
        @Order(4)
        public TenantStrategyInterceptor tenantStrategyInterceptor(
            TenancyStrategyFactory factory,
            TenantInfoProvider provider,
            DataSourceCircuitBreaker circuitBreaker) {
            return new TenantStrategyInterceptor(properties, factory, provider, circuitBreaker);
        }

        /**
         * 注册 {@link ConnectionResetInterceptor} (连接资源清理)。
         * <p>{@code @Order(1)}:在最内层执行(紧贴 MyBatis Executor),SQL 完成后清理
         * {@code DataSourceContextHolder} + {@code TableSuffixHolder},
         * 防止连接归还连接池时携带租户状态导致跨租户数据泄漏。</p>
         */
        @Bean
        @Order(1)
        public ConnectionResetInterceptor connectionResetInterceptor() {
            return new ConnectionResetInterceptor(properties);
        }
    }

    // ==================== Reactive Web 配置 ====================

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(name = "reactor.core.publisher.Mono")
    static class ReactiveConfig {

        private final MultiTenancyProperties properties;
        private final TenantInfoProvider tenantInfoProvider;
        private final ObjectProvider<List<String>> whitelistPathsProvider;
        private final ObjectProvider<List<String>> superAdminPathsProvider;

        ReactiveConfig(MultiTenancyProperties properties,
                       TenantInfoProvider tenantInfoProvider,
                       ObjectProvider<List<String>> whitelistPathsProvider,
                       ObjectProvider<List<String>> superAdminPathsProvider) {
            this.properties = properties;
            this.tenantInfoProvider = tenantInfoProvider;
            this.whitelistPathsProvider = whitelistPathsProvider;
            this.superAdminPathsProvider = superAdminPathsProvider;
        }

        /**
         * Reactive 环境租户身份过滤器（WebFlux WebFilter）。
         * <p>与 Servlet 版 {@link TenantIdentityFilter} 同优先级，
         * 从 JWT / Header 解析租户，写入 Reactor {@code Context}。</p>
         */
        @Bean
        @ConditionalOnMissingBean
        public TenantWebFilter tenantWebFilter() {
            return new TenantWebFilter(
                properties,
                tenantInfoProvider,
                whitelistPathsProvider.getIfAvailable(),
                superAdminPathsProvider.getIfAvailable()
            );
        }

        /**
         * WebFlux 环境异常处理器（委托给已有的 {@link com.richie.component.tenant.handler.TenantExceptionHandler}）。
         * 已有的 {@code @RestControllerAdvice} 在 WebFlux 下同样生效。
         */
        @Bean
        @ConditionalOnMissingBean(name = "tenantWebFluxExceptionHandler")
        public com.richie.component.tenant.handler.TenantExceptionHandler tenantWebFluxExceptionHandler() {
            return new com.richie.component.tenant.handler.TenantExceptionHandler();
        }
    }
}
