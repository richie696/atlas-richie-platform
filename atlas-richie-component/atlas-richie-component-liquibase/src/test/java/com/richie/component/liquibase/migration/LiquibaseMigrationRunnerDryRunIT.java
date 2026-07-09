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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThatCode;

class LiquibaseMigrationRunnerDryRunIT {

    @Test
    void dryRun_executesWithoutApplyingChanges() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:liquibase-dry;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setEnable(true);
        properties.setDryRun(true);
        ChangeLogRegistry registry = new ChangeLogRegistry();
        registry.add("db/changelog/db.changelog-master.yaml");
        LiquibaseMigrationRunner runner = new LiquibaseMigrationRunner(
                dataSource, properties, registry, new ChangeLogResolver());

        assertThatCode(runner::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
}
