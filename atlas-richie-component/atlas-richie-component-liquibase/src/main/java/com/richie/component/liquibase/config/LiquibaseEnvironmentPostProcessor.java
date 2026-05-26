package com.richie.component.liquibase.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * 为 Spring Boot 设置默认的 Liquibase 行为：
 * <p>
 * - 如果用户未显式配置 {@code spring.liquibase.enabled}，则默认关闭 Spring Boot 自带的 Liquibase；
 * - 用户如需启用 Spring Boot 自带的 Liquibase，可在业务侧配置 {@code spring.liquibase.enabled=true} 覆盖。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16 10:00:00
 */
public class LiquibaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "richie-liquibase-defaults";

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public LiquibaseEnvironmentPostProcessor() {
    }

    /**
     * 在环境准备阶段注入默认属性：当未配置 spring.liquibase.enabled 时默认关闭 Spring Boot 自带的 Liquibase。
     *
     * @param environment 可配置环境
     * @param application Spring 应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 仅当外部未显式配置 spring.liquibase.enabled 时，才设置默认值为 false
        if (!environment.containsProperty("spring.liquibase.enabled")) {
            Map<String, Object> defaults = new HashMap<>();
            defaults.put("spring.liquibase.enabled", "false");
            MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, defaults);
            // 放到较高优先级，确保作为默认值生效，但仍可被显式配置覆盖
            environment.getPropertySources().addFirst(source);
        }
    }

    /**
     * 返回处理器顺序，使用最高优先级以优先于大部分内置处理器执行。
     *
     * @return Ordered.HIGHEST_PRECEDENCE
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

