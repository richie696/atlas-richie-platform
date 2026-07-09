/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
