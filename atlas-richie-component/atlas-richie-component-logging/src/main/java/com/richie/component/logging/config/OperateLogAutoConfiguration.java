package com.richie.component.logging.config;

import com.richie.component.liquibase.migration.ChangeLogRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;


/**
 * 操作日志自动配置类。
 * <p>
 * 注册访问日志切面、持久化任务等，并在启用 DB 持久化时向 ChangeLogRegistry 注册 logging 模块的 changelog。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-09 15:58:40
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@MapperScan("com.richie.component.logging.mapper")
@EnableConfigurationProperties({OperateLogProperties.class})
@ConfigurationPropertiesScan("com.richie.component.logging.config")
public class OperateLogAutoConfiguration {

    /** 供各组件注册 changelog 的注册表 */
    private final ChangeLogRegistry changeLogRegistry;

    /** 操作日志配置 */
    private final OperateLogProperties properties;

    /**
     * 初始化：若启用数据库持久化，则注册 logging 模块的 Liquibase changelog。
     */
    @PostConstruct
    public void init() {
        log.info("Do OperateLogAutoConfiguration init.");
        if (properties.isDbPersistent()) {
            changeLogRegistry.add("db/changelog/logging/db.changelog-master.yaml");
        }
    }

}
