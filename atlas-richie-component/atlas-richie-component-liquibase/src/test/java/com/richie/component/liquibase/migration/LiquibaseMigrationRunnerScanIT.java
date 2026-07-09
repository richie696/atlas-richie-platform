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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiquibaseMigrationRunnerScanIT {

    @Test
    void afterSingletonsInstantiated_mergesScannedChangeLogsInDryRun() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:liquibase-scan;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        LiquibaseProperties properties = new LiquibaseProperties();
        properties.setEnable(true);
        properties.setEnableScan(true);
        properties.setDryRun(true);
        ChangeLogResolver resolver = mock(ChangeLogResolver.class);
        when(resolver.resolveChangeLogs(any())).thenReturn(
                java.util.List.of("db/changelog/db.changelog-master.yaml"));
        LiquibaseMigrationRunner runner = new LiquibaseMigrationRunner(
                dataSource, properties, new ChangeLogRegistry(), resolver);

        assertThatCode(runner::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
}
