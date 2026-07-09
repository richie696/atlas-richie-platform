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
package com.richie.component.concurrency.config;

import com.richie.component.concurrency.config.configuration.AlgorithmAutoConfiguration;
import com.richie.component.concurrency.config.configuration.DynamicExecutorRegistrar;
import com.richie.component.concurrency.config.properties.CircuitBreakerProperties;
import com.richie.component.concurrency.config.properties.PoolProperties;
import com.richie.component.concurrency.config.properties.RateLimiterProperties;
import com.richie.component.concurrency.threadpool.DynamicExecutor;
import com.richie.component.concurrency.threadpool.ThreadPoolConfigRefresher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Map;

/**
 * 并发组件统一自动装配入口 —— 聚合限流器、熔断器与动态线程池的装配。
 *
 * <p>本类是 Spring Boot {@code AutoConfiguration.imports} 中注册的唯一入口,
 * 通过 {@link Import @Import} 引入子装配类:</p>
 * <ul>
 *   <li>{@link AlgorithmAutoConfiguration} — 令牌桶限流器、熔断器 Bean 注册</li>
 * </ul>
 *
 * <p>动态线程池装配由 {@link DynamicExecutorRegistrar} ({@code BeanDefinitionRegistryPostProcessor})
 * 独立处理,详见该类 Javadoc 说明。</p>
 *
 * <p>关于 {@link PoolProperties} 注册:Spring Boot 4.x 自动绑定嵌套
 * {@code Map<String, POJO>} 行为存在兼容性问题,故动态线程池装配改走
 * {@link DynamicExecutorRegistrar} 主动绑定路径,但仍保留 {@link PoolProperties}
 * 的 {@code @ConfigurationProperties} 注册以备未来兼容性恢复时启用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties({
        ConcurrencyProperties.class,
        RateLimiterProperties.class,
        CircuitBreakerProperties.class,
        PoolProperties.class
})
@Import(AlgorithmAutoConfiguration.class)
public class ConcurrencyAutoConfiguration {

    /**
     * 动态线程池 BeanDefinition 注册器 —— 在 Spring 最早扩展点
     * 从 Environment 绑定 {@code platform.concurrency.thread-pools} 并注册
     * 每个命名池的 {@link DynamicExecutor} BeanDefinition。
     *
     * <p>Spring 会自动识别 {@code BeanDefinitionRegistryPostProcessor} 类型的 Bean
     * 并调用其 {@code postProcessBeanDefinitionRegistry} 方法。</p>
     *
     * @return Registrar 实例
     */
    @Bean
    public DynamicExecutorRegistrar dynamicExecutorRegistrar() {
        return new DynamicExecutorRegistrar();
    }

    /**
     * 线程池配置自动刷新器 —— 监听 Spring Cloud {@code EnvironmentChangeEvent},
     * 自动比对并刷新受影响的 {@link DynamicExecutor} 线程池。
     *
     * <p>仅当 classpath 中存在 Spring Cloud Context(即
     * {@code org.springframework.cloud.context.environment.EnvironmentChangeEvent})
     * 时此 Bean 才会创建。无需任何额外配置即可生效。</p>
     *
     * @param executors   全部已注册的 {@link DynamicExecutor} Bean,按 poolName 索引
     * @param environment Spring Environment,本方法内部用其创建 {@link Binder}
     * @param properties  当前配置属性
     * @return 配置刷新器实例
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.cloud.context.environment.EnvironmentChangeEvent")
    public ThreadPoolConfigRefresher threadPoolConfigRefresher(
            Map<String, DynamicExecutor> executors,
            ConfigurableEnvironment environment,
            ConcurrencyProperties properties) {
        // Binder 由本方法主动创建,不依赖 Spring 容器提供 Binder bean。
        // ConfigurationPropertySources.get(env) 包含 application.yml + spring.config.import (Nacos)
        // 等所有来源,Nacos 推送时 RefreshEvent 会触发环境重建,Binder 自动看到最新值。
        Binder binder = new Binder(ConfigurationPropertySources.get(environment));
        return new ThreadPoolConfigRefresher(executors, binder, properties);
    }
}