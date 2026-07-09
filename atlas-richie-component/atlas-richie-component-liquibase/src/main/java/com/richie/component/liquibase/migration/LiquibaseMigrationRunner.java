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
package com.richie.component.liquibase.migration;

import com.richie.component.liquibase.config.LiquibaseProperties;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

import javax.sql.DataSource;
import java.io.StringWriter;
import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

/**
 * 通用 Liquibase 迁移运行器。
 * <p>
 * 通过配置开关执行表结构迁移，避免在业务代码中硬编码 DDL。在 Spring 单例初始化完成后根据
 * {@link LiquibaseProperties} 与 {@link ChangeLogRegistry} 解析出的 changelog 执行迁移。
 *
 * @author richie696
 * @since 2025-12-15
 */
@Slf4j
@RequiredArgsConstructor
public class LiquibaseMigrationRunner implements SmartInitializingSingleton {

    /** 数据源 */
    private final DataSource dataSource;
    /** Liquibase 配置 */
    private final LiquibaseProperties properties;
    /** 组件注册的 changelog 注册表 */
    private final ChangeLogRegistry registry;
    /** changelog 路径解析器 */
    private final ChangeLogResolver changeLogResolver;

    @Override
    public void afterSingletonsInstantiated() {
        runMigration();
    }

    /**
     * 根据配置与注册表解析 changelog 并执行迁移（或 dry-run 输出 SQL）。
     */
    private void runMigration() {
        if (!properties.isEnable()) {
            return;
        }
        // 1) 组件注册的 changelog
        Set<String> resolvedChangeLogs = new HashSet<>(registry.getAll());
        // 2) 配置文件的 changelog（支持通配）
        if (properties.isEnableScan()) {
            resolvedChangeLogs.addAll(changeLogResolver.resolveChangeLogs(properties));
        }
        boolean dryRun = properties.isDryRun();

        try {
            for (String changeLogFile : resolvedChangeLogs) {
                try (Connection connection = dataSource.getConnection()) {
                    Database database = DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection));

                    try (Liquibase liquibase = new Liquibase(changeLogFile, new ClassLoaderResourceAccessor(), database)) {
                        if (dryRun) {
                            StringWriter writer = new StringWriter();
                            liquibase.updateSql(new Contexts(), new LabelExpression(), writer);
                            log.info("[liquibase] Dry run SQL ({}):\n{}", changeLogFile, writer);
                        } else {
                            liquibase.update(new Contexts(), new LabelExpression());
                            log.info("[liquibase] 迁移执行完成，changelog={}", changeLogFile);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[liquibase] 迁移执行失败", e);
            throw new RuntimeException("Liquibase migration failed: %s".formatted(e.getMessage()), e);
        }
    }
}
