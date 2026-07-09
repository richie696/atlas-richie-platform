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
package com.richie.component.storage.local.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.richie.component.liquibase.migration.ChangeLogRegistry;
import com.richie.component.storage.core.StorageEngineProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

/**
 * 本地存储自动装配
 *
 * <p>注册本地存储相关的初始化器与运行器，
 * 通过 Spring Boot AutoConfiguration 方式生效。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-10-14
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(MybatisPlusAutoConfiguration.class)
@RequiredArgsConstructor
@MapperScan("com.richie.component.storage.local.repository.mapper")
public class LocalStorageAutoConfiguration {

    /** 变更日志注册表，用于注册本地存储的 Liquibase 脚本 */
    private final ChangeLogRegistry changeLogRegistry;

    /**
     * 注册本地存储的数据库变更日志路径。
     */
    @PostConstruct
    public void init() {
        log.info("Do LocalStorageAutoConfiguration init.");
        changeLogRegistry.add("db/changelog/storage-local/db.changelog-master.yaml");
    }

    /**
     * 本地存储引擎 Provider（支持自动模式 + 手动模式）
     */
    @Bean
    public StorageEngineProvider localStorageEngineProvider() {
        return new LocalStorageEngineProvider();
    }
}


