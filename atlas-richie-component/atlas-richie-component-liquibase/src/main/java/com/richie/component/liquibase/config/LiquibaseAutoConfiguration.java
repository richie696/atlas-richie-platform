package com.richie.component.liquibase.config;

import com.richie.component.liquibase.migration.ChangeLogRegistry;
import com.richie.component.liquibase.migration.ChangeLogResolver;
import com.richie.component.liquibase.migration.LiquibaseMigrationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Liquibase 通用自动配置。
 * <p>
 * 注册 ChangeLog 注册表、解析器及迁移运行器，由配置前缀 {@code platform.component.liquibase.*} 控制是否启用与 changelog 路径。
 *
 * @author richie696
 * @since 2025-12-15
 */
@AutoConfiguration
@ConfigurationPropertiesScan("com.richie.component.liquibase.config")
@EnableConfigurationProperties(LiquibaseProperties.class)
public class LiquibaseAutoConfiguration {

    /**
     * 默认构造函数（供 Spring 使用）。
     */
    public LiquibaseAutoConfiguration() {
    }

    /**
     * 注册 changelog 注册表 Bean，供各组件按需注册自己的 changelog 路径。
     *
     * @return ChangeLogRegistry 实例
     */
    @Bean
    public ChangeLogRegistry changeLogRegistry() {
        return new ChangeLogRegistry();
    }

    /**
     * 注册 Liquibase 迁移运行器，在单例初始化后根据配置执行迁移。
     *
     * @param dataSource 数据源
     * @param properties Liquibase 配置
     * @param registry   changelog 注册表
     * @return LiquibaseMigrationRunner 实例
     */
    @Bean
    public LiquibaseMigrationRunner liquibaseMigrationRunner(DataSource dataSource,
                                                             LiquibaseProperties properties,
                                                             ChangeLogRegistry registry) {
        return new LiquibaseMigrationRunner(dataSource, properties, registry, new ChangeLogResolver());
    }
}
